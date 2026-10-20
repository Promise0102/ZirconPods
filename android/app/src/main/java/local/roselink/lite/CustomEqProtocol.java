package local.roselink.lite;

/** Shared V2 EQ commands recovered from the original APK; Zircon support needs a real reply. */
public final class CustomEqProtocol {
    private CustomEqProtocol() { }
    public static final int BAND_COUNT = 10;
    public static final int MIN_GAIN = -6, MAX_GAIN = 6;
    public static final String[] FREQUENCIES = {"20", "100", "300", "500", "1k", "2k", "3k", "5k", "8k", "15k"};

    // RsCommonV2Sender.getCustomEq 0xd572c8 / setEqCustom 0xd573b8.
    public static byte[] query() { return new byte[]{(byte) 0xfa, 0x3e}; }
    public static byte[] queryMode() { return new byte[]{(byte) 0xfa, 0x2a}; }
    public static byte[] select() { return new byte[]{0x2a, 4}; }
    public static boolean valid(int[] gains) {
        if (gains == null || gains.length != BAND_COUNT) return false;
        for (int gain : gains) if (gain < MIN_GAIN || gain > MAX_GAIN) return false;
        return true;
    }
    public static byte[] write(int[] gains) {
        if (!valid(gains)) throw new IllegalArgumentException("EQ requires ten integer gains from -6 to +6 dB");
        byte[] payload = new byte[BAND_COUNT + 1];
        payload[0] = 0x3e;
        // 0xd56868 / intToEqList 0xd56930: sign bit + absolute magnitude, NOT two's complement.
        // Closure 0xd56b40 truncates double to integer without a scale factor.
        for (int i = 0; i < gains.length; i++)
            payload[i + 1] = (byte) (Math.abs(gains[i]) | (gains[i] < 0 ? 0x80 : 0));
        return payload;
    }
    public static int[] parse(byte[] payload) {
        if (payload == null || payload.length != BAND_COUNT + 1 || payload[0] != 0x3e) return null;
        int[] gains = new int[BAND_COUNT];
        // EqData.fromFormat 0xbb95a4: high bit is sign, low seven bits are magnitude.
        for (int i = 0; i < gains.length; i++) {
            int b = payload[i + 1] & 255;
            gains[i] = (b & 0x80) == 0 ? b & 0x7f : -(b & 0x7f);
        }
        return valid(gains) ? gains : null;
    }
}
