# VLC EQ style data

- Source: https://github.com/videolan/vlc
- Pinned commit: `6050eca3f959eb84316083d7fb2671b7e05cc8b5`
- File: `modules/audio_filter/equalizer_presets.h`, original preserved beside this document.
- SHA-256: `4119034a6d8b235f632e3408a22385875f644600404c67f6c208a9e480747ab9`.
- Copyright (C) 2004 VLC authors and VideoLAN; author Laurent Aimar.
- File license: LGPL-2.1-or-later, as declared in the source header. Upstream `COPYING.LIB` is preserved here and packaged in the APK at `assets/licenses/VLC-presets-LGPL-2.1.txt`.

Selected style arrays: flat, pop, rock, classical, dance, fullbasstreble, fulltreble. The GraphicEQ files in `app/src/main/assets/eq_styles` pair the original amplitudes with the explicitly selected legacy VLC frequency table: 60,170,310,600,1000,3000,6000,12000,14000,16000Hz.

These are adapted graphical style references, not VLC's original filter transfer functions and not measured ROSE Zircon corrections. Original preamp values are omitted. The importer maps the frequency/gain points in log frequency onto ZirconPods' ten control points, proportionally scales values exceeding ±6dB, then rounds to 1dB. The phone engine supplies its own negative headroom and limiter. Each file retains origin, license and adaptation comments.
