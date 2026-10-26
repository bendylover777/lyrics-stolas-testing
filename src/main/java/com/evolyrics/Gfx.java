package com.evolyrics;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Small drawing helpers shared by the island and the menu: rounded shapes, easing and player icons.
 *
 * Rounded shapes are built from a cached "profile" per radius: rows with the same corner inset are merged
 * into one fill, so a pill costs ~10-20 fills instead of one fill per pixel row (that was the main FPS drain).
 */
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

    /** Linear blend of two ARGB colours (alpha included). */
    public static int lerpArgb(int c1, int c2, float t) {
        t = clamp01(t);
        int a = Math.round(((c1 >>> 24) & 0xFF) + (((c2 >>> 24) & 0xFF) - ((c1 >>> 24) & 0xFF)) * t);
        int r = Math.round(((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
        int g = Math.round(((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
        int b = Math.round((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Blend of two plain RGB colours. */
    public static int lerpRgb(int c1, int c2, float t) {
        return lerpArgb(0xFF000000 | c1, 0xFF000000 | c2, t) & 0xFFFFFF;
    }

    // ------------------------------------------------------------------ easing / smoothing

    public static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static float easeOut(float p) {
        return easeOutCubic(p);
    }

    public static float easeOutCubic(float p) {
        float q = 1f - clamp01(p);
        return 1f - q * q * q;
    }

    public static float easeInCubic(float p) {
        p = clamp01(p);
        return p * p * p;
    }

    public static float easeInOut(float p) {
        p = clamp01(p);
        return p < 0.5f ? 4f * p * p * p : 1f - (float) Math.pow(-2f * p + 2f, 3) / 2f;
    }

    /** Gentle overshoot (about 5 %) before settling at 1. */
    public static float easeOutBack(float p) {
        p = clamp01(p);
        final float c1 = 0.9f;
        final float c3 = c1 + 1f;
        float q = p - 1f;
        return 1f + c3 * q * q * q + c1 * q * q;
    }

    /** Frame-rate independent follow: moves {@code cur} towards {@code target}, speed ~ 1/seconds. */
    public static float damp(float cur, float target, float speed, float dt) {
        return cur + (target - cur) * (1f - (float) Math.exp(-speed * dt));
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

    // ------------------------------------------------------------------ text

    /**
     * Draws text with an alpha. Vanilla treats an alpha below 4/255 as FULLY opaque, which shows up as
     * text "popping" at the start / end of a fade; so very faint text is simply skipped.
     */
    public static void text(GuiGraphics g, Font font, String s, int x, int y, int rgb, float alpha) {
        if (alpha < 0.03f || s == null || s.isEmpty()) return;
        g.drawString(font, s, x, y, argb(rgb, alpha), false);
    }

    public static void textCentered(GuiGraphics g, Font font, String s, int cx, int y, int rgb, float alpha) {
        text(g, font, s, cx - font.width(s) / 2, y, rgb, alpha);
    }

    // ------------------------------------------------------------------ rounded shapes

    private static final class Prof {
        int[] gy, gh, gs;
        float[] gq;
        int n;
    }

    private static final Prof[] PROFS = new Prof[64];

    private static Prof prof(int r) {
        Prof p = PROFS[r];
        if (p != null) return p;
        p = new Prof();
        List<int[]> rows = new ArrayList<>();
        List<Float> cov = new ArrayList<>();
        for (int i = 0; i < r; i++) {
            float dy = r - i - 0.5f;
            float inset = r - (float) Math.sqrt(Math.max(0f, (float) r * r - dy * dy));
            int s;
            float q = 0f;
            if (inset < 0.1f) {
                s = 0;
            } else {
                s = (int) Math.ceil(inset - 0.001f);
                float c = s - inset;                    // how much of the pixel left of s is covered
                q = Math.round(c * 4f) / 4f;            // 4 alpha levels so neighbouring rows merge
                if (q < 0.25f) q = 0f;
                if (q > 0.9f) {                         // practically full pixel: treat it as solid
                    s -= 1;
                    q = 0f;
                }
            }
            rows.add(new int[]{i, s});
            cov.add(q);
        }
        int[] gy = new int[r], gh = new int[r], gs = new int[r];
        float[] gq = new float[r];
        int n = 0;
        for (int i = 0; i < r; i++) {
            int s = rows.get(i)[1];
            float q = cov.get(i);
            if (n > 0 && gs[n - 1] == s && gq[n - 1] == q) {
                gh[n - 1]++;
            } else {
                gy[n] = i;
                gh[n] = 1;
                gs[n] = s;
                gq[n] = q;
                n++;
            }
        }
        p.gy = gy;
        p.gh = gh;
        p.gs = gs;
        p.gq = gq;
        p.n = n;
        PROFS[r] = p;
        return p;
    }

    private static void roundImpl(GuiGraphics g, int x, int y, int w, int h, int r, int color, boolean aa) {
        if (w <= 0 || h <= 0 || ((color >>> 24) & 0xFF) == 0) return;
        r = Math.max(0, Math.min(r, Math.min(Math.min(w, h) / 2, PROFS.length - 1)));
        if (r == 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        Prof p = prof(r);
        if (h - 2 * r > 0) g.fill(x, y + r, x + w, y + h - r, color);
        for (int k = 0; k < p.n; k++) {
            int s = p.gs[k];
            int y0 = y + p.gy[k];
            int y1 = y0 + p.gh[k];
            int b1 = y + h - p.gy[k];
            int b0 = b1 - p.gh[k];
            if (w - 2 * s > 0) {
                g.fill(x + s, y0, x + w - s, y1, color);
                g.fill(x + s, b0, x + w - s, b1, color);
            }
            if (aa && p.gq[k] > 0f && s > 0) {
                int edge = fade(color, p.gq[k]);
                g.fill(x + s - 1, y0, x + s, y1, edge);
                g.fill(x + w - s, y0, x + w - s + 1, y1, edge);
                g.fill(x + s - 1, b0, x + s, b1, edge);
                g.fill(x + w - s, b0, x + w - s + 1, b1, edge);
            }
        }
    }

    /** Rounded rectangle with soft (anti-aliased) corners. r = h/2 gives a full oval ("pill"). */
    public static void fillRound(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        roundImpl(g, x, y, w, h, r, color, true);
    }

    /** Same shape without the soft corner pixels: cheaper, good for small radii and glow layers. */
    public static void fillRoundFast(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        roundImpl(g, x, y, w, h, r, color, false);
    }

    public static void fillPill(GuiGraphics g, int x, int y, int w, int h, int color) {
        fillRound(g, x, y, w, h, h / 2, color);
    }

    public static void fillCircle(GuiGraphics g, int cx, int cy, int r, int color) {
        fillRound(g, cx - r, cy - r, r * 2, r * 2, r, color);
    }

    /** 1px outline that follows the rounded corners. */
    public static void outlineRound(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if (w <= 2 || h <= 2 || ((color >>> 24) & 0xFF) == 0) return;
        r = Math.max(0, Math.min(r, Math.min(Math.min(w, h) / 2, PROFS.length - 1)));
        if (r == 0) {
            g.fill(x, y, x + w, y + 1, color);
            g.fill(x, y + h - 1, x + w, y + h, color);
            g.fill(x, y + 1, x + 1, y + h - 1, color);
            g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
            return;
        }
        Prof p = prof(r);
        if (h - 2 * r > 0) {
            g.fill(x, y + r, x + 1, y + h - r, color);
            g.fill(x + w - 1, y + r, x + w, y + h - r, color);
        }
        int prevS = 0;
        for (int k = 0; k < p.n; k++) {
            int s = p.gs[k];
            int y0 = y + p.gy[k];
            int y1 = y0 + p.gh[k];
            int b1 = y + h - p.gy[k];
            int b0 = b1 - p.gh[k];
            int from = 0;
            if (k == 0) {                               // straight top / bottom edge
                if (w - 2 * s > 0) {
                    g.fill(x + s, y0, x + w - s, y0 + 1, color);
                    g.fill(x + s, b1 - 1, x + w - s, b1, color);
                }
                from = 1;
            }
            if (k > 0 && prevS > s) {                   // join the steps of the curve into one continuous line
                g.fill(x + s, y0, x + prevS, y0 + 1, color);
                g.fill(x + w - prevS, y0, x + w - s, y0 + 1, color);
                g.fill(x + s, b1 - 1, x + prevS, b1, color);
                g.fill(x + w - prevS, b1 - 1, x + w - s, b1, color);
            }
            prevS = s;
            int ta = y0 + from;
            int bb = b1 - from;
            if (y1 > ta) {
                g.fill(x + s, ta, x + s + 1, y1, color);
                g.fill(x + w - s - 1, ta, x + w - s, y1, color);
            }
            if (bb > b0) {
                g.fill(x + s, b0, x + s + 1, bb, color);
                g.fill(x + w - s - 1, b0, x + w - s, bb, color);
            }
        }
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
