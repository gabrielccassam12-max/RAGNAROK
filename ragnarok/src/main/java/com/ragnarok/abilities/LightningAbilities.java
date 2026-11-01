package com.ragnarok.abilities;

import com.ragnarok.combat.Combat;
import com.ragnarok.combat.Status;
import com.ragnarok.core.Ability;
import com.ragnarok.core.Elem;
import com.ragnarok.core.Scheduler;
import com.ragnarok.particle.Fx;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/** RAIO CELESTIAL: speed, precision, instant strikes, paralysis. */
final class LightningAbilities {
    private LightningAbilities() {}

    private static final int L = Elem.LIGHTNING;

    static boolean run(Ability a, ServerPlayerEntity p, ServerWorld w) {
        return switch (a) {
            case THUNDER_PUNCH -> thunderPunch(p, w);
            case LIGHTNING_BARRAGE -> lightningBarrage(p, w);
            case FLASH_STEP -> flashStep(p, w);
            case THUNDERCLAP -> thunderclap(p, w);
            case DIVINE_UPPERCUT -> divineUppercut(p, w);
            case ZEUS_BREAKER -> zeusBreaker(p, w);
            default -> false;
        };
    }

    /** A bolt that falls on the target and arcs to its neighbours. */
    static void smite(ServerPlayerEntity p, ServerWorld w, Vec3d at, double dmg, double radius, boolean realBolt) {
        if (realBolt) Combat.bolt(w, at);
        Vec3d top = at.add(0, 24, 0);
        for (int i = 0; i < 12; i++) {
            Vec3d q = top.lerp(at, i / 11.0).add(Abilities.R.nextGaussian() * 0.25, 0, Abilities.R.nextGaussian() * 0.25);
            Fx.vel(w, "bolt", q.x, q.y, q.z, 0, 0, 0);
            Fx.vel(w, "glow_bolt", q.x, q.y, q.z, 0, 0, 0);
        }
        Fx.explode(w, L, at.add(0, 0.5, 0), 18, 0.3);
        for (LivingEntity e : Combat.around(p, w, at, radius)) {
            Combat.hurtDirect(p, w, e, dmg);
            Combat.launch(p, w, e, Combat.center(e).subtract(at), 0.7, 0.55);
            Status.paralyze(e, 30);
        }
    }

    /** Electric punch: extra damage and paralysis, with chain arcs. */
    private static boolean thunderPunch(ServerPlayerEntity p, ServerWorld w) {
        LivingEntity e = Abilities.tgt(p, w, 7);
        if (e == null) return false;
        Abilities.dashAdjacent(p, w, e, L);
        p.swingHand(Hand.MAIN_HAND, true);
        Combat.strike(p, w, e, 12, Combat.flat(e.getPos().subtract(p.getPos())), 1.0, 0.25, L);
        Status.paralyze(e, 50);
        Vec3d c = Combat.center(e);
        Combat.bolt(w, c);
        for (LivingEntity o : Combat.around(p, w, c, 5)) {
            if (o == e) continue;
            Fx.arc(w, c, Combat.center(o));
            Combat.hurtDirect(p, w, o, 5);
            Status.paralyze(o, 30);
        }
        Combat.sound(w, c, SoundEvents.ITEM_TRIDENT_THUNDER, 0.8f, 1.4f);
        Combat.shake(p, 1.6f, 6);
        return true;
    }

    /** Barrage so fast it leaves after-images of the fighter around the target. */
    private static boolean lightningBarrage(ServerPlayerEntity p, ServerWorld w) {
        LivingEntity e = Abilities.tgt(p, w, 7);
        if (e == null) return false;
        Abilities.dashAdjacent(p, w, e, L);
        int hits = 12;
        Combat.barrage(p, w, e, L, hits, 2.4, false);
        for (int i = 0; i < hits; i++) {
            final int k = i;
            Scheduler.later(2 + i * 2, () -> {
                if (!e.isAlive()) return;
                Vec3d c = e.getPos();
                double ang = k * 2.4;
                Vec3d at = c.add(Math.cos(ang) * 2.0, 0, Math.sin(ang) * 2.0);
                for (int h = 0; h < 6; h++) Fx.vel(w, "glow_bolt", at.x, at.y + 0.1 + h * 0.33, at.z, 0, 0, 0);
                Fx.vel(w, "bolt", at.x, at.y + 1.0, at.z, 0, 0, 0);
                Fx.arc(w, at.add(0, 1, 0), Combat.center(e));
                if (k % 3 == 0) Combat.sound(w, c, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, 0.4f, 1.8f);
            });
        }
        return true;
    }

    /** Vanishes in a bolt and reappears behind the target with an automatic punch. */
    private static boolean flashStep(ServerPlayerEntity p, ServerWorld w) {
        LivingEntity e = Abilities.tgt(p, w, 18);
        if (e == null) return false;
        Vec3d from = p.getPos();
        Fx.arc(w, from.add(0, 4, 0), from.add(0, 0.5, 0));
        Fx.explode(w, L, from.add(0, 1, 0), 14, 0.25);
        Vec3d to = Combat.behind(w, e);
        Combat.faceAndTeleport(p, to, e.getPos());
        Combat.trail(w, L, from, to);
        Fx.arc(w, to.add(0, 4, 0), to.add(0, 0.5, 0));
        Combat.sound(w, to, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 1.7f);
        Scheduler.later(2, () -> {
            if (!Abilities.alive(p) || !e.isAlive()) return;
            p.swingHand(Hand.MAIN_HAND, true);
            Combat.strike(p, w, e, 11, Combat.flat(e.getPos().subtract(p.getPos())), 1.1, 0.3, L);
            Status.paralyze(e, 25);
            Combat.shake(p, 1.3f, 5);
        });
        return true;
    }

    /** Claps the fists: a radial wave of electricity. */
    private static boolean thunderclap(ServerPlayerEntity p, ServerWorld w) {
        Vec3d o = p.getPos().add(0, 1, 0);
        p.swingHand(Hand.MAIN_HAND, true);
        p.swingHand(Hand.OFF_HAND, true);
        Combat.sound(w, o, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 1.4f, 1.3f);
        Combat.shake(p, 2.0f, 8);
        Fx.explode(w, L, o, 40, 0.5);
        Abilities.loop(8, i -> {
            double r = 1.2 + i * 1.0;
            for (int k = 0; k < 18; k++) {
                double ang = k * Math.PI * 2 / 18 + i * 0.3;
                Fx.vel(w, "spark_e", o.x + Math.cos(ang) * r, o.y - 0.4, o.z + Math.sin(ang) * r, 0, 0.05, 0);
                if (k % 3 == 0) Fx.vel(w, "bolt", o.x + Math.cos(ang) * r, o.y, o.z + Math.sin(ang) * r, 0, 0, 0);
            }
            Fx.ring(w, L, o.add(0, -0.8, 0), r / 5.0);
            for (LivingEntity e : Combat.around(p, w, o, r + 1)) {
                double d = Math.hypot(e.getX() - o.x, e.getZ() - o.z);
                if (d > r - 1.3 && d <= r + 0.8) {
                    Combat.hurtDirect(p, w, e, 9);
                    Combat.launch(p, w, e, Combat.center(e).subtract(o), 1.1, 0.4);
                    Status.paralyze(e, 35);
                    if (i % 2 == 0) Fx.arc(w, o, Combat.center(e));
                }
            }
        });
        return true;
    }

    /** A huge vertical bolt carries the target into the sky. */
    private static boolean divineUppercut(ServerPlayerEntity p, ServerWorld w) {
        LivingEntity e = Abilities.tgt(p, w, 7);
        if (e == null) return false;
        Abilities.dashAdjacent(p, w, e, L);
        p.swingHand(Hand.MAIN_HAND, true);
        Vec3d base = e.getPos();
        Combat.strike(p, w, e, 13, Vec3d.ZERO, 0.0, 2.2, L);
        Combat.setVel(p, new Vec3d(0, 0.9, 0));
        Combat.bolt(w, base);
        Combat.sound(w, base, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 1.0f);
        Abilities.loop(14, i -> {
            for (int h = 0; h < 4; h++) {
                double y = base.y + i * 2.2 + h * 0.5;
                Fx.vel(w, "bolt", base.x + Abilities.R.nextGaussian() * 0.25, y, base.z + Abilities.R.nextGaussian() * 0.25, 0, 0, 0);
                Fx.vel(w, "glow_bolt", base.x, y, base.z, 0, 0.2, 0);
            }
            if (i % 3 == 0) Fx.ring(w, L, base.add(0, i * 2.0, 0), 0.5);
            for (LivingEntity o : Combat.around(p, w, base.add(0, i * 1.0, 0), 3.2)) {
                if (i % 4 == 0) {
                    Combat.hurtDirect(p, w, o, 5);
                    Combat.setVel(o, new Vec3d(0, 1.5, 0));
                    Status.paralyze(o, 20);
                }
            }
        });
        Combat.shake(p, 2.4f, 10);
        return true;
    }

    /** ULTIMATE: a punch that summons a storm of bolts over the impact area. */
    private static boolean zeusBreaker(ServerPlayerEntity p, ServerWorld w) {
        Vec3d aim = Abilities.aimPoint(p, w, 16);
        LivingEntity e = Abilities.tgt(p, w, 16);
        Combat.zoom(p, 0.78f, 36);
        Abilities.loop(14, i -> {
            if (!Abilities.alive(p)) return;
            Vec3d fist = p.getEyePos().add(p.getRotationVec(1f).multiply(1.0)).add(0, -0.4, 0);
            Fx.burst(w, "spark_e", fist.x, fist.y, fist.z, 4, 0.2, 0.2, 0.2, 0.1);
            Fx.arc(w, fist, fist.add(Abilities.R.nextGaussian(), Abilities.R.nextGaussian(), Abilities.R.nextGaussian()));
            Fx.aura(w, L, p.getX(), p.getY(), p.getZ(), 1.0, 2.0, 4);
            if (i % 5 == 0) Combat.sound(w, p.getPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, 0.7f, 0.6f + i * 0.05f);
        });
        Scheduler.later(16, () -> {
            if (!Abilities.alive(p)) return;
            Vec3d at = aim;
            if (e != null && e.isAlive()) {
                Abilities.dashAdjacent(p, w, e, L);
                p.swingHand(Hand.MAIN_HAND, true);
                Combat.strike(p, w, e, 24, Combat.flat(e.getPos().subtract(p.getPos())), 1.5, 0.4, L);
                at = e.getPos();
            }
            final Vec3d center = at;
            Combat.explosion(p, w, center.add(0, 0.5, 0), 5, 14, L, 1.2, 1);
            Combat.shake(p, 3.0f, 14);
            Abilities.loop(55, i -> {
                if (i % 2 == 0) {
                    double ang = Abilities.R.nextDouble() * Math.PI * 2;
                    double r = Abilities.R.nextDouble() * 9;
                    Vec3d s = center.add(Math.cos(ang) * r, 0, Math.sin(ang) * r);
                    smite(p, w, s, 14, 3.2, i % 6 == 0);
                    Fx.ring(w, L, s.add(0, 0.2, 0), 0.6);
                }
                Fx.burst(w, "spark_e", center.x, center.y + 10, center.z, 6, 7, 2, 7, 0.01);
                if (i % 10 == 0) Combat.sound(w, center, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 1.4f, 0.8f);
            });
        });
        return true;
    }
}
