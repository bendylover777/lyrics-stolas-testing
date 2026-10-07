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
        c.setRequestProperty("User-Agent", "StolasLyrics/1.1 (Minecraft mod)");
        try (InputStream in = c.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            c.disconnect();
        }
    }

    private static String synced(JsonObject o) {
        JsonElement e = o.get("syncedLyrics");
        if (e == null || e.isJsonNull()) return null;
        String s = e.getAsString();
        return s.isBlank() ? null : s;
    }

    /** Returns LRC text, or null when nothing was found. */
    public static String fetchSynced(String title, String artist, double duration) {
        StringBuilder q = new StringBuilder("track_name=").append(enc(title)).append("&artist_name=").append(enc(artist));
        if (duration > 0) q.append("&duration=").append(Math.round(duration));
        try {
            String s = synced(JsonParser.parseString(http(BASE + "get?" + q)).getAsJsonObject());
            if (s != null) return s;
        } catch (Exception ignored) {
        }
        try {
            JsonArray arr = JsonParser.parseString(http(BASE + "search?q=" + enc((artist + " " + title).trim()))).getAsJsonArray();
            String best = null;
            double bestDiff = Double.MAX_VALUE;
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                String s = synced(o);
                if (s == null) continue;
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
        return null;
    }
}
