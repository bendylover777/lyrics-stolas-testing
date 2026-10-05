package com.evolyrics;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SettingsScreen extends Screen {
    private static final String[] TABS = {"Lyrics", "Look", "Font", "Music"};
    private static final String[] PRESET_NAMES = {"Ice", "Pink", "Violet", "Gold", "Mint", "Pure white"};
    private static final int[][] PRESETS = {
        {0xFFFFFF, 0x7FA8FF}, {0xFFFFFF, 0xFF7ACB}, {0xF2E8FF, 0xA855F7},
        {0xFFF3B0, 0xFFB020}, {0xE8FFE8, 0x4ADE80}, {0xFFFFFF, 0xFFFFFF}};

    private static int tab = 0;
    private static int preset = 0;
    private static boolean switching = false;
    private static boolean skipSlide = false;
    private static long panelStart = 0L;

    private final Screen parent;
    private final long tabStart;
    private final List<AbstractWidget> ws = new ArrayList<>();
    private int top, bottom, left, right, colW, contentTop;

    public SettingsScreen(Screen parent) {
        super(Component.literal("Stolas Lyrics"));
        this.parent = parent;
        long now = System.nanoTime();
        if (!switching) panelStart = now;
        tabStart = skipSlide ? 0L : now;
        switching = false;
        skipSlide = false;
    }

    private void openTab(int t) {
        tab = t;
        switching = true;
        minecraft.setScreen(new SettingsScreen(parent));
    }

    private void refresh() {
        switching = true;
        skipSlide = true;
        minecraft.setScreen(new SettingsScreen(parent));
    }

    private <T extends AbstractWidget> T add(T w) {
        ws.add(w);
        return addRenderableWidget(w);
    }

    @Override
    protected void init() {
        Settings s = Settings.I;
        colW = 150;
        int gap = 8, h = 20, step = 22;
        left = width / 2 - colW - gap / 2;
        right = width / 2 + gap / 2;
        int y0 = Math.max(34, height / 2 - 100);
        top = y0;

        int tw = (colW * 2 + gap - 3 * 4) / 4;
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            add(Button.builder(Component.literal(TABS[i]), b -> openTab(idx))
                .bounds(left + i * (tw + 4), y0, tw, h).build());
        }

        int cy = y0 + 28;
        contentTop = cy;
        switch (tab) {
            case 0 -> initLyrics(s, cy, step, h);
            case 1 -> initLook(s, cy + 8, step, h);
            case 2 -> initFont(s, cy, step, h);
            default -> initMusic(s, cy, step, h);
        }

        int doneY = y0 + 28 + 8 + step * 6 + 6;
        bottom = doneY + 30;
        add(Button.builder(Component.literal("Done"), b -> onClose())
            .bounds(width / 2 - 60, doneY, 120, h).build());
    }

    private void initLyrics(Settings s, int cy, int step, int h) {
        add(toggle(left, cy, colW, "Lyrics", () -> s.enabled, v -> s.enabled = v));
        add(new FSlider(left, cy + step, colW, h, "Size", 0.3f, 3f, s.size, 2, v -> s.size = v));
        add(new FSlider(left, cy + step * 2, colW, h, "Distance", 2f, 20f, s.distance, 1, v -> s.distance = v));
        add(new FSlider(left, cy + step * 3, colW, h, "Spread", 0f, 1f, s.scatter, 2, v -> s.scatter = v));
        add(new FSlider(left, cy + step * 4, colW, h, "Opacity", 0.1f, 1f, s.opacity, 2, v -> s.opacity = v));
        add(new FSlider(left, cy + step * 5, colW, h, "Sync offset", -3f, 3f, s.syncOffset, 2, v -> s.syncOffset = v));

        add(cycle(right, cy, colW, "In effect", () -> s.in().label, () -> s.inEffect = s.in().next().name()));
        add(cycle(right, cy + step, colW, "Out effect", () -> s.out().label, () -> s.outEffect = s.out().next().name()));
        add(cycle(right, cy + step * 2, colW, "Layout", () -> Settings.POSITIONS[s.position % Settings.POSITIONS.length],
            () -> s.position = (s.position + 1) % Settings.POSITIONS.length));
        add(toggle(right, cy + step * 3, colW, "Through walls", () -> s.throughWalls, v -> s.throughWalls = v));
        add(new FSlider(right, cy + step * 4, colW, h, "Shimmer speed", 0.2f, 3f, s.shimmerSpeed, 1, v -> s.shimmerSpeed = v));
    }

    private void initLook(Settings s, int cy, int step, int h) {
        add(colorSlider(left, cy, "Text R", 16, true));
        add(colorSlider(left, cy + step, "Text G", 8, true));
        add(colorSlider(left, cy + step * 2, "Text B", 0, true));
        add(colorSlider(right, cy, "Glow R", 16, false));
        add(colorSlider(right, cy + step, "Glow G", 8, false));
        add(colorSlider(right, cy + step * 2, "Glow B", 0, false));

        add(new FSlider(left, cy + step * 3, colW, h, "Neon", 0f, 1f, s.neon, 2, v -> s.neon = v));
        add(new FSlider(right, cy + step * 3, colW, h, "Glow", 0f, 1f, s.glow, 2, v -> s.glow = v));
        add(new FSlider(left, cy + step * 4, colW, h, "Blur", 0f, 1f, s.blur, 2, v -> s.blur = v));
        add(new FSlider(right, cy + step * 4, colW, h, "Tilt", 0f, 15f, s.tilt, 1, v -> s.tilt = v));

        int next = (preset + 1) % PRESETS.length;
        add(Button.builder(Component.literal("Preset: " + PRESET_NAMES[next]), b -> {
            preset = next;
            s.textColor = PRESETS[next][0];
            s.glowColor = PRESETS[next][1];
            refresh();
        }).bounds(left, cy + step * 5, colW, h).build());
        add(cycle(right, cy + step * 5, colW, "Shimmer", () -> Settings.SHIMMER[s.shimmer % Settings.SHIMMER.length],
            () -> s.shimmer = (s.shimmer + 1) % Settings.SHIMMER.length));
    }

    private void initFont(Settings s, int cy, int step, int h) {
        List<FontList.Entry> fonts = FontList.list();
        int n = Math.min(fonts.size(), 12);
        String cur = s.font == null ? "" : s.font;
        for (int i = 0; i < n; i++) {
            final FontList.Entry e = fonts.get(i);
            boolean sel = e.id.equals(cur);
            int x = (i % 2 == 0) ? left : right;
            int y = cy + (i / 2) * step;
            add(Button.builder(Component.literal((sel ? "> " : "") + e.label), b -> {
                s.font = e.id;
                refresh();
            }).bounds(x, y, colW, h).build());
        }
    }

    private void initMusic(Settings s, int cy, int step, int h) {
        add(cycle(left, cy, colW, "Source", () -> s.useBridge ? "Bridge" : "Manual", () -> s.useBridge = !s.useBridge));
        add(cycle(right, cy, colW, "Song", () -> Playback.current == null ? "none" : Playback.current.id, () -> {
            List<String> ids = new ArrayList<>(SongLibrary.songs.keySet());
            if (ids.isEmpty()) return;
            int i = Playback.current == null ? -1 : ids.indexOf(Playback.current.id);
            Song next = SongLibrary.songs.get(ids.get((i + 1) % ids.size()));
            s.lastSong = next.id;
            Playback.play(next);
        }));
        add(cycle(left, cy + step, colW, "Playback", () -> Playback.isPlaying() ? "Playing" : "Paused", Playback::toggle));
        add(Button.builder(Component.literal("Reload songs"), b -> {
            String cur = Playback.current == null ? null : Playback.current.id;
            SongLibrary.reload();
            Playback.current = SongLibrary.find(cur);
        }).bounds(right, cy + step, colW, h).build());
        add(toggle(left, cy + step * 2, colW, "Island", () -> s.island, v -> s.island = v));
        add(toggle(right, cy + step * 2, colW, "Island scroll", () -> s.islandScroll, v -> s.islandScroll = v));
        add(toggle(left, cy + step * 3, colW, "Island lyrics", () -> s.islandLyrics, v -> s.islandLyrics = v));
    }

    private FSlider colorSlider(int x, int y, String label, int shift, boolean text) {
        Settings s = Settings.I;
        int cur = ((text ? s.textColor : s.glowColor) >> shift) & 0xFF;
        return new FSlider(x, y, colW, 20, label, 0f, 255f, cur, 0, v -> {
            int c = Math.round(v) & 0xFF;
            int mask = ~(0xFF << shift);
            if (text) s.textColor = (s.textColor & mask) | (c << shift);
            else s.glowColor = (s.glowColor & mask) | (c << shift);
        });
    }

    private Button toggle(int x, int y, int w, String name, Supplier<Boolean> get, Consumer<Boolean> set) {
        return Button.builder(Component.literal(name + ": " + (get.get() ? "ON" : "OFF")), b -> {
            set.accept(!get.get());
            b.setMessage(Component.literal(name + ": " + (get.get() ? "ON" : "OFF")));
        }).bounds(x, y, w, 20).build();
    }

    private Button cycle(int x, int y, int w, String name, Supplier<String> get, Runnable next) {
        return Button.builder(Component.literal(name + ": " + get.get()), b -> {
            next.run();
            b.setMessage(Component.literal(name + ": " + get.get()));
        }).bounds(x, y, w, 20).build();
    }

    private static float ease(float p) {
        if (p < 0f) p = 0f;
        if (p > 1f) p = 1f;
        float q = 1f - p;
        return 1f - q * q * q;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        Settings st = Settings.I;
        long now = System.nanoTime();
        float pAnim = ease((now - panelStart) / 1e9f / 0.28f);
        float tAnim = ease((now - tabStart) / 1e9f / 0.22f);
        double secs = now / 1e9;
        int glow = st.glowColor & 0xFFFFFF;
        int pulse = 0x90 + (int) (0x6F * (Math.sin(secs * 2.4) * 0.5 + 0.5));
        int bgA = (int) (0xD0 * pAnim);

        g.pose().pushPose();
        float sc = 0.9f + 0.1f * pAnim;
        g.pose().translate(width / 2f, height / 2f, 0f);
        g.pose().scale(sc, sc, 1f);
        g.pose().translate(-width / 2f, -height / 2f, 0f);

        int x1 = width / 2 - 168, x2 = width / 2 + 168;
        int y1 = top - 24;
        g.fill(x1 - 2, y1 - 2, x2 + 2, bottom + 2, ((bgA / 6) << 24) | glow);
        g.fill(x1, y1, x2, bottom, (bgA << 24) | 0x0A0614);
        g.renderOutline(x1, y1, x2 - x1, bottom - y1, ((int) (pulse * pAnim) << 24) | glow);

        // glowing title
        int tcx = width / 2;
        int ty = top - 16;
        int gl = ((int) (0x55 * pAnim) << 24) | glow;
        g.drawCenteredString(font, title, tcx - 1, ty, gl);
        g.drawCenteredString(font, title, tcx + 1, ty, gl);
        g.drawCenteredString(font, title, tcx, ty - 1, gl);
        g.drawCenteredString(font, title, tcx, ty + 1, gl);
        g.drawCenteredString(font, title, tcx, ty, 0xFFFFFFFF);

        // active tab underline
        int tw = (colW * 2 + 8 - 12) / 4;
        int ux = left + tab * (tw + 4);
        g.fill(ux, top + 22, ux + tw, top + 24, 0xFF000000 | glow);

        // content slides in on tab switch
        g.pose().pushPose();
        g.pose().translate((1f - tAnim) * 18f, 0f, 0f);
        if (tab == 1) {
            g.fill(left, contentTop + 1, left + colW, contentTop + 6, 0xFF000000 | st.textColor);
            g.fill(right, contentTop + 1, right + colW, contentTop + 6, 0xFF000000 | st.glowColor);
        }
        super.render(g, mx, my, pt);
        for (AbstractWidget w : ws) {
            if (w.active && w.isMouseOver(mx, my)) {
                g.renderOutline(w.getX() - 1, w.getY() - 1, w.getWidth() + 2, w.getHeight() + 2, 0xFF000000 | glow);
            }
        }
        if (tab == 2) {
            g.drawCenteredString(font, "Your own fonts: resource pack -> assets/evolyrics/font/", width / 2, contentTop + 136, 0xFF9A8CC0);
        } else if (tab == 3) {
            g.drawCenteredString(font, "Songs: .minecraft/config/evolyrics/songs", width / 2, contentTop + 96, 0xFF9A8CC0);
        }
        g.pose().popPose();

        g.pose().popPose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        Settings.save();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private static final class FSlider extends AbstractSliderButton {
        private final String name;
        private final float min, max;
        private final int decimals;
        private final Consumer<Float> set;

        FSlider(int x, int y, int w, int h, String name, float min, float max, float val, int decimals, Consumer<Float> set) {
            super(x, y, w, h, Component.empty(), (val - min) / (max - min));
            this.name = name;
            this.min = min;
            this.max = max;
            this.decimals = decimals;
            this.set = set;
            updateMessage();
        }

        private float get() {
            return min + (float) value * (max - min);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(name + ": " + String.format(Locale.ROOT, "%." + decimals + "f", get())));
        }

        @Override
        protected void applyValue() {
            set.accept(get());
        }
    }
}
