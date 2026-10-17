package local.roselink.lite;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import java.util.ArrayDeque;

/** UI binding only. Closing the activity does not close an active earphone session. */
final class BluetoothClient {
    private final Context context;
    private final BluetoothController.Listener listener;
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    private EarphoneService service;
    private boolean bound, closed;
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            if (closed) return;
            service = ((EarphoneService.LocalBinder) binder).service();
            service.addListener(listener);
            while (!pending.isEmpty() && !closed) {
                try { pending.removeFirst().run(); }
                catch (RuntimeException error) { listener.onLog("操作未完成：" + error.getClass().getSimpleName()); }
            }
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            service = null;
            DeviceState state = new DeviceState();
            state.status = "后台连接已停止，请重新连接耳机";
            if (!closed) listener.onState(state);
        }
    };

    BluetoothClient(Context context, BluetoothController.Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        bound = this.context.bindService(new Intent(this.context, EarphoneService.class), connection, Context.BIND_AUTO_CREATE);
        if (!bound) listener.onLog("无法绑定蓝牙服务，请重新打开应用");
    }
    private void ready(Runnable action) {
        if (closed) return;
        if (service != null) action.run();
        else if (bound && pending.size() < 16) pending.addLast(action);
        else listener.onLog("蓝牙服务尚未就绪，请重新打开应用");
    }
    void scan() { ready(() -> service.scan()); }
    void refresh() { ready(() -> service.refresh()); }
    void command(String key, int value) { ready(() -> service.command(key, value)); }
    void disconnect() { ready(() -> service.disconnect()); }
    void autoConnect() { ready(() -> AutoConnection.start(context, AutoConnection.address(context), "打开应用")); }
    void tileClick(Runnable needsPairing) { ready(() -> service.tileClick(needsPairing)); }
    void connect(String address) {
        if (closed) return;
        // Called only after the foreground activity has obtained Bluetooth permissions.
        context.startForegroundService(new Intent(context, EarphoneService.class)
                .setAction(EarphoneService.ACTION_CONNECT).putExtra(EarphoneService.EXTRA_ADDRESS, address));
    }
    void close() {
        closed = true;
        pending.clear();
        if (service != null) service.removeListener(listener);
        service = null;
        if (bound) { context.unbindService(connection); bound = false; }
    }
}
