package com.evolyrics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.List;

/**
 * iPhone-style "dynamic island": song title on top (marquee if long) and a running
 * lyrics ticker underneath that glides to the current line.
 */
public final class Island {
    private static final int MAX_TEXT = 150;
    private static final double SPEED = 28.0;   // px per second (title marquee)
    private static final double PAUSE = 1.2;    // seconds at the start of each marquee loop
    private static final int GAP = 36;
    private static final int TICK_GAP = 7;

    private static Song cachedSong;
    private static String cachedFull = "";
    private static String cachedShort = "";
    private static int cachedW = 0;
    private static boolean cachedHas = false;
    private static float anim;
    private static long last = System.nanoTime();

    private static Song tickSong;
    private static int tickCount = -1;
    private static float[] tickX = new float[0];
    private static float[] tickW = new float[0];

    private Island() {
    }

    private static void buildTicker(Song s, Font font) {
        int n = s.lyrics.size();
        tickX = new float[n];
        tickW = new float[n];
        float x = 0f;
        for (int i = 0; i < n; i++) {
            float w = font.width(s.lyrics.get(i).text);
            tickX[i] = x;
            tickW[i] = w;
            x += w + TICK_GAP;
        }
        tickCount = n;
        tickSong = s;
    }

    public static void render(ForgeGui gui, GuiGraphics g, float pt, int sw, int sh) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - last) / 1e9f);
        last = now;

        Settings st = Settings.I;
        Song s = Playback.current;
        boolean show = st.island && s != null;
        anim += (show ? dt : -dt) * 4f;
        if (anim > 1f) anim = 1f;
        if (anim <= 0.001f) {
            anim = 0f;
            return;
        }

        Font font = Minecraft.getInstance().font;
        if (s != null && s != cachedSong) {
            cachedSong = s;
            String title = s.title == null || s.title.isBlank() ? s.id : s.title;
            cachedFull = s.artist == null || s.artist.isBlank() ? title : title + " - " + s.artist;
            cachedW = font.width(cachedFull);
            cachedShort = cachedW <= MAX_TEXT ? cachedFull
                : font.plainSubstrByWidth(cachedFull, MAX_TEXT - font.width("...")) + "...";
        }
        if (s != null) {
            cachedHas = st.islandLyrics && !s.lyrics.isEmpty();
            if (cachedHas && (s != tickSong || s.lyrics.size() != tickCount)) buildTicker(s, font);
        }

        boolean overflow = cachedW > MAX_TEXT;
        boolean scroll = st.islandScroll && overflow;
        int tw = cachedHas ? MAX_TEXT : Math.min(cachedW, MAX_TEXT);
        int h = cachedHas ? 32 : 20;
        int w = tw + 52;
        float e = 1f - (1f - anim) * (1f - anim);
        int x = (sw - w) / 2;
        int y = Math.round(5 - (1f - e) * 40);
        int glowRgb = st.glowColor & 0xFFFFFF;
        int textRgb = st.textColor & 0xFFFFFF;

        double t = now / 1e9;
        int pulse = 0x55 + (int) (0x22 * (Math.sin(t * 3.0) * 0.5 + 0.5));
        fillPill(g, x - 2, y - 2, w + 4, h + 4, (0x22 << 24) | glowRgb);
        fillPill(g, x - 1, y - 1, w + 2, h + 2, (pulse << 24) | glowRgb);
        fillPill(g, x, y, w, h, 0xF2000000);

        // icon
        int iy = y + (h - 12) / 2;
        fillPill(g, x + 8, iy, 12, 12, 0xFF000000 | glowRgb);
        fillPill(g, x + 12, iy + 4, 4, 4, 0xFFFFFFFF);

        // title row
        int tx = x + 26;
        int ty = cachedHas ? y + 5 : y + 6;
        int rowH = cachedHas ? 15 : h;
        if (scroll) {
            double cycle = cachedW + GAP;
            double period = cycle / SPEED + PAUSE;
            double phase = t % period;
            int off = phase < PAUSE ? 0 : (int) Math.round((phase - PAUSE) * SPEED);
            g.enableScissor(tx, y, tx + tw, y + rowH);
            g.drawString(font, cachedFull, tx - off, ty, 0xFFFFFFFF, false);
            g.drawString(font, cachedFull, tx - off + (int) cycle, ty, 0xFFFFFFFF, false);
            g.disableScissor();
        } else {
            g.drawString(font, overflow ? cachedShort : cachedFull, tx, ty, 0xFFFFFFFF, false);
        }

        // running lyrics ticker
        if (cachedHas && s != null && tickSong == s && tickCount > 0) {
            g.fill(tx, y + 16, tx + tw, y + 17, (0x30 << 24) | glowRgb);
            drawTicker(g, font, s, tx, y, tw, h, glowRgb);
        }

        // equalizer
        boolean playing = Playback.isPlaying();
        int bx = x + w - 8 - 11;
        int base = y + h / 2 + 5;
        for (int i = 0; i < 4; i++) {
            int bh = playing ? 3 + (int) ((Math.sin(t * 7 + i * 1.7) + 1) * 3.5) : 3;
            g.fill(bx + i * 3, base - bh, bx + i * 3 + 2, base, 0xFF000000 | textRgb);
        }
    }

    private static void drawTicker(GuiGraphics g, Font font, Song s, int tx, int y, int tw, int h, int glowRgb) {
        List<Song.Line> ls = s.lyrics;
        int n = tickCount;
        double pos = Playback.position() + Settings.I.syncOffset;
        int idx = indexAt(ls, pos);

        float center;
        if (idx < 0) {
            center = tickX[0] + tickW[0] / 2f;
        } else {
            center = tickX[idx] + tickW[idx] / 2f;
            if (idx + 1 < n) {
                double b = ls.get(idx + 1).time;
                float gk = (float) ((pos - (b - 0.3)) / 0.3);
                if (gk > 0f) {
                    if (gk > 1f) gk = 1f;
                    float sm = gk * gk * (3f - 2f * gk);
                    float nextC = tickX[idx + 1] + tickW[idx + 1] / 2f;
                    center += (nextC - center) * sm;
                }
            }
        }

        int cx0 = tx + tw / 2;
        int from = Math.max(0, idx - 5);
        int to = Math.min(n - 1, Math.max(idx, 0) + 6);
        g.enableScissor(tx, y + 17, tx + tw, y + h - 3);
        for (int i = from; i <= to; i++) {
            int sx = cx0 + Math.round(tickX[i] - center);
            if (sx > tx + tw || sx + tickW[i] < tx) continue;
            int col = i == idx ? 0xFFFFFFFF : (i < idx ? 0x80FFFFFF : 0xB0B8C0FF);
            g.drawString(font, ls.get(i).text, sx, y + 19, col, false);
        }
        g.disableScissor();
    }

    private static int indexAt(List<Song.Line> ls, double t) {
        int lo = 0, hi = ls.size() - 1, r = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (ls.get(mid).time <= t) {
                r = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return r;
    }

    /** Stadium (fully rounded) rectangle using 1px rows, no allocations. */
    private static void fillPill(GuiGraphics g, int x, int y, int w, int h, int color) {
        float r = h / 2f;
        for (int i = 0; i < h; i++) {
            float dy = Math.abs(i + 0.5f - r);
            int inset = Math.round(r - (float) Math.sqrt(Math.max(0f, r * r - dy * dy)));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
        }
    }
}
