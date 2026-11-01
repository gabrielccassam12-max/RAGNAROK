package com.ragnarok.core;

import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** All 40 abilities. style 0-3 = elements, 4-7 = combinations. level = mastery needed (0 Iniciante .. 3 Ascendente). */
public enum Ability {
    // ---- FOGO ABISSAL
    INFERNAL_STRIKE(0, 20, 60, 0), HELLFIRE_BARRAGE(0, 35, 120, 0), DRAGON_RISE(0, 35, 140, 1),
    CATACLISM(0, 50, 220, 1), PHOENIX_IMPACT(0, 55, 240, 2), RAGNAROK(0, 120, 900, 3),
    // ---- RAIO CELESTIAL
    THUNDER_PUNCH(1, 20, 50, 0), LIGHTNING_BARRAGE(1, 35, 120, 0), FLASH_STEP(1, 30, 100, 1),
    THUNDERCLAP(1, 45, 200, 1), DIVINE_UPPERCUT(1, 55, 220, 2), ZEUS_BREAKER(1, 120, 900, 3),
    // ---- GELO PRIMORDIAL
    FROST_PUNCH(2, 20, 50, 0), GLACIAL_BARRAGE(2, 35, 130, 0), FROZEN_UPPERCUT(2, 40, 150, 1),
    ABSOLUTE_ZERO(2, 55, 240, 1), SHATTER_IMPACT(2, 40, 100, 2), ETERNAL_WINTER(2, 120, 900, 3),
    // ---- ENERGIA DO VOID
    VOID_PUNCH(3, 20, 60, 0), DARK_BARRAGE(3, 35, 130, 0), PHANTOM_STRIKE(3, 30, 100, 1),
    GRAVITY_CRUSH(3, 50, 220, 1), BLACK_HOLE(3, 65, 300, 2), SINGULARITY(3, 130, 1000, 3),
    // ---- FOGO + RAIO
    VOLT_BARRAGE(4, 40, 140, 1), PLASMA_LANCE(4, 45, 160, 1), STORM_BLAZE(4, 60, 260, 2), INFERNO_VOLTAICO(4, 130, 1000, 3),
    // ---- FOGO + GELO
    THERMAL_SHOCK(5, 40, 140, 1), STEAM_BURST(5, 45, 180, 1), FROSTFIRE_WAVE(5, 60, 260, 2), THERMAL_APOCALYPSE(5, 130, 1000, 3),
    // ---- RAIO + VOID
    RIFT_STEP(6, 40, 120, 1), STATIC_RIFT(6, 50, 200, 1), RIFT_STORM(6, 65, 280, 2), SPACE_BREAKER(6, 130, 1000, 3),
    // ---- GELO + VOID
    STASIS_FIELD(7, 45, 180, 1), CRYO_GRAVITY(7, 55, 220, 1), CRYO_RIFT(7, 65, 260, 2), FROZEN_DIMENSION(7, 130, 1000, 3);

    public final int style, cost, cooldown, level;
    public final String key;

    Ability(int style, int cost, int cooldown, int level) {
        this.style = style;
        this.cost = cost;
        this.cooldown = cooldown;
        this.level = level;
        this.key = name().toLowerCase();
    }

    private static final List<List<Ability>> BY_STYLE = new ArrayList<>();

    static {
        for (int i = 0; i < 8; i++) BY_STYLE.add(new ArrayList<>());
        for (Ability a : values()) BY_STYLE.get(a.style).add(a);
    }

    public static List<Ability> of(int style) {
        return BY_STYLE.get(Math.floorMod(style, 8));
    }

    public static Ability get(int style, int index) {
        List<Ability> l = of(style);
        return l.get(Math.floorMod(index, l.size()));
    }

    public boolean ultimate() {
        return this == of(style).get(of(style).size() - 1);
    }

    public Text displayName() {
        return Text.translatable("ability.ragnarok." + key);
    }

    public static Text styleName(int style) {
        return Text.translatable("style.ragnarok." + style);
    }

    public static Text levelName(int level) {
        return Text.translatable("level.ragnarok." + Math.max(0, Math.min(3, level)));
    }
}
