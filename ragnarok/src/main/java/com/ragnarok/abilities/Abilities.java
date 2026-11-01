package com.ragnarok.abilities;

import com.ragnarok.combat.Combat;
import com.ragnarok.core.Ability;
import com.ragnarok.core.Elem;
import com.ragnarok.core.Scheduler;
import com.ragnarok.particle.Fx;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Random;
import java.util.function.IntConsumer;

/** Dispatcher + helpers shared by the element ability classes. */
public final class Abilities {
    private Abilities() {}

    static final Random R = new Random();

    /** Runs an ability. Returns false (nothing spent) when it could not start, e.g. no target. */
    public static boolean run(Ability a, ServerPlayerEntity p, ServerWorld w, int level) {
        return switch (a.style) {
            case 0 -> FireAbilities.run(a, p, w);
            case 1 -> LightningAbilities.run(a, p, w);
            case 2 -> IceAbilities.run(a, p, w);
            case 3 -> VoidAbilities.run(a, p, w);
            default -> ComboAbilities.run(a, p, w);
        };
    }

    // ------------------------------------------------------------ helpers
    static void loop(int n, IntConsumer step) {
        for (int i = 0; i < n; i++) {
            final int k = i;
            Scheduler.later(i + 1, () -> step.accept(k));
        }
    }

    static boolean alive(ServerPlayerEntity p) {
        return !p.isRemoved() && p.isAlive();
    }

    static LivingEntity tgt(ServerPlayerEntity p, ServerWorld w, double range) {
        return Combat.target(p, w, range);
    }

    /** Where the crosshair points: an entity, a block, or a point in the air. */
    static Vec3d aimPoint(ServerPlayerEntity p, ServerWorld w, double range) {
        LivingEntity e = Combat.target(p, w, range);
        if (e != null) return e.getPos();
        HitResult h = p.raycast(range * Combat.cfg().rangeMultiplier, 0f, false);
        if (h.getType() == HitResult.Type.BLOCK) return h.getPos();
        return p.getEyePos().add(p.getRotationVec(1f).multiply(range));
    }

    /** Last free position along a straight dash (stops before walls). */
    static Vec3d safe(ServerPlayerEntity p, ServerWorld w, Vec3d from, Vec3d dest) {
        Vec3d a = from.add(0, 0.9, 0), b = dest.add(0, 0.9, 0);
        BlockHitResult r = w.raycast(new RaycastContext(a, b, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
        if (r.getType() == HitResult.Type.MISS) return dest;
        Vec3d dir = b.subtract(a).normalize();
        return r.getPos().subtract(dir.multiply(0.6)).subtract(0, 0.9, 0);
    }

    /** Streak next to a target (leaves a luminous trail). */
    static void dashAdjacent(ServerPlayerEntity p, ServerWorld w, LivingEntity e, int elem) {
        Vec3d from = p.getPos(), to = e.getPos();
        double d = from.distanceTo(to);
        if (d <= 2.4) return;
        Vec3d dir = to.subtract(from).normalize();
        Vec3d dest = safe(p, w, from, to.subtract(dir.multiply(1.6)));
        Combat.faceAndTeleport(p, dest, to);
        Combat.trail(w, elem, from, dest);
        Fx.flash(w, elem, from.add(0, 1, 0), 0.9);
        Fx.flash(w, elem, dest.add(0, 1, 0), 0.9);
    }

    /** Dash to a point (no target). */
    static Vec3d dashTo(ServerPlayerEntity p, ServerWorld w, Vec3d dest, int elem) {
        Vec3d from = p.getPos();
        Vec3d s = safe(p, w, from, dest);
        p.networkHandler.requestTeleport(s.x, s.y, s.z, p.getYaw(), p.getPitch());
        p.fallDistance = 0;
        Combat.trail(w, elem, from, s);
        Fx.flash(w, elem, s.add(0, 1, 0), 0.9);
        return s;
    }

    /** Points on a sphere shell (domes, singularities). */
    static void shell(ServerWorld w, Vec3d c, double radius, String type, int count, boolean upperHalf) {
        for (int i = 0; i < count; i++) {
            Vec3d d = new Vec3d(R.nextGaussian(), R.nextGaussian(), R.nextGaussian());
            if (d.lengthSquared() < 1.0E-4) continue;
            d = d.normalize();
            if (upperHalf && d.y < 0) d = new Vec3d(d.x, -d.y, d.z);
            Vec3d q = c.add(d.multiply(radius));
            Fx.vel(w, type, q.x, q.y, q.z, 0, 0, 0);
        }
    }

    /** Pulls living things, items and projectiles toward a point. */
    static void pull(ServerPlayerEntity p, ServerWorld w, Vec3d c, double radius, double strength, boolean extras) {
        for (LivingEntity e : Combat.around(p, w, c, radius)) {
            Vec3d to = c.subtract(Combat.center(e));
            double d = to.length();
            if (d < 0.9) {
                Combat.setVel(e, e.getVelocity().multiply(0.3));
                continue;
            }
            Combat.setVel(e, e.getVelocity().multiply(0.55).add(to.normalize().multiply(strength * (1 - d / radius * 0.5))));
        }
        if (!extras) return;
        Box box = new Box(c, c).expand(radius);
        for (ItemEntity it : w.getEntitiesByClass(ItemEntity.class, box, x -> true)) {
            Vec3d to = c.subtract(it.getPos());
            if (to.length() > 0.6) it.setVelocity(to.normalize().multiply(Math.min(0.7, strength)));
        }
        for (ProjectileEntity pr : w.getEntitiesByClass(ProjectileEntity.class, box, x -> true)) {
            Vec3d to = c.subtract(pr.getPos());
            if (to.length() > 0.6) pr.setVelocity(pr.getVelocity().multiply(0.5).add(to.normalize().multiply(strength)));
        }
    }

    /** One tick of a ground-hugging expanding ring (quake style). */
    static void groundRing(ServerPlayerEntity p, ServerWorld w, Vec3d o, double r, int elem, double dmg, double up, double kb, boolean ignite) {
        int pts = Math.max(12, (int) (r * 7));
        for (int k = 0; k < pts; k++) {
            double ang = k * 2 * Math.PI / pts;
            double x = o.x + Math.cos(ang) * r, z = o.z + Math.sin(ang) * r;
            Fx.vel(w, Elem.BODY[elem], x, o.y + 0.1, z, 0, 0.05, 0);
            if (k % 3 == 0) Fx.vel(w, Elem.SPARK[elem], x, o.y + 0.3, z, 0, 0.12, 0);
        }
        Fx.ring(w, elem, o.add(0, 0.2, 0), r / 5.0);
        for (LivingEntity e : Combat.around(p, w, o, r + 1.2)) {
            double d = Math.hypot(e.getX() - o.x, e.getZ() - o.z);
            if (d > r - 1.4 && d <= r + 0.8) {
                Combat.hurtDirect(p, w, e, dmg);
                Combat.launch(p, w, e, new Vec3d(e.getX() - o.x, 0, e.getZ() - o.z), kb, up);
                if (ignite) e.setOnFireFor(5);
                Combat.elementHit(p, w, e, elem, 0.8);
            }
        }
    }
}
