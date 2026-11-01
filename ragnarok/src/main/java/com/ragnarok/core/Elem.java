package com.ragnarok.core;

/** The four elements and the particle names / colours that give each one its identity. */
public final class Elem {
    private Elem() {}

    public static final int FIRE = 0, LIGHTNING = 1, ICE = 2, VOID = 3;

    public static final String[] KEY = {"fire", "lightning", "ice", "void"};
    public static final String[] BODY = {"flame", "bolt", "shard", "void_orb"};
    public static final String[] SPARK = {"ember", "spark_e", "frost_mist", "void_spark"};
    public static final String[] GLOW = {"glow_fire", "glow_bolt", "glow_ice", "glow_void"};
    public static final String[] RING = {"ring_fire", "ring_bolt", "ring_ice", "ring_void"};
    public static final String[] FLASH = {"flash_fire", "flash_bolt", "flash_ice", "flash_void"};
    public static final int[] COLOR = {0xFF5A1E, 0x7FC8FF, 0xA8E4FF, 0xA83CFF};

    /** Combo styles: index = style - 4 -> the two elements it merges. */
    public static final int[][] COMBO = {{FIRE, LIGHTNING}, {FIRE, ICE}, {LIGHTNING, VOID}, {ICE, VOID}};
}
