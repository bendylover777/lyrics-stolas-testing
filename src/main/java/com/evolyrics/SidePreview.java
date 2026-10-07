package com.evolyrics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.List;

/** Previous lines fade out on the left side of the screen, upcoming lines fade in on the right side. */
public final class SidePreview {
    private static final float K = 1.6f;      // text scale
    private static final double HORIZON = 4.0; // seconds an upcoming line is visible in advance

    private SidePreview() {
    }

    public static void render(ForgeGui gui, GuiGraphics g, float pt, int sw, int sh) {
        Settings st = Settings.I;
        Song song = Playback.current;
        if (!st.enabled || !st.sidePreview || song == null || song.lyrics.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        Font font = mc.font;
        List<Song.Line> ls = song.lyrics;
        double t = Playback.position() + st.syncOffset;
        int idx = indexAt(ls, t);
        int mid = sh / 2;
        int rgb = lerp(st.textColor & 0xFFFFFF, st.glowColor & 0xFFFFFF, 0.45f);

        // left: what was just sung
        for (int back = 1; back <= 2; back++) {
            int i = idx - back;
            if (i < 0 || idx < 0) break;
            Song.Line l = ls.get(i);
            float a = (float) (1.0 - (t - l.end) / (HORIZON + back)) * (back == 1 ? 0.75f : 0.45f);
            if (t < l.end) a = back == 1 ? 0.75f : 0.45f;
            draw(g, font, l, 14 - (1f - Math.min(1f, a * 1.3f)) * 14f, mid - 4 - (back - 1) * 16, false, a, rgb, st);
        }
        // right: what is coming
        for (int ahead = 1; ahead <= 2; ahead++) {
            int i = idx + ahead;
            if (i >= ls.size()) break;
            Song.Line l = ls.get(i);
            float a = (float) (1.0 - (l.time - t) / (HORIZON + ahead)) * (ahead == 1 ? 0.85f : 0.5f);
            draw(g, font, l, sw - 14 + (1f - Math.min(1f, a * 1.3f)) * 14f, mid - 4 + (ahead - 1) * 16, true, a, rgb, st);
        }
    }

    private static void draw(GuiGraphics g, Font font, Song.Line l, float x, int y, boolean alignRight, float alpha, int rgb, Settings st) {
        if (alpha < 0.04f) return;
        if (alpha > 1f) alpha = 1f;
        Component c = l.comp(st.font);
        float w = font.width(c) * K;
        float px = alignRight ? x - w : x;
        int a = (int) (alpha * 255f);
        g.pose().pushPose();
        g.pose().translate(px, y, 0f);
        g.pose().scale(K, K, 1f);
        g.drawString(font, c, 0, 0, (a << 24) | rgb, false);
        g.pose().popPose();
    }

    private static int lerp(int a, int b, float k) {
        int r = ((a >> 16) & 0xFF) + (int) ((((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * k);
        int gr = ((a >> 8) & 0xFF) + (int) ((((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * k);
        int bl = (a & 0xFF) + (int) (((b & 0xFF) - (a & 0xFF)) * k);
        return (r << 16) | (gr << 8) | bl;
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
