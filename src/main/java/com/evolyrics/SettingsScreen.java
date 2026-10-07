package com.evolyrics;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
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
    private int gridLeft, gridRight, gridTop;
    private static int page = 0;

    public SettingsScreen(Screen parent) {
        super(Component.literal("Stolas Lyrics"));
        this.parent = parent;
    }

    private int gx(int k) {
        return k % 2 == 0 ? gridLeft : gridRight;
    }

    private int gy(int k) {
        return gridTop + 22 * (k / 2);
    }

    private static Component pageLabel() {
        return Component.literal(page == 0 ? "Style & effects  >" : "<  Main options");
    }

    @Override
    protected void init() {
        Settings s = Settings.I;
        int colW = 150, gap = 8, h = 20, step = 22;
        gridLeft = width / 2 - colW - gap / 2;
        gridRight = width / 2 + gap / 2;
        int rows = 9;
        int y0 = Math.max(30, (height - (step * rows + 34)) / 2 + 22);
        gridTop = y0;
        top = y0;
        int nFonts = Settings.FONT_NAMES.length;
        int nColors = Settings.COLOR_NAMES.length;

        List<AbstractWidget> p1 = new ArrayList<>();
        List<AbstractWidget> p2 = new ArrayList<>();

        // ---- page 1: main options ----
        int k = 0;
        p1.add(toggle(gx(k), gy(k), colW, "Lyrics", () -> s.enabled, v -> s.enabled = v)); k++;
        p1.add(toggle(gx(k), gy(k), colW, "Island (song title)", () -> s.island, v -> s.island = v)); k++;
        p1.add(new FSlider(gx(k), gy(k), colW, h, "Size", 0.3f, 3f, s.size, v -> s.size = v)); k++;
        p1.add(new FSlider(gx(k), gy(k), colW, h, "Distance", 3f, 20f, s.distance, v -> s.distance = v)); k++;
        p1.add(new FSlider(gx(k), gy(k), colW, h, "Scatter", 0f, 1f, s.scatter, v -> s.scatter = v)); k++;
        p1.add(new FSlider(gx(k), gy(k), colW, h, "Opacity", 0.1f, 1f, s.opacity, v -> s.opacity = v)); k++;
        p1.add(new FSlider(gx(k), gy(k), colW, h, "Sync offset", -3f, 3f, s.syncOffset, v -> s.syncOffset = v)); k++;
        p1.add(cycle(gx(k), gy(k), colW, "Position", () -> Settings.POSITIONS[s.position % Settings.POSITIONS.length],
            () -> s.position = (s.position + 1) % Settings.POSITIONS.length)); k++;
        p1.add(cycle(gx(k), gy(k), colW, "In effect", () -> s.in().label, () -> s.inEffect = s.in().next().name())); k++;
        p1.add(cycle(gx(k), gy(k), colW, "Out effect", () -> s.out().label, () -> s.outEffect = s.out().next().name())); k++;
        p1.add(cycle(gx(k), gy(k), colW, "Font", () -> Settings.FONT_NAMES[Math.floorMod(s.font, nFonts)],
            () -> s.font = (Math.floorMod(s.font, nFonts) + 1) % nFonts)); k++;
        p1.add(toggle(gx(k), gy(k), colW, "Keep in view", () -> s.inView, v -> s.inView = v)); k++;
        p1.add(toggle(gx(k), gy(k), colW, "Through walls", () -> s.throughWalls, v -> s.throughWalls = v)); k++;
        p1.add(toggle(gx(k), gy(k), colW, "Status line", () -> s.statusLine, v -> s.statusLine = v)); k++;
        p1.add(cycle(gx(k), gy(k), colW, "Source", () -> s.useBridge ? "Bridge" : "Manual", () -> s.useBridge = !s.useBridge)); k++;
        p1.add(cycle(gx(k), gy(k), colW, "Song", () -> Playback.current == null ? "none" : Playback.current.id, () -> {
            List<String> ids = new ArrayList<>(SongLibrary.songs.keySet());
            if (ids.isEmpty()) return;
            int i = Playback.current == null ? -1 : ids.indexOf(Playback.current.id);
            Song next = SongLibrary.songs.get(ids.get((i + 1) % ids.size()));
            s.lastSong = next.id;
            Playback.play(next);
        })); k++;
        p1.add(cycle(gx(k), gy(k), colW, "Playback", () -> Playback.isPlaying() ? "Playing" : "Paused", Playback::toggle)); k++;
        p1.add(Button.builder(Component.literal("Reload songs"), b -> {
            String cur = Playback.current == null ? null : Playback.current.id;
            SongLibrary.reload();
            Playback.current = SongLibrary.find(cur);
        }).bounds(gx(k), gy(k), colW, h).build());

        // ---- page 2: style & effects ----
        k = 0;
        p2.add(colorCycle(gx(k), gy(k), colW, "Text color", () -> s.textColor,
            () -> s.textColor = (Math.floorMod(s.textColor, nColors) + 1) % nColors)); k++;
        p2.add(colorCycle(gx(k), gy(k), colW, "Glow color", () -> s.glowColor,
            () -> s.glowColor = (Math.floorMod(s.glowColor, nColors) + 1) % nColors)); k++;
        p2.add(new FSlider(gx(k), gy(k), colW, h, "Glow", 0f, 1f, s.glow, v -> s.glow = v)); k++;
        p2.add(new FSlider(gx(k), gy(k), colW, h, "Blur", 0f, 1f, s.blur, v -> s.blur = v)); k++;
        p2.add(toggle(gx(k), gy(k), colW, "Neon flow", () -> s.neon, v -> s.neon = v)); k++;
        p2.add(new FSlider(gx(k), gy(k), colW, h, "Neon speed", 0.2f, 3f, s.neonSpeed, v -> s.neonSpeed = v)); k++;
        p2.add(new FSlider(gx(k), gy(k), colW, h, "Glint", 0f, 1f, s.glint, v -> s.glint = v)); k++;
        p2.add(toggle(gx(k), gy(k), colW, "Preview next line", () -> s.preview, v -> s.preview = v)); k++;
        p2.add(toggle(gx(k), gy(k), colW, "Shadow", () -> s.shadow, v -> s.shadow = v));

        for (AbstractWidget w : p1) addRenderableWidget(w);
        for (AbstractWidget w : p2) addRenderableWidget(w);
        Runnable applyPage = () -> {
            for (AbstractWidget w : p1) {
                w.visible = page == 0;
                w.active = page == 0;
            }
            for (AbstractWidget w : p2) {
                w.visible = page == 1;
                w.active = page == 1;
            }
        };
        applyPage.run();

        int by = y0 + step * rows + 4;
        bottom = y0 + step * rows + 30;
        addRenderableWidget(Button.builder(pageLabel(), b -> {
            page = 1 - page;
            applyPage.run();
            b.setMessage(pageLabel());
        }).bounds(gridLeft, by, colW, h).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
            .bounds(gridRight, by, colW, h).build());
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
