package com.ragnarok.particle;

/** Behaviour of one custom particle type. sizeFromVx: the spawn "velocity X" is used as a size multiplier (rings, flashes). */
public record ParticleProfile(String texture, int rgb, int life, float size, float grow, float gravity,
                              float drag, float spin, float rise, boolean sizeFromVx, boolean glow) {
}
