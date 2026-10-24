package local.roselink.lite

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.view.View
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/** Select a matching native launch window before Compose draws its first frame. */
internal fun Activity.applyAppearanceWindow(dialog: Boolean = false) {
    val mode = getSharedPreferences("appearance", Context.MODE_PRIVATE).getInt("theme_mode", 0).coerceIn(0, 2)
    val systemDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    val dark = mode == 2 || (mode == 0 && systemDark)
    setTheme(if (dialog) {
        if (dark) R.style.Theme_NotificationPanel_Dark else R.style.Theme_NotificationPanel
    } else {
        if (dark) R.style.Theme_RoseLinkLite_Dark else R.style.Theme_RoseLinkLite
    })
}

@Composable
private fun appearanceMode(): Pair<SharedPreferences, Int> {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("appearance", Context.MODE_PRIVATE) }
    var mode by remember(prefs) { mutableIntStateOf(prefs.getInt("theme_mode", 0).coerceIn(0, 2)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "theme_mode") mode = prefs.getInt("theme_mode", 0).coerceIn(0, 2)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return prefs to mode
}

@Composable
internal fun ZirconTheme(activity: Activity, content: @Composable () -> Unit) {
    val (_, mode) = appearanceMode()
    val dark = mode == 2 || (mode == 0 && isSystemInDarkTheme())
    val colors = if (dark) darkColorScheme() else lightColorScheme()
    SideEffect {
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility = activity.window.decorView.systemUiVisibility.let {
            val light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            if (dark) it and light.inv() else it or light
        }
        @Suppress("DEPRECATION")
        activity.window.statusBarColor = colors.background.toArgb()
        @Suppress("DEPRECATION")
        activity.window.navigationBarColor = colors.background.toArgb()
    }
    MiuixTheme(colors = colors, content = content)
}

@Composable
internal fun ThemeSetting() {
    val (prefs, mode) = appearanceMode()
    AdaptiveDropdown(title = "外观", items = listOf("跟随系统", "浅色", "暗色"), selectedIndex = mode,
        onSelectedIndexChange = { if (it in 0..2) prefs.edit().putInt("theme_mode", it).apply() },
        alwaysRight = true, insideMargin = DpSize(20.dp, 20.dp))
}
