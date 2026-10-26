package com.evolyrics;

import net.minecraft.client.gui.GuiGraphics;

/** Small drawing helpers shared by the island and the menu: rounded shapes, easing and player icons. */
public final class Gfx {
    public static final int GREEN = 0x1ED760;   // "active" colour of shuffle / repeat (like the Spotify bar)

    private Gfx() {
    }

    // ------------------------------------------------------------------ colours

    /** rgb + alpha (0..1) -> ARGB */
    public static int argb(int rgb, float alpha) {
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    /** Multiplies the alpha of an ARGB colour. */
    public static int fade(int argb, float k) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, k)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    // ------------------------------------------------------------------ easing

    public static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }

    public static float easeOut(float p) {
        float q = 1f - clamp01(p);
        return 1f - q * q * q;
    }

    /** Falls and bounces a few times, never goes past 1 (so a dropped panel only ever bounces upwards). */
    public static float bounce(float p) {
        p = clamp01(p);
        final float n = 7.5625f;
        final float d = 2.75f;
        if (p < 1f / d) return n * p * p;
        if (p < 2f / d) {
            p -= 1.5f / d;
            return n * p * p + 0.75f;
        }
        if (p < 2.5f / d) {
            p -= 2.25f / d;
            return n * p * p + 0.9375f;
        }
        p -= 2.625f / d;
        return n * p * p + 0.984375f;
    }

    // ------------------------------------------------------------------ shapes

    /** Rounded rectangle from 1px rows (no allocations). r = h/2 gives a full oval ("pill"). */
    public static void fillRound(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int i = 0; i < h; i++) {
            int inset = 0;
            if (i < r || i >= h - r) {
                float dy = i < r ? (r - i - 0.5f) : (i - (h - r) + 0.5f);
                inset = Math.round(r - (float) Math.sqrt(Math.max(0f, (float) r * r - dy * dy)));
            }
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
        }
    }

    public static void fillPill(GuiGraphics g, int x, int y, int w, int h, int color) {
        fillRound(g, x, y, w, h, h / 2, color);
    }

    public static void fillCircle(GuiGraphics g, int cx, int cy, int r, int color) {
        fillRound(g, cx - r, cy - r, r * 2, r * 2, r, color);
    }

    // ------------------------------------------------------------------ icons (centred on cx, cy; about 9 px tall)

    private static void tri(GuiGraphics g, int x, int y, int w, int h, boolean right, int c) {
        for (int k = 0; k < w; k++) {
            float f = right ? 1f - k / (float) w : (k + 1) / (float) w;
            int half = Math.max(0, Math.round(h / 2f * f));
            int px = x + k;
            g.fill(px, y + h / 2 - half, px + 1, y + h / 2 + half + 1, c);
        }
    }

    public static void iconPlay(GuiGraphics g, int cx, int cy, int c) {
        tri(g, cx - 3, cy - 4, 8, 9, true, c);
    }

    public static void iconPause(GuiGraphics g, int cx, int cy, int c) {
        g.fill(cx - 4, cy - 4, cx - 1, cy + 5, c);
        g.fill(cx + 1, cy - 4, cx + 4, cy + 5, c);
    }

    public static void iconNext(GuiGraphics g, int cx, int cy, int c) {
        tri(g, cx - 5, cy - 4, 7, 9, true, c);
        g.fill(cx + 3, cy - 4, cx + 5, cy + 5, c);
    }

    public static void iconPrev(GuiGraphics g, int cx, int cy, int c) {
        g.fill(cx - 5, cy - 4, cx - 3, cy + 5, c);
        tri(g, cx - 3, cy - 4, 7, 9, false, c);
    }

    public static void iconShuffle(GuiGraphics g, int cx, int cy, int c) {
        for (int t = 0; t <= 9; t++) {
            int yy = Math.round(t * 7f / 9f);
            g.fill(cx - 5 + t, cy - 3 + yy, cx - 4 + t, cy - 2 + yy, c);   // top-left -> bottom-right
            g.fill(cx - 5 + t, cy + 3 - yy, cx - 4 + t, cy + 4 - yy, c);   // bottom-left -> top-right
        }
        g.fill(cx + 3, cy - 5, cx + 7, cy - 4, c);
        g.fill(cx + 5, cy - 6, cx + 6, cy - 3, c);
        g.fill(cx + 3, cy + 4, cx + 7, cy + 5, c);
        g.fill(cx + 5, cy + 3, cx + 6, cy + 6, c);
    }

    /** Loop icon; {@code one} adds a small bar in the middle (repeat one). */
    public static void iconRepeat(GuiGraphics g, int cx, int cy, boolean one, int c) {
        g.fill(cx - 5, cy - 4, cx + 3, cy - 3, c);   // top line
        g.fill(cx - 6, cy - 3, cx - 5, cy + 3, c);   // left side
        g.fill(cx - 3, cy + 3, cx + 6, cy + 4, c);   // bottom line
        g.fill(cx + 5, cy - 2, cx + 6, cy + 3, c);   // right side
        g.fill(cx + 3, cy - 6, cx + 4, cy - 1, c);   // arrow head, top right
        g.fill(cx + 4, cy - 5, cx + 5, cy - 2, c);
        g.fill(cx - 5, cy + 1, cx - 4, cy + 6, c);   // arrow head, bottom left
        g.fill(cx - 6, cy + 2, cx - 5, cy + 5, c);
        if (one) {
            g.fill(cx - 1, cy - 2, cx, cy + 3, c);
            g.fill(cx - 2, cy - 1, cx - 1, cy, c);
        }
    }
}
