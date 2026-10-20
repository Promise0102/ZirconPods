package local.roselink.lite;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public final class GraphicEqFileTest {
    private static int checks;
    private static void check(boolean good, String reason) { checks++; if (!good) throw new AssertionError(reason); }
    private static void rejects(String data, String reason) {
        try { GraphicEqFile.parse(data, "bad"); throw new AssertionError(reason); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    public static void main(String[] args) throws Exception {
        GraphicEqFile.Curve curve = GraphicEqFile.parse("\ufeff# Name: 人声\r\n# Source: 示例\r\nGraphicEQ: 20 -2; 100 0; 500 4; 15000 0;\r\nPreamp: -4 dB", "a.txt");
        check(curve.name.equals("人声") && curve.source.equals("示例"), "BOM/CRLF/comment metadata");
        check(curve.gains[0] == -2 && curve.gains[1] == 0 && curve.gains[3] == 4, "control points sampled correctly");
        check(curve.note.contains("Preamp -4.00dB不直接采用"), "file preamp handling disclosed");
        curve = GraphicEqFile.parse("GraphicEQ: 20 -12; 15000 12", "large");
        check(curve.scale == .5 && curve.gains[0] == -6 && curve.gains[9] == 6, "whole-curve scaling preserves sign and endpoints");
        check(curve.note.contains("0.500倍"), "normalization visible to user");
        curve = GraphicEqFile.parse("graphiceq: 20 0; 500 6; 15000 0", "safe");
        check(curve.scale == 1 && curve.gains[3] == 6, "within-range curve not scaled");
        curve = GraphicEqFile.parse("# arbitrary text is data only\nGraphicEQ: 20 2; 15000 2 # comment", "flat");
        check(Arrays.stream(curve.gains).allMatch(v -> v == 2), "constant curve and inline comments");
        curve = GraphicEqFile.parse("GraphicEQ: 20 0; 500 4", "short");
        check(curve.gains[9] == 4, "range endpoints held when file stops early");
        rejects("GraphicEQ: 100 0; 20 2", "unsorted frequency");
        rejects("GraphicEQ: 20 0; 20 1", "duplicate frequency");
        rejects("GraphicEQ: 0 1; 20000 2", "non-positive frequency");
        rejects("GraphicEQ: 20 0; 50000 0", "frequency limit");
        rejects("GraphicEQ: 20 NaN; 100 0", "NaN");
        rejects("GraphicEQ: 20 1e999; 100 0", "overflow");
        rejects("GraphicEQ: 20 61; 100 0", "absurd gain");
        rejects("GraphicEQ: 20 0;; 100 0", "empty middle point");
        rejects("GraphicEQ: 20 0", "single point");
        rejects("Include: https://example.com/curve\nGraphicEQ: 20 0; 100 0", "no includes or network directives");
        rejects("GraphicEQ: 20 0; 100 0\nFilter 1: ON PK Fc 100 Hz Gain 3 dB Q 1", "parametric commands cannot be silently discarded");
        rejects("GraphicEQ: 20 0; 100 0\nGraphicEQ: 20 1; 100 1", "multiple curves");
        rejects("Preamp: 2 dB\nPreamp: 3 dB\nGraphicEQ: 20 0; 100 0", "duplicate preamp");
        rejects("{\"gains\":[1,2]}", "unsupported JSON rejected explicitly");
        char[] huge = new char[GraphicEqFile.MAX_BYTES + 1]; Arrays.fill(huge, ' ');
        rejects(new String(huge), "bounded input");
        StringBuilder tooMany = new StringBuilder("GraphicEQ: ");
        for (int i = 1; i <= 513; i++) tooMany.append(i).append(" 0;");
        rejects(tooMany.toString(), "bounded points");
        Path styles = Paths.get(args[0]);
        int count = 0;
        try (java.util.stream.Stream<Path> files = Files.list(styles)) {
            for (Path file : (Iterable<Path>)files.filter(p -> p.toString().endsWith(".txt")).sorted()::iterator) {
                GraphicEqFile.Curve style = GraphicEqFile.parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8), file.getFileName().toString());
                check(CustomEqProtocol.valid(style.gains), "bundled style fits editor");
                check(!style.license.equals("未声明") && !style.source.contains("来源未声明"), "bundled source and license retained");
                System.out.println(style.name + ": " + Arrays.toString(style.gains)); count++;
            }
        }
        check(count == 9, "nine curated style files bundled");
        System.out.println("GraphicEQ import: " + checks + " checks passed");
    }
}
