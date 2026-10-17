package local.roselink.lite;

/** Finite connection task, followed by five seconds of completion information. Uses monotonic time. */
final class ConnectionPopupWindow {
    static final long COMPLETION_MS = 5000;
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
        if (!state.connected) return "正在连接耳机，请留意系统配对提示";
        Integer mode = state.values.get("noise");
        return "左耳 " + battery(state.leftBattery) + " · 右耳 " + battery(state.rightBattery)
                + " · 充电盒 " + battery(state.caseBattery) + "\n当前模式："
                + NoiseModeSelection.label(mode == null ? -1 : mode);
    }
    static String chip(DeviceState state) {
        if (!state.connected) return "连接中";
        Integer mode = state.values.get("noise");
        String label = mode == null ? "已连接" : NoiseModeSelection.label(mode);
        if ("未读取".equals(label)) label = "已连接";
        String level = state.leftBattery >= 0 && state.leftBattery <= 100 ? state.leftBattery + "%" : "";
        String combined = label + level;
        return combined.length() <= 7 ? combined : label;
    }
}
