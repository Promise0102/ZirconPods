package local.roselink.lite;

/** A tile advances only from a mode confirmed in this live connection. */
final class NoiseModeCycle {
    static int next(boolean connected, Integer confirmed, boolean pending) {
        if (!connected || confirmed == null || pending) return NoiseModeSelection.UNKNOWN;
        switch (confirmed) {
            case 1: return 4;
            case 4: return 3;
            case 3: return 2;
            case 2: return 1;
            case 6: return 1;
            default: return NoiseModeSelection.UNKNOWN;
        }
    }
}
