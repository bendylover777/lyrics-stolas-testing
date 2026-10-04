package com.evolyrics;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SettingsScreen extends Screen {
    private final Screen parent;
    private int top, bottom;

    public SettingsScreen(Screen parent) {
        super(Component.literal("Stolas Lyrics"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Settings s = Settings.I;
        int colW = 150, gap = 8, h = 20, step = 22;
        int left = width / 2 - colW - gap / 2;
        int right = width / 2 + gap / 2;
        int rows = 11;
        int y0 = Math.max(30, (height - (step * rows + 34)) / 2 + 22);
        top = y0;
        int nFonts = Settings.FONT_NAMES.length;
        int nColors = Settings.COLOR_NAMES.length;

        // left column
        addRenderableWidget(toggle(left, y0, colW, "Lyrics", () -> s.enabled, v -> s.enabled = v));
        addRenderableWidget(new FSlider(left, y0 + step, colW, h, "Size", 0.3f, 3f, s.size, v -> s.size = v));
        addRenderableWidget(new FSlider(left, y0 + step * 2, colW, h, "Distance", 3f, 20f, s.distance, v -> s.distance = v));
        addRenderableWidget(new FSlider(left, y0 + step * 3, colW, h, "Scatter", 0f, 1f, s.scatter, v -> s.scatter = v));
        addRenderableWidget(new FSlider(left, y0 + step * 4, colW, h, "Opacity", 0.1f, 1f, s.opacity, v -> s.opacity = v));
        addRenderableWidget(new FSlider(left, y0 + step * 5, colW, h, "Glow", 0f, 1f, s.glow, v -> s.glow = v));
        addRenderableWidget(new FSlider(left, y0 + step * 6, colW, h, "Blur", 0f, 1f, s.blur, v -> s.blur = v));
        addRenderableWidget(new FSlider(left, y0 + step * 7, colW, h, "Sync offset", -3f, 3f, s.syncOffset, v -> s.syncOffset = v));
        addRenderableWidget(cycle(left, y0 + step * 8, colW, "Font", () -> Settings.FONT_NAMES[Math.floorMod(s.font, nFonts)],
            () -> s.font = (Math.floorMod(s.font, nFonts) + 1) % nFonts));
        addRenderableWidget(colorCycle(left, y0 + step * 9, colW, "Glow color", () -> s.glowColor,
            () -> s.glowColor = (Math.floorMod(s.glowColor, nColors) + 1) % nColors));
        addRenderableWidget(toggle(left, y0 + step * 10, colW, "Keep in view", () -> s.inView, v -> s.inView = v));

        // right column
        addRenderableWidget(cycle(right, y0, colW, "In effect", () -> s.in().label, () -> s.inEffect = s.in().next().name()));
        addRenderableWidget(cycle(right, y0 + step, colW, "Out effect", () -> s.out().label, () -> s.outEffect = s.out().next().name()));
        addRenderableWidget(cycle(right, y0 + step * 2, colW, "Position", () -> Settings.POSITIONS[s.position % Settings.POSITIONS.length],
            () -> s.position = (s.position + 1) % Settings.POSITIONS.length));
        addRenderableWidget(toggle(right, y0 + step * 3, colW, "Through walls", () -> s.throughWalls, v -> s.throughWalls = v));
        addRenderableWidget(cycle(right, y0 + step * 4, colW, "Source", () -> s.useBridge ? "Bridge" : "Manual", () -> s.useBridge = !s.useBridge));
        addRenderableWidget(cycle(right, y0 + step * 5, colW, "Song", () -> Playback.current == null ? "none" : Playback.current.id, () -> {
            List<String> ids = new ArrayList<>(SongLibrary.songs.keySet());
            if (ids.isEmpty()) return;
            int i = Playback.current == null ? -1 : ids.indexOf(Playback.current.id);
            Song next = SongLibrary.songs.get(ids.get((i + 1) % ids.size()));
            s.lastSong = next.id;
            Playback.play(next);
        }));
        addRenderableWidget(cycle(right, y0 + step * 6, colW, "Playback", () -> Playback.isPlaying() ? "Playing" : "Paused", Playback::toggle));
        addRenderableWidget(Button.builder(Component.literal("Reload songs"), b -> {
            String cur = Playback.current == null ? null : Playback.current.id;
            SongLibrary.reload();
            Playback.current = SongLibrary.find(cur);
        }).bounds(right, y0 + step * 7, colW, h).build());
        addRenderableWidget(colorCycle(right, y0 + step * 8, colW, "Text color", () -> s.textColor,
            () -> s.textColor = (Math.floorMod(s.textColor, nColors) + 1) % nColors));
        addRenderableWidget(toggle(right, y0 + step * 9, colW, "Shadow", () -> s.shadow, v -> s.shadow = v));
        addRenderableWidget(toggle(right, y0 + step * 10, colW, "Island (song title)", () -> s.island, v -> s.island = v));

        bottom = y0 + step * rows + 30;
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
            .bounds(width / 2 - 60, y0 + step * rows + 4, 120, h).build());
    }

    private Button colorCycle(int x, int y, int w, String name, Supplier<Integer> get, Runnable next) {
        return Button.builder(colorLabel(name, get.get()), b -> {
            next.run();
            b.setMessage(colorLabel(name, get.get()));
        }).bounds(x, y, w, 20).build();
    }

    private static Component colorLabel(String name, int idx) {
        int i = Math.floorMod(idx, Settings.COLOR_NAMES.length);
        MutableComponent c = Component.literal(name + ": " + Settings.COLOR_NAMES[i]);
        int rgb = Settings.COLOR_RGB[i];
        return rgb < 0 ? c : c.withStyle(Style.EMPTY.withColor(rgb));
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

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int x1 = width / 2 - 168, x2 = width / 2 + 168;
        g.fill(x1, top - 24, x2, bottom, 0xD0120820);
        g.renderOutline(x1, top - 24, x2 - x1, bottom - top + 24, 0xFF8B5CF6);
        g.drawCenteredString(font, title, width / 2, top - 16, 0xFFD9C8FF);
        super.render(g, mx, my, pt);
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
        private final Consumer<Float> set;

        FSlider(int x, int y, int w, int h, String name, float min, float max, float val, Consumer<Float> set) {
            super(x, y, w, h, Component.empty(), (val - min) / (max - min));
            this.name = name;
            this.min = min;
            this.max = max;
            this.set = set;
            updateMessage();
        }

        private float get() {
            return min + (float) value * (max - min);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(name + ": " + String.format(Locale.ROOT, "%.2f", get())));
        }

        @Override
        protected void applyValue() {
            set.accept(get());
        }
    }
}
