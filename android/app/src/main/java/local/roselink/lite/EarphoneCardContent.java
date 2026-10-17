package local.roselink.lite;

/** Truthful content shared by notifications, quick controls and native Live Updates. */
final class EarphoneCardContent {
    static String title(DeviceState state) {
        String name = state.name == null ? "" : state.name.trim();
        return name.isEmpty() || "锆势".equals(name) ? "ROSE Zircon" : name;
    }
    static String battery(boolean connected, int value) {
        return connected && value >= 0 && value <= 100 ? value + "%" : "—";
    }
    static String batteryLine(DeviceState state) {
        return "充电盒：" + battery(state.connected, state.caseBattery)
                + "  左：" + battery(state.connected, state.leftBattery)
                + "  右：" + battery(state.connected, state.rightBattery);
    }
    static String ancLevel(DeviceState state) {
        if (!state.connected) return "未连接";
        Integer noise = state.values.get("noise");
        if (noise == null) return "未读取";
        if (noise == 1) return "标准";
        if (noise == 6) return "极限";
        return "未开启降噪";
    }
    static boolean canSetAncLevel(DeviceState state) {
        Integer noise = state.values.get("noise");
        return state.connected && noise != null && (noise == 1 || noise == 6);
    }
}
