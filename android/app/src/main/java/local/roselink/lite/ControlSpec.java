package local.roselink.lite;

public final class ControlSpec {
    public final String key, title;
    public final String[] labels;
    public final int[] values;

    public ControlSpec(String key, String title, String[] labels, int[] values) {
        if (labels.length != values.length || labels.length == 0) throw new IllegalArgumentException("Control options");
        this.key = key; this.title = title;
        this.labels = labels.clone(); this.values = values.clone();
    }
}
