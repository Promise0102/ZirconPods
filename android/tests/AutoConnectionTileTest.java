package local.roselink.lite;

public final class AutoConnectionTileTest {
    private static int checks;
    public static void main(String[] args) {
        String target = "AA:BB:CC:DD:EE:01", other = "AA:BB:CC:DD:EE:02";
        check(AutoConnectPolicy.allow(true, false, target, target, false, 100_000, 0), "first event allowed");
        check(!AutoConnectPolicy.allow(false, false, target, target, false, 100_000, 0), "disabled stops app and broadcast triggers");
        check(!AutoConnectPolicy.allow(true, true, target, target, false, 100_000, 0), "manual disconnect pause survives app-open");
        check(!AutoConnectPolicy.allow(true, false, target, other, false, 100_000, 0), "another device cannot trigger connection");
        check(!AutoConnectPolicy.allow(true, false, "", target, false, 100_000, 0), "no remembered device requires manual pairing");
        check(!AutoConnectPolicy.allow(true, false, null, null, false, 100_000, 0), "null addresses rejected");
        check(!AutoConnectPolicy.allow(true, false, "Zircon", "Zircon", false, 100_000, 0), "a matching device name is insufficient");
        check(AutoConnectPolicy.matches(target, target.toLowerCase()), "MAC comparison ignores case");
        check(!AutoConnectPolicy.allow(true, false, target, target, true, 100_000, 0), "active connection blocks duplicate events");
        check(!AutoConnectPolicy.allow(true, false, target, target, false, 100_001, 100_000), "ACL then A2DP are deduplicated");
        check(!AutoConnectPolicy.allow(true, false, target, target, false, 129_999, 100_000), "cooldown boundary guarded");
        check(AutoConnectPolicy.allow(true, false, target, target, false, 130_000, 100_000), "later fresh trigger allowed");
        check(AutoConnectPolicy.allow(true, false, target, target, false, 90_000, 100_000), "clock rollback does not lock out forever");

        DeviceState state = new DeviceState();
        NoiseModeSelection selection = new NoiseModeSelection();
        check(NoiseModeCycle.next(false, 2, false) == -1, "offline tile cannot queue a mode for reconnection");
        check(NoiseModeCycle.next(true, null, false) == -1, "unknown mode requires refresh");
        check(NoiseModeCycle.next(true, 99, false) == -1, "unexpected firmware mode cannot select a default");
        state.connected = true;
        int[] confirmed = {2, 1, 4, 3, 2};
        for (int i = 0; i < 4; i++) {
            ZirconProtocol.apply(new byte[]{9, (byte)confirmed[i]}, state);
            selection.report(true, state.values.get("noise"));
            int next = NoiseModeCycle.next(true, state.values.get("noise"), selection.requested() != -1);
            check(next == confirmed[i + 1], "cycle advances from actual reply " + confirmed[i]);
            check(selection.request(true, next), "tile request accepted");
            check(selection.confirmed() == confirmed[i], "tile request does not invent confirmed state");
            check(NoiseModeCycle.next(true, state.values.get("noise"), selection.requested() != -1) == -1,
                    "rapid second tap cannot advance while pending");
            byte[] payload = ZirconProtocol.payload("noise", next);
            check(payload[0] == 9 && payload[1] == next, "tile uses the recovered noise command");
            ZirconProtocol.apply(new byte[]{9, (byte)next}, state);
            selection.report(true, state.values.get("noise"));
            check(selection.requested() == -1, "real mode reply releases pending guard");
        }
        check(NoiseModeCycle.next(true, 6, false) == 1, "extreme ANC exits to standard ANC before cycle");
        selection.request(true, 4);
        selection.expire();
        check(NoiseModeCycle.next(true, 2, selection.requested() != -1) == 1, "timeout retries from confirmed normal");
        state.clearReadings();
        check(NoiseModeCycle.next(true, state.values.get("noise"), false) == -1, "new session never reuses previous mode");
        System.out.println("PASS: " + checks + " automatic connection and tile checks (host only)");
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
