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
import androidx.compose.ui.platform.LocalDensity
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
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.UUID
import kotlin.math.roundToInt

@Composable
internal fun CustomEqScreen(store: CustomEqPresets, phone: PhoneEqState, enablePhone: (IntArray, Boolean) -> Unit,
    disablePhone: () -> Unit, retryPhone: () -> Unit, copyPhoneDiagnostics: () -> Unit,
    importFile: () -> Unit, imported: GraphicEqFile.Curve?, importHint: String, consumeImport: () -> Unit) {
    val accent = Color(0xff3482ff)
    val muted = MiuixTheme.colorScheme.onSurfaceVariantSummary
    var presets by remember { mutableStateOf(store.load()) }
    var selectedId by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("我的音效") }
    var gains by rememberSaveable { mutableStateOf(phone.gains.clone()) }
    var globalMode by rememberSaveable { mutableStateOf(phone.global) }
    var initialPhoneLoaded by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val styleEntries = remember(context) { EqStyleCatalog.entries(context) }
    val styles = styleEntries.map { it.curve }
    val favorites = rememberEqFavorites(store)
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
    val draftKey = when {
        selected >= 0 && !dirty -> "local:$selectedId"
        selectedId.isEmpty() && selectedStyle in styles.indices && name == styles[selectedStyle].name && gains.contentEquals(styles[selectedStyle].gains) -> styleEntries[selectedStyle].key
        else -> null
    }
    val isFavorite = draftKey != null && draftKey in favorites
    val favoritePresets = orderedEqPresets(eqLibraryPresets(presets, styleEntries), favorites, onlyFavorites = true)

    Text("十段均衡器 · 手机端处理", Modifier.padding(horizontal = 12.dp, vertical = 4.dp), fontSize = 14.sp, color = muted)
    Card(Modifier.fillMaxWidth(), cornerRadius = 20.dp, color = MiuixTheme.colorScheme.surface) {
        if (favoritePresets.isEmpty()) {
            Text("收藏的 EQ", Modifier.padding(start = 20.dp, top = 18.dp), fontSize = 17.sp)
            Text("载入或调好曲线后，点击下方星标收藏；收藏会在通知面板置顶。",
                Modifier.padding(20.dp), fontSize = 13.sp, color = muted)
        } else {
            val favoriteIndex = favoritePresets.indexOfFirst { it.key == draftKey }
            AdaptiveDropdown(title = "收藏的 EQ", summary = "载入编辑区后可继续调整，不自动应用",
                items = favoritePresets.map { "★ ${it.name}" } + "请选择收藏",
                selectedIndex = if (favoriteIndex >= 0) favoriteIndex else favoritePresets.size,
                onSelectedIndexChange = { index ->
                    favoritePresets.getOrNull(index)?.let { preset ->
                        selectedId = preset.localId.orEmpty(); selectedStyle = preset.styleIndex
                        gains = preset.gains.toIntArray()
                        fileReference = if (preset.styleIndex in styles.indices) styles[preset.styleIndex] else null
                        name = fileReference?.name ?: preset.name
                        deletePending = false; feedback = "已载入收藏，尚未应用到手机 EQ"
                    }
                })
        }
    }
    Card(Modifier.fillMaxWidth(), cornerRadius = 20.dp, color = MiuixTheme.colorScheme.surface) {
        AdaptiveDropdown(title = "风格文件库", summary = "9种风格参考，非锆势测量校正",
            items = styles.mapIndexed { index, curve -> (if (styleEntries[index].key in favorites) "★ " else "") + curve.name } + "请选择风格",
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
        Card(Modifier.fillMaxWidth(), cornerRadius = 20.dp, color = MiuixTheme.colorScheme.surface) {
            Text("文件预览：${curve.name}", Modifier.padding(20.dp), fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text("文件声明来源：${curve.source}\n十段增益：${curve.gains.joinToString(" / ")} dB\n${curve.note}",
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp), fontSize = 13.sp, color = muted)
            AdaptiveActions(Modifier.fillMaxWidth().padding(16.dp)) { actionModifier ->
                Button(text = "载入编辑区", modifier = actionModifier, onClick = {
                    gains = curve.gains.clone(); name = curve.name; selectedId = ""; selectedStyle = -1
                    fileReference = curve; deletePending = false; feedback = "文件曲线已载入，尚未保存或应用"; consumeImport()
                })
                Button(text = "取消", modifier = actionModifier, onClick = consumeImport)
            }
        }
    }
    Card(Modifier.fillMaxWidth(), cornerRadius = 20.dp, color = MiuixTheme.colorScheme.surface) {
        val choices = presets.map { (if ("local:${it.id}" in favorites) "★ " else "") + it.name } + "新建预设"
        AdaptiveDropdown(title = "本地音效预设", items = choices, selectedIndex = if (selected >= 0) selected else choices.lastIndex,
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
            cursorBrush = androidx.compose.ui.graphics.SolidColor(accent),
            textStyle = TextStyle(color = MiuixTheme.colorScheme.onSurface, fontSize = 17.sp),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth()
                .background(MiuixTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp)).padding(16.dp)
                .semantics { contentDescription = "音效名称，最多40个字" })
        Button(text = when { isFavorite -> "★ 取消收藏"; draftKey != null -> "☆ 收藏这条 EQ"; else -> "☆ 保存并收藏当前曲线" },
            enabled = name.isNotBlank() && (draftKey != null || selected >= 0 || presets.size < 20),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), onClick = {
                if (draftKey != null) {
                    if (store.setFavorite(draftKey, !isFavorite)) feedback = if (isFavorite) "已取消收藏，预设仍保留" else "已收藏，通知面板会优先显示"
                    else feedback = "收藏保存失败，请重试"
                } else {
                    val preset = EqPreset(if (selected >= 0) selectedId else UUID.randomUUID().toString(), name.trim(), gains.toList())
                    val next = if (selected >= 0) presets.toMutableList().also { it[selected] = preset } else presets + preset
                    if (store.save(next, store.favorites() + "local:${preset.id}")) {
                        presets = next; selectedId = preset.id; selectedStyle = -1; name = preset.name
                        feedback = "曲线已保存并收藏，尚未应用到手机 EQ"
                    } else feedback = "保存及收藏失败，请重试"
                }
            })
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("均衡器", Modifier.weight(1f), color = muted, fontSize = 14.sp)
            Button(text = "重置", onClick = { gains = IntArray(10); selectedStyle = -1; fileReference = null; feedback = "曲线已归零，尚未应用" })
        }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            val scale = LocalDensity.current.fontScale
            val chartWidth = AdaptiveLayoutPolicy.eqWidth(maxWidth.value, scale).dp
            val trackHeight = if (LocalAdaptiveViewport.current.height < 480.dp) 160.dp else 240.dp
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Row(Modifier.width(chartWidth).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(24.dp).height(trackHeight + (40 * scale).dp).padding(top = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        for (label in listOf("+6", "0", "−6")) Text(label, fontSize = 10.sp, color = muted)
                    }
                    for (index in gains.indices) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            val gain = gains[index]
                            Text(if (gain > 0) "+$gain" else "$gain", fontSize = 11.sp, color = muted)
                            EqBand(CustomEqProtocol.FREQUENCIES[index], gain, accent, Modifier.fillMaxWidth().height(trackHeight)) { value ->
                                gains = gains.clone().also { it[index] = value }
                                feedback = "曲线已编辑，点击下方应用到手机 EQ"
                            }
                            Text(CustomEqProtocol.FREQUENCIES[index], fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
        Text("频率 Hz · 增益 dB · 1 dB 步进 · 窄窗口可左右滑动频段", Modifier.padding(start = 20.dp, top = 6.dp, bottom = 16.dp), color = muted, fontSize = 12.sp)
        AdaptiveActions(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) { actionModifier ->
            Button(text = if (selected >= 0) "保存修改" else "保存到手机", enabled = name.isNotBlank() && (selected >= 0 || presets.size < 20),
                modifier = actionModifier, onClick = {
                    val preset = EqPreset(if (selected >= 0) selectedId else UUID.randomUUID().toString(), name.trim(), gains.toList())
                    val next = if (selected >= 0) presets.toMutableList().also { it[selected] = preset } else presets + preset
                    if (store.save(next)) { presets = next; selectedId = preset.id; name = preset.name; feedback = "预设已保存；当前音效是否启用请看下方手机 EQ 状态" }
                    else feedback = "本地保存失败，请重试"
                })
            Button(text = if (deletePending) "确认删除" else "删除", enabled = selected >= 0,
                modifier = actionModifier, onClick = {
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
    Card(Modifier.fillMaxWidth(), cornerRadius = 20.dp, color = MiuixTheme.colorScheme.surface) {
        SuperSwitch(title = "手机端 EQ", summary = "处理本机音频，参数保存在手机",
            checked = phone.enabled, enabled = phone.loaded && Build.VERSION.SDK_INT >= 28, insideMargin = DpSize(20.dp, 20.dp),
            onCheckedChange = { if (it) enablePhone(gains.clone(), globalMode) else disablePhone() })
        AdaptiveDropdown(title = "接入方式", items = listOf("播放器会话", "全局兼容（实验）"), selectedIndex = if (globalMode) 1 else 0,
            onSelectedIndexChange = { globalMode = it == 1; feedback = "接入方式已选择，点击应用后使用" },
            alwaysRight = true, insideMargin = DpSize(20.dp, 20.dp))
        Text(phone.status, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = accent, fontSize = 14.sp)
        if (phone.enabled) Text("当前运行：${if (phone.global) "全局兼容" else "播放器会话"} · 已接入 ${phone.effects} 个音效\n"
            + "当前曲线：${phone.gains.joinToString(" / ")} dB\n自动输入余量：${PhoneEqCurve.headroom(phone.gains).toInt()} dB",
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp), fontSize = 12.sp, color = muted)
        Button(text = if (phone.enabled) "应用本页曲线到手机 EQ" else "应用曲线并启用手机 EQ",
            enabled = phone.loaded && Build.VERSION.SDK_INT >= 28,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), onClick = { enablePhone(gains.clone(), globalMode) })
        AdaptiveActions(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) { actionModifier ->
            Button(text = "重建音效", enabled = phone.enabled, modifier = actionModifier, onClick = retryPhone)
            Button(text = "复制手机 EQ 诊断", modifier = actionModifier, onClick = copyPhoneDiagnostics)
        }
        Text("先连接耳机再播放音乐。会话模式需要播放器公开音频会话，可重新开始播放或在播放器内选择系统均衡器。全局兼容可能被系统或播放器绕过；请试听对比，建议先关闭其他均衡器。检测不到耳机输出时暂停处理。",
            Modifier.padding(20.dp), color = muted, fontSize = 12.sp)
        Text("引擎部分移植自 GPL-3.0 开源项目 Equalizer314。Android 9+ 可用；本功能不向耳机写入 EQ。",
            Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp), color = muted, fontSize = 12.sp)
    }
    if (feedback.isNotEmpty()) Text(feedback, Modifier.padding(horizontal = 12.dp), color = accent, fontSize = 13.sp)
}

@Composable
private fun EqBand(frequency: String, gain: Int, accent: Color, modifier: Modifier, change: (Int) -> Unit) {
    val onChange by rememberUpdatedState(change)
    val track = MiuixTheme.colorScheme.surfaceContainer
    val handle = MiuixTheme.colorScheme.surface
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
        drawLine(track, Offset(center, top), Offset(center, bottom), 4.dp.toPx(), StrokeCap.Round)
        drawLine(accent, Offset(center, y), Offset(center, bottom), 4.dp.toPx(), StrokeCap.Round)
        drawCircle(handle, 8.dp.toPx(), Offset(center, y))
        drawCircle(accent, 8.dp.toPx(), Offset(center, y), style = Stroke(2.5.dp.toPx()))
    }
}
