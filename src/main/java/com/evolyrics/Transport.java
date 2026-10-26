package com.evolyrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Player controls: previous / next / play-pause / seek / shuffle / repeat.
 * Controls the real player when the built-in bridge is active, otherwise the local song list.
 */
public final class Transport {
    private static boolean shuffleLocal;
    private static int repeatLocal;               // 0 off, 1 all, 2 one
    private static List<String> order = new ArrayList<>();

    private Transport() {
    }

    private static boolean remote() {
        return Settings.I.source == 1 && SystemBridge.active();
    }

    /** True when the real player (Spotify...) is in charge, so the next song is not known to us. */
    public static boolean isRemote() {
        return remote();
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

    // ------------------------------------------------------------------ shuffle / repeat

    public static boolean shuffle() {
        return remote() ? SystemBridge.shuffle : shuffleLocal;
    }

    /** 0 = off, 1 = repeat all, 2 = repeat one. */
    public static int repeat() {
        return remote() ? SystemBridge.repeat : repeatLocal;
    }

    public static void toggleShuffle() {
        if (remote()) {
            SystemBridge.setShuffle(!SystemBridge.shuffle);
        } else {
            shuffleLocal = !shuffleLocal;
            order = new ArrayList<>();
        }
    }

    public static void cycleRepeat() {
        if (remote()) {
            SystemBridge.setRepeat((SystemBridge.repeat + 1) % 3);
        } else {
            repeatLocal = (repeatLocal + 1) % 3;
        }
    }

    // ------------------------------------------------------------------ seek / length

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

    // ------------------------------------------------------------------ previous / next song (for the menu)

    private static List<String> ids() {
        List<String> base = new ArrayList<>(SongLibrary.songs.keySet());
        if (!shuffleLocal) return base;
        if (order.size() != base.size() || !order.containsAll(base)) {
            order = new ArrayList<>(base);
            Collections.shuffle(order);
        }
        return order;
    }

    /**
     * The song before (d = -1) or after (d = +1) the current one, or null when it is not known.
     * With a real player the next song is only known after Previous was pressed (Spotify does not share its queue).
     */
    public static Song peek(int d) {
        if (remote()) return d < 0 ? SystemBridge.historyPrev() : SystemBridge.historyNext();
        List<String> ids = ids();
        if (ids.isEmpty()) return null;
        int i = Playback.current == null ? -1 : ids.indexOf(Playback.current.id);
        if (i < 0) return SongLibrary.songs.get(ids.get(d > 0 ? 0 : ids.size() - 1));
        if (ids.size() < 2) return null;
        return SongLibrary.songs.get(ids.get(Math.floorMod(i + d, ids.size())));
    }

    private static void step(int d) {
        Song next = peek(d);
        if (next == null) return;
        Settings.I.lastSong = next.id;
        Playback.play(next);
    }

    /** Every client tick: shuffle / repeat for the local (manual) song list. */
    public static void tick() {
        if (remote() || Playback.current == null || !Playback.isPlaying()) return;
        if (repeatLocal == 0 && !shuffleLocal) return;
        double len = length();
        if (len <= 0 || Playback.position() < len) return;
        if (repeatLocal == 2 || ids().size() < 2) Playback.play(Playback.current);
        else step(1);
    }
}
