package com.evolyrics;

/** What the music player reports right now. Pure Java (no Minecraft classes). */
public final class PlayerInfo {
    public final String title;
    public final String artist;
    public final String app;
    public final boolean playing;
    public final double pos;   // seconds, at the moment of creation
    public final double len;   // seconds (0 = unknown)
    public final long atNanos;
    /** true when the player told us its shuffle / repeat state (Windows media API). */
    public final boolean hasModes;
    public final boolean shuffle;
    public final int repeat;   // 0 off, 1 repeat all, 2 repeat one

    public PlayerInfo(String title, String artist, boolean playing, double pos, double len, String app) {
        this(title, artist, playing, pos, len, app, false, false, 0);
    }

    public PlayerInfo(String title, String artist, boolean playing, double pos, double len, String app,
                      boolean hasModes, boolean shuffle, int repeat) {
        this.title = title;
        this.artist = artist;
        this.playing = playing;
        this.pos = pos;
        this.len = len;
        this.app = app;
        this.atNanos = System.nanoTime();
        this.hasModes = hasModes;
        this.shuffle = shuffle;
        this.repeat = repeat;
    }

    /** Position now, extrapolated since the moment the player was asked. */
    public double posNow() {
        return playing ? pos + (System.nanoTime() - atNanos) / 1e9 : pos;
    }
}
