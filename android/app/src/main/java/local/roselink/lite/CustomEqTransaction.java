package local.roselink.lite;

import java.util.Arrays;

/** Readback gates activation; an ACK or a locally saved curve never counts as device success. */
public final class CustomEqTransaction {
    public enum Phase { IDLE, READING, READY, VERIFYING, SELECTING, CONFIRMED, MISMATCH, TIMEOUT }
    private Phase phase = Phase.IDLE;
    private int[] requested;
    public Phase phase() { return phase; }
    public boolean busy() { return phase == Phase.READING || phase == Phase.VERIFYING || phase == Phase.SELECTING; }
    public boolean beginRead(boolean connected) {
        if (!connected || busy()) return false;
        requested = null; phase = Phase.READING; return true;
    }
    public boolean beginApply(boolean connected, int[] reported, int[] desired) {
        if (!connected || busy() || !CustomEqProtocol.valid(reported) || !CustomEqProtocol.valid(desired)) return false;
        requested = desired.clone(); phase = Phase.VERIFYING; return true;
    }
    /** True requests selecting custom mode, only after a matching band readback. */
    public boolean bands(int[] reported) {
        if (!CustomEqProtocol.valid(reported)) return false;
        if (phase == Phase.READING) phase = Phase.READY;
        else if (phase == Phase.VERIFYING) {
            if (!Arrays.equals(requested, reported)) phase = Phase.MISMATCH;
            else { phase = Phase.SELECTING; return true; }
        } else if (phase == Phase.SELECTING || phase == Phase.CONFIRMED) {
            if (!Arrays.equals(requested, reported)) phase = Phase.MISMATCH;
        }
        return false;
    }
    public void mode(int value) {
        if (phase == Phase.SELECTING && value == 4) phase = Phase.CONFIRMED;
        else if (phase == Phase.CONFIRMED && value != 4) { phase = Phase.READY; requested = null; }
    }
    public void timeout() { if (busy()) phase = Phase.TIMEOUT; }
    public void reset() { phase = Phase.IDLE; requested = null; }
    public String label() {
        switch (phase) {
            case READING: return "正在读取耳机的十段 EQ…";
            case READY: return "已读取十段 EQ；可尝试应用并核对耳机回复";
            case VERIFYING: return "已发送曲线，等待耳机回读确认…";
            case SELECTING: return "曲线已回读一致，等待自定义模式确认…";
            case CONFIRMED: return "耳机已回读相同曲线，并报告自定义模式";
            case MISMATCH: return "耳机回读曲线不一致，未确认应用成功";
            case TIMEOUT: return "未在时限内获得确认；请重读或导出日志，不能据此认定固件支持或不支持";
            default: return "锆势固件兼容性待验证，请先读取耳机 EQ";
        }
    }
}
