package com.ragnarok.core;

import com.ragnarok.combat.Combat;
import com.ragnarok.combat.Status;
import com.ragnarok.particle.Fx;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/** Transformations: DEMONIC OVERDRIVE (fire), DIVINE OVERLOAD (lightning), ABSOLUTE FORM (ice), VOID ASCENSION (void). */
public final class Forms {
    private Forms() {}

    private static final Random R = new Random();

    /** Element of the active transformation, or -1. */
    public static int active(ServerPlayerEntity p) {
        PState s = Manager.st(p);
        return s.formElem >= 0 && s.formEnd > p.getWorld().getTime() ? s.formElem : -1;
    }

    public static double damageBonus(ServerPlayerEntity p) {
        return active(p) >= 0 ? 1.35 : 1.0;
    }

    /** Cooldown factor: forms recharge faster; Void Ascension makes teleports almost instant. */
    public static double cooldownFactor(ServerPlayerEntity p, Ability a) {
        int f = active(p);
        if (f < 0) return 1.0;
        boolean teleport = a == Ability.PHANTOM_STRIKE || a == Ability.FLASH_STEP || a == Ability.RIFT_STEP || a == Ability.SPACE_BREAKER;
        return f == Elem.VOID && teleport ? 0.4 : 0.8;
    }

    public static void toggle(ServerPlayerEntity p) {
        PlayerData d = Manager.get(p);
        PState s = Manager.st(p);
        ServerWorld w = (ServerWorld) p.getWorld();
        if (!d.any()) return;
        if (active(p) >= 0) {
            end(p, w, false);
            return;
        }
        if (d.style() >= 4) {
            p.sendMessage(Text.translatable("message.ragnarok.form_style").formatted(Formatting.YELLOW), true);
            return;
        }
        int elem = d.style();
        if (d.level(elem) < 2) {
            p.sendMessage(Text.translatable("message.ragnarok.form_locked", Ability.levelName(2)).formatted(Formatting.RED), true);
            return;
        }
        int cost = Manager.cfg().transformCost;
        if (d.essence() < cost) {
            p.sendMessage(Text.translatable("message.ragnarok.low", cost).formatted(Formatting.RED), true);
            return;
        }
        Manager.addEssence(p, -cost);
        long now = w.getTime();
        s.formElem = elem;
        s.formStart = now;
        s.formEnd = now + Manager.cfg().transformTicks;
        s.dirty = true;
        awaken(p, w, elem);
        p.sendMessage(Text.translatable("form.ragnarok." + elem).formatted(Formatting.GOLD, Formatting.BOLD), true);
    }

    /** Cinematic activation: rising rings, converging particles, a flash, camera zoom. */
    private static void awaken(ServerPlayerEntity p, ServerWorld w, int elem) {
        Combat.zoom(p, 0.8f, 26);
        Combat.shake(p, 1.2f, 20);
        Object snd = switch (elem) {
            case Elem.FIRE -> SoundEvents.ENTITY_WITHER_SPAWN;
            case Elem.LIGHTNING -> SoundEvents.ITEM_TRIDENT_THUNDER;
            case Elem.ICE -> SoundEvents.BLOCK_GLASS_BREAK;
            default -> SoundEvents.BLOCK_END_PORTAL_SPAWN;
        };
        Combat.sound(w, p.getPos(), snd, 1.2f, 0.8f);
        for (int i = 0; i < 14; i++) {
            final int k = i;
            Scheduler.later(1 + i * 2, () -> {
                if (p.isRemoved()) return;
                Vec3d c = p.getPos().add(0, k * 0.14, 0);
                Fx.ring(w, elem, c, 0.35 + k * 0.03);
                Fx.aura(w, elem, p.getX(), p.getY(), p.getZ(), 1.2, 2.0, 5);
                for (int j = 0; j < 6; j++) {
                    double a = R.nextDouble() * Math.PI * 2;
                    double r = 3.0;
                    Fx.vel(w, Elem.SPARK[elem], p.getX() + Math.cos(a) * r, p.getY() + 0.2 + R.nextDouble() * 1.6, p.getZ() + Math.sin(a) * r,
                            -Math.cos(a) * 0.3, 0.02, -Math.sin(a) * 0.3);
                }
            });
        }
        Scheduler.later(30, () -> {
            if (p.isRemoved()) return;
            Vec3d c = p.getPos().add(0, 1, 0);
            Fx.explode(w, elem, c, 50, 0.5);
            Combat.sound(w, c, SoundEvents.ENTITY_GENERIC_EXPLODE, 0.8f, 1.1f);
            Combat.shake(p, 2.2f, 8);
        });
    }

    /** Cinematic ending. */
    public static void end(ServerPlayerEntity p, ServerWorld w, boolean silent) {
        PState s = Manager.st(p);
        int elem = s.formElem;
        s.formElem = -1;
        s.formEnd = 0;
        s.dirty = true;
        if (elem < 0) return;
        p.removeStatusEffect(StatusEffects.STRENGTH);
        p.removeStatusEffect(StatusEffects.SPEED);
        p.removeStatusEffect(StatusEffects.HASTE);
        p.removeStatusEffect(StatusEffects.JUMP_BOOST);
        p.removeStatusEffect(StatusEffects.RESISTANCE);
        p.removeStatusEffect(StatusEffects.FIRE_RESISTANCE);
        p.removeStatusEffect(StatusEffects.SLOW_FALLING);
        if (silent) return;
        Vec3d c = p.getPos().add(0, 1, 0);
        for (int i = 0; i < 4; i++) Fx.ring(w, elem, p.getPos().add(0, 0.3 + i * 0.4, 0), 0.5 + i * 0.15);
        Fx.explode(w, elem, c, 36, 0.35);
        Combat.sound(w, c, SoundEvents.BLOCK_BEACON_DEACTIVATE, 1f, 0.7f);
        Combat.zoom(p, 1.08f, 10);
    }

    private static void eff(ServerPlayerEntity p, net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.effect.StatusEffect> e, int amp) {
        p.addStatusEffect(new StatusEffectInstance(e, 50, amp, true, false, true));
    }

    public static void tick(ServerPlayerEntity p, PState s, long now) {
        if (s.formElem < 0) return;
        ServerWorld w = (ServerWorld) p.getWorld();
        if (!p.isAlive()) {
            end(p, w, true);
            return;
        }
        if (now >= s.formEnd) {
            end(p, w, false);
            return;
        }
        int elem = s.formElem;
        if (now % 20 == 0) {
            switch (elem) {
                case Elem.FIRE -> {
                    eff(p, StatusEffects.STRENGTH, 1);
                    eff(p, StatusEffects.SPEED, 1);
                    eff(p, StatusEffects.FIRE_RESISTANCE, 0);
                    eff(p, StatusEffects.RESISTANCE, 0);
                }
                case Elem.LIGHTNING -> {
                    eff(p, StatusEffects.SPEED, 3);
                    eff(p, StatusEffects.HASTE, 1);
                    eff(p, StatusEffects.JUMP_BOOST, 1);
                }
                case Elem.ICE -> {
                    eff(p, StatusEffects.RESISTANCE, 1);
                    eff(p, StatusEffects.STRENGTH, 0);
                }
                default -> {
                    eff(p, StatusEffects.SLOW_FALLING, 0);
                    eff(p, StatusEffects.JUMP_BOOST, 2);
                    eff(p, StatusEffects.SPEED, 1);
                }
            }
        }
        // glowing eyes (two bright sparks where the eyes are) + aura
        if (now % 2 == 0) {
            double yaw = Math.toRadians(p.getYaw());
            Vec3d fwd = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw));
            Vec3d right = new Vec3d(-Math.cos(yaw), 0, -Math.sin(yaw));
            Vec3d eye = p.getEyePos().add(fwd.multiply(0.27));
            for (int sd = -1; sd <= 1; sd += 2) {
                Vec3d e = eye.add(right.multiply(0.14 * sd));
                Fx.vel(w, Elem.SPARK[elem], e.x, e.y, e.z, 0, 0, 0);
            }
            Fx.aura(w, elem, p.getX(), p.getY(), p.getZ(), 0.55, 1.9, 2);
        }
        switch (elem) {
            case Elem.FIRE -> {
                // black flames on the arms
                if (now % 3 == 0) {
                    double yaw = Math.toRadians(p.getYaw());
                    Vec3d right = new Vec3d(-Math.cos(yaw), 0, -Math.sin(yaw));
                    for (int sd = -1; sd <= 1; sd += 2) {
                        Vec3d a = p.getPos().add(right.multiply(0.45 * sd)).add(0, 1.15, 0);
                        Fx.vel(w, "flame", a.x, a.y, a.z, 0, 0.02, 0);
                    }
                }
            }
            case Elem.LIGHTNING -> {
                Vec3d v = p.getVelocity();
                if (now % 3 == 0 && Math.hypot(v.x, v.z) > 0.18) {
                    // afterimage: a silhouette of light left where we were
                    for (int i = 0; i < 6; i++) Fx.vel(w, Elem.GLOW[1], p.getX(), p.getY() + 0.1 + i * 0.32, p.getZ(), 0, 0, 0);
                    Fx.vel(w, "bolt", p.getX(), p.getY() + 0.9, p.getZ(), 0, 0, 0);
                }
                if (now % 8 == 0) {
                    Vec3d c = p.getPos().add(0, 1, 0);
                    Fx.arc(w, c.add(R.nextGaussian() * 0.3, 0.7, R.nextGaussian() * 0.3), c.add(R.nextGaussian() * 0.9, -0.8, R.nextGaussian() * 0.9));
                }
            }
            case Elem.ICE -> {
                p.setFrozenTicks(0); // immune to freezing
                if (now % 20 == 0) {
                    Vec3d c = p.getPos();
                    for (LivingEntity e : Combat.around(p, w, c, 3.6)) Status.addFreeze(e, 1);
                    Fx.ring(w, Elem.ICE, c.add(0, 0.2, 0), 0.6);
                }
                // ice structures grow around the fighter
                if (now % 40 == 0 && Manager.cfg().allowBlockDestruction) {
                    for (int n = 0; n < 2; n++) {
                        BlockPos base = p.getBlockPos().add(R.nextInt(9) - 4, 0, R.nextInt(9) - 4);
                        for (int dy = 2; dy >= -3; dy--) {
                            BlockPos q = base.up(dy);
                            if (TempBlocks.replaceable(w.getBlockState(q)) && !TempBlocks.replaceable(w.getBlockState(q.down()))) {
                                int h = 2 + R.nextInt(3);
                                for (int i = 0; i < h; i++) {
                                    if (TempBlocks.place(w, q.up(i), Blocks.PACKED_ICE.getDefaultState(), 240))
                                        Fx.burst(w, "shard", q.getX() + 0.5, q.getY() + i + 0.5, q.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.05);
                                }
                                break;
                            }
                        }
                    }
                }
            }
            default -> {
                // levitation: hover in the air unless sneaking
                Vec3d v = p.getVelocity();
                if (!p.isOnGround() && !p.isSneaking() && v.y < 0 && now % 2 == 0) {
                    Combat.setVel(p, new Vec3d(v.x, Math.max(v.y, -0.02), v.z));
                    p.fallDistance = 0;
                }
                // gravity manipulation: things nearby are drawn in
                if (now % 4 == 0) {
                    Vec3d c = p.getPos().add(0, 1, 0);
                    for (LivingEntity e : Combat.around(p, w, c, 6)) {
                        Vec3d to = c.subtract(Combat.center(e));
                        if (to.length() > 1.8) e.addVelocity(to.normalize().multiply(0.07).x, 0.0, to.normalize().multiply(0.07).z);
                        e.velocityModified = true;
                    }
                }
                if (now % 6 == 0) {
                    double a = now * 0.4;
                    Fx.vel(w, "void_orb", p.getX() + Math.cos(a) * 1.0, p.getY() + 1.0, p.getZ() + Math.sin(a) * 1.0, 0, 0, 0);
                }
            }
        }
    }
}
