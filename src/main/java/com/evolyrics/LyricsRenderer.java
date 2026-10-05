package com.evolyrics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Renders lyrics as billboarded neon text in front of the camera. No per-frame allocations. */
public final class LyricsRenderer {
    private static final int SLOTS = 8;
    private static final float IN_DUR = 0.35f;
    private static final float OUT_DUR = 0.4f;
    private static final int FULL_BRIGHT = 15728880;
    private static final int[] CELL_ORDER = {4, 0, 8, 2, 6, 5, 3, 1, 7};

    private static final double[] SX = new double[SLOTS], SY = new double[SLOTS], SZ = new double[SLOTS];
    private static final float[] DIST = new float[SLOTS], SIZE_MUL = new float[SLOTS], TILT_RND = new float[SLOTS];
    private static final int[] SLOT_LINE = new int[SLOTS];
    private static final int[] SLOT_GEN = new int[SLOTS];
    private static final LyricEffect.State STATE = new LyricEffect.State();
    private static final Random RND = new Random();

    private static final float[] RX = new float[8], RY = new float[8];

    static {
        Arrays.fill(SLOT_LINE, -1);
        Arrays.fill(SLOT_GEN, -1);
        Arrays.fill(DIST, 5f);
        Arrays.fill(SIZE_MUL, 1f);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            RX[i] = (float) Math.cos(a);
            RY[i] = (float) Math.sin(a);
        }
    }

    private LyricsRenderer() {
    }

    public static void render(PoseStack ps, Camera camera, float partial) {
        Settings st = Settings.I;
        Song song = Playback.current;
        if (!st.enabled || song == null || song.lyrics.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        double t = Playback.position() + st.syncOffset;
        List<Song.Line> ls = song.lyrics;
        int idx = lastAtOrBefore(ls, t);
        if (idx < 0) return;

        Vec3 cam = camera.getPosition();
        Font font = mc.font;
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        Font.DisplayMode mode = st.throughWalls ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
        int rgb = st.textColor & 0xFFFFFF;
        int glowRgb = st.glowColor & 0xFFFFFF;
        int coreRgb = mixWhite(rgb, st.neon * 0.4f);
        boolean drew = false;

        for (int i = idx, n = 0; i >= 0 && n < SLOTS; i--, n++) {
            Song.Line l = ls.get(i);
            if (t >= l.end + OUT_DUR) continue;

            float inP = clamp((float) ((t - l.time) / IN_DUR));
            float outP = t > l.end ? clamp((float) ((t - l.end) / OUT_DUR)) : 0f;

            int s = i % SLOTS;
            if (SLOT_LINE[s] != i || SLOT_GEN[s] != Playback.generation) {
                spawn(s, i, camera, l, st);
                SLOT_LINE[s] = i;
                SLOT_GEN[s] = Playback.generation;
            }

            LyricEffect inFx = l.inFx != null ? l.inFx : st.in();
            LyricEffect outFx = l.outFx != null ? l.outFx : st.out();
            LyricEffect.State state = STATE;
            state.reset();
            inFx.applyIn(state, inP, (float) t);
            if (outP > 0f) outFx.applyOut(state, outP);

            float alpha = state.alpha * st.opacity;
            if (alpha < 0.03f) continue; // Font treats alpha < 4/255 as opaque

            float unit = st.size * (DIST[s] / 9f) * SIZE_MUL[s];
            float scale = 0.16f * unit * state.scale;
            Component comp = l.comp(st.font);
            if (l.width < 0) l.width = font.width(comp);
            float x = -l.width / 2f;
            float y = -4.5f;
            float rot = state.rot + TILT_RND[s] * st.tilt;
            float pulse = 0.92f + 0.08f * (float) Math.sin(t * 5.0 + i);

            ps.pushPose();
            ps.translate(SX[s] - cam.x, SY[s] - cam.y, SZ[s] - cam.z);
            ps.mulPose(camera.rotation());
            ps.translate(-state.dx * unit, state.dy * unit, 0.0);
            if (rot != 0f) ps.mulPose(Axis.ZP.rotationDegrees(rot));
            ps.scale(-scale, -scale, scale);
            Matrix4f m = ps.last().pose();

            // soft glow (two wide rings)
            if (st.glow > 0.02f) {
                float spread = 0.9f + st.blur * 1.8f;
                drawRing(font, comp, x, y, spread * 2.2f, alpha * 0.10f * st.glow, glowRgb, m, buf, mode);
                drawRing(font, comp, x, y, spread, alpha * 0.20f * st.glow * pulse, glowRgb, m, buf, mode);
            }
            // neon tube outline (tight bright ring)
            if (st.neon > 0.02f) {
                drawRing(font, comp, x, y, 0.75f, alpha * 0.9f * st.neon * pulse, glowRgb, m, buf, mode);
            }
            int a = Math.min(255, (int) (alpha * 255f));
            font.drawInBatch(comp, x, y, (a << 24) | coreRgb, false, m, buf, mode, 0, FULL_BRIGHT);
            ps.popPose();
            drew = true;
        }
        if (drew) buf.endBatch();
    }

    private static void drawRing(Font font, Component comp, float x, float y, float r, float alpha, int rgb,
                                 Matrix4f m, MultiBufferSource buf, Font.DisplayMode mode) {
        int a = (int) (alpha * 255f);
        if (a < 4) return;
        if (a > 255) a = 255;
        int color = (a << 24) | rgb;
        for (int k = 0; k < 8; k++) {
            font.drawInBatch(comp, x + RX[k] * r, y + RY[k] * r, color, false, m, buf, mode, 0, FULL_BRIGHT);
        }
    }

    private static int mixWhite(int rgb, float k) {
        if (k <= 0f) return rgb;
        if (k > 1f) k = 1f;
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        r += (int) ((255 - r) * k);
        g += (int) ((255 - g) * k);
        b += (int) ((255 - b) * k);
        return (r << 16) | (g << 8) | b;
    }

    /** New line appears inside the camera's field of view (works in 1st and 3rd person: based on the camera). */
    private static void spawn(int slot, int lineIndex, Camera cam, Song.Line l, Settings st) {
        RND.setSeed(lineIndex * 7919L + 13L);
        double r1 = RND.nextDouble() * 2 - 1;
        double r2 = RND.nextDouble() * 2 - 1;
        double r3 = RND.nextDouble();
        double r4 = RND.nextDouble() * 2 - 1;
        double spreadH = 12.0 + st.scatter * 38.0;
        double spreadV = 6.0 + st.scatter * 14.0;
        double yawOff;
        double pitchOff;
        String pos = l.position == null ? "" : l.position.toLowerCase(Locale.ROOT);
        switch (pos) {
            case "left" -> { yawOff = -spreadH; pitchOff = 0; }
            case "right" -> { yawOff = spreadH; pitchOff = 0; }
            case "center" -> { yawOff = 0; pitchOff = 0; }
            case "top" -> { yawOff = r1 * spreadH * 0.5; pitchOff = -spreadV; }
            case "bottom" -> { yawOff = r1 * spreadH * 0.5; pitchOff = spreadV; }
            case "random" -> { yawOff = r1 * spreadH; pitchOff = r2 * spreadV; }
            default -> {
                switch (st.position) {
                    case 1 -> { yawOff = 0; pitchOff = 0; }
                    case 2 -> { yawOff = r1 * spreadH * 0.5; pitchOff = -spreadV; }
                    case 3 -> { yawOff = r1 * spreadH * 0.5; pitchOff = spreadV; }
                    default -> {
                        int cell = CELL_ORDER[Math.floorMod(lineIndex, 9)];
                        int col = cell % 3 - 1;
                        int row = cell / 3 - 1;
                        yawOff = col * spreadH * 0.66 + r1 * spreadH * 0.28;
                        pitchOff = row * spreadV * 0.66 + r2 * spreadV * 0.28;
                    }
                }
            }
        }
        double yaw = Math.toRadians(cam.getYRot() + yawOff);
        double pitch = Math.toRadians(cam.getXRot() + pitchOff);
        double dist = st.distance * (0.8 + 0.5 * r3);
        double cp = Math.cos(pitch);
        Vec3 p = cam.getPosition();
        SX[slot] = p.x - Math.sin(yaw) * cp * dist;
        SY[slot] = p.y - Math.sin(pitch) * dist;
        SZ[slot] = p.z + Math.cos(yaw) * cp * dist;
        DIST[slot] = (float) dist;
        SIZE_MUL[slot] = (float) (0.85 + 0.4 * Math.abs(r4));
        TILT_RND[slot] = (float) r4;
    }

    private static int lastAtOrBefore(List<Song.Line> ls, double t) {
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

    private static float clamp(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }
}
