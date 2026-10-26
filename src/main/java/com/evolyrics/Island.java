package com.evolyrics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.List;
import java.util.Locale;

/**
 * iPhone-style "dynamic island": song title on top (marquee if long) and a running
 * lyrics ticker underneath that glides to the current line.
 * While any menu / chat / inventory is open (mouse is free) the island grows (spring animation) and shows a player:
 * progress bar and shuffle, previous, play/pause, next, repeat buttons you can click.
 */
public final class Island {
    private static final int MAX_TEXT = 150;
    private static final double SPEED = 28.0;   // px per second (title marquee)
    private static final double PAUSE = 1.2;    // seconds at the start of each marquee loop
    private static final int GAP = 36;
    private static final int TICK_GAP = 7;
    private static final int EXTRA = 44;      // extra height of the player part
    private static final int EXP_W = 200;     // minimal width when expanded
    private static final int BTN_N = 5;       // shuffle, prev, play, next, repeat
    private static final int HIT_R = 12;

    private static Song cachedSong;
    private static String cachedFull = "";
    private static String cachedShort = "";
    private static int cachedW = 0;
    private static boolean cachedHas = false;
    private static float anim;
    private static long last = System.nanoTime();

    // spring that drives the expand / collapse (can overshoot a little = lively, never jerky)
    private static float expPos, expVel;
    // smoothed size of the compact body, so a new song does not make the island jump
    private static float wBase = -1f, hBase = -1f;
    // little animations of the player part
    private static final float[] hov = new float[BTN_N];
    private static final float[] pls = new float[BTN_N];
    private static float playMix, shufMix, repMix, barHov, eqAmt;

    // click areas of the last drawn frame (valid only while the player part is open)
    private static boolean hitsOn;
    private static int hShuffleX, hPrevX, hPlayX, hNextX, hRepeatX, hCtlY;
    private static int barX, barY, barW;

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

    /** HUD overlay: the compact island while playing. Menus draw it themselves (see renderOverScreen). */
    public static void render(ForgeGui gui, GuiGraphics g, float pt, int sw, int sh) {
        if (Minecraft.getInstance().screen != null) return;
        draw(g, sw, -1, -1, false);
    }

    /** Drawn on top of every screen while in a world: expands into a clickable player. */
    public static void renderOverScreen(GuiGraphics g, Screen screen, int mx, int my) {
        if (Minecraft.getInstance().level == null) return;
        // our own menu on a small window has no room above it: keep the island compact there
        boolean room = !(screen instanceof SettingsScreen) || screen.height >= 330;
        draw(g, screen.width, mx, my, room);
    }

    /** Mouse click on a screen. Returns true when the island used it. */
    public static boolean click(double mx, double my, int button) {
        if (button != 0 || !hitsOn) return false;
        int hit = -1;
        if (near(mx, my, hShuffleX, hCtlY, HIT_R)) hit = 0;
        else if (near(mx, my, hPrevX, hCtlY, HIT_R)) hit = 1;
        else if (near(mx, my, hPlayX, hCtlY, HIT_R)) hit = 2;
        else if (near(mx, my, hNextX, hCtlY, HIT_R)) hit = 3;
        else if (near(mx, my, hRepeatX, hCtlY, HIT_R)) hit = 4;
        boolean used = true;
        switch (hit) {
            case 0 -> Transport.toggleShuffle();
            case 1 -> Transport.prev();
            case 2 -> Transport.toggle();
            case 3 -> Transport.next();
            case 4 -> Transport.cycleRepeat();
            default -> {
                if (mx >= barX - 2 && mx <= barX + barW + 2 && my >= barY - 5 && my <= barY + 8) {
                    double len = Transport.length();
                    if (len > 0 && barW > 0) {
                        double sec = Math.max(0.0, Math.min(1.0, (mx - barX) / barW)) * len;
                        Transport.seekLocal(sec);
                        Transport.seekRemote(sec);
                    }
                } else {
                    used = false;
                }
            }
        }
        if (used) {
            if (hit >= 0) pls[hit] = 1f;     // ripple on the pressed button
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
        return used;
    }

    private static boolean near(double mx, double my, int cx, int cy, int r) {
        return Math.abs(mx - cx) <= r && Math.abs(my - cy) <= r;
    }

    private static String fmt(double sec) {
        int t = (int) Math.max(0, sec);
        return String.format(Locale.ROOT, "%d:%02d", t / 60, t % 60);
    }

    private static void draw(GuiGraphics g, int sw, int mx, int my, boolean interactive) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - last) / 1e9f);
        last = now;

        Settings st = Settings.I;
        Song s = Playback.current;
        boolean show = st.island && s != null;
        anim += (show ? dt : -dt) * 3.2f;
        if (anim > 1f) anim = 1f;
        if (anim < 0f) anim = 0f;

        // expand / collapse spring (sub-stepped, so it behaves the same at 20 and at 240 fps)
        float target = (show && interactive) ? 1f : 0f;
        float rem = dt;
        while (rem > 0f) {
            float stp = Math.min(rem, 0.008f);
            expVel += ((target - expPos) * 190f - expVel * 21f) * stp;
            expPos += expVel * stp;
            rem -= stp;
        }
        if (Math.abs(target - expPos) < 0.002f && Math.abs(expVel) < 0.05f) {
            expPos = target;
            expVel = 0f;
        }

        hitsOn = false;
        if (anim <= 0.03f) {              // nearly invisible: skip drawing (faint text would flash at full alpha)
            if (!show) {
                expPos = 0f;
                expVel = 0f;
            }
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
        float tgtW = tw + 52;
        float tgtH = cachedHas ? 32 : 20;
        if (wBase < 0f) {
            wBase = tgtW;
            hBase = tgtH;
        } else {
            wBase = Gfx.damp(wBase, tgtW, 13f, dt);
            hBase = Gfx.damp(hBase, tgtH, 13f, dt);
        }
        int baseH = Math.round(hBase);
        int baseW = Math.round(wBase);

        float ee = Math.max(0f, Math.min(1.08f, expPos));        // may overshoot a bit on purpose
        float e = 1f - (1f - anim) * (1f - anim);                  // show / hide fade + slide
        int h = baseH + Math.round(EXTRA * ee);
        int w = Math.round(baseW + (Math.max(baseW, EXP_W) - baseW) * ee);
        int rad = Math.min(h / 2, 16);
        int x = Math.round((sw - w) / 2f);
        int y = Math.round(st.islandY - (1f - e) * 40);
        int glowRgb = Theme.accent();
        int textRgb = st.textColor & 0xFFFFFF;

        double t = now / 1e9;
        float pulse = 0.34f + 0.12f * (float) (Math.sin(t * 3.0) * 0.5 + 0.5);
        Gfx.fillRoundFast(g, x - 2, y - 2, w + 4, h + 4, rad + 2, Gfx.argb(glowRgb, 0.14f * e));
        Gfx.fillRoundFast(g, x - 1, y - 1, w + 2, h + 2, rad + 1, Gfx.argb(glowRgb, pulse * e));
        Gfx.fillRound(g, x, y, w, h, rad, Gfx.argb(Theme.bg(), st.islandOpacity * e));

        // icon
        int iy = y + (baseH - 12) / 2;
        Gfx.fillPill(g, x + 8, iy, 12, 12, Gfx.argb(glowRgb, e));
        Gfx.fillPill(g, x + 12, iy + 4, 4, 4, Gfx.argb(0xFFFFFF, e));

        // title row
        int tx = x + 26;
        int ty = cachedHas ? y + 5 : y + 6;
        int rowH = cachedHas ? 15 : baseH;
        int white = Gfx.argb(0xFFFFFF, e);
        if (scroll) {
            double cycle = cachedW + GAP;
            double period = cycle / SPEED + PAUSE;
            double phase = t % period;
            float off = phase < PAUSE ? 0f : (float) ((phase - PAUSE) * SPEED);
            g.enableScissor(tx, y, tx + tw, y + rowH);
            g.pose().pushPose();
            g.pose().translate(-off, 0f, 0f);          // float offset: the text glides instead of stepping per pixel
            g.drawString(font, cachedFull, tx, ty, white, false);
            g.drawString(font, cachedFull, tx + (int) cycle, ty, white, false);
            g.pose().popPose();
            g.disableScissor();
        } else {
            g.drawString(font, overflow ? cachedShort : cachedFull, tx, ty, white, false);
        }

        // running lyrics ticker
        if (cachedHas && s != null && tickSong == s && tickCount > 0) {
            g.fill(tx, y + 16, tx + tw, y + 17, Gfx.argb(glowRgb, 0.19f * e));
            drawTicker(g, font, s, tx, y, tw, baseH, e);
        }

        // equalizer (calms down smoothly on pause)
        boolean playing = Playback.isPlaying();
        eqAmt = Gfx.damp(eqAmt, playing ? 1f : 0f, 9f, dt);
        int bx = x + w - 8 - 11;
        int base = y + baseH / 2 + 5;
        int eqCol = Gfx.argb(textRgb, e);
        for (int i = 0; i < 4; i++) {
            int bh = 3 + Math.round(eqAmt * (float) ((Math.sin(t * 7 + i * 1.7) + 1) * 3.5));
            g.fill(bx + i * 3, base - bh, bx + i * 3 + 2, base, eqCol);
        }

        // player part (only while a menu is open)
        float ea = Gfx.clamp01((ee - 0.35f) / 0.55f);
        ea = ea * ea * (3f - 2f * ea) * e;
        if (ea > 0.02f) drawPlayer(g, font, x, y, w, baseH, ea, dt, mx, my, glowRgb, ee > 0.85f);
    }

    private static void drawPlayer(GuiGraphics g, Font font, int x, int y, int w, int baseH, float ea, float dt,
                                   int mx, int my, int glowRgb, boolean clickable) {
        int top = y + baseH;
        g.fill(x + 12, top, x + w - 12, top + 1, Gfx.argb(glowRgb, 0.20f * ea));

        // progress: 1:23 [======o-----] 3:10
        double len = Transport.length();
        double pos = Playback.position();
        float frac = len > 0 ? (float) Math.min(1.0, Math.max(0.0, pos / len)) : 0f;
        String tl = fmt(pos);
        String tr = len > 0 ? fmt(len) : "--:--";
        int tlw = font.width(tl);
        int trw = font.width(tr);
        int bx0 = x + 12 + tlw + 5;
        int bx1 = x + w - 12 - trw - 5;
        int by = top + 7;
        Gfx.text(g, font, tl, x + 12, top + 4, 0xB8C0FF, ea);
        Gfx.text(g, font, tr, x + w - 12 - trw, top + 4, 0xB8C0FF, ea);
        int bw = Math.max(10, bx1 - bx0);
        boolean overBar = mx >= bx0 - 2 && mx <= bx0 + bw + 2 && my >= by - 5 && my <= by + 8;
        barHov = Gfx.damp(barHov, overBar ? 1f : 0f, 16f, dt);
        int thick = 3 + Math.round(barHov * 2f);                  // the bar swells under the mouse
        int ty0 = by + 1 - thick / 2;
        Gfx.fillRound(g, bx0, ty0, bw, thick, thick / 2, Gfx.argb(0xFFFFFF, 0.25f * ea));
        int fw = Math.max(thick, Math.round(bw * frac));
        Gfx.fillRound(g, bx0, ty0, fw, thick, thick / 2, Gfx.argb(0xFFFFFF, ea));
        int kr = Math.round(barHov * 4f);
        if (kr > 0) Gfx.fillCircle(g, bx0 + fw, by + 1, kr, Gfx.argb(0xFFFFFF, ea));

        // buttons
        int cy = top + 28;
        int cx = x + w / 2;
        int dx = 38;
        int[] bxs = {cx - 2 * dx, cx - dx, cx, cx + dx, cx + 2 * dx};
        for (int i = 0; i < BTN_N; i++) {
            boolean over = near(mx, my, bxs[i], cy, HIT_R);
            hov[i] = Gfx.damp(hov[i], over ? 1f : 0f, 16f, dt);
            pls[i] = Math.max(0f, pls[i] - dt * 4.5f);
            // soft round highlight under the icon + a ripple after a click
            if (hov[i] > 0.02f && i != 2) Gfx.fillCircle(g, bxs[i], cy, HIT_R, Gfx.argb(0xFFFFFF, 0.10f * hov[i] * ea));
            if (pls[i] > 0.02f) {
                int rr = Math.round(HIT_R + 5f * (1f - pls[i]));
                Gfx.fillCircle(g, bxs[i], cy, rr, Gfx.argb(0xFFFFFF, 0.20f * pls[i] * ea));
            }
        }

        boolean shuffle = Transport.shuffle();
        int repeat = Transport.repeat();
        shufMix = Gfx.damp(shufMix, shuffle ? 1f : 0f, 14f, dt);
        repMix = Gfx.damp(repMix, repeat > 0 ? 1f : 0f, 14f, dt);
        playMix = Gfx.damp(playMix, Playback.isPlaying() ? 1f : 0f, 18f, dt);

        int green = Gfx.argb(Gfx.GREEN, ea);
        int shufC = Gfx.lerpArgb(Gfx.argb(0xFFFFFF, (0.62f + 0.38f * hov[0]) * ea), green, shufMix);
        int prevC = Gfx.argb(0xFFFFFF, (0.62f + 0.38f * hov[1]) * ea);
        int nextC = Gfx.argb(0xFFFFFF, (0.62f + 0.38f * hov[3]) * ea);
        int repC = Gfx.lerpArgb(Gfx.argb(0xFFFFFF, (0.62f + 0.38f * hov[4]) * ea), green, repMix);
        Gfx.iconShuffle(g, bxs[0], cy, shufC);
        if (shufMix > 0.05f) g.fill(bxs[0] - 1, cy + 8, bxs[0] + 1, cy + 10, Gfx.fade(green, shufMix));
        Gfx.iconPrev(g, bxs[1], cy, prevC);
        Gfx.iconNext(g, bxs[3], cy, nextC);
        Gfx.iconRepeat(g, bxs[4], cy, repeat == 2, repC);
        if (repMix > 0.05f) g.fill(bxs[4] - 1, cy + 8, bxs[4] + 1, cy + 10, Gfx.fade(green, repMix));

        // play / pause: the button grows a little under the mouse, the icons cross-fade
        int pr = Math.round(9f + hov[2] * 1.2f - pls[2] * 1.5f);
        Gfx.fillCircle(g, cx, cy, pr, Gfx.argb(0xFFFFFF, (0.92f + 0.08f * hov[2]) * ea));
        float pa = ea * playMix;
        float ia = ea * (1f - playMix);
        if (pa > 0.03f) Gfx.iconPause(g, cx, cy, Gfx.argb(0x000000, pa));
        if (ia > 0.03f) Gfx.iconPlay(g, cx + 1, cy, Gfx.argb(0x000000, ia));

        if (clickable) {
            hitsOn = true;
            hShuffleX = bxs[0];
            hPrevX = bxs[1];
            hPlayX = bxs[2];
            hNextX = bxs[3];
            hRepeatX = bxs[4];
            hCtlY = cy;
            barX = bx0;
            barY = by;
            barW = bw;
        }
    }

    private static void drawTicker(GuiGraphics g, Font font, Song s, int tx, int y, int tw, int h, float fa) {
        List<Song.Line> ls = s.lyrics;
        int n = tickCount;
        double pos = Playback.position() + Settings.I.syncOffset;
        int idx = indexAt(ls, pos);

        float center;
        float sm = 0f;                                  // 0..1 while gliding from this line to the next
        if (idx < 0) {
            center = tickX[0] + tickW[0] / 2f;
        } else {
            center = tickX[idx] + tickW[idx] / 2f;
            if (idx + 1 < n) {
                double b = ls.get(idx + 1).time;
                float gk = (float) ((pos - (b - 0.3)) / 0.3);
                if (gk > 0f) {
                    if (gk > 1f) gk = 1f;
                    sm = gk * gk * (3f - 2f * gk);
                    float nextC = tickX[idx + 1] + tickW[idx + 1] / 2f;
                    center += (nextC - center) * sm;
                }
            }
        }

        int cx0 = tx + tw / 2;
        int from = Math.max(0, idx - 5);
        int to = Math.min(n - 1, Math.max(idx, 0) + 6);
        // the sub-pixel part of the scroll is applied through the pose, so the ticker glides smoothly
        int ci = (int) Math.floor(center);
        float cf = center - ci;
        g.enableScissor(tx, y + 17, tx + tw, y + h - 3);
        g.pose().pushPose();
        g.pose().translate(-cf, 0f, 0f);
        for (int i = from; i <= to; i++) {
            int sx = cx0 + Math.round(tickX[i]) - ci;
            if (sx > tx + tw + 1 || sx + tickW[i] < tx - 1) continue;
            // line colours cross-fade with the glide, so the current line does not "pop" white
            int base = i < idx ? 0x80FFFFFF : 0xB0B8C0FF;
            float k = i == idx ? 1f - sm : (i == idx + 1 ? sm : 0f);
            int col = Gfx.lerpArgb(base, 0xFFFFFFFF, k);
            g.drawString(font, ls.get(i).text, sx, y + 19, Gfx.fade(col, fa), false);
        }
        g.pose().popPose();
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
}
