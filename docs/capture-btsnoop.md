# 抓取锆势蓝牙日志（btsnoop）操作指南

目的：捕获 RoseLink 与锆势耳机之间的 GATT/RFCOMM 通信字节流，用于协议逆向。
所需：Android 手机（有锆势配对）+ 电脑（可选，用于导出日志）+ RoseLink App。

---

## 方案 A：Android 开发者选项 btsnoop（首选，无需 root）

### 步骤
1. **开启开发者选项**（若未开）：设置 → 关于手机 → 连点「版本号」7 次。
2. **开启蓝牙 HCI 日志**：设置 → 开发者选项 → 找到 **「蓝牙 HCI 信息收集日志」(Bluetooth HCI snoop log)**，打开。
   - ⚠️ 部分 ROM 开启后需要 **重启蓝牙**（关蓝牙再开）或重启手机才生效；保险起见开完开关后重启一次手机。
3. **准备复现**：
   - 手机与锆势**已完成配对**；
   - 打开 RoseLink，停在首页（能显示耳机电量的页面）。
4. **复现操作**（每个动作之间间隔 3~5 秒，方便后面按时间切分）：
   - 连接耳机 → 等电量加载出来；
   - 切 ANC：降噪 → 通透 → 关闭，各停 5 秒；
   - 刷新一次电量（若有该按钮）；
   - （可选）切一次 EQ 预设。
5. **关闭日志开关**（Android 会自动把日志落盘；不关的话日志会一直增长覆盖）。
6. **导出日志**，见下方「日志位置与导出」。

### 日志位置与导出（按 ROM 分）
| 系统 | 日志位置 / 导出方式 |
|---|---|
| **通用（原生/多数 ROM）** | `adb bugreport` 打包 → 在 zip 里搜 `btsnoop_hci.log`（可能位于 `FS/data/misc/bluetooth/logs/` 或 `FS/data/log/` 下） |
| **MIUI / HyperOS（小米）** | 开发者选项开启后，文件一般在 `/sdcard/MIUI/debug_log/` 下（如 `bluetooth_bt_snoop.log`），`adb pull` 或文件管理器直接拷出 |
| **ColorOS / realme UI** | 开发者选项 → 「蓝牙 HCI 日志」，文件在 `/data/misc/bluetooth/logs/`（需 root）；无 root 时用 adb bugreport 碰运气 |
| **EMUI / HarmonyOS** | 开发者选项 → 「蓝牙 HCI 日志」；无 root 用 adb bugreport |
| **有 root** | 直接 `adb pull /data/misc/bluetooth/logs/btsnoop_hci.log`（几乎所有 ROM 通用） |

> 通用兜底：`adb bugreport` 生成的 zip 包含系统快照，里面常能挖出 btsnoop；解压后文件名可能是 `btsnoop_hci.log` 或 `btsnoop.cfa`。

### 用 Wireshark 看
1. 打开 Wireshark → 打开 btsnoop 文件；
2. 过滤 `btatt`（BLE GATT）看 Read/Write/Notify；
3. 或过滤 `btrfcomm`（若耳机走经典蓝牙 RFCOMM）；
4. 在 RoseLink 上切 ANC 的时间点附近，找到对应的 Write/Notify 字节，记下来。

---

## 方案 B：Windows 桌面端抓包（无 root Android 时的替代路径）

官方 RoseLink 有 Windows 客户端——可以在 Windows 上抓**桌面客户端 ↔ 耳机**的蓝牙流量，完全不需要 root：

1. 下载微软官方工具 **Bluetooth Virtual Sniffer (BTVS)**（配合 Wireshark 插件，Win10/11 内置蓝牙即可）；
2. 在 Windows 上配对锆势并安装 RoseLink Windows 版；
3. 启动 BTVS 抓 HCI 流量 → 操作 RoseLink 切 ANC；
4. Wireshark 打开 BTVS 输出的日志，同样过滤 `btatt` 分析。

> 限制：BTVS 抓的是本机蓝牙适配器流量；若桌面版与手机版协议不同，结论以 Android 端为准，桌面端用于交叉验证。

---

## 方案 C：Frida hook（有 root / 可调试手机时）

更精准地定位 App 内部命令构造：
```bash
frida -U -f cn.ikaile.ruoshui.client -l gatt_hook.js
```
hook 点：
- `android.bluetooth.BluetoothGatt.writeCharacteristic(...)`
- `android.bluetooth.BluetoothGattCallback.onCharacteristicChanged(...)`
- `onCharacteristicWrite(...)` / `onDescriptorWrite(...)`
把参数与返回值（含字节 hex）打印出来，能直接得到「功能 → 字节」映射，无需猜 Wireshark。

---

## 方案 D：硬件抓包（兜底，成本高）
- **Ubertooth One** + Wireshark：监听 BLE 空中流量，可抓其他设备间的通信（需要耳机重新连接产生广播/连接包；蓝牙跳频使监听有挑战）。
- 仅当 A/B/C 全不可行时考虑。

---

## 常见坑
- 开启 snoop 开关后**必须重启蓝牙或手机**，否则日志可能不产生；
- 抓包期间**保持 RoseLink 在前台**，避免后台被系统冻结导致无流量；
- 每次只做一个动作、动作间停顿，便于按时间戳切分；
- 抓完立刻关开关，防止日志被新流量覆盖；
- 若日志文件巨大，先用 Wireshark 的 `time` 过滤定位操作时段再细看。

## 交付物
把导出的 btsnoop（或关键片段的 hex 转储）放到 `tools/` 下（体积大则放网盘并在 `docs/reverse-engineering.md` 记录链接），我会基于它完成通道确认 + 命令表提取。
