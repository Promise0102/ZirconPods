# 弱水时砂「锆势」第三方驱动软件 —— 开发规划

> 状态：规划稿 v1.0（2026-02）
> 参考项目：[HyperPods](https://github.com/Art-Chen/HyperPods)（Art_Chen，GPL-3.0）、[OppoPods](https://github.com/Leaf-lsgtky/OppoPods)（Leaf-lsgtky，GPL-3.0）

---

## 一、调研结论（事实基础）

| 项目 | 结论 | 依据 |
|---|---|---|
| 产品定位 | 锆势 = 弱水时砂旗舰 TWS，支持 LHDC-V5 / LC3 / 双设备 | 我爱音频网新品报道 |
| 主控芯片 | **推测物奇 WUQI WQ7034 系列**（RISC-V + HiFi4 DSP + 24bit DAC，双模 BT/BLE 5.4）；同厂旗舰 CERAMICS Ultra 拆解确认用 WQ7034AX | 我爱音频网（推断，未实锤） |
| 官方 App | **RoseLink 3.0**，包名 `cn.ikaile.ruoshui.client`（应用宝在架），支持 Android / HarmonyOS / iOS / **Windows / MacOS**；弱水时砂 TWS 全系共用 | 官网下载页、应用宝 |
| 通信通道 | **推测 BLE GATT**（物奇平台 + 桌面客户端存在）；未证实，逆向第一步必须确认 | 行业惯例推断 |
| 现成逆向资料 | **无**。GitHub / 中文社区均无弱水时砂 / 锆势协议逆向 | 多方检索 |

### 从参考项目学到什么（架构层面）
- **OppoPods**：
  - `AppRfcommController` — **独立模式控制器**：不依赖 Xposed 运行时，App 进程内直接建连耳机、状态机（DISCONNECTED/CONNECTING/CONNECTED/ERROR）、`StateFlow` 暴露状态、周期轮询电量 → 这正是"独立 App"该有的形态，锆势照搬此模式，把 RFCOMM 换成 BLE GATT。
  - `DeviceProfile` / `ProfileKeys` — **声明式设备配置档**：机型相关指令用 `Map<String, PodCommand>` 描述，加功能只加键不改结构 → 锆势的协议层用同样的 JSON 命令表。
  - `docs/` 逆向笔记 + `DebugPage` 协议调试页 → 逆向成果必须文档化、可复现。
- **HyperPods**：AAP over BLE 的实现（BLE 连接管理、特征读写、状态解析）→ BLE 侧工程模式可借鉴。

---

## 二、目标与形态（已确认）

- **形态**：Android App + Windows/Mac 桌面客户端，两套 UI 共享同一协议层定义。
- **第一版功能（MVP）**：ANC 切换（降噪 / 通透 / 关闭）+ 连接状态、左右耳/盒子电量显示（ANC 状态回读、电量上报是切换功能能闭环验证的最小配套）。
- **后续扩展**（MVP 后按需）：EQ 预设/自定义、手势自定义、入耳检测开关、双设备连接管理。
- **明确不做**：固件升级（协议加密 + 风险高）、官方 App 本体重分发（合规）。

---

## 三、总体技术路线

```
协议逆向（Phase 1）──► 协议文档 + JSON 命令表（唯一事实源）
        │
        ├──► Python 验证原型（Phase 2）← 协议正确性验证，最快闭环
        ├──► Android App（Phase 3）   Kotlin + Jetpack Compose + BLE GATT
        └──► 桌面客户端（Phase 4）    Python + bleak（CLI → 简单 GUI）
```

- **技术栈推荐**（你选择"听推荐"）：
  - 逆向工具链：`jadx`（静态反编译）、Android 开发者选项 btsnoop（动态抓包，无需 root）、`nRF Connect`（手工 GATT 交互）、必要时 `frida`（hook 定位）。
  - 协议验证/桌面：**Python 3 + bleak**（BLE 生态最成熟，一套代码从验证原型演进出桌面客户端）。
  - Android：**Kotlin + Jetpack Compose**（与参考项目一致，资料多）。
  - 不推荐 Flutter 做桌面端：桌面 BLE 支持弱（`flutter_blue_plus` 仅移动端，桌面需平台通道桥接 bleak，收益低）。
- **为什么协议层不跨端共享同一份代码**：BLE 在 JVM 桌面（Windows）无好用的原生库，KMP/共享库收益小于成本。改为"**协议文档 + JSON 命令表 = 唯一事实源**"，两端各自实现薄薄一层收发，由 Phase 2 的 Python 参考实现保证两端行为一致。

---

## 四、分阶段计划

### Phase 0 — 环境准备（0.5 天）
- Android 开发机：Android Studio、SDK、adb；真机开启「开发者选项」。
- Python 环境：`pip install bleak`；抓包后装 Wireshark。
- 从应用宝（`https://sj.qq.com/appdetail/cn.ikaile.ruoshui.client`）或官网下载页获取 RoseLink APK。

### Phase 1 — 协议逆向（核心，约 60% 工作量）★
**里程碑 M1：协议草案（1–2 周）**

1. **确认通道**（第一步！）：
   - 装 RoseLink → 连锆势 → 手机开发者选项开启「蓝牙 HCI 信息收集日志」（btsnoop），操作 App 后导出日志；
   - 用 Wireshark 打开 btsnoop：若看到 **GATT ATT 读写** → BLE 路线；若看到 **RFCOMM/SPP** → 经典蓝牙路线（概率低）。
2. **静态分析**（`jadx-gui` 打开 APK）：
   - 全局搜 UUID 字符串（GATT Service/Characteristic，注意 128 位自定义 UUID 与 0xFFxx 标准自定义段）；
   - 定位 `BluetoothGatt` / `BluetoothLeScanner` 相关类与连接流程；
   - 找命令构造逻辑（自定义二进制 / protobuf / JSON），搜索 `anc`、`eq`、`wq`、`wuqi` 等关键字，判断是否带 CRC/校验。
3. **动态抓包建立交互矩阵**：
   - 逐项操作 RoseLink：ANC 关→降噪→通透、刷新电量、切 EQ 预设；
   - 记录每次操作的 GATT `Write` / `Notify` 字节流，建立「操作 ↔ 字节」映射表。
4. **手工验证**：用 `nRF Connect` 直接对特征写命令，确认耳机 ANC 实际切换 → 命令表初步可信。
5. **沉淀文档**：
   - `docs/protocol.md`：service/characteristic UUID、帧格式、命令表、notify 状态解析（参考 OppoPods `docs/HeyMelody_Bluetooth_Protocol_Notes.md` 的写法）；
   - `protocol/zircon-commands.json`：结构化命令表（OppoPods `DeviceProfile.commands` 的 JSON 化）。

### Phase 2 — Python 验证原型（1 周）
**里程碑 M2：脚本可切换 ANC**
- `tools/anc_probe.py`：bleak 扫描→连接→枚举 GATT→发送 ANC 命令→打印 notify/电量。
- 目标：不依赖官方 App 独立复现 ANC 三态切换 + 读到电量，验证协议闭环；此脚本同时是桌面客户端的前身。

### Phase 3 — Android App（2–3 周）
**里程碑 M3：Android v0.1**
- 结构（镜像 OppoPods 但去掉 hook 层，独立 App）：
  - `protocol/`：`BleGattController`（连接状态机、特征读写、notify 解析、电量轮询，参照 `AppRfcommController` 的 `StateFlow` 设计）、`ZirconProfile`（加载 JSON 命令表，参照 `DeviceProfile`）；
  - `ui/`（Compose）：连接卡片、ANC 三态切换（SegmentedButton）、电量展示、协议调试页（hex 收发日志，参照 OppoPods `DebugPage`）；
  - 权限：`BLUETOOTH_CONNECT` / `BLUETOOTH_SCAN`（Android 12+ 运行时申请）。
- 不依赖 Xposed/root；系统级集成（如 HyperOS 焦点岛）列为可选后续，另起 hook 模块。

### Phase 4 — 桌面客户端（1–2 周）
**里程碑 M4：桌面 v0.1**
- `desktop/`：Python + bleak，先 CLI（`zircon-cli anc nc|trans|off`），再包一个极简 GUI（tkinter/PySide6），功能与 Android 对齐（ANC + 电量）。
- 打包：PyInstaller 出 Windows exe；macOS 用 py2app/Homebrew（若需要）。

### Phase 5 — 联调、文档与发布（1 周）
**里程碑 M5：发布**
- 真机功能验证矩阵（Android 实机 + 桌面蓝牙适配器），逐项打勾；
- README（中英）：功能、构建、逆向过程说明、致谢 HyperPods/OppoPods；
- GitHub 仓库 + Actions：Android `assembleDebug` 出 APK、桌面端 PyInstaller 出包；
- 许可证：**GPL-3.0**（呼应生态；若未来直接借 OppoPods 代码也合规）。

---

## 五、仓库结构规划

```
ZirconPods/
├── README.md
├── LICENSE                     # GPL-3.0
├── docs/
│   ├── protocol.md             # 协议逆向笔记（UUID/帧格式/命令表/notify 解析）
│   └── reverse-engineering.md  # 逆向过程记录（抓包步骤、交互矩阵）
├── protocol/
│   └── zircon-commands.json    # 唯一事实源：结构化命令表
├── tools/                      # Python 逆向辅助脚本
│   ├── anc_probe.py            # 验证原型（也是桌面端前身）
│   └── btsnoop_parse.py        # btsnoop 日志分析
├── android/                    # Kotlin + Compose + BLE GATT
│   └── app/src/main/java/...
└── desktop/                    # Python + bleak
    ├── zircon_cli.py
    └── gui/...
```

---

## 六、风险与对策

| 风险 | 概率 | 对策 |
|---|---|---|
| 通信通道不是 BLE（而是 RFCOMM 等） | 低 | Phase 1.1 第一步就确认；两条路线的工具链已分别列出，切换成本 ~1 天 |
| 命令带 CRC/密钥/加盐 | 中 | 静态分析优先找校验实现；物奇公版多为简单 CRC，可破；frida hook 加密函数兜底 |
| RoseLink 更新导致协议变化 | 中 | 命令表版本化（`zircon-commands.json` 带版本号），抓包固件版本记录 |
| EQ/手势等高级功能协议复杂 | 中 | MVP 只做 ANC + 电量，其余作为独立逆向任务排队 |
| 桌面端 BLE 适配器兼容性 | 中 | Windows 用内置蓝牙（WinRT 驱动层由 bleak 封装），实测常见 USB 适配器；失败则桌面端降级为"Android 抓包辅助工具" |

---

## 七、合规说明
- 仅逆向协议用于个人设备互操作（个人研究用途），不重分发 RoseLink APK 本体，不绕过官方 App 的付费/鉴权机制。
- 所有逆向结论以文档/代码形式开源，与参考项目保持 GPL-3.0 兼容。

---

## 八、下一步（等你确认后开工）
1. 确认 Phase 1 启动：拿到 RoseLink APK + 真机开启 btsnoop 抓一份"连接 + 切 ANC"的日志；
2. 我先把仓库骨架（`docs/`、`protocol/`、`tools/`、`android/`、`desktop/`）和 `tools/anc_probe.py` 搭起来；
3. 按 Phase 1 → 5 顺序推进，每阶段一个里程碑。
