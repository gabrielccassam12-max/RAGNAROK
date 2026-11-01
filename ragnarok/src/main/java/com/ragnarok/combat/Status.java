package com.ragnarok.combat;

import com.ragnarok.core.Elem;
import com.ragnarok.particle.Fx;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Frozen / paralysed targets. Uses vanilla effects only (nothing that could stay stuck in a save file). */
public final class Status {
    private Status() {}

    private record Entry(LivingEntity e, long until) {}

    private static final Map<UUID, Entry> FROZEN = new HashMap<>();
    private static final Map<UUID, Entry> PARA = new HashMap<>();
    private static final Map<UUID, long[]> LAYERS = new HashMap<>(); // {layers, expire}

    private static long now(LivingEntity e) {
        return e.getWorld().getTime();
    }

    public static boolean isFrozen(LivingEntity e) {
        Entry en = FROZEN.get(e.getUuid());
        return en != null && now(e) < en.until;
    }

    public static boolean isParalyzed(LivingEntity e) {
        Entry en = PARA.get(e.getUuid());
        return en != null && now(e) < en.until;
    }

    public static int layers(LivingEntity e) {
        long[] l = LAYERS.get(e.getUuid());
        return l == null || now(e) > l[1] ? 0 : (int) l[0];
    }

    /** Adds layers of frost; at 4 layers the target is frozen solid. */
    public static void addFreeze(LivingEntity e, int add) {
        long now = now(e);
        long[] l = LAYERS.get(e.getUuid());
        int layers = (l == null || now > l[1] ? 0 : (int) l[0]) + add;
        if (layers >= 4) {
            LAYERS.remove(e.getUuid());
            freezeFully(e, 90);
            return;
        }
        LAYERS.put(e.getUuid(), new long[]{layers, now + 100});
        e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, layers * 2 - 1));
        e.setFrozenTicks(Math.min(e.getMinFreezeDamageTicks() + 20, layers * 45));
        if (e.getWorld() instanceof ServerWorld w) {
            Fx.burst(w, "frost_mist", e.getX(), e.getBodyY(0.5), e.getZ(), 8, 0.35, 0.5, 0.35, 0.03);
            Fx.burst(w, "shard", e.getX(), e.getBodyY(0.5), e.getZ(), 3, 0.3, 0.4, 0.3, 0.05);
        }
    }

    public static void freezeFully(LivingEntity e, int ticks) {
        long now = now(e);
        FROZEN.put(e.getUuid(), new Entry(e, now + ticks));
        e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ticks, 9));
        e.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, ticks, 2));
        e.setFrozenTicks(e.getMinFreezeDamageTicks() + 40);
        if (e.getWorld() instanceof ServerWorld w) {
            Vec3d c = new Vec3d(e.getX(), e.getBodyY(0.5), e.getZ());
            Fx.flash(w, Elem.ICE, c, 1.0);
            Fx.burst(w, "shard", c, 14, 0.5, 0.12);
            Fx.burst(w, "frost_mist", c, 12, 0.5, 0.05);
        }
    }

    public static void paralyze(LivingEntity e, int ticks) {
        long until = now(e) + ticks;
        Entry old = PARA.get(e.getUuid());
        if (old != null && old.until > until) return;
        PARA.put(e.getUuid(), new Entry(e, until));
        e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ticks, 9));
    }

    /** Breaks the ice: returns true if the target was frozen. */
    public static boolean shatter(LivingEntity e) {
        if (!isFrozen(e)) return false;
        FROZEN.remove(e.getUuid());
        e.setFrozenTicks(0);
        e.removeStatusEffect(StatusEffects.SLOWNESS);
        e.removeStatusEffect(StatusEffects.WEAKNESS);
        if (e.getWorld() instanceof ServerWorld w) {
            Vec3d c = new Vec3d(e.getX(), e.getBodyY(0.5), e.getZ());
            Fx.explode(w, Elem.ICE, c, 30, 0.45);
        }
        return true;
    }

    public static void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Entry>> it = FROZEN.entrySet().iterator();
        while (it.hasNext()) {
            Entry en = it.next().getValue();
            LivingEntity e = en.e;
            if (e.isRemoved() || !e.isAlive() || now(e) >= en.until) {
                it.remove();
                continue;
            }
            if (e.getWorld() instanceof ServerWorld w && w.getTime() % 4 == 0) {
                Fx.burst(w, "frost_mist", e.getX(), e.getBodyY(0.5), e.getZ(), 2, 0.4, 0.6, 0.4, 0.01);
                if (w.getTime() % 8 == 0) Fx.burst(w, "shard", e.getX(), e.getBodyY(0.6), e.getZ(), 1, 0.4, 0.5, 0.4, 0.01);
            }
            if (!(e instanceof PlayerEntity) && e.isOnGround()) {
                Vec3d v = e.getVelocity();
                e.setVelocity(0, Math.min(v.y, 0), 0);
            }
        }
        it = PARA.entrySet().iterator();
        while (it.hasNext()) {
            Entry en = it.next().getValue();
            LivingEntity e = en.e;
            if (e.isRemoved() || !e.isAlive() || now(e) >= en.until) {
                it.remove();
                continue;
            }
            if (e.getWorld() instanceof ServerWorld w && w.getTime() % 3 == 0) {
                Vec3d c = new Vec3d(e.getX(), e.getBodyY(0.5), e.getZ());
                Fx.arc(w, c.add(0, 0.7, 0), c.add((w.random.nextDouble() - 0.5) * 1.2, -0.7, (w.random.nextDouble() - 0.5) * 1.2));
            }
            if (!(e instanceof ServerPlayerEntity) && e.isOnGround()) {
                Vec3d v = e.getVelocity();
                e.setVelocity(0, Math.min(v.y, 0), 0);
            }
        }
        LAYERS.values().removeIf(l -> l[1] < 0);
    }
}
