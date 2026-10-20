package local.roselink.lite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Clean implementation recovered from the supplied RoseLink APK, libapp.so.
 * This is static evidence, not a claim of testing against a physical Zircon.
 * Addresses below refer to ARM64 virtual addresses in that original library.
 * A Dart Smi immediate is twice its integer value; constants were checked against
 * raw instructions, not just the experimental decompiler's reconstructed code.
 */
public final class ZirconProtocol {
    private ZirconProtocol() { }

    private static final String[] GESTURE_KEYS = {"left_single", "left_double", "left_triple", "left_long",
            "right_single", "right_double", "right_triple", "right_long"};
    private static final String[] GESTURE_NAMES = {"左耳单击", "左耳双击", "左耳三击", "左耳长按",
            "右耳单击", "右耳双击", "右耳三击", "右耳长按"};
    private static final String[] ACTIONS = {"无操作", "播放 / 暂停", "上一曲", "下一曲", "音量 +", "音量 −",
            "游戏模式", "语音助手", "切换降噪"};

    /** Options are limited to ZirconDeviceConfig's own arrays, not every shared sender method. */
    public static ControlSpec[] controls() {
        List<ControlSpec> all = new ArrayList<>();
        // Zircon ctor 0x6d27f0, pp+0x88c0: anc, normal, trans, wind.
        all.add(new ControlSpec("noise", "降噪模式", new String[]{"降噪", "普通", "通透", "抗风噪"}, new int[]{1, 2, 3, 4}));
        // pp+0x8108: POP/HIFI/ROCK; their UI ordinal 0/1 differs from wire HIFI=0/POP=1.
        all.add(new ControlSpec("eq", "声音预设", new String[]{"HiFi", "流行", "摇滚"}, new int[]{0, 1, 2}));
        // Zircon ctor 0x6d2a48, pp+0x86e0: AAC/SBC, LDAC, LHDC, LC3.
        all.add(new ControlSpec("codec", "音频编码", new String[]{"AAC / SBC", "LDAC", "LHDC", "LC3"}, new int[]{0, 1, 2, 3}));
        all.add(toggle("game", "游戏低延迟"));
        all.add(toggle("wear", "入耳检测"));
        all.add(toggle("touch", "触控开关"));
        all.add(toggle("multipoint", "双设备连接"));
        all.add(toggle("deep_anc", "极限降噪"));
        // Zircon ctor 0x6d2ee0: pp+0x8628 = one/two/three/long, both ears.
        // pp+0x8708 = IntValueModels action 0..8; raw array recovery cross-checks DAE.
        for (int i = 0; i < GESTURE_KEYS.length; i++) {
            all.add(new ControlSpec(GESTURE_KEYS[i], GESTURE_NAMES[i], ACTIONS, new int[]{0,1,2,3,4,5,6,7,8}));
        }
        return all.toArray(new ControlSpec[0]);
    }

    private static ControlSpec toggle(String key, String title) {
        return new ControlSpec(key, title, new String[]{"关闭", "开启"}, new int[]{0, 1});
    }

    /** FF sequence L T V... SUM AA. Sender.transform 0xef9ee0; checksum 0x7d1554. */
    public static byte[] frame(int sequence, byte[] payload) {
        if (sequence < 0 || sequence > 255) throw new IllegalArgumentException("sequence must be 0..255");
        if (payload == null || payload.length < 1 || payload.length > 255) throw new IllegalArgumentException("payload length must be 1..255");
        byte[] frame = new byte[payload.length + 5];
        frame[0] = (byte) 0xff; frame[1] = (byte) sequence; frame[2] = (byte) payload.length;
        System.arraycopy(payload, 0, frame, 3, payload.length);
        int sum = 0;
        for (int i = 0; i < frame.length - 2; i++) sum += frame[i] & 255;
        frame[frame.length - 2] = (byte) sum; frame[frame.length - 1] = (byte) 0xaa;
        return frame;
    }

    public static byte[] payload(String key, int value) {
        if (key == null) throw new IllegalArgumentException("unknown setting");
        boolean allowed = false;
        for (ControlSpec spec : controls()) {
            if (spec.key.equals(key)) for (int option : spec.values) if (option == value) allowed = true;
        }
        // Dedicated slider/action UI; these are not ordinary controls() menu rows.
        if (key.equals("volume_limit")) allowed = value >= 0 && value <= 6;
        if (key.equals("prompt_volume")) allowed = value >= 0 && value <= 5;
        if (key.equals("find")) allowed = value >= 1 && value <= 4;
        if (key.equals("anc_cycle")) allowed = value >= 0 && value <= 15 && Integer.bitCount(value) >= 2;
        if (!allowed) throw new IllegalArgumentException("unsupported option: " + key + "=" + value);
        switch (key) {
            // DeviceDeepAncIntent 0x78e89c / sender 0x7d5b8c: off returns to ordinary mode.
            case "deep_anc": return bytes(0x09, value == 1 ? 6 : 2);
            // setAncLine 0x7d0ff8. Wire order ANC / wind / ordinary / transparency.
            case "anc_cycle": return bytes(0x36, value & 1, (value >> 1) & 1, (value >> 2) & 1, (value >> 3) & 1);
            // setHearingProtection 0x881a54. 0=off; 1..6=75,80,85,90,95,100 dB.
            case "volume_limit": return bytes(0x46, value);
            case "prompt_volume": return bytes(0x2e, value); // setDingLevel 0x8fab34, 0..5.
            // setFindDevice 0x7d2dd0 / setFindOff 0x7d2c48: 1=left,2=right,3=both,4=stop.
            case "find": return bytes(0x2f, value);
            case "noise": return bytes(0x09, value); // ANC 0x872874; normal 0x802a74; trans 0x7ed324; wind 0x80d858.
            case "eq": return bytes(0x2a, value); // Hifi 0xe9df94, pop 0xea2b4c, rock 0xe9ddcc.
            case "codec": return bytes(0x2b, value); // AAC 0xd23cd0, LDAC 0xd23b08, LHDC 0xd20280, LC3 0xd20144.
            case "game": return bytes(0x0e, value); // off 0xd24bd8, on 0xd32044.
            case "wear": return bytes(0x08, 1 - value); // off 0x925514, on 0x925acc (inverted on wire).
            case "touch": return bytes(0x07, 1 - value); // off 0x991170, on 0x992a14 (inverted on wire).
            case "multipoint": return bytes(0x32, value); // off 0xd23ea0, on 0xecaf64.
            default:
                // setTouchKey 0xc30eec: [01, gesture index 1..8, action].
                for (int i = 0; i < GESTURE_KEYS.length; i++) if (GESTURE_KEYS[i].equals(key)) return bytes(0x01, i + 1, value);
                throw new IllegalArgumentException("unknown setting: " + key);
        }
    }

    /** Exact shared getInfo payload recovered at 0xee6194; no invented probe bytes. */
    public static List<byte[]> queryPayloads() {
        List<byte[]> queries = new ArrayList<>();
        queries.add(bytes(0xfa,0x01,0x07,0x08,0x09,0x0c,0x0d,0x0e,0x12,0x2a,0x2b,0x2c,0x2d,
                0x2e,0x2f,0x31,0x32,0x33,0x36,0x37,0x38,0x39,0x3a,0x3b,0x3c,0x3d,0x3f,0x45,0x46,0x49));
        return queries;
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) result[i] = (byte) values[i];
        return result;
    }

    /** Consumes one verified T V... block; acknowledgements do not fabricate device state. */
    public static boolean apply(byte[] payload, DeviceState state) {
        if (payload == null || state == null || payload.length < 2) return false;
        int type = payload[0] & 255, value = payload[1] & 255;
        switch (type) {
            case 0x3e:
                int[] gains = CustomEqProtocol.parse(payload);
                if (gains == null) return false;
                state.customEq = gains;
                state.customEqRevision++;
                return true;
            case 0x36: // handleAncList 0xf081d0, four independent boolean bytes.
                if (payload.length != 5) return false;
                int mask = 0;
                for (int i = 1; i <= 4; i++) {
                    if (payload[i] != 0 && payload[i] != 1) return false;
                    mask |= payload[i] << (i - 1);
                }
                return Integer.bitCount(mask) >= 2 && put(state, "anc_cycle", mask);
            case 0x46: // handleHearingProtection 0xf069fc validates exactly one byte, 0..6.
                return payload.length == 2 && value <= 6 && put(state, "volume_limit", value);
            case 0x2e: // V2 dispatch 0xefd20c delegates to F8 handlePromptToneLevel 0xefab94.
                return payload.length == 2 && value <= 5 && put(state, "prompt_volume", value);
            case 0x2f: // findMeReceived 0xea382c: 1..4 as above; 5/6 stop left/right only.
                return payload.length == 2 && value >= 1 && value <= 6 && put(state, "find", value);
            case 0x01: // handleKeyData 0xf09490; keyValuesFromPairs 0xf07538.
                if ((payload.length & 1) == 0) return false;
                boolean changed = false;
                for (int i = 1; i + 1 < payload.length; i += 2) {
                    int gesture = payload[i] & 255, action = payload[i + 1] & 255;
                    if (gesture >= 1 && gesture <= 8 && action <= 8) changed |= put(state, GESTURE_KEYS[gesture - 1], action);
                }
                return changed;
            case 0x0c: // handleBatteryData 0xf095e8: high bit = in-case, low 7 bits = level.
                if (payload.length < 4) return false;
                int left = battery(payload[1]), right = battery(payload[2]), box = battery(payload[3]);
                boolean batteriesChanged = left != state.leftBattery || right != state.rightBattery || box != state.caseBattery;
                state.leftBattery = left; state.rightBattery = right; state.caseBattery = box;
                return batteriesChanged;
            case 0x0d: // handleFirmwareVersion 0xf08e5c: three numeric version components.
                if (payload.length < 4) return false;
                String version = (payload[1] & 255) + "." + (payload[2] & 255) + "." + (payload[3] & 255);
                boolean firmwareChanged = !version.equals(state.firmware); state.firmware = version; return firmwareChanged;
            case 0x09: // handleANCData 0xf09aac; UI uses normalized enum, this client keeps original wire values.
                if (payload.length != 2 || value < 1 || value > 7) return false;
                boolean noiseChanged = put(state, "noise", value);
                return put(state, "deep_anc", value == 6 ? 1 : 0) | noiseChanged;
            case 0x2a: // handleEQData 0xf09cc0 accepts 1-byte and 2-byte preset values.
                if (payload.length != 2 && payload.length != 3) return false;
                return put(state, "eq", payload.length == 3 ? (value << 8) | (payload[2] & 255) : value);
            case 0x2b: return payload.length == 2 && value <= 6 && put(state, "codec", value);
            case 0x0e: return payload.length == 2 && value <= 1 && put(state, "game", value); // 0xf08ca4.
            case 0x32: return payload.length == 2 && value <= 1 && put(state, "multipoint", value); // 0xf08aec.
            case 0x08: return payload.length == 2 && value <= 1 && put(state, "wear", 1 - value); // 0xf09128.
            case 0x07: return payload.length == 2 && value <= 1 && put(state, "touch", 1 - value); // 0xf092dc.
            default: return false;
        }
    }

    private static int battery(byte value) {
        int percentage = value & 0x7f;
        return percentage <= 100 ? percentage : -1;
    }

    private static boolean put(DeviceState state, String key, int value) {
        Integer previous = state.values.put(key, value);
        return previous == null || previous != value;
    }

    /**
     * Streaming frame decoder. Handles fragmented/combined frames and LTV chains.
     * FrameDecoder.frameLength 0xf0d038 advances by (L+1) bytes. Receiver.process
     * 0xefccbc removes two header and two trailer bytes before splitLtvs 0xf0a50c.
     * isValidPacket 0xf0cc34 accepts checksums over the complete prefix or only
     * the LTV area; both are checked. Only complete verified blocks are returned.
     */
    public static final class Decoder {
        private static final int MAX_FRAME = 4096;
        private byte[] pending = new byte[0];
        public void reset() { pending = new byte[0]; }

        public List<byte[]> accept(byte[] input, int length) {
            if (input == null || length < 0 || length > input.length) throw new IllegalArgumentException("input length");
            List<byte[]> out = new ArrayList<>();
            // Bound storage even when handed an unusually large read or a corrupt stream.
            for (int chunk = 0; chunk < length; chunk += MAX_FRAME) {
                int count = Math.min(MAX_FRAME, length - chunk);
                byte[] combined = Arrays.copyOf(pending, pending.length + count);
                System.arraycopy(input, chunk, combined, pending.length, count);
                pending = combined;
                drain(out);
            }
            return out;
        }

        private void drain(List<byte[]> out) {
            int consumed = 0, earliestPartial = -1;
            for (int start = 0; start < pending.length; start++) {
                int header = pending[start] & 255;
                if (header != 0xdd && header != 0xff) continue;
                int end = frameEnd(start);
                if (end == -1) {
                    if (earliestPartial < 0) earliestPartial = start;
                    continue;
                }
                if (end < 0) continue;
                // Validate the entire packet before publishing any of its LTVs.
                for (int p = start + 2; p < end - 2;) {
                    int blockLength = pending[p] & 255;
                    out.add(Arrays.copyOfRange(pending, p + 1, p + 1 + blockLength));
                    p += blockLength + 1;
                }
                consumed = end; earliestPartial = -1; start = end - 1;
            }
            int keep = earliestPartial >= 0 ? earliestPartial : pending.length;
            keep = Math.max(keep, consumed);
            pending = Arrays.copyOfRange(pending, keep, pending.length);
            if (pending.length > MAX_FRAME) reset();
        }

        // Positive result is exclusive packet end; -1 incomplete; -2 malformed.
        private int frameEnd(int start) {
            int p = start + 2;
            while (p - start <= MAX_FRAME - 2) {
                if (p >= pending.length) return -1;
                int blockLength = pending[p] & 255;
                if (blockLength == 0) return -2; // Original zero-length recovery variants remain unimplemented, see notes.
                int next = p + 1 + blockLength;
                if (next - start > MAX_FRAME - 2) return -2;
                if (next + 1 >= pending.length) return -1;
                if ((pending[next + 1] & 255) == 0xaa) {
                    int all = 0, data = 0;
                    for (int i = start; i < next; i++) all += pending[i] & 255;
                    for (int i = start + 2; i < next; i++) data += pending[i] & 255;
                    int checksum = pending[next] & 255;
                    if ((all & 255) == checksum || (data & 255) == checksum) return next + 2;
                    return -2;
                }
                p = next;
            }
            return -2;
        }
    }
}
