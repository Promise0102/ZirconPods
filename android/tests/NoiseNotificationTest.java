package local.roselink.lite;

/** Host checks for notification requests flowing through the actual protocol/state model. */
public final class NoiseNotificationTest {
    private static int checks;
    public static void main(String[] args) {
        NoiseModeSelection modes = new NoiseModeSelection();
        DeviceState state = new DeviceState();
        check(modes.confirmed() == -1, "startup has no invented default mode");
        check(!modes.request(false, 1), "disconnected action cannot queue a later mode switch");
        check(!modes.request(true, -1) && !modes.request(true, 0) && !modes.request(true, 5)
                && !modes.request(true, Integer.MAX_VALUE), "invalid intent values rejected");
        check(modes.requested() == -1, "rejected action leaves no pending request");
        state.connected = true;
        ZirconProtocol.apply(new byte[]{9, 2}, state);
        modes.report(state.connected, state.values.get("noise"));
        check(modes.confirmed() == 2, "normal reply establishes the selected mode");
        check(modes.request(true, 1), "connected ANC action accepted");
        check(modes.confirmed() == 2 && modes.requested() == 1, "click keeps actual normal selected while ANC is pending");
        ZirconProtocol.apply(new byte[]{(byte)0xfe, 0}, state);
        modes.report(state.connected, state.values.get("noise"));
        check(modes.confirmed() == 2 && modes.requested() == 1, "ACK does not highlight requested mode");
        ZirconProtocol.apply(new byte[]{12, 90, 80, 70}, state);
        modes.report(state.connected, state.values.get("noise"));
        check(modes.requested() == 1, "battery update cannot confirm pending mode");
        ZirconProtocol.apply(new byte[]{9, 1}, state);
        modes.report(state.connected, state.values.get("noise"));
        check(modes.confirmed() == 1 && modes.requested() == -1, "ANC status reply selects ANC and completes request");
        modes.request(true, 4);
        modes.request(true, 3);
        ZirconProtocol.apply(new byte[]{9, 4}, state);
        modes.report(state.connected, state.values.get("noise"));
        check(modes.confirmed() == 4 && modes.requested() == 3, "late wind reply does not complete newer transparency request");
        ZirconProtocol.apply(new byte[]{9, 3}, state);
        modes.report(state.connected, state.values.get("noise"));
        check(modes.confirmed() == 3 && modes.requested() == -1, "newer transparency reply wins");
        modes.request(true, 2);
        modes.expire();
        check(modes.confirmed() == 3 && modes.requested() == -1, "timeout preserves confirmed selection");
        modes.request(true, 1);
        modes.report(false, state.values.get("noise"));
        check(modes.confirmed() == -1 && modes.requested() == -1, "disconnect clears both cached highlight and pending command");
        state.clearReadings();
        modes.report(true, state.values.get("noise"));
        check(modes.confirmed() == -1, "new connection does not reuse old earphone state");
        modes.report(true, 99);
        check(modes.confirmed() == -1, "unknown device mode has no selected button");
        modes.report(true, 6);
        check(modes.confirmed() == 6 && "极限降噪".equals(NoiseModeSelection.label(modes.confirmed())), "laboratory extreme mode remains explicit in notification status");
        check(!modes.request(true, 6), "four-button notification cannot request unsupported fifth shortcut");
        int[] wireModes = {1, 4, 3, 2};
        for (int mode : wireModes) {
            byte[] payload = ZirconProtocol.payload("noise", mode);
            check(payload.length == 2 && payload[0] == 9 && payload[1] == mode, "notification mode maps to noise command " + mode);
        }
        System.out.println("PASS: " + checks + " notification state checks (host only; Android runtime untested)");
    }
    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
}
