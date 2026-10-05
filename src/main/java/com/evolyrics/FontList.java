package com.evolyrics;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Fonts the user can pick for lyrics: vanilla ones + every font json in assets/evolyrics/font/. */
public final class FontList {
    public static final class Entry {
        public final String id;
        public final String label;

        Entry(String id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    private FontList() {
    }

    public static List<Entry> list() {
        List<Entry> out = new ArrayList<>();
        out.add(new Entry("", "Minecraft (pixel)"));
        out.add(new Entry("minecraft:uniform", "Minecraft Unicode"));
        try {
            ResourceManager rm = Minecraft.getInstance().getResourceManager();
            Map<ResourceLocation, Resource> found = rm.listResources("font",
                rl -> rl.getNamespace().equals(EvoLyrics.MODID) && rl.getPath().endsWith(".json"));
            List<String> names = new ArrayList<>();
            for (ResourceLocation rl : found.keySet()) {
                String p = rl.getPath();
                names.add(p.substring(5, p.length() - 5));
            }
            Collections.sort(names);
            for (String n : names) {
                out.add(new Entry(EvoLyrics.MODID + ":" + n, pretty(n)));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static String pretty(String n) {
        StringBuilder b = new StringBuilder();
        boolean up = true;
        for (char c : n.toCharArray()) {
            if (c == '_' || c == '-' || c == '/') {
                b.append(' ');
                up = true;
            } else {
                b.append(up ? Character.toUpperCase(c) : c);
                up = false;
            }
        }
        return b.toString();
    }
}
