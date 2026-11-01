package com.ragnarok.abilities;

import com.ragnarok.combat.Combat;
import com.ragnarok.core.Ability;
import com.ragnarok.core.Elem;
import com.ragnarok.core.Scheduler;
import com.ragnarok.particle.Fx;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

/** ENERGIA DO VOID: spatial manipulation, gravity, concentrated damage. */
final class VoidAbilities {
    private VoidAbilities() {}

    private static final int V = Elem.VOID;

    static boolean run(Ability a, ServerPlayerEntity p, ServerWorld w) {
        return switch (a) {
            case VOID_PUNCH -> voidPunch(p, w);
            case DARK_BARRAGE -> darkBarrage(p, w);
            case PHANTOM_STRIKE -> phantomStrike(p, w);
            case GRAVITY_CRUSH -> gravityCrush(p, w);
            case BLACK_HOLE -> blackHole(p, w);
            case SINGULARITY -> singularity(p, w);
            default -> false;
        };
    }

    /** Space folds inward at a point. */
    static void implode(ServerWorld w, Vec3d c, double radius, int count) {
        for (int i = 0; i < count; i++) {
            Vec3d d = new Vec3d(Abilities.R.nextGaussian(), Abilities.R.nextGaussian(), Abilities.R.nextGaussian()).normalize().multiply(radius);
            Fx.vel(w, "void_spark", c.x + d.x, c.y + d.y, c.z + d.z, -d.x * 0.18, -d.y * 0.18, -d.z * 0.18);
        }
        Fx.vel(w, "void_orb", c.x, c.y, c.z, 0, 0, 0);
    }

    private static boolean voidPunch(ServerPlayerEntity p, ServerWorld w) {
        LivingEntity e = Abilities.tgt(p, w, 7);
        if (e == null) return false;
        Abilities.dashAdjacent(p, w, e, V);
        p.swingHand(Hand.MAIN_HAND, true);
        Vec3d c = Combat.center(e);
        implode(w, c, 2.5, 20);
        Combat.strike(p, w, e, 10, Combat.flat(e.getPos().subtract(p.getPos())), 0.9, 0.2, V);
        Combat.hurtMagic(p, w, e, 10); // space is torn: ignores armour
        Fx.explode(w, V, c, 20, 0.3);
        Combat.sound(w, c, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
        Combat.shake(p, 1.4f, 6);
        return true;
    }

    /** Each hit tears a small rupture that damages whatever is near. */
    private static boolean darkBarrage(ServerPlayerEntity p, ServerWorld w) {
        LivingEntity e = Abilities.tgt(p, w, 7);
        if (e == null) return false;
        Abilities.dashAdjacent(p, w, e, V);
        int hits = 10;
        Combat.barrage(p, w, e, V, hits, 2.4, false);
        for (int i = 0; i < hits; i++) {
            final int k = i;
            Scheduler.later(2 + i * 2, () -> {
                if (!e.isAlive()) return;
                Vec3d c = Combat.center(e).add(Abilities.R.nextGaussian() * 0.8, Abilities.R.nextGaussian() * 0.5, Abilities.R.nextGaussian() * 0.8);
                Fx.ring(w, V, c, 0.5);
                implode(w, c, 1.6, 8);
                for (LivingEntity o : Combat.around(p, w, c, 2.2)) Combat.hurtMagic(p, w, o, 2.5);
                if (k % 3 == 0) Combat.sound(w, c, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 0.5f, 0.6f + k * 0.08f);
            });
        }
        return true;
    }

    /** Crosses space and reappears behind the enemy. */
    private static boolean phantomStrike(ServerPlayerEntity p, ServerWorld w) {
        LivingEntity e = Abilities.tgt(p, w, 20);
        if (e == null) return false;
        Vec3d from = p.getPos();
        implode(w, from.add(0, 1, 0), 2.2, 22);
        Fx.ring(w, V, from.add(0, 1, 0), 0.7);
        Combat.sound(w, from, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1f, 0.6f);
        Vec3d to = Combat.behind(w, e);
        Combat.faceAndTeleport(p, to, e.getPos());
        Combat.trail(w, V, from, to);
        Fx.explode(w, V, to.add(0, 1, 0), 14, 0.25);
        Scheduler.later(2, () -> {
            if (!Abilities.alive(p) || !e.isAlive()) return;
            p.swingHand(Hand.MAIN_HAND, true);
            Combat.strike(p, w, e, 12, Combat.flat(e.getPos().subtract(p.getPos())), 1.0, 0.25, V);
            Combat.hurtMagic(p, w, e, 6);
            Combat.shake(p, 1.2f, 5);
        });
        return true;
    }

    /** Crushes everything in an area into the ground. */
    private static boolean gravityCrush(ServerPlayerEntity p, ServerWorld w) {
        Vec3d c = Abilities.aimPoint(p, w, 14);
        double radius = 7;
        Combat.sound(w, c, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 1f, 0.5f);
        Combat.shake(p, 1.5f, 12);
        Scheduler.later(2, () -> Combat.crater(w, c.add(0, -1, 0), 2));
        Abilities.loop(44, i -> {
            if (i % 2 == 0) {
                for (int k = 0; k < 24; k++) {
                    double ang = Abilities.R.nextDouble() * Math.PI * 2;
                    double r = Abilities.R.nextDouble() * radius;
                    Fx.vel(w, "void_spark", c.x + Math.cos(ang) * r, c.y + 4 + Abilities.R.nextDouble() * 3, c.z + Math.sin(ang) * r, 0, -0.5, 0);
                }
                Fx.ring(w, V, c.add(0, 0.3, 0), (radius - i * 0.05) / 5.0);
            }
            for (LivingEntity e : Combat.around(p, w, c, radius + 1)) {
                Combat.setVel(e, new Vec3d(e.getVelocity().x * 0.2, Math.min(e.getVelocity().y, 0) - 0.9, e.getVelocity().z * 0.2));
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 10, 4));
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 20, 1));
                if (i % 4 == 0) {
                    Combat.hurtMagic(p, w, e, 3);
                    Fx.burst(w, "smoke", e.getX(), e.getY() + 0.2, e.getZ(), 4, 0.4, 0.1, 0.4, 0.03);
                }
            }
        });
        return true;
    }

    /** A singularity that pulls entities, projectiles and items toward its centre. */
    private static boolean blackHole(ServerPlayerEntity p, ServerWorld w) {
        Vec3d aim = Abilities.aimPoint(p, w, 18);
        Vec3d c = aim.add(0, 1.6, 0);
        Combat.sound(w, c, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.2f, 0.4f);
        Abilities.loop(90, i -> {
            Abilities.pull(p, w, c, 13, 0.32, true);
            double spin = i * 0.45;
            for (int k = 0; k < 4; k++) {
                double ang = spin + k * Math.PI / 2;
                double r = 1.8 + (k % 2) * 0.6;
                Fx.vel(w, "void_orb", c.x + Math.cos(ang) * r, c.y + Math.sin(ang * 0.5) * 0.3, c.z + Math.sin(ang) * r, 0, 0, 0);
            }
            implode(w, c, 6 + Abilities.R.nextDouble() * 5, 4);
            Fx.burst(w, "smoke", c.x, c.y, c.z, 1, 0.3, 0.3, 0.3, 0.01);
            if (i % 12 == 0) Fx.ring(w, V, c, 0.6 + (i % 36) * 0.02);
            if (i % 5 == 0) for (LivingEntity e : Combat.around(p, w, c, 2.6)) Combat.hurtMagic(p, w, e, 3.5);
            if (i == 89) {
                Fx.explode(w, V, c, 40, 0.5);
                Combat.sound(w, c, SoundEvents.ENTITY_GENERIC_EXPLODE, 1f, 0.6f);
                for (LivingEntity e : Combat.around(p, w, c, 6)) Combat.launch(p, w, e, Combat.center(e).subtract(c), 1.2, 0.4);
            }
        });
        return true;
    }

    /** ULTIMATE: a core of Void energy absorbs everything around, then releases a gravitational explosion. */
    private static boolean singularity(ServerPlayerEntity p, ServerWorld w) {
        Vec3d aim = Abilities.aimPoint(p, w, 12);
        Vec3d c = aim.add(0, 3.0, 0);
        Combat.zoom(p, 0.8f, 70);
        Combat.sound(w, c, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 0.4f);
        Abilities.loop(70, i -> {
            double grow = Math.min(1.0, i / 40.0);
            double radius = 3 + 12 * grow;
            Abilities.pull(p, w, c, radius + 3, 0.4 + 0.5 * grow, true);
            for (int k = 0; k < 8; k++) {
                double ang = i * 0.5 + k * Math.PI / 4;
                double r = 2.2 + 1.2 * Math.sin(i * 0.2 + k);
                Fx.vel(w, "void_orb", c.x + Math.cos(ang) * r, c.y + Math.sin(ang * 2) * 0.5, c.z + Math.sin(ang) * r, 0, 0, 0);
            }
            implode(w, c, radius, 12);
            Fx.burst(w, "smoke", c.x, c.y, c.z, 3, 0.8, 0.8, 0.8, 0.02);
            if (i % 8 == 0) Fx.ring(w, V, c, 0.7 + grow * 1.2);
            if (i % 6 == 0) {
                Combat.shake(p, 0.6f + (float) grow * 1.2f, 4);
                for (LivingEntity e : Combat.around(p, w, c, 4)) Combat.hurtMagic(p, w, e, 5);
            }
        });
        Scheduler.later(72, () -> {
            if (!Abilities.alive(p)) return;
            Combat.explosion(p, w, c.add(0, -2.0, 0), 14, 45, V, 2.4, 6);
            Fx.explode(w, V, c, 80, 0.8);
            Combat.zoom(p, 1.18f, 14);
            Combat.shake(p, 3.8f, 20);
            for (LivingEntity e : Combat.around(p, w, c, 14)) Combat.hurtMagic(p, w, e, 12);
        });
        return true;
    }
}
