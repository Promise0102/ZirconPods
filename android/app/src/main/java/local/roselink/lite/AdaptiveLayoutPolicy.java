package local.roselink.lite;

/** Window-local hinge geometry, kept independent of Android for resize regression checks. */
public final class AdaptiveLayoutPolicy {
    private AdaptiveLayoutPolicy() { }
    public static final class Region {
        public final int left, top, width, height;
        Region(int left, int top, int width, int height) {
            this.left = left; this.top = top; this.width = width; this.height = height;
        }
    }
    public static Region region(int width, int height, boolean separating, boolean vertical,
                                int hingeStart, int hingeEnd, int clearance) {
        width = Math.max(0, width); height = Math.max(0, height);
        Region full = new Region(0, 0, width, height);
        int length = vertical ? width : height;
        if (!separating || hingeEnd < hingeStart || hingeEnd <= 0 || hingeStart >= length) return full;
        int before = Math.max(0, Math.min(length, hingeStart - Math.max(0, clearance)));
        int after = Math.max(0, Math.min(length, hingeEnd + Math.max(0, clearance)));
        if (before == 0 && after == length) return new Region(0, 0, 0, 0);
        boolean first = before >= length - after;
        if (vertical) return new Region(first ? 0 : after, 0, first ? before : length - after, height);
        return new Region(0, first ? 0 : after, width, first ? before : length - after);
    }
    public static boolean twoColumns(float widthDp, float fontScale) {
        return widthDp >= Math.max(680f, 600f * Math.max(1f, fontScale));
    }
    public static float eqWidth(float availableDp, float fontScale) {
        return Math.max(availableDp, 24f + 10f * Math.max(48f, 36f * Math.max(1f, fontScale)));
    }
}
