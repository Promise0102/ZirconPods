package local.roselink.lite;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Only protected system Bluetooth events for the exact remembered device are accepted. */
public final class BluetoothConnectionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !AutoConnection.permitted(context)) return;
        String action = intent.getAction();
        if (!BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)
                && !BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)
                && !BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED.equals(action)) return;
        try {
            @SuppressWarnings("deprecation")
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (device == null || !AutoConnectPolicy.matches(AutoConnection.address(context), device.getAddress())) return;
            if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                AutoConnection.physicalDisconnect(context, device.getAddress());
            } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)
                    || intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1) == BluetoothProfile.STATE_CONNECTED) {
                AutoConnection.physicalConnect(context);
                AutoConnection.start(context, device.getAddress(), "耳机已连上手机");
            }
        } catch (RuntimeException ignored) {
            // Revoked Bluetooth permissions must not crash a system broadcast receiver.
        }
    }
}
