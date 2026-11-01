package com.ragnarok.particle;

import com.ragnarok.Ragnarok;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The mod's OWN particles (not vanilla ones): own textures, own behaviour, drawn bright and translucent.
 * Names here must match assets/ragnarok/particles/<name>.json.
 */
public final class ModParticles {
    private ModParticles() {}

    public static final Map<String, ParticleProfile> PROFILES = new LinkedHashMap<>();
    public static final Map<String, SimpleParticleType> TYPES = new LinkedHashMap<>();

    private static void def(String name, String tex, int rgb, int life, float size, float grow, float gravity,
                            float drag, float spin, float rise, boolean sizeFromVx, boolean glow) {
        PROFILES.put(name, new ParticleProfile(tex, rgb, life, size, grow, gravity, drag, spin, rise, sizeFromVx, glow));
    }

    static {
        //   name          texture  colour    life size  grow  grav  drag  spin  rise  sizeVx glow
        def("flame",      "flame", 0xFFFFFF, 16, 0.62f, 0.96f, 0.0f, 0.92f, 0.00f, 0.014f, false, true);
        def("ember",      "spark", 0xFF9628, 22, 0.17f, 0.97f, 0.3f, 0.94f, 0.10f, 0.000f, false, true);
        def("smoke",      "soft",  0x3C373C, 34, 0.55f, 1.03f, 0.0f, 0.90f, 0.02f, 0.010f, false, false);
        def("bolt",       "bolt",  0xC8E6FF, 5,  1.10f, 1.00f, 0.0f, 1.00f, 0.00f, 0.000f, false, true);
        def("spark_e",    "spark", 0x96DCFF, 8,  0.24f, 0.92f, 0.0f, 0.80f, 0.00f, 0.000f, false, true);
        def("shard",      "shard", 0xC8F0FF, 28, 0.32f, 0.99f, 0.5f, 0.96f, 0.25f, 0.000f, false, true);
        def("frost_mist", "soft",  0xBEEBFF, 24, 0.42f, 1.04f, 0.0f, 0.90f, 0.00f, 0.004f, false, true);
        def("void_orb",   "swirl", 0xFFFFFF, 22, 0.58f, 0.96f, 0.0f, 0.90f, 0.35f, 0.000f, false, true);
        def("void_spark", "spark", 0xBE50FF, 14, 0.22f, 0.95f, 0.0f, 0.85f, 0.00f, 0.000f, false, true);
        def("ring_fire",  "ring",  0xFF6E1E, 9,  0.50f, 1.28f, 0.0f, 1.00f, 0.00f, 0.000f, true, true);
        def("ring_bolt",  "ring",  0xAADCFF, 9,  0.50f, 1.28f, 0.0f, 1.00f, 0.00f, 0.000f, true, true);
        def("ring_ice",   "ring",  0xBEF0FF, 9,  0.50f, 1.28f, 0.0f, 1.00f, 0.00f, 0.000f, true, true);
        def("ring_void",  "ring",  0xAA3CFF, 9,  0.50f, 1.28f, 0.0f, 1.00f, 0.00f, 0.000f, true, true);
        def("flash_fire", "flash", 0xFFA03C, 6,  0.90f, 1.12f, 0.0f, 1.00f, 0.20f, 0.000f, true, true);
        def("flash_bolt", "flash", 0xE6F5FF, 6,  0.90f, 1.12f, 0.0f, 1.00f, 0.20f, 0.000f, true, true);
        def("flash_ice",  "flash", 0xD2F5FF, 6,  0.90f, 1.12f, 0.0f, 1.00f, 0.20f, 0.000f, true, true);
        def("flash_void", "flash", 0xC864FF, 6,  0.90f, 1.12f, 0.0f, 1.00f, 0.20f, 0.000f, true, true);
        def("glow_fire",  "soft",  0xFF5A14, 14, 0.36f, 0.96f, 0.0f, 0.92f, 0.00f, 0.010f, false, true);
        def("glow_bolt",  "soft",  0x8CC8FF, 14, 0.36f, 0.96f, 0.0f, 0.92f, 0.00f, 0.010f, false, true);
        def("glow_ice",   "soft",  0xAAE6FF, 14, 0.36f, 0.96f, 0.0f, 0.92f, 0.00f, 0.010f, false, true);
        def("glow_void",  "soft",  0x9632E6, 14, 0.36f, 0.96f, 0.0f, 0.92f, 0.00f, 0.010f, false, true);
    }

    public static SimpleParticleType get(String name) {
        return TYPES.get(name);
    }

    public static void init() {
        for (String name : PROFILES.keySet()) {
            SimpleParticleType t = FabricParticleTypes.simple(true);
            Registry.register(Registries.PARTICLE_TYPE, Ragnarok.id(name), t);
            TYPES.put(name, t);
        }
    }
}
