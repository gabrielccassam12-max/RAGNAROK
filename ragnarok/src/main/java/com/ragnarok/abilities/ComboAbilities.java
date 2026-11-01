package com.ragnarok.abilities;

import com.ragnarok.combat.Combat;
import com.ragnarok.combat.Status;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The four elemental combinations: 3 own abilities + 1 ultimate each. */
final class ComboAbilities {
    private ComboAbilities() {}

    private static final int F = Elem.FIRE, L = Elem.LIGHTNING, I = Elem.ICE, V = Elem.VOID;

    static boolean run(Ability a, ServerPlayerEntity p, ServerWorld w) {
        return switch (a) {
            case VOLT_BARRAGE -> voltBarrage(p, w);
            case PLASMA_LANCE -> plasmaLance(p, w);
            case STORM_BLAZE -> stormBlaze(p, w);
            case INFERNO_VOLTAICO -> infernoVoltaico(p, w);
            case THERMAL_SHOCK -> thermalShock(p, w);
            case STEAM_BURST -> steamBurst(p, w);
            case FROSTFIRE_WAVE -> frostfireWave(p, w);
            case THERMAL_APOCALYPSE -> thermalApocalypse(p, w);
            case RIFT_STEP -> riftStep(p, w);
            case STATIC_RIFT -> staticRift(p, w);
            case RIFT_STORM -> riftStorm(p, w);
            case SPACE_BREAKER -> spaceBreaker(p, w);
            case STASIS_FIELD -> stasisField(p, w);
            case CRYO_GRAVITY -> cryoGravity(p, w);
            case CRYO_RIFT -> cryoRift(p, w);
            case FROZEN_DIMENSION -> frozenDimension(p, w);
            default -> false;
        };
    }

    private static double segDist(Vec3d pt, Vec3d a, Vec3d b) {
        Vec3d ab = b.subtract(a);
        double t = Math.max(0, Math.min(1, pt.subtract(a).dotProduct(ab) / Math.max(1.0E-6, ab.lengthSquared())));
        return pt.distanceTo(a.add(ab.multiply(t)));
    }

    /** Vertical ring of void energy (a portal). */
    private static void portal(ServerWorld w, Vec3d c, double yaw, double r) {
        double y = Math.toRadians(yaw);
        Vec3d side = new Vec3d(Math.cos(y), 0, Math.sin(y));
        for (int k = 0; k < 20; k++) {
            double ang = k * Math.PI * 2 / 20;
            Vec3d q = c.add(side.multiply(Math.cos(ang) * r)).add(0, Math.sin(ang) * r, 0);
            Fx.vel(w, k % 2 == 0 ? "void_spark" : "spark_e", q.x, q.y, q.z, 0, 0, 0);
        }
        Fx.vel(w, "void_orb", c.x, c.y, c.z, 0, 0, 0);
        Fx.ring(w, V, c, 0.35);
    }

    // =========================================================== FIRE + LIGHTNING
    /** Fast electric barrage that ends in a fire explosion. */
    private static boolean voltBarrage(ServerPlayerEntity p, ServerWorld w) {
        LivingEntity e = Abilities.tgt(p, w, 7);
        if (e == null) return false;
        Abilities.dashAdjacent(p, w, e, L);
        int hits = 12;
        Combat.barrage(p, w, e, L, hits, 2.4, false);
        for (int i = 0; i < hits; i++) {
            final int k = i;
            Scheduler.later(2 + i * 2, () -> {
                if (!e.isAlive()) return;
                Vec3d c = Combat.center(e);
                Fx.arc(w, c.add(Abilities.R.nextGaussian() * 2, 2, Abilities.R.nextGaussian() * 2), c);
                Fx.burst(w, "flame", c.x, c.y, c.z, 3, 0.3, 0.3, 0.3, 0.05);
                if (k == hits - 1) {
                    Combat.explosion(p, w, c, 4.5, 12, F, 1.2, 1);
                    for (LivingEntity o : Combat.around(p, w, c, 4.5)) Status.paralyze(o, 30);
                }
            });
        }
        return true;
    }

    /** A plasma dash through everything in a straight line. */
    private static boolean plasmaLance(ServerPlayerEntity p, ServerWorld w) {
        Vec3d from = p.getPos();
        Vec3d look = p.getRotationVec(1f);
        Combat.sound(w, from, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.8f);
        Vec3d dest = Abilities.dashTo(p, w, from.add(look.multiply(14)), F);
        Fx.line(w, "flame", from.add(0, 1, 0), dest.add(0, 1, 0), 0.5);
        Fx.line(w, "bolt", from.add(0, 1.2, 0), dest.add(0, 1.2, 0), 1.6);
        Fx.line(w, "glow_bolt", from.add(0, 0.8, 0), dest.add(0, 0.8, 0), 0.6);
        int n = 0;
        for (LivingEntity e : Combat.around(p, w, from.lerp(dest, 0.5), 10)) {
            if (segDist(Combat.center(e), from.add(0, 1, 0), dest.add(0, 1, 0)) > 2.2) continue;
            Combat.strike(p, w, e, 12, Combat.flat(dest.subtract(from)), 1.3, 0.4, n++ % 2 == 0 ? F : L);
            Combat.explosionLite(p, w, Combat.center(e), 2.4, 3, F);
            Status.paralyze(e, 25);
        }
        Combat.explosion(p, w, dest.add(0, 1, 0), 3.2, 6, L, 0.8, 0);
        Combat.shake(p, 1.8f, 8);
        return true;
    }

    /** Bolts rain down and ignite everything. */
    private static boolean stormBlaze(ServerPlayerEntity p, ServerWorld w) {
        Vec3d o = p.getPos();
        Combat.sound(w, o, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 1.4f, 0.9f);
        Combat.zoom(p, 0.9f, 20);
        Abilities.loop(44, i -> {
            if (i % 2 != 0) return;
            double ang = Abilities.R.nextDouble() * Math.PI * 2, r = 2 + Abilities.R.nextDouble() * 8;
            Vec3d s = o.add(Math.cos(ang) * r, 0, Math.sin(ang) * r);
            LightningAbilities.smite(p, w, s, 6, 2.6, i % 6 == 0);
            Combat.explosionLite(p, w, s.add(0, 0.5, 0), 3.0, 6, F);
            for (LivingEntity e : Combat.around(p, w, s, 3)) e.setOnFireFor(5);
            Fx.burst(w, "flame", s.x, s.y + 0.3, s.z, 8, 0.6, 0.3, 0.6, 0.08);
        });
        return true;
    }

    /** ULTIMATE: dash, 14-hit barrage, colossal explosion and a rain of bolts. */
    private static boolean infernoVoltaico(ServerPlayerEntity p, ServerWorld w) {
        LivingEntity e = Abilities.tgt(p, w, 18);
        if (e == null) return false;
        Combat.zoom(p, 0.8f, 50);
        Abilities.dashAdjacent(p, w, e, L);
        int hits = 14;
        Combat.barrage(p, w, e, L, hits, 3.0, false);
        for (int i = 0; i < hits; i++) {
            final int k = i;
            Scheduler.later(2 + i * 2, () -> {
                if (!e.isAlive()) return;
                Vec3d c = Combat.center(e);
                Fx.arc(w, c.add(Abilities.R.nextGaussian() * 2.5, 3, Abilities.R.nextGaussian() * 2.5), c);
                Combat.explosionLite(p, w, c, 2.4, 2, k % 2 == 0 ? F : L);
            });
        }
        Scheduler.later(hits * 2 + 4, () -> {
            if (!Abilities.alive(p)) return;
            Vec3d c = e.isAlive() ? e.getPos() : p.getPos().add(p.getRotationVec(1f).multiply(3));
            Combat.explosion(p, w, c.add(0, 0.5, 0), 8, 40, F, 2.0, 4);
            Combat.zoom(p, 1.14f, 14);
            Combat.shake(p, 3.4f, 16);
            Abilities.loop(24, i -> {
                double ang = Abilities.R.nextDouble() * Math.PI * 2, r = Abilities.R.nextDouble() * 8;
                if (i % 2 == 0) LightningAbilities.smite(p, w, c.add(Math.cos(ang) * r, 0, Math.sin(ang) * r), 9, 3, i % 4 == 0);
            });
        });
        return true;
    }

    // =========================================================== FIRE + ICE
    /** Freeze, then break the ice with a fire punch. */
    private static boolean thermalShock(ServerPlayerEntity p, ServerWorld w) {
        LivingEntity e = Abilities.tgt(p, w, 7);
        if (e == null) return false;
        Abilities.dashAdjacent(p, w, e, I);
        p.swingHand(Hand.MAIN_HAND, true);
        Status.freezeFully(e, 80);
        Combat.sound(w, e.getPos(), SoundEvents.BLOCK_GLASS_BREAK, 1f, 1.2f);
        Scheduler.later(9, () -> {
            if (!Abilities.alive(p) || !e.isAlive()) return;
            p.swingHand(Hand.OFF_HAND, true);
            Vec3d c = Combat.center(e);
            Combat.strike(p, w, e, 22, Combat.flat(e.getPos().subtract(p.getPos())), 1.2, 0.4, F);
            Status.shatter(e);
            Combat.explosion(p, w, c, 3.8, 10, F, 1.0, 1);
            Fx.explode(w, I, c, 30, 0.45);
            Combat.shake(p, 2.4f, 8);
        });
        return true;
    }

    /** Fire meets frost: scalding steam that blinds and burns. */
    private static boolean steamBurst(ServerPlayerEntity p, ServerWorld w) {
        Vec3d o = p.getPos();
        Combat.sound(w, o, SoundEvents.BLOCK_FIRE_EXTINGUISH, 1.4f, 0.6f);
        Combat.shake(p, 1.2f, 10);
        Abilities.loop(14, i -> {
            double r = 1.5 + i * 0.5;
            for (int k = 0; k < 20; k++) {
                double ang = Abilities.R.nextDouble() * Math.PI * 2, rr = Abilities.R.nextDouble() * r;
                Fx.vel(w, "frost_mist", o.x + Math.cos(ang) * rr, o.y + 0.3, o.z + Math.sin(ang) * rr, 0, 0.12, 0);
                if (k % 3 == 0) Fx.vel(w, "ember", o.x + Math.cos(ang) * rr, o.y + 0.3, o.z + Math.sin(ang) * rr, 0, 0.2, 0);
            }
            Fx.ring(w, i % 2 == 0 ? F : I, o.add(0, 0.3, 0), r / 5.0);
            for (LivingEntity e : Combat.around(p, w, o, 7.5)) {
                if (i % 3 == 0) {
                    Combat.hurtDirect(p, w, e, 4);
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0));
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1));
                    Combat.setVel(e, e.getVelocity().add(0, 0.3, 0));
                }
            }
            if (i == 13) {
                Fx.explode(w, F, o.add(0, 1, 0), 30, 0.5);
                Fx.explode(w, I, o.add(0, 1, 0), 30, 0.5);
                for (LivingEntity e : Combat.around(p, w, o, 7.5)) {
                    Combat.hurtDirect(p, w, e, 8);
                    Combat.launch(p, w, e, Combat.center(e).subtract(o), 1.0, 0.5);
                }
            }
        });
        return true;
    }

    /** A double-helix wave of fire and ice sweeps forward. */
    private static boolean frostfireWave(ServerPlayerEntity p, ServerWorld w) {
        Vec3d o = p.getPos();
        Vec3d fwd = Combat.flat(p.getRotationVec(1f));
        if (fwd.lengthSquared() < 1.0E-4) fwd = new Vec3d(0, 0, 1);
        final Vec3d dir = fwd;
        Vec3d side = new Vec3d(-dir.z, 0, dir.x);
        Combat.sound(w, o, SoundEvents.ENTITY_BLAZE_SHOOT, 1f, 0.7f);
        Combat.sound(w, o, SoundEvents.BLOCK_GLASS_BREAK, 0.8f, 1.2f);
        Abilities.loop(18, i -> {
            double d = 1.5 + i * 0.9;
            Vec3d c = o.add(dir.multiply(d));
            for (int k = 0; k < 10; k++) {
                double ang = i * 0.7 + k * 0.63;
                double lat = Math.sin(ang) * 1.8, h = 1.0 + Math.cos(ang) * 0.9;
                Vec3d q1 = c.add(side.multiply(lat)).add(0, h, 0);
                Vec3d q2 = c.add(side.multiply(-lat)).add(0, 2.0 - h, 0);
                Fx.vel(w, "flame", q1.x, q1.y, q1.z, dir.x * 0.1, 0, dir.z * 0.1);
                Fx.vel(w, "shard", q2.x, q2.y, q2.z, dir.x * 0.1, 0, dir.z * 0.1);
            }
            Fx.ring(w, i % 2 == 0 ? F : I, c.add(0, 1, 0), 0.5);
            for (LivingEntity e : Combat.around(p, w, c.add(0, 1, 0), 3.0)) {
                Combat.hurtDirect(p, w, e, 4);
                e.setOnFireFor(4);
                Status.addFreeze(e, 1);
                Combat.launch(p, w, e, dir, 0.7, 0.3);
            }
        });
        Combat.shake(p, 1.5f, 14);
        return true;
    }

    /** ULTIMATE: freezes the whole field, then detonates it with fire. */
    private static boolean thermalApocalypse(ServerPlayerEntity p, ServerWorld w) {
        Vec3d o = p.getPos();
        Combat.zoom(p, 0.8f, 40);
        Combat.sound(w, o, SoundEvents.BLOCK_GLASS_BREAK, 1.5f, 0.5f);
        Abilities.loop(14, i -> {
            double r = 1.5 + i * 0.9;
            Fx.ring(w, I, o.add(0, 0.3, 0), r / 5.0);
            for (int k = 0; k < 30; k++) {
                double ang = k * Math.PI * 2 / 30;
                Fx.vel(w, "frost_mist", o.x + Math.cos(ang) * r, o.y + 0.3, o.z + Math.sin(ang) * r, 0, 0.05, 0);
                if (k % 3 == 0) Fx.vel(w, "shard", o.x + Math.cos(ang) * r, o.y + 0.5, o.z + Math.sin(ang) * r, 0, 0.1, 0);
            }
            for (LivingEntity e : Combat.around(p, w, o, r + 0.8)) if (!Status.isFrozen(e)) Status.freezeFully(e, 60);
        });
        Scheduler.later(26, () -> {
            if (!Abilities.alive(p)) return;
            Combat.sound(w, o, SoundEvents.ENTITY_GENERIC_EXPLODE, 2f, 0.5f);
            for (LivingEntity e : Combat.around(p, w, o, 13.5)) {
                if (Status.shatter(e)) Combat.hurtDirect(p, w, e, 22);
            }
            Combat.explosion(p, w, o.add(0, 0.5, 0), 13, 30, F, 2.2, 5);
            Abilities.loop(10, i -> {
                Fx.ring(w, i % 2 == 0 ? F : I, o.add(0, 0.4, 0), 0.8 + i * 0.2);
                Fx.burst(w, "flame", o.x, o.y + 1, o.z, 16, 6 + i * 0.5, 1.5, 6 + i * 0.5, 0.1);
            });
            Combat.zoom(p, 1.14f, 14);
            Combat.shake(p, 3.5f, 18);
        });
        return true;
    }

    // =========================================================== LIGHTNING + VOID
    /** Opens a rift, steps through and tears space where it lands. */
    private static boolean riftStep(ServerPlayerEntity p, ServerWorld w) {
        LivingEntity e = Abilities.tgt(p, w, 20);
        if (e == null) return false;
        Vec3d from = p.getPos();
        portal(w, from.add(0, 1, 0), p.getYaw(), 1.2);
        Combat.sound(w, from, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
        Vec3d to = Combat.behind(w, e);
        Combat.faceAndTeleport(p, to, e.getPos());
        Combat.trail(w, V, from, to);
        portal(w, to.add(0, 1, 0), p.getYaw(), 1.2);
        Fx.arc(w, to.add(0, 5, 0), to.add(0, 0.5, 0));
        Scheduler.later(2, () -> {
            if (!Abilities.alive(p) || !e.isAlive()) return;
            p.swingHand(Hand.MAIN_HAND, true);
            Vec3d c = Combat.center(e);
            Combat.strike(p, w, e, 13, Combat.flat(e.getPos().subtract(p.getPos())), 1.1, 0.3, L);
            Combat.hurtMagic(p, w, e, 8);
            Combat.explosionLite(p, w, c, 3.5, 5, V);
            Fx.ring(w, V, c, 1.0);
            Combat.shake(p, 1.8f, 6);
        });
        return true;
    }

    /** A rift that drags enemies in and zaps them. */
    private static boolean staticRift(ServerPlayerEntity p, ServerWorld w) {
        Vec3d c = Abilities.aimPoint(p, w, 14).add(0, 1.6, 0);
        float yaw = p.getYaw();
        Combat.sound(w, c, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.2f, 0.4f);
        Abilities.loop(40, i -> {
            Abilities.pull(p, w, c, 9, 0.3, false);
            if (i % 3 == 0) portal(w, c, yaw + i * 6, 1.4);
            VoidAbilities.implode(w, c, 5, 5);
            if (i % 4 == 0) {
                for (LivingEntity e : Combat.around(p, w, c, 9)) {
                    Fx.arc(w, c, Combat.center(e));
                    Combat.hurtDirect(p, w, e, 4);
                    Status.paralyze(e, 20);
                }
            }
            if (i == 39) {
                Fx.explode(w, V, c, 30, 0.5);
                Fx.explode(w, L, c, 30, 0.5);
                for (LivingEntity e : Combat.around(p, w, c, 6)) Combat.launch(p, w, e, Combat.center(e).subtract(c), 1.1, 0.4);
            }
        });
        return true;
    }

    /** Rifts open around the fighter and each one fires a bolt of void lightning. */
    private static boolean riftStorm(ServerPlayerEntity p, ServerWorld w) {
        Vec3d o = p.getPos();
        Combat.sound(w, o, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 1.2f, 0.6f);
        Combat.zoom(p, 0.9f, 30);
        Abilities.loop(42, i -> {
            if (i % 6 != 0) return;
            double ang = Abilities.R.nextDouble() * Math.PI * 2, r = 3 + Abilities.R.nextDouble() * 7;
            Vec3d s = o.add(Math.cos(ang) * r, 0, Math.sin(ang) * r);
            portal(w, s.add(0, 3.2, 0), (float) Math.toDegrees(ang), 1.5);
            Scheduler.later(7, () -> {
                if (!Abilities.alive(p)) return;
                LightningAbilities.smite(p, w, s, 12, 3.2, true);
                Fx.explode(w, V, s.add(0, 0.6, 0), 24, 0.35);
                Abilities.pull(p, w, s.add(0, 1, 0), 5, 0.6, false);
            });
        });
        return true;
    }

    /** ULTIMATE: teleports through up to six enemies in a blink, then breaks space around the last. */
    private static boolean spaceBreaker(ServerPlayerEntity p, ServerWorld w) {
        List<LivingEntity> list = new ArrayList<>(Combat.around(p, w, p.getPos(), 16));
        list.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(p)));
        if (list.isEmpty()) return false;
        List<LivingEntity> chain = list.subList(0, Math.min(6, list.size()));
        Combat.zoom(p, 0.85f, 10 + chain.size() * 4 + 16);
        Combat.sound(w, p.getPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.2f, 0.4f);
        int idx = 0;
        for (LivingEntity e : chain) {
            Scheduler.later(2 + idx * 4, () -> {
                if (!Abilities.alive(p) || !e.isAlive()) return;
                Vec3d from = p.getPos();
                Vec3d to = Combat.behind(w, e);
                portal(w, from.add(0, 1, 0), p.getYaw(), 1.0);
                Combat.faceAndTeleport(p, to, e.getPos());
                Combat.trail(w, L, from, to);
                p.swingHand(Hand.MAIN_HAND, true);
                Combat.strike(p, w, e, 12, Combat.flat(e.getPos().subtract(p.getPos())), 0.7, 0.4, L);
                Combat.hurtMagic(p, w, e, 5);
                Fx.arc(w, to.add(0, 4, 0), Combat.center(e));
                Fx.flash(w, V, Combat.center(e), 1.2);
                Combat.sound(w, to, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8f, 1.5f);
            });
            idx++;
        }
        final LivingEntity last = chain.get(chain.size() - 1);
        Scheduler.later(2 + chain.size() * 4 + 2, () -> {
            if (!Abilities.alive(p)) return;
            Vec3d c = Combat.center(last);
            Combat.sound(w, c, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 0.6f);
            for (int r = 0; r < 4; r++) Fx.ring(w, r % 2 == 0 ? V : L, c, 0.8 + r * 0.4);
            VoidAbilities.implode(w, c, 8, 40);
            Combat.explosion(p, w, c, 7, 30, V, 1.8, 3);
            for (LivingEntity e : Combat.around(p, w, c, 7)) Combat.hurtMagic(p, w, e, 10);
            Combat.zoom(p, 1.14f, 12);
            Combat.shake(p, 3.2f, 16);
        });
        return true;
    }

    // =========================================================== ICE + VOID
    /** A small frozen pocket of space: enemies inside cannot leave and keep freezing. */
    private static boolean stasisField(ServerPlayerEntity p, ServerWorld w) {
        Vec3d c = Abilities.aimPoint(p, w, 16);
        double radius = 6;
        Combat.sound(w, c, SoundEvents.BLOCK_GLASS_BREAK, 1.2f, 0.5f);
        Abilities.loop(160, i -> {
            Abilities.shell(w, c, radius, "frost_mist", 8, true);
            Abilities.shell(w, c, radius, "void_spark", 6, true);
            if (i % 10 == 0) Fx.ring(w, I, c.add(0, 0.3, 0), radius / 5.0);
            for (LivingEntity e : Combat.around(p, w, c, radius + 1.5)) {
                Vec3d to = c.subtract(e.getPos());
                if (to.length() > radius - 0.8) Combat.setVel(e, new Vec3d(to.x, 0, to.z).normalize().multiply(0.35).add(0, 0.1, 0));
                else Combat.setVel(e, e.getVelocity().multiply(0.4));
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 10, 3));
                if (i % 20 == 0) Status.addFreeze(e, 1);
                if (i % 20 == 10) Combat.hurtMagic(p, w, e, 3);
            }
            if (i == 159) {
                Fx.explode(w, I, c.add(0, 2, 0), 60, 0.6);
                Combat.sound(w, c, SoundEvents.BLOCK_GLASS_BREAK, 1.2f, 0.4f);
            }
        });
        return true;
    }

    /** Gravity crushes the area while the cold sets in; frozen enemies shatter at the end. */
    private static boolean cryoGravity(ServerPlayerEntity p, ServerWorld w) {
        Vec3d c = Abilities.aimPoint(p, w, 14);
        double radius = 6.5;
        Combat.sound(w, c, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 1f, 0.6f);
        Abilities.loop(46, i -> {
            if (i % 2 == 0) {
                for (int k = 0; k < 20; k++) {
                    double ang = Abilities.R.nextDouble() * Math.PI * 2, r = Abilities.R.nextDouble() * radius;
                    Fx.vel(w, k % 2 == 0 ? "shard" : "void_spark", c.x + Math.cos(ang) * r, c.y + 4 + Abilities.R.nextDouble() * 3, c.z + Math.sin(ang) * r, 0, -0.5, 0);
                }
                Fx.ring(w, k(i), c.add(0, 0.3, 0), radius / 5.0);
            }
            for (LivingEntity e : Combat.around(p, w, c, radius + 1)) {
                Combat.setVel(e, new Vec3d(e.getVelocity().x * 0.2, Math.min(e.getVelocity().y, 0) - 0.9, e.getVelocity().z * 0.2));
                if (i % 8 == 0) Status.addFreeze(e, 1);
                if (i % 4 == 0) Combat.hurtMagic(p, w, e, 2.5);
            }
        });
        Scheduler.later(48, () -> {
            for (LivingEntity e : Combat.around(p, w, c, radius + 1)) {
                if (!Status.isFrozen(e)) Status.freezeFully(e, 5);
                if (Status.shatter(e)) Combat.hurtDirect(p, w, e, 16);
            }
            Fx.explode(w, I, c.add(0, 1, 0), 40, 0.5);
            Combat.sound(w, c, SoundEvents.BLOCK_GLASS_BREAK, 1.3f, 0.5f);
            Combat.shake(p, 1.8f, 8);
        });
        return true;
    }

    private static int k(int i) {
        return i % 4 == 0 ? V : I;
    }

    /** A freezing fissure opens in the ground ahead and drags enemies into it. */
    private static boolean cryoRift(ServerPlayerEntity p, ServerWorld w) {
        Vec3d o = p.getPos();
        Vec3d fwd = Combat.flat(p.getRotationVec(1f));
        if (fwd.lengthSquared() < 1.0E-4) fwd = new Vec3d(0, 0, 1);
        final Vec3d dir = fwd;
        Vec3d side = new Vec3d(-dir.z, 0, dir.x);
        Combat.sound(w, o, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 0.9f, 0.7f);
        Abilities.loop(30, i -> {
            if (i < 16) {
                Vec3d c = o.add(dir.multiply(2 + i * 1.0));
                for (int k = -3; k <= 3; k++) {
                    Vec3d q = c.add(side.multiply(k * 0.22));
                    Fx.vel(w, "void_spark", q.x, q.y + 0.2, q.z, 0, 0.1, 0);
                    Fx.vel(w, "shard", q.x, q.y + 0.3, q.z, 0, 0.14, 0);
                }
                Fx.vel(w, "void_orb", c.x, c.y + 0.4, c.z, 0, 0.02, 0);
                IceAbilities.spike(w, c.add(side.multiply(1.6 * (i % 2 == 0 ? 1 : -1))), 2);
            }
            Vec3d end = o.add(dir.multiply(18));
            for (LivingEntity e : Combat.around(p, w, o.lerp(end, 0.5), 11)) {
                double d = segDist(e.getPos(), o, end);
                if (d > 4 || e.getPos().subtract(o).dotProduct(dir) < 0) continue;
                // pulled toward the crack
                Vec3d onLine = o.add(dir.multiply(Math.max(0, e.getPos().subtract(o).dotProduct(dir))));
                Combat.setVel(e, onLine.subtract(e.getPos()).multiply(0.25).add(0, -0.2, 0));
                if (i % 5 == 0) {
                    Combat.hurtMagic(p, w, e, 4);
                    Status.addFreeze(e, 1);
                }
            }
        });
        Combat.shake(p, 1.6f, 14);
        return true;
    }

    /** ULTIMATE: a frozen dimension. Everyone inside is frozen, floats helplessly and is highly vulnerable. */
    private static boolean frozenDimension(ServerPlayerEntity p, ServerWorld w) {
        Vec3d c = p.getPos();
        double radius = 11;
        Combat.zoom(p, 0.8f, 50);
        Combat.sound(w, c, SoundEvents.BLOCK_END_PORTAL_SPAWN, 1.5f, 0.5f);
        Combat.sound(w, c, SoundEvents.BLOCK_GLASS_BREAK, 1.5f, 0.4f);
        Abilities.loop(200, i -> {
            double grow = Math.min(1.0, i / 12.0);
            Vec3d cc = c.add(0, 1, 0);
            Abilities.shell(w, cc, radius * grow, "frost_mist", 14, true);
            Abilities.shell(w, cc, radius * grow, "void_spark", 8, true);
            Abilities.shell(w, cc, radius * grow, "shard", 3, true);
            if (i % 12 == 0) Fx.ring(w, i % 24 == 0 ? V : I, c.add(0, 0.3, 0), radius / 5.0 * grow);
            if (i % 10 == 0) {
                for (LivingEntity e : Combat.around(p, w, c, radius)) {
                    if (!Status.isFrozen(e)) Status.freezeFully(e, 40);
                    Combat.hurtMagic(p, w, e, 4);
                }
            }
            for (LivingEntity e : Combat.around(p, w, c, radius)) {
                if (Status.isFrozen(e)) Combat.setVel(e, new Vec3d(0, 0.035, 0)); // floating, helpless
            }
            if (i % 25 == 0 && i < 160) {
                double ang = Abilities.R.nextDouble() * Math.PI * 2, r = 3 + Abilities.R.nextDouble() * (radius - 4);
                IceAbilities.spike(w, c.add(Math.cos(ang) * r, 0, Math.sin(ang) * r), 3 + Abilities.R.nextInt(3));
            }
            if (i == 199) {
                Combat.sound(w, c, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.5f, 0.6f);
                for (int s = 0; s < 6; s++) Abilities.shell(w, cc, radius, "shard", 40, false);
                Fx.explode(w, I, cc, 80, 0.8);
                for (LivingEntity e : Combat.around(p, w, c, radius)) {
                    Status.shatter(e);
                    Combat.hurtDirect(p, w, e, 25);
                    Combat.launch(p, w, e, Combat.center(e).subtract(c), 1.2, 0.4);
                }
                Combat.shake(p, 3.0f, 14);
            }
        });
        return true;
    }
}
