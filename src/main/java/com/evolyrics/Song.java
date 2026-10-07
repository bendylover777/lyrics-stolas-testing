package com.evolyrics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
        public transient int width = -1;
        public transient int fontIdx = -1;
        public transient LyricEffect inFx, outFx;
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
            l.comp = makeComp(l.text, Settings.I.font);
            l.fontIdx = Settings.I.font;
            l.width = -1;
        }
    }

    /** Builds the text component in the currently selected font. */
    /** Style of the currently selected font (bold only where it makes sense). */
    public static Style makeStyle(int fontIdx) {
        int i = Math.floorMod(fontIdx, Settings.FONT_IDS.length);
        Style style = Style.EMPTY.withBold(Settings.FONT_BOLD[i]);
        if (Settings.FONT_IDS[i] != null) style = style.withFont(new ResourceLocation(Settings.FONT_IDS[i]));
        return style;
    }

    /** Builds the text component in the currently selected font. */
    public static Component makeComp(String text, int fontIdx) {
        return Component.literal(text).withStyle(makeStyle(fontIdx));
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
