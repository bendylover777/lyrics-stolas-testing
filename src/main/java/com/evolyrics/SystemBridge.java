package com.evolyrics;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The built-in bridge: no cmd window, no Python. Watches what the OS player is playing, finds or downloads
 * the lyrics file and feeds Playback. Also keeps the status shown by the in-game "console line".
 */
public final class SystemBridge {
    private static final DateTimeFormatter HMS = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "StolasLyrics-net");
        t.setDaemon(true);
        return t;
    });

    private static NowPlaying.Poller poller;
    private static boolean osUnsupportedLogged;
    private static String lastKey = "";
    private static Song trackSong;

    // ---- status for the HUD (read on the render thread)
    public static volatile int version = 0;
    public static String state = "off";          // off / none / playing / paused / error
    public static String app = "";
    public static String nowTitle = "";
    public static String nowArtist = "";
    public static String lyricsStatus = "";
    public static String lastError = "";
    public static int tracks, downloaded, hadOwn, notFound;
    private static final ArrayDeque<String> LOG = new ArrayDeque<>();

    private SystemBridge() {
    }

    public static synchronized void log(String msg) {
        String line = LocalTime.now().format(HMS) + " " + msg;
        if (!LOG.isEmpty() && LOG.peekLast().substring(9).equals(msg)) return;
        LOG.addLast(line);
        while (LOG.size() > 6) LOG.removeFirst();
        version++;
    }

    public static synchronized String[] lastLog(int n) {
        int size = Math.min(n, LOG.size());
        String[] out = new String[size];
        int skip = LOG.size() - size;
        int i = 0;
        int k = 0;
        for (String s : LOG) {
            if (k++ < skip) continue;
            out[i++] = s;
        }
        return out;
    }

    private static void setState(String s) {
        if (!s.equals(state)) {
            state = s;
            version++;
        }
    }

    private static void start() {
        poller = NowPlaying.create();
        if (poller == null) {
            if (!osUnsupportedLogged) {
                osUnsupportedLogged = true;
                log("Built-in bridge: " + NowPlaying.osName() + " is not supported (use File bridge)");
            }
            setState("off");
            return;
        }
        poller.start();
        log("Built-in bridge started (" + NowPlaying.osName() + ")");
        Runtime.getRuntime().addShutdownHook(new Thread(SystemBridge::stop));
    }

    public static void stop() {
        if (poller != null) {
            poller.stop();
            poller = null;
        }
    }

    /** Every client tick. */
    public static void tick() {
        Settings st = Settings.I;
        if (st.source != 1) {
            if (poller != null) stop();
            return;
        }
        if (poller == null && !osUnsupportedLogged) start();
        if (poller == null) return;

        PlayerInfo info = poller.latest();
        String err = poller.error();
        if (err != null && !err.equals(lastError)) {
            lastError = err;
            log(err);
        } else if (err == null) {
            lastError = "";
        }

        if (info == null) {
            setState(err != null ? "error" : "none");
            return; // nothing playing: leave the manual song alone
        }

        String key = info.title + "\u0001" + info.artist;
        if (!key.equals(lastKey)) {
            lastKey = key;
            onNewTrack(info);
        }
        setState(info.playing ? "playing" : "paused");
        if (!info.app.equals(app)) {
            app = info.app;
            version++;
        }
        if (trackSong != null) Playback.external(trackSong, info.posNow(), info.playing);
    }

    private static void onNewTrack(PlayerInfo info) {
        tracks++;
        nowTitle = info.title;
        nowArtist = info.artist;
        Song s = SongLibrary.findByTitle(info.title, info.artist);
        if (s == null) {
            s = new Song();
            s.id = SongLibrary.safeName(info.title, info.artist);
            s.title = info.title;
            s.artist = info.artist;
            s.prepare();
        }
        trackSong = s;
        final Song song = s;
        if (!s.lyrics.isEmpty()) {
            hadOwn++;
            lyricsStatus = "have (" + s.lyrics.size() + " lines)";
            log("Lyrics already here: " + label(info));
        } else if (Settings.I.autoLyrics) {
            lyricsStatus = "searching...";
            log("Searching lyrics: " + label(info));
            final String title = info.title;
            final String artist = info.artist;
            final double len = info.len;
            POOL.submit(() -> {
                String lrc = LrcLib.fetchSynced(title, artist, len);
                Minecraft.getInstance().execute(() -> applyLyrics(song, title, artist, lrc));
            });
        } else {
            lyricsStatus = "auto lyrics off";
        }
        version++;
    }

    private static String label(PlayerInfo i) {
        return i.artist.isEmpty() ? i.title : i.artist + " - " + i.title;
    }

    /** Runs on the game thread. */
    private static void applyLyrics(Song song, String title, String artist, String lrc) {
        boolean current = song == trackSong;
        String name = artist.isEmpty() ? title : artist + " - " + title;
        List<LrcParser.Chunk> chunks = lrc == null ? List.of() : LrcParser.build(LrcParser.parse(lrc), 2, 5.0);
        if (chunks.isEmpty()) {
            notFound++;
            if (current) lyricsStatus = "not found";
            log("Lyrics not found: " + name);
        } else {
            song.lyrics.clear();
            for (LrcParser.Chunk c : chunks) {
                Song.Line l = new Song.Line();
                l.time = c.time;
                l.text = c.text;
                l.effect = c.effect;
                l.out = c.out;
                l.duration = c.duration;
                song.lyrics.add(l);
            }
            song.prepare();
            downloaded++;
            if (current) lyricsStatus = "found (" + song.lyrics.size() + " lines)";
            log("Lyrics downloaded (" + song.lyrics.size() + " lines): " + name);
        }
        // save (also an empty stub, so the folder exists and can be filled by hand) and register the song
        try {
            SongLibrary.save(song);
        } catch (IOException e) {
            log("Could not save: " + e.getMessage());
        }
        SongLibrary.songs.put(song.id, song);
        version++;
    }
}
