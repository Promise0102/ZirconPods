package local.roselink.lite

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.extra.SuperSwitch
import androidx.compose.ui.unit.DpSize
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtil.Companion.MiuixPopupHost

/** A direct notification PendingIntent opens this private dialog Activity, without overlay permission. */
class NotificationPanelActivity : ComponentActivity(), BluetoothController.Listener {
    companion object {
        const val ACTION_CONTROLS = "local.roselink.lite.NOTIFICATION_CONTROLS"
        const val ACTION_LEVEL = "local.roselink.lite.NOTIFICATION_LEVEL"
        const val ACTION_DONE = "local.roselink.lite.NOTIFICATION_DONE"
    }
    private var device by mutableStateOf(DeviceState())
    private var pendingMode by mutableStateOf<Int?>(null)
    private var feedback by mutableStateOf("")
    private lateinit var client: BluetoothClient
    private lateinit var phoneClient: PhoneEqClient
    private var phone by mutableStateOf(PhoneEqState())
    private var quickPresets by mutableStateOf<List<EqLibraryPreset>>(emptyList())
    private val presetStore by lazy { CustomEqPresets(this) }
    private var levelOnly = false

    override fun onCreate(savedInstanceState: Bundle?) {
        applyAppearanceWindow(dialog = true)
        super.onCreate(savedInstanceState)
        // Direct Activity notification actions let SystemUI end the interaction. No hidden shade APIs.
        if (intent.action == ACTION_DONE) { finish(); return }
        levelOnly = intent.action == ACTION_LEVEL
        setFinishOnTouchOutside(true)
        client = BluetoothClient(this, this)
        phoneClient = PhoneEqClient(this) { next -> if (!isFinishing) phone = next.copy() }
        setContent {
            ZirconTheme(this) {
                LaunchedEffect(pendingMode) {
                    val requested = pendingMode
                    if (requested != null) {
                        kotlinx.coroutines.delay(5000)
                        if (pendingMode == requested) { pendingMode = null; feedback = "尚未收到耳机确认，保留实际模式" }
                    }
                }
                // Dropdowns use an overlay host. Keep it outside the rounded/scrolling card,
                // in a full-window coordinate space, so it can draw and receive input.
                Box(Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTapGestures(onTap = { finish() })
                }, contentAlignment = Alignment.Center) {
                    FoldAwareRegion(this@NotificationPanelActivity, Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(12.dp)
                        .pointerInput(Unit) { detectTapGestures(onTap = {}) },
                        cornerRadius = 24.dp, color = MiuixTheme.colorScheme.background) {
                        AdaptiveModalContent(title = {
                            Text(if (levelOnly) "降噪等级" else EarphoneCardContent.title(device),
                                Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        }, body = {
                            if (levelOnly) LevelOptions() else Controls()
                            if (feedback.isNotEmpty()) Text(feedback, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }, actions = {
                            AdaptiveActions(Modifier.fillMaxWidth()) { actionModifier ->
                                if (!levelOnly) Button(text = "更多", modifier = actionModifier, onClick = {
                                    startActivity(Intent(this@NotificationPanelActivity, MainActivity::class.java)
                                        .setAction(LiveConnectionNotification.ACTION_OPEN_CONTROLS)
                                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                                    finish()
                                })
                                Button(text = "完成", modifier = actionModifier, onClick = { finish() })
                            }
                        })
                    }
                    }
                    }
                    MiuixPopupHost()
                }
            }
        }
    }
    override fun onStart() {
        super.onStart()
        if (!isFinishing) window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
    }
    override fun onResume() {
        super.onResume()
        quickPresets = eqLibraryPresets(presetStore.load(), EqStyleCatalog.entries(this))
    }
    @Composable private fun Controls() {
        Card(Modifier.fillMaxWidth(), cornerRadius = 16.dp, color = MiuixTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Battery("左耳", device.leftBattery, Modifier.weight(1f))
                Battery("右耳", device.rightBattery, Modifier.weight(1f))
                Battery("充电盒", device.caseBattery, Modifier.weight(1f))
            }
        }
        Card(Modifier.fillMaxWidth(), cornerRadius = 16.dp, color = MiuixTheme.colorScheme.surface) {
            NoiseModeButtons(device, pendingMode != null, ::choose)
            Row(Modifier.fillMaxWidth().clickable(enabled = EarphoneCardContent.canSetAncLevel(device)) {
                startActivity(Intent(this@NotificationPanelActivity, NotificationPanelActivity::class.java).setAction(ACTION_LEVEL))
            }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("降噪等级", Modifier.weight(1f), fontSize = 14.sp)
                Text(EarphoneCardContent.ancLevel(device), color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 13.sp)
            }
        }
        Text(if (device.connected) "当前：${NoiseModeSelection.label(device.values["noise"] ?: -1)}" else "请先打开应用连接耳机",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
        EqPresets()
    }
    @Composable private fun EqPresets() {
        var expanded by rememberSaveable { mutableStateOf(false) }
        val favorites = rememberEqFavorites(presetStore)
        var onlyFavorites by remember { mutableStateOf(presetStore.favoriteOnly()) }
        val choices = orderedEqPresets(quickPresets, favorites, onlyFavorites)
        val labels = choices.map { (if (it.key in favorites) "★ " else "") + it.name }
        val matching = if (phone.enabled) choices.indexOfFirst { it.gains == phone.gains.toList() } else -1
        val sentinel = if (phone.enabled) "当前自定义曲线" else "未启用 EQ"
        Card(Modifier.fillMaxWidth(), cornerRadius = 16.dp, color = MiuixTheme.colorScheme.surface) {
            SuperSwitch(title = "仅显示收藏", checked = onlyFavorites,
                insideMargin = DpSize(16.dp, 12.dp), onCheckedChange = { only ->
                    if (presetStore.setFavoriteOnly(only)) { onlyFavorites = only; expanded = false }
                    else feedback = "收藏筛选保存失败，请重试"
                })
            if (choices.isEmpty()) {
                Text("还没有收藏的 EQ，可在自定义 EQ 页面点击星标收藏。",
                    Modifier.padding(16.dp), fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Button(text = "管理收藏", modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), onClick = {
                    startActivity(Intent(this@NotificationPanelActivity, MainActivity::class.java).setAction(PhoneEqEngine.OPEN)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)); finish()
                })
            } else {
            if (LocalAdaptiveViewport.current.separated || LocalAdaptiveViewport.current.height < 400.dp) {
                Button(text = if (expanded) "收起 EQ 预设" else "切换 EQ 预设",
                    modifier = Modifier.fillMaxWidth().padding(12.dp), onClick = { expanded = !expanded })
                if (expanded) choices.forEachIndexed { index, preset ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .clickable(enabled = phone.loaded && android.os.Build.VERSION.SDK_INT >= 28) {
                            phoneClient.enable(preset.gains.toIntArray(), phone.global); expanded = false
                        }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(labels[index], Modifier.weight(1f))
                        if (index == matching) Text("✓", color = MiuixTheme.colorScheme.primary)
                    }
                }
            } else {
            SuperDropdown(title = "切换 EQ 预设", summary = "应用到手机音效", items = labels + sentinel,
                defaultWindowInsetsPadding = false,
                selectedIndex = if (matching >= 0) matching else choices.size,
                enabled = phone.loaded && android.os.Build.VERSION.SDK_INT >= 28,
                alwaysRight = true, insideMargin = DpSize(16.dp, 16.dp), onSelectedIndexChange = { index ->
                    if (index in choices.indices) phoneClient.enable(choices[index].gains.toIntArray(), phone.global)
                })
            }
            }
            if (phone.enabled) Button(text = "停用 EQ", modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                onClick = { phoneClient.disable() })
            Text(if (android.os.Build.VERSION.SDK_INT < 28) "手机 EQ 需要 Android 9 及以上" else phone.status,
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp), fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
    @Composable private fun Battery(label: String, value: Int, modifier: Modifier = Modifier) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(EarphoneCardContent.battery(device.connected, value), color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
            Image(painterResource(R.drawable.battery_status), null, Modifier.padding(top = 5.dp).size(18.dp, 9.dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MiuixTheme.colorScheme.onSurface))
        }
    }
    @Composable private fun LevelOptions() {
        Text("锆势已确认支持标准降噪与极限降噪", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
        Card(Modifier.fillMaxWidth(), cornerRadius = 16.dp, color = MiuixTheme.colorScheme.surface) {
            for ((mode, label) in listOf(1 to "标准", 6 to "极限")) {
                Row(Modifier.fillMaxWidth().clickable(enabled = EarphoneCardContent.canSetAncLevel(device) && pendingMode == null) {
                    choose(mode)
                }.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), fontSize = 17.sp,
                        color = if (device.connected && device.values["noise"] == mode) Color(0xff3482ff) else MiuixTheme.colorScheme.onSurface)
                    if (device.connected && device.values["noise"] == mode) Text("✓", color = Color(0xff3482ff), fontSize = 20.sp)
                }
            }
        }
        if (!EarphoneCardContent.canSetAncLevel(device)) Text("请先启用降噪模式", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
    private fun choose(mode: Int) {
        if (!device.connected || pendingMode != null) return
        if (device.values["noise"] == mode) { if (levelOnly) finish(); return }
        if (mode == 6) client.command("deep_anc", 1) else client.command("noise", mode)
        pendingMode = mode
        feedback = "等待耳机确认…"
    }
    override fun onState(next: DeviceState) = runOnUiThread {
        if (!isFinishing) {
            device = next.copy()
            if (!next.connected && !next.connecting) { pendingMode = null; feedback = "耳机未连接" }
            else if (pendingMode != null && next.values["noise"] == pendingMode) {
                pendingMode = null; feedback = "耳机已确认"
                if (levelOnly) finish()
            }
        }
    }
    override fun onDevices(devices: MutableList<BluetoothController.DeviceRecord>) = Unit
    override fun onLog(line: String) = Unit
    override fun onDestroy() {
        if (::client.isInitialized) client.close()
        if (::phoneClient.isInitialized) phoneClient.close()
        super.onDestroy()
    }
}
