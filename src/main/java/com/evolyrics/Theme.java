package com.evolyrics;

import java.util.Locale;

/**
 * Colour themes for the lyrics (text + glow) and the look of the menu / island / HUD.


 * Fix: the whole UI used to take its accent colour straight from "Glow color", so a dark glow (black, deep blue...)
 * made the menu, the toggles and the island almost invisible. {@link #accent()} now guarantees a minimum brightness
 * and the accent can also be fixed to its own colour (More tab > UI accent).
 */
public final class Theme {
    public static final class Preset {
        public final String name;
        public final int text;
        public final int glow;

        Preset(String name, int text, int glow) {
            this.name = name;
            this.text = text;
            this.glow = glow;
        }
    }

    public static final Preset[] PRESETS = {
        new Preset("Ice", 0xFFFFFF, 0x7FA8FF),
        new Preset("Pink", 0xFFFFFF, 0xFF7ACB),
        new Preset("Violet", 0xF2E8FF, 0xA855F7),
        new Preset("Gold", 0xFFF3B0, 0xFFB020),
        new Preset("Mint", 0xE8FFE8, 0x4ADE80),
        new Preset("Pure white", 0xFFFFFF, 0xFFFFFF),
        new Preset("Sunset", 0xFFE3C2, 0xFF5E3A),
        new Preset("Ocean", 0xD6F5FF, 0x0096C7),
        new Preset("Cyberpunk", 0xFFFB8F, 0xFF2BD6),
        new Preset("Lava", 0xFFD9A0, 0xFF3B00),
        new Preset("Forest", 0xE3FFD6, 0x2E9E4F),
        new Preset("Sakura", 0xFFF0F5, 0xFF9EBB),
        new Preset("Matrix", 0xB8FFC8, 0x00FF41),
        new Preset("Vaporwave", 0xFFD6F7, 0x7B5CFF),
        new Preset("Blood", 0xFFD6D6, 0xD10000),
        new Preset("Aqua", 0xE0FFFF, 0x00E5E5),
        new Preset("Lime", 0xF7FFC9, 0xB6FF00),
        new Preset("Candy", 0xFFE0F0, 0x66D9FF),
        new Preset("Galaxy", 0xE6E0FF, 0x5B3DF5),
        new Preset("Spotify", 0xFFFFFF, 0x1ED760),
        new Preset("Discord", 0xFFFFFF, 0x5865F2),
        new Preset("Ash", 0xDDDDDD, 0x888888),
    };

    /** UI accent: 0 = follow the glow colour, 1.. = fixed colour. */
    public static final String[] UI_ACCENT_NAMES = {"Follow glow", "Violet", "Ice", "Pink", "Mint", "Gold", "Crimson", "Sky"};
    private static final int[] UI_ACCENT = {0, 0xA855F7, 0x7FA8FF, 0xFF7ACB, 0x4ADE80, 0xFFB020, 0xF43F5E, 0x38BDF8};

    /** Menu background: {panel, cards}. */
    public static final String[] UI_BG_NAMES = {"Violet night", "Black", "Midnight", "Wine", "Forest", "Slate"};
    private static final int[][] UI_BG = {
        {0x0B0716, 0x120A22}, {0x000000, 0x0C0C0C}, {0x070D1C, 0x0D1830},
        {0x14070B, 0x240E15}, {0x06120B, 0x0D2316}, {0x101216, 0x1C1F26}};

    private Theme() {
    }

    /** Index of the preset with exactly these colours, or -1 (custom). */
    public static int indexOf(int text, int glow) {
        for (int i = 0; i < PRESETS.length; i++) {
            if ((PRESETS[i].text & 0xFFFFFF) == (text & 0xFFFFFF) && (PRESETS[i].glow & 0xFFFFFF) == (glow & 0xFFFFFF)) return i;
        }
        return -1;
    }

    public static int indexOf(String name) {
        if (name == null) return -1;
        String n = name.trim().toLowerCase(Locale.ROOT).replace('_', ' ');
        for (int i = 0; i < PRESETS.length; i++) {
            if (PRESETS[i].name.toLowerCase(Locale.ROOT).equals(n)) return i;
        }
        return -1;
    }

    public static String currentName(Settings s) {
        int i = indexOf(s.textColor, s.glowColor);
        return i < 0 ? "Custom" : PRESETS[i].name;
    }

    public static void apply(Settings s, int index) {
        Preset p = PRESETS[Math.floorMod(index, PRESETS.length)];
        s.textColor = p.text;
        s.glowColor = p.glow;
    }

    /** The next preset after the current colours (the first one when the colours are custom). */
    public static int nextIndex(Settings s) {
        int i = indexOf(s.textColor, s.glowColor);
        return (i + 1) % PRESETS.length;
    }

    public static int prevIndex(Settings s) {
        int i = indexOf(s.textColor, s.glowColor);
        return i < 0 ? PRESETS.length - 1 : Math.floorMod(i - 1, PRESETS.length);
    }

    // ------------------------------------------------------------------ UI colours

    public static int accent() {
        Settings s = Settings.I;
        int k = s.uiAccent;
        int base = (k <= 0 || k >= UI_ACCENT.length) ? (s.glowColor & 0xFFFFFF) : UI_ACCENT[k];
        return ensureVisible(base);
    }

    public static int bg() {
        return UI_BG[clampBg(Settings.I.uiBg)][0];
    }

    public static int bg2() {
        return UI_BG[clampBg(Settings.I.uiBg)][1];
    }

    private static int clampBg(int i) {
        return i < 0 || i >= UI_BG.length ? 0 : i;
    }

    /** Perceived brightness 0..1. */
    public static float luma(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        return 0.2126f * r + 0.7152f * g + 0.0722f * b;
    }

    /** Lightens a colour until it is readable on the dark menu background. */
    public static int ensureVisible(int rgb) {
        rgb &= 0xFFFFFF;
        if (luma(rgb) >= 0.36f) return rgb;
        int c = rgb;
        for (int i = 1; i <= 12 && luma(c) < 0.36f; i++) {
            c = Gfx.lerpRgb(rgb, 0xFFFFFF, i * 0.07f);
        }
        return c;
    }

    // ------------------------------------------------------------------ hex

    public static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    /** "#RRGGBB", "RRGGBB" or "#RGB" -> rgb, or -1 when it is not a colour. */
    public static int parseHex(String s) {
        if (s == null) return -1;
        String t = s.trim();
        if (t.startsWith("#")) t = t.substring(1);
        try {
            if (t.length() == 3) {
                int r = Integer.parseInt(t.substring(0, 1), 16);
                int g = Integer.parseInt(t.substring(1, 2), 16);
                int b = Integer.parseInt(t.substring(2, 3), 16);
                return (r * 17 << 16) | (g * 17 << 8) | (b * 17);
            }
            if (t.length() == 6) return Integer.parseInt(t, 16);
        } catch (NumberFormatException ignored) {
        }
        return -1;
    }
}
