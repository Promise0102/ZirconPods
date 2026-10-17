package local.roselink.lite;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import java.util.List;

/** A system Quick Settings tile. Binding to listen never starts a Bluetooth connection. */
public final class EarphoneModeTile extends TileService implements BluetoothController.Listener {
    private BluetoothClient client;
    private DeviceState state = new DeviceState();
    private boolean listening;
    static void requestUpdate(Context context) {
        try { requestListeningState(context, new ComponentName(context, EarphoneModeTile.class)); }
        catch (RuntimeException ignored) { /* System may not have added this tile yet. */ }
    }
    @Override public void onStartListening() {
        super.onStartListening();
        listening = true;
        if (client == null) client = new BluetoothClient(this, this);
        paint();
    }
    @Override public void onStopListening() {
        listening = false;
        if (client != null) { client.close(); client = null; }
        state = new DeviceState();
        super.onStopListening();
    }
    @Override public void onClick() {
        super.onClick();
        if (isLocked()) unlockAndRun(this::clickUnlocked); else clickUnlocked();
    }
    private void clickUnlocked() {
        if (!listening) return;
        if (client == null) client = new BluetoothClient(this, this);
        // Re-evaluate inside the owning service; a stale tile can never target a new session.
        client.tileClick(() -> {
            if (!listening) return;
            Intent intent = new Intent(this, MainActivity.class).setAction("local.roselink.lite.OPEN_PAIRING")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 91, intent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            else startActivityAndCollapse(intent);
        });
    }
    private void paint() {
        if (!listening || getQsTile() == null) return;
        Tile tile = getQsTile();
        Integer mode = state.values.get("noise");
        String subtitle = state.connected ? NoiseModeSelection.label(mode == null ? -1 : mode)
                : state.connecting ? "连接中" : "点击连接";
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_mode_tile));
        tile.setLabel("耳机模式");
        if (Build.VERSION.SDK_INT >= 29) tile.setSubtitle(subtitle);
        tile.setContentDescription("ZirconPods 耳机模式，" + subtitle + "；点击循环：降噪、风噪、通透、普通");
        tile.setState(state.connecting ? Tile.STATE_UNAVAILABLE
                : state.connected && mode != null && (mode == 1 || mode == 3 || mode == 4 || mode == 6)
                ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }
    @Override public void onState(DeviceState next) { state = next.copy(); paint(); }
    @Override public void onDevices(List<BluetoothController.DeviceRecord> devices) { }
    @Override public void onLog(String line) { }
    @Override public void onDestroy() {
        listening = false;
        if (client != null) { client.close(); client = null; }
        super.onDestroy();
    }
}
