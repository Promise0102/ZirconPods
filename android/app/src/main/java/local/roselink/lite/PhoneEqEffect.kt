/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Portions adapted from Equalizer314 (bearinmindcat and contributors),
 * SessionEffectManager.createSessionDp/applySessionEq and DynamicsProcessingManager.
 * Upstream revision 90dd51a8f3e1333b7ffcfedba4e167ab3f250034.
 * Modified for ZirconPods: ten control points, headroom, bounded fallbacks,
 * normal priority, parameter readback, and no reverb/MBC/hidden APIs.
 * Full attribution and license: android/third_party/Equalizer314/.
 */
package local.roselink.lite

import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import kotlin.math.abs

internal class PhoneEqEffect private constructor(private val dp: DynamicsProcessing, val bands: Int) {
    fun controlled(): Boolean = dp.hasControl() && dp.enabled
    fun apply(gains: IntArray) {
        require(CustomEqProtocol.valid(gains))
        check(dp.hasControl()) { "其他音效应用持有控制权" }
        // Adapted initialization order: limiter -> EQ -> input gain -> enable.
        val limiter = DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -1f, 0f)
        for (channel in 0..1) {
            dp.setLimiterByChannelIndex(channel, limiter)
            val eq = DynamicsProcessing.Eq(true, true, bands)
            for (band in 0 until bands)
                eq.setBand(band, DynamicsProcessing.EqBand(true, PhoneEqCurve.cutoff(band, bands), PhoneEqCurve.gain(gains, band, bands)))
            dp.setPreEqByChannelIndex(channel, eq)
            dp.setInputGainbyChannel(channel, PhoneEqCurve.headroom(gains))
        }
        check(dp.setEnabled(true) == AudioEffect.SUCCESS && dp.enabled) { "系统未启用音效" }
        // Accepted parameters are evidence of configuration, not proof that every playback route uses it.
        for (channel in 0..1) {
            val actual = dp.getPreEqByChannelIndex(channel)
            check(actual.bandCount == bands && actual.isEnabled) { "系统返回的EQ结构不一致" }
            for (band in 0 until bands) {
                val returned = actual.getBand(band)
                check(abs(returned.gain - PhoneEqCurve.gain(gains, band, bands)) <= .15f) { "系统未接受第${band + 1}段增益" }
                check(abs(returned.cutoffFrequency - PhoneEqCurve.cutoff(band, bands)) <= maxOf(2f, returned.cutoffFrequency * .01f)) { "系统改变了频段映射" }
            }
        }
    }
    fun release() { runCatching { dp.release() } }
    companion object {
        fun create(session: Int, gains: IntArray): PhoneEqEffect {
            var failure: Exception? = null
            for (count in intArrayOf(64, 32, 10)) {
                var fx: DynamicsProcessing? = null
                try {
                    val config = DynamicsProcessing.Config.Builder(
                        DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                        2, true, count, false, 0, false, 0, true
                    ).setPreferredFrameDuration(40f).build()
                    fx = DynamicsProcessing(0, session, config)
                    val effect = PhoneEqEffect(fx, count)
                    effect.apply(gains)
                    return effect
                } catch (error: Exception) { failure = error; runCatching { fx?.release() } }
            }
            throw IllegalStateException("系统拒绝音效：${failure?.message ?: "未知错误"}", failure)
        }
    }
}
