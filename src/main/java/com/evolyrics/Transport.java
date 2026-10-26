package com.evolyrics;

import java.util.ArrayList;
import java.util.List;

/** Player controls: previous / next / play-pause / seek. Controls the real player when the built-in bridge is active. */
public final class Transport {
    private Transport() {
    }

    private static boolean remote() {
        return Settings.I.source == 1 && SystemBridge.active();
    }

    public static void next() {
        if (remote()) SystemBridge.control("next", 0);
        else step(1);
    }

    public static void prev() {
        if (remote()) SystemBridge.control("prev", 0);
        else step(-1);
    }

    public static void toggle() {
        if (remote()) SystemBridge.control("toggle", 0);
        Playback.toggle();
    }

    /** Local jump (used while dragging the slider). */
    public static void seekLocal(double sec) {
        Playback.seek(sec);
    }

    /** Tells the real player to jump (called when the slider is released). */
    public static void seekRemote(double sec) {
        if (remote()) SystemBridge.control("seek", Math.max(0, sec));
    }

    /** Length of the current song in seconds (0 = unknown). */
    public static double length() {
        if (remote() && SystemBridge.len > 0) return SystemBridge.len;
        Song s = Playback.current;
        double end = 0;
        if (s != null) {
            for (Song.Line l : s.lyrics) end = Math.max(end, Math.max(l.end, l.time));
        }
        return end > 0 ? end + 3.0 : 0.0;
    }

    private static void step(int d) {
        List<String> ids = new ArrayList<>(SongLibrary.songs.keySet());
        if (ids.isEmpty()) return;
        int i = Playback.current == null ? -1 : ids.indexOf(Playback.current.id);
        int n = ids.size();
        int j = i < 0 ? (d > 0 ? 0 : n - 1) : Math.floorMod(i + d, n);
        Song next = SongLibrary.songs.get(ids.get(j));
        Settings.I.lastSong = next.id;
        Playback.play(next);
    }
}
