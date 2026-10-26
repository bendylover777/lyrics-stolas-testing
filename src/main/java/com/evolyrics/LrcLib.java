package com.evolyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Free synced-lyrics service (lrclib.net). Called from a background thread only. */
public final class LrcLib {
    private static final String BASE = "https://lrclib.net/api/";

    private LrcLib() {
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private static String http(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent", "StolasLyrics/1.2 (Minecraft mod)");
        try (InputStream in = c.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            c.disconnect();
        }
    }

    private static String field(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) return null;
        String s = e.getAsString();
        return s.isBlank() ? null : s;
    }

    /** Returns LRC text, or null when nothing was found. */
    public static String fetchSynced(String title, String artist, double duration) {
        String r = fetch(title, artist, duration, "syncedLyrics");
        if (r != null) return r;
        // second try: title without "(feat. ...)", "- Remastered" and only the first artist
        String t2 = LyricsSources.baseTitle(title);
        String a2 = LyricsSources.firstArtist(artist);
        if (!t2.isEmpty() && (!t2.equals(title) || !a2.equals(artist))) {
            return fetch(t2, a2, duration, "syncedLyrics");
        }
        return null;
    }

    /** Plain text lyrics (no time codes), or null. */
    public static String fetchPlain(String title, String artist, double duration) {
        String r = fetch(title, artist, duration, "plainLyrics");
        if (r != null) return r;
        String t2 = LyricsSources.baseTitle(title);
        String a2 = LyricsSources.firstArtist(artist);
        return t2.isEmpty() ? null : fetch(t2, a2, duration, "plainLyrics");
    }

    private static String fetch(String title, String artist, double duration, String key) {
        StringBuilder q = new StringBuilder("track_name=").append(enc(title)).append("&artist_name=").append(enc(artist));
        if (duration > 0) q.append("&duration=").append(Math.round(duration));
        try {
            String s = field(JsonParser.parseString(http(BASE + "get?" + q)).getAsJsonObject(), key);
            if (s != null) return s;
        } catch (Exception ignored) {
        }
        // free text search, then title-only search (the artist is checked by name)
        String[] queries = {"q=" + enc((artist + " " + title).trim()), "track_name=" + enc(title)};
        for (String qs : queries) {
            try {
                JsonArray arr = JsonParser.parseString(http(BASE + "search?" + qs)).getAsJsonArray();
                String best = null;
                double bestDiff = Double.MAX_VALUE;
                for (JsonElement el : arr) {
                    JsonObject o = el.getAsJsonObject();
                    String s = field(o, key);
                    if (s == null) continue;
                    if (qs.startsWith("track_name=") && !artistMatches(artist, field(o, "artistName"))) continue;
                    double d = o.has("duration") && !o.get("duration").isJsonNull() ? o.get("duration").getAsDouble() : 0;
                    double diff = duration > 0 ? Math.abs(d - duration) : 0;
                    if (diff < bestDiff) {
                        bestDiff = diff;
                        best = s;
                    }
                }
                if (best != null && (duration <= 0 || bestDiff <= 5)) return best;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static boolean artistMatches(String want, String got) {
        if (want == null || want.isBlank() || got == null) return true;
        String a = want.toLowerCase().replaceAll("[^\\p{L}\\p{N}]", "");
        String b = got.toLowerCase().replaceAll("[^\\p{L}\\p{N}]", "");
        return a.isEmpty() || b.isEmpty() || a.contains(b) || b.contains(a);
    }
}
