package com.evolyrics;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public final class ClientEvents {
    public static final KeyMapping OPEN = new KeyMapping("key.stolas.menu", InputConstants.KEY_RSHIFT, "key.categories.stolas");
    public static final KeyMapping NEXT = new KeyMapping("key.stolas.next", InputConstants.UNKNOWN.getValue(), "key.categories.stolas");
    public static final KeyMapping PREV = new KeyMapping("key.stolas.prev", InputConstants.UNKNOWN.getValue(), "key.categories.stolas");
    public static final KeyMapping TOGGLE = new KeyMapping("key.stolas.toggle", InputConstants.UNKNOWN.getValue(), "key.categories.stolas");
    public static final KeyMapping LYRICS = new KeyMapping("key.stolas.lyrics", InputConstants.UNKNOWN.getValue(), "key.categories.stolas");
    public static final KeyMapping SYNC_UP = new KeyMapping("key.stolas.sync_up", InputConstants.UNKNOWN.getValue(), "key.categories.stolas");
    public static final KeyMapping SYNC_DOWN = new KeyMapping("key.stolas.sync_down", InputConstants.UNKNOWN.getValue(), "key.categories.stolas");
    public static final KeyMapping THEME = new KeyMapping("key.stolas.theme", InputConstants.UNKNOWN.getValue(), "key.categories.stolas");
    public static final KeyMapping REFETCH = new KeyMapping("key.stolas.refetch", InputConstants.UNKNOWN.getValue(), "key.categories.stolas");

    /** Short message above the hotbar (hotkeys / commands). */
    public static void toast(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (Settings.I.toasts && mc.player != null) mc.player.displayClientMessage(Component.literal(msg), true);
    }

    private static void nudgeOffset(float d) {
        Settings s = Settings.I;
        s.syncOffset = Math.round(Math.max(-3f, Math.min(3f, s.syncOffset + d)) * 100f) / 100f;
        Settings.save();
        toast(String.format(java.util.Locale.ROOT, "Lyrics sync offset: %+.2f s", s.syncOffset));
    }

    @Mod.EventBusSubscriber(modid = EvoLyrics.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent e) {
            e.register(OPEN);
            e.register(NEXT);
            e.register(PREV);
            e.register(TOGGLE);
            e.register(LYRICS);
            e.register(SYNC_UP);
            e.register(SYNC_DOWN);
            e.register(THEME);
            e.register(REFETCH);
        }

        @SubscribeEvent
        public static void overlays(RegisterGuiOverlaysEvent e) {
            e.registerAboveAll("island", Island::render);
            e.registerAboveAll("preview", SidePreview::render);
            e.registerAboveAll("bridge_status", StatusHud::render);
        }

        @SubscribeEvent
        public static void setup(FMLClientSetupEvent e) {
            Settings.load();
            SongLibrary.reload();
            Song s = SongLibrary.find(Settings.I.lastSong);
            if (s != null) Playback.current = s;
        }
    }

    @Mod.EventBusSubscriber(modid = EvoLyrics.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ForgeBus {
        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            while (OPEN.consumeClick()) {
                if (mc.screen == null) mc.setScreen(new SettingsScreen(null));
            }
            while (NEXT.consumeClick()) Transport.next();
            while (PREV.consumeClick()) Transport.prev();
            while (TOGGLE.consumeClick()) Transport.toggle();
            while (LYRICS.consumeClick()) {
                Settings.I.enabled = !Settings.I.enabled;
                Settings.save();
                toast("Lyrics: " + (Settings.I.enabled ? "ON" : "OFF"));
            }
            while (SYNC_UP.consumeClick()) nudgeOffset(0.1f);
            while (SYNC_DOWN.consumeClick()) nudgeOffset(-0.1f);
            while (THEME.consumeClick()) {
                Theme.apply(Settings.I, Theme.nextIndex(Settings.I));
                Settings.save();
                toast("Theme: " + Theme.currentName(Settings.I));
            }
            while (REFETCH.consumeClick()) {
                SystemBridge.refetchCurrent();
                toast("Searching lyrics...");
            }
            SystemBridge.tick();
            Playback.tick();
            Transport.tick();
        }

        /** The island grows into a clickable player on top of every menu. */
        @SubscribeEvent
        public static void screenRender(ScreenEvent.Render.Post e) {
            Island.renderOverScreen(e.getGuiGraphics(), e.getScreen(), e.getMouseX(), e.getMouseY());
        }

        @SubscribeEvent
        public static void screenClick(ScreenEvent.MouseButtonPressed.Pre e) {
            if (Island.click(e.getMouseX(), e.getMouseY(), e.getButton())) e.setCanceled(true);
        }

        @SubscribeEvent
        public static void render(RenderLevelStageEvent e) {
            if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                LyricsRenderer.render(e.getPoseStack(), e.getCamera(), e.getPartialTick());
            }
        }

        @SubscribeEvent
        public static void commands(RegisterClientCommandsEvent e) {
            CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
            d.register(root("stolaslyrics"));
        }

        private static LiteralArgumentBuilder<CommandSourceStack> root(String name) {
            return Commands.literal(name)
                .then(Commands.literal("list").executes(c -> {
                    say(c, "Songs: " + String.join(", ", SongLibrary.songs.keySet()));
                    return 1;
                }))
                .then(Commands.literal("reload").executes(c -> {
                    SongLibrary.reload();
                    say(c, "Loaded songs: " + SongLibrary.songs.size());
                    return 1;
                }))
                .then(Commands.literal("play").then(Commands.argument("song", StringArgumentType.greedyString())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(SongLibrary.songs.keySet(), b))
                    .executes(c -> {
                        Song s = SongLibrary.find(StringArgumentType.getString(c, "song"));
                        if (s == null) {
                            say(c, "Song not found");
                            return 0;
                        }
                        Settings.I.lastSong = s.id;
                        Settings.save();
                        Playback.play(s);
                        say(c, "Playing: " + s.id);
                        return 1;
                    })))
                .then(Commands.literal("pause").executes(c -> {
                    Playback.pause();
                    return 1;
                }))
                .then(Commands.literal("resume").executes(c -> {
                    Playback.resume();
                    return 1;
                }))
                .then(Commands.literal("stop").executes(c -> {
                    Playback.stop();
                    return 1;
                }))
                .then(Commands.literal("seek").then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0))
                    .executes(c -> {
                        Playback.seek(DoubleArgumentType.getDouble(c, "seconds"));
                        return 1;
                    })))
                .then(Commands.literal("fetch").executes(c -> {
                    SystemBridge.refetchCurrent();
                    say(c, "Searching lyrics for the current song...");
                    return 1;
                }))
                .then(Commands.literal("search").then(Commands.argument("song", StringArgumentType.greedyString())
                    .executes(c -> {
                        SystemBridge.searchAndPlay(StringArgumentType.getString(c, "song"));
                        say(c, "Searching (format: Artist - Title)...");
                        return 1;
                    })))
                .then(Commands.literal("offset")
                    .executes(c -> {
                        say(c, String.format(java.util.Locale.ROOT, "Sync offset: %.2f s", Settings.I.syncOffset));
                        return 1;
                    })
                    .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(-3, 3)).executes(c -> {
                        Settings.I.syncOffset = (float) DoubleArgumentType.getDouble(c, "seconds");
                        Settings.save();
                        say(c, String.format(java.util.Locale.ROOT, "Sync offset: %.2f s", Settings.I.syncOffset));
                        return 1;
                    })))
                .then(Commands.literal("theme")
                    .executes(c -> {
                        StringBuilder b = new StringBuilder("Current: " + Theme.currentName(Settings.I) + ". Themes: ");
                        for (int i = 0; i < Theme.PRESETS.length; i++) b.append(i == 0 ? "" : ", ").append(Theme.PRESETS[i].name);
                        say(c, b.toString());
                        return 1;
                    })
                    .then(Commands.argument("name", StringArgumentType.greedyString())
                        .suggests((c, b) -> {
                            for (Theme.Preset p : Theme.PRESETS) b.suggest(p.name);
                            return b.buildFuture();
                        })
                        .executes(c -> {
                            int i = Theme.indexOf(StringArgumentType.getString(c, "name"));
                            if (i < 0) {
                                say(c, "Unknown theme. Use /stolaslyrics theme to see the list");
                                return 0;
                            }
                            Theme.apply(Settings.I, i);
                            Settings.save();
                            say(c, "Theme: " + Theme.PRESETS[i].name);
                            return 1;
                        })))
                .then(Commands.literal("font").then(Commands.argument("name", StringArgumentType.greedyString())
                    .suggests((c, b) -> {
                        for (FontList.Entry fe : FontList.list()) b.suggest(fe.label);
                        return b.buildFuture();
                    })
                    .executes(c -> {
                        String want = StringArgumentType.getString(c, "name").trim();
                        for (FontList.Entry fe : FontList.list()) {
                            if (fe.label.equalsIgnoreCase(want) || fe.id.equalsIgnoreCase(want)) {
                                Settings.I.font = fe.id;
                                Settings.save();
                                say(c, "Font: " + fe.label);
                                return 1;
                            }
                        }
                        say(c, "Unknown font. Open the menu > Font tab to see the list");
                        return 0;
                    })))
                .then(Commands.literal("toggle").executes(c -> {
                    Settings.I.enabled = !Settings.I.enabled;
                    Settings.save();
                    say(c, "Lyrics: " + (Settings.I.enabled ? "ON" : "OFF"));
                    return 1;
                }))
                .then(Commands.literal("sources").executes(c -> {
                    String[] n = LyricsSources.names();
                    StringBuilder b = new StringBuilder("Lyrics sites: ");
                    for (int i = 0; i < n.length; i++) {
                        b.append(i == 0 ? "" : ", ").append(n[i]).append(Settings.I.siteOn(i) ? " (on)" : " (off)");
                    }
                    b.append(". Unsynced fallback: ").append(Settings.I.allowUnsynced ? "on" : "off");
                    say(c, b.toString());
                    return 1;
                }))
                .then(Commands.literal("rechunk").executes(c -> {
                    int n = SongLibrary.rechunkAll(Settings.I.wordsPerChunk);
                    say(c, "Rebuilt " + n + " songs with " + Settings.I.wordsPerChunk + " word(s) per piece");
                    return 1;
                }))
                .then(Commands.literal("info").executes(c -> {
                    Song s = Playback.current;
                    if (s == null) {
                        say(c, "No active song");
                        return 0;
                    }
                    say(c, String.format(java.util.Locale.ROOT, "%s | %d lines | %.1f s | %s | theme %s",
                        s.id, s.lyrics.size(), Playback.position(), Playback.isPlaying() ? "playing" : "paused",
                        Theme.currentName(Settings.I)));
                    return 1;
                }))
                .then(Commands.literal("reset").executes(c -> {
                    Settings.reset();
                    say(c, "Settings reset to defaults");
                    return 1;
                }))
                .then(Commands.literal("add").then(Commands.argument("effect", StringArgumentType.word())
                    .suggests((c, b) -> {
                        for (LyricEffect fx : LyricEffect.values()) b.suggest(fx.name().toLowerCase());
                        return b.buildFuture();
                    })
                    .then(Commands.argument("text", StringArgumentType.greedyString()).executes(c -> {
                        Song s = Playback.current;
                        if (s == null) {
                            say(c, "No active song");
                            return 0;
                        }
                        double t = Playback.position();
                        s.addLine(t, StringArgumentType.getString(c, "text"), StringArgumentType.getString(c, "effect"));
                        try {
                            SongLibrary.save(s);
                            say(c, String.format("Added at %.2fs", t));
                        } catch (Exception ex) {
                            say(c, "Save failed: " + ex.getMessage());
                        }
                        return 1;
                    }))));
        }

        private static void say(CommandContext<CommandSourceStack> c, String msg) {
            c.getSource().sendSuccess(() -> Component.literal(msg), false);
        }
    }
}
