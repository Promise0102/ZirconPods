package local.roselink.lite

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

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
    private var levelOnly = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Direct Activity notification actions let SystemUI end the interaction. No hidden shade APIs.
        if (intent.action == ACTION_DONE) { finish(); return }
        levelOnly = intent.action == ACTION_LEVEL
        setFinishOnTouchOutside(true)
        client = BluetoothClient(this, this)
        setContent {
            MiuixTheme(colors = lightColorScheme()) {
                LaunchedEffect(pendingMode) {
                    val requested = pendingMode
                    if (requested != null) {
                        kotlinx.coroutines.delay(5000)
                        if (pendingMode == requested) { pendingMode = null; feedback = "尚未收到耳机确认，保留实际模式" }
                    }
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Card(Modifier.widthIn(max = 380.dp).fillMaxWidth().padding(12.dp), cornerRadius = 24.dp, color = Color(0xfff7f7f7)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(if (levelOnly) "降噪等级" else EarphoneCardContent.title(device),
                                Modifier.align(Alignment.CenterHorizontally), fontSize = 17.sp, fontWeight = FontWeight.Medium)
                            if (levelOnly) LevelOptions() else Controls()
                            if (feedback.isNotEmpty()) Text(feedback, fontSize = 12.sp, color = Color(0xff777777))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (!levelOnly) Button(text = "更多", modifier = Modifier.weight(1f), onClick = {
                                    startActivity(Intent(this@NotificationPanelActivity, MainActivity::class.java)
                                        .setAction(LiveConnectionNotification.ACTION_OPEN_CONTROLS)
                                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                                    finish()
                                })
                                Button(text = "完成", modifier = Modifier.weight(1f), onClick = { finish() })
                            }
                        }
                    }
                }
            }
        }
    }
    override fun onStart() {
        super.onStart()
        if (!isFinishing) window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
    }
    @Composable private fun Controls() {
        Card(Modifier.fillMaxWidth(), cornerRadius = 16.dp, color = Color.White) {
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Battery("左耳", device.leftBattery)
                Battery("右耳", device.rightBattery)
                Battery("充电盒", device.caseBattery)
            }
        }
        Card(Modifier.fillMaxWidth(), cornerRadius = 16.dp, color = Color.White) {
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                for ((mode, kind) in listOf(1 to 0, 4 to 3, 3 to 2, 2 to 1)) {
                    val selected = device.connected && device.values["noise"] == mode
                    val icon = remember(kind, selected) {
                        Bitmap.createBitmap(104, 104, Bitmap.Config.ARGB_8888).also {
                            NoiseModeDrawable(kind, selected, 2f).apply { setBounds(0, 0, 104, 104); draw(android.graphics.Canvas(it)) }
                        }.asImageBitmap()
                    }
                    Column(Modifier.weight(1f).alpha(if (device.connected) 1f else .4f)
                        .clickable(enabled = device.connected && pendingMode == null) { choose(mode) }
                        .padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(icon, NoiseModeSelection.label(mode), Modifier.size(42.dp))
                        Text(NoiseModeSelection.label(mode), Modifier.padding(top = 4.dp), fontSize = 12.sp,
                            color = if (selected) Color(0xff168aff) else Color(0xff333333))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().clickable(enabled = EarphoneCardContent.canSetAncLevel(device)) {
                startActivity(Intent(this@NotificationPanelActivity, NotificationPanelActivity::class.java).setAction(ACTION_LEVEL))
            }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("降噪等级", Modifier.weight(1f), fontSize = 14.sp)
                Text(EarphoneCardContent.ancLevel(device), color = Color(0xff888888), fontSize = 13.sp)
            }
        }
        Text(if (device.connected) "当前：${NoiseModeSelection.label(device.values["noise"] ?: -1)}" else "请先打开应用连接耳机",
            color = Color(0xff888888), fontSize = 12.sp)
    }
    @Composable private fun Battery(label: String, value: Int) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(EarphoneCardContent.battery(device.connected, value), color = Color(0xff888888), fontSize = 12.sp)
            Image(painterResource(R.drawable.battery_status), null, Modifier.padding(top = 5.dp).size(18.dp, 9.dp))
        }
    }
    @Composable private fun LevelOptions() {
        Text("锆势已确认支持标准降噪与极限降噪", color = Color(0xff888888), fontSize = 12.sp)
        Card(Modifier.fillMaxWidth(), cornerRadius = 16.dp, color = Color.White) {
            for ((mode, label) in listOf(1 to "标准", 6 to "极限")) {
                Row(Modifier.fillMaxWidth().clickable(enabled = EarphoneCardContent.canSetAncLevel(device) && pendingMode == null) {
                    choose(mode)
                }.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), fontSize = 17.sp,
                        color = if (device.connected && device.values["noise"] == mode) Color(0xff3482ff) else Color.Black)
                    if (device.connected && device.values["noise"] == mode) Text("✓", color = Color(0xff3482ff), fontSize = 20.sp)
                }
            }
        }
        if (!EarphoneCardContent.canSetAncLevel(device)) Text("请先启用降噪模式", fontSize = 12.sp, color = Color(0xff888888))
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
        super.onDestroy()
    }
}
