package com.evolyrics;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public final class SongLibrary {
    public static final Map<String, Song> songs = new LinkedHashMap<>();

    private static Path dirCache;

    /** config/stolas (the old config/evolyrics folder is moved here automatically). */
    public static Path dir() {
        if (dirCache != null) return dirCache;
        Path root = FMLPaths.CONFIGDIR.get();
        Path fresh = root.resolve("stolas");
        Path old = root.resolve("evolyrics");
        if (!Files.exists(fresh) && Files.isDirectory(old)) {
            try {
                Files.move(old, fresh);
            } catch (IOException e) {
                dirCache = old;
                return old;
            }
        }
        dirCache = fresh;
        return fresh;
    }

    public static Path songsDir() {
        return dir().resolve("songs");
    }

    /** Same folder naming as the Python bridge: "Artist - Title" without forbidden characters. */
    public static String safeName(String title, String artist) {
        String t = artist == null || artist.isBlank() ? title : artist + " - " + title;
        t = t.replaceAll("[\\\\/:*?\"<>|]", "").trim();
        while (t.endsWith(".")) t = t.substring(0, t.length() - 1).trim();
        if (t.length() > 80) t = t.substring(0, 80);
        return t.isEmpty() ? "unknown" : t;
    }

    public static Path fileOf(Song s) {
        return songsDir().resolve(s.id).resolve("lyrics.json");
    }

    public static void reload() {
        songs.clear();
        try {
            Files.createDirectories(songsDir());
            importLrcFiles();
            try (Stream<Path> st = Files.list(songsDir())) {
                st.filter(Files::isDirectory).sorted().forEach(p -> {
                    Path f = p.resolve("lyrics.json");
                    if (!Files.isRegularFile(f)) return;
                    try {
                        Song s = Song.read(f);
                        s.id = p.getFileName().toString();
                        s.prepare();
                        songs.put(s.id, s);
                    } catch (Exception ignored) {
                    }
                });
            }
            if (songs.isEmpty()) createExample();
        } catch (IOException ignored) {
        }
    }

    /**
     * Drop "Artist - Title.lrc" into the songs folder and it becomes a song on the next reload
     * (the file is moved into its own folder as source.lrc).
     */
    private static void importLrcFiles() {
        try (Stream<Path> st = Files.list(songsDir())) {
            for (Path f : (Iterable<Path>) st.filter(Files::isRegularFile)::iterator) {
                String fn = f.getFileName().toString();
                if (!fn.toLowerCase(Locale.ROOT).endsWith(".lrc")) continue;
                try {
                    String base = fn.substring(0, fn.length() - 4);
                    String text = Files.readString(f, StandardCharsets.UTF_8);
                    if (text.startsWith("\uFEFF")) text = text.substring(1);
                    java.util.List<LrcParser.Chunk> chunks = LrcParser.build(LrcParser.parse(text), Settings.I.wordsPerChunk, 5.0);
                    if (chunks.isEmpty()) continue;
                    Song s = new Song();
                    int dash = base.indexOf(" - ");
                    s.artist = dash > 0 ? base.substring(0, dash).trim() : "";
                    s.title = dash > 0 ? base.substring(dash + 3).trim() : base.trim();
                    s.id = safeName(s.title, s.artist);
                    s.lrc = text;
                    s.applyChunks(chunks);
                    Path target = songsDir().resolve(s.id);
                    Files.createDirectories(target);
                    s.save(target.resolve("lyrics.json"));
                    Files.move(f, target.resolve("source.lrc"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** Builds every downloaded song again with the current "words per chunk". Returns how many songs changed. */
    public static int rechunkAll(int words) {
        int n = 0;
        for (Song s : songs.values()) {
            try {
                if (s.rechunk(words)) {
                    save(s);
                    n++;
                }
            } catch (Exception ignored) {
            }
        }
        Playback.generation++;
        return n;
    }

    private static void createExample() throws IOException {
        Song s = new Song();
        s.id = "example";
        s.title = "Example";
        s.artist = "Stolas";
        s.addLine(1.0, "STOLAS", "pop");
        s.addLine(3.0, "rise", "rise");
        s.addLine(5.0, "fade in", "fade");
        s.addLine(7.0, "scale in", "scale_in");
        s.addLine(9.0, "scale out", "scale_out");
        s.addLine(11.0, "slide left", "slide_left");
        s.addLine(13.0, "slide right", "slide_right");
        s.addLine(15.0, "float", "float");
        s.addLine(17.0, "rotate", "rotate");
        s.addLine(20.0, "the end", "fade");
        s.save(fileOf(s));
        s.prepare();
        songs.put(s.id, s);
    }

    public static void save(Song s) throws IOException {
        s.save(fileOf(s));
    }

    public static Song find(String key) {
        if (key == null) return null;
        Song s = songs.get(key);
        if (s != null) return s;
        String n = norm(key);
        for (Song c : songs.values()) {
            if (norm(c.id).equals(n)) return c;
        }
        return null;
    }

    public static Song findByTitle(String title, String artist) {
        if (title == null || title.isBlank()) return null;
        String t = norm(title);
        String a = artist == null ? "" : norm(artist);
        for (Song c : songs.values()) {
            String ct = norm(c.title);
            String ca = norm(c.artist);
            if (!ct.isEmpty() && (ct.equals(t) || (ct + ca).equals(t + a) || (ca + ct).equals(t + a))) return c;
            String ci = norm(c.id);
            if (ci.equals(t) || ci.equals(a + t) || ci.equals(t + a)) return c;
        }
        return null;
    }

    private static String norm(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c)) b.append(c);
        }
        return b.toString();
    }
}
