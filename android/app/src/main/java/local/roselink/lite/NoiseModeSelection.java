package local.roselink.lite;

/** Pending requests never replace the mode actually reported by the earphones. */
final class NoiseModeSelection {
    static final int UNKNOWN = -1;
    private int requested = UNKNOWN;
    private int confirmed = UNKNOWN;
    static boolean valid(int mode) { return mode >= 1 && mode <= 4; }
    static String label(int mode) {
        switch (mode) {
            case 1: return "降噪";
            case 2: return "普通";
            case 3: return "通透";
            case 4: return "风噪";
            case 6: return "极限降噪";
            default: return "未读取";
        }
    }
    boolean request(boolean connected, int mode) {
        if (!connected || !valid(mode)) return false;
        requested = mode;
        return true;
    }
    void report(boolean connected, Integer mode) {
        confirmed = connected && mode != null && (valid(mode) || mode == 6) ? mode : UNKNOWN;
        if (!connected || confirmed == requested) requested = UNKNOWN;
    }
    int confirmed() { return confirmed; }
    int requested() { return requested; }
    void expire() { requested = UNKNOWN; }
}
