# Equalizer314 portions used by ZirconPods

- Source: https://github.com/bearinmindcat/Equalizer314
- Revision: `90dd51a8f3e1333b7ffcfedba4e167ab3f250034`
- Authors: bearinmindcat and Equalizer314 contributors.
- License: GPL-3.0, full upstream license preserved in `LICENSE`, also shipped in the APK at `assets/licenses/Equalizer314-GPL-3.0.txt` and readable from Settings → 开源许可.

Ported and adapted portions:

- `app/src/main/java/com/bearinmind/equalizer314/audio/SessionEffectManager.kt`: `createSessionDp` construction/stage setup and `applySessionEq` per-channel EQ object construction. Source SHA-256: `953838fdc9da9d50df65c9c5e38fd8d7199f2f63294c2d64574027452e189fe8`.
- `app/src/main/java/com/bearinmind/equalizer314/audio/DynamicsProcessingManager.kt`: stage configuration before enabling, effect ownership and release pattern. SHA-256: `3da004e44ae10a96fa644e90f2189ad7aa310a6a87822142a5bb53544b48075f`.
- `app/src/main/java/com/bearinmind/equalizer314/audio/AudioSessionReceiver.kt`: standard OPEN/CLOSE action selection and audio-session/package extra routing. SHA-256: `2ca5bc94048709845607c1d313d573be176411a5b3b23b3f5ac1ab1934480e78`.

Adaptations in `PhoneEqEffect.kt` and `PhoneEqService.kt`: independent user-started service, dynamic receiver while service exists, bounded session count, explicit global/session selection, 64/32/10-band fallback, ten graphical control points interpolated on log-frequency upper-cutoff bands, normal priority, automatic negative headroom, limiter, parameter readback, output-device presence gating, no forced control reclaim, no hidden API/reverb/MBC/AutoEQ assets. The original app's parametric UI and full signal-processing features are not bundled.

The surrounding ZirconPods implementation and adaptations are provided under this repository's GPL-3.0 license. Standard Android API calls are documented at https://developer.android.com/reference/android/media/audiofx/DynamicsProcessing and https://developer.android.com/reference/android/media/audiofx/AudioEffect.

Research also considered OpenEQ (GPL-3.0) and RootlessJamesDSP (GPL-3.0). No code from those projects is bundled. A repository without an explicit license was excluded from code reuse.

## 0.11.0 style formula adaptation

`app/src/main/java/com/bearinmind/equalizer314/dsp/ParametricEqualizer.kt`, same pinned revision, SHA-256 `08a7c616c2fa327a5da8f4612b106c0386c9239598e492d92aa4d0ea1cee10a5`: the `loadPreset` Bass Boost and Vocal Enhance gain-shape formulas are adapted to ten graphical control points in `eq_styles/01-vocal.txt` and `02-bass.txt`. The original implementation can use variable filter counts/types/frequencies; these files intentionally represent only the style formulas. They are not an exact reproduction of the upstream parametric filter response. Range scaling and rounding are explicitly displayed before application.
