package com.zirconpods

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.zirconpods.bluetooth.ZirconRfcommController
import com.zirconpods.ui.App

class MainActivity : ComponentActivity() {

    private lateinit var controller: ZirconRfcommController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = ZirconRfcommController()

        setContent {
            DisposableEffect(Unit) {
                onDispose { controller.release() }
            }
            App(controller = controller)
        }
    }
}
