> 历史研究记录：本文描述早期原型及当时的验证结论；当前 0.7.0 实现与验证范围以根目录 README 和 android/ 下文档为准。

# 逆向过程记录（APK 静态分析）

> 时间：2026-02 · 对象：`base.apk`（RoseLink，包名 `cn.ikaile.ruoshui.client`）
> 结论先行：RoseLink 是 **Flutter 应用**，BLE 协议逻辑在 `libapp.so`（Dart AOT）中；锆势属 **rs_common_v2 协议族**。

## 1. APK 结构

```
base.apk (65MB)
├── assets/
│   ├── ble_analyze.js        ← 误导性命名！实际是网易云音乐 openapi RSA_SHA256 签名脚本（与蓝牙无关）
│   ├── umi.js                ← 内嵌 Web 前端（umi 框架）
│   └── flutter_assets/       ← Flutter 资源
├── classes.dex (5.8MB)       ← 仅 Flutter 引擎壳 + 插件
└── lib/arm64-v8a/
    ├── libapp.so (14.7MB)    ← ★ Dart AOT 业务代码（协议逻辑所在）
    ├── libflutter.so
    ├── libjl_ota_auth.so     ← 杰理(Jieli) OTA 认证库
    ├── libEncryptorP.so      ← 加密库
    └── libonnxruntime.so     ← AI（录音转写等）
```

## 2. 协议库架构（从 libapp.so 字符串池还原）

**`package:roselink_bluetooth/`（独立 BLE/蓝牙协议库）**

```
commander/                    ← 命令层
  sender/  r_sender / f8_sender / rs_common_sender / rs_common_v2_sender / zt_sender / command_util
  receiver/ r_receiver / f8_receiver / rs_common_receiver / rs_common_v2_receiver / zt_receiver
  r_function.dart / r_commander.dart
connector/                    ← 连接层
  ble_connector / spp_client(SPP!) / usb_hid_connector / a2dp_manager / bt_connector / connect_manager
scanner/  r_ble_scanner / r_usb_hid_scanner
```

**`package:roselink/rose_bluetooth/config/`（各型号设备配置）**

```
f8/           agate, agate_x, ceramics, ceramics_x, ceramics_x_7034(物奇WQ7034), explorer
rs_common/    budsfree, cambrian, openfree, openfun, sportfree, swift
rs_common_v2/ ★ zircon(锆势!), ceramics_u, ceramics_mk2(8912f=蓝讯), astro_x, auramic, ...
zt/           earfree_i3, earfree_i5, headfree
```

> 判断：锆势（zircon）→ `rs_common_v2` 协议族 → 发送/解析在
> `rs_common_v2_sender.dart` / `rs_common_v2_receiver.dart` / `zircon_device_config.dart`

## 3. GATT UUID（libapp.so 字符串池）

| UUID | 含义 |
|---|---|
| `00007034-0000-1000-8000-00805f9b34fb` | **物奇 WQ7034 服务**（0x7034），关联 `CeramicsX7034DeviceConfig` |
| `0000AE00/0000AE01/0000AE02-...` | 自定义特征（EQ 相关，邻近字符串含 "Step 0 is -12dB..."） |
| `0cf12d31-fac3-4553-bd80-d6832e7b3931` | 自定义特征（设备切换/贴合测试相关） |
| `0cf12d32-fac3-4553-bd80-d6832e7b3931` | 自定义特征 |
| `7d8bee7c-b17c-49bc-aa48-9a7b76098f20` | 自定义特征（邻近 setHeadMoveModeOff 头部跟踪） |
| `00001101-...` | **SPP（经典蓝牙 RFCOMM）** — App 支持 SPP 连接 |
| `00002902-...` | CCCD（BLE notify） |

> 注：0x7034 服务绑定 f8 族的 CeramicsX；锆势属 rs_common_v2 族，**其实际 BLE 服务 UUID 需动态确认**。

## 4. 命令样本（hex 字符串池，疑似协议测试向量/映射表）

```
FF 00 03 01 01 / 02 / 03 / 04            ← noise_mode：降噪/风噪/普通/通透（推测）
FF 00 03 01 11 / 12 / 13 / 14            ← noise_mode 深度/等级档（推测）
08 EE 00 00 00 04 81 0D 00 [L/R] [mode]  ← 状态上报帧头（0x81 0x0D 与欢律/OppoPods 0x810D 同源）
08 EE 00 00 00 06 84 0b 00 / 85 0E 00 ... ← 其他状态上报（电量等）
00 22 01 00 03 01..08 01                 ← EQ 预设 8 档（推测）
00 2a 01 00 01 / 00 30..32 01 00 01      ← 单条设置命令
525300010001 / 525300020002              ← "RS" 连接握手（协议版本协商）
70076ec0XXXXXXXXXX33                     ← OTA/鉴权签名命令（含头 70 07 6e c0 尾 33）
```

## 5. 动态验证结论（2026-02，logcat + nRF Connect）

### ★ 控制通道 = 经典蓝牙 RFCOMM（SCN 6），不是 BLE！

logcat 实锤（RoseLink 启动连接锆势 48:78 时）：
```
BTA_JvStartDiscovery: bd_addr:xx:48:78, rfcomm_slot_id:14
BTA_JvRfcommConnect: remote_scn:6, peer_bd_addr:xx:48:78   ← RFCOMM 通道 6
RFCOMM ... dlci:13 scn:6, RFC_PORT_EVENT_DATA              ← 数据通道建立
BTA_JvRfcommWrite: write ok                                ← App 持续收发
RFCOMM_BufDataInd: buf_len=7 (高频) / 9 / 10 / 26          ← 小包心跳/轮询
```

- nRF Connect 连到的 `ZZZ`（蓝牙地址已脱敏）有 LE Audio 标准服务（0x184C/0x1849/0x1855）+ 0xFCC0，但**无** 0x7034/0cf12d31 → **不是锆势**（是用户另一台设备）；锆势的 BLE 无私有控制服务。
- 技术路线与 OppoPods 相同：RFCOMM 私有协议帧（hex 样本见 §4）。

## 6. 下一步

- [ ] Blutter 反编译 libapp.so，精确定位 `rs_common_v2_sender` / `zircon_device_config` 的帧组包实现（静态拿命令码）
- [ ] 动态抓 RFCOMM 数据流（备选）：Windows 蓝牙适配器 + 微软 BTVS + Wireshark，抓 RoseLink Windows 版与锆势的帧（无 root 验证方案）
- [ ] 确认连接握手序列（525300010001 "RS" 包是否必须先发）
- [ ] 确认 ANC 命令帧与模式映射（FF 00 03 01 XX：01=降噪/02=风噪/03=普通/04=通透?）

## 7. 黑盒协议破解进展（PC AF_BTH 直连实验，2026-02）

### 连接参数（已实锤）
- 通道：经典蓝牙 **RFCOMM SCN 6**，设备蓝牙地址已脱敏（PC 侧 AF_BTH socket 直连成功过多次）
- **连接模型**：耳机**只允许一个 RFCOMM 控制会话**；控制权由最近连接者持有；空闲后耳机休眠（需佩戴/播放唤醒）

### 帧格式（已破解）
```
接收（耳机→中心）：DD [seq:1B] [len:1B] [data...] [csum:1B] AA
   csum = (0xDD + seq + len + sum(data)) & 0xFF   ← 4 样本验证 100% 吻合
发送（中心→耳机）：裸帧，如 FF 00 03 01 06
```

### 已确认命令/响应
| 命令 | 功能 | 响应 |
|---|---|---|
| `FF 00 03 01 06` / `FF 00 03 01 1B` | 状态查询 | 完整键值流（数十帧） |
| `FF 00 03 01 XX`(其他) | — | 仅 ACK `DD 00 02 FE 00 DD AA` |
| 任意帧 | — | ACK `DD 00 02 FE 00 DD AA` |

### 键值流解码（部分）
```
0C: [左耳%][右耳%][盒%]      ← 电量（5d 3c 39 = 93% 60% 57%）
35: [模式]                   ← 当前状态模式（00=普通/关闭? 01=降噪? 待确认）
2D/2E/2F: 03/02/04           ← 疑似模式值（普通/风噪/通透编码）
01: 01, 07:00, 08:01, 09:02  ← 其他状态（待解码）
12:01, 2A..2F, 31..33, 36:01010100 ← 待解码
```
> 与 OppoPods 逆向的欢律协议（0x810D 键值流）同源，可参考其键语义。

### 待办（耳机唤醒后）
1. 独占轮换差分：手机 RoseLink 切 ANC → 释放 → PC 查询 → 对比键 35/2D/2E/2F 变化
2. 由键值变化反推 **ANC 设置命令**（黑盒穷举 `FF 00 03 02/03/04/05 XX` 未命中）
3. 完整键表解码

## 8. ★ ANC 控制命令已破解（真人听感验证，2026-02）

### ANC 模式命令（核心成果）
```
FF 00 03 09 [key] [val]
├─ key=01 → 降噪模式（val=降噪深度档 1~5，耳机播报"深度降噪"）
├─ key=02 → 普通模式（val=00，播报"普通模式"）
├─ key=03 → 通透模式（val=00，播报"环境音/通透"）
└─ key=04 → 抗风降噪/风噪（val=00，播报"抗风降噪"）
```
- 4 个模式全部经真人听感 + 耳机语音播报验证（普通↔降噪↔通透↔风噪 全循环）
- 降噪深度档：key=01 的 val 1~5 均进入降噪并播报"深度降噪"（具体深度差异待细分）
- **验证方法**：手机 RoseLink 切基线模式（人耳确认）→ 关闭手机蓝牙释放控制会话 → PC AF_BTH 连 SCN 6 发命令 → 用户听播报/效果确认

### 其他已确认命令
| 命令 | 功能 |
|---|---|
| `FF 00 03 0E 01 04` | 游戏模式开（播报"游戏模式"） |
| `FF 00 03 01 06` / `1B` | 状态查询（返回键值流：0C=电量、35=连接态等） |

### 无效/待定
- `FF 00 03 09 02 01~05`（普通模式的其他值）→ 播报普通（无效）
- `08 EE 00 00 00 04 81 0D ...` 样本 → 无响应（应为响应帧而非命令）
- 游戏模式关闭命令、降噪深度档细分 → 待测

## 9. ★ 连接方式的关键发现（Android App 真机验证，2026-02）

**控制连接必须通过私有服务 UUID `0cf12d31-fac3-4553-bd80-d6832e7b3931` 做服务发现连接**！

| 连接方式 | 结果 |
|---|---|
| `createRfcommSocketToServiceRecord(SPP 0x1101)` | 连接保持、查询有响应，**但 ANC 命令无效**（耳机不识别为控制会话） |
| 直连通道（`createInsecureRfcommSocket(6)` 反射） | **被耳机拒绝**（read -1，连接即断） |
| `createInsecureRfcommSocketToServiceRecord(0cf12d31)` | **✓ 控制完整生效**（ANC 4 模式全部真机验证） |

- RoseLink 手机 App 的 logcat 显示 `BTA_JvStartDiscovery num_uuid:1` + `remote_scn:6`——即用单个私有 UUID 查询到 SCN 6 再连接，与上述结论吻合。
- 电量键（0x0C）在私有连接下查询（`FF 00 03 01 06`）暂未稳定返回 → 电量显示待优化。

### Android 驱动 App（com.zirconpods）
- 位置：`android/`（Kotlin + Compose + **Miuix 0.9.3**）
- 功能：连接（私有 UUID）、ANC 降噪/风噪/普通/通透 + 降噪深度、游戏模式、电量（待完善）
- 构建：AGP 9.1.0 / Gradle 9.3.1 / compileSdk 37 / JDK 22 / Miuix 0.9.3（`platforms;android-37.0`）
- 已装真机验证：ANC 4 模式切换全部生效
