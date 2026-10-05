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

    public LyricEffect in() {
        LyricEffect e = LyricEffect.parse(inEffect);
        return e == null ? LyricEffect.RISE : e;
    }

    public LyricEffect out() {
        LyricEffect e = LyricEffect.parse(outEffect);
        return e == null ? LyricEffect.FADE : e;
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
                    if (s != null) {
                        if (s.version < 2) {
                            // old layout (text far away around the player) -> new layout (in front of the camera)
                            s.distance = 5.0f;
                            s.scatter = 0.5f;
                            s.size = 1.0f;
                            s.glow = 0.8f;
                            s.blur = 0.25f;
                        }
                        s.version = 2;
                        if (s.font == null) s.font = "";
                        I = s;
                    }
                }
            } else {
                I.version = 2;
            }
        } catch (Exception ignored) {
        }
    }

    public static void save() {
        try {
            I.version = 2;
            Path f = file();
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(I), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }
}
