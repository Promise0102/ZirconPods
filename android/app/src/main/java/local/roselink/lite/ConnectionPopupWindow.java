package local.roselink.lite;

/** Finite connection task, followed by ten seconds of completion information. Uses monotonic time. */
final class ConnectionPopupWindow {
    static final long COMPLETION_MS = 10000;
    private boolean active;
    private long completedAt = -1;
    void begin() { active = true; completedAt = -1; }
    void cancel() { active = false; }
    boolean update(boolean connected, boolean connecting, long now) {
        if (!active) return false;
        if (!connected && !connecting) { cancel(); return false; }
        if (connected && completedAt < 0) completedAt = now;
        if (completedAt >= 0 && now - completedAt >= COMPLETION_MS) cancel();
        return active;
    }
    long remaining(long now) {
        return !active || completedAt < 0 ? -1 : Math.max(0, COMPLETION_MS - (now - completedAt));
    }
    static String battery(int value) { return value >= 0 && value <= 100 ? value + "%" : "—"; }
    static String text(DeviceState state) {
        String batteries = EarphoneCardContent.batteryLine(state);
        return state.connected ? batteries : batteries + "\n正在连接耳机，请留意系统配对提示";
    }
    static String chip(DeviceState state) {
        if (!state.connected) return "连接中";
        if (state.caseBattery >= 0 && state.caseBattery <= 100) return "充电盒" + state.caseBattery + "%";
        if (state.leftBattery >= 0 && state.leftBattery <= 100) return "左耳" + state.leftBattery + "%";
        return "已连接";
    }
}
