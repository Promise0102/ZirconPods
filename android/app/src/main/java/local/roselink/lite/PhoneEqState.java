package local.roselink.lite;

public final class PhoneEqState {
    public boolean loaded, enabled, global, externalOutput;
    public int effects, announcedSessions;
    public int[] gains = new int[10];
    public String status = "手机端 EQ 未启用", diagnostic = "尚未创建系统音效";
    public PhoneEqState copy() {
        PhoneEqState s = new PhoneEqState();
        s.loaded = loaded; s.enabled = enabled; s.global = global; s.externalOutput = externalOutput;
        s.effects = effects; s.announcedSessions = announcedSessions; s.gains = gains.clone();
        s.status = status; s.diagnostic = diagnostic; return s;
    }
}
