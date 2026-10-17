package local.roselink.lite;

import java.util.Arrays;
import java.util.List;

/** Host-side protocol regression tests; these vectors are static, not captured radio traffic. */
public final class ZirconProtocolTest {
    private static int checks;
    public static void main(String[] args) {
        eq(ZirconProtocol.frame(0, h("09 01")), h("FF 00 02 09 01 0B AA"), "ANC frame and low-byte checksum");
        eq(ZirconProtocol.payload("wear", 1), h("08 00"), "wear uses inverted enable byte");
        eq(ZirconProtocol.frame(0, ZirconProtocol.payload("deep_anc", 1)), h("FF 00 02 09 06 10 AA"), "extreme ANC is wire six, distinct from ordinary ANC");
        eq(ZirconProtocol.payload("deep_anc", 0), h("09 02"), "disabling extreme ANC returns to normal");
        reject(() -> ZirconProtocol.payload("deep_anc", 2), "extreme ANC only supports a boolean");
        eq(ZirconProtocol.payload("anc_cycle", 9), h("36 01 00 00 01"), "ANC and transparency cycle use first and fourth wire slots");
        eq(ZirconProtocol.payload("anc_cycle", 6), h("36 00 01 01 00"), "wind and normal cycle use middle wire slots");
        eq(ZirconProtocol.payload("anc_cycle", 15), h("36 01 01 01 01"), "all four cycle modes allowed");
        reject(() -> ZirconProtocol.payload("anc_cycle", 0), "empty cycle rejected");
        reject(() -> ZirconProtocol.payload("anc_cycle", 1), "single-mode cycle rejected");
        reject(() -> ZirconProtocol.payload("anc_cycle", 16), "cycle out-of-range bit rejected");
        eq(ZirconProtocol.payload("touch", 0), h("07 01"), "touch uses inverted enable byte");
        eq(ZirconProtocol.payload("left_double", 8), h("01 02 08"), "left double ANC action");
        eq(ZirconProtocol.payload("right_long", 1), h("01 08 01"), "right long play action");
        eq(ZirconProtocol.frame(0, ZirconProtocol.payload("volume_limit", 0)), h("FF 00 02 46 00 47 AA"), "hearing protection off golden frame");
        eq(ZirconProtocol.frame(255, ZirconProtocol.payload("volume_limit", 6)), h("FF FF 02 46 06 4C AA"), "100 dB hearing limit golden frame with checksum rollover");
        eq(ZirconProtocol.payload("prompt_volume", 0), h("2E 00"), "minimum prompt volume");
        eq(ZirconProtocol.payload("prompt_volume", 5), h("2E 05"), "maximum prompt volume");
        eq(ZirconProtocol.payload("find", 1), h("2F 01"), "find left ear");
        eq(ZirconProtocol.payload("find", 2), h("2F 02"), "find right ear");
        eq(ZirconProtocol.payload("find", 3), h("2F 03"), "find both ears");
        eq(ZirconProtocol.frame(0, ZirconProtocol.payload("find", 4)), h("FF 00 02 2F 04 34 AA"), "find stop golden frame");
        reject(() -> ZirconProtocol.payload("volume_limit", -1), "negative hearing level rejected");
        reject(() -> ZirconProtocol.payload("volume_limit", 7), "hearing level upper bound");
        reject(() -> ZirconProtocol.payload("prompt_volume", -1), "negative prompt level rejected");
        reject(() -> ZirconProtocol.payload("prompt_volume", 6), "prompt level upper bound");
        reject(() -> ZirconProtocol.payload("find", 0), "find stop is four, not zero");
        reject(() -> ZirconProtocol.payload("find", 5), "receive-only per-ear stop event cannot be sent");
        reject(() -> ZirconProtocol.payload("codec", 4), "Zircon does not offer shared aptX options");
        reject(() -> ZirconProtocol.payload("eq", 3), "Zircon does not offer shared Light preset");
        reject(() -> ZirconProtocol.frame(256, h("09 01")), "sequence bounds");
        reject(() -> ZirconProtocol.frame(0, new byte[0]), "empty payload");
        eq(ZirconProtocol.queryPayloads().get(0), h("FA 01 07 08 09 0C 0D 0E 12 2A 2B 2C 2D 2E 2F 31 32 33 36 37 38 39 3A 3B 3C 3D 3F 45 46 49"), "original initialization query");

        ZirconProtocol.Decoder decoder = new ZirconProtocol.Decoder();
        byte[] batteryFrame = h("DD 01 04 0C E4 32 4B 4F AA");
        List<byte[]> blocks = decoder.accept(Arrays.copyOf(batteryFrame, 4), 4);
        check(blocks.isEmpty(), "fragment is held");
        byte[] rest = Arrays.copyOfRange(batteryFrame, 4, batteryFrame.length);
        blocks = decoder.accept(rest, rest.length);
        check(blocks.size() == 1, "fragment completes exactly one block");
        eq(blocks.get(0), h("0C E4 32 4B"), "battery block strips LTV length");

        byte[] multi = h("DD 22 02 09 01 02 0E 01 1C AA");
        blocks = decoder.accept(multi, multi.length);
        check(blocks.size() == 2, "multiple LTV blocks in one frame");
        eq(blocks.get(0), h("09 01"), "first LTV");
        eq(blocks.get(1), h("0E 01"), "second LTV");
        multi[multi.length - 2] = 0x1d;
        check(decoder.accept(multi, multi.length).size() == 2, "original data-only checksum variant");
        multi[multi.length - 2] = 0x11;
        check(decoder.accept(multi, multi.length).isEmpty(), "checksum failure publishes no partial state");
        byte[] noise = h("56 12 DD 00 FE 12 FF 00 02 09 01 0B AA DD 01 04 0C E4 32 4B 4F AA");
        blocks = decoder.accept(noise, noise.length);
        check(blocks.size() == 2, "garbage/incomplete earlier header resynchronizes to complete frames");
        decoder.accept(h("DD 00 02"), 3); decoder.reset();
        check(decoder.accept(h("09 01 E9 AA"), 4).isEmpty(), "reset clears prior fragments");

        DeviceState state = new DeviceState();
        check(ZirconProtocol.apply(h("09 06"), state) && state.values.get("noise") == 6 && state.values.get("deep_anc") == 1, "extreme mode confirmation updates both state views");
        check(!ZirconProtocol.apply(h("09 06"), state), "duplicate extreme reply does not fabricate a state change");
        check(ZirconProtocol.apply(h("09 02"), state) && state.values.get("deep_anc") == 0, "normal reply clears extreme selection");
        check(ZirconProtocol.apply(h("36 01 00 00 01"), state) && state.values.get("anc_cycle") == 9, "cycle reply retains exact wire bit order");
        check(!ZirconProtocol.apply(h("36 01 00 00 01"), state), "duplicate cycle report unchanged");
        check(!ZirconProtocol.apply(h("36 01 00 00"), state), "short cycle reply rejected");
        check(!ZirconProtocol.apply(h("36 01 00 00 01 00"), state), "trailing cycle reply rejected");
        check(!ZirconProtocol.apply(h("36 01 00 00 00"), state), "single-mode cycle report rejected");
        check(!ZirconProtocol.apply(h("36 01 02 00 01"), state) && state.values.get("anc_cycle") == 9, "invalid boolean cycle report preserves actual selection");
        check(ZirconProtocol.apply(h("0C E4 32 4B"), state), "battery updates");
        check(state.leftBattery == 100 && state.rightBattery == 50 && state.caseBattery == 75, "battery low 7 bits and component order");
        check(!ZirconProtocol.apply(h("0C E4 32 4B"), state), "identical reply reports no change");
        check(ZirconProtocol.apply(h("08 00"), state) && state.values.get("wear") == 1, "confirmed inverted wear state");
        check(!ZirconProtocol.apply(h("FE 00"), state), "ACK does not impersonate device state");
        check(!ZirconProtocol.apply(h("09 FF"), state), "unknown noise mode rejected");
        check(ZirconProtocol.apply(h("01 02 08 08 01"), state), "gesture pairs decode");
        check(state.values.get("left_double") == 8 && state.values.get("right_long") == 1, "gesture values preserve wire actions");
        check(!ZirconProtocol.apply(h("01 02"), state), "incomplete gesture pair rejected");
        check(ZirconProtocol.apply(h("0D 01 02 03"), state) && "1.2.3".equals(state.firmware), "numeric firmware version");
        check(ZirconProtocol.apply(h("46 06"), state) && state.values.get("volume_limit") == 6, "hearing limit reply upper bound");
        check(!ZirconProtocol.apply(h("46 06"), state), "identical hearing reply unchanged");
        check(!ZirconProtocol.apply(h("46 07"), state) && state.values.get("volume_limit") == 6, "invalid hearing reply preserves last confirmed level");
        check(!ZirconProtocol.apply(h("46 00 01"), state), "trailing hearing byte rejected");
        check(ZirconProtocol.apply(h("46 00"), state) && state.values.get("volume_limit") == 0, "hearing off reply");
        check(ZirconProtocol.apply(h("2E 05"), state) && state.values.get("prompt_volume") == 5, "prompt reply maximum");
        check(!ZirconProtocol.apply(h("2E FF"), state) && state.values.get("prompt_volume") == 5, "out of range prompt reply rejected");
        check(!ZirconProtocol.apply(h("2E 00 01"), state), "trailing prompt byte rejected");
        check(ZirconProtocol.apply(h("2E 00"), state) && state.values.get("prompt_volume") == 0, "prompt reply minimum");
        check(ZirconProtocol.apply(h("2F 01"), state) && state.values.get("find") == 1, "find left confirmed reply");
        check(ZirconProtocol.apply(h("2F 05"), state) && state.values.get("find") == 5, "left-stop receive event preserved");
        check(ZirconProtocol.apply(h("2F 06"), state) && state.values.get("find") == 6, "right-stop receive event preserved");
        check(!ZirconProtocol.apply(h("2F 00"), state), "unknown zero find reply rejected");
        check(!ZirconProtocol.apply(h("2F 07"), state), "unknown find event rejected");
        check(!ZirconProtocol.apply(h("2F 04 00"), state) && state.values.get("find") == 6, "malformed find reply preserves confirmed event");
        System.out.println("PASS: " + checks + " protocol checks (static vectors; hardware untested)");
    }
    private static byte[] h(String s) {
        String[] parts=s.split(" "); byte[] out=new byte[parts.length];
        for(int i=0;i<out.length;i++) out[i]=(byte)Integer.parseInt(parts[i],16);
        return out;
    }
    private static void eq(byte[] a, byte[] b, String label) { check(Arrays.equals(a,b),label); }
    private static void reject(Runnable action, String label) {
        try { action.run(); } catch (IllegalArgumentException expected) { check(true,label); return; }
        check(false,label);
    }
    private static void check(boolean condition, String label) {
        checks++; if(!condition) throw new AssertionError(label);
    }
}
