package com.evolyrics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** One song = one lyrics.json. Editable at runtime (editor-ready): addLine/prepare/save. */
public class Song {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public String title = "";
    public String artist = "";
    public List<Line> lyrics = new ArrayList<>();
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
            ResourceLocation rl = (font == null || font.isEmpty()) ? null : ResourceLocation.tryParse(font);
            MutableComponent c = Component.literal(s);
            if (rl != null) {
                return c.withStyle(st -> st.withFont(rl));
            }
            return c.withStyle(ChatFormatting.BOLD);
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
