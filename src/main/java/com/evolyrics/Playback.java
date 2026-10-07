package com.evolyrics;

import com.google.gson.Gson;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Playback clock. Sources:
 *  - manual (commands / menu)
 *  - built-in bridge (SystemBridge asks the OS what is playing, calls external())
 *  - file bridge: an external program writes config/evolyrics/bridge.json
 *    {"title":"..","artist":"..","position_ms":84320,"playing":true}
 */
public final class Playback {
    public static Song current;
    public static int generation = 0;

    private static boolean playing;
    private static double basePos;
    private static long baseNanos;
    private static int bridgeTick;
    private static final Gson GSON = new Gson();

    private static class Bridge {
        String title;
        String artist;
        long position_ms;
        boolean playing;
    }

    public static double position() {
        return playing ? basePos + (System.nanoTime() - baseNanos) / 1e9 : basePos;
    }

    public static boolean isPlaying() {
        return playing;
    }

    public static void play(Song s) {
        current = s;
        basePos = 0;
        baseNanos = System.nanoTime();
        playing = s != null;
        generation++;
    }

    public static void pause() {
        basePos = position();
        playing = false;
    }

    public static void resume() {
        if (current == null) return;
        baseNanos = System.nanoTime();
        playing = true;
    }

    public static void toggle() {
        if (playing) pause(); else resume();
    }

    public static void seek(double sec) {
        basePos = Math.max(0, sec);
        baseNanos = System.nanoTime();
        generation++;
    }

    public static void stop() {
        current = null;
        playing = false;
        basePos = 0;
        generation++;
    }

    /** An external source (built-in bridge / file bridge) says: this song, this position, playing or not. */
    public static void external(Song s, double np, boolean pl) {
        boolean changed = s != current;
        if (changed) {
            current = s;
            generation++;
        }
        double ext = position();
        if (changed || pl != playing || Math.abs(np - ext) > 0.15) {
            if (Math.abs(np - ext) > 1.0) generation++;
            basePos = np;
            baseNanos = System.nanoTime();
        }
        playing = pl;
    }

    /** Called every client tick. Only the file bridge is polled here. */
    public static void tick() {
        if (Settings.I.source != 2) return;
        if (++bridgeTick < 5) return;
        bridgeTick = 0;
        Path f = SongLibrary.dir().resolve("bridge.json");
        if (!Files.isRegularFile(f)) return;
        try (var r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            Bridge b = GSON.fromJson(r, Bridge.class);
            if (b == null) return;
            Song s = SongLibrary.findByTitle(b.title, b.artist);
            external(s, b.position_ms / 1000.0, b.playing);
        } catch (Exception ignored) {
        }
    }
}
