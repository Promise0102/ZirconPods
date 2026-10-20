package local.roselink.lite;

public final class PhoneEqCurveTest {
    private static int checks;
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    private static void near(float value, double expected, String why) { check(Math.abs(value - expected) < .001, why); }
    public static void main(String[] args) {
        int[] vocal = {0,-1,2,3,4,2,0,-1,0,1};
        double[] centers = {20,100,300,500,1000,2000,3000,5000,8000,15000};
        for (int i = 0; i < centers.length; i++) near(PhoneEqCurve.interpolate(vocal, centers[i]), vocal[i], "control point preserved");
        near(PhoneEqCurve.interpolate(vocal, Math.sqrt(1000d * 2000)), 3, "log-frequency midpoint");
        near(PhoneEqCurve.interpolate(vocal, 10), 0, "sub-bass endpoint held");
        near(PhoneEqCurve.interpolate(vocal, 24000), 1, "treble endpoint held");
        near(PhoneEqCurve.headroom(vocal), -4, "positive peak creates negative headroom");
        int[] cut = {-6,-2,-1,0,0,0,0,-1,-2,-6};
        near(PhoneEqCurve.headroom(cut), 0, "cut-only curve does not auto-boost volume");
        for (int count : new int[]{10,32,64}) {
            float previous = 0; boolean ascending = true, withinHeadroom = true;
            for (int i = 0; i < count; i++) {
                float cutoff = PhoneEqCurve.cutoff(i, count);
                ascending &= cutoff > previous; previous = cutoff;
                float gain = PhoneEqCurve.gain(vocal, i, count);
                withinHeadroom &= gain <= 4 && gain >= -1 && gain + PhoneEqCurve.headroom(vocal) <= .001;
            }
            check(ascending, "DP upper cutoffs increase strictly");
            near(previous, 20000, "last band covers high-frequency tail");
            check(withinHeadroom, "interpolation neither overshoots nor defeats headroom");
        }
        boolean rejected = false;
        try { PhoneEqCurve.interpolate(vocal, Double.NaN); } catch (IllegalArgumentException e) { rejected = true; }
        check(rejected, "NaN frequency rejected");
        rejected = false;
        try { PhoneEqCurve.gain(new int[2], 0, 32); } catch (IllegalArgumentException e) { rejected = true; }
        check(rejected, "malformed curve rejected");
        PhoneEqState state = new PhoneEqState(); state.gains = vocal;
        PhoneEqState copy = state.copy(); copy.gains[0] = 6;
        check(state.gains[0] == 0, "runtime snapshot cannot mutate active curve");
        check(!new PhoneEqState().enabled, "service binding alone cannot enable processing");
        System.out.println("Phone EQ curve: " + checks + " checks passed (audio engine requires device testing)");
    }
}
