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
    private static final String[] TABS = {"Lyrics", "Look", "Anim", "Font", "Music", "Now"};
    private static final String[] PRESET_NAMES = {"Ice", "Pink", "Violet", "Gold", "Mint", "Pure white"};
    private static final int[][] PRESETS = {
        {0xFFFFFF, 0x7FA8FF}, {0xFFFFFF, 0xFF7ACB}, {0xF2E8FF, 0xA855F7},
        {0xFFF3B0, 0xFFB020}, {0xE8FFE8, 0x4ADE80}, {0xFFFFFF, 0xFFFFFF}};

    private static int tab = 0;
    private TimeSlider timeSlider;
    private static int preset = 0;
    private static boolean switching = false;
    private static boolean skipSlide = false;
    private static long panelStart = 0L;

    private final Screen parent;
    private final long tabStart;
    private final List<AbstractWidget> content = new ArrayList<>();
    private final List<AbstractWidget> header = new ArrayList<>();
    private int panelTop, tabsY, bottom, left, right, colW, contentTop;

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
        content.add(w);
        return addRenderableWidget(w);
    }

    /** Header widgets (tabs, Done) are drawn by hand so they do not slide with the content. */
    private <T extends AbstractWidget> T addHeader(T w) {
        header.add(w);
        return addWidget(w);
    }

    @Override
    protected void init() {
        Settings s = Settings.I;
        colW = 150;
        int gap = 8, h = 20, step = 22;
        left = width / 2 - colW - gap / 2;
        right = width / 2 + gap / 2;
        panelTop = Math.max(4, (height - 230) / 2);
        if (height >= 330) panelTop = Math.max(panelTop, 76);   // room for the expanded island above the panel
        tabsY = panelTop + 56;

        int x2 = width / 2 + 168;
        addHeader(Button.builder(Component.literal("Done"), b -> onClose())
            .bounds(x2 - 8 - 46, panelTop + 4, 46, 14).build());

        int tw = (colW * 2 + gap - (TABS.length - 1) * 4) / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            addHeader(Button.builder(Component.literal(TABS[i]), b -> openTab(idx))
                .bounds(left + i * (tw + 4), tabsY, tw, h).build());
        }

        int cy = tabsY + 28;
        contentTop = cy;
        switch (tab) {
            case 0 -> initLyrics(s, cy, step, h);
            case 1 -> initLook(s, cy, step, h);
            case 2 -> initAnim(s, cy, step, h);
            case 3 -> initFont(s, cy, step, h);
            case 4 -> initMusic(s, cy, step, h);
            default -> { }   // "Now" tab is drawn by hand (see drawNow)
        }
        bottom = cy + step * 6 + 14;
    }

    private void initLyrics(Settings s, int cy, int step, int h) {
        add(toggle(left, cy, colW, "Lyrics", () -> s.enabled, v -> s.enabled = v));
        add(new FSlider(left, cy + step, colW, h, "Size", 0.3f, 3f, s.size, 2, v -> s.size = v));
        add(new FSlider(left, cy + step * 2, colW, h, "Distance", 2f, 20f, s.distance, 1, v -> s.distance = v));
        add(new FSlider(left, cy + step * 3, colW, h, "Spread", 0f, 1f, s.scatter, 2, v -> s.scatter = v));
        add(new FSlider(left, cy + step * 4, colW, h, "Opacity", 0.1f, 1f, s.opacity, 2, v -> s.opacity = v));
        add(new FSlider(left, cy + step * 5, colW, h, "Sync offset", -3f, 3f, s.syncOffset, 2, v -> s.syncOffset = v));

        add(cycle(right, cy, colW, "Layout", () -> Settings.POSITIONS[s.position % Settings.POSITIONS.length],
            () -> s.position = (s.position + 1) % Settings.POSITIONS.length));
        add(toggle(right, cy + step, colW, "Through walls", () -> s.throughWalls, v -> s.throughWalls = v));
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

        int next = (preset + 1) % PRESETS.length;
        add(Button.builder(Component.literal("Preset: " + PRESET_NAMES[next]), b -> {
            preset = next;
            s.textColor = PRESETS[next][0];
            s.glowColor = PRESETS[next][1];
            refresh();
        }).bounds(right, cy + step * 4, colW, h).build());
    }

    private void initAnim(Settings s, int cy, int step, int h) {
        add(cycle(left, cy, colW, "In effect", () -> s.in().label, () -> s.inEffect = s.in().next().name()));
        add(cycle(right, cy, colW, "Out effect", () -> s.out().label, () -> s.outEffect = s.out().next().name()));
        add(cycle(left, cy + step, colW, "Effects from", () -> Settings.EFFECT_MODES[s.effectMode % Settings.EFFECT_MODES.length],
            () -> s.effectMode = (s.effectMode + 1) % Settings.EFFECT_MODES.length));
        add(cycle(right, cy + step, colW, "Shimmer", () -> Settings.SHIMMER[s.shimmer % Settings.SHIMMER.length],
            () -> s.shimmer = (s.shimmer + 1) % Settings.SHIMMER.length));
        add(new FSlider(left, cy + step * 2, colW, h, "Shimmer speed", 0.2f, 3f, s.shimmerSpeed, 1, v -> s.shimmerSpeed = v));
        add(new FSlider(right, cy + step * 2, colW, h, "Glint brightness", 0f, 3f, s.glint, 1, v -> s.glint = v));
        add(new FSlider(left, cy + step * 3, colW, h, "Tilt", 0f, 15f, s.tilt, 1, v -> s.tilt = v));
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
        add(cycle(left, cy, colW, "Source", () -> Settings.SOURCES[s.source % Settings.SOURCES.length],
            () -> s.source = (s.source + 1) % Settings.SOURCES.length));
        add(Button.builder(Component.literal("Reload songs"), b -> {
            String cur = Playback.current == null ? null : Playback.current.id;
            SongLibrary.reload();
            Playback.current = SongLibrary.find(cur);
        }).bounds(right, cy, colW, h).build());

        // transport: previous / play-pause / next
        int total = colW * 2 + 8;
        int bw = (total - 8) / 3;
        add(Button.builder(Component.literal("<<  Prev"), b -> Transport.prev()).bounds(left, cy + step, bw, h).build());
        add(Button.builder(Component.literal(Playback.isPlaying() ? "Pause" : "Play"), b -> {
            Transport.toggle();
            b.setMessage(Component.literal(Playback.isPlaying() ? "Pause" : "Play"));
        }).bounds(left + bw + 4, cy + step, bw, h).build());
        add(Button.builder(Component.literal("Next  >>"), b -> Transport.next()).bounds(left + (bw + 4) * 2, cy + step, bw, h).build());

        timeSlider = add(new TimeSlider(left, cy + step * 2, total, h));

        add(toggle(left, cy + step * 3, colW, "Island", () -> s.island, v -> s.island = v));
        add(toggle(right, cy + step * 3, colW, "Island scroll", () -> s.islandScroll, v -> s.islandScroll = v));
        add(toggle(left, cy + step * 4, colW, "Island lyrics", () -> s.islandLyrics, v -> s.islandLyrics = v));
        add(toggle(right, cy + step * 4, colW, "Console line", () -> s.consoleLine, v -> s.consoleLine = v));
        add(toggle(left, cy + step * 5, colW, "Side preview", () -> s.sidePreview, v -> s.sidePreview = v));
        add(toggle(right, cy + step * 5, colW, "Auto lyrics", () -> s.autoLyrics, v -> s.autoLyrics = v));
    }

    @Override
    public void tick() {
        super.tick();
        if (timeSlider != null) timeSlider.sync();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (ClientEvents.OPEN.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
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

    private void hover(GuiGraphics g, List<AbstractWidget> list, int mx, int my, int glow) {
        for (AbstractWidget w : list) {
            if (w.active && w.isMouseOver(mx, my)) {
                g.renderOutline(w.getX() - 1, w.getY() - 1, w.getWidth() + 2, w.getHeight() + 2, 0xFF000000 | glow);
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        Settings st = Settings.I;
        long now = System.nanoTime();
        float pAnim = ease((now - panelStart) / 1e9f / 0.15f);
        float tAnim = ease((now - tabStart) / 1e9f / 0.22f);
        double secs = now / 1e9;
        int glow = st.glowColor & 0xFFFFFF;
        int pulse = 0x90 + (int) (0x6F * (Math.sin(secs * 2.4) * 0.5 + 0.5));
        int bgA = (int) (0xD0 * pAnim);

        g.pose().pushPose();
        // the panel drops in from above the screen and bounces a little
        float drop = Gfx.bounce((now - panelStart) / 1e9f / 0.65f);
        g.pose().translate(0f, -(bottom + 12) * (1f - drop), 0f);

        int x1 = width / 2 - 168, x2 = width / 2 + 168;
        int y1 = panelTop;
        g.fill(x1 - 2, y1 - 2, x2 + 2, bottom + 2, ((bgA / 6) << 24) | glow);
        g.fill(x1, y1, x2, bottom, (bgA << 24) | 0x0A0614);
        g.renderOutline(x1, y1, x2 - x1, bottom - y1, ((int) (pulse * pAnim) << 24) | glow);

        // glowing title (left of the header)
        int tx = x1 + 8;
        int ty = y1 + 8;
        int gl = ((int) (0x55 * pAnim) << 24) | glow;
        g.drawString(font, title, tx - 1, ty, gl, false);
        g.drawString(font, title, tx + 1, ty, gl, false);
        g.drawString(font, title, tx, ty - 1, gl, false);
        g.drawString(font, title, tx, ty + 1, gl, false);
        g.drawString(font, title, tx, ty, 0xFFFFFFFF, false);

        // live effect preview (menu only)
        EffectPreview.render(g, font, x1 + 8, y1 + 22, x2 - x1 - 16, 30);

        // tabs + Done (do not slide)
        for (AbstractWidget w : header) w.render(g, mx, my, pt);
        hover(g, header, mx, my, glow);
        int tw = (colW * 2 + 8 - (TABS.length - 1) * 4) / TABS.length;
        int ux = left + tab * (tw + 4);
        g.fill(ux, tabsY + 22, ux + tw, tabsY + 24, 0xFF000000 | glow);

        // content slides in on tab switch
        g.pose().pushPose();
        g.pose().translate((1f - tAnim) * 18f, 0f, 0f);
        super.render(g, mx, my, pt);
        hover(g, content, mx, my, glow);
        if (tab == 3) {
            g.drawCenteredString(font, "More fonts: add them in a resource pack", width / 2, contentTop + 134, 0xFF9A8CC0);
        } else if (tab == 4) {
            g.drawCenteredString(font, "Keys: Controls > Player (menu, next, previous, pause)", width / 2, contentTop + 132, 0xFF9A8CC0);
        } else if (tab == 5) {
            drawNow(g, now, mx, my, glow);
        }
        g.pose().popPose();

        g.pose().popPose();
    }

    // ------------------------------------------------------------------ "Now" tab: previous / playing / next

    /** x, y, w, h of the three ovals: 0 = previous, 1 = now playing, 2 = next. */
    private int[] pill(int i) {
        int w = colW * 2 + 8;
        return switch (i) {
            case 0 -> new int[]{left, contentTop, w, 28};
            case 1 -> new int[]{left, contentTop + 33, w, 42};
            default -> new int[]{left, contentTop + 80, w, 28};
        };
    }

    private int pillAt(double mx, double my) {
        for (int i = 0; i < 3; i++) {
            int[] r = pill(i);
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) return i;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (tab == 5 && button == 0) {
            int hit = pillAt(mx, my);
            if (hit == 0) {
                Transport.prev();
                return true;
            }
            if (hit == 1) {
                Transport.toggle();
                return true;
            }
            if (hit == 2) {
                Transport.next();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private String clip(String s, int max) {
        if (max <= 0) return "";
        return font.width(s) <= max ? s : font.plainSubstrByWidth(s, max - font.width("...")) + "...";
    }

    private static String nameOf(Song s) {
        if (s == null) return "";
        return s.title == null || s.title.isBlank() ? s.id : s.title;
    }

    private static String initial(Song s) {
        String t = nameOf(s).trim();
        if (t.isEmpty()) return "?";
        return new String(Character.toChars(t.codePointAt(0))).toUpperCase(Locale.ROOT);
    }

    private void drawNow(GuiGraphics g, long now, int mx, int my, int glow) {
        float since = (now - tabStart) / 1e9f;
        double secs = now / 1e9;
        boolean remote = Transport.isRemote();
        String app = SystemBridge.app.isEmpty() ? "your player" : SystemBridge.app;
        Song prev = Transport.peek(-1);
        Song cur = Playback.current;
        Song next = Transport.peek(1);
        int hover = pillAt(mx, my);

        for (int i = 0; i < 3; i++) {
            int[] r = pill(i);
            int order = i == 1 ? 0 : (i == 0 ? 1 : 2);          // now playing drops first
            float p = (since - 0.09f * order) / 0.5f;
            float a = Gfx.easeOut(p * 2f);
            if (a < 0.02f) continue;
            int dy = Math.round(-26f * (1f - Gfx.bounce(p)));
            if (i == 0) {
                drawPill(g, r[0], r[1] + dy, r[2], r[3], "PREVIOUS", prev,
                    remote ? "No history yet" : "Nothing here", false, hover == 0, a, glow, secs);
            } else if (i == 1) {
                drawPill(g, r[0], r[1] + dy, r[2], r[3], Playback.isPlaying() ? "NOW PLAYING" : "PAUSED", cur,
                    "Nothing playing", true, hover == 1, a, glow, secs);
            } else {
                drawPill(g, r[0], r[1] + dy, r[2], r[3], "NEXT", next,
                    remote ? "Chosen by " + app : "Nothing here", false, hover == 2, a, glow, secs);
            }
        }
        int hy = contentTop + 114;
        g.drawCenteredString(font, remote ? "Click Previous / Next to switch the track in " + app
            : "Click Previous / Next to jump to that song", width / 2, hy, 0xFF9A8CC0);
        g.drawCenteredString(font, "Shuffle, repeat and the time bar are on the island", width / 2, hy + 10, 0xFF9A8CC0);
    }

    private void drawPill(GuiGraphics g, int x, int y, int w, int h, String label, Song song, String empty,
                          boolean big, boolean hover, float a, int glow, double secs) {
        int r = h / 2;
        float pulse = big ? (float) (Math.sin(secs * 2.4) * 0.5 + 0.5) : 0f;
        float halo = big ? 0.20f + 0.16f * pulse : (hover ? 0.30f : 0.10f);
        Gfx.fillRound(g, x - 2, y - 2, w + 4, h + 4, r + 2, Gfx.argb(glow, halo * a));
        Gfx.fillRound(g, x, y, w, h, r, Gfx.fade(0xF0120A22, a));

        // round "cover" with the first letter
        int dr = r - 4;
        int dcx = x + r;
        int dcy = y + h / 2;
        Gfx.fillCircle(g, dcx, dcy, dr, Gfx.argb(glow, (big ? 0.85f : 0.40f) * a));
        g.drawCenteredString(font, song == null ? "-" : initial(song), dcx, dcy - 4, Gfx.argb(0xFFFFFF, a));

        int tx = x + h + 2;
        int maxW = x + w - r - 6 - tx;
        g.pose().pushPose();
        g.pose().translate(tx, y + (big ? 6 : 4), 0f);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, label, 0, 0, Gfx.argb(big ? glow : 0xB8C0FF, 0.9f * a), false);
        g.pose().popPose();

        if (song == null) {
            g.drawString(font, clip(empty, maxW), tx, y + (big ? 17 : 13), Gfx.argb(0x9A8CC0, a), false);
            return;
        }
        String title = clip(nameOf(song), maxW);
        int ty = y + (big ? 15 : 12);
        g.drawString(font, title, tx, ty, Gfx.argb(0xFFFFFF, a), false);
        String artist = song.artist == null ? "" : song.artist;
        if (!artist.isBlank()) {
            if (big) {
                g.drawString(font, clip(artist, maxW), tx, ty + 10, Gfx.argb(0xB8C0FF, 0.85f * a), false);
            } else {
                int used = font.width(title) + 8;
                if (maxW - used > 24) g.drawString(font, clip(artist, maxW - used), tx + used, ty, Gfx.argb(0xB8C0FF, 0.7f * a), false);
            }
        }
        if (big) {
            // time + bar
            double len = Transport.length();
            double pos = Playback.position();
            float frac = len > 0 ? (float) Math.min(1.0, Math.max(0.0, pos / len)) : 0f;
            String time = TimeSlider.fmt(pos) + " / " + (len > 0 ? TimeSlider.fmt(len) : "--:--");
            g.pose().pushPose();
            g.pose().translate(x + w - r - 6 - font.width(time) * 0.75f, y + 6, 0f);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.drawString(font, time, 0, 0, Gfx.argb(0xB8C0FF, 0.9f * a), false);
            g.pose().popPose();
            int bw = Math.max(10, maxW);
            Gfx.fillRound(g, tx, y + h - 8, bw, 2, 1, Gfx.argb(0xFFFFFF, 0.22f * a));
            Gfx.fillRound(g, tx, y + h - 8, Math.max(2, Math.round(bw * frac)), 2, 1, Gfx.argb(glow, a));
        }
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

    /** Position slider: drag to jump; the real player is told when you let go. */
    private static final class TimeSlider extends AbstractSliderButton {
        private boolean dragging;

        TimeSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), 0.0);
            sync();
        }

        private static String fmt(double sec) {
            int t = (int) Math.max(0, sec);
            return String.format(Locale.ROOT, "%d:%02d", t / 60, t % 60);
        }

        void sync() {
            if (dragging) return;
            double len = Transport.length();
            this.value = len <= 0 ? 0.0 : Math.min(1.0, Math.max(0.0, Playback.position() / len));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            double len = Transport.length();
            setMessage(Component.literal("Time  " + fmt(this.value * len) + " / " + (len > 0 ? fmt(len) : "--:--")));
        }

        @Override
        protected void applyValue() {
            double len = Transport.length();
            if (len > 0) Transport.seekLocal(this.value * len);
        }

        @Override
        public void onClick(double mx, double my) {
            dragging = true;
            super.onClick(mx, my);
        }

        @Override
        public void onRelease(double mx, double my) {
            super.onRelease(mx, my);
            dragging = false;
            double len = Transport.length();
            if (len > 0) Transport.seekRemote(this.value * len);
        }
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
