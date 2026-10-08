package com.evolyrics;

import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/** Live demo of the chosen effects / colors / font / shimmer. Drawn ONLY inside the settings menu. */
public final class EffectPreview {
    private static final String TEXT = "Stolas";
    private static final String SCRAMBLE = "#%&@$?<>/|+=*~0123456789";
    private static final LyricEffect.State ST = new LyricEffect.State();
    private static final float[] RX = new float[8], RY = new float[8];

    private static String cachedFont = "\u0000";
    private static Component[] comps = new Component[0];
    private static int[] offs = new int[0];
    private static int total;
    private static Component[] scr = new Component[0];
    private static int[] scrW = new int[0];
    private static Component caret;

    static {
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            RX[i] = (float) Math.cos(a);
            RY[i] = (float) Math.sin(a);
        }
    }

    private EffectPreview() {
    }

    private static void ensure(Font font, String fontId) {
        if (Objects.equals(cachedFont, fontId)) return;
        cachedFont = fontId;
        int n = TEXT.length();
        comps = new Component[n];
        offs = new int[n];
        int x = 0;
        for (int i = 0; i < n; i++) {
            comps[i] = Song.styledComponent(String.valueOf(TEXT.charAt(i)), fontId);
            offs[i] = x;
            x += font.width(comps[i]);
        }
        total = x;
        scr = new Component[SCRAMBLE.length()];
        scrW = new int[SCRAMBLE.length()];
        for (int i = 0; i < scr.length; i++) {
            scr[i] = Song.styledComponent(String.valueOf(SCRAMBLE.charAt(i)), fontId);
            scrW[i] = font.width(scr[i]);
        }
        caret = Song.styledComponent("|", fontId);
    }

    private static void ring(GuiGraphics g, Font font, Component c, float x, float y, float r, float alpha, int rgb) {
        int a = (int) (alpha * 255f);
        if (a < 4) return;
        if (a > 255) a = 255;
        int color = (a << 24) | rgb;
        for (int k = 0; k < 8; k++) {
            g.pose().pushPose();
            g.pose().translate(x + RX[k] * r, y + RY[k] * r, 0f);
            g.drawString(font, c, 0, 0, color, false);
            g.pose().popPose();
        }
    }

    private static void letter(GuiGraphics g, Font font, Component c, float x, float y, int argb) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0f);
        g.drawString(font, c, 0, 0, argb, false);
        g.pose().popPose();
    }

    public static void render(GuiGraphics g, Font font, int x, int y, int w, int h) {
        Settings st = Settings.I;
        ensure(font, st.font);
        double rt = System.nanoTime() / 1e9;
        double cyc = rt % 4.2;
        float inP = LyricsRenderer.clamp01((float) (cyc / 0.7));
        float outP = cyc > 3.2 ? LyricsRenderer.clamp01((float) ((cyc - 3.2) / 0.6)) : 0f;
        int glowRgb = st.glowColor & 0xFFFFFF;
        int rgb = st.textColor & 0xFFFFFF;

        g.fill(x, y, x + w, y + h, 0xA0050509);
        g.renderOutline(x, y, w, h, (0x66 << 24) | glowRgb);
        g.drawString(font, "preview: " + st.in().label + " > " + st.out().label, x + 4, y + 3, 0xFF6B7280, false);
        if (cyc > 3.8) return;

        ST.reset();
        st.in().applyIn(ST, inP, (float) rt);
        if (outP > 0f) st.out().applyOut(ST, outP);
        float alpha = ST.alpha * st.opacity;
        if (alpha < 0.03f) return;

        float k = 2.3f * ST.scale;
        float cx = x + w / 2f + ST.dx * 22f;
        float cy = y + h / 2f + 3f - ST.dy * 16f;
        float rot = ST.rot + st.tilt * 0.6f;
        float pulse = 0.92f + 0.08f * (float) Math.sin(rt * 5.0);
        float spread = (0.5f + st.blur * 1.0f) * (1f + 3f * ST.blur);
        float neonR = 0.5f;
        float glowA = alpha * st.glow * (1f + 1.5f * ST.blur);
        int a = Math.min(255, (int) (alpha * (1f - 0.6f * ST.blur) * 255f));
        int coreRgb = LyricsRenderer.mixWhite(rgb, st.neon * 0.4f);
        int n = comps.length;
        int visible = ST.reveal >= 0.999f ? n : (int) Math.ceil(ST.reveal * n);
        float bx = -total / 2f;
        float fallPx = 16f / k;
        float prog = 1f - ST.scramble;
        boolean scrOn = ST.scramble > 0.01f;
        long tick = (long) Math.floor(rt * 18.0);
        float waveAmp = ST.wave > 0f ? 1.6f : 0f;
        float gAmp = ST.glitch > 0.01f ? 1.1f * (0.4f + ST.glitch) : 0f;

        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0f);
        if (rot != 0f) g.pose().mulPose(Axis.ZP.rotationDegrees(rot));
        g.pose().scale(k, k, 1f);

        if (st.glow > 0.02f && st.shimmer >= 0 && ST.wave <= 0f && ST.reveal >= 0.999f) {
            int cMid = LyricsRenderer.shimmer(st.shimmer, rgb, glowRgb, rt, st.shimmerSpeed, n / 2);
            for (int i = 0; i < n; i++) {
                float lx = bx + offs[i];
                ring(g, font, comps[i], lx, -4.5f, spread * 2f, glowA * 0.10f, cMid);
                ring(g, font, comps[i], lx, -4.5f, spread, glowA * 0.20f * pulse, cMid);
            }
        }
        for (int i = 0; i < visible; i++) {
            float lx = bx + offs[i];
            float ly = -4.5f + (waveAmp != 0f ? (float) Math.sin(rt * 4.0 + i * 0.7) * waveAmp : 0f);
            Component cc = comps[i];
            if (ST.fall < 0.999f) {
                float lp = LyricsRenderer.clamp01((ST.fall - (n > 1 ? i * 0.5f / (n - 1) : 0f)) / 0.5f);
                if (lp <= 0.001f) continue;
                ly -= (1f - LyricEffect.bounce(lp)) * 1.3f * fallPx;
            }
            if (ST.fallOut > 0.001f) {
                float lo = LyricsRenderer.clamp01((ST.fallOut - (n > 1 ? i * 0.4f / (n - 1) : 0f)) / 0.6f);
                ly += lo * lo * 1.2f * fallPx;
            }
            int col = LyricsRenderer.shimmer(st.shimmer, rgb, glowRgb, rt, st.shimmerSpeed, i);
            float gk = st.shimmer == 1 ? LyricsRenderer.glintK(rt, st.shimmerSpeed, i) : 0f;
            float boost = 1f + gk * st.glint * 0.7f;
            if (scrOn && prog * 1.15f < (i + 1f) / n) {
                int gi = (int) Math.floorMod(tick + i * 7L, (long) scr.length);
                cc = scr[gi];
                int cw = (i + 1 < n ? offs[i + 1] : total) - offs[i];
                lx += (cw - scrW[gi]) / 2f;
                col = LyricsRenderer.lerpRgb(col, 0x39FFB0, 0.55f);
            }
            if (st.neon > 0.02f) ring(g, font, cc, lx, ly, neonR, alpha * 0.9f * st.neon * pulse * boost, col);
            if (gk > 0.35f && st.glint > 0.05f) ring(g, font, cc, lx, ly, neonR * 3f, alpha * 0.3f * gk * st.glint, col);
            float mx = lx;
            if (gAmp > 0f) {
                float jx = (float) Math.sin(rt * 47.0 + i * 12.9898) * gAmp;
                float jy = (float) Math.sin(rt * 31.0 + i * 78.233) * gAmp * 0.35f;
                int ga = Math.min(255, (int) (alpha * 0.75f * 255f));
                letter(g, font, cc, lx + jx, ly + jy, (ga << 24) | 0xFF2D55);
                letter(g, font, cc, lx - jx, ly - jy, (ga << 24) | 0x2DE6FF);
                if (LyricsRenderer.hash01(i, (long) Math.floor(rt * 14.0)) > 0.8f) {
                    mx += (LyricsRenderer.hash01(i + 91, (long) Math.floor(rt * 14.0)) - 0.5f) * 6f * gAmp;
                }
            }
            int core = LyricsRenderer.lerpRgb(coreRgb, col, 0.28f);
            if (gk > 0f) core = LyricsRenderer.lerpRgb(core, 0xFFFFFF, Math.min(1f, gk * st.glint * 0.6f));
            letter(g, font, cc, mx, ly, (Math.max(a, 4) << 24) | core);
        }
        if (ST.caret && ((long) Math.floor(rt * 3.2)) % 2 == 0) {
            float cxk = bx + (visible < n ? offs[visible] : total);
            letter(g, font, caret, cxk, -4.5f, (Math.max(a, 4) << 24) | glowRgb);
        }
        g.pose().popPose();
        g.disableScissor();
    }
}
