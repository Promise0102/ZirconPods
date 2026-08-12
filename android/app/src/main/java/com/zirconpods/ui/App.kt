package com.zirconpods.ui

import androidx.compose.runtime.Composable
import com.zirconpods.bluetooth.ZirconRfcommController
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

@Composable
fun App(controller: ZirconRfcommController) {
    ZirconAppTheme(colorSchemeMode = ColorSchemeMode.System) {
        HomeScreen(controller = controller)
    }
}
