package local.roselink.lite

import android.os.Build

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.extra.SuperSwitch
import java.util.UUID
import kotlin.math.roundToInt

@Composable
internal fun CustomEqScreen(device: DeviceState, store: CustomEqPresets, read: () -> Unit, apply: (IntArray) -> Unit,
    copyDiagnostics: () -> Unit, phone: PhoneEqState, enablePhone: (IntArray, Boolean) -> Unit,
    disablePhone: () -> Unit, retryPhone: () -> Unit, copyPhoneDiagnostics: () -> Unit,
    importFile: () -> Unit, imported: GraphicEqFile.Curve?, importHint: String, consumeImport: () -> Unit) {
    val accent = Color(0xff3482ff)
    val muted = Color(0xff898989)
    var presets by remember { mutableStateOf(store.load()) }
    var selectedId by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("我的音效") }
    var gains by rememberSaveable { mutableStateOf(phone.gains.clone()) }
    var globalMode by rememberSaveable { mutableStateOf(phone.global) }
    var initialPhoneLoaded by rememberSaveable { mutableStateOf(false) }
    var showHardware by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val styles = remember(context) { EqStyleCatalog.load(context) }
    var selectedStyle by rememberSaveable { mutableStateOf(-1) }
    var fileReference by remember { mutableStateOf<GraphicEqFile.Curve?>(null) }
    LaunchedEffect(phone.loaded) {
        if (phone.loaded && !initialPhoneLoaded) {
            if (gains.all { it == 0 } && selectedId.isEmpty() && name == "我的音效") gains = phone.gains.clone()
            globalMode = phone.global; initialPhoneLoaded = true
        }
    }
    var feedback by rememberSaveable { mutableStateOf("") }
    var deletePending by remember { mutableStateOf(false) }
    val selected = presets.indexOfFirst { it.id == selectedId }
    val dirty = selected < 0 || presets[selected].name != name || presets[selected].gains != gains.toList()

    Text("十段均衡器 · 手机端处理", Modifier.padding(horizontal = 12.dp, vertical = 4.dp), fontSize = 14.sp, color = muted)
    Card(Modifier.fillMaxWidth(), cornerRadius = 20.dp, color = Color.White) {
        SuperDropdown(title = "风格文件库", summary = "9种风格参考，非锆势测量校正", items = styles.map { it.name } + "请选择风格",
            selectedIndex = if (selectedStyle in styles.indices) selectedStyle else styles.size,
            onSelectedIndexChange = { index ->
                if (index in styles.indices) {
                    val style = styles[index]
                    selectedStyle = index; selectedId = ""; name = style.name; gains = style.gains.clone()
                    fileReference = style; deletePending = false
                    feedback = "风格曲线已载入编辑区，尚未保存或应用"
                }
            }, alwaysRight = true, insideMargin = DpSize(20.dp, 20.dp))
        Button(text = "导入 GraphicEQ 文件", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), onClick = importFile)
        Text("支持GraphicEQ文本（常见于Wavelet/AutoEQ导出），先预览再载入；不会自动启用音效。参数Filter、卷积文件和JSON暂不支持。",
            Modifier.padding(horizontal = 20.dp, vertical = 12.dp), color = muted, fontSize = 12.sp)
        fileReference?.let { reference ->
            Text("参考来源：${reference.source}\n许可声明：${reference.license}\n${reference.note}",
                Modifier.padding(horizontal = 20.dp, vertical = 12.dp), color = muted, fontSize = 12.sp)
        }
        if (importHint.isNotEmpty()) Text(importHint, Modifier.padding(20.dp), color = accent, fontSize = 13.sp)
    }
    imported?.let { curve ->
        Card(Modifier.fillMaxWidth(), cornerRadius = 20.dp, color = Color.White) {
            Text("文件预览：${curve.name}", Modifier.padding(20.dp), fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text("文件声明来源：${curve.source}\n十段增益：${curve.gains.joinToString(" / ")} dB\n${curve.note}",
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp), fontSize = 13.sp, color = muted)
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(text = "载入编辑区", modifier = Modifier.weight(1f), onClick = {
                    gains = curve.gains.clone(); name = curve.name; selectedId = ""; selectedStyle = -1
                    fileReference = curve; deletePending = false; feedback = "文件曲线已载入，尚未保存或应用"; consumeImport()
                })
                Button(text = "取消", modifier = Modifier.weight(1f), onClick = consumeImport)
            }
        }
    }
    Card(Modifier.fillMaxWidth(), cornerRadius = 20.dp, color = Color.White) {
        val choices = presets.map { it.name } + "新建预设"
        SuperDropdown(title = "本地音效预设", items = choices, selectedIndex = if (selected >= 0) selected else choices.lastIndex,
            onSelectedIndexChange = { index ->
                selectedStyle = -1; fileReference = null
                if (index in presets.indices) {
                    val preset = presets[index]
                    selectedId = preset.id; name = preset.name; gains = preset.gains.toIntArray()
                    feedback = "已载入预设，点击下方应用到手机 EQ"
                } else { selectedId = ""; name = "我的音效"; gains = IntArray(10); feedback = "新建平直曲线，尚未保存" }
                deletePending = false
            }, alwaysRight = true, insideMargin = DpSize(20.dp, 20.dp))
        Text("音效名称", Modifier.padding(start = 20.dp, top = 4.dp), fontSize = 15.sp)
        BasicTextField(value = name, onValueChange = { name = it.take(40); deletePending = false }, singleLine = true,
            textStyle = TextStyle(color = Color(0xff252525), fontSize = 17.sp),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth()
                .background(Color(0xfff3f4f5), RoundedCornerShape(16.dp)).padding(16.dp)
                .semantics { contentDescription = "音效名称，最多40个字" })
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("均衡器", Modifier.weight(1f), color = muted, fontSize = 14.sp)
            Button(text = "重置", onClick = { gains = IntArray(10); selectedStyle = -1; fileReference = null; feedback = "曲线已归零，尚未应用" })
        }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            val chartWidth = maxOf(maxWidth, 300.dp)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Row(Modifier.width(chartWidth).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(20.dp).height(298.dp).padding(top = 26.dp, bottom = 24.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        for (label in listOf("+6", "0", "−6")) Text(label, fontSize = 10.sp, color = muted)
                    }
                    for (index in gains.indices) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            val gain = gains[index]
                            Text(if (gain > 0) "+$gain" else "$gain", fontSize = 11.sp, color = muted)
                            EqBand(CustomEqProtocol.FREQUENCIES[index], gain, accent, Modifier.fillMaxWidth().height(260.dp)) { value ->
                                gains = gains.clone().also { it[index] = value }
                                feedback = "曲线已编辑，点击下方应用到手机 EQ"
                            }
                            Text(CustomEqProtocol.FREQUENCIES[index], fontSize = 11.sp, color = Color(0xff333333))
                        }
                    }
                }
            }
        }
        Text("频率 Hz · 增益 dB · 1 dB 步进", Modifier.padding(start = 20.dp, top = 6.dp, bottom = 16.dp), color = muted, fontSize = 12.sp)
        Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(text = if (selected >= 0) "保存修改" else "保存到手机", enabled = name.isNotBlank() && (selected >= 0 || presets.size < 20),
                modifier = Modifier.weight(1f), onClick = {
                    val preset = EqPreset(if (selected >= 0) selectedId else UUID.randomUUID().toString(), name.trim(), gains.toList())
                    val next = if (selected >= 0) presets.toMutableList().also { it[selected] = preset } else presets + preset
                    if (store.save(next)) { presets = next; selectedId = preset.id; name = preset.name; feedback = "预设已保存；当前音效是否启用请看下方手机 EQ 状态" }
                    else feedback = "本地保存失败，请重试"
                })
            Button(text = if (deletePending) "确认删除" else "删除", enabled = selected >= 0,
                modifier = Modifier.weight(1f), onClick = {
                    if (!deletePending) { deletePending = true; feedback = "再次点击确认删除该手机预设" }
                    else {
                        val next = presets.filterNot { it.id == selectedId }
                        if (store.save(next)) { presets = next; selectedId = ""; deletePending = false; feedback = "手机预设已删除，耳机设置未改变" }
                        else feedback = "删除未保存，请重试"
                    }
                })
        }
        Text(if (dirty) "当前编辑尚未保存到手机" else "手机预设已保存", Modifier.padding(20.dp), color = muted, fontSize = 12.sp)
    }
    Card(Modifier.fillMaxWidth(), cornerRadius = 20.dp, color = Color.White) {
        SuperSwitch(title = "手机端 EQ", summary = "处理本机音频，参数保存在手机",
            checked = phone.enabled, enabled = phone.loaded && Build.VERSION.SDK_INT >= 28, insideMargin = DpSize(20.dp, 20.dp),
            onCheckedChange = { if (it) enablePhone(gains.clone(), globalMode) else disablePhone() })
        SuperDropdown(title = "接入方式", items = listOf("播放器会话", "全局兼容（实验）"), selectedIndex = if (globalMode) 1 else 0,
            onSelectedIndexChange = { globalMode = it == 1; feedback = "接入方式已选择，点击应用后使用" },
            alwaysRight = true, insideMargin = DpSize(20.dp, 20.dp))
        Text(phone.status, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = accent, fontSize = 14.sp)
        if (phone.enabled) Text("当前运行：${if (phone.global) "全局兼容" else "播放器会话"} · 已接入 ${phone.effects} 个音效\n"
            + "当前曲线：${phone.gains.joinToString(" / ")} dB\n自动输入余量：${PhoneEqCurve.headroom(phone.gains).toInt()} dB",
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp), fontSize = 12.sp, color = muted)
        Button(text = if (phone.enabled) "应用本页曲线到手机 EQ" else "应用曲线并启用手机 EQ",
            enabled = phone.loaded && Build.VERSION.SDK_INT >= 28,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), onClick = { enablePhone(gains.clone(), globalMode) })
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(text = "重建音效", enabled = phone.enabled, modifier = Modifier.weight(1f), onClick = retryPhone)
            Button(text = "复制手机 EQ 诊断", modifier = Modifier.weight(1f), onClick = copyPhoneDiagnostics)
        }
        Text("先连接耳机再播放音乐。会话模式需要播放器公开音频会话，可重新开始播放或在播放器内选择系统均衡器。全局兼容可能被系统或播放器绕过；请试听对比，建议先关闭其他均衡器。检测不到耳机输出时暂停处理。",
            Modifier.padding(20.dp), color = muted, fontSize = 12.sp)
        Text("引擎部分移植自 GPL-3.0 开源项目 Equalizer314。Android 9+ 可用；本功能不向耳机写入 EQ。",
            Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp), color = muted, fontSize = 12.sp)
    }
    Button(text = if (showHardware) "收起耳机内置 EQ 诊断" else "耳机内置 EQ（实验诊断）",
        onClick = { showHardware = !showHardware }, modifier = Modifier.padding(horizontal = 12.dp))
    if (showHardware) {
    Card(Modifier.fillMaxWidth(), cornerRadius = 20.dp, color = Color.White) {
        Text("耳机 EQ", Modifier.padding(start = 20.dp, top = 20.dp), fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Text(if (device.connected) device.customEqStatus else "请先连接锆势；离线仍可编辑和保存预设。",
            Modifier.padding(horizontal = 20.dp, vertical = 12.dp), color = muted, fontSize = 13.sp)
        Text(device.customEqDiagnosticSummary, Modifier.padding(horizontal = 20.dp, vertical = 6.dp), color = muted, fontSize = 12.sp)
        val actual = device.customEq
        if (actual != null) {
            Text("耳机回读：" + actual.joinToString(" / ") { if (it > 0) "+$it" else "$it" } + " dB",
                Modifier.padding(horizontal = 20.dp, vertical = 6.dp), fontSize = 12.sp, color = muted)
            Text("当前音效：" + when (device.values["eq"]) { 0 -> "HiFi"; 1 -> "流行"; 2 -> "摇滚"; 4 -> "自定义"; else -> "未读取" },
                Modifier.padding(horizontal = 20.dp, vertical = 6.dp), fontSize = 13.sp, color = muted)
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(text = "读取耳机 EQ", enabled = device.connected && !device.customEqBusy, modifier = Modifier.weight(1f), onClick = read)
            Button(text = "载入回读曲线", enabled = device.connected && actual != null && !device.customEqBusy, modifier = Modifier.weight(1f), onClick = {
                actual?.let { gains = it.clone(); selectedId = ""; name = "耳机曲线"; feedback = "已载入耳机曲线，可命名后保存到手机" }
            })
        }
        Button(text = "应用到耳机（实验）", enabled = device.connected && actual != null && !device.customEqBusy,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), onClick = { apply(gains.clone()) })
        Button(text = "复制 EQ 诊断", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), onClick = copyDiagnostics)
        Text("先读取兼容数据，再写入并回读核对；曲线一致后才切换自定义模式。频点按 i7 参考显示，锆势的实际频点、听感及断电保留情况仍需实测。",
            Modifier.padding(20.dp), fontSize = 12.sp, color = muted)
    }
    }
    if (feedback.isNotEmpty()) Text(feedback, Modifier.padding(horizontal = 12.dp), color = accent, fontSize = 13.sp)
}

@Composable
private fun EqBand(frequency: String, gain: Int, accent: Color, modifier: Modifier, change: (Int) -> Unit) {
    val onChange by rememberUpdatedState(change)
    Canvas(modifier.semantics {
        contentDescription = "$frequency Hz，$gain dB"
        progressBarRangeInfo = ProgressBarRangeInfo(gain.toFloat(), -6f..6f, 11)
        setProgress { value ->
            if (value.isFinite()) { onChange(value.roundToInt().coerceIn(-6, 6)); true } else false
        }
    }.pointerInput(Unit) {
        detectTapGestures { point ->
            val inset = 12.dp.toPx()
            onChange((6f - (point.y - inset) / (size.height - 2 * inset).coerceAtLeast(1f) * 12).roundToInt().coerceIn(-6, 6))
        }
    }.pointerInput(Unit) {
        detectVerticalDragGestures(onDragStart = { point ->
            val inset = 12.dp.toPx()
            onChange((6f - (point.y - inset) / (size.height - 2 * inset).coerceAtLeast(1f) * 12).roundToInt().coerceIn(-6, 6))
        }) { event, _ ->
            event.consume()
            val inset = 12.dp.toPx()
            onChange((6f - (event.position.y - inset) / (size.height - 2 * inset).coerceAtLeast(1f) * 12).roundToInt().coerceIn(-6, 6))
        }
    }) {
        val top = 12.dp.toPx(); val bottom = size.height - top; val center = size.width / 2
        val y = top + (6 - gain) / 12f * (bottom - top)
        drawLine(Color(0xffe8e8e8), Offset(center, top), Offset(center, bottom), 4.dp.toPx(), StrokeCap.Round)
        drawLine(accent, Offset(center, y), Offset(center, bottom), 4.dp.toPx(), StrokeCap.Round)
        drawCircle(Color.White, 8.dp.toPx(), Offset(center, y))
        drawCircle(accent, 8.dp.toPx(), Offset(center, y), style = Stroke(2.5.dp.toPx()))
    }
}
