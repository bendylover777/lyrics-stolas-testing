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

    @Mod.EventBusSubscriber(modid = EvoLyrics.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent e) {
            e.register(OPEN);
            e.register(NEXT);
            e.register(PREV);
            e.register(TOGGLE);
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
