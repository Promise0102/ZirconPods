package local.roselink.lite;

import java.util.ArrayDeque;
import java.util.Locale;

/** Bounded, per-operation wire evidence. No Bluetooth address is added to the report. */
public final class CustomEqDiagnostics {
    private final ArrayDeque<String> lines = new ArrayDeque<>();
    private boolean active;
    private String kind = "尚未读取", result = "";
    private int chunks, bytes, packets, acknowledgements, eqPackets, validEqPackets;
    public void begin(String kind) {
        this.kind = kind; result = ""; active = true; lines.clear();
        chunks = bytes = packets = acknowledgements = eqPackets = validEqPackets = 0;
    }
    public void raw(byte[] data) {
        if (!active || data == null) return;
        chunks++; bytes += data.length; add("RX RAW " + hex(data));
    }
    public void sent(byte[] frame) { if (active) add("TX " + hex(frame)); }
    public void packet(byte[] payload) {
        if (!active || payload == null || payload.length == 0) return;
        packets++;
        if ((payload[0] & 255) == 0xfe) acknowledgements++;
        if ((payload[0] & 255) == 0x3e) {
            eqPackets++;
            if (CustomEqProtocol.parse(payload) != null) validEqPackets++;
            else add("EQ 回包未采用：数据长度=" + (payload.length - 1) + "，要求10段且每段−6～+6dB");
        }
        add("RX LTV " + hex(payload));
    }
    public void finish(String result) {
        if (!active) return;
        this.result = result; add("结果：" + result); active = false;
    }
    public String summary() {
        String clue;
        if (validEqPackets > 0) clue = "收到有效十段 EQ 数据";
        else if (eqPackets > 0) clue = "收到3E数据，但长度或增益范围与当前解析不符";
        else if (packets > acknowledgements) clue = "控制通道有其他回包，尚未收到3E曲线";
        else if (acknowledgements > 0) clue = "仅收到普通确认，不能据此认定支持自定义EQ";
        else if (chunks > 0) clue = "收到原始数据，但未解出校验通过的协议块";
        else clue = "尚未收到原始数据";
        return kind + " · " + (active ? "进行中" : result.isEmpty() ? "未开始" : "已结束")
                + "\n接收：" + chunks + "块 / " + bytes + "字节；有效协议块：" + packets
                + "；普通确认：" + acknowledgements + "；3E回包：" + eqPackets + "\n" + clue;
    }
    public String report() {
        StringBuilder report = new StringBuilder(summary()).append("\n\n本次通信（最多48条，每块最多128字节）：\n");
        for (String line : lines) report.append(line).append('\n');
        return report.toString();
    }
    private void add(String line) { lines.addLast(line); while (lines.size() > 48) lines.removeFirst(); }
    private static String hex(byte[] data) {
        if (data == null) return "null";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < Math.min(data.length, 128); i++) {
            if (i > 0) out.append(' ');
            out.append(String.format(Locale.ROOT, "%02X", data[i] & 255));
        }
        if (data.length > 128) out.append(" … 总长").append(data.length);
        return out.toString();
    }
}
