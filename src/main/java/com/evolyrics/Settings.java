package com.evolyrics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Settings {
    public static final String[] POSITIONS = {"Around", "Center", "Top", "Bottom"};

    /** Fonts: ids live in assets/evolyrics/font/*.json (null = vanilla default). */
    public static final String[] FONT_NAMES = {"Default", "Sans", "Serif", "Mono", "Soft", "Wide", "Italic", "Unifont"};
    public static final String[] FONT_IDS = {null, "evolyrics:sans", "evolyrics:serif", "evolyrics:mono",
        "evolyrics:soft", "evolyrics:wide", "evolyrics:italic", "minecraft:uniform"};
    public static final boolean[] FONT_BOLD = {true, false, false, false, false, false, false, true};

    /** Color presets. -1 = rainbow. */
    public static final String[] COLOR_NAMES = {"White", "Blue", "Purple", "Pink", "Red", "Orange", "Yellow", "Green", "Cyan", "Rainbow"};
    public static final int[] COLOR_RGB = {0xFFFFFF, 0x7FA8FF, 0xB57CFF, 0xFF7AD9, 0xFF5A5A, 0xFFA23A, 0xFFE45C, 0x6BFF8E, 0x5CF2FF, -1};
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static Settings I = new Settings();

    public boolean enabled = true;
    public boolean island = true;
    public float size = 1.0f;
    public float distance = 9.0f;
    public float scatter = 0.7f;
    public float opacity = 1.0f;
    public float glow = 0.9f;
    public float blur = 0.4f;
    public float syncOffset = 0f;
    public String inEffect = "RISE";
    public String outEffect = "FADE";
    public int position = 0;
    public boolean throughWalls = true;
    public boolean useBridge = false;
    public int font = 0;
    public int textColor = 0;
    public int glowColor = 1;
    /** Optional "#RRGGBB" in settings.json, overrides the preset. */
    public String customTextColor = "";
    public String customGlowColor = "";
    public boolean shadow = true;
    /** Spawn words inside the camera's field of view (works in 1st and 3rd person). */
    public boolean inView = true;
    public String lastSong = "";

    public LyricEffect in() {
        LyricEffect e = LyricEffect.parse(inEffect);
        return e == null ? LyricEffect.RISE : e;
    }

    public LyricEffect out() {
        LyricEffect e = LyricEffect.parse(outEffect);
        return e == null ? LyricEffect.FADE : e;
    }

    public int textRgb(double time, int lineIdx) {
        return resolve(customTextColor, textColor, time, lineIdx);
    }

    public int glowRgb(double time, int lineIdx) {
        return resolve(customGlowColor, glowColor, time, lineIdx);
    }

    private static int resolve(String custom, int idx, double time, int lineIdx) {
        if (custom != null && !custom.isBlank()) {
            try {
                String h = custom.trim();
                if (h.startsWith("#")) h = h.substring(1);
                return Integer.parseInt(h, 16) & 0xFFFFFF;
            } catch (NumberFormatException ignored) {
            }
        }
        int i = Math.floorMod(idx, COLOR_RGB.length);
        if (COLOR_RGB[i] < 0) return rainbow(time * 0.12 + lineIdx * 0.11);
        return COLOR_RGB[i];
    }

    private static int rainbow(double h) {
        float hh = (float) (h - Math.floor(h)) * 6f;
        int i = (int) hh;
        float f = hh - i;
        float q = 1f - f;
        float r;
        float g;
        float b;
        switch (i % 6) {
            case 0 -> { r = 1f; g = f; b = 0f; }
            case 1 -> { r = q; g = 1f; b = 0f; }
            case 2 -> { r = 0f; g = 1f; b = f; }
            case 3 -> { r = 0f; g = q; b = 1f; }
            case 4 -> { r = f; g = 0f; b = 1f; }
            default -> { r = 1f; g = 0f; b = q; }
        }
        // soften towards white so it stays readable
        r = 0.3f + 0.7f * r;
        g = 0.3f + 0.7f * g;
        b = 0.3f + 0.7f * b;
        return ((int) (r * 255f) << 16) | ((int) (g * 255f) << 8) | (int) (b * 255f);
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("evolyrics").resolve("settings.json");
    }

    public static void load() {
        try {
            Path f = file();
            if (Files.isRegularFile(f)) {
                try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                    Settings s = GSON.fromJson(r, Settings.class);
                    if (s != null) I = s;
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static void save() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(I), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }
}
