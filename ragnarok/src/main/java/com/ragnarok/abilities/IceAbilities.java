package com.ragnarok.abilities;

import com.ragnarok.combat.Combat;
import com.ragnarok.combat.Status;
import com.ragnarok.core.Ability;
import com.ragnarok.core.Elem;
import com.ragnarok.core.Scheduler;
import com.ragnarok.core.TempBlocks;
import com.ragnarok.particle.Fx;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** GELO PRIMORDIAL: area control, freezing, finishers. */
final class IceAbilities {
    private IceAbilities() {}

    private static final int I = Elem.ICE;

    static boolean run(Ability a, ServerPlayerEntity p, ServerWorld w) {
        return switch (a) {
            case FROST_PUNCH -> frostPunch(p, w);
            case GLACIAL_BARRAGE -> glacialBarrage(p, w);
            case FROZEN_UPPERCUT -> frozenUppercut(p, w);
            case ABSOLUTE_ZERO -> absoluteZero(p, w);
            case SHATTER_IMPACT -> shatterImpact(p, w);
            case ETERNAL_WINTER -> eternalWinter(p, w);
            default -> false;
        };
    }

    /** Spike of ice rising from the ground (temporary blocks), if destruction is allowed. */
    static void spike(ServerWorld w, Vec3d at, int height) {
        BlockPos base = BlockPos.ofFloored(at);
        for (int dy = 2; dy >= -3; dy--) {
            BlockPos q = base.up(dy);
            if (TempBlocks.replaceable(w.getBlockState(q)) && !TempBlocks.replaceable(w.getBlockState(q.down()))) {
                if (Combat.cfg().allowBlockDestruction) {
                    for (int i = 0; i < height; i++) TempBlocks.place(w, q.up(i), Blocks.PACKED_ICE.getDefaultState(), 260);
                }
                for (int i = 0; i < height; i++) Fx.burst(w, "shard", q.getX() + 0.5, q.getY() + i + 0.5, q.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.06);
                return;
            }
        }
    }

    private static boolean frostPunch(ServerPlayerEntity p, ServerWorld w) {
        LivingEntity e = Abilities.tgt(p, w, 7);
        if (e == null) return false;
        Abilities.dashAdjacent(p, w, e, I);
        p.swingHand(Hand.MAIN_HAND, true);
        Combat.strike(p, w, e, 11, Combat.flat(e.getPos().subtract(p.getPos())), 0.9, 0.2, I);
        Status.addFreeze(e, 2);
        Vec3d c = Combat.center(e);
        Fx.explode(w, I, c, 16, 0.25);
        Combat.sound(w, c, SoundEvents.BLOCK_GLASS_BREAK, 0.9f, 1.3f);
        Combat.shake(p, 1.2f, 5);
        return true;
    }

    /** Each punch stacks frost; the target ends frozen solid. */
    private static boolean glacialBarrage(ServerPlayerEntity p, ServerWorld w) {
        LivingEntity e = Abilities.tgt(p, w, 7);
        if (e == null) return false;
        Abilities.dashAdjacent(p, w, e, I);
        int hits = 9;
        Combat.barrage(p, w, e, I, hits, 2.4, false);
        Scheduler.later(hits * 2 + 3, () -> {
            if (!e.isAlive()) return;
            Status.freezeFully(e, 110);
            Fx.explode(w, I, Combat.center(e), 24, 0.3);
            Combat.sound(w, e.getPos(), SoundEvents.BLOCK_GLASS_BREAK, 1f, 0.7f);
        });
        return true;
    }

    private static boolean frozenUppercut(ServerPlayerEntity p, ServerWorld w) {
        LivingEntity e = Abilities.tgt(p, w, 7);
        if (e == null) return false;
        Abilities.dashAdjacent(p, w, e, I);
        p.swingHand(Hand.MAIN_HAND, true);
        Vec3d base = e.getPos();
        spike(w, base, 4);
        Combat.strike(p, w, e, 12, Vec3d.ZERO, 0.0, 1.7, I);
        Status.addFreeze(e, 2);
        Combat.setVel(p, new Vec3d(0, 0.7, 0));
        Abilities.loop(10, i -> {
            for (int k = 0; k < 4; k++) {
                double ang = i * 0.8 + k * Math.PI / 2;
                Fx.vel(w, "shard", base.x + Math.cos(ang) * 0.9, base.y + i * 0.6, base.z + Math.sin(ang) * 0.9, 0, 0.12, 0);
            }
            Fx.vel(w, "frost_mist", base.x, base.y + i * 0.6, base.z, 0, 0.06, 0);
            if (i % 3 == 0) Fx.ring(w, I, base.add(0, i * 0.5, 0), 0.5);
        });
        Combat.sound(w, base, SoundEvents.BLOCK_GLASS_BREAK, 1f, 0.6f);
        Combat.shake(p, 1.6f, 6);
        return true;
    }

    /** Freezes everyone in a wide area. */
    private static boolean absoluteZero(ServerPlayerEntity p, ServerWorld w) {
        Vec3d o = p.getPos();
        Combat.sound(w, o, SoundEvents.BLOCK_GLASS_BREAK, 1.4f, 0.5f);
        Combat.zoom(p, 0.9f, 20);
        Combat.shake(p, 1.4f, 10);
        Abilities.loop(9, i -> {
            double r = 1.5 + i * 1.0;
            for (int k = 0; k < 28; k++) {
                double ang = k * Math.PI * 2 / 28;
                Fx.vel(w, "frost_mist", o.x + Math.cos(ang) * r, o.y + 0.3, o.z + Math.sin(ang) * r, 0, 0.04, 0);
                if (k % 4 == 0) Fx.vel(w, "shard", o.x + Math.cos(ang) * r, o.y + 0.5, o.z + Math.sin(ang) * r, 0, 0.08, 0);
            }
            Fx.ring(w, I, o.add(0, 0.3, 0), r / 5.0);
            for (LivingEntity e : Combat.around(p, w, o, r + 0.8)) {
                if (Status.isFrozen(e)) continue;
                if (Math.hypot(e.getX() - o.x, e.getZ() - o.z) <= r + 0.8) {
                    Combat.hurtDirect(p, w, e, 6);
                    Status.freezeFully(e, 130);
                }
            }
        });
        return true;
    }

    /** Smashes the nearest frozen enemy: massive damage and a burst of shards that hits its neighbours. */
    private static boolean shatterImpact(ServerPlayerEntity p, ServerWorld w) {
        LivingEntity best = null;
        double bd = 1e9;
        for (LivingEntity e : Combat.around(p, w, p.getPos(), 12)) {
            if (!Status.isFrozen(e)) continue;
            double d = e.squaredDistanceTo(p);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        LivingEntity e = best != null ? best : Abilities.tgt(p, w, 7);
        if (e == null) return false;
        Abilities.dashAdjacent(p, w, e, I);
        p.swingHand(Hand.MAIN_HAND, true);
        Vec3d c = Combat.center(e);
        if (Status.isFrozen(e)) {
            Combat.hurtDirect(p, w, e, 28);
            Status.shatter(e);
            Combat.explosion(p, w, c, 4.0, 10, I, 0.9, 1);
            Combat.sound(w, c, SoundEvents.BLOCK_GLASS_BREAK, 1.4f, 0.5f);
        } else {
            Combat.strike(p, w, e, 14, Combat.flat(e.getPos().subtract(p.getPos())), 1.0, 0.3, I);
            Status.addFreeze(e, 2);
        }
        Combat.shake(p, 2.2f, 8);
        return true;
    }

    /** ULTIMATE: an ice storm covers the battlefield; ice spires grow and everything freezes. */
    private static boolean eternalWinter(ServerPlayerEntity p, ServerWorld w) {
        Vec3d o = p.getPos();
        Combat.zoom(p, 0.82f, 30);
        Combat.sound(w, o, SoundEvents.ENTITY_PLAYER_HURT_FREEZE, 1.5f, 0.5f);
        Combat.shake(p, 1.6f, 14);
        Abilities.loop(160, i -> {
            // blizzard
            for (int k = 0; k < 10; k++) {
                double ang = Abilities.R.nextDouble() * Math.PI * 2;
                double r = Math.sqrt(Abilities.R.nextDouble()) * 13;
                double x = o.x + Math.cos(ang) * r, z = o.z + Math.sin(ang) * r;
                Fx.vel(w, k % 3 == 0 ? "shard" : "frost_mist", x, o.y + 3 + Abilities.R.nextDouble() * 5, z, -0.18, -0.12, 0.05);
            }
            if (i < 14) Fx.ring(w, I, o.add(0, 0.3, 0), 0.5 + i * 0.15);
            if (i % 10 == 0) {
                for (LivingEntity e : Combat.around(p, w, o, 13)) {
                    Status.addFreeze(e, 1);
                    Combat.hurtDirect(p, w, e, 3);
                }
            }
            if (i % 14 == 0 && i < 120) {
                double ang = Abilities.R.nextDouble() * Math.PI * 2, r = 3 + Abilities.R.nextDouble() * 9;
                spike(w, o.add(Math.cos(ang) * r, 0, Math.sin(ang) * r), 3 + Abilities.R.nextInt(4));
            }
            if (i % 40 == 0) Combat.sound(w, o, SoundEvents.ENTITY_PLAYER_HURT_FREEZE, 1f, 0.6f);
        });
        return true;
    }
}
