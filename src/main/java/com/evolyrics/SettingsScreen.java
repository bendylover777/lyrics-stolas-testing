package com.evolyrics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The settings menu. All widgets are drawn by hand (rounded, animated); tabs are switched inside the same screen
 * (no re-creation), so nothing flickers. Everything is time based, so it is equally smooth at any frame rate.
 */
public class SettingsScreen extends Screen {
    private static final String[] TABS = {"Text", "Theme", "Anim", "Font", "Music", "Now", "More"};
    private static final int MAX_ROWS = 7;
    private static final java.util.Random RNG = new java.util.Random();

    private static final int PANEL_HALF = 168;
    private static final float OPEN_TIME = 0.34f;
    private static final float CLOSE_TIME = 0.16f;

    private static int tab = 0;
    private static int fontPage = 0;

    // shared with the widgets, set once per frame in render()
    private static float uiAlpha = 1f;     // panel fade (open / close)
    private static float uiAge = 10f;      // seconds since the current tab was opened (drives the staggered entry)
    private static int uiGlow = 0x7FA8FF;

    // "Now" tab: what changed last (to slide the cards in the right direction)
    private static String lastCurId, lastPrevId, lastNextId;
    private static long changeNs;
    private static int changeDir;

    private final Screen parent;
    private final long openNs;
    private long tabNs;
    private long closeNs;
    private long lastNs;
    private boolean closing;
    private float indX = Float.NaN;
    private final float[] cardHov = new float[3];
    private final float[] cardPress = new float[3];
    private TimeSlider timeSlider;
    private EditBox hexText, hexGlow;
    private int fontPageCount = 1, fontTotal = 0;
    private float shownBottom = Float.NaN;

    private final List<AbstractWidget> content = new ArrayList<>();
    private final List<AbstractWidget> header = new ArrayList<>();
    private int panelTop, tabsY, bottom, left, right, colW, contentTop;

    public SettingsScreen(Screen parent) {
        super(Component.literal("Stolas Lyrics"));
        this.parent = parent;
        long now = System.nanoTime();
        openNs = now;
        tabNs = now - 150_000_000L;     // widgets start rising a moment after the panel appears
        lastNs = now;
        lastCurId = null;
    }

    private void openTab(int t) {
        if (t == tab) return;
        tab = t;
        tabNs = System.nanoTime();
        lastCurId = null;
        rebuild();
    }

    /** Re-creates the widgets in place (same screen object: no flicker, no restart of the open animation). */
    private void rebuild() {
        clearWidgets();
        setFocused(null);
        init();
    }

    private <T extends AbstractWidget> T add(T w) {
        int row = Math.max(0, (w.getY() - contentTop) / 22);
        int ord = row * 2 + (w.getX() >= right ? 1 : 0);
        if (w instanceof Btn b) b.order = ord;
        else if (w instanceof Sld s) s.order = ord;
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
        content.clear();
        header.clear();
        timeSlider = null;
        hexText = null;
        hexGlow = null;
        Settings s = Settings.I;
        colW = 150;
        int gap = 8, h = 20, step = 22;
        left = width / 2 - colW - gap / 2;
        right = width / 2 + gap / 2;
        panelTop = Math.max(4, (height - (98 + step * MAX_ROWS)) / 2);
        if (height >= 330) panelTop = Math.max(panelTop, 76);   // room for the expanded island above the panel
        tabsY = panelTop + 56;

        int x2 = width / 2 + PANEL_HALF;
        addHeader(new Btn(x2 - 12 - 46, panelTop + 5, 46, 14, "Done", Btn.ACTION, b -> onClose()));

        int tw = tabW();
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            Btn b = new Btn(left + i * (tw + 4), tabsY, tw, h, TABS[i], Btn.TAB, bb -> openTab(idx));
            b.selected = i == tab;
            addHeader(b);
        }

        int cy = tabsY + 28;
        contentTop = cy;
        int rows;
        switch (tab) {
            case 0 -> { initText(s, cy, step, h); rows = 6; }
            case 1 -> { initTheme(s, cy, step, h); rows = 7; }
            case 2 -> { initAnim(s, cy, step, h); rows = 5; }
            case 3 -> { initFont(s, cy, step, h); rows = 7; }
            case 4 -> { initMusic(s, cy, step, h); rows = 6; }
            case 6 -> { initMore(s, cy, step, h); rows = 7; }
            default -> rows = 6;   // "Now" tab is drawn by hand (see drawNow)
        }
        bottom = cy + step * rows + 14;
    }

    private int tabW() {
        return (colW * 2 + 8 - (TABS.length - 1) * 4) / TABS.length;
    }

    private void initText(Settings s, int cy, int step, int h) {
        add(toggle(left, cy, colW, "Lyrics", () -> s.enabled, v -> s.enabled = v));
        add(cycle(right, cy, colW, "Layout", () -> Settings.POSITIONS[s.position % Settings.POSITIONS.length],
            () -> s.position = (s.position + 1) % Settings.POSITIONS.length));
        add(new FSlider(left, cy + step, colW, h, "Size", 0.3f, 3f, s.size, 2, v -> s.size = v));
        add(toggle(right, cy + step, colW, "Through walls", () -> s.throughWalls, v -> s.throughWalls = v));
        add(new FSlider(left, cy + step * 2, colW, h, "Distance", 2f, 20f, s.distance, 1, v -> s.distance = v));
        add(toggle(right, cy + step * 2, colW, "Text shadow", () -> s.shadow, v -> s.shadow = v));
        add(new FSlider(left, cy + step * 3, colW, h, "Spread", 0f, 1f, s.scatter, 2, v -> s.scatter = v));
        add(toggle(right, cy + step * 3, colW, "Hide if paused", () -> s.hideWhenPaused, v -> s.hideWhenPaused = v));
        add(new FSlider(left, cy + step * 4, colW, h, "Opacity", 0.1f, 1f, s.opacity, 2, v -> s.opacity = v));
        add(new FSlider(right, cy + step * 4, colW, h, "Max lines", 1f, 8f, s.maxVisible, 0, v -> s.maxVisible = Math.round(v)));
        add(new FSlider(left, cy + step * 5, colW, h, "Sync offset", -3f, 3f, s.syncOffset, 2, v -> s.syncOffset = v));
        add(new FSlider(right, cy + step * 5, colW, h, "Anim speed", 0.3f, 3f, s.animSpeed, 2, v -> s.animSpeed = v));
    }

    private void initTheme(Settings s, int cy, int step, int h) {
        // the button shows the CURRENT theme ("Custom" when the colours match none) and goes to the next one
        add(cycle(left, cy, colW, "Theme", () -> Theme.currentName(s), () -> {
            Theme.apply(s, Theme.nextIndex(s));
            rebuild();
        }));
        add(new Btn(right, cy, colW, h, "Random theme", Btn.ACTION, b -> {
            Theme.apply(s, RNG.nextInt(Theme.PRESETS.length));
            rebuild();
        }));

        add(colorSlider(left, cy + step, "Text R", 16, true));
        add(colorSlider(left, cy + step * 2, "Text G", 8, true));
        add(colorSlider(left, cy + step * 3, "Text B", 0, true));
        add(colorSlider(right, cy + step, "Glow R", 16, false));
        add(colorSlider(right, cy + step * 2, "Glow G", 8, false));
        add(colorSlider(right, cy + step * 3, "Glow B", 0, false));

        hexText = add(hexBox(left, cy + step * 4, true));
        hexGlow = add(hexBox(right, cy + step * 4, false));

        add(new FSlider(left, cy + step * 5, colW, h, "Neon", 0f, 1f, s.neon, 2, v -> s.neon = v));
        add(new FSlider(right, cy + step * 5, colW, h, "Glow", 0f, 1f, s.glow, 2, v -> s.glow = v));
        add(new FSlider(left, cy + step * 6, colW, h, "Blur", 0f, 1f, s.blur, 2, v -> s.blur = v));
        add(new Btn(right, cy + step * 6, colW, h, "Swap colors", Btn.ACTION, b -> {
            int t = s.textColor;
            s.textColor = s.glowColor;
            s.glowColor = t;
            rebuild();
        }));
    }

    /** "#RRGGBB" field next to a small label (the label is drawn in render()). */
    private EditBox hexBox(int x, int y, boolean text) {
        Settings s = Settings.I;
        EditBox eb = new EditBox(font, x + 52, y, colW - 52, 20, Component.literal(text ? "Text hex" : "Glow hex"));
        eb.setMaxLength(7);
        eb.setFilter(v -> v.matches("#?[0-9a-fA-F]{0,6}"));
        eb.setValue(Theme.hex(text ? s.textColor : s.glowColor));
        eb.setResponder(v -> {
            int c = Theme.parseHex(v);
            if (c < 0) return;
            if (text) s.textColor = c;
            else s.glowColor = c;
        });
        return eb;
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
        add(new Btn(left, cy + step * 4, colW, h, "Random effects", Btn.ACTION, b -> {
            LyricEffect[] all = LyricEffect.values();
            s.inEffect = all[RNG.nextInt(all.length)].name();
            s.outEffect = all[RNG.nextInt(all.length)].name();
        }));
        add(new Btn(right, cy + step * 4, colW, h, "Swap in / out", Btn.ACTION, b -> {
            String t = s.inEffect;
            s.inEffect = s.outEffect;
            s.outEffect = t;
        }));
    }

    private void initFont(Settings s, int cy, int step, int h) {
        List<FontList.Entry> fonts = FontList.list();
        int per = 8;
        fontTotal = fonts.size();
        fontPageCount = Math.max(1, (fonts.size() + per - 1) / per);
        if (fontPage >= fontPageCount) fontPage = fontPageCount - 1;
        if (fontPage < 0) fontPage = 0;
        String cur = s.font == null ? "" : s.font;
        for (int i = 0; i < per; i++) {
            int idx = fontPage * per + i;
            if (idx >= fonts.size()) break;
            final FontList.Entry e = fonts.get(idx);
            int x = (i % 2 == 0) ? left : right;
            int y = cy + (i / 2) * step;
            Btn b = new Btn(x, y, colW, h, e.label, Btn.OPTION, bb -> {
                s.font = e.id;
                rebuild();
            });
            b.selected = e.id.equals(cur);
            b.fontId = e.id;                                  // each entry is shown in its own font
            add(b);
        }
        add(toggle(left, cy + step * 4, colW, "Bold", () -> s.bold, v -> s.bold = v));
        add(toggle(right, cy + step * 4, colW, "Italic", () -> s.italic, v -> s.italic = v));
        add(toggle(left, cy + step * 5, colW, "Underline", () -> s.underline, v -> s.underline = v));
        add(cycle(right, cy + step * 5, colW, "Letters", () -> Settings.CASES[s.textCase % Settings.CASES.length],
            () -> s.textCase = (s.textCase + 1) % Settings.CASES.length));
        final int pages = fontPageCount;
        int bw = (colW * 2 + 8 - 8) / 3;
        add(new Btn(left, cy + step * 6, bw, h, "< Page", Btn.ACTION, b -> {
            fontPage = Math.floorMod(fontPage - 1, pages);
            rebuild();
        }));
        add(new Btn(left + (bw + 4) * 2, cy + step * 6, bw, h, "Page >", Btn.ACTION, b -> {
            fontPage = Math.floorMod(fontPage + 1, pages);
            rebuild();
        }));
    }

    private void initMusic(Settings s, int cy, int step, int h) {
        add(cycle(left, cy, colW, "Source", () -> Settings.SOURCES[s.source % Settings.SOURCES.length],
            () -> s.source = (s.source + 1) % Settings.SOURCES.length));
        add(new Btn(right, cy, colW, h, "Reload songs", Btn.ACTION, b -> {
            String cur = Playback.current == null ? null : Playback.current.id;
            SongLibrary.reload();
            Playback.current = SongLibrary.find(cur);
        }));

        // transport: previous / play-pause / next
        int total = colW * 2 + 8;
        int bw = (total - 8) / 3;
        add(new Btn(left, cy + step, bw, h, "<<  Prev", Btn.ACTION, b -> Transport.prev()));
        add(new Btn(left + bw + 4, cy + step, bw, h, Playback.isPlaying() ? "Pause" : "Play", Btn.ACTION, b -> {
            Transport.toggle();
            b.setMessage(Component.literal(Playback.isPlaying() ? "Pause" : "Play"));
        }));
        add(new Btn(left + (bw + 4) * 2, cy + step, bw, h, "Next  >>", Btn.ACTION, b -> Transport.next()));

        timeSlider = add(new TimeSlider(left, cy + step * 2, total, h));

        add(toggle(left, cy + step * 3, colW, "Island", () -> s.island, v -> s.island = v));
        add(toggle(right, cy + step * 3, colW, "Island scroll", () -> s.islandScroll, v -> s.islandScroll = v));
        add(toggle(left, cy + step * 4, colW, "Island lyrics", () -> s.islandLyrics, v -> s.islandLyrics = v));
        add(toggle(right, cy + step * 4, colW, "Console line", () -> s.consoleLine, v -> s.consoleLine = v));
        add(toggle(left, cy + step * 5, colW, "Side preview", () -> s.sidePreview, v -> s.sidePreview = v));
        add(toggle(right, cy + step * 5, colW, "Auto lyrics", () -> s.autoLyrics, v -> s.autoLyrics = v));
    }

    private void initMore(Settings s, int cy, int step, int h) {
        int na = Theme.UI_ACCENT_NAMES.length, nb = Theme.UI_BG_NAMES.length;
        add(cycle(left, cy, colW, "UI accent", () -> Theme.UI_ACCENT_NAMES[s.uiAccent % na], () -> s.uiAccent = (s.uiAccent + 1) % na));
        add(cycle(right, cy, colW, "UI background", () -> Theme.UI_BG_NAMES[s.uiBg % nb], () -> s.uiBg = (s.uiBg + 1) % nb));
        add(new FSlider(left, cy + step, colW, h, "Island opacity", 0.2f, 1f, s.islandOpacity, 2, v -> s.islandOpacity = v));
        add(new FSlider(right, cy + step, colW, h, "Island height", 0f, 30f, s.islandY, 0, v -> s.islandY = Math.round(v)));
        add(new FSlider(left, cy + step * 2, colW, h, "Side text size", 0.8f, 3f, s.sideScale, 2, v -> s.sideScale = v));
        add(new FSlider(right, cy + step * 2, colW, h, "Words / piece", 1f, 6f, s.wordsPerChunk, 0, v -> s.wordsPerChunk = Math.round(v)));
        add(new Btn(left, cy + step * 3, colW, h, "Rebuild songs", Btn.ACTION, b -> {
            int n = SongLibrary.rechunkAll(s.wordsPerChunk);
            ClientEvents.toast("Rebuilt " + n + " songs (" + s.wordsPerChunk + " word(s) per piece)");
        }));
        add(toggle(right, cy + step * 3, colW, "Unsynced fallback", () -> s.allowUnsynced, v -> s.allowUnsynced = v));
        String[] names = LyricsSources.names();
        for (int i = 0; i < names.length && i < 5; i++) {
            final int k = i;
            add(toggle(i % 2 == 0 ? left : right, cy + step * (4 + i / 2), colW, names[i], () -> s.siteOn(k), v -> s.setSite(k, v)));
        }
        add(toggle(right, cy + step * 6, colW, "Hotkey messages", () -> s.toasts, v -> s.toasts = v));
    }

    @Override
    public void tick() {
        super.tick();
        if (timeSlider != null) timeSlider.sync();
        if (closing && (System.nanoTime() - closeNs) / 1e9f >= CLOSE_TIME && minecraft != null) {
            closing = false;
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (closing) return true;
        if (getFocused() instanceof EditBox eb && eb.isFocused()) return super.keyPressed(key, scan, mods);
        if (ClientEvents.OPEN.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private FSlider colorSlider(int x, int y, String label, int shift, boolean text) {
        Settings s = Settings.I;
        int cur = ((text ? s.textColor : s.glowColor) >> shift) & 0xFF;
        FSlider f = new FSlider(x, y, colW, 20, label, 0f, 255f, cur, 0, v -> {
            int c = Math.round(v) & 0xFF;
            int mask = ~(0xFF << shift);
            if (text) s.textColor = (s.textColor & mask) | (c << shift);
            else s.glowColor = (s.glowColor & mask) | (c << shift);
            EditBox hb = text ? hexText : hexGlow;
            if (hb != null && !hb.isFocused()) hb.setValue(Theme.hex(text ? s.textColor : s.glowColor));
        });
        f.tint = shift == 16 ? 0xFF6B6B : (shift == 8 ? 0x5BE38A : 0x6BA3FF);   // R / G / B tinted tracks
        return f;
    }

    private Btn toggle(int x, int y, int w, String name, Supplier<Boolean> get, Consumer<Boolean> set) {
        Btn b = new Btn(x, y, w, 20, name, Btn.TOGGLE, bb -> set.accept(!get.get()));
        b.on = get;
        return b;
    }

    private Btn cycle(int x, int y, int w, String name, Supplier<String> get, Runnable next) {
        Btn b = new Btn(x, y, w, 20, name, Btn.CYCLE, bb -> next.run());
        b.val = get;
        return b;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNs) / 1e9f);
        lastNs = now;
        Settings st = Settings.I;
        int glow = Theme.accent();
        double secs = now / 1e9;

        float openP = (now - openNs) / 1e9f / OPEN_TIME;
        float shut = closing ? Gfx.easeInCubic((now - closeNs) / 1e9f / CLOSE_TIME) : 0f;
        float pa = Gfx.easeOutCubic(openP) * (1f - shut);
        uiAlpha = pa;
        uiAge = (now - tabNs) / 1e9f;
        uiGlow = glow;

        // soft dim of the world behind the menu (fades together with the panel)
        g.fillGradient(0, 0, width, height, Gfx.argb(0x000000, 0.42f * pa), Gfx.argb(0x050210, 0.62f * pa));
        if (pa < 0.01f) return;

        // gentle slide down with a tiny overshoot instead of the old hard bounce
        float yo = -(1f - Gfx.easeOutBack(openP)) * 28f + shut * 14f;
        g.pose().pushPose();
        g.pose().translate(0f, yo, 0f);

        int x1 = width / 2 - PANEL_HALF, x2 = width / 2 + PANEL_HALF;
        int y1 = panelTop;
        shownBottom = Float.isNaN(shownBottom) ? bottom : Gfx.damp(shownBottom, bottom, 14f, dt);   // panel height glides between tabs
        int pw = x2 - x1, ph = Math.round(shownBottom) - y1;
        float pulse = 0.5f + 0.5f * (float) Math.sin(secs * 2.2);
        Gfx.fillRoundFast(g, x1 - 4, y1 - 4, pw + 8, ph + 8, 14, Gfx.argb(glow, 0.045f * pa));
        Gfx.fillRoundFast(g, x1 - 2, y1 - 2, pw + 4, ph + 4, 12, Gfx.argb(glow, (0.06f + 0.05f * pulse) * pa));
        Gfx.fillRound(g, x1 - 1, y1 - 1, pw + 2, ph + 2, 11, Gfx.argb(glow, (0.40f + 0.30f * pulse) * pa));
        Gfx.fillRound(g, x1, y1, pw, ph, 10, Gfx.argb(Theme.bg(), 0.96f * pa));

        // glowing title
        int tx = x1 + 12;
        int ty = y1 + 9;
        int gl = Gfx.argb(glow, 0.30f * pa);
        if (pa > 0.05f) {
            g.drawString(font, title, tx - 1, ty, gl, false);
            g.drawString(font, title, tx + 1, ty, gl, false);
            g.drawString(font, title, tx, ty - 1, gl, false);
            g.drawString(font, title, tx, ty + 1, gl, false);
            g.drawString(font, title, tx, ty, Gfx.argb(0xFFFFFF, pa), false);
        }

        // live effect preview (menu only)
        EffectPreview.render(g, font, x1 + 12, y1 + 22, pw - 24, 30, pa, Math.round(yo));

        // tabs + Done (do not slide with the content)
        for (AbstractWidget w : header) w.render(g, mx, my, pt);
        int tw = tabW();
        float targetX = left + tab * (tw + 4);
        indX = Float.isNaN(indX) ? targetX : Gfx.damp(indX, targetX, 16f, dt);       // the underline glides to the new tab
        g.fill(left, tabsY + 24, left + colW * 2 + 8, tabsY + 25, Gfx.argb(0xFFFFFF, 0.07f * pa));
        int iw = tw - 14;
        Gfx.fillRound(g, Math.round(indX) + 7, tabsY + 23, iw, 2, 1, Gfx.argb(glow, pa));

        // content (each widget fades / rises in with a small delay, see Btn / Sld)
        super.render(g, mx, my, pt);
        float ha = Gfx.easeOutCubic((uiAge - 0.25f) / 0.3f) * pa;
        if (tab == 1) {
            Gfx.text(g, font, "Text hex", left + 6, contentTop + 22 * 4 + 6, 0xB4B0CF, ha);
            Gfx.text(g, font, "Glow hex", right + 6, contentTop + 22 * 4 + 6, 0xB4B0CF, ha);
        } else if (tab == 3) {
            Gfx.textCentered(g, font, "Page " + (fontPage + 1) + "/" + fontPageCount + "  (" + fontTotal + " fonts)",
                width / 2, contentTop + 22 * 6 + 6, 0x9A8CC0, ha);
        } else if (tab == 4) {
            Gfx.textCentered(g, font, "Keys: Controls > Player (menu, next, previous, pause)", width / 2, contentTop + 132, 0x9A8CC0, ha);
        } else if (tab == 5) {
            drawNow(g, now, dt, mx, my, glow, pa);
        }

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
        if (closing) return false;
        if (tab == 5 && button == 0) {
            int hit = pillAt(mx, my);
            if (hit >= 0) {
                cardPress[hit] = 1f;
                if (hit == 0) Transport.prev();
                else if (hit == 1) Transport.toggle();
                else Transport.next();
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

    private void drawNow(GuiGraphics g, long now, float dt, int mx, int my, int glow, float pa) {
        float since = (now - tabNs) / 1e9f;
        double secs = now / 1e9;
        boolean remote = Transport.isRemote();
        String app = SystemBridge.app.isEmpty() ? "your player" : SystemBridge.app;
        Song prev = Transport.peek(-1);
        Song cur = Playback.current;
        Song next = Transport.peek(1);
        int hover = pillAt(mx, my);

        // song changed -> the cards slide in from the side the new song came from
        String curId = cur == null ? "" : cur.id;
        if (lastCurId == null) {
            lastCurId = curId;
        } else if (!curId.equals(lastCurId)) {
            changeDir = curId.equals(lastNextId) ? 1 : (curId.equals(lastPrevId) ? -1 : 0);
            changeNs = now;
            lastCurId = curId;
        }
        lastPrevId = prev == null ? "" : prev.id;
        lastNextId = next == null ? "" : next.id;
        float cp = Gfx.easeOutCubic((now - changeNs) / 1e9f / 0.42f);
        float slide = changeDir * 34f * (1f - cp);
        float textA = 0.25f + 0.75f * cp;

        for (int i = 0; i < 3; i++) {
            cardHov[i] = Gfx.damp(cardHov[i], hover == i ? 1f : 0f, 16f, dt);
            cardPress[i] = Math.max(0f, cardPress[i] - dt * 4.5f);
        }

        for (int i = 0; i < 3; i++) {
            int[] r = pill(i);
            int order = i == 1 ? 0 : (i == 0 ? 1 : 2);          // now playing first, then previous, then next
            float p = (since - 0.07f * order) / 0.45f;
            float e = Gfx.easeOutCubic(p);
            float a = e * pa;
            if (a < 0.03f) continue;
            int dy = Math.round((1f - e) * 14f);                 // rises gently into place
            int sx = Math.round(slide * (i == 1 ? 1f : 0.6f));
            int gr = Math.round(cardHov[i] * 2f - cardPress[i] * 2f);
            int x = r[0] + sx - gr, y = r[1] + dy - gr, w = r[2] + gr * 2, h = r[3] + gr * 2;
            if (i == 0) {
                drawPill(g, x, y, w, h, "PREVIOUS", prev,
                    remote ? "No history yet" : "Nothing here", false, cardHov[i], a, textA, glow, secs);
            } else if (i == 1) {
                drawPill(g, x, y, w, h, Playback.isPlaying() ? "NOW PLAYING" : "PAUSED", cur,
                    "Nothing playing", true, cardHov[i], a, textA, glow, secs);
            } else {
                drawPill(g, x, y, w, h, "NEXT", next,
                    remote ? "Chosen by " + app : "Nothing here", false, cardHov[i], a, textA, glow, secs);
            }
        }
        float ha = Gfx.easeOutCubic((since - 0.3f) / 0.3f) * pa;
        int hy = contentTop + 114;
        Gfx.textCentered(g, font, remote ? "Click Previous / Next to switch the track in " + app
            : "Click Previous / Next to jump to that song", width / 2, hy, 0x9A8CC0, ha);
        Gfx.textCentered(g, font, "Shuffle, repeat and the time bar are on the island", width / 2, hy + 10, 0x9A8CC0, ha);
    }

    private void drawPill(GuiGraphics g, int x, int y, int w, int h, String label, Song song, String empty,
                          boolean big, float hover, float a, float ta, int glow, double secs) {
        int r = h / 2;
        float pulse = big ? (float) (Math.sin(secs * 2.4) * 0.5 + 0.5) : 0f;
        float halo = big ? 0.12f + 0.12f * pulse : 0.04f + 0.18f * hover;
        Gfx.fillRoundFast(g, x - 2, y - 2, w + 4, h + 4, r + 2, Gfx.argb(glow, halo * a));
        Gfx.fillRound(g, x, y, w, h, r, Gfx.argb(Theme.bg2(), 0.95f * a));
        if (hover > 0.02f) Gfx.fillRound(g, x, y, w, h, r, Gfx.argb(glow, 0.12f * hover * a));
        if (big) Gfx.outlineRound(g, x, y, w, h, r, Gfx.argb(glow, 0.45f * a));
        else if (hover > 0.03f) Gfx.outlineRound(g, x, y, w, h, r, Gfx.argb(glow, 0.55f * hover * a));

        // round "cover" with the first letter
        int dr = r - 4;
        int dcx = x + r;
        int dcy = y + h / 2;
        Gfx.fillCircle(g, dcx, dcy, dr, Gfx.argb(glow, (big ? 0.85f : 0.40f + 0.25f * hover) * a));
        Gfx.textCentered(g, font, song == null ? "-" : initial(song), dcx, dcy - 4, 0xFFFFFF, a);

        int tx = x + h + 2;
        int maxW = x + w - r - 6 - tx;
        float la = 0.9f * a;
        if (la >= 0.03f) {
            g.pose().pushPose();
            g.pose().translate(tx, y + (big ? 6 : 4), 0f);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.drawString(font, label, 0, 0, Gfx.argb(big ? glow : 0xB8C0FF, la), false);
            g.pose().popPose();
        }

        float sa = a * ta;
        if (song == null) {
            Gfx.text(g, font, clip(empty, maxW), tx, y + (big ? 17 : 13), 0x9A8CC0, sa);
            return;
        }
        String title = clip(nameOf(song), maxW);
        int ty = y + (big ? 15 : 12);
        Gfx.text(g, font, title, tx, ty, 0xFFFFFF, sa);
        String artist = song.artist == null ? "" : song.artist;
        if (!artist.isBlank()) {
            if (big) {
                Gfx.text(g, font, clip(artist, maxW), tx, ty + 10, 0xB8C0FF, 0.85f * sa);
            } else {
                int used = font.width(title) + 8;
                if (maxW - used > 24) Gfx.text(g, font, clip(artist, maxW - used), tx + used, ty, 0xB8C0FF, 0.7f * sa);
            }
        }
        if (big) {
            // time + bar
            double len = Transport.length();
            double pos = Playback.position();
            float frac = len > 0 ? (float) Math.min(1.0, Math.max(0.0, pos / len)) : 0f;
            String time = TimeSlider.fmt(pos) + " / " + (len > 0 ? TimeSlider.fmt(len) : "--:--");
            float ta2 = 0.9f * a;
            if (ta2 >= 0.03f) {
                g.pose().pushPose();
                g.pose().translate(x + w - r - 6 - font.width(time) * 0.75f, y + 6, 0f);
                g.pose().scale(0.75f, 0.75f, 1f);
                g.drawString(font, time, 0, 0, Gfx.argb(0xB8C0FF, ta2), false);
                g.pose().popPose();
            }
            int bw = Math.max(10, maxW);
            Gfx.fillRound(g, tx, y + h - 9, bw, 3, 1, Gfx.argb(0xFFFFFF, 0.22f * a));
            Gfx.fillRound(g, tx, y + h - 9, Math.max(3, Math.round(bw * frac)), 3, 1, Gfx.argb(glow, a));
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

    /** Closing plays a short fade; the screen is really closed from tick(). */
    @Override
    public void onClose() {
        if (closing) return;
        closing = true;
        closeNs = System.nanoTime();
    }

    // ------------------------------------------------------------------ widgets

    private static float entry(int order) {
        return order < 0 ? 1f : Gfx.easeOutCubic((uiAge - order * 0.03f) / 0.30f);
    }

    /** Soft rounded field behind a widget; the accent outline fades in on hover. */
    private static void field(GuiGraphics g, int x, int y, int w, int h, float hover, float a, float extra, int accent) {
        Gfx.fillRoundFast(g, x, y, w, h, 4, Gfx.argb(0xFFFFFF, (0.055f + 0.07f * hover + extra) * a));
        if (hover > 0.03f) Gfx.outlineRound(g, x, y, w, h, 4, Gfx.argb(accent, 0.70f * hover * a));
    }

    private static final class Btn extends Button {
        static final int ACTION = 0, TOGGLE = 1, CYCLE = 2, TAB = 3, OPTION = 4;

        final int kind;
        final String label;
        Supplier<Boolean> on = () -> false;
        Supplier<String> val = () -> "";
        boolean selected;
        String fontId = "";
        int order = -1;
        float hover, press, knob = -1f;
        long lastNs = System.nanoTime();

        Btn(int x, int y, int w, int h, String label, int kind, OnPress onPress) {
            super(x, y, w, h, Component.literal(label), onPress, DEFAULT_NARRATION);
            this.kind = kind;
            this.label = label;
        }

        @Override
        public void onPress() {
            press = 1f;
            super.onPress();
        }

        @Override
        public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            long now = System.nanoTime();
            float dt = Math.min(0.1f, (now - lastNs) / 1e9f);
            lastNs = now;
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            boolean over = active && mx >= x && mx < x + w && my >= y && my < y + h;
            hover = Gfx.damp(hover, over ? 1f : 0f, 18f, dt);
            press = Math.max(0f, press - dt * 5f);
            boolean isOn = kind == TOGGLE && on.get();
            if (knob < 0f) knob = isOn ? 1f : 0f;
            knob = Gfx.damp(knob, isOn ? 1f : 0f, 16f, dt);

            float e = entry(order);
            float a = e * uiAlpha;
            if (a < 0.03f) return;
            Font font = Minecraft.getInstance().font;
            int glow = uiGlow;
            int soft = Gfx.lerpRgb(glow, 0xFFFFFF, 0.55f);

            g.pose().pushPose();
            g.pose().translate(0f, (1f - e) * 6f, 0f);
            int sh = Math.round(press * 1.5f);                  // a quick "squeeze" when pressed
            int bx = x + sh, by = y + sh, bw = w - sh * 2, bh = h - sh * 2;
            int ty = by + (bh - 8) / 2;

            switch (kind) {
                case TOGGLE -> {
                    field(g, bx, by, bw, bh, hover, a, 0f, glow);
                    Gfx.text(g, font, label, bx + 8, ty, Gfx.lerpRgb(0xC9C5E0, 0xFFFFFF, hover), a);
                    int tw = 24, th = 12;
                    int sx = bx + bw - 8 - tw, sy = by + (bh - th) / 2;
                    int track = Gfx.lerpArgb(Gfx.argb(0x3A3555, a), Gfx.argb(glow, a), knob);
                    Gfx.fillRound(g, sx, sy, tw, th, th / 2, track);
                    int kx = sx + 6 + Math.round(knob * 12f);
                    Gfx.fillCircle(g, kx, sy + th / 2, 4, Gfx.argb(Gfx.lerpRgb(0xB9B5D2, 0xFFFFFF, knob), a));
                }
                case CYCLE -> {
                    field(g, bx, by, bw, bh, hover, a, 0f, glow);
                    Gfx.text(g, font, label, bx + 8, ty, 0xB4B0CF, a);
                    String v = val.get();
                    int maxV = bw - 16 - font.width(label) - 8;
                    if (font.width(v) > maxV) v = font.plainSubstrByWidth(v, Math.max(0, maxV - font.width("..."))) + "...";
                    Gfx.text(g, font, v, bx + bw - 8 - font.width(v), ty, Gfx.lerpRgb(soft, 0xFFFFFF, hover), a);
                }
                case TAB -> {
                    float bg = 0.07f * hover + (selected ? 0.08f : 0f);
                    if (bg > 0.005f) Gfx.fillRoundFast(g, bx, by, bw, bh, 4, Gfx.argb(0xFFFFFF, bg * a));
                    String m = getMessage().getString();
                    int col = Gfx.lerpRgb(0x8F8BB0, 0xFFFFFF, Math.max(selected ? 1f : 0f, hover * 0.85f));
                    Gfx.text(g, font, m, bx + (bw - font.width(m)) / 2, ty, col, a);
                }
                case OPTION -> {
                    field(g, bx, by, bw, bh, hover, a, selected ? 0.05f : 0f, glow);
                    if (selected) Gfx.outlineRound(g, bx, by, bw, bh, 4, Gfx.argb(glow, 0.55f * a));
                    int col = selected ? soft : Gfx.lerpRgb(0xD6D2EE, 0xFFFFFF, hover);
                    if (fontId.isEmpty()) {
                        Gfx.text(g, font, label, bx + 8, ty, col, a);
                    } else if (a >= 0.03f) {
                        g.drawString(font, Song.styledComponent(label, fontId), bx + 8, ty, Gfx.argb(col, a), false);
                    }
                    if (selected) Gfx.fillCircle(g, bx + bw - 12, by + bh / 2, 3, Gfx.argb(glow, a));
                }
                default -> {
                    field(g, bx, by, bw, bh, hover, a, 0f, glow);
                    String m = getMessage().getString();
                    Gfx.text(g, font, m, bx + (bw - font.width(m)) / 2, ty, Gfx.lerpRgb(0xD6D2EE, 0xFFFFFF, hover), a);
                }
            }
            g.pose().popPose();
        }
    }

    /** Slider: label left, value right, thin rounded track with a knob that grows under the mouse. */
    private abstract static class Sld extends AbstractSliderButton {
        int order = -1;
        int tint = -1;
        float hover, knob;
        boolean drag;
        long lastNs = System.nanoTime();

        Sld(int x, int y, int w, int h, double value) {
            super(x, y, w, h, Component.empty(), value);
        }

        abstract String name();

        abstract String valueText();

        @Override
        protected void updateMessage() {
        }

        private void setFrom(double mx) {
            double v = Mth.clamp((mx - (getX() + 8)) / (double) (getWidth() - 16), 0.0, 1.0);
            if (v != this.value) {
                this.value = v;
                applyValue();
            }
        }

        @Override
        public void onClick(double mx, double my) {
            drag = true;
            setFrom(mx);
        }

        @Override
        protected void onDrag(double mx, double my, double dx, double dy) {
            setFrom(mx);
        }

        @Override
        public void onRelease(double mx, double my) {
            drag = false;
            super.onRelease(mx, my);
        }

        @Override
        public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            long now = System.nanoTime();
            float dt = Math.min(0.1f, (now - lastNs) / 1e9f);
            lastNs = now;
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            boolean over = active && mx >= x && mx < x + w && my >= y && my < y + h;
            hover = Gfx.damp(hover, over ? 1f : 0f, 18f, dt);
            knob = Gfx.damp(knob, (over || drag) ? 1f : 0f, 20f, dt);

            float e = entry(order);
            float a = e * uiAlpha;
            if (a < 0.03f) return;
            Font font = Minecraft.getInstance().font;
            int glow = uiGlow;
            int fillRgb = tint >= 0 ? tint : glow;

            g.pose().pushPose();
            g.pose().translate(0f, (1f - e) * 6f, 0f);
            field(g, x, y, w, h, hover, a, 0f, glow);
            Gfx.text(g, font, name(), x + 8, y + 3, Gfx.lerpRgb(0xB4B0CF, 0xFFFFFF, hover), a);
            String v = valueText();
            Gfx.text(g, font, v, x + w - 8 - font.width(v), y + 3, 0xFFFFFF, a);

            int tx0 = x + 8, tw = w - 16, ty = y + h - 7;
            int fw = Math.round(tw * (float) this.value);
            Gfx.fillRound(g, tx0, ty, tw, 4, 2, Gfx.argb(0xFFFFFF, 0.12f * a));
            if (fw > 0) Gfx.fillRound(g, tx0, ty, Math.max(4, fw), 4, 2, Gfx.argb(fillRgb, a));
            int kr = Math.round(knob * 4f);
            if (kr > 0) {
                Gfx.fillCircle(g, tx0 + fw, ty + 2, kr + 1, Gfx.argb(fillRgb, 0.35f * a));
                Gfx.fillCircle(g, tx0 + fw, ty + 2, kr, Gfx.argb(0xFFFFFF, a));
            }
            g.pose().popPose();
        }
    }

    /** Position slider: drag to jump; the real player is told when you let go. */
    private static final class TimeSlider extends Sld {
        TimeSlider(int x, int y, int w, int h) {
            super(x, y, w, h, 0.0);
            sync();
        }

        static String fmt(double sec) {
            int t = (int) Math.max(0, sec);
            return String.format(Locale.ROOT, "%d:%02d", t / 60, t % 60);
        }

        void sync() {
            if (drag) return;
            double len = Transport.length();
            this.value = len <= 0 ? 0.0 : Math.min(1.0, Math.max(0.0, Playback.position() / len));
        }

        @Override
        String name() {
            return "Time";
        }

        @Override
        String valueText() {
            double len = Transport.length();
            return fmt(this.value * len) + " / " + (len > 0 ? fmt(len) : "--:--");
        }

        @Override
        protected void applyValue() {
            double len = Transport.length();
            if (len > 0) Transport.seekLocal(this.value * len);
        }

        @Override
        public void onRelease(double mx, double my) {
            super.onRelease(mx, my);
            double len = Transport.length();
            if (len > 0) Transport.seekRemote(this.value * len);
        }
    }

    private static final class FSlider extends Sld {
        private final String name;
        private final float min, max;
        private final int decimals;
        private final Consumer<Float> set;

        FSlider(int x, int y, int w, int h, String name, float min, float max, float val, int decimals, Consumer<Float> set) {
            super(x, y, w, h, (val - min) / (max - min));
            this.name = name;
            this.min = min;
            this.max = max;
            this.decimals = decimals;
            this.set = set;
        }

        private float get() {
            return min + (float) value * (max - min);
        }

        @Override
        String name() {
            return name;
        }

        @Override
        String valueText() {
            return String.format(Locale.ROOT, "%." + decimals + "f", get());
        }

        @Override
        protected void applyValue() {
            set.accept(get());
        }
    }
}
