package com.evolyrics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** Renders lyrics as billboarded text in the world. No per-frame allocations. */
public final class LyricsRenderer {
    private static final int SLOTS = 8;
    private static final float IN_DUR = 0.35f;
    private static final float OUT_DUR = 0.4f;
    private static final int FULL_BRIGHT = 15728880;

    private static final double[] SX = new double[SLOTS], SY = new double[SLOTS], SZ = new double[SLOTS];
    private static final int[] SLOT_LINE = new int[SLOTS];
    private static final int[] SLOT_GEN = new int[SLOTS];
    private static final LyricEffect.State STATE = new LyricEffect.State();
    private static final Random RND = new Random();

    private static final float[] RX = new float[8], RY = new float[8];

    static {
        Arrays.fill(SLOT_LINE, -1);
        Arrays.fill(SLOT_GEN, -1);
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
        LocalPlayer player = mc.player;
        if (player == null) return;

        double t = Playback.position() + st.syncOffset;
        List<Song.Line> ls = song.lyrics;
        int idx = lastAtOrBefore(ls, t);
        if (idx < 0) return;

        Vec3 cam = camera.getPosition();
        Font font = mc.font;
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        Font.DisplayMode mode = st.throughWalls ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
        float unit = st.size * (st.distance / 9f);
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

            if (l.fontIdx != st.font) {
                l.comp = Song.makeComp(l.text, st.font);
                l.fontIdx = st.font;
                l.width = -1;
            }

            LyricEffect inFx = l.inFx != null ? l.inFx : st.in();
            LyricEffect outFx = l.outFx != null ? l.outFx : st.out();
            LyricEffect.State state = STATE;
            state.reset();
            inFx.applyIn(state, inP, (float) t);
            if (outP > 0f) outFx.applyOut(state, outP);

            float alpha = state.alpha * st.opacity;
            if (alpha < 0.03f) continue; // Font treats alpha < 4/255 as opaque

            float scale = 0.16f * unit * state.scale;
            if (l.width < 0) l.width = font.width(l.comp);
            float x = -l.width / 2f;
            float y = -4.5f;

            ps.pushPose();
            ps.translate(SX[s] - cam.x, SY[s] - cam.y, SZ[s] - cam.z);
            ps.mulPose(camera.rotation());
            ps.translate(-state.dx * unit, state.dy * unit, 0.0);
            if (state.rot != 0f) ps.mulPose(Axis.ZP.rotationDegrees(state.rot));
            ps.scale(-scale, -scale, scale);
            Matrix4f m = ps.last().pose();

            // glow + blur halo (two rings)
            if (st.glow > 0.02f) {
                float spread = 0.8f + st.blur * 2.2f;
                int grgb = st.glowRgb(t, i);
                drawRing(font, l, x, y, spread, alpha * 0.20f * st.glow, grgb, m, buf, mode);
                drawRing(font, l, x, y, spread * 2f, alpha * 0.09f * st.glow, grgb, m, buf, mode);
            }
            int a = Math.min(255, (int) (alpha * 255f));
            font.drawInBatch(l.comp, x, y, (a << 24) | st.textRgb(t, i), st.shadow, m, buf, mode, 0, FULL_BRIGHT);
            ps.popPose();
            drew = true;
        }
        if (drew) buf.endBatch();
    }

    private static void drawRing(Font font, Song.Line l, float x, float y, float r, float alpha, int rgb,
                                 Matrix4f m, MultiBufferSource buf, Font.DisplayMode mode) {
        int a = (int) (alpha * 255f);
        if (a < 4) return;
        int color = (Math.min(a, 255) << 24) | (rgb & 0xFFFFFF);
        for (int k = 0; k < 8; k++) {
            font.drawInBatch(l.comp, x + RX[k] * r, y + RY[k] * r, color, false, m, buf, mode, 0, FULL_BRIGHT);
        }
    }

    private static void spawn(int slot, int lineIndex, Camera cam, Song.Line l, Settings st) {
        RND.setSeed(lineIndex * 7919L + 13L);
        double r1 = RND.nextDouble() * 2 - 1;
        double r2 = RND.nextDouble() * 2 - 1;
        String pos = l.position == null ? "" : l.position.toLowerCase(java.util.Locale.ROOT);
        double yawOff = 0;
        double pitchOff = 0; // degrees, negative = up (view mode)
        double yOff = 0;     // blocks (legacy "around" mode)

        if (st.inView) {
            // words appear inside the field of view, alternating left/right and up/down
            double sx = (lineIndex & 1) == 0 ? -1 : 1;
            double sy = ((lineIndex >> 1) & 1) == 0 ? -1 : 1;
            double yawSpan = 10 + st.scatter * 32;
            double pitchSpan = 5 + st.scatter * 14;
            yawOff = sx * (0.35 + 0.65 * Math.abs(r1)) * yawSpan;
            pitchOff = sy * (0.25 + 0.75 * Math.abs(r2)) * pitchSpan;
            String mode = pos;
            if (mode.isEmpty()) {
                if (st.position == 1) mode = "center";
                else if (st.position == 2) mode = "top";
                else if (st.position == 3) mode = "bottom";
            }
            if (mode.equals("left")) {
                yawOff = -Math.abs(yawOff) - 6;
                pitchOff *= 0.3;
            } else if (mode.equals("right")) {
                yawOff = Math.abs(yawOff) + 6;
                pitchOff *= 0.3;
            } else if (mode.equals("center")) {
                yawOff = r1 * 6;
                pitchOff = r2 * 4;
            } else if (mode.equals("top")) {
                pitchOff = -Math.abs(pitchOff) - 6;
                yawOff *= 0.6;
            } else if (mode.equals("bottom")) {
                pitchOff = Math.abs(pitchOff) + 4;
                yawOff *= 0.6;
            }
        } else {
            switch (pos) {
                case "left" -> { yawOff = -40; yOff = 0.2; }
                case "right" -> { yawOff = 40; yOff = 0.2; }
                case "center" -> { yawOff = 0; yOff = 0.2; }
                case "top" -> { yawOff = r1 * 25; yOff = 2.2; }
                case "bottom" -> { yawOff = r1 * 25; yOff = -1.4; }
                case "random" -> { yawOff = r1 * 110; yOff = r2 * 1.8; }
                default -> {
                    switch (st.position) {
                        case 1 -> { yawOff = 0; yOff = 0.2; }
                        case 2 -> { yawOff = r1 * st.scatter * 60; yOff = 2.2; }
                        case 3 -> { yawOff = r1 * st.scatter * 60; yOff = -1.4; }
                        default -> { yawOff = r1 * st.scatter * 100; yOff = r2 * st.scatter * 1.6 - 0.1; }
                    }
                }
            }
        }

        // origin = camera, so it also works in third person
        double pitchDeg = st.inView ? cam.getXRot() + pitchOff : 0.0;
        if (pitchDeg > 75.0) pitchDeg = 75.0;
        if (pitchDeg < -75.0) pitchDeg = -75.0;
        double yaw = Math.toRadians(cam.getYRot() + yawOff);
        double pitch = Math.toRadians(pitchDeg);
        double cp = Math.cos(pitch);
        Vec3 c = cam.getPosition();
        SX[slot] = c.x - Math.sin(yaw) * cp * st.distance;
        SY[slot] = c.y - Math.sin(pitch) * st.distance + yOff;
        SZ[slot] = c.z + Math.cos(yaw) * cp * st.distance;
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
