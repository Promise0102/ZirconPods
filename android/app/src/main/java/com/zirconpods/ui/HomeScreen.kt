package com.zirconpods.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.zirconpods.bluetooth.ZirconRfcommController
import com.zirconpods.protocol.ZirconProtocol
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun HomeScreen(controller: ZirconRfcommController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val connectionState by controller.connectionState.collectAsState()
    val battery by controller.battery.collectAsState()
    val deviceName by controller.deviceName.collectAsState()
    val lastError by controller.lastError.collectAsState()

    var selectedMode by remember { mutableStateOf(ZirconProtocol.AncMode.NORMAL) }
    var depth by remember { mutableIntStateOf(3) }
    var gameMode by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var hasPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermission = result.values.all { it }
        refreshDevices(context)?.let { devices = it }
    }

    LaunchedEffect(Unit) {
        hasPermission = hasBluetoothPermission(context)
        if (hasPermission) {
            refreshDevices(context)?.let { devices = it }
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                )
            )
        }
    }

    val topBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(topBarState)

    Scaffold(
        topBar = {
            TopAppBar(
                title = "锆势控制",
                scrollBehavior = scrollBehavior,
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .overScrollVertical()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ===== 连接卡片 =====
            SectionCard(title = "连接") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (devices.isEmpty()) {
                        Text(
                            "未找到已配对的锆势耳机，请先在系统蓝牙中配对",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 13.sp
                        )
                    } else {
                        devices.forEach { dev ->
                            DeviceRow(
                                device = dev,
                                selected = deviceName == dev.name,
                                onClick = {
                                    controller.disconnect()
                                    controller.connect(dev)
                                }
                            )
                        }
                    }
                    when (connectionState) {
                        ZirconRfcommController.ConnectionState.CONNECTED -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .background(Color(0xFF4CAF50), RoundedCornerShape(4.dp))
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("已连接：$deviceName", color = Color(0xFF4CAF50), fontSize = 13.sp)
                        }
                        ZirconRfcommController.ConnectionState.CONNECTING -> Text(
                            "连接中…", color = MiuixTheme.colorScheme.primary, fontSize = 13.sp
                        )
                        ZirconRfcommController.ConnectionState.ERROR -> Text(
                            "连接失败：$lastError", color = Color(0xFFE53935), fontSize = 13.sp
                        )
                        else -> Text(
                            "未连接", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 13.sp
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (connectionState == ZirconRfcommController.ConnectionState.CONNECTED) {
                            TextButton(text = "断开", onClick = { controller.disconnect() })
                        }
                        TextButton(text = "刷新", onClick = {
                            refreshDevices(context)?.let { devices = it }
                            controller.queryStatus()
                        })
                    }
                }
            }

            // ===== 电量卡片 =====
            SectionCard(title = "电量") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    BatteryItem("左耳", battery.left)
                    BatteryItem("右耳", battery.right)
                    BatteryItem("充电仓", battery.case)
                }
            }

            // ===== 噪声控制 =====
            SectionCard(title = "噪声控制") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ZirconProtocol.AncMode.entries.forEach { mode ->
                            ModeButton(
                                title = mode.title,
                                selected = selectedMode == mode,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    selectedMode = mode
                                    controller.sendAncMode(mode, depth)
                                }
                            )
                        }
                    }
                    if (selectedMode == ZirconProtocol.AncMode.NOISE_CANCELLING) {
                        Text(
                            "降噪深度",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ZirconProtocol.NOISE_DEPTHS.forEach { d ->
                                DepthButton(
                                    title = d.title,
                                    selected = depth == d.value,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        depth = d.value
                                        controller.sendAncMode(ZirconProtocol.AncMode.NOISE_CANCELLING, d.value)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ===== 游戏模式 =====
            SectionCard(title = "游戏模式") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .miuixClick(onClick = {
                            gameMode = !gameMode
                            controller.sendGameMode(gameMode)
                        }),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "降低延迟，声音响应更快速",
                        modifier = Modifier.weight(1f),
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    SuperSwitchCompat(checked = gameMode, onCheckedChange = {
                        gameMode = it
                        controller.sendGameMode(it)
                    })
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    val cardColor = if (isSystemInDarkTheme()) Color(0xFF1A1A1A) else Color(0xFFFFFFFF)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(cardColor, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun DeviceRow(device: BluetoothDevice, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .miuixClick(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(device.name ?: "未知设备", fontSize = 15.sp)
            Text(device.address, fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        if (selected) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(Color(0xFF4CAF50), RoundedCornerShape(5.dp))
            )
        }
    }
}

@Composable
private fun BatteryItem(label: String, value: Int?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value?.let { "$it%" } ?: "--",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (value != null) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun ModeButton(title: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg = if (selected) MiuixTheme.colorScheme.primary else
        if (isSystemInDarkTheme()) Color(0xFF2A2A2A) else Color(0xFFF2F2F7)
    val fg = if (selected) Color.White else MiuixTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .height(44.dp)
            .background(bg, RoundedCornerShape(14.dp))
            .miuixClick(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(title, color = fg, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun DepthButton(title: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg = if (selected) MiuixTheme.colorScheme.surfaceContainer else
        if (isSystemInDarkTheme()) Color(0xFF2A2A2A) else Color(0xFFF2F2F7)
    Box(
        modifier = modifier
            .height(36.dp)
            .background(bg, RoundedCornerShape(12.dp))
            .miuixClick(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(title, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SuperSwitchCompat(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val trackOn = MiuixTheme.colorScheme.primary
    val trackOff = if (isSystemInDarkTheme()) Color(0xFF3A3A3A) else Color(0xFFE5E5EA)
    Box(
        modifier = Modifier
            .size(width = 50.dp, height = 30.dp)
            .background(if (checked) trackOn else trackOff, RoundedCornerShape(15.dp))
            .miuixClick(onClick = { onCheckedChange(!checked) }),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .padding(3.dp)
                .size(24.dp)
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .background(Color.White, RoundedCornerShape(12.dp))
        )
    }
}

private fun hasBluetoothPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

/** Miuix 按压反馈 + 点击处理（Miuix 0.9.3: pressable 仅视觉反馈，点击由 clickable 处理） */
@Composable
private fun Modifier.miuixClick(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this
        .pressable(interactionSource = interactionSource, indication = SinkFeedback())
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
}

private fun refreshDevices(context: Context): List<BluetoothDevice>? {
    val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
    if (!hasBluetoothPermission(context)) return null
    return ZirconRfcommController.findZirconDevices(adapter)
}
