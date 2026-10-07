package com.evolyrics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/** iPhone-style "dynamic island" pill at the top of the screen with the current song title. */
public final class Island {
    private static Song cachedSong;
    private static String cachedText = "";
    private static float anim;
    private static long last = System.nanoTime();

    private Island() {
    }

    public static void render(ForgeGui gui, GuiGraphics g, float pt, int sw, int sh) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - last) / 1e9f);
        last = now;

        Song s = Playback.current;
        boolean show = Settings.I.island && s != null;
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
            String full = s.artist == null || s.artist.isBlank() ? title : title + " - " + s.artist;
            int max = 150;
            cachedText = font.width(full) <= max ? full
                : font.plainSubstrByWidth(full, max - font.width("...")) + "...";
        }

        int h = 20;
        int tw = font.width(cachedText);
        int w = tw + 52;
        float e = 1f - (1f - anim) * (1f - anim);
        int x = (sw - w) / 2;
        int y = Math.round(5 - (1f - e) * 30);

        fillPill(g, x - 1, y - 1, w + 2, h + 2, 0x558B5CF6);
        fillPill(g, x, y, w, h, 0xF2000000);

        // icon
        fillPill(g, x + 8, y + 4, 12, 12, 0xFF8B5CF6);
        fillPill(g, x + 12, y + 8, 4, 4, 0xFFFFFFFF);

        // title
        g.drawString(font, cachedText, x + 26, y + 6, 0xFFFFFFFF, false);

        // equalizer
        double t = now / 1e9;
        boolean playing = Playback.isPlaying();
        int bx = x + w - 8 - 11;
        int base = y + 15;
        for (int i = 0; i < 4; i++) {
            int bh = playing ? 3 + (int) ((Math.sin(t * 7 + i * 1.7) + 1) * 3.5) : 3;
            g.fill(bx + i * 3, base - bh, bx + i * 3 + 2, base, 0xFFD9C8FF);
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
