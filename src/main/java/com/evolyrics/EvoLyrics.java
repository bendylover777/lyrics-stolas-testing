package com.evolyrics;

import net.minecraftforge.fml.common.Mod;

@Mod(EvoLyrics.MODID)
public class EvoLyrics {
    public static final String MODID = "evolyrics";

    public EvoLyrics() {
        // Client-only mod. All logic lives in ClientEvents (Dist.CLIENT subscribers).
    }
}
