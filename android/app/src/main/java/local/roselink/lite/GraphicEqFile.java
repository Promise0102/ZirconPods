package local.roselink.lite;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Data-only reader for GraphicEQ text; never follows Include, URLs, or executable directives. */
public final class GraphicEqFile {
    private GraphicEqFile() { }
    public static final int MAX_BYTES = 131072;
    private static final String NUMBER = "[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?";
    private static final Pattern PAIR = Pattern.compile("^\\s*(" + NUMBER + ")\\s+(" + NUMBER + ")\\s*$");
    private static final Pattern PREAMP = Pattern.compile("^Preamp\\s*:\\s*(" + NUMBER + ")\\s*dB\\s*$", Pattern.CASE_INSENSITIVE);
    public static final class Curve {
        public final String name, source, license, note;
        public final int[] gains;
        public final int points;
        public final double scale;
        private Curve(String name, String source, String license, String note, int[] gains, int points, double scale) {
            this.name = name; this.source = source; this.license = license; this.note = note;
            this.gains = gains.clone(); this.points = points; this.scale = scale;
        }
    }
    public static Curve parse(String text, String fallbackName) {
        if (text == null || text.length() > MAX_BYTES) throw new IllegalArgumentException("文件为空或超过128KiB");
        String name = clean(fallbackName, 40), source = "本地文件（来源未声明）", license = "未声明";
        if (text.startsWith("\ufeff")) text = text.substring(1);
        String graphic = null; Double preamp = null;
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#")) {
                String comment = line.substring(1).trim();
                int colon = comment.indexOf(':');
                if (colon > 0) {
                    String key = comment.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                    String value = comment.substring(colon + 1).trim();
                    if (key.equals("name")) name = clean(value, 40);
                    if (key.equals("source")) source = clean(value, 240);
                    if (key.equals("license")) license = clean(value, 80);
                }
                continue;
            }
            int commentAt = line.indexOf('#');
            if (commentAt >= 0) line = line.substring(0, commentAt).trim();
            if (line.matches("(?i)^GraphicEQ\\s*:.*")) {
                if (graphic != null) throw new IllegalArgumentException("一个文件只支持一条GraphicEQ曲线");
                graphic = line.substring(line.indexOf(':') + 1).trim();
            } else {
                Matcher amp = PREAMP.matcher(line);
                if (amp.matches() && preamp == null) {
                    preamp = Double.parseDouble(amp.group(1));
                    if (!Double.isFinite(preamp) || Math.abs(preamp) > 60) throw new IllegalArgumentException("Preamp数值无效");
                } else throw new IllegalArgumentException("仅支持GraphicEQ文本；不支持Filter、Include或其他指令");
            }
        }
        if (graphic == null || graphic.isEmpty()) throw new IllegalArgumentException("未找到GraphicEQ: 频率 增益; ...");
        String[] pairs = graphic.split(";", -1);
        List<Double> hz = new ArrayList<>(), db = new ArrayList<>();
        for (int index = 0; index < pairs.length; index++) {
            if (index == pairs.length - 1 && pairs[index].trim().isEmpty()) continue;
            Matcher pair = PAIR.matcher(pairs[index]);
            if (!pair.matches()) throw new IllegalArgumentException("频率/增益数据格式错误");
            double frequency = Double.parseDouble(pair.group(1)), gain = Double.parseDouble(pair.group(2));
            if (!Double.isFinite(frequency) || frequency < 1 || frequency > 48000 || !Double.isFinite(gain) || Math.abs(gain) > 60)
                throw new IllegalArgumentException("频率或增益超出允许范围");
            if (!hz.isEmpty() && frequency <= hz.get(hz.size() - 1)) throw new IllegalArgumentException("频率必须严格递增，不能重复");
            hz.add(frequency); db.add(gain);
            if (hz.size() > 512) throw new IllegalArgumentException("最多支持512个曲线点");
        }
        if (hz.size() < 2) throw new IllegalArgumentException("曲线至少需要两个频率点");
        double[] sampled = new double[10]; double peak = 0;
        for (int i = 0; i < sampled.length; i++) {
            sampled[i] = interpolate(hz, db, PhoneEqCurve.FREQUENCIES[i]);
            peak = Math.max(peak, Math.abs(sampled[i]));
        }
        double scale = peak > 6 ? 6 / peak : 1;
        int[] gains = new int[10];
        for (int i = 0; i < gains.length; i++) gains[i] = (int)Math.round(sampled[i] * scale);
        String note = "将" + hz.size() + "个原始点按对数频率映射到十段，并取整到1dB；不是原滤波器的精确复刻。";
        if (scale < 1) note += String.format(Locale.ROOT, "\n超出±6dB，整条十段曲线按%.3f倍缩放。", scale);
        if (preamp != null) note += String.format(Locale.ROOT, "\n文件Preamp %.2fdB不直接采用，手机EQ使用自动输入余量。", preamp);
        if (name.isEmpty()) name = "导入音效";
        return new Curve(name, source, license, note, gains, hz.size(), scale);
    }
    private static double interpolate(List<Double> hz, List<Double> db, double frequency) {
        if (frequency <= hz.get(0)) return db.get(0);
        for (int i = 1; i < hz.size(); i++) if (frequency <= hz.get(i)) {
            double ratio = Math.log(frequency / hz.get(i - 1)) / Math.log(hz.get(i) / hz.get(i - 1));
            return db.get(i - 1) + ratio * (db.get(i) - db.get(i - 1));
        }
        return db.get(db.size() - 1);
    }
    private static String clean(String value, int limit) {
        if (value == null) return "";
        String text = value.replaceAll("[\\p{Cntrl}]", " ").trim();
        return text.length() > limit ? text.substring(0, limit) : text;
    }
}
