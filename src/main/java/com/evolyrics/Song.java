package com.evolyrics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** One song = one lyrics.json. Editable at runtime (editor-ready): addLine/prepare/save. */
public class Song {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public String title = "";
    public String artist = "";
    public List<Line> lyrics = new ArrayList<>();
    /** Cleaned LRC text this song was built from (downloaded songs only): lets "words per chunk" be changed later. */
    public String lrc = "";
    public transient String id = "";

    public static class Line {
        public double time;
        public String text = "";
        public String effect;     // in-effect (optional)
        public String out;        // out-effect (optional)
        public double duration;   // seconds (optional)
        public String position;   // left/right/center/top/bottom/random (optional)

        public transient double end;
        public transient Component comp;
        public transient String compFont;
        public transient int width = -1;
        public transient LyricEffect inFx, outFx;
        public transient Component[] charComps;
        public transient int[] charOff;
        public transient int charCount;
        public transient int textWidth = -1;
        public transient String charFont;

        private Component styled(String s, String font) {
            return styledComponent(s, font);
        }

        /** Cached styled text for the chosen font (rebuilt only when the font changes). */
        public Component comp(String font) {
            if (comp == null || !Objects.equals(compFont, font)) {
                compFont = font;
                width = -1;
                comp = styled(text, font);
            }
            return comp;
        }

        /** Per-letter components + x offsets (for shimmering letters). Rebuilt only when the font changes. */
        public void buildChars(String font, Font mcFont) {
            if (charComps != null && textWidth >= 0 && Objects.equals(charFont, font)) return;
            charFont = font;
            int n = text.codePointCount(0, text.length());
            Component[] comps = new Component[n];
            int[] offs = new int[n];
            int x = 0;
            int k = 0;
            int i = 0;
            while (i < text.length()) {
                int cp = text.codePointAt(i);
                i += Character.charCount(cp);
                Component c = styled(new String(Character.toChars(cp)), font);
                comps[k] = c;
                offs[k] = x;
                x += mcFont.width(c);
                k++;
            }
            charComps = comps;
            charOff = offs;
            charCount = n;
            textWidth = x;
        }
    }

    /**
     * Text in the chosen lyrics font. {@code fontKey} is "fontId" or "fontId|flags" (see Settings.fontKey):
     * flags = bold 1, italic 2, underline 4, case (UPPER 1 / lower 2) in bits 3-4.
     * The vanilla pixel font is always bold, custom fonts are used as they are unless bold is asked for.
     */
    public static Component styledComponent(String s, String fontKey) {
        String id = fontKey == null ? "" : fontKey;
        int fl = 0;
        int bar = id.indexOf('|');
        if (bar >= 0) {
            try {
                fl = Integer.parseInt(id.substring(bar + 1));
            } catch (NumberFormatException ignored) {
            }
            id = id.substring(0, bar);
        }
        final ResourceLocation rl = id.isEmpty() ? null : ResourceLocation.tryParse(id);
        final int flags = fl;
        int cs = (fl >> 3) & 3;
        String txt = cs == 1 ? s.toUpperCase(Locale.ROOT) : cs == 2 ? s.toLowerCase(Locale.ROOT) : s;
        MutableComponent c = Component.literal(txt);
        return c.withStyle(st -> {
            Style x = st;
            if (rl != null) x = x.withFont(rl);
            if (rl == null || (flags & 1) != 0) x = x.withBold(true);
            if ((flags & 2) != 0) x = x.withItalic(true);
            if ((flags & 4) != 0) x = x.withUnderlined(true);
            return x;
        });
    }

    /** Replaces the lines by freshly built chunks (a downloaded LRC). */
    public void applyChunks(List<LrcParser.Chunk> chunks) {
        lyrics.clear();
        for (LrcParser.Chunk c : chunks) {
            Line l = new Line();
            l.time = c.time;
            l.text = c.text;
            l.effect = c.effect;
            l.out = c.out;
            l.duration = c.duration;
            lyrics.add(l);
        }
        prepare();
    }

    /** Builds the lines again from the stored LRC with another number of words per chunk. False when there is no LRC. */
    public boolean rechunk(int wordsPerChunk) {
        if (lrc == null || lrc.isBlank()) return false;
        List<LrcParser.Chunk> chunks = LrcParser.build(LrcParser.parse(lrc), wordsPerChunk, 5.0);
        if (chunks.isEmpty()) return false;
        applyChunks(chunks);
        return true;
    }

    public void prepare() {
        lyrics.removeIf(l -> l == null || l.text == null);
        lyrics.sort(Comparator.comparingDouble(l -> l.time));
        for (int i = 0; i < lyrics.size(); i++) {
            Line l = lyrics.get(i);
            double next = i + 1 < lyrics.size() ? lyrics.get(i + 1).time : l.time + 3.0;
            double d = l.duration > 0 ? l.duration : Math.min(Math.max(next - l.time, 0.8), 6.0);
            l.end = l.time + d;
            l.inFx = LyricEffect.parse(l.effect);
            l.outFx = LyricEffect.parse(l.out);
            l.comp = null;
            l.compFont = null;
            l.width = -1;
            l.charComps = null;
            l.charFont = null;
            l.textWidth = -1;
        }
    }

    public Line addLine(double time, String text, String effect) {
        Line l = new Line();
        l.time = Math.round(time * 100.0) / 100.0;
        l.text = text;
        l.effect = effect;
        lyrics.add(l);
        prepare();
        return l;
    }

    public static Song read(Path file) throws IOException {
        try (var r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Song s = GSON.fromJson(r, Song.class);
            if (s == null) s = new Song();
            if (s.lyrics == null) s.lyrics = new ArrayList<>();
            if (s.lrc == null) s.lrc = "";
            if (s.title == null) s.title = "";
            if (s.artist == null) s.artist = "";
            return s;
        }
    }

    public void save(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
    }
}
