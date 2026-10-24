package local.roselink.lite

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

internal data class AdaptiveViewport(val width: Dp, val height: Dp, val separated: Boolean = false)
internal val LocalAdaptiveViewport = staticCompositionLocalOf { AdaptiveViewport(360.dp, 640.dp) }

/** Uses the current laid-out window, including split-screen and dialog windows, rather than device size. */
@Composable
internal fun FoldAwareRegion(activity: ComponentActivity, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val folds by produceState<List<FoldingFeature>>(emptyList(), activity) {
        try {
            activity.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).collect { layout ->
                    value = layout.displayFeatures.filterIsInstance<FoldingFeature>()
                }
            }
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { value = emptyList() } // Unsupported vendor extensions fall back to window sizing.
    }
    val density = LocalDensity.current
    val view = LocalView.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(modifier.onGloballyPositioned { coordinates ->
        val location = IntArray(2); view.getLocationOnScreen(location)
        origin = coordinates.positionInRoot() + Offset(location[0].toFloat(), location[1].toFloat())
    }) {
        val widthPx = with(density) { maxWidth.roundToPx() }
        val heightPx = with(density) { maxHeight.roundToPx() }
        val screen = IntArray(2); val window = IntArray(2)
        activity.window.decorView.getLocationOnScreen(screen)
        activity.window.decorView.getLocationInWindow(window)
        val fold = folds.firstOrNull { it.isSeparating || it.occlusionType == FoldingFeature.OcclusionType.FULL }
        val vertical = fold?.orientation == FoldingFeature.Orientation.VERTICAL
        val bounds = fold?.bounds
        val translation = if (vertical) screen[0] - window[0] - origin.x else screen[1] - window[1] - origin.y
        val start = if (vertical) bounds?.left ?: 0 else bounds?.top ?: 0
        val end = if (vertical) bounds?.right ?: 0 else bounds?.bottom ?: 0
        val region = AdaptiveLayoutPolicy.region(widthPx, heightPx, fold != null, vertical,
            (start + translation).roundToInt(), (end + translation).roundToInt(), with(density) { 8.dp.roundToPx() })
        val separated = region.width != widthPx || region.height != heightPx
        if (region.width > 0 && region.height > 0) {
            with(density) {
                Box(Modifier.offset(region.left.toDp(), region.top.toDp()).size(region.width.toDp(), region.height.toDp())) {
                    CompositionLocalProvider(LocalAdaptiveViewport provides AdaptiveViewport(region.width.toDp(), region.height.toDp(), separated), content = content)
                }
            }
        }
    }
}

/** Expand inside the scrollable pane when a floating menu could cross a hinge or a short window. */
@Composable
internal fun AdaptiveDropdown(title: String, items: List<String>, selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit, summary: String? = null, enabled: Boolean = true,
    alwaysRight: Boolean = true, insideMargin: DpSize = DpSize(20.dp, 20.dp)) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val viewport = LocalAdaptiveViewport.current
    if (!viewport.separated && viewport.height >= 400.dp) {
        SuperDropdown(title = title, summary = summary, items = items, selectedIndex = selectedIndex,
            onSelectedIndexChange = onSelectedIndexChange, enabled = enabled,
            alwaysRight = alwaysRight, insideMargin = insideMargin)
    } else {
        Column {
            Column(Modifier.fillMaxWidth().clickable(enabled = enabled) { expanded = !expanded }
                .padding(horizontal = insideMargin.width, vertical = insideMargin.height)) {
                Text(title, fontSize = 17.sp)
                if (summary != null) Text(summary, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(items.getOrNull(selectedIndex).orEmpty(), Modifier.weight(1f), fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Text(if (expanded) "⌃" else "⌄")
                }
            }
            if (expanded) items.forEachIndexed { index, label ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = enabled) {
                    onSelectedIndexChange(index); expanded = false
                }.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), color = if (index == selectedIndex) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface)
                    if (index == selectedIndex) Text("✓", color = MiuixTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
internal fun AdaptiveModalContent(title: @Composable () -> Unit, body: @Composable () -> Unit, actions: @Composable () -> Unit) {
    val height = (LocalAdaptiveViewport.current.height - 24.dp).coerceAtLeast(0.dp)
    val short = height < (240 * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp
    if (short) {
        Column(Modifier.heightIn(max = height).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) { title(); body(); actions() }
    } else {
        Column(Modifier.heightIn(max = height).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            title()
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) { body() }
            actions()
        }
    }
}

/** Keep action labels readable instead of squeezing two buttons into a narrow or enlarged-text row. */
@Composable
internal fun AdaptiveActions(modifier: Modifier = Modifier, content: @Composable (Modifier) -> Unit) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        if (maxWidth < (300 * density.fontScale.coerceAtLeast(1f)).dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content(Modifier.fillMaxWidth()) }
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { content(Modifier.weight(1f)) }
    }
}
