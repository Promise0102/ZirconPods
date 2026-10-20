package local.roselink.lite;

import java.util.HashMap;
import java.util.Map;

/** Device-confirmed values; -1/absence means unknown, never a simulated default. */
public final class DeviceState {
    public String name = "锆势", address = "", firmware = "", status = "未连接";
    public boolean connected;
    public boolean connecting;
    public int leftBattery = -1, rightBattery = -1, caseBattery = -1;
    public Map<String, Integer> values = new HashMap<>();
    public int[] customEq;
    public long customEqRevision;
    public boolean customEqBusy;
    public String customEqStatus = "锆势固件兼容性待验证，请先读取耳机 EQ";
    public String customEqDiagnosticSummary = "尚无本次 EQ 诊断";
    public String customEqDiagnostics = "尚无本次 EQ 诊断";

    public DeviceState copy() {
        DeviceState s = new DeviceState();
        s.name = name; s.address = address; s.firmware = firmware; s.status = status;
        s.connected = connected; s.connecting = connecting;
        s.leftBattery = leftBattery; s.rightBattery = rightBattery; s.caseBattery = caseBattery;
        s.values.putAll(values);
        s.customEq = customEq == null ? null : customEq.clone();
        s.customEqRevision = customEqRevision;
        s.customEqBusy = customEqBusy; s.customEqStatus = customEqStatus;
        s.customEqDiagnosticSummary = customEqDiagnosticSummary; s.customEqDiagnostics = customEqDiagnostics;
        return s;
    }

    public void clearReadings() {
        leftBattery = rightBattery = caseBattery = -1;
        firmware = "";
        values.clear();
        customEq = null; customEqRevision = 0; customEqBusy = false;
        customEqStatus = "锆势固件兼容性待验证，请先读取耳机 EQ";
    }
}
