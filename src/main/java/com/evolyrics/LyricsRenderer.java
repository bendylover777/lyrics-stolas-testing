package com.evolyrics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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
        double rt = System.nanoTime() / 1e9;

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
            float y = -4.5f;
            float rot = state.rot + TILT_RND[s] * st.tilt;
            float pulse = 0.92f + 0.08f * (float) Math.sin(t * 5.0 + i);
            // halo/outline radii are in font pixels; one font pixel is ~15 screen pixels at size 1,
            // so keep them tiny and shrink them further when the text is bigger
            float invS = Math.min(2f, 1f / Math.max(0.25f, st.size * SIZE_MUL[s]));
            float spread = (0.4f + st.blur * 0.8f) * invS * (1f + 3f * state.blur);
            float neonR = 0.2f * invS;
            float glowA = alpha * st.glow * (1f + 1.5f * state.blur);

            ps.pushPose();
            ps.translate(SX[s] - cam.x, SY[s] - cam.y, SZ[s] - cam.z);
            ps.mulPose(camera.rotation());
            ps.translate(-state.dx * unit, state.dy * unit, 0.0);
            if (rot != 0f) ps.mulPose(Axis.ZP.rotationDegrees(rot));
            ps.scale(-scale, -scale, scale);
            Matrix4f m = ps.last().pose();
            int a = Math.min(255, (int) (alpha * (1f - 0.6f * state.blur) * 255f));

            if (st.shimmer > 0 || state.needsLetters()) {
                // letters are drawn one by one (shimmer / typewriter / wave / glitch), no allocations per frame
                l.buildChars(st.font, font);
                float bx = -l.textWidth / 2f;
                int visible = state.reveal >= 0.999f ? l.charCount : (int) Math.ceil(state.reveal * l.charCount);
                float waveAmp = state.wave > 0f ? 1.6f * invS : 0f;
                float gAmp = state.glitch > 0.01f ? 1.3f * invS * (0.4f + state.glitch) : 0f;
                boolean perCharGlow = state.wave > 0f || state.reveal < 0.999f;
                if (st.glow > 0.02f && !perCharGlow) {
                    int cMid = shimmer(st.shimmer, rgb, glowRgb, rt, st.shimmerSpeed, l.charCount / 2);
                    drawRing(font, comp, bx, y, spread * 2f, glowA * 0.10f, cMid, m, buf, mode, 1);
                    drawRing(font, comp, bx, y, spread, glowA * 0.20f * pulse, cMid, m, buf, mode, 1);
                }
                for (int k = 0; k < visible; k++) {
                    float cx = bx + l.charOff[k];
                    float cy = waveAmp != 0f ? y + (float) Math.sin(rt * 4.0 + k * 0.7) * waveAmp : y;
                    Component cc = l.charComps[k];
                    int col = shimmer(st.shimmer, rgb, glowRgb, rt, st.shimmerSpeed, k);
                    float gk = st.shimmer == 1 ? glintK(rt, st.shimmerSpeed, k) : 0f;
                    float boost = 1f + gk * st.glint * 0.7f;
                    if (perCharGlow && st.glow > 0.02f) {
                        drawRing(font, cc, cx, cy, spread * 2f, glowA * 0.10f, col, m, buf, mode, 1);
                        drawRing(font, cc, cx, cy, spread, glowA * 0.20f * pulse, col, m, buf, mode, 1);
                    }
                    if (st.neon > 0.02f) {
                        drawRing(font, cc, cx, cy, neonR, alpha * 0.9f * st.neon * pulse * boost, col, m, buf, mode, 1);
                    }
                    if (gk > 0.35f && st.glint > 0.05f) {
                        drawRing(font, cc, cx, cy, neonR * 3f, alpha * 0.3f * gk * st.glint, col, m, buf, mode, 1);
                    }
                    if (gAmp > 0f) {
                        float jx = (float) Math.sin(rt * 47.0 + k * 12.9898) * gAmp;
                        float jy = (float) Math.sin(rt * 31.0 + k * 78.233) * gAmp * 0.35f;
                        int ga = Math.min(255, (int) (alpha * 0.75f * 255f));
                        font.drawInBatch(cc, cx + jx, cy + jy, (ga << 24) | 0xFF2D55, false, m, buf, mode, 0, FULL_BRIGHT);
                        font.drawInBatch(cc, cx - jx, cy - jy, (ga << 24) | 0x2DE6FF, false, m, buf, mode, 0, FULL_BRIGHT);
                    }
                    int core = lerpRgb(coreRgb, col, 0.28f);
                    if (gk > 0f) core = lerpRgb(core, 0xFFFFFF, Math.min(1f, gk * st.glint * 0.6f));
                    font.drawInBatch(cc, cx, cy, (a << 24) | core, false, m, buf, mode, 0, FULL_BRIGHT);
                }
            } else {
                if (l.width < 0) l.width = font.width(comp);
                float x = -l.width / 2f;
                if (st.glow > 0.02f) {
                    drawRing(font, comp, x, y, spread * 2f, glowA * 0.10f, glowRgb, m, buf, mode, 1);
                    drawRing(font, comp, x, y, spread, glowA * 0.20f * pulse, glowRgb, m, buf, mode, 1);
                }
                if (st.neon > 0.02f) {
                    drawRing(font, comp, x, y, neonR, alpha * 0.9f * st.neon * pulse, glowRgb, m, buf, mode, 1);
                }
                font.drawInBatch(comp, x, y, (a << 24) | coreRgb, false, m, buf, mode, 0, FULL_BRIGHT);
            }
            ps.popPose();
            drew = true;
        }
        if (drew) buf.endBatch();
    }

    /** step 1 = 8 directions (soft glow), step 2 = 4 directions (cheap tight outline). */
    private static void drawRing(Font font, Component comp, float x, float y, float r, float alpha, int rgb,
                                 Matrix4f m, MultiBufferSource buf, Font.DisplayMode mode, int step) {
        int a = (int) (alpha * 255f);
        if (a < 4) return;
        if (a > 255) a = 255;
        int color = (a << 24) | rgb;
        for (int k = 0; k < 8; k += step) {
            font.drawInBatch(comp, x + RX[k] * r, y + RY[k] * r, color, false, m, buf, mode, 0, FULL_BRIGHT);
        }
    }

    private static int mixWhite(int rgb, float k) {
        return lerpRgb(rgb, 0xFFFFFF, k);
    }

    private static int lerpRgb(int a, int b, float k) {
        if (k <= 0f) return a;
        if (k >= 1f) return b;
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = ar + (int) ((br - ar) * k);
        int g = ag + (int) ((bg - ag) * k);
        int bl = ab + (int) ((bb - ab) * k);
        return (r << 16) | (g << 8) | bl;
    }

    /** 0..1 strength of the white glint sweep on letter #idx (wide, bright band). */
    private static float glintK(double rt, double speed, int idx) {
        double v = Math.sin((rt * speed - idx * 0.11) * 2.2) * 0.5 + 0.5;
        return (float) (v * v);
    }

    /** Color of letter #idx at real time rt. 1 = glint sweep, 2 = rainbow, 3 = flow between text and glow color. */
    private static int shimmer(int mode, int baseRgb, int altRgb, double rt, double speed, int idx) {
        double ph = rt * speed - idx * 0.11;
        switch (mode) {
            case 1: {
                double v = Math.sin(ph * 2.2) * 0.5 + 0.5;
                float k = (float) (v * v * v);
                return lerpRgb(altRgb, 0xFFFFFF, k);
            }
            case 2: {
                double hh = ph * 0.35;
                float h = (float) (hh - Math.floor(hh));
                return Mth.hsvToRgb(h, 0.75f, 1f) & 0xFFFFFF;
            }
            case 3: {
                float k = (float) (Math.sin(ph * 1.8) * 0.5 + 0.5);
                return lerpRgb(baseRgb, altRgb, k);
            }
            default:
                return altRgb;
        }
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
        // Keep words out of blocks: shrink the distance (or turn the word a bit) until the path to it is free.
        double dist0 = st.distance * (0.8 + 0.5 * r3);
        Minecraft mc = Minecraft.getInstance();
        Vec3 from = cam.getPosition();
        double halfWBase = mc.font.width(l.text) * 0.5 * 0.16 * st.size * 1.15 / 9.0 * 1.1;
        double[] tries = {yawOff, yawOff * 0.5, 0.0, -yawOff, yawOff * 1.6};
        double bestYaw = yawOff;
        double bestDist = -1.0;
        for (double yo : tries) {
            double d = fitDistance(mc.level, mc.player, cam, from, yo, pitchOff, dist0, halfWBase);
            if (d > bestDist) {
                bestDist = d;
                bestYaw = yo;
            }
            if (d >= dist0 * 0.95) break;
        }
        double dist = Math.max(0.9, bestDist);
        double yaw = Math.toRadians(cam.getYRot() + bestYaw);
        double pitch = Math.toRadians(cam.getXRot() + pitchOff);
        double cp = Math.cos(pitch);
        SX[slot] = from.x - Math.sin(yaw) * cp * dist;
        SY[slot] = from.y - Math.sin(pitch) * dist;
        SZ[slot] = from.z + Math.cos(yaw) * cp * dist;
        DIST[slot] = (float) dist;
        SIZE_MUL[slot] = (float) (0.85 + 0.4 * Math.abs(r4));
        TILT_RND[slot] = (float) r4;
    }

    /** Distance at which a word of the given half-width fits without touching blocks (3 rays: center, left, right). */
    private static double fitDistance(Level level, Entity ent, Camera cam, Vec3 from, double yawOff, double pitchOff,
                                      double dist, double halfWBase) {
        if (level == null || ent == null) return dist;
        double yaw = Math.toRadians(cam.getYRot() + yawOff);
        double pitch = Math.toRadians(cam.getXRot() + pitchOff);
        double cp = Math.cos(pitch);
        double dx = -Math.sin(yaw) * cp;
        double dy = -Math.sin(pitch);
        double dz = Math.cos(yaw) * cp;
        double rx = -Math.cos(yaw);
        double rz = -Math.sin(yaw);
        double hw = halfWBase * dist;
        Vec3 target = from.add(dx * dist, dy * dist, dz * dist);
        double f = clipFactor(level, ent, from, target);
        f = Math.min(f, clipFactor(level, ent, from, target.add(rx * hw, 0.0, rz * hw)));
        f = Math.min(f, clipFactor(level, ent, from, target.add(-rx * hw, 0.0, -rz * hw)));
        return f >= 0.999 ? dist : dist * f * 0.9;
    }

    /** 1.0 = free path, less = fraction of the path before the first solid block. */
    private static double clipFactor(Level level, Entity ent, Vec3 from, Vec3 to) {
        BlockHitResult r = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ent));
        if (r.getType() == HitResult.Type.MISS) return 1.0;
        double full = from.distanceTo(to);
        if (full < 1.0e-4) return 1.0;
        double f = r.getLocation().distanceTo(from) / full;
        return f < 0.0 ? 0.0 : Math.min(f, 1.0);
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
