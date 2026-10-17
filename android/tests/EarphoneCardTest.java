package local.roselink.lite;

/** Shared card content and native popup boundary checks, without inventing device or Android state. */
public final class EarphoneCardTest {
    private static int checks;
    public static void main(String[] args) {
        DeviceState state = new DeviceState();
        check("ROSE Zircon".equals(EarphoneCardContent.title(state)), "Zircon title does not copy the reference's i7 model");
        state.name = null;
        check("ROSE Zircon".equals(EarphoneCardContent.title(state)), "missing Bluetooth name has a real model fallback");
        state.name = "  My Zircon  ";
        check("My Zircon".equals(EarphoneCardContent.title(state)), "actual Bluetooth device name is retained");
        state.leftBattery = 53; state.rightBattery = 55; state.caseBattery = 75;
        check(!EarphoneCardContent.batteryLine(state).contains("53%"), "disconnected snapshot cannot expose stale battery values");
        state.connected = true;
        check("充电盒：75%  左：53%  右：55%".equals(EarphoneCardContent.batteryLine(state)), "card uses case-left-right reference order and actual values");
        check("充电盒75%".equals(ConnectionPopupWindow.chip(state)), "native capsule prioritizes case battery");
        check(ConnectionPopupWindow.text(state).equals(EarphoneCardContent.batteryLine(state)), "native and compact cards share their battery text");
        check(!ConnectionPopupWindow.text(state).contains("模式"), "native popup has no mode-control content");
        check("0%".equals(EarphoneCardContent.battery(true, 0)), "empty battery is valid");
        check("100%".equals(EarphoneCardContent.battery(true, 100)), "full battery is valid");
        check("—".equals(EarphoneCardContent.battery(true, 101)) && "—".equals(EarphoneCardContent.battery(true, -1)), "invalid and unknown battery values remain unknown");
        check(!EarphoneCardContent.canSetAncLevel(state) && "未读取".equals(EarphoneCardContent.ancLevel(state)), "unread ANC cannot enable a level selector");
        ZirconProtocol.apply(new byte[]{9, 1}, state);
        check(EarphoneCardContent.canSetAncLevel(state) && "标准".equals(EarphoneCardContent.ancLevel(state)), "standard ANC comes from a verified noise reply");
        ZirconProtocol.apply(new byte[]{9, 6}, state);
        check(EarphoneCardContent.canSetAncLevel(state) && "极限".equals(EarphoneCardContent.ancLevel(state)), "extreme ANC comes from a verified noise reply");
        for (int mode : new int[]{2, 3, 4, 99}) {
            state.values.put("noise", mode);
            check(!EarphoneCardContent.canSetAncLevel(state) && "未开启降噪".equals(EarphoneCardContent.ancLevel(state)), "level is unavailable outside known ANC mode " + mode);
        }
        state.connected = false;
        check("未连接".equals(EarphoneCardContent.ancLevel(state)) && !EarphoneCardContent.canSetAncLevel(state), "disconnect disables levels");
        ConnectionPopupWindow window = new ConnectionPopupWindow();
        window.begin();
        check(window.update(true, false, 100) && window.remaining(5100) == 5000, "popup stays active after the old five-second deadline");
        check(window.update(true, false, 10099) && window.remaining(10099) == 1, "ten-second window preserves its last millisecond");
        check(!window.update(true, false, 10100), "popup ends at exactly ten seconds");
        check(!window.update(true, false, 10101), "late battery reply cannot revive completed popup");
        System.out.println("PASS: " + checks + " shared notification card checks (host only)");
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
