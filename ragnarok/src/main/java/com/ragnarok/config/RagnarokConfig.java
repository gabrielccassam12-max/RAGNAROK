package com.ragnarok.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** config/ragnarok.json, created on first launch. */
public class RagnarokConfig {
    public static RagnarokConfig INSTANCE = new RagnarokConfig();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public int maxEssence = 200;
    /** Essence regained every 5 ticks while not using abilities. */
    public int regenPer5Ticks = 2;
    public int regenDelayTicks = 60;
    public double damageMultiplier = 1.0;
    public double costMultiplier = 1.0;
    public double cooldownMultiplier = 1.0;
    public double rangeMultiplier = 1.0;
    /** Craters and dug terrain (restored automatically). Turn off to protect builds. */
    public boolean allowBlockDestruction = true;
    public int maxCraterRadius = 6;
    public int blockLifetimeTicks = 700;
    public int maxTempBlocks = 5000;
    /** Duration of a transformation (ticks) and its Essence cost. */
    public int transformTicks = 1200;
    public int transformCost = 50;
    /** Heat added per Essence spent; at overloadHeat the fighter overloads for a while. */
    public double heatPerEssence = 0.6;
    public int overloadHeat = 120;
    public int overloadTicks = 100;
    /** If false, abilities never hurt other players. */
    public boolean allowPvp = true;
    /** The Core items are needed to unlock an element. */
    public boolean requireCore = true;

    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("ragnarok.json");
        try {
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file)) {
                    RagnarokConfig c = GSON.fromJson(r, RagnarokConfig.class);
                    if (c != null) INSTANCE = c;
                }
            }
            try (Writer w = Files.newBufferedWriter(file)) {
                GSON.toJson(INSTANCE, w);
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("[ragnarok] could not read config, using defaults: " + e);
            INSTANCE = new RagnarokConfig();
        }
    }
}
