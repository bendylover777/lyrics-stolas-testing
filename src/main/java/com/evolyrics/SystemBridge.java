package com.evolyrics;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
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
    public static volatile double len = 0;
    public static int tracks, downloaded, hadOwn, notFound;
    private static final ArrayDeque<String> LOG = new ArrayDeque<>();

    // ---- shuffle / repeat of the real player (0 off, 1 repeat all, 2 repeat one)
    public static boolean shuffle;
    public static int repeat;
    private static long modesLockUntil;

    // ---- tracks we have seen (the real player does not share its queue with other programs)
    private static final List<Song> history = new ArrayList<>();
    private static final ArrayDeque<Song> forward = new ArrayDeque<>();

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

    /** Is the built-in bridge running (so we can control the real player)? */
    public static boolean active() {
        return poller != null;
    }

    /** next / prev / toggle / seek (seconds). Runs in the background, no window. */
    public static void control(String cmd, double arg) {
        NowPlaying.Poller p = poller;
        if (p != null) p.command(cmd, arg);
    }

    /** Shuffle button: change at once, the player's own answer is ignored for a moment. */
    public static void setShuffle(boolean on) {
        shuffle = on;
        modesLockUntil = System.nanoTime() + 2_500_000_000L;
        control("shuffle", on ? 1 : 0);
    }

    /** Repeat button: 0 off, 1 all, 2 one. */
    public static void setRepeat(int mode) {
        repeat = mode;
        modesLockUntil = System.nanoTime() + 2_500_000_000L;
        control("repeat", mode);
    }

    /** The track that played before the current one (null = none seen yet). */
    public static Song historyPrev() {
        return history.isEmpty() ? null : history.get(history.size() - 1);
    }

    /** Known only after Previous was pressed: the track we came back from. */
    public static Song historyNext() {
        return forward.peek();
    }

    private static void trackChanged(Song old, Song now) {
        if (old == null || now == null || old == now || old.id.equals(now.id)) return;
        if (!history.isEmpty() && history.get(history.size() - 1).id.equals(now.id)) {
            history.remove(history.size() - 1);      // went back
            forward.push(old);
        } else {
            if (!forward.isEmpty() && forward.peek().id.equals(now.id)) forward.pop();   // went forward again
            else forward.clear();
            history.add(old);
            while (history.size() > 30) history.remove(0);
        }
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
        len = info.len;
        if (info.hasModes && System.nanoTime() > modesLockUntil) {
            shuffle = info.shuffle;
            repeat = info.repeat;
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
        trackChanged(trackSong, s);
        trackSong = s;
        final Song song = s;
        if (!s.lyrics.isEmpty()) {
            hadOwn++;
            lyricsStatus = "have (" + s.lyrics.size() + " lines)";
            log("Lyrics already here: " + label(info));
        } else if (Settings.I.autoLyrics) {
            fetchInto(song, info.title, info.artist, info.len, null);
        } else {
            lyricsStatus = "auto lyrics off";
        }
        version++;
    }

    private static String label(PlayerInfo i) {
        return i.artist.isEmpty() ? i.title : i.artist + " - " + i.title;
    }

    /** Looks the lyrics up again for the song that is playing now (hotkey / command). */
    public static void refetchCurrent() {
        Song s = trackSong != null ? trackSong : Playback.current;
        if (s == null) {
            log("Nothing to search lyrics for");
            return;
        }
        String title = s.title == null || s.title.isBlank() ? s.id : s.title;
        double dur = len > 0 ? len : Transport.length();
        fetchInto(s, title, s.artist == null ? "" : s.artist, dur, null);
    }

    /** "Artist - Title": searches the sites, saves the song and starts it. */
    public static void searchAndPlay(String text) {
        String artist = "";
        String title = text.trim();
        int dash = title.indexOf(" - ");
        if (dash > 0) {
            artist = title.substring(0, dash).trim();
            title = title.substring(dash + 3).trim();
        }
        Song s = SongLibrary.findByTitle(title, artist);
        if (s == null) {
            s = new Song();
            s.id = SongLibrary.safeName(title, artist);
            s.title = title;
            s.artist = artist;
            s.prepare();
        }
        final Song song = s;
        fetchInto(song, title, artist, 0, () -> {
            if (!song.lyrics.isEmpty()) {
                Settings.I.source = 0;                     // manual: the song is started by us, not by Spotify
                Settings.I.lastSong = song.id;
                Playback.play(song);
            }
        });
    }

    /** Searches in the background, then applies the result on the game thread. */
    private static void fetchInto(Song song, String title, String artist, double dur, Runnable after) {
        lyricsStatus = "searching...";
        String name = artist.isEmpty() ? title : artist + " - " + title;
        log("Searching lyrics: " + name);
        version++;
        POOL.submit(() -> {
            LyricsSources.Result res = LyricsSources.fetchSynced(title, artist, dur);
            Minecraft.getInstance().execute(() -> {
                applyLyrics(song, title, artist, res == null ? null : res.lrc, res == null ? "" : res.source);
                if (after != null) after.run();
            });
        });
    }

    /** Runs on the game thread. */
    private static void applyLyrics(Song song, String title, String artist, String lrc, String from) {
        boolean current = song == trackSong || song == Playback.current;
        String name = artist.isEmpty() ? title : artist + " - " + title;
        List<LrcParser.Chunk> chunks = lrc == null ? List.of()
            : LrcParser.build(LrcParser.parse(lrc), Settings.I.wordsPerChunk, 5.0);
        if (chunks.isEmpty()) {
            notFound++;
            if (current) lyricsStatus = "not found";
            log("Lyrics not found (tried all sites): " + name);
        } else {
            song.lrc = lrc;
            song.applyChunks(chunks);
            downloaded++;
            if (current) {
                lyricsStatus = "found (" + song.lyrics.size() + " lines, " + from + ")";
                Playback.generation++;                       // new lines: place them again
            }
            log("Lyrics downloaded from " + from + " (" + song.lyrics.size() + " lines): " + name);
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
