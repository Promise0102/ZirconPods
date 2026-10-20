/* SPDX-License-Identifier: GPL-3.0-only
 * Session routing portions adapted from Equalizer314 AudioSessionReceiver and SessionEffectManager.
 * See third_party/Equalizer314/UPSTREAM.md for pinned sources and modifications. */
package local.roselink.lite

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.media.*
import android.media.audiofx.AudioEffect
import android.os.*
import org.json.JSONArray

/** User-started software FX service, independent of the Bluetooth control socket. */
class PhoneEqService : Service() {
    private val state = PhoneEqState()
    private val listeners = mutableSetOf<(PhoneEqState) -> Unit>()
    private val sessions = linkedMapOf<Int, String>()
    private val effects = linkedMapOf<Int, PhoneEqEffect>()
    private val history = ArrayDeque<String>()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var audio: AudioManager
    private var foreground = false
    private var stopping = false
    private val preferences by lazy { getSharedPreferences("phone_eq", MODE_PRIVATE) }
    inner class LocalBinder : Binder() { val service get() = this@PhoneEqService }
    override fun onBind(intent: Intent?) = LocalBinder()
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
            if (id <= 0) return // Never accept output-mix 0 from an external broadcast.
            val owner = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME)?.take(120).orEmpty()
            when (intent.action) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                    if (intent.getIntExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC) !in 0..2) return
                    offerSession(id, owner)
                }
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                    if (owner.isNotEmpty() && sessions[id] != owner) return
                    sessions.remove(id); effects.remove(id)?.release(); note("关闭会话 $id"); publish()
                }
            }
        }
    }
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) { if (state.enabled) rebuild() }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) { if (state.enabled) rebuild() }
    }
    private val monitor = object : Runnable {
        override fun run() { if (state.enabled && !stopping) { publish(); main.postDelayed(this, 2000) } }
    }
    override fun onCreate() {
        super.onCreate()
        audio = getSystemService(AudioManager::class.java)
        state.global = preferences.getBoolean("global", false)
        state.gains = try {
            val array = JSONArray(preferences.getString("gains", "[]"))
            IntArray(array.length()) { array.getInt(it) }.takeIf { CustomEqProtocol.valid(it) } ?: IntArray(10)
        } catch (_: Exception) { IntArray(10) }
        state.loaded = true
        val channel = NotificationChannel(CHANNEL, "手机端 EQ", NotificationManager.IMPORTANCE_LOW)
        channel.setSound(null, null); channel.setShowBadge(false)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val filter = IntentFilter(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_EXPORTED) else registerReceiver(receiver, filter)
        audio.registerAudioDeviceCallback(deviceCallback, main)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { disable(); return START_NOT_STICKY }
        if (intent?.action != ENABLE) { stopSelf(); return START_NOT_STICKY }
        if (Build.VERSION.SDK_INT < 28) { state.status = "手机端 EQ 需要 Android 9 及以上"; publish(); stopSelf(); return START_NOT_STICKY }
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(ID, notification())
            foreground = true
            val gains = intent.getIntArrayExtra(GAINS)
            require(CustomEqProtocol.valid(gains)) { "曲线必须是十段−6～+6dB增益" }
            state.gains = gains!!.clone(); state.global = intent.getBooleanExtra(GLOBAL, false); state.enabled = true
            preferences.edit().putString("gains", JSONArray(gains.toList()).toString()).putBoolean("global", state.global).apply()
            note("启用：${if (state.global) "全局兼容" else "播放器会话"}；自动余量 ${PhoneEqCurve.headroom(gains)}dB")
            rebuild(); main.removeCallbacks(monitor); main.postDelayed(monitor, 2000)
        } catch (error: Exception) {
            state.enabled = false; state.status = "手机 EQ 启动失败：${error.message}"
            note(state.status); releaseEffects(); publish(); stopForeground(STOP_FOREGROUND_REMOVE); foreground = false; stopSelf()
        }
        return START_NOT_STICKY
    }
    fun addListener(listener: (PhoneEqState) -> Unit) { listeners.add(listener); listener(state.copy()) }
    fun removeListener(listener: (PhoneEqState) -> Unit) { listeners.remove(listener) }
    fun offerSession(id: Int, owner: String) {
        if (id <= 0 || (sessions.size >= 16 && !sessions.containsKey(id))) return
        if (sessions[id] == owner) return
        sessions[id] = owner.take(120)
        note("播放器会话 $id ${owner.take(120).ifEmpty { "未提供包名" }}")
        if (state.enabled && !state.global && hasExternalOutput()) attach(id)
        publish()
    }
    fun retry() { if (state.enabled) rebuild() else publish() }
    fun disable() {
        state.enabled = false; main.removeCallbacks(monitor); releaseEffects()
        state.status = "手机端 EQ 已停用"; note("用户停用并释放音效"); publish()
        if (foreground) stopForeground(STOP_FOREGROUND_REMOVE)
        foreground = false; stopSelf()
    }
    private fun attach(id: Int) {
        effects.remove(id)?.release()
        if (Build.VERSION.SDK_INT < 28) return
        try { effects[id] = PhoneEqEffect.create(id, state.gains); note("会话 $id 已配置 ${effects[id]!!.bands} 频带，参数回读一致") }
        catch (error: Exception) { note("会话 $id 创建失败：${error.javaClass.simpleName} ${error.message?.take(300)}") }
    }
    private fun rebuild() {
        releaseEffects()
        if (state.enabled && hasExternalOutput()) {
            if (state.global) attach(0) else sessions.keys.toList().forEach { attach(it) }
        }
        publish()
    }
    private fun hasExternalOutput(): Boolean = try {
        audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type in setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE)
                || (Build.VERSION.SDK_INT >= 31 && it.type in setOf(AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER))
        }
    } catch (error: Exception) { note("读取输出设备失败：${error.message?.take(200)}"); false }
    private fun publish() {
        state.externalOutput = hasExternalOutput(); state.announcedSessions = sessions.size
        state.effects = effects.values.count { runCatching { it.controlled() }.getOrDefault(false) }
        if (state.enabled) state.status = when {
            !state.externalOutput -> "等待蓝牙或有线耳机连接；音效已暂停"
            effects.isNotEmpty() && state.effects == 0 -> "音效控制权已丢失或被停用，请关闭其他均衡器后重试"
            state.global && state.effects > 0 -> "全局音效参数已配置；该播放器是否经过音效链需试听"
            !state.global && state.effects > 0 -> "已配置 ${state.effects} 个播放器会话；效果需试听确认"
            !state.global && sessions.isEmpty() -> "等待播放器会话，请重新开始播放或打开播放器的系统均衡器"
            else -> "系统拒绝创建音效，请复制手机 EQ 诊断"
        }
        state.diagnostic = ("Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}\n模式：${if (state.global) "全局兼容（0）" else "播放器会话"}\n"
            + "系统可见耳机输出：${state.externalOutput}\n收到会话：${state.announcedSessions}；受控音效：${state.effects}\n"
            + "曲线：${state.gains.joinToString()}；输入余量：${PhoneEqCurve.headroom(state.gains)}dB\n"
            + "当前：${state.status}\n" + history.joinToString("\n"))
        listeners.toList().forEach { it(state.copy()) }
        if (foreground) getSystemService(NotificationManager::class.java).notify(ID, notification())
    }
    private fun note(message: String) { history.addLast(message); while (history.size > 24) history.removeFirst() }
    private fun releaseEffects() { effects.values.forEach { it.release() }; effects.clear() }
    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 101, Intent(this, MainActivity::class.java).setAction(OPEN), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 102, Intent(this, PhoneEqService::class.java).setAction(STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_headphones).setContentTitle("ZirconPods · 手机端 EQ")
            .setContentText(state.status).setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "停用 EQ", stop).build()).build()
    }
    override fun onDestroy() {
        stopping = true; main.removeCallbacksAndMessages(null); releaseEffects(); listeners.clear()
        unregisterReceiver(receiver); audio.unregisterAudioDeviceCallback(deviceCallback)
        if (foreground) stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    companion object {
        const val ENABLE = "local.roselink.lite.PHONE_EQ_ENABLE"
        const val STOP = "local.roselink.lite.PHONE_EQ_STOP"
        const val OPEN = "local.roselink.lite.PHONE_EQ_OPEN"
        const val GAINS = "gains"; const val GLOBAL = "global"
        private const val CHANNEL = "phone_equalizer"; private const val ID = 79
    }
}
