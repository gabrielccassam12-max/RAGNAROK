package com.ragnarok.particle;

import com.ragnarok.core.Elem;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/** Server-side helpers that spawn the mod's custom particles. */
public final class Fx {
    private Fx() {}

    private static final Random R = new Random();

    private static ParticleEffect t(String name) {
        return ModParticles.get(name);
    }

    /** Cloud of particles. */
    public static void burst(ServerWorld w, String type, double x, double y, double z, int count,
                             double dx, double dy, double dz, double speed) {
        w.spawnParticles(t(type), x, y, z, count, dx, dy, dz, speed);
    }

    public static void burst(ServerWorld w, String type, Vec3d p, int count, double spread, double speed) {
        burst(w, type, p.x, p.y, p.z, count, spread, spread, spread, speed);
    }

    /** One particle with an exact velocity. */
    public static void vel(ServerWorld w, String type, double x, double y, double z, double vx, double vy, double vz) {
        w.spawnParticles(t(type), x, y, z, 0, vx, vy, vz, 1.0);
    }

    /** Expanding shockwave ring; mul is the size multiplier (final width is roughly 9 * mul blocks). */
    public static void ring(ServerWorld w, int elem, Vec3d p, double mul) {
        w.spawnParticles(t(Elem.RING[elem]), p.x, p.y, p.z, 0, mul, 0, 0, 1.0);
    }

    /** Star-burst impact flash. */
    public static void flash(ServerWorld w, int elem, Vec3d p, double mul) {
        w.spawnParticles(t(Elem.FLASH[elem]), p.x, p.y, p.z, 0, mul, 0, 0, 1.0);
    }

    /** The "signature" particle of the element thrown outwards in all directions. */
    public static void explode(ServerWorld w, int elem, Vec3d p, int count, double speed) {
        for (int i = 0; i < count; i++) {
            Vec3d d = new Vec3d(R.nextGaussian(), R.nextGaussian() * 0.8, R.nextGaussian()).normalize().multiply(speed * (0.4 + R.nextDouble() * 0.8));
            vel(w, Elem.BODY[elem], p.x, p.y, p.z, d.x, d.y, d.z);
            if (i % 2 == 0) vel(w, Elem.SPARK[elem], p.x, p.y, p.z, d.x * 1.4, d.y * 1.4, d.z * 1.4);
        }
        flash(w, elem, p, 1.2 + count / 40.0);
        ring(w, elem, p, 0.6 + count / 60.0);
    }

    /** Small impact: flash + sparks (used by every punch). */
    public static void hit(ServerWorld w, int elem, Vec3d p, double power) {
        flash(w, elem, p, 0.5 + power * 0.5);
        burst(w, Elem.SPARK[elem], p, 6 + (int) (power * 6), 0.25, 0.18);
        if (elem == Elem.FIRE) burst(w, "smoke", p, 3, 0.2, 0.02);
    }

    /** Glowing aura around an entity body. */
    public static void aura(ServerWorld w, int elem, double x, double y, double z, double width, double height, int count) {
        for (int i = 0; i < count; i++) {
            double a = R.nextDouble() * Math.PI * 2;
            double r = width * (0.5 + R.nextDouble() * 0.5);
            double yy = y + R.nextDouble() * height;
            double rise = 0.02 + R.nextDouble() * 0.05;
            vel(w, Elem.GLOW[elem], x + Math.cos(a) * r, yy, z + Math.sin(a) * r, 0, rise, 0);
            if (R.nextInt(3) == 0) vel(w, Elem.BODY[elem], x + Math.cos(a) * r, y + R.nextDouble() * 0.6, z + Math.sin(a) * r, 0, 0.02, 0);
        }
    }

    /** A line of particles between two points (arcs, beams, trails). */
    public static void line(ServerWorld w, String type, Vec3d a, Vec3d b, double step) {
        Vec3d d = b.subtract(a);
        double len = d.length();
        if (len < 0.01) return;
        Vec3d n = d.multiply(1.0 / len);
        for (double s = 0; s <= len; s += step) {
            Vec3d q = a.add(n.multiply(s));
            vel(w, type, q.x, q.y, q.z, 0, 0, 0);
        }
    }

    /** Jagged lightning arc between two points. */
    public static void arc(ServerWorld w, Vec3d a, Vec3d b) {
        Vec3d d = b.subtract(a);
        int seg = Math.max(2, (int) (d.length() / 1.2));
        Vec3d prev = a;
        for (int i = 1; i <= seg; i++) {
            Vec3d q = a.add(d.multiply(i / (double) seg));
            if (i < seg) q = q.add(R.nextGaussian() * 0.35, R.nextGaussian() * 0.35, R.nextGaussian() * 0.35);
            vel(w, "bolt", (prev.x + q.x) / 2, (prev.y + q.y) / 2, (prev.z + q.z) / 2, 0, 0, 0);
            line(w, "spark_e", prev, q, 0.5);
            prev = q;
        }
    }
}
