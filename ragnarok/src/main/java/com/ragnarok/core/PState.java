package com.ragnarok.core;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** Per-player runtime state (not saved). */
public final class PState {
    public final Map<Ability, Long> cooldownEnd = new EnumMap<>(Ability.class);
    public long globalCd = 0;
    public long lastUse = -1000;
    public double heat = 0;
    public long overloadUntil = 0;

    // combat chain
    public long lastPunch = -1000, punchCd = 0;
    public int chain = 0;
    public long lastBarrageEnd = -1000;
    public long lastUppercut = -1000;
    public UUID uppercutTarget = null;
    public int combo = 0;
    public long lastHit = -1000;
    public long dodgeUntil = 0, dodgeCd = 0;
    public int altElem = 0;

    // transformation
    public int formElem = -1;
    public long formStart = 0, formEnd = 0;

    // hud sync
    public boolean dirty = true;
    public int lastCdMax = 1;

    public double comboMult() {
        return 1.0 + Math.min(30, combo) * 0.04;
    }
}
