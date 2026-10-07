package com.evolyrics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/** Small status line under the island: source, playback state, lyrics and time. Toggle: menu K -> Status line. */
public final class StatusHud {
    private StatusHud() {
    }

    public static void render(ForgeGui gui, GuiGraphics g, float pt, int sw, int sh) {
        Settings st = Settings.I;
        if (!st.statusLine) return;
        Font font = Minecraft.getInstance().font;
        Song s = Playback.current;
        boolean playing = Playback.isPlaying();

        String src;
        int dot;
        if (st.useBridge) {
            if (Playback.bridgeFresh) {
                src = "Bridge";
                dot = playing ? 0xFF4ADE80 : 0xFFFACC15;
            } else {
                src = "Bridge: no signal";
                dot = 0xFFF87171;
            }
        } else {
            src = "Manual";
            dot = s == null ? 0xFF9CA3AF : (playing ? 0xFF4ADE80 : 0xFFFACC15);
        }
        String lyr = s == null ? "no song" : (s.lyrics.isEmpty() ? "no lyrics" : s.lyrics.size() + " lines");
        int sec = (int) Math.max(0, Playback.position());
        String time = String.format("%02d:%02d", sec / 60, sec % 60);
        String text = src + "  \u00b7  " + (playing ? "Playing" : "Paused") + "  \u00b7  " + lyr + "  \u00b7  " + time;

        int w = font.width(text) + 20;
        int x = (sw - w) / 2;
        int y = st.island && s != null ? 29 : 5;
        g.fill(x, y, x + w, y + 12, 0x90000000);
        g.fill(x + 5, y + 4, x + 9, y + 8, dot);
        g.drawString(font, text, x + 13, y + 2, 0xFFD9C8FF, false);
    }
}
