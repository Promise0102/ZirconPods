package local.roselink.lite;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Classic Bluetooth RFCOMM transport recovered from ZirconDeviceConfig and EarObject.toMap. */
public final class BluetoothController {
    public interface Listener {
        void onState(DeviceState state);
        void onDevices(List<DeviceRecord> devices);
        void onLog(String line);
    }
    public static final class DeviceRecord {
        public String name, address;
        public int rssi;
        public boolean bonded;
    }
    static final UUID CONTROL_UUID = UUID.fromString("0cf12d31-fac3-4553-bd80-d6832e7b3931");
    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    private final BluetoothAdapter adapter;
    private final DeviceState state = new DeviceState();
    private final Map<String, DeviceRecord> devices = new LinkedHashMap<>();
    private volatile BluetoothSocket socket;
    private volatile OutputStream output;
    private volatile int generation;
    private volatile boolean closed;
    private int sequence;
    private int scanGeneration;
    private boolean registered;
    private boolean refreshing;
    private long lastReply;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (closed) return;
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (d != null) addDevice(d, intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, (short) 0));
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                if (!state.connected && socket == null) { state.status = "扫描结束，点击设备连接"; emit(); }
                log("扫描结束，共 " + devices.size() + " 台设备");
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_OFF) {
                    disconnectWithStatus("蓝牙已关闭");
                }
            }
        }
    };

    public BluetoothController(Context context, Listener listener) {
        this.context = context; this.listener = listener;
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager == null ? null : manager.getAdapter();
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        // Bluetooth discovery broadcasts come from the privileged Bluetooth stack, not this app.
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        else context.registerReceiver(receiver, filter);
        registered = true;
        main.post(this::emit);
    }

    public static ControlSpec[] controls() { return ZirconProtocol.controls(); }

    public void scan() {
        if (closed) return;
        if (state.connecting) { log("正在连接耳机，请连接完成后再扫描"); return; }
        if (adapter == null) { status("这台设备不支持蓝牙"); return; }
        try {
            if (!adapter.isEnabled()) { status("请先开启蓝牙"); return; }
            if (adapter.isDiscovering()) adapter.cancelDiscovery();
            final int scanSession = ++scanGeneration;
            devices.clear();
            for (BluetoothDevice d : adapter.getBondedDevices()) addDevice(d, 0);
            publishDevices();
            if (adapter.startDiscovery()) {
                if (!state.connected) status("正在扫描，已配对设备可直接连接");
                log("开始扫描；锆势优先显示。可先在系统蓝牙设置完成音频配对。");
            } else status("系统未开始扫描，请稍后重试或在系统蓝牙设置中配对");
            main.postDelayed(() -> {
                try { if (!closed && scanSession == scanGeneration && adapter.isDiscovering()) adapter.cancelDiscovery(); }
                catch (SecurityException e) { log("扫描权限已撤销"); }
            }, 14000);
        } catch (SecurityException e) { status("需要附近设备权限才能扫描"); }
    }

    private void addDevice(BluetoothDevice d, int rssi) {
        try {
            String name = d.getName();
            if (name == null || name.trim().isEmpty()) return;
            DeviceRecord record = new DeviceRecord();
            record.name = name; record.address = d.getAddress(); record.rssi = rssi;
            record.bonded = d.getBondState() == BluetoothDevice.BOND_BONDED;
            devices.put(record.address, record); publishDevices();
        } catch (SecurityException ignored) { }
    }

    private static boolean isZircon(DeviceRecord d) {
        String n = d.name.toLowerCase(Locale.ROOT);
        return n.contains("zircon") || n.contains("锆势");
    }

    private void publishDevices() {
        List<DeviceRecord> list = new ArrayList<>(devices.values());
        list.sort(Comparator.comparing((DeviceRecord d) -> !isZircon(d))
                .thenComparing(d -> !d.bonded).thenComparing(d -> d.name));
        listener.onDevices(list);
    }

    public void connect(String address) {
        if (closed || adapter == null) return;
        disconnectWithStatus("正在连接…");
        final int session = generation;
        state.address = address;
        try {
            BluetoothDevice d = adapter.getRemoteDevice(address);
            state.name = d.getName() == null ? "锆势" : d.getName();
            if (adapter.isDiscovering()) adapter.cancelDiscovery();
            final BluetoothSocket pending = d.createRfcommSocketToServiceRecord(CONTROL_UUID);
            final long timeoutMs = d.getBondState() == BluetoothDevice.BOND_BONDED ? 20000L : 90000L;
            socket = pending; state.connecting = true; emit();
            log("连接 " + state.name + "，使用锆势专用 SPP 服务");
            main.postDelayed(() -> {
                if (session == generation && !state.connected && !closed) {
                    disconnectWithStatus("连接超时，请保持耳机开机并关闭原版控制页面");
                }
            }, timeoutMs);
            Thread connection = new Thread(() -> {
                try {
                    pending.connect();
                    final InputStream input = pending.getInputStream();
                    final OutputStream out = pending.getOutputStream();
                    if (session != generation || closed) { pending.close(); return; }
                    main.post(() -> {
                        if (session != generation || closed || socket != pending) return;
                        output = out;
                        state.connecting = false;
                        state.connected = true;
                        state.status = "已连接，正在读取耳机状态…";
                        lastReply = 0; emit(); refresh();
                        main.postDelayed(() -> {
                            if (session == generation && state.connected && lastReply == 0) {
                                state.status = "蓝牙已连接，尚未收到协议回复"; emit();
                                log("没有收到状态回复；请导出日志用于核对固件版本。");
                            }
                        }, 6000);
                    });
                    readLoop(input, session);
                } catch (Exception e) {
                    main.post(() -> {
                        if (session == generation && !closed) {
                            disconnectWithStatus("连接中断：" + readable(e));
                        }
                    });
                }
            }, "zircon-rfcomm");
            connection.setDaemon(true); connection.start();
        } catch (SecurityException e) { disconnectWithStatus("需要附近设备权限才能连接"); }
        catch (Exception e) { disconnectWithStatus("无法连接：" + readable(e)); }
    }

    private void readLoop(InputStream input, int session) throws IOException {
        ZirconProtocol.Decoder decoder = new ZirconProtocol.Decoder();
        byte[] buffer = new byte[2048];
        while (!closed && session == generation) {
            int n = input.read(buffer);
            if (n < 0) throw new IOException("耳机关闭了连接");
            final byte[] received = java.util.Arrays.copyOf(buffer, n);
            main.post(() -> {
                if (!closed && session == generation) { eqDiagnostics.raw(received); log("RX RAW " + hex(received)); }
            });
            for (byte[] payload : decoder.accept(buffer, n)) {
                main.post(() -> {
                    if (closed || session != generation) return;
                    lastReply = android.os.SystemClock.elapsedRealtime();
                    eqDiagnostics.packet(payload);
                    log("RX " + hex(payload));
                    long previousEqRevision = state.customEqRevision;
                    if (ZirconProtocol.apply(payload, state)) state.status = "已连接 · 状态已同步";
                    if (state.customEqRevision != previousEqRevision && eqAwaitingReadback) {
                        if (customEq.bands(state.customEq)) {
                            send(CustomEqProtocol.select());
                            final long operation = eqOperation;
                            main.postDelayed(() -> {
                                if (!closed && session == generation && operation == eqOperation && state.connected)
                                    send(CustomEqProtocol.queryMode());
                            }, 400);
                        }
                    }
                    if ((payload[0] & 255) == 0x2a && (payload.length == 2 || payload.length == 3))
                        customEq.mode(payload.length == 2 ? payload[1] & 255 : ((payload[1] & 255) << 8) | (payload[2] & 255));
                    updateEqState();
                    emit();
                });
            }
        }
    }

    private final CustomEqTransaction customEq = new CustomEqTransaction();
    private final CustomEqDiagnostics eqDiagnostics = new CustomEqDiagnostics();
    private String eqDiagnosticIdentity = "尚未执行 EQ 操作";
    private long eqOperation;
    private boolean eqAwaitingReadback;

    public void queryCustomEq() {
        if (closed || !customEq.beginRead(state.connected)) return;
        final long operation = ++eqOperation;
        final int session = generation;
        eqDiagnostics.begin("只读 EQ 查询");
        eqDiagnosticIdentity = "设备：" + state.name + "\n固件：" + (state.firmware.isEmpty() ? "未读取" : state.firmware);
        eqAwaitingReadback = true;
        updateEqState(); emit();
        log("自定义 EQ：只读探测，等待十段有效回包；普通 ACK 不算支持");
        send(CustomEqProtocol.query());
        // A separate ordinary-mode query distinguishes a live control channel from complete silence.
        main.postDelayed(() -> {
            if (!closed && generation == session && eqOperation == operation && state.connected
                    && customEq.phase() == CustomEqTransaction.Phase.READING) send(CustomEqProtocol.queryMode());
        }, 1000);
        // One bounded read-only retry, never a blind write or an automatic loop.
        main.postDelayed(() -> {
            if (!closed && generation == session && eqOperation == operation && state.connected
                    && customEq.phase() == CustomEqTransaction.Phase.READING) {
                log("自定义 EQ：尚未获得曲线，重试一次只读查询"); send(CustomEqProtocol.query());
            }
        }, 3000);
        scheduleEqTimeout(operation);
    }

    public void applyCustomEq(int[] gains) {
        if (closed || !customEq.beginApply(state.connected, state.customEq, gains)) {
            log("自定义 EQ：请先连接并读取有效的十段数据，操作期间不可重复应用"); return;
        }
        final int session = generation;
        final long operation = ++eqOperation;
        eqDiagnostics.begin("EQ 写入与回读验证");
        eqDiagnosticIdentity = "设备：" + state.name + "\n固件：" + (state.firmware.isEmpty() ? "未读取" : state.firmware);
        eqAwaitingReadback = false;
        updateEqState(); emit();
        send(CustomEqProtocol.write(gains));
        log("自定义 EQ：已排队写入，随后回读；曲线核对成功后才切换自定义模式");
        main.postDelayed(() -> {
            if (closed || session != generation || operation != eqOperation || !state.connected) return;
            eqAwaitingReadback = true;
            send(CustomEqProtocol.query());
        }, 500);
        scheduleEqTimeout(operation);
    }

    private void scheduleEqTimeout(long operation) {
        final int session = generation;
        main.postDelayed(() -> {
            if (closed || generation != session || operation != eqOperation || !customEq.busy()) return;
            customEq.timeout(); updateEqState(); emit(); log("自定义 EQ：" + state.customEqStatus);
        }, 7000);
    }

    private void updateEqState() {
        state.customEqBusy = customEq.busy();
        state.customEqStatus = customEq.label();
        if (!customEq.busy()) eqDiagnostics.finish(state.customEqStatus);
        state.customEqDiagnosticSummary = eqDiagnostics.summary();
        state.customEqDiagnostics = eqDiagnosticIdentity + "\n" + eqDiagnostics.report();
    }

    public void refresh() {
        if (closed || !state.connected || refreshing) return;
        refreshing = true;
        final int session = generation;
        List<byte[]> payloads = ZirconProtocol.queryPayloads();
        int delay = 0;
        for (byte[] p : payloads) {
            main.postDelayed(() -> { if (!closed && session == generation && state.connected) send(p); }, delay);
            delay += 140;
        }
        main.postDelayed(() -> refreshing = false, delay + 200);
    }

    public void command(String key, int value) {
        if (closed || !state.connected) { log("尚未连接耳机"); return; }
        if ("eq".equals(key) && customEq.busy()) { log("正在核对自定义 EQ，请完成后再切换预设"); return; }
        try {
            byte[] payload = ZirconProtocol.payload(key, value);
            send(payload);
            log("设置已排队，等待耳机返回实际状态");
            final int session = generation;
            main.postDelayed(() -> { if (generation == session) refresh(); }, 500);
        } catch (IllegalArgumentException e) { log("无法发送：" + e.getMessage()); }
    }

    private void send(byte[] payload) {
        final int session = generation;
        final OutputStream target = output;
        if (target == null) return;
        final byte[] frame = ZirconProtocol.frame(sequence++ & 255, payload);
        writer.execute(() -> {
            if (closed || session != generation) return;
            try {
                target.write(frame); target.flush();
                main.post(() -> {
                    if (session == generation) { eqDiagnostics.sent(frame); updateEqState(); emit(); log("TX " + hex(frame)); }
                });
            } catch (IOException e) {
                main.post(() -> { if (session == generation && !closed) disconnectWithStatus("写入失败：" + readable(e)); });
            }
        });
    }

    public void disconnect() { disconnectWithStatus("已断开连接"); }

    private void disconnectWithStatus(String message) {
        generation++; refreshing = false;
        ++eqOperation; eqAwaitingReadback = false; customEq.reset();
        eqDiagnostics.finish("连接已断开：" + message); updateEqState();
        BluetoothSocket old = socket; socket = null; output = null;
        if (old != null) try { old.close(); } catch (IOException ignored) { }
        state.connected = false; state.connecting = false; state.clearReadings(); state.status = message;
        if (!closed) { emit(); log(message); }
    }

    private void status(String message) { state.status = message; emit(); log(message); }
    private void emit() { if (!closed) listener.onState(state.copy()); }
    private void log(String message) { if (!closed) listener.onLog(message); }
    private static String readable(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
    static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte v : b) { if (s.length() > 0) s.append(' '); s.append(String.format(Locale.ROOT, "%02X", v & 255)); }
        return s.toString();
    }

    public void close() {
        if (closed) return;
        disconnectWithStatus("已关闭"); closed = true;
        main.removeCallbacksAndMessages(null);
        try { if (adapter != null && adapter.isDiscovering()) adapter.cancelDiscovery(); }
        catch (SecurityException ignored) { }
        if (registered) { context.unregisterReceiver(receiver); registered = false; }
        writer.shutdownNow();
    }
}
