package local.roselink.lite;

import java.util.List;

public final class CustomEqDiagnosticsTest {
    private static int checks;
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    public static void main(String[] args) {
        // Exact exchange transcribed from user's Zircon firmware 0.3.1 screenshot.
        byte[] tx = {(byte)0xff,1,2,(byte)0xfa,0x3e,0x3a,(byte)0xaa};
        byte[] rx = {(byte)0xdd,1,2,(byte)0xfe,0,(byte)0xde,(byte)0xaa};
        CustomEqDiagnostics diagnostic = new CustomEqDiagnostics();
        diagnostic.begin("只读 EQ 查询"); diagnostic.sent(tx); diagnostic.raw(rx);
        List<byte[]> packets = new ZirconProtocol.Decoder().accept(rx, rx.length);
        check(packets.size() == 1 && packets.get(0)[0] == (byte)0xfe, "real ACK passes frame checksum");
        diagnostic.packet(packets.get(0)); diagnostic.finish("未获曲线");
        check(diagnostic.summary().contains("仅收到普通确认"), "ACK-only classified independently from silence");
        check(diagnostic.summary().contains("3E回包：0"), "ACK cannot count as EQ data");
        check(diagnostic.report().contains("FF 01 02 FA 3E 3A AA"), "exact query retained");
        String ended = diagnostic.report(); diagnostic.raw(new byte[]{1,2,3});
        check(ended.equals(diagnostic.report()), "unrelated late traffic cannot rewrite completed evidence");

        diagnostic.begin("重新读取");
        check(!diagnostic.report().contains("FF 01 02") && diagnostic.summary().contains("尚未收到原始数据"), "new operation resets prior counts");
        diagnostic.raw(new byte[]{(byte)0xdd,1});
        check(diagnostic.summary().contains("未解出校验通过"), "raw but undecoded distinguished");
        diagnostic.packet(new byte[]{0x2a,0});
        check(diagnostic.summary().contains("控制通道有其他回包"), "working control channel does not prove EQ support");
        diagnostic.packet(new byte[]{0x3e,0,0});
        check(diagnostic.summary().contains("长度或增益范围"), "unexpected EQ shape explicitly surfaced");
        check(diagnostic.report().contains("数据长度=2"), "actual rejected data length retained");
        byte[] invalid = new byte[11]; invalid[0] = 0x3e; invalid[4] = 7;
        diagnostic.packet(invalid);
        check(!diagnostic.summary().contains("收到有效十段"), "out-of-range data never becomes capability proof");
        byte[] valid = new byte[11]; valid[0] = 0x3e;
        diagnostic.packet(valid);
        check(diagnostic.summary().contains("收到有效十段"), "valid curve gets distinct evidence status");

        diagnostic.begin("有界记录"); diagnostic.raw(new byte[300]);
        check(diagnostic.report().contains("总长300"), "large chunks record true size while limiting raw dump");
        for (int i = 0; i < 200; i++) diagnostic.packet(new byte[]{0x2a,0});
        check(diagnostic.report().length() < 5000, "trace bounded despite repeated notifications");
        diagnostic.finish("连接已断开");
        check(diagnostic.report().contains("连接已断开"), "disconnect reason retained");
        System.out.println("Custom EQ diagnostics: " + checks + " checks passed");
    }
}
