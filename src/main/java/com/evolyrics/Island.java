package com.evolyrics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/** iPhone-style "dynamic island" with the song title. Long titles scroll like a marquee. */
public final class Island {
    private static final int MAX_TEXT = 150;
    private static final double SPEED = 28.0;   // px per second
    private static final double PAUSE = 1.2;    // seconds at the start of each loop
    private static final int GAP = 36;

    private static Song cachedSong;
    private static String cachedFull = "";
    private static String cachedShort = "";
    private static int cachedW = 0;
    private static float anim;
    private static long last = System.nanoTime();

    private Island() {
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

        boolean overflow = cachedW > MAX_TEXT;
        boolean scroll = st.islandScroll && overflow;
        int tw = Math.min(cachedW, MAX_TEXT);
        int h = 20;
        int w = tw + 52;
        float e = 1f - (1f - anim) * (1f - anim);
        int x = (sw - w) / 2;
        int y = Math.round(5 - (1f - e) * 30);
        int glowRgb = st.glowColor & 0xFFFFFF;
        int textRgb = st.textColor & 0xFFFFFF;

        double t = now / 1e9;
        int pulse = 0x55 + (int) (0x22 * (Math.sin(t * 3.0) * 0.5 + 0.5));
        fillPill(g, x - 2, y - 2, w + 4, h + 4, (0x22 << 24) | glowRgb);
        fillPill(g, x - 1, y - 1, w + 2, h + 2, (pulse << 24) | glowRgb);
        fillPill(g, x, y, w, h, 0xF2000000);

        // icon
        fillPill(g, x + 8, y + 4, 12, 12, 0xFF000000 | glowRgb);
        fillPill(g, x + 12, y + 8, 4, 4, 0xFFFFFFFF);

        // title
        int tx = x + 26;
        int ty = y + 6;
        if (scroll) {
            double cycle = cachedW + GAP;
            double period = cycle / SPEED + PAUSE;
            double phase = t % period;
            int off = phase < PAUSE ? 0 : (int) Math.round((phase - PAUSE) * SPEED);
            g.enableScissor(tx, y, tx + tw, y + h);
            g.drawString(font, cachedFull, tx - off, ty, 0xFFFFFFFF, false);
            g.drawString(font, cachedFull, tx - off + (int) cycle, ty, 0xFFFFFFFF, false);
            g.disableScissor();
        } else {
            g.drawString(font, overflow ? cachedShort : cachedFull, tx, ty, 0xFFFFFFFF, false);
        }

        // equalizer
        boolean playing = Playback.isPlaying();
        int bx = x + w - 8 - 11;
        int base = y + 15;
        for (int i = 0; i < 4; i++) {
            int bh = playing ? 3 + (int) ((Math.sin(t * 7 + i * 1.7) + 1) * 3.5) : 3;
            g.fill(bx + i * 3, base - bh, bx + i * 3 + 2, base, 0xFF000000 | textRgb);
        }
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
