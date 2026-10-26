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
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Several free sites with time-synced lyrics (LRC). They are tried one after another until one has the song:
 * LRCLIB, NetEase Cloud Music, QQ Music, Kugou, Kuwo (each can be switched off in the menu), then plain lyrics
 * (LRCLIB / lyrics.ovh) with an estimated timing. No keys needed. Pure Java, called from a background thread only.
 * Every site is optional: if it is down or changed its API it is simply skipped.
 */
public final class LyricsSources {
    public static final class Result {
        public final String lrc;
        public final String source;

        Result(String lrc, String source) {
            this.lrc = lrc;
            this.source = source;
        }
    }

    private interface Source {
        String name();

        /** LRC text or null. May throw: the caller skips to the next site. */
        String fetch(String title, String artist, double duration) throws Exception;
    }

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
        + "Chrome/124.0 Safari/537.36 StolasLyrics/1.2";

    private static final Pattern CREDIT = Pattern.compile(
        "^\\[\\d+:\\d+(?:\\.\\d+)?\\]\\s*(?:作词|作曲|编曲|制作人?|监制|混音|母带|录音|词|曲|"
            + "lyrics?(?:\\s+by)?|lyricist|music(?:\\s+by)?|composer|composed(?:\\s+by)?|written(?:\\s+by)?|"
            + "produced(?:\\s+by)?|arranged(?:\\s+by)?|arranger)\\s*[:：].*$",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern STAMP = Pattern.compile("^\\[\\d+:\\d+(?:\\.\\d+)?\\].*");

    private static final List<Source> SOURCES = List.of(
        new Source() {
            public String name() {
                return "LRCLIB";
            }

            public String fetch(String t, String a, double d) {
                return LrcLib.fetchSynced(t, a, d);
            }
        },
        new Source() {
            public String name() {
                return "NetEase";
            }

            public String fetch(String t, String a, double d) throws Exception {
                return netease(t, a, d);
            }
        },
        new Source() {
            public String name() {
                return "QQ Music";
            }

            public String fetch(String t, String a, double d) throws Exception {
                return qq(t, a, d);
            }
        },
        new Source() {
            public String name() {
                return "Kugou";
            }

            public String fetch(String t, String a, double d) throws Exception {
                return kugou(t, a, d);
            }
        },
        new Source() {
            public String name() {
                return "Kuwo";
            }

            public String fetch(String t, String a, double d) throws Exception {
                return kuwo(t, a, d);
            }
        });

    private LyricsSources() {
    }

    /** Names of all sites, in the order they are tried. */
    public static String[] names() {
        String[] n = new String[SOURCES.size()];
        for (int i = 0; i < n.length; i++) n[i] = SOURCES.get(i).name();
        return n;
    }

    /**
     * First site that has synced lyrics for this song, or null. Sites switched off in the menu are skipped.
     * When no site has synced lyrics and "unsynced fallback" is on, plain lyrics are used with an estimated timing.
     */
    public static Result fetchSynced(String title, String artist, double duration) {
        Settings st = Settings.I;
        for (int i = 0; i < SOURCES.size(); i++) {
            if (!st.siteOn(i)) continue;
            Source s = SOURCES.get(i);
            try {
                String lrc = clean(s.fetch(title, artist, duration));
                if (lrc != null) return new Result(lrc, s.name());
            } catch (Exception ignored) {
                // site down / blocked / changed: try the next one
            }
        }
        if (st.allowUnsynced) {
            try {
                String lrc = spread(LrcLib.fetchPlain(title, artist, duration), duration);
                if (lrc != null) return new Result(lrc, "LRCLIB (plain, timing estimated)");
            } catch (Exception ignored) {
            }
            try {
                String lrc = spread(lyricsOvh(title, artist), duration);
                if (lrc != null) return new Result(lrc, "lyrics.ovh (plain, timing estimated)");
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ Kuwo

    private static String kuwo(String title, String artist, double dur) throws Exception {
        String ref = "https://www.kuwo.cn/";
        String body = http("https://search.kuwo.cn/r.s?client=kt&all=" + enc(query(title, artist))
            + "&pn=0&rn=10&uid=794762570&ver=kwplayer_ar_9.2.2.1&vipver=1&show_copyright_off=1&newver=1"
            + "&ft=music&cluster=0&strategy=2012&encoding=utf8&rformat=json&vermerge=1&mobi=1&issubtitle=1", ref);
        // the answer is "almost JSON" (single quotes): JsonParser.parseString is lenient and accepts it
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonArray list = arr(root, "abslist");
        if (list == null) return null;
        String id = null;
        double bestDiff = Double.MAX_VALUE;
        for (JsonElement el : list) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            double diff = score(title, artist, dur, str(o, "SONGNAME"), str(o, "ARTIST"), num(o, "DURATION"));
            if (diff >= 0 && diff < bestDiff) {
                bestDiff = diff;
                id = str(o, "MUSICRID").replace("MUSIC_", "");
            }
        }
        if (id == null || id.isEmpty()) return null;
        String lyr = http("https://m.kuwo.cn/newh5/singles/songinfoandlrc?musicId=" + enc(id), ref);
        JsonObject data = obj(JsonParser.parseString(lyr), "data");
        JsonArray lines = data == null ? null : arr(data, "lrclist");
        if (lines == null) return null;
        StringBuilder b = new StringBuilder();
        for (JsonElement el : lines) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            String text = str(o, "lineLyric").trim();
            double t = num(o, "time");
            if (text.isEmpty()) continue;
            int m = (int) (t / 60);
            b.append(String.format(Locale.ROOT, "[%02d:%05.2f]%s%n", m, t - m * 60, text));
        }
        return b.length() == 0 ? null : b.toString();
    }

    // ------------------------------------------------------------------ plain lyrics (no time codes)

    private static String lyricsOvh(String title, String artist) throws Exception {
        String a = enc(firstArtist(artist)).replace("+", "%20");
        String t = enc(baseTitle(title)).replace("+", "%20");
        if (a.isEmpty() || t.isEmpty()) return null;
        String body = http("https://api.lyrics.ovh/v1/" + a + "/" + t, null);
        String text = str(JsonParser.parseString(body).getAsJsonObject(), "lyrics");
        return text.isBlank() ? null : text;
    }

    /**
     * Plain lyrics -> LRC with an even timing over the song (intro and outro left free).
     * Not exact, but the sync-offset slider and hotkeys can still move it.
     */
    static String spread(String plain, double dur) {
        if (plain == null || plain.isBlank()) return null;
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (String raw : plain.split("\\R")) {
            String l = raw.trim();
            if (l.isEmpty()) continue;
            if (l.startsWith("[") && l.endsWith("]")) continue;      // [Verse 1], [Chorus] ...
            lines.add(l);
        }
        if (lines.size() < 4) return null;
        double total = dur > 20 ? dur : lines.size() * 3.5 + 12;
        double start = Math.min(14.0, total * 0.07);
        double end = total - Math.min(12.0, total * 0.06);
        double step = Math.max(1.2, (end - start) / lines.size());
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            double t = start + i * step;
            int m = (int) (t / 60);
            b.append(String.format(Locale.ROOT, "[%02d:%05.2f]%s%n", m, t - m * 60, lines.get(i)));
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ NetEase Cloud Music

    private static String netease(String title, String artist, double dur) throws Exception {
        String ref = "https://music.163.com/";
        String body = http("https://music.163.com/api/search/get/web?csrf_token=&hlpretag=&hlposttag=&type=1&offset=0"
            + "&total=true&limit=10&s=" + enc(query(title, artist)), ref);
        JsonObject res = obj(JsonParser.parseString(body), "result");
        JsonArray songs = res == null ? null : arr(res, "songs");
        if (songs == null) return null;
        String id = null;
        double bestDiff = Double.MAX_VALUE;
        for (JsonElement el : songs) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            String name = str(o, "name");
            String who = names(o.has("artists") ? arr(o, "artists") : arr(o, "ar"));
            double secs = num(o, o.has("duration") ? "duration" : "dt") / 1000.0;
            double diff = score(title, artist, dur, name, who, secs);
            if (diff >= 0 && diff < bestDiff) {
                bestDiff = diff;
                id = str(o, "id");
            }
        }
        if (id == null || id.isEmpty()) return null;
        String lyr = http("https://music.163.com/api/song/lyric?lv=1&kv=1&tv=-1&id=" + enc(id), ref);
        return str(obj(JsonParser.parseString(lyr), "lrc"), "lyric");
    }

    // ------------------------------------------------------------------ QQ Music

    private static String qq(String title, String artist, double dur) throws Exception {
        String ref = "https://y.qq.com/";
        String body = http("https://c.y.qq.com/soso/fcgi-bin/client_search_cp?ct=24&qqmusic_ver=1298&new_json=1"
            + "&remoteplace=txt.yqq.song&t=0&aggr=1&cr=1&catZhida=1&lossless=0&flag_qc=0&p=1&n=10"
            + "&format=json&inCharset=utf8&outCharset=utf-8&platform=yqq.json&w=" + enc(query(title, artist)), ref);
        JsonObject data = obj(JsonParser.parseString(body), "data");
        JsonObject song = data == null ? null : obj(data, "song");
        JsonArray list = song == null ? null : arr(song, "list");
        if (list == null) return null;
        String mid = null;
        double bestDiff = Double.MAX_VALUE;
        for (JsonElement el : list) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            String name = str(o, o.has("name") ? "name" : o.has("songname") ? "songname" : "title");
            String who = names(arr(o, "singer"));
            double secs = num(o, "interval");
            double diff = score(title, artist, dur, name, who, secs);
            if (diff >= 0 && diff < bestDiff) {
                bestDiff = diff;
                mid = str(o, o.has("mid") ? "mid" : "songmid");
            }
        }
        if (mid == null || mid.isEmpty()) return null;
        String lyr = http("https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?g_tk=5381&format=json"
            + "&inCharset=utf8&outCharset=utf-8&nobase64=0&songmid=" + enc(mid), ref);
        String b64 = str(JsonParser.parseString(lyr).getAsJsonObject(), "lyric");
        if (b64.isEmpty()) return null;
        return new String(Base64.getMimeDecoder().decode(b64), StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ Kugou

    private static String kugou(String title, String artist, double dur) throws Exception {
        String body = http("https://mobilecdn.kugou.com/api/v3/search/song?format=json&page=1&pagesize=10&showtype=1"
            + "&keyword=" + enc(query(title, artist)), null);
        JsonObject data = obj(JsonParser.parseString(body), "data");
        JsonArray info = data == null ? null : arr(data, "info");
        if (info == null) return null;
        String hash = null;
        double secs = 0;
        double bestDiff = Double.MAX_VALUE;
        for (JsonElement el : info) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            double d = num(o, "duration");
            double diff = score(title, artist, dur, str(o, "songname"), str(o, "singername"), d);
            if (diff >= 0 && diff < bestDiff) {
                bestDiff = diff;
                hash = str(o, "hash");
                secs = d;
            }
        }
        if (hash == null || hash.isEmpty()) return null;
        String found = http("https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&hash=" + enc(hash)
            + "&duration=" + Math.round(secs * 1000) + "&keyword=" + enc(query(title, artist)), null);
        JsonArray cand = arr(JsonParser.parseString(found).getAsJsonObject(), "candidates");
        if (cand == null || cand.isEmpty() || !cand.get(0).isJsonObject()) return null;
        JsonObject c = cand.get(0).getAsJsonObject();
        String id = str(c, "id");
        String key = str(c, "accesskey");
        if (id.isEmpty() || key.isEmpty()) return null;
        String dl = http("https://lyrics.kugou.com/download?ver=1&client=pc&fmt=lrc&charset=utf8&id=" + enc(id)
            + "&accesskey=" + enc(key), null);
        String b64 = str(JsonParser.parseString(dl).getAsJsonObject(), "content");
        if (b64.isEmpty()) return null;
        String text = new String(Base64.getMimeDecoder().decode(b64), StandardCharsets.UTF_8);
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    // ------------------------------------------------------------------ matching

    private static String query(String title, String artist) {
        return (baseTitle(title) + " " + firstArtist(artist)).trim();
    }

    static String firstArtist(String artist) {
        if (artist == null) return "";
        String[] parts = artist.split("\\s*(?:,|&|;|/|\\bfeat\\.?|\\bft\\.?)\\s*");
        return parts.length == 0 ? "" : parts[0].trim();
    }

    /** Title without "(feat. ...)", "[Remastered]" and a trailing " - Remastered 2011". */
    static String baseTitle(String t) {
        if (t == null) return "";
        String s = t.replaceAll("[\\(\\[（【][^\\)\\]）】]*[\\)\\]）】]", " ");
        s = s.replaceAll("\\s+-\\s+.*$", "");
        return s.trim();
    }

    private static String norm(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c)) b.append(c);
        }
        return b.toString();
    }

    private static boolean titleOk(String want, String got) {
        String a = norm(baseTitle(want));
        String b = norm(baseTitle(got));
        if (a.isEmpty() || b.isEmpty()) return false;
        return a.equals(b) || (a.length() >= 4 && b.contains(a)) || (b.length() >= 4 && a.contains(b));
    }

    private static boolean artistOk(String want, String got) {
        String q = norm(want);
        String c = norm(got);
        if (q.isEmpty() || c.isEmpty()) return true;
        if (c.contains(q) || q.contains(c)) return true;
        for (String part : want.split("\\s*(?:,|&|;|/|\\bfeat\\.?|\\bft\\.?)\\s*")) {
            String p = norm(part);
            if (p.length() >= 2 && c.contains(p)) return true;
        }
        return false;
    }

    /** -1 = not this song, otherwise the length difference in seconds (smaller is better). */
    private static double score(String title, String artist, double dur, String gotTitle, String gotArtist, double gotDur) {
        if (!titleOk(title, gotTitle) || !artistOk(artist, gotArtist)) return -1;
        if (dur > 0 && gotDur > 0) {
            double d = Math.abs(dur - gotDur);
            return d <= 8 ? d : -1;
        }
        return 0;
    }

    /** Needs real timed lines; drops "作词 : ..." / "Lyrics by: ..." credit rows. Returns null when it does not look like LRC. */
    private static String clean(String lrc) {
        if (lrc == null || lrc.isBlank()) return null;
        StringBuilder out = new StringBuilder();
        int lines = 0;
        for (String raw : lrc.split("\\R")) {
            String l = raw.trim();
            if (!STAMP.matcher(l).matches()) continue;
            if (CREDIT.matcher(l).matches()) continue;
            out.append(l).append('\n');
            lines++;
        }
        return lines >= 4 ? out.toString() : null;
    }

    // ------------------------------------------------------------------ http / json helpers

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private static String http(String url, String referer) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(6000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept", "application/json, text/plain, */*");
        if (referer != null) c.setRequestProperty("Referer", referer);
        try (InputStream in = c.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            c.disconnect();
        }
    }

    private static JsonObject obj(JsonElement e, String key) {
        if (e == null || !e.isJsonObject()) return null;
        return obj(e.getAsJsonObject(), key);
    }

    private static JsonObject obj(JsonObject o, String key) {
        if (o == null) return null;
        JsonElement e = o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    private static JsonArray arr(JsonObject o, String key) {
        if (o == null) return null;
        JsonElement e = o.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : null;
    }

    private static String str(JsonObject o, String key) {
        if (o == null) return "";
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) return "";
        return e.getAsString();
    }

    private static double num(JsonObject o, String key) {
        try {
            String s = str(o, key);
            return s.isEmpty() ? 0 : Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String names(JsonArray a) {
        if (a == null) return "";
        StringBuilder b = new StringBuilder();
        for (JsonElement e : a) {
            if (e.isJsonObject()) b.append(str(e.getAsJsonObject(), "name")).append(' ');
        }
        return b.toString().trim();
    }
}
