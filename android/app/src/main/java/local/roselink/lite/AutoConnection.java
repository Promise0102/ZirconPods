package local.roselink.lite;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

/** Remembers only successful connections. Never guesses by Bluetooth name. */
final class AutoConnection {
    private static final String PREFS = "auto_connection";
    static volatile boolean busy;
    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
    static boolean enabled(Context context) { return prefs(context).getBoolean("enabled", true); }
    static String address(Context context) { return prefs(context).getString("address", ""); }
    static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean("enabled", enabled).putBoolean("paused", false).putBoolean("disconnected", false)
                .remove("last_attempt").apply();
    }
    static void remember(Context context, String address) {
        if (!AutoConnectPolicy.matches(address, address)) return;
        prefs(context).edit().putString("address", address).putBoolean("paused", false).apply();
    }
    static void pause(Context context) {
        prefs(context).edit().putBoolean("paused", true).putBoolean("disconnected", false).apply();
    }
    static void resumeManually(Context context) { prefs(context).edit().putBoolean("paused", false).apply(); }
    static void physicalDisconnect(Context context, String address) {
        if (AutoConnectPolicy.matches(address(context), address)) {
            prefs(context).edit().putBoolean("disconnected", true).remove("last_attempt").apply();
        }
    }
    static void physicalConnect(Context context) {
        if (prefs(context).getBoolean("disconnected", false))
            prefs(context).edit().putBoolean("paused", false).putBoolean("disconnected", false).apply();
    }
    static boolean permitted(Context context) {
        return Build.VERSION.SDK_INT < 31 || (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED && context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                == PackageManager.PERMISSION_GRANTED);
    }
    static boolean available(Context context, String candidate) {
        if (!permitted(context) || !AutoConnectPolicy.matches(candidate, candidate)) return false;
        try {
            BluetoothManager manager = context.getSystemService(BluetoothManager.class);
            return manager != null && manager.getAdapter() != null && manager.getAdapter().isEnabled()
                    && manager.getAdapter().getRemoteDevice(candidate).getBondState() == BluetoothDevice.BOND_BONDED;
        } catch (RuntimeException ignored) { return false; }
    }
    static boolean eligible(Context context, String candidate) {
        return enabled(context) && !prefs(context).getBoolean("paused", false)
                && AutoConnectPolicy.matches(address(context), candidate);
    }
    static String status(Context context) {
        SharedPreferences settings = prefs(context);
        if (address(context).isEmpty()) return "首次手动连接成功后，会记住这副耳机";
        if (!enabled(context)) return "自动连接已关闭";
        if (settings.getBoolean("paused", false)) return "已手动断开；下次耳机重新连接手机或手动连接后恢复";
        return "已记住上次成功连接的耳机 · " + settings.getString("result", "等待连接触发");
    }
    static boolean start(Context context, String candidate, String reason) {
        SharedPreferences settings = prefs(context);
        long now = System.currentTimeMillis();
        if (!AutoConnectPolicy.allow(enabled(context), settings.getBoolean("paused", false), address(context),
                candidate, busy, now, settings.getLong("last_attempt", 0)) || !permitted(context)) return false;
        try {
            if (!available(context, candidate)) return false;
            settings.edit().putLong("last_attempt", now).putString("result", reason + "，正在尝试连接").apply();
            context.startForegroundService(new Intent(context, EarphoneService.class)
                    .setAction(EarphoneService.ACTION_CONNECT).putExtra(EarphoneService.EXTRA_ADDRESS, candidate)
                    .putExtra(EarphoneService.EXTRA_AUTOMATIC, true));
            return true;
        } catch (RuntimeException error) {
            settings.edit().putString("result", "系统暂未允许后台连接，请打开应用或允许后台运行（"
                    + error.getClass().getSimpleName() + "）").apply();
            return false;
        }
    }
    static void result(Context context, boolean connected) {
        prefs(context).edit().putString("result", connected ? "控制连接已建立" : "本次连接已结束；等待下次触发").apply();
    }
}
