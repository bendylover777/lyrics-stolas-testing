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
    public static final String[] SOURCES = {"Manual", "Auto (built-in)", "File bridge"};
    public static final String[] SHIMMER = {"Off", "Glint", "Rainbow", "Flow", "Pulse", "Aurora", "Fire", "Flicker",
        "Gradient", "Sunset", "Ocean", "Candy", "Matrix"};
    public static final String[] CASES = {"As is", "UPPER", "lower"};
    public static final String[] EFFECT_MODES = {"Menu", "File", "Mix"};
    public static final String[] POSITIONS = {"Field of view", "Center", "Top", "Bottom"};
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static Settings I = new Settings();

    public int version = 0;
    public boolean enabled = true;
    public boolean island = true;
    public float size = 1.0f;
    public float distance = 5.0f;
    public float scatter = 0.5f;
    public float opacity = 1.0f;
    public float glow = 0.8f;
    public float blur = 0.25f;
    public float syncOffset = 0f;
    public String inEffect = "RISE";
    public String outEffect = "FADE";
    public int position = 0;
    public boolean throughWalls = true;
    public boolean useBridge = false;
    public String lastSong = "";
    public int textColor = 0xFFFFFF;
    public int glowColor = 0x7FA8FF;
    public String font = "";
    public float neon = 0.8f;
    public float tilt = 5.0f;
    public boolean islandScroll = true;
    public boolean islandLyrics = true;
    public int shimmer = 1;
    public float shimmerSpeed = 1.0f;
    public float glint = 1.4f;
    public int source = 1;            // 0 manual, 1 built-in (no cmd window), 2 file bridge (external bridge.py)
    public boolean consoleLine = true;
    public boolean sidePreview = true;
    public boolean autoLyrics = true;
    public int effectMode = 0;        // 0 = In/Out effect from the menu, 1 = per line from lyrics.json, 2 = mix of all

    // ---- added in 1.1: theme / text / sources / misc
    public int uiAccent = 0;          // 0 = follow glow colour, see Theme.UI_ACCENT_NAMES
    public int uiBg = 0;              // see Theme.UI_BG_NAMES
    public boolean shadow = false;    // dark drop shadow under the lyrics
    public boolean bold = false;      // custom fonts only (the pixel font is always bold)
    public boolean italic = false;
    public boolean underline = false;
    public int textCase = 0;          // 0 as is, 1 UPPER, 2 lower
    public int maxVisible = 8;        // lines on screen at once (1..8)
    public float animSpeed = 1.0f;    // speed of the in / out animations
    public int wordsPerChunk = 2;     // words per shown piece when lyrics are downloaded (1..6)
    public boolean hideWhenPaused = false;
    public int sourceMask = -1;       // bit i = lyrics site i (see LyricsSources.names()) is used
    public boolean allowUnsynced = true;   // plain lyrics without time codes: timing is estimated
    public float islandOpacity = 0.95f;
    public int islandY = 5;
    public float sideScale = 1.6f;
    public boolean toasts = true;     // short messages above the hotbar for hotkeys / commands

    private transient String keyCache;
    private transient String keyFont;
    private transient int keyFlags = -1;

    /** Font id + style flags, used as cache key and understood by {@link Song#styledComponent}. No allocation per frame. */
    public String fontKey() {
        int fl = (bold ? 1 : 0) | (italic ? 2 : 0) | (underline ? 4 : 0) | ((textCase & 3) << 3);
        String f = font == null ? "" : font;
        if (keyCache == null || fl != keyFlags || !f.equals(keyFont)) {
            keyFont = f;
            keyFlags = fl;
            keyCache = f + "|" + fl;
        }
        return keyCache;
    }

    public boolean siteOn(int index) {
        return index >= 0 && index < 31 && (sourceMask & (1 << index)) != 0;
    }

    public void setSite(int index, boolean on) {
        if (index < 0 || index >= 31) return;
        if (on) sourceMask |= (1 << index);
        else sourceMask &= ~(1 << index);
    }

    public LyricEffect in() {
        LyricEffect e = LyricEffect.parse(inEffect);
        return e == null ? LyricEffect.RISE : e;
    }

    public LyricEffect out() {
        LyricEffect e = LyricEffect.parse(outEffect);
        return e == null ? LyricEffect.FADE : e;
    }

    /** Back to the defaults (keeps the last song). */
    public static void reset() {
        String last = I.lastSong;
        I = new Settings();
        I.version = 3;
        I.lastSong = last;
        save();
    }

    private static Path file() {
        return SongLibrary.dir().resolve("settings.json");
    }

    public static void load() {
        try {
            Path f = file();
            if (Files.isRegularFile(f)) {
                try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                    Settings s = GSON.fromJson(r, Settings.class);
                    if (s != null) {
                        if (s.version < 2) {
                            // old layout (text far away around the player) -> new layout (in front of the camera)
                            s.distance = 5.0f;
                            s.scatter = 0.5f;
                            s.size = 1.0f;
                            s.glow = 0.8f;
                            s.blur = 0.25f;
                        }
                        s.version = 3;
                        if (s.font == null) s.font = "";
                        s.maxVisible = Math.max(1, Math.min(8, s.maxVisible));
                        s.wordsPerChunk = Math.max(1, Math.min(6, s.wordsPerChunk));
                        s.animSpeed = Math.max(0.3f, Math.min(3f, s.animSpeed));
                        s.sideScale = Math.max(0.8f, Math.min(3f, s.sideScale));
                        s.islandOpacity = Math.max(0.2f, Math.min(1f, s.islandOpacity));
                        s.islandY = Math.max(0, Math.min(30, s.islandY));
                        s.textCase = Math.max(0, Math.min(2, s.textCase));
                        I = s;
                    }
                }
            } else {
                I.version = 3;
            }
        } catch (Exception ignored) {
        }
    }

    public static void save() {
        try {
            I.version = 3;
            Path f = file();
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(I), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }
}
