package local.roselink.lite

import android.Manifest
import android.app.Activity
import android.app.StatusBarManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.ArrowBack
import top.yukonga.miuix.kmp.icon.icons.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Miuix owns the full UI; the bound foreground service continues to own Bluetooth. */
class MainActivity : ComponentActivity(), BluetoothController.Listener {
    private var device by mutableStateOf(DeviceState())
    private var records by mutableStateOf<List<BluetoothController.DeviceRecord>>(emptyList())
    private var logLines by mutableStateOf<List<String>>(emptyList())
    private var feedback by mutableStateOf("")
    private var scanHint by mutableStateOf("先在系统蓝牙中配对锆势，再扫描并连接。")
    private var pairing by mutableStateOf(false)
    private var page by mutableStateOf("home")
    private var productColor by mutableStateOf(0)
    private var cloudEnabled by mutableStateOf(true)
    private var cloudStatus by mutableStateOf("")
    private var autoEnabled by mutableStateOf(true)
    private var autoStatus by mutableStateOf("")
    private var tileStatus by mutableStateOf("点击后由系统确认添加；也可下拉控制中心，点铅笔添加“耳机模式”")
    private var autoTriedThisVisit = false
    private var pendingAction: (() -> Unit)? = null
    private var notificationAddress: String? = null
    private var destroyed = false
    private lateinit var client: BluetoothClient
    private val specs = ZirconProtocol.controls().associateBy { it.key }
    private val accent = Color(0xff3482ff)
    private val background = Color(0xfff7f7f7)
    private val muted = Color(0xff898989)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        window.statusBarColor = android.graphics.Color.rgb(247, 247, 247)
        window.navigationBarColor = android.graphics.Color.rgb(247, 247, 247)
        productColor = getSharedPreferences("appearance", MODE_PRIVATE).getInt("product_color", 0).coerceIn(0, 2)
        cloudEnabled = getSharedPreferences(LiveConnectionNotification.PREFERENCES, MODE_PRIVATE)
            .getBoolean(LiveConnectionNotification.ENABLED, true)
        autoEnabled = AutoConnection.enabled(this)
        autoStatus = AutoConnection.status(this)
        client = BluetoothClient(this, this)
        if (intent.action == "local.roselink.lite.OPEN_PAIRING") pairing = true
        setContent {
            MiuixTheme(colors = lightColorScheme()) { App() }
        }
    }

    @Composable private fun App() {
        BackHandler(enabled = page != "home") { goBack() }
        Scaffold(containerColor = background) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Header()
                key(page) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (page) {
                            "home" -> Home()
                            "touch" -> TouchSettings()
                            "lab" -> Laboratory()
                            "cycle" -> AncCycle()
                            "find" -> FindDevice()
                            "settings" -> SettingsPage()
                            "info" -> Information()
                            "logs" -> Logs()
                        }
                    }
                }
            }
            if (pairing) PairingPanel()
        }
    }

    @Composable private fun Header() {
        val title = when (page) {
            "home" -> "ROSE Zircon"
            "touch" -> "触控设置"
            "lab" -> "实验室功能"
            "cycle" -> "降噪触控设置"
            "find" -> "查找设备"
            "settings" -> "设置"
            "info" -> "设备信息"
            else -> "诊断日志"
        }
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (page == "home") ResourceButton(R.drawable.ic_pairing, "配对与连接设备") { pairing = true }
            else Box(Modifier.size(44.dp).clip(CircleShape).clickable { goBack() }, contentAlignment = Alignment.Center) {
                Icon(MiuixIcons.ArrowBack, "返回", Modifier.size(27.dp))
            }
            Text(title, Modifier.weight(1f).padding(start = 8.dp), fontSize = 23.sp, fontWeight = FontWeight.Medium)
            if (page == "home") {
                ResourceButton(R.drawable.ic_refresh, "刷新耳机状态", device.connected) { client.refresh() }
                ResourceButton(R.drawable.ic_settings, "设置") { page = "settings" }
            }
        }
    }

    @Composable private fun ResourceButton(resource: Int, label: String, enabled: Boolean = true, action: () -> Unit) {
        Box(Modifier.size(44.dp).clip(CircleShape).alpha(if (enabled) 1f else .35f)
            .clickable(enabled = enabled, onClick = action).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
            Image(painterResource(resource), label, Modifier.size(26.dp))
        }
    }

    @Composable private fun Home() {
        AndroidView(factory = { ProductImageView(it) }, update = { it.setColor(productColor) },
            modifier = Modifier.fillMaxWidth().height(292.dp))
        Group {
            Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Battery("左耳", device.leftBattery)
                Battery("右耳", device.rightBattery)
                Battery("充电盒", device.caseBattery)
            }
            SuperDropdown(title = "耳机配色", items = listOf("黑色", "银色", "紫色"), selectedIndex = productColor,
                onSelectedIndexChange = {
                    productColor = it
                    getSharedPreferences("appearance", MODE_PRIVATE).edit().putInt("product_color", it).apply()
                }, alwaysRight = true, insideMargin = DpSize(20.dp, 20.dp))
        }
        Group {
            Text("噪声控制", Modifier.padding(start = 20.dp, top = 18.dp), fontSize = 17.sp)
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                val modes = listOf(1 to 0, 4 to 3, 3 to 2, 2 to 1)
                for ((wire, kind) in modes) {
                    val selected = device.values["noise"] == wire
                    val glyph = remember(kind, selected) {
                        Bitmap.createBitmap(104, 104, Bitmap.Config.ARGB_8888).also {
                            NoiseModeDrawable(kind, selected, 2f).apply { setBounds(0, 0, 104, 104); draw(android.graphics.Canvas(it)) }
                        }.asImageBitmap()
                    }
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(enabled = device.connected) { send("noise", wire) }
                        .padding(vertical = 8.dp).alpha(if (device.connected) 1f else .42f)
                        .semantics { contentDescription = NoiseModeSelection.label(wire) + if (selected) "，耳机已确认" else "，点击切换" },
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(glyph, null, Modifier.size(52.dp))
                        Text(NoiseModeSelection.label(wire), Modifier.padding(top = 6.dp), fontSize = 14.sp,
                            color = if (selected) accent else Color.Black)
                    }
                }
            }
            if (device.values["noise"] == 6) Text("当前：极限降噪", Modifier.padding(start = 20.dp, bottom = 16.dp), color = accent, fontSize = 13.sp)
        }
        Group { DeviceDropdown("eq", title = "超级大师调音") }
        Group {
            Toggle("game", "游戏模式", "降低延迟，声音响应更快")
            Toggle("multipoint", "双设备连接", "允许耳机同时连接两个设备")
        }
        Group { DeviceDropdown("codec", "无损音频协议", "选择耳机使用的音频编码协议") }
        Group {
            Entry("触控设置", "自定义左右耳单击、双击、三击与长按") { page = "touch" }
            Entry("查找设备", "让左耳或右耳播放提示音") { page = "find" }
            Entry("实验室功能") { page = "lab" }
        }
        if (feedback.isNotEmpty()) Text(feedback, Modifier.padding(horizontal = 12.dp), fontSize = 12.sp, color = accent)
        Text(if (device.connected) "已连接 · 设置以耳机回复为准" else "打开耳机盒，点击左上角连接设备", Modifier.padding(horizontal = 12.dp), color = muted, fontSize = 12.sp)
    }

    @Composable private fun Battery(label: String, value: Int) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text(battery(value), Modifier.padding(top = 6.dp), fontSize = 13.sp, color = muted)
            Box(Modifier.padding(top = 10.dp).width(44.dp).height(5.dp).clip(CircleShape).background(Color(0xffe7e7e7))) {
                if (value in 0..100) Box(Modifier.fillMaxWidth(value / 100f).fillMaxHeight().background(if (value <= 20) Color(0xffed704c) else Color(0xff00b589)))
            }
        }
    }

    @Composable private fun Group(content: @Composable ColumnScope.() -> Unit) {
        Card(Modifier.fillMaxWidth(), cornerRadius = 20.dp, color = Color.White, content = content)
    }

    @Composable private fun Entry(title: String, subtitle: String? = null, enabled: Boolean = true, action: () -> Unit) {
        Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = action).padding(horizontal = 20.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                if (subtitle != null) Text(subtitle, Modifier.padding(top = 4.dp), fontSize = 13.sp, color = muted)
            }
            Icon(MiuixIcons.ArrowRight, null, Modifier.padding(start = 10.dp).size(20.dp), tint = Color(0xffa8a8a8))
        }
    }

    @Composable private fun DeviceDropdown(key: String, title: String? = null, subtitle: String? = null) {
        val spec = specs.getValue(key)
        Picker(title ?: spec.title, subtitle, spec.labels.toList(), spec.values.indexOf(device.values[key] ?: -1), device.connected) {
            send(key, spec.values[it])
        }
    }

    @Composable private fun Picker(title: String, subtitle: String?, options: List<String>, selected: Int, enabled: Boolean, choose: (Int) -> Unit) {
        // Miuix requires a valid index. An explicit unknown sentinel avoids inventing a selected device option.
        val choices = if (selected < 0) options + "未读取" else options
        SuperDropdown(title = title, summary = subtitle, items = choices,
            selectedIndex = if (selected < 0) choices.lastIndex else selected,
            onSelectedIndexChange = { if (it in options.indices) choose(it) }, enabled = enabled,
            alwaysRight = true, insideMargin = DpSize(20.dp, 20.dp))
    }

    @Composable private fun Toggle(key: String, title: String, summary: String? = null) {
        val value = device.values[key]
        SuperSwitch(title = title, summary = summary ?: if (value == null) "未读取" else null,
            checked = value == 1, enabled = device.connected, insideMargin = DpSize(20.dp, 20.dp),
            onCheckedChange = { send(key, if (it) 1 else 0) })
    }

    @Composable private fun TouchSettings() {
        Text("自定义耳机触控操作", Modifier.padding(12.dp), color = muted, fontSize = 13.sp)
        Group { Toggle("touch", "耳机触控", "控制耳机是否响应触控操作") }
        for ((prefix, title) in listOf("left" to "左耳", "right" to "右耳")) {
            Text(title, Modifier.padding(start = 12.dp, top = 8.dp), color = muted, fontSize = 14.sp)
            Group {
                DeviceDropdown("${prefix}_single", "单击")
                DeviceDropdown("${prefix}_double", "双击")
                DeviceDropdown("${prefix}_triple", "三击")
                DeviceDropdown("${prefix}_long", "长按")
            }
        }
    }

    @Composable private fun Laboratory() {
        Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            EarBadge("L", "左耳")
            EarBadge("R", "右耳")
        }
        Group { Toggle("wear", "入耳检测", "控制耳机是否响应入耳 / 出耳操作") }
        Group {
            val value = device.values["prompt_volume"] ?: -1
            Picker("提示音等级", "调整设备提示音音量大小", (0..5).map { "$it 格" }, value, device.connected) { send("prompt_volume", it) }
        }
        Group { Toggle("deep_anc", "极限降噪", "关闭后切回普通模式") }
        Group { Entry("降噪触控设置", "选择触控循环中的噪声控制模式") { page = "cycle" } }
        Text("所有选中态以耳机回包为准", Modifier.padding(horizontal = 12.dp), fontSize = 12.sp, color = muted)
    }

    @Composable private fun EarBadge(letter: String, label: String) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.ic_earbud), label, Modifier.size(64.dp).graphicsLayer(scaleX = if (letter == "R") -1f else 1f))
            Text(letter, Modifier.padding(top = 12.dp).clip(CircleShape).background(accent).padding(horizontal = 9.dp, vertical = 3.dp), color = Color.White, fontSize = 15.sp)
        }
    }

    @Composable private fun AncCycle() {
        val confirmed = device.values["anc_cycle"]
        Text("选择触控切换时依次使用的模式，至少保留两种。", Modifier.padding(12.dp), fontSize = 14.sp, color = muted)
        if (confirmed == null) Text("等待耳机返回循环设置，可点击刷新重试。", Modifier.padding(horizontal = 12.dp), fontSize = 13.sp, color = muted)
        Group {
            for ((mask, label) in listOf(1 to "降噪", 2 to "风噪", 4 to "普通", 8 to "通透")) {
                SuperSwitch(title = label, checked = confirmed != null && confirmed and mask != 0,
                    enabled = device.connected && confirmed != null, insideMargin = DpSize(20.dp, 20.dp),
                    onCheckedChange = { enabled ->
                        if (confirmed != null) {
                            val next = if (enabled) confirmed or mask else confirmed and mask.inv()
                            if (Integer.bitCount(next) < 2) toast("至少保留两种噪声控制模式") else send("anc_cycle", next)
                        }
                    })
            }
        }
        Button(text = "刷新耳机设置", onClick = { client.refresh() }, enabled = device.connected, modifier = Modifier.padding(horizontal = 12.dp))
    }

    @Composable private fun FindDevice() {
        Text("请先取下耳机，再播放查找提示音。", Modifier.padding(12.dp), color = muted, fontSize = 14.sp)
        Group {
            for ((label, value) in listOf("查找左耳" to 1, "查找右耳" to 2, "查找双耳" to 3, "停止播放" to 4))
                Entry(label, enabled = device.connected) { send("find", value) }
        }
    }

    @Composable private fun SettingsPage() {
        Group {
            SuperSwitch(title = "自动连接", summary = "打开应用或耳机重新连上手机时，连接上次成功使用的耳机",
                checked = autoEnabled, insideMargin = DpSize(20.dp, 20.dp), onCheckedChange = {
                    autoEnabled = it
                    AutoConnection.setEnabled(this@MainActivity, it)
                    autoStatus = AutoConnection.status(this@MainActivity)
                    if (it) withBluetooth { safe { client.autoConnect() } }
                })
            Text(autoStatus, Modifier.padding(horizontal = 20.dp, vertical = 12.dp), fontSize = 12.sp, color = muted)
            Entry("后台运行设置", "若 ColorOS 限制自动连接，请允许 ZirconPods 后台运行") {
                safe { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            }
        }
        Group {
            Entry("添加耳机模式磁贴", "点击循环：降噪 → 风噪 → 通透 → 普通") { addModeTile() }
            Text(tileStatus, Modifier.padding(horizontal = 20.dp, vertical = 12.dp), fontSize = 12.sp, color = muted)
        }
        Group {
            Entry("设备信息") { page = "info" }
            Entry("诊断日志") { page = "logs" }
            Entry("通知栏控制设置") { startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) }
            Entry("系统蓝牙设置") { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
        }
        Group {
            SuperSwitch(title = "流体云连接提示", summary = "兼容测试 · 连接完成后短时显示电量与模式",
                checked = cloudEnabled, enabled = LiveConnectionNotification.apiAvailable(),
                insideMargin = DpSize(20.dp, 20.dp), onCheckedChange = {
                    cloudEnabled = it
                    getSharedPreferences(LiveConnectionNotification.PREFERENCES, MODE_PRIVATE)
                        .edit().putBoolean(LiveConnectionNotification.ENABLED, it).apply()
                })
            Entry("系统实时更新权限", "允许后，断开并重新连接耳机进行测试") { openPromotionSettings() }
            Text(cloudStatus, Modifier.padding(horizontal = 20.dp, vertical = 12.dp), fontSize = 12.sp, lineHeight = 19.sp, color = muted)
            Button(text = "重新检查系统支持", onClick = { cloudStatus = LiveConnectionNotification.status(this@MainActivity) }, modifier = Modifier.padding(start = 20.dp, bottom = 18.dp))
        }
        Group {
            Text("ZirconPods 0.7.0", Modifier.padding(start = 20.dp, top = 20.dp), fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text("Miuix 主题 · 锆势本地蓝牙控制", Modifier.padding(20.dp), color = muted, fontSize = 14.sp)
        }
    }

    @Composable private fun Information() {
        val info = "设备名称  ${device.name}\n蓝牙地址  ${device.address.ifEmpty { "未连接" }}\n固件版本  ${device.firmware.ifEmpty { "未读取" }}\n连接状态  ${device.status}"
        Group { SelectionContainer { Text(info, Modifier.padding(20.dp), fontSize = 15.sp, lineHeight = 28.sp) } }
        Button(text = "复制设备信息", onClick = { copy("ZirconPods 设备信息", info) }, modifier = Modifier.padding(horizontal = 12.dp))
    }

    @Composable private fun Logs() {
        val text = logLines.joinToString("\n").ifEmpty { "暂无通信记录" }
        Button(text = "复制日志", onClick = { copy("ZirconPods 诊断日志", text) }, modifier = Modifier.padding(horizontal = 12.dp))
        Group { SelectionContainer { Text(text, Modifier.padding(16.dp), fontSize = 11.sp, lineHeight = 17.sp) } }
    }

    @Composable private fun PairingPanel() {
        Dialog(onDismissRequest = { pairing = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                Card(Modifier.widthIn(max = 360.dp).fillMaxWidth(.9f).fillMaxHeight(), cornerRadius = 24.dp, color = background) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("连接设备", fontSize = 25.sp, fontWeight = FontWeight.Medium)
                        Text(device.status, color = if (device.connected) accent else muted, fontSize = 14.sp)
                        Text(scanHint, fontSize = 13.sp, color = muted)
                        Button(text = "扫描设备", submit = true, enabled = !device.connecting, modifier = Modifier.fillMaxWidth(),
                            onClick = { withBluetooth { safe { client.scan(); scanHint = "正在扫描，点击设备连接。" } } })
                        Button(text = "断开当前连接", enabled = device.connected || device.connecting, modifier = Modifier.fillMaxWidth(), onClick = { safe { client.disconnect() } })
                        if (records.isEmpty()) Text("尚未发现设备", fontSize = 14.sp, color = muted)
                        for (record in records) Group {
                            Entry(record.name, "${if (record.bonded) "已配对" else "附近设备"} · ${record.address}", enabled = !device.connecting) {
                                withBluetooth { connectWithNotification(record.address) }
                            }
                        }
                        Button(text = "返回控制界面", modifier = Modifier.fillMaxWidth(), onClick = { pairing = false })
                    }
                }
            }
        }
    }

    private fun send(key: String, value: Int) {
        if (!device.connected) { toast("请先连接耳机"); return }
        safe { client.command(key, value); feedback = "已提交设置，等待耳机确认" }
    }
    override fun onResume() {
        super.onResume()
        cloudStatus = LiveConnectionNotification.status(this)
        autoStatus = AutoConnection.status(this)
        if (!autoTriedThisVisit && AutoConnection.permitted(this) && ::client.isInitialized) {
            autoTriedThisVisit = true
            safe { client.autoConnect() }
        }
    }
    override fun onStop() {
        super.onStop()
        autoTriedThisVisit = false
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == LiveConnectionNotification.ACTION_OPEN_CONTROLS) {
            page = "home"
            pairing = false
        }
        if (intent.action == "local.roselink.lite.OPEN_PAIRING") { page = "home"; pairing = true }
    }
    private fun addModeTile() {
        if (Build.VERSION.SDK_INT < 33) {
            tileStatus = "下拉控制中心 → 编辑（铅笔）→ 添加“耳机模式”"
            toast(tileStatus)
            return
        }
        safe {
            getSystemService(StatusBarManager::class.java).requestAddTileService(
                ComponentName(this, EarphoneModeTile::class.java), "耳机模式",
                android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_mode_tile), mainExecutor) { result ->
                if (!destroyed) tileStatus = when (result) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "已添加，下拉控制中心即可使用"
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "磁贴已在控制中心，可通过铅笔调整位置"
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> "尚未添加，可稍后重试或在控制中心通过铅笔添加"
                    else -> "系统未完成添加（$result），请通过控制中心铅笔手动添加"
                }
            }
        }
    }
    private fun openPromotionSettings() {
        try {
            startActivity(Intent("android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS")
                .setData(android.net.Uri.parse("package:$packageName"))
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        } catch (error: android.content.ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            toast("该系统未提供独立入口，请检查应用通知中的实时更新设置")
        }
    }
    private fun goBack() { page = when (page) { "cycle" -> "lab"; "info", "logs" -> "settings"; else -> "home" } }
    private fun withBluetooth(action: () -> Unit) {
        pendingAction = action
        val permissions = if (Build.VERSION.SDK_INT >= 31)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 41) else continueBluetoothAction()
    }
    private fun continueBluetoothAction() {
        safe {
            val adapter = getSystemService(BluetoothManager::class.java)?.adapter
            if (adapter == null) { pendingAction = null; toast("这台设备不支持蓝牙"); return@safe }
            if (!adapter.isEnabled) {
                @Suppress("DEPRECATION")
                startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 42)
                return@safe
            }
            val action = pendingAction; pendingAction = null; action?.invoke()
        }
    }
    private fun connectWithNotification(address: String) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            && !getPreferences(MODE_PRIVATE).getBoolean("notification_permission_asked", false)) {
            notificationAddress = address
            getPreferences(MODE_PRIVATE).edit().putBoolean("notification_permission_asked", true).apply()
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 43)
        } else safe { client.connect(address) }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 43) {
            val address = notificationAddress; notificationAddress = null
            if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) toast("可从设置中开启通知栏控制")
            if (address != null && !destroyed) safe { client.connect(address) }
        } else if (requestCode == 41) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) continueBluetoothAction()
            else { pendingAction = null; scanHint = "需要附近设备权限才能扫描和连接耳机。" }
        }
    }
    @Deprecated("Uses the platform Bluetooth enable dialog")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 42 && resultCode == Activity.RESULT_OK) continueBluetoothAction()
        else if (requestCode == 42) pendingAction = null
    }
    override fun onState(next: DeviceState) = runOnUiThread {
        if (!destroyed) {
            val newlyConnected = !device.connected && next.connected
            device = next.copy()
            autoStatus = AutoConnection.status(this)
            if (newlyConnected) pairing = false
            if (!next.connected) feedback = ""
        }
    }
    override fun onDevices(devices: MutableList<BluetoothController.DeviceRecord>) = runOnUiThread {
        if (!destroyed) records = devices.toList()
    }
    override fun onLog(line: String) = runOnUiThread {
        if (!destroyed) logLines = (logLines + "${SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date())}  ${line.take(1200)}").takeLast(100)
    }
    private fun safe(action: () -> Unit) {
        try { action() }
        catch (error: SecurityException) { toast("需要蓝牙权限，请在系统设置中允许附近设备"); onLog(error.toString()) }
        catch (error: RuntimeException) { toast("操作未完成，请查看诊断日志"); onLog(error.toString()) }
    }
    private fun battery(value: Int) = if (value in 0..100) "$value%" else "—"
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    private fun copy(title: String, text: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(title, text)); toast("已复制")
    }
    override fun onDestroy() {
        destroyed = true; pendingAction = null; notificationAddress = null
        if (::client.isInitialized) client.close()
        super.onDestroy()
    }
}
