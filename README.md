# ZirconPods

![Version](https://img.shields.io/badge/version-0.11.0--test-blue) ![License](https://img.shields.io/badge/license-GPL--3.0-blue)

为弱水时砂锆势（ROSE Zircon）耳机提供简洁的 Android 本地蓝牙控制，用于缓解官方应用连接异常带来的控制困难。最新公开测试版为 **0.11.0 风格预设与导入测试版**，稳定版保留 **0.8.1**。应用支持 Android 8.0 及以上，手机端 EQ 需要 Android 9 及以上。

[下载 0.11.0 测试版与 EQ 文件](https://github.com/Promise0102/ZirconPods/releases/tag/v0.11.0) · [0.8.1 稳定版](https://github.com/Promise0102/ZirconPods/releases/tag/v0.8.1) · [更新日志](CHANGELOG.md) · [Android 项目与构建](android/README.md)

## 0.11.0 风格文件与导入（公开测试）

内置9种有来源声明的人声、低音及音乐风格文件，并支持通过系统文件选择器导入GraphicEQ文本。先预览十段适配/缩放说明再载入，不自动改变音效，不覆盖旧本地预设。数据来源与格式边界见 [EQ文件与风格预设说明](android/EQ文件与风格预设说明.md)。

## 0.10.0 手机端 EQ（开发测试）

移植GPL-3.0项目Equalizer314的音效与会话路由部分，提供手机侧十段处理、播放器会话和全局兼容两种方式。手机EQ需要Android9+；独立前台服务保留音效，通知可停用，真实听感和播放器覆盖尚待实测。详见 [手机端 EQ 说明](android/手机端EQ说明.md)。这些改动统一包含在 v0.11.0 公开测试版中。

## 0.9.0 自定义 EQ（开发测试）

按用户提供的 i7 截图新增十段均衡器、本地预设保存与管理。锆势固件0.3.1的反馈尚未出现有效十段曲线，不能确认耳机内置自定义EQ支持；相关入口保留为可折叠的实验诊断。手机端EQ独立运行，离线保存不代表耳机生效。详见 [自定义 EQ 说明](android/自定义EQ说明.md)。这些改动统一包含在 v0.11.0 公开测试版中。

## 0.8.1 毛玻璃背景修复

移除自绘通知的白色卡片与不透明分组底色，按钮改为半透明，透出 ColorOS 原有通知毛玻璃。左侧应用图标和顶部应用名属于系统强制模板，普通 APK 无法删除；详见 [修复说明](android/毛玻璃通知修复说明.md)。

## 0.8.0 通知更新

折叠通知采用耳机图、电量及两个操作按钮；展开为三路电量、四模式和降噪等级。流体云去掉操作按钮，共用设备图和电量，成功连接后显示 10 秒。设计与限制见 [截图通知说明](android/截图通知说明.md)。0.8.0 未单独发布到 GitHub，其变化包含在 v0.8.1 中。

## 功能

- Miuix 主题与黑、银、紫三种产品展示配色。
- 左右耳、充电盒电量；降噪、风噪、通透、普通四种噪声模式。
- HiFi/流行/摇滚音效，游戏模式、双设备连接与音频编码设置。
- 手机端十段自定义EQ、本地预设管理、9种风格文件和GraphicEQ文本导入（公开测试）。
- 单独的触控设置页：左右耳单击、双击、三击、长按。
- 实验室：入耳检测、提示音等级、极限降噪、降噪触控循环。
- 通知栏四模式按钮、系统快捷设置“耳机模式”循环磁贴。
- 记住上次成功连接的耳机，打开应用或蓝牙连接恢复时尝试自动建立控制连接。
- Android 16 实时更新连接提示兼容测试，设置中可查看实际系统支持情况。

## 安装与使用

1. 从 [v0.11.0 测试版 Release](https://github.com/Promise0102/ZirconPods/releases/tag/v0.11.0) 下载签名 APK，使用相同包名与原发布签名，可覆盖更新此前版本；需要旧稳定版可选择 [v0.8.1](https://github.com/Promise0102/ZirconPods/releases/tag/v0.8.1)。
2. 手机系统蓝牙先配对耳机；打开应用，允许附近设备和通知权限。
3. 点击左上角设备面板，扫描并手动连接成功一次；此后可使用自动连接。
4. 在设置中选择“添加耳机模式磁贴”，或下拉控制中心、点击铅笔后添加“耳机模式”。
5. 磁贴按“降噪 → 风噪 → 通透 → 普通”循环。手动断开会暂停自动连接。
6. 体验手机EQ：进入“自定义 EQ”选择风格文件库或导入GraphicEQ，点击“应用曲线并启用手机 EQ”，再重新打开播放器播放。文件包仅用于额外导入或分享，9种风格已经内置。

所有模式、开关和电量以耳机真实回包为准；未读取时不会假定已开启。离线点击磁贴先尝试连接，不会排队发送旧模式。

## 构建与测试

完整工程位于 [`android/`](android/)。依赖固定为 Gradle 8.9、AGP 8.5.2、Kotlin 2.0.21、Compose 1.7.0、Miuix 0.2.9，compileSdk/targetSdk 34，minSdk 26。

```powershell
cd android
.\test.ps1 -JavaHome $env:JAVA_HOME
.\build.ps1 -Online -JavaHome $env:JAVA_HOME
```

Windows 构建脚本在 ASCII 临时目录编译，并验证 APK 签名和对齐；首次构建会生成自己的本地签名。原发布私钥不入库，自建签名 APK 无法覆盖原发布签名安装包。其他平台可使用 Gradle wrapper 构建 debug 版本。

协议、状态与文件模型已通过 **331 项主机检查**：此前288项协议、状态与手机EQ检查，以及新增43项GraphicEQ文件与风格库检查。0.11.0 APK已完成release编译、签名、对齐及内置风格/许可资产核验。系统文件选择器、原生音效、播放器覆盖、实际听感及完整固件兼容性仍待实机确认。

## 协议与文档

控制通道为经典蓝牙 RFCOMM，服务 UUID 为 `0cf12d31-fac3-4553-bd80-d6832e7b3931`。当前帧封装、校验与命令映射见 [`ZirconProtocol.java`](android/app/src/main/java/local/roselink/lite/ZirconProtocol.java) 及测试固定向量。

- [逆向方法与静态证据](android/逆向与验证说明.md)
- [实验室协议](android/实验室协议说明.md)
- [通知栏控制](android/通知栏控制说明.md)
- [自动连接与系统磁贴](android/自动连接与快捷磁贴说明.md)
- [ColorOS 实时更新兼容测试](android/ColorOS流体云说明.md)
- [UI 与产品图片来源](android/UI设计说明.md)

仓库早期规划与研究资料保留作为历史记录；其中的推测和旧版实现说明不等同于当前版本行为，请以当前 Android 工程与上述文档为准。

## 已知限制

ColorOS 后台管理和 Android 前台服务启动限制可能影响自动唤醒。被用户强行停止的应用需重新打开；蓝牙音频配对、音频路由仍由系统管理。原生流体云/胶囊是否显示由实际 Android API、通知授权和系统策略决定。具体耳机固件兼容性尚待实机确认。

本应用无网络权限，不包含录音、AI、翻译、社区和在线固件更新。未分发官方 RoseLink/Padlink 安装包、原始反编译输出或分析工具。产品图片由用户截图经生成式背景提取得到，相关说明见 UI 文档；商标与产品外观归相应权利人。

## 致谢与许可证

界面使用 [Miuix](https://github.com/compose-miuix-ui/miuix)。手机EQ部分改编自 [Equalizer314](https://github.com/bearinmindcat/Equalizer314)（GPL-3.0）；风格文件另使用 [VLC](https://github.com/videolan/vlc/blob/master/modules/audio_filter/equalizer_presets.h) 的预设数组（LGPL-2.1-or-later）。固定来源版本、修改说明及许可保留于 [third_party/](android/third_party/) 和应用内开源许可。历史研究参考了 HyperPods 和 OppoPods，当前实现与验证范围见上述文档。

项目原创代码按 [GPL-3.0](LICENSE) 许可，Copyright (C) 2026 Promise0102。第三方依赖保持各自许可；产品图片、商标与产品外观的权利不因本项目代码许可而转移。
