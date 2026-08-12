package com.zirconpods.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.SystemClock
import android.util.Log
import com.zirconpods.protocol.ZirconProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream

/**
 * 锆势 RFCOMM 控制器（独立 App 进程内直连，不需要 Xposed/root）。
 * 连接模型：耳机只允许一个 RFCOMM 控制会话；先断开其他客户端（RoseLink）再连接。
 */
@SuppressLint("MissingPermission")
class ZirconRfcommController {

    enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

    companion object {
        private const val TAG = "ZirconController"
        private const val BATTERY_POLL_INTERVAL_MS = 30_000L
        private const val QUERY_INTERVAL_MS = 5_000L

        /** 已配对设备中识别锆势的关键字 */
        private val DEVICE_KEYWORDS = listOf("zircon", "rose")

        fun findZirconDevices(bluetoothAdapter: BluetoothAdapter): List<BluetoothDevice> {
            val bonded = bluetoothAdapter.bondedDevices.orEmpty()
            return bonded.filter { dev ->
                val name = dev.name?.lowercase() ?: return@filter false
                DEVICE_KEYWORDS.any { name.contains(it) }
            }.sortedByDescending { it.name }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _deviceName = MutableStateFlow<String?>(null)
    val deviceName: StateFlow<String?> = _deviceName

    private val _battery = MutableStateFlow(ZirconProtocol.BatteryInfo(null, null, null))
    val battery: StateFlow<ZirconProtocol.BatteryInfo> = _battery

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    private var socket: BluetoothSocket? = null
    private var readJob: Job? = null
    private var pollJob: Job? = null

    /** 连接已配对设备。调用前需持有 BLUETOOTH_CONNECT 权限。 */
    fun connect(device: BluetoothDevice) {
        if (_connectionState.value == ConnectionState.CONNECTED) return
        _connectionState.value = ConnectionState.CONNECTING
        _deviceName.value = device.name
        _lastError.value = null

        scope.launch {
            try {
                // 通过私有控制服务（0cf12d31）做服务发现连接；SPP/直连通道不被耳机识别为控制会话
                val sock = device.createInsecureRfcommSocketToServiceRecord(ZirconProtocol.CONTROL_UUID)
                sock.connect()
                socket = sock
                _connectionState.value = ConnectionState.CONNECTED
                startReadLoop(sock.inputStream)
                startPolling()
                Log.i(TAG, "RFCOMM connected: ${device.name}")
            } catch (e: IOException) {
                Log.e(TAG, "connect failed", e)
                _lastError.value = e.message ?: "连接失败"
                _connectionState.value = ConnectionState.ERROR
            }
        }
    }

    fun disconnect() {
        pollJob?.cancel()
        readJob?.cancel()
        pollJob = null
        readJob = null
        runCatching { socket?.close() }
        socket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    /** 发送命令（裸帧）。 */
    fun sendCommand(cmd: ByteArray) {
        val sock = socket ?: return
        scope.launch {
            try {
                sock.outputStream.write(cmd)
                sock.outputStream.flush()
            } catch (e: IOException) {
                Log.e(TAG, "send failed", e)
                _lastError.value = e.message ?: "发送失败"
                onSocketClosed()
            }
        }
    }

    fun sendAncMode(mode: ZirconProtocol.AncMode, depth: Int = 3) {
        sendCommand(ZirconProtocol.ancModeCommand(mode, depth))
    }

    fun sendGameMode(enabled: Boolean) {
        sendCommand(ZirconProtocol.gameModeCommand(enabled))
    }

    fun queryStatus() {
        sendCommand(ZirconProtocol.queryStatusCommand())
    }

    private fun startReadLoop(input: InputStream) {
        readJob = scope.launch {
            val buffer = ByteArray(1024)
            try {
                while (isActive) {
                    val n = input.read(buffer)
                    if (n <= 0) break
                    handleReceived(buffer.copyOf(n))
                }
            } catch (e: IOException) {
                Log.e(TAG, "read loop ended", e)
            } finally {
                onSocketClosed()
            }
        }
    }

    private fun handleReceived(data: ByteArray) {
        val frames = ZirconProtocol.parseFrames(data)
        ZirconProtocol.parseBattery(frames)?.let { _battery.value = it }
        ZirconProtocol.parseAncMode(frames)?.let { /* 供 UI 回显；当前查询键值不稳定，以本地状态为准 */ }
    }

    private fun startPolling() {
        pollJob = scope.launch {
            var lastQuery = 0L
            while (isActive) {
                val now = SystemClock.elapsedRealtime()
                // 立即查一次，之后定期
                if (now - lastQuery >= QUERY_INTERVAL_MS) {
                    queryStatus()
                    lastQuery = now
                }
                delay(QUERY_INTERVAL_MS)
            }
        }
    }

    private fun onSocketClosed() {
        if (_connectionState.value != ConnectionState.DISCONNECTED) {
            runCatching { socket?.close() }
            socket = null
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    fun release() {
        disconnect()
        scope.cancel()
    }
}
