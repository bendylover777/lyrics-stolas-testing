package com.evolyrics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns synced LRC text into short timed chunks (a couple of words each). Pure Java. */
public final class LrcParser {
    public static final String[] EFFECTS = {"FADE", "RISE", "SCALE_IN", "SLIDE_LEFT", "SLIDE_RIGHT", "POP", "FLOAT",
        "ROTATE", "BLUR", "GLITCH", "WAVE", "DROP"};
    private static final Pattern LRC = Pattern.compile("\\[(\\d+):(\\d+(?:\\.\\d+)?)\\]\\s*(.*)");

    public static final class Row {
        public final double t;
        public final String text;

        Row(double t, String text) {
            this.t = t;
            this.text = text;
        }
    }

    public static final class Chunk {
        public double time;
        public String text;
        public String effect;
        public String out;
        public double duration;
    }

    private LrcParser() {
    }

    public static List<Row> parse(String lrc) {
        List<Row> rows = new ArrayList<>();
        for (String raw : lrc.split("\\R")) {
            Matcher m = LRC.matcher(raw.trim());
            if (!m.matches()) continue;
            double t = Integer.parseInt(m.group(1)) * 60 + Double.parseDouble(m.group(2));
            rows.add(new Row(t, m.group(3).trim()));
        }
        rows.sort(Comparator.comparingDouble(r -> r.t));
        return rows;
    }

    public static List<Chunk> build(List<Row> rows, int wordsPerChunk, double maxLine) {
        List<Chunk> out = new ArrayList<>();
        int n = 0;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (row.text.isEmpty()) continue;
            double end = i + 1 < rows.size() ? rows.get(i + 1).t : row.t + maxLine;
            double span = Math.max(0.5, Math.min(end - row.t, maxLine));
            String[] words = row.text.trim().split("\\s+");
            List<String> chunks = new ArrayList<>();
            for (int j = 0; j < words.length; j += wordsPerChunk) {
                chunks.add(String.join(" ", java.util.Arrays.copyOfRange(words, j, Math.min(words.length, j + wordsPerChunk))));
            }
            int total = 0;
            for (String c : chunks) total += c.length();
            if (total == 0) total = 1;
            double cur = row.t;
            for (String c : chunks) {
                double dur = span * c.length() / total;
                Chunk ch = new Chunk();
                ch.time = round2(cur);
                ch.text = c;
                ch.effect = EFFECTS[n % EFFECTS.length].toUpperCase(Locale.ROOT);
                ch.out = "FADE";
                ch.duration = round2(Math.max(0.8, dur + 0.4));
                out.add(ch);
                cur += dur;
                n++;
            }
        }
        return out;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
