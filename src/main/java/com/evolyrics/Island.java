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
 * While any menu / chat / inventory is open (mouse is free) the island grows and shows a player:
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

    private static Song cachedSong;
    private static String cachedFull = "";
    private static String cachedShort = "";
    private static int cachedW = 0;
    private static boolean cachedHas = false;
    private static float anim;
    private static float expand;
    private static long last = System.nanoTime();

    // click areas of the last drawn frame (valid only while the player part is fully open)
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
        boolean used = true;
        if (near(mx, my, hShuffleX, hCtlY, 11)) Transport.toggleShuffle();
        else if (near(mx, my, hPrevX, hCtlY, 11)) Transport.prev();
        else if (near(mx, my, hPlayX, hCtlY, 11)) Transport.toggle();
        else if (near(mx, my, hNextX, hCtlY, 11)) Transport.next();
        else if (near(mx, my, hRepeatX, hCtlY, 11)) Transport.cycleRepeat();
        else if (mx >= barX - 2 && mx <= barX + barW + 2 && my >= barY - 4 && my <= barY + 7) {
            double len = Transport.length();
            if (len > 0 && barW > 0) {
                double sec = Math.max(0.0, Math.min(1.0, (mx - barX) / barW)) * len;
                Transport.seekLocal(sec);
                Transport.seekRemote(sec);
            }
        } else used = false;
        if (used) {
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
        anim += (show ? dt : -dt) * 4f;
        if (anim > 1f) anim = 1f;
        expand += ((show && interactive) ? dt : -dt) * 5f;
        if (expand > 1f) expand = 1f;
        if (expand < 0f) expand = 0f;
        hitsOn = false;
        if (anim <= 0.001f) {
            anim = 0f;
            expand = 0f;
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
        int baseH = cachedHas ? 32 : 20;
        float ee = Gfx.easeOut(expand);
        int h = baseH + Math.round(EXTRA * ee);
        int baseW = tw + 52;
        int w = Math.round(baseW + (Math.max(baseW, EXP_W) - baseW) * ee);
        int rad = Math.min(h / 2, 16);
        float e = 1f - (1f - anim) * (1f - anim);
        int x = (sw - w) / 2;
        int y = Math.round(5 - (1f - e) * 40);
        int glowRgb = st.glowColor & 0xFFFFFF;
        int textRgb = st.textColor & 0xFFFFFF;

        double t = now / 1e9;
        int pulse = 0x55 + (int) (0x22 * (Math.sin(t * 3.0) * 0.5 + 0.5));
        Gfx.fillRound(g, x - 2, y - 2, w + 4, h + 4, rad + 2, (0x22 << 24) | glowRgb);
        Gfx.fillRound(g, x - 1, y - 1, w + 2, h + 2, rad + 1, (pulse << 24) | glowRgb);
        Gfx.fillRound(g, x, y, w, h, rad, 0xF2000000);

        // icon
        int iy = y + (baseH - 12) / 2;
        fillPill(g, x + 8, iy, 12, 12, 0xFF000000 | glowRgb);
        fillPill(g, x + 12, iy + 4, 4, 4, 0xFFFFFFFF);

        // title row
        int tx = x + 26;
        int ty = cachedHas ? y + 5 : y + 6;
        int rowH = cachedHas ? 15 : baseH;
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
            drawTicker(g, font, s, tx, y, tw, baseH, glowRgb);
        }

        // equalizer
        boolean playing = Playback.isPlaying();
        int bx = x + w - 8 - 11;
        int base = y + baseH / 2 + 5;
        for (int i = 0; i < 4; i++) {
            int bh = playing ? 3 + (int) ((Math.sin(t * 7 + i * 1.7) + 1) * 3.5) : 3;
            g.fill(bx + i * 3, base - bh, bx + i * 3 + 2, base, 0xFF000000 | textRgb);
        }

        // player part (only while a menu is open)
        if (ee > 0.5f) drawPlayer(g, font, x, y, w, baseH, Gfx.clamp01((ee - 0.5f) / 0.5f), mx, my, glowRgb, textRgb);
    }

    private static void drawPlayer(GuiGraphics g, Font font, int x, int y, int w, int baseH, float ea,
                                   int mx, int my, int glowRgb, int textRgb) {
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
        int timeCol = Gfx.argb(0xB8C0FF, ea);
        g.drawString(font, tl, x + 12, top + 4, timeCol, false);
        g.drawString(font, tr, x + w - 12 - trw, top + 4, timeCol, false);
        int bw = Math.max(10, bx1 - bx0);
        boolean overBar = mx >= bx0 - 2 && mx <= bx0 + bw + 2 && my >= by - 4 && my <= by + 7;
        Gfx.fillRound(g, bx0, by, bw, 3, 1, Gfx.argb(0xFFFFFF, 0.25f * ea));
        int fw = Math.max(2, Math.round(bw * frac));
        Gfx.fillRound(g, bx0, by, fw, 3, 1, Gfx.argb(0xFFFFFF, ea));
        if (overBar) Gfx.fillCircle(g, bx0 + fw, by + 1, 3, Gfx.argb(0xFFFFFF, ea));

        // buttons
        int cy = top + 28;
        int cx = x + w / 2;
        int dx = 38;
        boolean shuffle = Transport.shuffle();
        int repeat = Transport.repeat();
        boolean hSh = near(mx, my, cx - 2 * dx, cy, 11);
        boolean hPr = near(mx, my, cx - dx, cy, 11);
        boolean hPl = near(mx, my, cx, cy, 11);
        boolean hNx = near(mx, my, cx + dx, cy, 11);
        boolean hRp = near(mx, my, cx + 2 * dx, cy, 11);

        int dim = Gfx.argb(0xFFFFFF, 0.70f * ea);
        int lit = Gfx.argb(0xFFFFFF, ea);
        int green = Gfx.argb(Gfx.GREEN, ea);
        Gfx.iconShuffle(g, cx - 2 * dx, cy, shuffle ? green : (hSh ? lit : dim));
        if (shuffle) g.fill(cx - 2 * dx - 1, cy + 8, cx - 2 * dx + 1, cy + 10, green);
        Gfx.iconPrev(g, cx - dx, cy, hPr ? lit : dim);
        Gfx.fillCircle(g, cx, cy, 9, Gfx.argb(0xFFFFFF, (hPl ? 1f : 0.92f) * ea));
        if (Playback.isPlaying()) Gfx.iconPause(g, cx, cy, Gfx.fade(0xFF000000, ea));
        else Gfx.iconPlay(g, cx + 1, cy, Gfx.fade(0xFF000000, ea));
        Gfx.iconNext(g, cx + dx, cy, hNx ? lit : dim);
        Gfx.iconRepeat(g, cx + 2 * dx, cy, repeat == 2, repeat > 0 ? green : (hRp ? lit : dim));
        if (repeat > 0) g.fill(cx + 2 * dx - 1, cy + 8, cx + 2 * dx + 1, cy + 10, green);

        if (ea >= 0.99f) {
            hitsOn = true;
            hShuffleX = cx - 2 * dx;
            hPrevX = cx - dx;
            hPlayX = cx;
            hNextX = cx + dx;
            hRepeatX = cx + 2 * dx;
            hCtlY = cy;
            barX = bx0;
            barY = by;
            barW = bw;
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
        Gfx.fillPill(g, x, y, w, h, color);
    }
}
