package com.evolyrics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/** The in-game "console line": what the built-in bridge sees and does. Toggle in the menu (Music tab). */
public final class StatusHud {
    private static int cachedVersion = -1;
    private static int cachedSource = -1;
    private static String[] lines = new String[0];
    private static int[] colors = new int[0];
    private static int width = 0;
    private static int dot = 0xFF888888;

    private StatusHud() {
    }

    private static String clip(Font font, String s, int max) {
        return font.width(s) <= max ? s : font.plainSubstrByWidth(s, max - font.width("...")) + "...";
    }

    private static void rebuild(Font font) {
        String[] tail = SystemBridge.lastLog(3);
        lines = new String[3 + tail.length];
        colors = new int[lines.length];
        String st = SystemBridge.state;
        String app = SystemBridge.app.isEmpty() ? "player" : SystemBridge.app;
        switch (st) {
            case "playing" -> dot = 0xFF4ADE80;
            case "paused" -> dot = 0xFFFACC15;
            case "error" -> dot = 0xFFF87171;
            default -> dot = 0xFF888888;
        }
        String head = switch (st) {
            case "playing" -> "bridge: " + app + " playing";
            case "paused" -> "bridge: " + app + " paused";
            case "error" -> "bridge: problem";
            case "none" -> "bridge: waiting for a player";
            default -> "bridge: off";
        };
        lines[0] = head;
        colors[0] = 0xFFE5E7EB;
        String now = SystemBridge.nowTitle.isEmpty() ? "-"
            : (SystemBridge.nowArtist.isEmpty() ? SystemBridge.nowTitle : SystemBridge.nowArtist + " - " + SystemBridge.nowTitle);
        lines[1] = clip(font, "> " + now, 250);
        colors[1] = 0xFF9CA3AF;
        String ls = SystemBridge.lyricsStatus.isEmpty() ? "-" : SystemBridge.lyricsStatus;
        lines[2] = "> lyrics: " + ls + "   [" + SystemBridge.downloaded + " new, " + SystemBridge.notFound + " missing]";
        colors[2] = ls.startsWith("found") || ls.startsWith("have") ? 0xFF86EFAC
            : ls.startsWith("not found") ? 0xFFFCA5A5 : 0xFF9CA3AF;
        for (int i = 0; i < tail.length; i++) {
            lines[3 + i] = clip(font, tail[i], 250);
            colors[3 + i] = 0xFF6B7280;
        }
        int w = 0;
        for (String l : lines) w = Math.max(w, font.width(l));
        width = w + 22;
    }

    public static void render(ForgeGui gui, GuiGraphics g, float pt, int sw, int sh) {
        Settings st = Settings.I;
        if (!st.consoleLine || st.source != 1) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        Font font = mc.font;
        if (cachedVersion != SystemBridge.version || cachedSource != st.source) {
            rebuild(font);
            cachedVersion = SystemBridge.version;
            cachedSource = st.source;
        }
        int x = 6;
        int y = 6;
        int h = lines.length * 10 + 6;
        int glow = st.glowColor & 0xFFFFFF;
        g.fill(x - 1, y - 1, x + width + 1, y + h + 1, (0x55 << 24) | glow);
        g.fill(x, y, x + width, y + h, 0xC8080810);
        g.fill(x + 4, y + 5, x + 9, y + 10, dot);
        for (int i = 0; i < lines.length; i++) {
            g.drawString(font, lines[i], x + (i == 0 ? 13 : 5), y + 3 + i * 10, colors[i], false);
        }
    }
}
