package com.ragnarok.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;

/** Saved with the player and kept after death. */
public record PlayerData(int unlocked, int style, int ability, List<Integer> xp, int essence) {
    public static final PlayerData DEFAULT = new PlayerData(0, 0, 0, List.of(0, 0, 0, 0), 0);
    /** XP needed for Iniciante, Avançado, Mestre, Ascendente. */
    public static final int[] LEVEL_XP = {0, 100, 400, 1000};

    public static final Codec<PlayerData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("unlocked").forGetter(PlayerData::unlocked),
            Codec.INT.fieldOf("style").forGetter(PlayerData::style),
            Codec.INT.fieldOf("ability").forGetter(PlayerData::ability),
            Codec.INT.listOf().fieldOf("xp").forGetter(PlayerData::xp),
            Codec.INT.fieldOf("essence").forGetter(PlayerData::essence)
    ).apply(i, PlayerData::new));

    public boolean has(int elem) {
        return (unlocked >> elem & 1) != 0;
    }

    public boolean any() {
        return unlocked != 0;
    }

    public int xp(int elem) {
        return elem >= 0 && elem < xp.size() ? xp.get(elem) : 0;
    }

    public static int levelOf(int xp) {
        int l = 0;
        for (int i = 0; i < LEVEL_XP.length; i++) if (xp >= LEVEL_XP[i]) l = i;
        return l;
    }

    public int level(int elem) {
        return levelOf(xp(elem));
    }

    /** Mastery of a style: an element's own level, or the lower of the two for a combination. */
    public int styleLevel(int style) {
        if (style < 4) return level(style);
        int[] c = Elem.COMBO[style - 4];
        return Math.min(level(c[0]), level(c[1]));
    }

    public boolean styleUnlocked(int style) {
        if (style < 4) return has(style);
        int[] c = Elem.COMBO[style - 4];
        return has(c[0]) && has(c[1]);
    }

    public PlayerData withStyle(int v) { return new PlayerData(unlocked, v, 0, xp, essence); }
    public PlayerData withAbility(int v) { return new PlayerData(unlocked, style, v, xp, essence); }
    public PlayerData withEssence(int v) { return new PlayerData(unlocked, style, ability, xp, v); }
    public PlayerData withUnlocked(int v) { return new PlayerData(v, style, ability, xp, essence); }
    public PlayerData withXp(List<Integer> v) { return new PlayerData(unlocked, style, ability, v, essence); }

    public PlayerData addXp(int elem, int n) {
        List<Integer> l = new ArrayList<>(xp);
        while (l.size() < 4) l.add(0);
        l.set(elem, l.get(elem) + n);
        return withXp(l);
    }
}
