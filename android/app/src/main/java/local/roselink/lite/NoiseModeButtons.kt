package local.roselink.lite

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun NoiseModeButtons(device: DeviceState, pending: Boolean = false, choose: (Int) -> Unit) {
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= (288 * scale).dp) 4 else 2
        Column(Modifier.fillMaxWidth()) {
            listOf(1 to 0, 4 to 3, 3 to 2, 2 to 1).chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { (mode, kind) ->
                        val confirmed = device.connected && device.values["noise"] == mode
                        val icon = remember(kind, confirmed) {
                            Bitmap.createBitmap(104, 104, Bitmap.Config.ARGB_8888).also {
                                NoiseModeDrawable(kind, confirmed, 2f).apply { setBounds(0, 0, 104, 104); draw(android.graphics.Canvas(it)) }
                            }.asImageBitmap()
                        }
                        Column(Modifier.weight(1f).heightIn(min = 48.dp)
                            .alpha(if (device.connected) 1f else .4f)
                            .clickable(enabled = device.connected && !pending, role = Role.RadioButton) { choose(mode) }
                            .semantics { selected = confirmed; contentDescription = NoiseModeSelection.label(mode) }
                            .padding(horizontal = 4.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(icon, null, Modifier.size(48.dp))
                            Text(NoiseModeSelection.label(mode), Modifier.padding(top = 4.dp), fontSize = 14.sp,
                                color = if (confirmed) Color(0xff3482ff) else MiuixTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}
