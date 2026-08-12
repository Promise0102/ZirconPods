package com.zirconpods.protocol

import java.util.UUID

/**
 * 弱水时砂「锆势」(ROSE Zircon) RFCOMM 控制协议。
 *
 * 逆向来源：RoseLink 3.7.7 (Android/Windows) + PC AF_BTH 直连黑盒验证 + 真人听感/耳机语音播报确认。
 * 详见 docs/reverse-engineering.md
 *
 * 连接：经典蓝牙 RFCOMM，SPP 服务（SCN 6）
 * 发送帧：裸命令，如 FF 00 03 09 02 00
 * 接收帧：DD [seq:1B] [len:1B] [key] [payload...] [csum:1B] AA
 *   csum = (0xDD + seq + len + sum(all body bytes)) & 0xFF
 */
object ZirconProtocol {

    /** SPP 服务 UUID（Android 用 createRfcommSocketToServiceRecord 自动解析 SCN） */
    val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

    /** 控制通道私有服务 UUID（RoseLink 实际使用的 RFCOMM 服务，SDP 解析到 SCN 6） */
    val CONTROL_UUID: UUID = UUID.fromString("0cf12d31-fac3-4553-bd80-d6832e7b3931")

    /** 已知的控制通道 MAC（可被扫描替代） */
    const val KNOWN_MAC = "95:11:06:00:48:78"

    /**
     * 噪声控制模式（UI 顺序：降噪 / 风噪 / 普通 / 通透）
     * 命令结构：FF 00 03 09 [key] [val]
     */
    enum class AncMode(val key: Int, val value: Int, val title: String) {
        NOISE_CANCELLING(0x01, 0x01, "降噪"),
        WIND(0x04, 0x00, "风噪"),
        NORMAL(0x02, 0x00, "普通"),
        TRANSPARENCY(0x03, 0x00, "通透");

        companion object {
            fun fromKey(key: Int): AncMode? = entries.firstOrNull { it.key == key }
        }
    }

    /** 降噪深度档（仅降噪模式，1~5 已确认可用，播报"深度降噪"） */
    data class NoiseDepth(val value: Int, val title: String)

    val NOISE_DEPTHS: List<NoiseDepth> = listOf(
        NoiseDepth(1, "轻度"),
        NoiseDepth(2, "适中"),
        NoiseDepth(3, "标准"),
        NoiseDepth(4, "较强"),
        NoiseDepth(5, "深度"),
    )

    /**
     * ANC 模式命令。
     * @param mode 目标模式
     * @param depth 仅降噪模式生效的深度档（1~5）
     */
    fun ancModeCommand(mode: AncMode, depth: Int = 3): ByteArray {
        val value = if (mode == AncMode.NOISE_CANCELLING) {
            depth.coerceIn(1, 5)
        } else {
            mode.value
        }
        return byteArrayOf(0xFF.toByte(), 0x00, 0x03, 0x09, mode.key.toByte(), value.toByte())
    }

    /** 游戏模式开关（开：04，关：00 —— 关闭命令待真机验证） */
    fun gameModeCommand(enabled: Boolean): ByteArray =
        byteArrayOf(0xFF.toByte(), 0x00, 0x03, 0x0E.toByte(), 0x01, if (enabled) 0x04 else 0x00)

    /** 状态查询（返回键值流：0C=电量、35=连接状态等） */
    fun queryStatusCommand(): ByteArray =
        byteArrayOf(0xFF.toByte(), 0x00, 0x03, 0x01, 0x06)

    /** 解析出的一帧 */
    data class Frame(val seq: Int, val key: Int, val payload: ByteArray)

    /**
     * 解析接收字节流中的全部帧。
     * 帧格式：DD [seq] [len] [key] [payload: len-1] [csum] AA
     */
    fun parseFrames(data: ByteArray): List<Frame> {
        val frames = mutableListOf<Frame>()
        var i = 0
        while (i + 4 < data.size) {
            if ((data[i].toInt() and 0xFF) == 0xDD) {
                val seq = data[i + 1].toInt() and 0xFF
                val len = data[i + 2].toInt() and 0xFF
                val end = i + 3 + len
                if (end + 1 < data.size && len >= 1) {
                    val body = data.copyOfRange(i + 3, end)
                    frames.add(Frame(seq, body[0].toInt() and 0xFF, body.copyOfRange(1, len)))
                    i = end + 2 // 跳过 csum + AA
                    continue
                }
            }
            i++
        }
        return frames
    }

    /** 电量信息（键 0x0C，payload = [左耳%, 右耳%, 充电仓%]） */
    data class BatteryInfo(val left: Int?, val right: Int?, val case: Int?)

    fun parseBattery(frames: List<Frame>): BatteryInfo? {
        val f = frames.firstOrNull { it.key == 0x0C } ?: return null
        if (f.payload.size < 3) return null
        return BatteryInfo(
            left = f.payload[0].toInt() and 0xFF,
            right = f.payload[1].toInt() and 0xFF,
            case = f.payload[2].toInt() and 0xFF,
        )
    }

    /** 当前 ANC 模式（帧 key=35 的 payload 首字节，0x00=普通，其余待解码） */
    fun parseAncMode(frames: List<Frame>): AncMode? {
        val f = frames.firstOrNull { it.key == 0x35 } ?: return null
        return AncMode.fromKey(f.payload.firstOrNull()?.toInt() ?: 0)
    }
}
