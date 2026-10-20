package local.roselink.lite;

/** Maps the ten user control points to DP's upper-cutoff frequency bands in log frequency. */
public final class PhoneEqCurve {
    private PhoneEqCurve() { }
    public static final double[] FREQUENCIES = {20,100,300,500,1000,2000,3000,5000,8000,15000};
    public static float interpolate(int[] gains, double hz) {
        if (!CustomEqProtocol.valid(gains) || !Double.isFinite(hz) || hz <= 0) throw new IllegalArgumentException("invalid EQ curve/frequency");
        if (hz <= FREQUENCIES[0]) return gains[0];
        for (int i = 1; i < FREQUENCIES.length; i++) {
            if (hz <= FREQUENCIES[i]) {
                double t = Math.log(hz / FREQUENCIES[i - 1]) / Math.log(FREQUENCIES[i] / FREQUENCIES[i - 1]);
                return (float)(gains[i - 1] + t * (gains[i] - gains[i - 1]));
            }
        }
        return gains[gains.length - 1];
    }
    public static float cutoff(int band, int count) {
        if (count < 10 || count > 128 || band < 0 || band >= count) throw new IllegalArgumentException("invalid band");
        return (float)(20 * Math.pow(1000, (band + 1d) / count));
    }
    public static float gain(int[] gains, int band, int count) {
        double center = band == 0 ? 20 : Math.sqrt(cutoff(band - 1, count) * (double)cutoff(band, count));
        return interpolate(gains, center);
    }
    public static float headroom(int[] gains) {
        if (!CustomEqProtocol.valid(gains)) throw new IllegalArgumentException("invalid EQ curve");
        int peak = 0; for (int gain : gains) peak = Math.max(peak, gain);
        return -peak;
    }
}
