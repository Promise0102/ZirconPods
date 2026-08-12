# ZirconPods — 弱水时砂「锆势」第三方驱动

为弱水时砂（ROSESELSA）**锆势（ROSE Zircon）** TWS 耳机开发的第三方控制软件。
协议通过逆向 RoseLink（Android/Windows）并真机验证破解，无需官方 App。

> ⚠️ 逆向研究用途。不包含官方 App 的任何代码/资源，协议结论以文档形式开源。

## 功能

- **噪声控制**：降噪（含 5 档深度）/ 风噪（抗风降噪）/ 普通 / 通透
- **电量显示**：左右耳 + 充电仓
- **游戏模式**：低延迟开关
- 协议调试能力（状态查询）

## 协议摘要（详见 [docs/reverse-engineering.md](../docs/reverse-engineering.md)）

```
连接：经典蓝牙 RFCOMM，私有控制服务 0cf12d31-fac3-4553-bd80-d6832e7b3931（SDP 解析 SCN 6）
      ★ 必须用私有 UUID 服务发现连接；SPP(0x1101) 或直连通道不被耳机识别为控制会话
发送：裸命令帧    FF 00 03 09 [key] [val]
接收：封装帧      DD [seq] [len] [key] [payload...] [csum] AA
                 csum = (0xDD + seq + len + sum(body)) & 0xFF

噪声控制：
  FF 00 03 09 01 <1~5>   # 降噪（深度档）
  FF 00 03 09 02 00      # 普通
  FF 00 03 09 03 00      # 通透（环境音）
  FF 00 03 09 04 00      # 风噪（抗风降噪）
游戏模式：
  FF 00 03 0E 01 04      # 开（关闭命令待补测）
状态查询：
  FF 00 03 01 06         # 键值流：0C=电量
```

## 构建（Android）

环境要求：Android Studio（2026 新版，AGP 9.1 / JDK 22 / compileSdk 37）

```bash
cd android
# 方式一：Android Studio 直接打开 android/ 目录，Sync 后 Run
# 方式二：命令行
gradle wrapper
./gradlew assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

### 使用

1. 手机系统蓝牙先配对锆势；
2. 打开 App，授予蓝牙权限；
3. 在「连接」卡片点耳机条目连接；
4. 切换噪声控制/深度/游戏模式；
5. ⚠️ 耳机同一时间只允许一个控制会话：若官方 RoseLink 正连着，先断开它。

## 已知限制

- 耳机只允许一个 RFCOMM 控制会话（需先断开 RoseLink）
- 降噪深度 1~5 均确认进入降噪，各档深度差异待细分验证
- 游戏模式**关闭**命令尚未破解（`00/01` 值无效），当前开关为实验性
- 电量键值解析基于状态查询（0x0C），未完全验证

## 致谢

- [HyperPods](https://github.com/Art-Chen/HyperPods) (Art_Chen, GPL-3.0)
- [OppoPods](https://github.com/Leaf-lsgtky/OppoPods) (Leaf-lsgtky, GPL-3.0) — RFCOMM 架构、Miuix UI 参考
- [Miuix](https://github.com/YuKongA/miuix) (YuKongA) — HyperOS 风格 Compose UI

## 许可证

GPL-3.0
