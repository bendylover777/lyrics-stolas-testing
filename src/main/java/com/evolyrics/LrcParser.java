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
        "ROTATE", "BLUR", "GLITCH", "WAVE", "DROP", "DECODE", "FALL", "TYPE"};
    private static final Pattern STAMP = Pattern.compile("\\[(\\d+):(\\d+(?:[.:]\\d+)?)\\]");
    private static final Pattern OFFSET = Pattern.compile("^\\[offset:\\s*([+-]?\\d+)\\s*\\]", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern WORD_TAG = Pattern.compile("<\\d+:\\d+(?:[.:]\\d+)?>");

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

    /**
     * Reads LRC text. Handles several time stamps on one line ("[00:10.00][00:50.00]text"), word-level
     * tags ("<00:10.50>") and the [offset:ms] header, which the old parser got wrong.
     */
    public static List<Row> parse(String lrc) {
        List<Row> rows = new ArrayList<>();
        double offset = 0;
        Matcher om = OFFSET.matcher(lrc);
        if (om.find()) {
            try {
                offset = Integer.parseInt(om.group(1)) / 1000.0;   // +offset = lyrics appear earlier
            } catch (NumberFormatException ignored) {
            }
        }
        for (String raw : lrc.split("\\R")) {
            String line = raw.trim();
            Matcher m = STAMP.matcher(line);
            List<Double> times = new ArrayList<>();
            int end = 0;
            while (m.find() && m.start() == end) {
                times.add(stampSeconds(m.group(1), m.group(2)));
                end = m.end();
            }
            if (times.isEmpty()) continue;
            String text = WORD_TAG.matcher(line.substring(end)).replaceAll("").trim();
            for (double t : times) rows.add(new Row(Math.max(0.0, t - offset), text));
        }
        rows.sort(Comparator.comparingDouble(r -> r.t));
        return rows;
    }

    private static double stampSeconds(String min, String sec) {
        return Integer.parseInt(min) * 60 + Double.parseDouble(sec.replace(':', '.'));
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
            int per = wordsPerChunk <= 0 ? Math.max(1, words.length) : wordsPerChunk;
            List<String> chunks = new ArrayList<>();
            for (int j = 0; j < words.length; j += per) {
                chunks.add(String.join(" ", java.util.Arrays.copyOfRange(words, j, Math.min(words.length, j + per))));
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
