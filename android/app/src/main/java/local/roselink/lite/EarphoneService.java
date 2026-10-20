package local.roselink.lite;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Toast;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** The foreground service owns the RFCOMM socket, including when no activity is visible. */
public final class EarphoneService extends Service implements BluetoothController.Listener {
    static final String CHANNEL = "earphone_controls";
    static final String ACTION_CONNECT = "local.roselink.lite.CONNECT";
    static final String ACTION_MODE = "local.roselink.lite.MODE";
    static final String ACTION_DISCONNECT = "local.roselink.lite.DISCONNECT";
    static final String EXTRA_ADDRESS = "address", EXTRA_MODE = "mode";
    static final String EXTRA_AUTOMATIC = "automatic";
    private static final int NOTIFICATION_ID = 77;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<BluetoothController.Listener> listeners = new ArrayList<>();
    private final ArrayDeque<String> logs = new ArrayDeque<>();
    private final NoiseModeSelection modes = new NoiseModeSelection();
    private final LocalBinder binder = new LocalBinder();
    private List<BluetoothController.DeviceRecord> devices = new ArrayList<>();
    private DeviceState state = new DeviceState();
    private BluetoothController controller;
    private NoiseNotification notification;
    private LiveConnectionNotification liveConnection;
    private android.content.SharedPreferences appearance;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener appearanceListener = (prefs, key) -> {
        if ("product_color".equals(key)) scheduleNotification();
    };
    private boolean foreground, launchingConnection, destroying, notificationScheduled;
    private final Runnable updateNotification = () -> {
        notificationScheduled = false;
        if (foreground && !destroying) {
            getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, buildNotification());
            liveConnection.update(state);
        }
    };
    private final Runnable expireRequest = () -> {
        if (modes.requested() != NoiseModeSelection.UNKNOWN) {
            onLog("通知栏切换尚未获得耳机确认；显示最后一次已确认的模式");
            modes.expire();
            scheduleNotification();
        }
    };

    public final class LocalBinder extends Binder {
        EarphoneService service() { return EarphoneService.this; }
    }
    @Override public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel(CHANNEL, "耳机模式控制", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("连接耳机后，在通知栏切换降噪、风噪、通透和普通模式");
        channel.setShowBadge(false);
        channel.setSound(null, null);
        channel.enableVibration(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        notification = new NoiseNotification(this);
        liveConnection = new LiveConnectionNotification(this, this::onLog);
        controller = new BluetoothController(this, this);
        appearance = getSharedPreferences("appearance", MODE_PRIVATE);
        appearance.registerOnSharedPreferenceChangeListener(appearanceListener);
    }
    @Override public IBinder onBind(Intent intent) { return binder; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_CONNECT.equals(action)) {
            String address = intent.getStringExtra(EXTRA_ADDRESS);
            boolean automatic = intent.getBooleanExtra(EXTRA_AUTOMATIC, false);
            // Duplicate ACL/A2DP events must never replace an established or in-flight session.
            if ((automatic && (state.connected || state.connecting))
                    || ((state.connected || state.connecting) && AutoConnectPolicy.matches(state.address, address)))
                return START_NOT_STICKY;
            launchingConnection = true;
            AutoConnection.busy = true;
            try {
                // Must enter the foreground before opening the potentially slow Bluetooth socket.
                state.connecting = true;
                state.status = "正在连接…";
                modes.report(false, null);
                startForeground(NOTIFICATION_ID, buildNotification());
                foreground = true;
                // Recheck settings after dispatch, including cancellation while Android starts us.
                if (automatic && !AutoConnection.eligible(this, address)) {
                    controller.disconnect();
                    return START_NOT_STICKY;
                }
                if (!automatic) AutoConnection.resumeManually(this);
                onLog(automatic ? "自动连接上次成功连接的耳机" : "手动连接耳机");
                DeviceState pendingConnection = new DeviceState();
                pendingConnection.connecting = true;
                liveConnection.begin(pendingConnection);
                controller.connect(address);
            } catch (RuntimeException error) {
                onLog("无法启动后台连接：" + error.getClass().getSimpleName());
                controller.disconnect();
                if (!automatic) Toast.makeText(this, "无法启动后台连接，请检查附近设备权限后重试", Toast.LENGTH_LONG).show();
            } finally {
                launchingConnection = false;
                AutoConnection.busy = state.connected || state.connecting;
                finishIfDisconnected();
            }
        } else if (ACTION_MODE.equals(action)) {
            command("noise", intent.getIntExtra(EXTRA_MODE, NoiseModeSelection.UNKNOWN));
            finishIfDisconnected();
        } else if (ACTION_DISCONNECT.equals(action)) {
            disconnect();
        } else if (LiveConnectionNotification.ACTION_DISMISS.equals(action)) {
            liveConnection.cancel();
            finishIfDisconnected();
        } else finishIfDisconnected();
        // Reconnection comes only from an explicit action or a fresh Bluetooth/app-open event.
        return START_NOT_STICKY;
    }
    void addListener(BluetoothController.Listener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
        listener.onState(state.copy());
        listener.onDevices(new ArrayList<>(devices));
        for (String line : new ArrayList<>(logs)) listener.onLog(line);
    }
    void removeListener(BluetoothController.Listener listener) { listeners.remove(listener); }
    void scan() { controller.scan(); }
    void refresh() { controller.refresh(); }
    void disconnect() {
        AutoConnection.pause(this);
        controller.disconnect();
    }
    void tileClick(Runnable needsPairing) {
        if (destroying || state.connecting) return;
        if (!state.connected) {
            String remembered = AutoConnection.address(this);
            if (!AutoConnection.available(this, remembered)) { needsPairing.run(); return; }
            startForegroundService(new Intent(this, EarphoneService.class).setAction(ACTION_CONNECT)
                    .putExtra(EXTRA_ADDRESS, remembered));
            return;
        }
        int next = NoiseModeCycle.next(state.connected, state.values.get("noise"),
                modes.requested() != NoiseModeSelection.UNKNOWN);
        if (next == NoiseModeSelection.UNKNOWN) {
            if (modes.requested() == NoiseModeSelection.UNKNOWN) controller.refresh();
            return;
        }
        command("noise", next);
    }
    void queryCustomEq() { if (!destroying) controller.queryCustomEq(); }
    void applyCustomEq(int[] gains) { if (!destroying) controller.applyCustomEq(gains); }
    void command(String key, int value) {
        if (destroying || !state.connected) return;
        if ("noise".equals(key)) {
            if (!modes.request(true, value)) return;
            main.removeCallbacks(expireRequest);
            main.postDelayed(expireRequest, 5000);
            scheduleNotification();
        }
        controller.command(key, value);
    }
    @Override public void onState(DeviceState next) {
        if (destroying) return;
        boolean wasConnected = state.connected;
        boolean wasConnecting = state.connecting;
        boolean tileChanged = state.connected != next.connected || state.connecting != next.connecting
                || !java.util.Objects.equals(state.values.get("noise"), next.values.get("noise"));
        state = next.copy();
        AutoConnection.busy = launchingConnection || state.connected || state.connecting;
        if (state.connected && !wasConnected) {
            AutoConnection.remember(this, state.address);
            AutoConnection.result(this, true);
        } else if ((wasConnected || wasConnecting) && !state.connected && !state.connecting && !launchingConnection)
            AutoConnection.result(this, false);
        modes.report(state.connected, state.values.get("noise"));
        if (modes.requested() == NoiseModeSelection.UNKNOWN) main.removeCallbacks(expireRequest);
        for (BluetoothController.Listener listener : new ArrayList<>(listeners)) listener.onState(state.copy());
        if (tileChanged) EarphoneModeTile.requestUpdate(this);
        if (foreground) {
            if (state.connected || state.connecting || launchingConnection) scheduleNotification();
            else finishIfDisconnected();
        }
    }
    @Override public void onDevices(List<BluetoothController.DeviceRecord> records) {
        devices = new ArrayList<>(records);
        for (BluetoothController.Listener listener : new ArrayList<>(listeners)) listener.onDevices(new ArrayList<>(devices));
    }
    @Override public void onLog(String line) {
        if (destroying || line == null) return;
        if (line.length() > 1200) line = line.substring(0, 1200) + "…";
        logs.addLast(line);
        while (logs.size() > 80) logs.removeFirst();
        for (BluetoothController.Listener listener : new ArrayList<>(listeners)) listener.onLog(line);
    }
    private Notification buildNotification() { return notification.build(state, modes); }
    private void scheduleNotification() {
        if (foreground && !notificationScheduled) {
            notificationScheduled = true;
            main.postDelayed(updateNotification, 100);
        }
    }
    private void finishIfDisconnected() {
        if (launchingConnection || state.connected || state.connecting) return;
        main.removeCallbacks(updateNotification);
        notificationScheduled = false;
        main.removeCallbacks(expireRequest);
        if (liveConnection != null) liveConnection.cancel();
        if (foreground) { stopForeground(STOP_FOREGROUND_REMOVE); foreground = false; }
        stopSelf(); // While the UI is bound, the idle service remains available for scanning.
    }
    @Override public void onDestroy() {
        destroying = true;
        if (appearance != null) appearance.unregisterOnSharedPreferenceChangeListener(appearanceListener);
        AutoConnection.busy = false;
        main.removeCallbacksAndMessages(null);
        listeners.clear();
        if (liveConnection != null) liveConnection.close();
        if (controller != null) controller.close();
        if (foreground) stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
}
