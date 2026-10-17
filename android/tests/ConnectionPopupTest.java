package local.roselink.lite;

/** Monotonic connection lifecycle and accurate native popup content. No Android system is simulated. */
public final class ConnectionPopupTest {
    private static int checks;
    public static void main(String[] args) {
        ConnectionPopupWindow popup = new ConnectionPopupWindow();
        check(!popup.update(true, false, 10), "cached device state cannot create a popup on app reopen");
        popup.begin();
        check(popup.update(false, true, 100), "user initiated connection creates a finite task");
        check(popup.remaining(200) == -1, "connection phase has no invented progress deadline");
        check(popup.update(false, true, 30100), "real pairing can remain in progress for more than five seconds");
        check(popup.update(true, false, 30200), "connection completion starts information phase");
        check(popup.remaining(30200) == 10000, "completion lasts ten seconds");
        check(popup.update(true, false, 33000) && popup.remaining(33000) == 7200, "battery or mode reports do not extend completion window");
        check(popup.update(true, false, 40199), "completion stays visible just before timeout");
        check(!popup.update(true, false, 40200), "completion closes at its deadline");
        check(!popup.update(true, false, 45000), "late replies cannot reopen a completed popup");
        popup.begin();
        check(popup.update(true, false, 50000) && popup.remaining(50000) == 10000, "explicit reconnect starts a new generation");
        popup.cancel();
        check(!popup.update(true, false, 50100), "user dismissal cannot be undone by another device reply");
        popup.begin();
        check(!popup.update(false, false, 60000), "connection failure ends task immediately");
        popup.begin();
        popup.update(true, false, 70000);
        check(!popup.update(false, false, 70001), "disconnect removes an active completion popup");
        DeviceState state = new DeviceState();
        check(ConnectionPopupWindow.chip(state).equals("连接中"), "connecting chip does not claim connected");
        state.connected = true;
        check(ConnectionPopupWindow.text(state).contains("左：—") && ConnectionPopupWindow.text(state).contains("充电盒：—"), "unknown batteries are explicit");
        state.values.put("noise", 6);
        state.leftBattery = 100;
        check(ConnectionPopupWindow.chip(state).equals("左耳100%") && ConnectionPopupWindow.text(state).contains("100%"), "chip and card show battery instead of controls");
        state.leftBattery = 150;
        check(ConnectionPopupWindow.chip(state).equals("已连接") && ConnectionPopupWindow.battery(150).equals("—"), "invalid battery never appears as a valid percentage");
        state.values.put("noise", 99);
        check(ConnectionPopupWindow.chip(state).equals("已连接"), "unknown mode does not impersonate normal mode");
        System.out.println("PASS: " + checks + " live-connection lifecycle/content checks (host only; native promotion untested)");
    }
    private static void check(boolean condition, String label) { checks++; if (!condition) throw new AssertionError(label); }
}
