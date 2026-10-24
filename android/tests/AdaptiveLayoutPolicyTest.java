package local.roselink.lite;

public final class AdaptiveLayoutPolicyTest {
    private static int checks;
    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        checks++;
    }
    private static AdaptiveLayoutPolicy.Region region(int w, int h, boolean vertical, int start, int end) {
        return AdaptiveLayoutPolicy.region(w, h, true, vertical, start, end, 8);
    }
    public static void main(String[] args) {
        AdaptiveLayoutPolicy.Region r = AdaptiveLayoutPolicy.region(320, 720, false, true, 160, 160, 8);
        check(r.left == 0 && r.width == 320 && r.height == 720, "cover display uses full area without separating fold");
        r = region(760, 700, true, 370, 390);
        check(r.left == 0 && r.width == 362 && r.height == 700, "book posture leaves clearance before occluding hinge");
        r = region(1080, 800, true, 320, 340);
        check(r.left == 348 && r.width == 732, "unbalanced hinge uses larger right pane");
        r = region(720, 900, false, 440, 460);
        check(r.top == 0 && r.height == 432 && r.width == 720, "tabletop keeps controls above fold");
        r = region(720, 900, false, 260, 280);
        check(r.top == 288 && r.height == 612, "tabletop can use larger bottom pane");
        r = region(800, 600, true, 400, 400);
        check(r.width == 392, "zero-thickness separating fold still gets clearance");
        r = region(320, 600, true, 600, 620);
        check(r.width == 320 && r.left == 0, "fold outside split-screen window is ignored");
        r = region(320, 600, true, -80, -40);
        check(r.width == 320, "fold before window is ignored");
        r = region(320, 600, true, -20, 12);
        check(r.left == 20 && r.width == 300, "partial occlusion at window edge");
        r = region(320, 600, true, 0, 320);
        check(r.width == 0 && r.height == 0, "fully occluded viewport never lays out over hinge");
        r = region(320, 600, true, 170, 140);
        check(r.width == 320, "malformed vendor bounds fall back");
        check(!AdaptiveLayoutPolicy.twoColumns(360, 1), "cover stays single-column");
        check(AdaptiveLayoutPolicy.twoColumns(720, 1), "inner display supports two columns");
        check(!AdaptiveLayoutPolicy.twoColumns(720, 1.5f), "large text avoids compressed two-column layout");
        check(AdaptiveLayoutPolicy.twoColumns(1200, 2), "large window can accommodate enlarged text");
        check(AdaptiveLayoutPolicy.eqWidth(320, 1) >= 504, "ten bands keep 48dp touch targets on cover");
        check(AdaptiveLayoutPolicy.eqWidth(840, 1) == 840, "inner EQ fills available width");
        check(AdaptiveLayoutPolicy.eqWidth(320, 2) >= 744, "enlarged band labels get horizontal room");
        for (int w = 280; w <= 1280; w += 17) {
            for (boolean vertical : new boolean[]{true, false}) {
                int length = vertical ? w : 900;
                int start = length / 3, end = start + 16;
                r = region(w, 900, vertical, start, end);
                if (r.left < 0 || r.top < 0 || r.left + r.width > w || r.top + r.height > 900)
                    throw new AssertionError("resize bounds");
                if (vertical ? !(r.left + r.width <= start || r.left >= end) : !(r.top + r.height <= start || r.top >= end))
                    throw new AssertionError("hinge overlap during resize");
            }
        }
        check(true, "resize sweep keeps every chosen region inside window and outside hinge");
        System.out.println("Adaptive layout: " + checks + " checks passed (geometry only; Android UI needs device testing)");
    }
}
