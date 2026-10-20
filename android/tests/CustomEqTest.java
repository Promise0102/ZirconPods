package local.roselink.lite;

import java.util.Arrays;
import java.util.List;

public final class CustomEqTest {
    private static int checks;
    private static void check(boolean ok, String reason) { checks++; if (!ok) throw new AssertionError(reason); }
    public static void main(String[] args) {
        int[] bass = {2,3,1,0,1,0,0,0,1,0};
        int[] vocal = {0,-1,2,3,4,2,0,-1,0,1};
        byte[] vocalWire = {0x3e,0,(byte)0x81,2,3,4,2,0,(byte)0x81,0,1};
        check(Arrays.equals(CustomEqProtocol.write(vocal), vocalWire), "screenshot curve uses sign-magnitude bytes");
        check(Arrays.equals(CustomEqProtocol.query(), new byte[]{(byte)0xfa,0x3e}), "read command matches APK");
        check(Arrays.equals(CustomEqProtocol.select(), new byte[]{0x2a,4}), "custom preset selector");
        for (int gain = -6; gain <= 6; gain++) {
            int[] all = new int[10]; Arrays.fill(all, gain);
            byte[] encoded = CustomEqProtocol.write(all);
            check((encoded[1] & 255) == (gain < 0 ? 0x80 | -gain : gain), "integer dB, no scaling");
            check(Arrays.equals(CustomEqProtocol.parse(encoded), all), "full gain range");
        }
        check(CustomEqProtocol.parse(new byte[]{0x3e,0}) == null, "short response not capability proof");
        check(CustomEqProtocol.parse(new byte[12]) == null, "unexpected band count rejected");
        byte[] wrong = vocalWire.clone(); wrong[0] = 0x2a;
        check(CustomEqProtocol.parse(wrong) == null, "other setting is not EQ data");
        wrong = vocalWire.clone(); wrong[3] = (byte)0x87;
        check(CustomEqProtocol.parse(wrong) == null, "outside supported gain range rejected");
        check(!CustomEqProtocol.valid(null) && !CustomEqProtocol.valid(new int[9]), "missing curve rejected");
        int[] invalid = bass.clone(); invalid[2] = 7;
        try { CustomEqProtocol.write(invalid); throw new AssertionError("invalid accepted"); }
        catch (IllegalArgumentException expected) { checks++; }

        DeviceState state = new DeviceState();
        check(!ZirconProtocol.apply(new byte[]{(byte)0xfe,0}, state) && state.customEq == null, "ACK cannot fabricate EQ capability");
        byte[] frame = ZirconProtocol.frame(9, vocalWire); frame[0] = (byte)0xdd;
        int sum = 0; for (int i = 0; i < frame.length - 2; i++) sum += frame[i] & 255;
        frame[frame.length - 2] = (byte)sum;
        ZirconProtocol.Decoder decoder = new ZirconProtocol.Decoder();
        check(decoder.accept(Arrays.copyOf(frame, 7), 7).isEmpty(), "fragment waits");
        byte[] rest = Arrays.copyOfRange(frame, 7, frame.length);
        List<byte[]> decoded = decoder.accept(rest, rest.length);
        check(decoded.size() == 1 && ZirconProtocol.apply(decoded.get(0), state), "valid framed response parsed");
        check(Arrays.equals(state.customEq, vocal) && state.customEqRevision == 1, "only device data populates reading");
        DeviceState copy = state.copy(); copy.customEq[0] = 6;
        check(state.customEq[0] == 0, "snapshots own arrays");
        check(!ZirconProtocol.apply(wrong, state) && state.customEqRevision == 1, "invalid response preserves last reading");
        check(ZirconProtocol.apply(vocalWire, state) && state.customEqRevision == 2, "identical reply still verifies transaction");
        state.clearReadings();
        check(state.customEq == null && state.customEqRevision == 0, "disconnect invalidates support");

        CustomEqTransaction tx = new CustomEqTransaction();
        check(!tx.beginRead(false) && !tx.beginApply(true, null, vocal), "connection and successful read required");
        check(tx.beginRead(true) && tx.busy(), "reading starts");
        check(!tx.beginRead(true) && !tx.beginApply(true, bass, vocal), "operations cannot overlap");
        tx.mode(4); check(tx.phase() == CustomEqTransaction.Phase.READING, "mode alone cannot prove bands");
        tx.bands(vocal); check(tx.phase() == CustomEqTransaction.Phase.READY, "read probe completes");
        check(tx.beginApply(true, bass, vocal), "apply after valid probe");
        tx.mode(4); check(tx.phase() == CustomEqTransaction.Phase.VERIFYING, "early mode not apply success");
        check(!tx.bands(bass) && tx.phase() == CustomEqTransaction.Phase.MISMATCH, "mismatch never activates mode");
        check(tx.beginApply(true, bass, vocal), "retry allowed");
        vocal[1] = 6;
        check(tx.bands(new int[]{0,-1,2,3,4,2,0,-1,0,1}), "requested curve cloned");
        check(tx.phase() == CustomEqTransaction.Phase.SELECTING, "matching bands await actual mode");
        tx.mode(0); check(tx.phase() == CustomEqTransaction.Phase.SELECTING, "builtin mode is not success");
        tx.mode(4); check(tx.phase() == CustomEqTransaction.Phase.CONFIRMED, "both readings confirm");
        tx.mode(1); check(tx.phase() == CustomEqTransaction.Phase.READY, "external mode change clears applied label");
        check(tx.beginRead(true), "read again"); tx.timeout();
        check(tx.phase() == CustomEqTransaction.Phase.TIMEOUT && !tx.busy(), "timeout unlocks without declaring unsupported");
        tx.mode(4); check(tx.phase() == CustomEqTransaction.Phase.TIMEOUT, "late mode cannot confirm failed operation");
        tx.reset(); check(tx.phase() == CustomEqTransaction.Phase.IDLE, "new connection resets transaction");
        System.out.println("Custom EQ: " + checks + " checks passed");
    }
}
