package com.ragnarok.abilities;

import com.ragnarok.combat.Combat;
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

/** FOGO ABISSAL: black flames with red and orange edges. Destruction, explosions, damage over time. */
final class FireAbilities {
    private FireAbilities() {}

    private static final int F = Elem.FIRE;

    static boolean run(Ability a, ServerPlayerEntity p, ServerWorld w) {
        return switch (a) {
            case INFERNAL_STRIKE -> infernalStrike(p, w);
            case HELLFIRE_BARRAGE -> hellfireBarrage(p, w);
            case DRAGON_RISE -> dragonRise(p, w);
            case CATACLISM -> cataclism(p, w);
            case PHOENIX_IMPACT -> phoenixImpact(p, w);
            case RAGNAROK -> ragnarok(p, w);
            default -> false;
        };
    }

    /** A charged punch of black fire; explodes where it lands. */
    private static boolean infernalStrike(ServerPlayerEntity p, ServerWorld w) {
        LivingEntity e = Abilities.tgt(p, w, 7);
        if (e == null) return false;
        Abilities.dashAdjacent(p, w, e, F);
        p.swingHand(Hand.MAIN_HAND, true);
        Combat.sound(w, p.getPos(), SoundEvents.ENTITY_BLAZE_SHOOT, 1f, 0.6f);
        Abilities.loop(5, i -> Fx.burst(w, "flame", p.getX() + p.getRotationVec(1f).x * 0.8, p.getY() + 1.2, p.getZ() + p.getRotationVec(1f).z * 0.8, 3 + i, 0.15, 0.15, 0.15, 0.03));
        Scheduler.later(6, () -> {
            if (!Abilities.alive(p) || !e.isAlive()) return;
            Vec3d dir = Combat.flat(e.getPos().subtract(p.getPos()));
            Combat.strike(p, w, e, 14, dir, 1.4, 0.3, F);
            Combat.explosion(p, w, Combat.center(e), 3.6, 8, F, 0.8, 1);
            Combat.shake(p, 1.8f, 8);
        });
        return true;
    }

    /** Ten punches, each one a small explosion; the last sends the target flying. */
    private static boolean hellfireBarrage(ServerPlayerEntity p, ServerWorld w) {
        LivingEntity e = Abilities.tgt(p, w, 7);
        if (e == null) return false;
        Abilities.dashAdjacent(p, w, e, F);
        int hits = 10;
        Combat.barrage(p, w, e, F, hits, 2.8, false);
        for (int i = 0; i < hits; i++) {
            final int k = i;
            Scheduler.later(2 + i * 2, () -> {
                if (!e.isAlive()) return;
                Vec3d c = Combat.center(e);
                if (k == hits - 1) Combat.explosion(p, w, c, 4.2, 10, F, 1.0, 2);
                else Combat.explosionLite(p, w, c, 2.2, 1.5, F);
            });
        }
        return true;
    }

    /** Uppercut that wraps the arm in a fire dragon and throws the target into the sky. */
    private static boolean dragonRise(ServerPlayerEntity p, ServerWorld w) {
        LivingEntity e = Abilities.tgt(p, w, 7);
        if (e == null) return false;
        Abilities.dashAdjacent(p, w, e, F);
        p.swingHand(Hand.MAIN_HAND, true);
        Vec3d base = e.getPos();
        Combat.strike(p, w, e, 12, Combat.flat(base.subtract(p.getPos())).multiply(0.2), 0.15, 1.9, F);
        Combat.setVel(p, new Vec3d(0, 0.85, 0));
        Combat.sound(w, base, SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1.3f);
        Abilities.loop(18, i -> {
            double y = base.y + i * 0.85;
            for (int s = 0; s < 3; s++) {
                double ang = i * 0.7 + s * Math.PI * 2 / 3;
                double rad = 1.3 - i * 0.02;
                double x = base.x + Math.cos(ang) * rad, z = base.z + Math.sin(ang) * rad;
                Fx.vel(w, "flame", x, y, z, -Math.sin(ang) * 0.05, 0.1, Math.cos(ang) * 0.05);
                Fx.vel(w, "glow_fire", x, y, z, 0, 0.05, 0);
            }
            Fx.vel(w, "ember", base.x, y, base.z, R(0.15), 0.1, R(0.15));
            for (LivingEntity o : Combat.around(p, w, new Vec3d(base.x, y, base.z), 2.4)) {
                if (i % 3 == 0) {
                    Combat.hurtDirect(p, w, o, 3);
                    Combat.setVel(o, new Vec3d(o.getVelocity().x * 0.5, 0.9, o.getVelocity().z * 0.5));
                    o.setOnFireFor(4);
                }
            }
            if (i == 17) { // the dragon's head
                Vec3d top = new Vec3d(base.x, y + 0.5, base.z);
                Fx.explode(w, F, top, 40, 0.45);
                for (int h = -1; h <= 1; h += 2) {
                    for (int k = 0; k < 6; k++) Fx.vel(w, "flame", top.x + h * k * 0.18, top.y + k * 0.22, top.z, h * 0.04, 0.05, 0);
                }
                Combat.sound(w, top, SoundEvents.ENTITY_GENERIC_EXPLODE, 0.8f, 1.0f);
            }
        });
        return true;
    }

    private static double R(double s) {
        return (Abilities.R.nextDouble() - 0.5) * 2 * s;
    }

    /** Ground slam: a wave of fire races across the terrain. */
    private static boolean cataclism(ServerPlayerEntity p, ServerWorld w) {
        Vec3d o = p.getPos();
        p.swingHand(Hand.MAIN_HAND, true);
        Combat.setVel(p, new Vec3d(0, -0.2, 0));
        Combat.sound(w, o, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.3f, 0.6f);
        Combat.crater(w, o.add(0, -1, 0), 2);
        Fx.explode(w, F, o.add(0, 0.5, 0), 40, 0.4);
        Combat.shake(p, 2.0f, 10);
        Abilities.loop(13, i -> Abilities.groundRing(p, w, o, 1.5 + i * 0.9, F, 9, 0.7, 0.9, true));
        return true;
    }

    /** Becomes flame, crosses the distance in a burst and strikes in the shape of a phoenix. */
    private static boolean phoenixImpact(ServerPlayerEntity p, ServerWorld w) {
        LivingEntity e = Abilities.tgt(p, w, 16);
        if (e == null) return false;
        Vec3d from = p.getPos();
        Combat.sound(w, from, SoundEvents.ENTITY_BLAZE_SHOOT, 1f, 0.5f);
        Fx.explode(w, F, from.add(0, 1, 0), 24, 0.25);
        Abilities.loop(4, i -> Fx.aura(w, F, p.getX(), p.getY(), p.getZ(), 1.0, 2.0, 8));
        Scheduler.later(5, () -> {
            if (!Abilities.alive(p) || !e.isAlive()) return;
            Vec3d to = e.getPos();
            Vec3d dir = to.subtract(from).normalize();
            Vec3d dest = Abilities.safe(p, w, from, to.subtract(dir.multiply(1.5)));
            Combat.faceAndTeleport(p, dest, to);
            for (int i = 0; i <= 12; i++) {
                Vec3d q = from.lerp(dest, i / 12.0).add(0, 1, 0);
                Fx.vel(w, "flame", q.x, q.y, q.z, 0, 0.02, 0);
                Fx.burst(w, "glow_fire", q.x, q.y, q.z, 2, 0.3, 0.3, 0.3, 0.02);
            }
            p.swingHand(Hand.MAIN_HAND, true);
            Combat.strike(p, w, e, 18, Combat.flat(dir), 1.6, 0.4, F);
            Vec3d c = Combat.center(e);
            // phoenix: two wings and a head made of flame
            double yaw = Math.toRadians(p.getYaw());
            Vec3d side = new Vec3d(Math.cos(yaw), 0, Math.sin(yaw));
            Vec3d back = new Vec3d(Math.sin(yaw), 0, -Math.cos(yaw));
            for (int s = -1; s <= 1; s += 2) {
                for (double t = 0.2; t <= 1.0; t += 0.08) {
                    Vec3d q = c.add(side.multiply(s * t * 3.4)).add(back.multiply(t * 1.6)).add(0, 0.3 + Math.sin(t * Math.PI * 0.9) * 1.7, 0);
                    Fx.vel(w, "flame", q.x, q.y, q.z, 0, 0.02, 0);
                    Fx.vel(w, "glow_fire", q.x, q.y, q.z, 0, 0.01, 0);
                }
            }
            Combat.explosion(p, w, c, 4.4, 10, F, 1.1, 2);
            Combat.shake(p, 2.2f, 10);
        });
        return true;
    }

    /** ULTIMATE: concentrates everything in one fist and detonates a colossal blast of black fire. */
    private static boolean ragnarok(ServerPlayerEntity p, ServerWorld w) {
        Vec3d aim = Abilities.aimPoint(p, w, 22);
        Combat.zoom(p, 0.75f, 40);
        Combat.sound(w, p.getPos(), SoundEvents.ENTITY_WITHER_SPAWN, 1.3f, 0.7f);
        Abilities.loop(26, i -> {
            if (!Abilities.alive(p)) return;
            Vec3d look = p.getRotationVec(1f);
            Vec3d fist = p.getEyePos().add(look.multiply(1.1)).add(0, -0.4, 0);
            Combat.setVel(p, new Vec3d(0, Math.min(0.0, p.getVelocity().y), 0));
            Fx.burst(w, "flame", fist.x, fist.y, fist.z, 2 + i / 3, 0.1 + i * 0.012, 0.1 + i * 0.012, 0.1 + i * 0.012, 0.02);
            Fx.burst(w, "glow_fire", fist.x, fist.y, fist.z, 1 + i / 5, 0.2, 0.2, 0.2, 0.01);
            Fx.aura(w, F, p.getX(), p.getY(), p.getZ(), 1.2 + i * 0.03, 2.2, 3);
            if (i % 6 == 0) Fx.ring(w, F, p.getPos().add(0, 0.2, 0), 0.4 + i * 0.03);
            if (i % 8 == 0) Combat.shake(p, 0.6f + i * 0.04f, 4);
        });
        Scheduler.later(28, () -> {
            if (!Abilities.alive(p)) return;
            p.swingHand(Hand.MAIN_HAND, true);
            Vec3d eye = p.getEyePos();
            Fx.line(w, "flame", eye.add(0, -0.4, 0), aim, 0.6);
            Fx.line(w, "glow_fire", eye.add(0, -0.4, 0), aim, 0.9);
            Combat.sound(w, aim, SoundEvents.ENTITY_GENERIC_EXPLODE, 2f, 0.5f);
            Combat.explosion(p, w, aim.add(0, 0.5, 0), 9.5, 55, F, 2.3, 6);
            Combat.zoom(p, 1.15f, 16);
            Combat.shake(p, 3.5f, 18);
            // a column of black flame that burns for a while
            Abilities.loop(30, i -> {
                for (int k = 0; k < 6; k++) {
                    double ang = Abilities.R.nextDouble() * Math.PI * 2;
                    double r = Abilities.R.nextDouble() * (5.5 - i * 0.12);
                    double h = Abilities.R.nextDouble() * (6 + i * 0.5);
                    Fx.vel(w, "flame", aim.x + Math.cos(ang) * r, aim.y + h, aim.z + Math.sin(ang) * r, 0, 0.12, 0);
                }
                Fx.burst(w, "smoke", aim.x, aim.y + 4 + i * 0.3, aim.z, 2, 2.5, 1.0, 2.5, 0.02);
                if (i % 6 == 0) Fx.ring(w, F, aim.add(0, 0.3, 0), 1.2 + i * 0.05);
                if (i % 5 == 0) {
                    for (LivingEntity e : Combat.around(p, w, aim, 8)) {
                        e.setOnFireFor(6);
                        Combat.hurtDirect(p, w, e, 4);
                    }
                }
            });
        });
        return true;
    }
}
