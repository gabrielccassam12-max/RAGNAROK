package com.ragnarok.combat;

import com.ragnarok.config.RagnarokConfig;
import com.ragnarok.core.Elem;
import com.ragnarok.core.Forms;
import com.ragnarok.core.Manager;
import com.ragnarok.core.PState;
import com.ragnarok.core.Scheduler;
import com.ragnarok.core.TempBlocks;
import com.ragnarok.net.FxPayload;
import com.ragnarok.particle.Fx;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Random;

/** Melee engine: punches, combos, impact physics, explosions, craters. Every ability is built from these pieces. */
public final class Combat {
    private Combat() {}

    private static final Random R = new Random();

    public static RagnarokConfig cfg() {
        return RagnarokConfig.INSTANCE;
    }

    // ------------------------------------------------------------ sound & camera
    public static void sound(ServerWorld w, BlockPos pos, Object snd, float vol, float pitch) {
        SoundEvent ev = null;
        if (snd instanceof SoundEvent s) ev = s;
        else if (snd instanceof RegistryEntry<?> r && r.value() instanceof SoundEvent s) ev = s;
        if (ev != null) w.playSound(null, pos, ev, SoundCategory.PLAYERS, vol, pitch);
    }

    public static void sound(ServerWorld w, Vec3d pos, Object snd, float vol, float pitch) {
        sound(w, BlockPos.ofFloored(pos), snd, vol, pitch);
    }

    public static void shake(ServerPlayerEntity p, float degrees, int ticks) {
        ServerPlayNetworking.send(p, new FxPayload(FxPayload.SHAKE, degrees, ticks));
    }

    public static void zoom(ServerPlayerEntity p, float factor, int ticks) {
        ServerPlayNetworking.send(p, new FxPayload(FxPayload.ZOOM, factor, ticks));
    }

    // ------------------------------------------------------------ targeting
    public static List<LivingEntity> near(ServerPlayerEntity p, ServerWorld w, Box box) {
        return w.getEntitiesByClass(LivingEntity.class, box, e -> e != p && e.isAlive() && !e.isSpectator()
                && !(e instanceof ArmorStandEntity) && (cfg().allowPvp || !(e instanceof PlayerEntity)));
    }

    public static List<LivingEntity> around(ServerPlayerEntity p, ServerWorld w, Vec3d c, double r) {
        return near(p, w, new Box(c, c).expand(r)).stream()
                .filter(e -> e.getPos().add(0, e.getHeight() / 2, 0).distanceTo(c) <= r).toList();
    }

    /** The entity under the crosshair, or the nearest one inside a narrow cone in front of the player. */
    public static LivingEntity target(ServerPlayerEntity p, ServerWorld w, double range) {
        range *= cfg().rangeMultiplier;
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVec(1f);
        Vec3d end = eye.add(look.multiply(range));
        Box box = p.getBoundingBox().stretch(look.multiply(range)).expand(1.0);
        EntityHitResult r = ProjectileUtil.raycast(p, eye, end, box,
                e -> e instanceof LivingEntity && e != p && e.isAlive() && !e.isSpectator()
                        && !(e instanceof ArmorStandEntity) && (cfg().allowPvp || !(e instanceof PlayerEntity)),
                range * range);
        if (r != null && r.getEntity() instanceof LivingEntity le) return le;
        LivingEntity best = null;
        double bd = 1e9;
        for (LivingEntity e : near(p, w, box)) {
            Vec3d to = e.getPos().add(0, e.getHeight() / 2, 0).subtract(eye);
            double d = to.length();
            if (d > range || d < 0.01) continue;
            if (to.normalize().dotProduct(look) < 0.82) continue;
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    public static Vec3d center(Entity e) {
        return new Vec3d(e.getX(), e.getBodyY(0.5), e.getZ());
    }

    public static Vec3d flat(Vec3d v) {
        Vec3d f = new Vec3d(v.x, 0, v.z);
        return f.lengthSquared() < 1.0E-6 ? Vec3d.ZERO : f.normalize();
    }

    /** The element that flavours the player's basic attacks (combination styles alternate between their two). */
    public static int styleElem(ServerPlayerEntity p) {
        int style = Manager.get(p).style();
        if (style < 4) return style;
        PState s = Manager.st(p);
        s.altElem ^= 1;
        return Elem.COMBO[style - 4][s.altElem];
    }

    // ------------------------------------------------------------ physics
    public static void setVel(Entity e, Vec3d v) {
        e.setVelocity(v);
        e.velocityModified = true;
        if (e instanceof ServerPlayerEntity sp) sp.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(sp));
    }

    public static double mass(LivingEntity e) {
        double vol = e.getWidth() * e.getWidth() * e.getHeight();
        return Math.max(0.4, Math.min(8.0, vol / 0.648));
    }

    /** Knock-back proportional to force and inversely to mass; watches for a wall slam. */
    public static void launch(ServerPlayerEntity p, ServerWorld w, LivingEntity e, Vec3d dir, double force, double up) {
        double m = mass(e);
        double f = force / Math.pow(m, 0.6);
        Vec3d d = dir.lengthSquared() < 1.0E-6 ? Vec3d.ZERO : dir.normalize();
        Vec3d v = new Vec3d(d.x * f, d.y * f + up / Math.pow(m, 0.4), d.z * f);
        setVel(e, v);
        double speed = Math.hypot(v.x, v.z);
        if (speed > 0.5) slamWatch(p, w, e, speed);
    }

    private static void slamWatch(ServerPlayerEntity p, ServerWorld w, LivingEntity e, double startSpeed) {
        double[] prev = {startSpeed};
        boolean[] done = {false};
        for (int i = 2; i <= 14; i++) {
            Scheduler.later(i, () -> {
                if (done[0] || e.isRemoved() || !e.isAlive()) return;
                if (e.horizontalCollision && prev[0] > 0.45) {
                    done[0] = true;
                    double dmg = Math.min(26, prev[0] * 9);
                    Vec3d c = center(e);
                    hurtDirect(p, w, e, dmg);
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 4));
                    Fx.hit(w, styleElem(p), c, 1.6);
                    Fx.burst(w, "smoke", c, 8, 0.4, 0.04);
                    BlockPos bp = e.getBlockPos();
                    BlockState st = w.getBlockState(bp.down());
                    w.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, st), c.x, c.y, c.z, 14, 0.4, 0.5, 0.4, 0.2);
                    sound(w, c, SoundEvents.BLOCK_ANVIL_LAND, 0.7f, 0.6f);
                    shake(p, 1.2f, 6);
                    return;
                }
                prev[0] = Math.hypot(e.getVelocity().x, e.getVelocity().z);
            });
        }
    }

    // ------------------------------------------------------------ damage
    public static double bonus(ServerPlayerEntity p) {
        return cfg().damageMultiplier * Manager.st(p).comboMult() * Forms.damageBonus(p);
    }

    /** Raw damage (already scaled by config / combo / form), ignoring hurt-time so barrages can chain. */
    public static boolean hurtDirect(ServerPlayerEntity p, ServerWorld w, LivingEntity e, double amount) {
        e.timeUntilRegen = 0;
        return e.damage(w.getDamageSources().playerAttack(p), (float) (amount * bonus(p)));
    }

    /** Damage that ignores armour (void energy). */
    public static boolean hurtMagic(ServerPlayerEntity p, ServerWorld w, LivingEntity e, double amount) {
        e.timeUntilRegen = 0;
        return e.damage(w.getDamageSources().indirectMagic(p, p), (float) (amount * bonus(p)));
    }

    /** One full hit: damage + launch + element + effects + Essence/XP/combo bookkeeping. */
    public static boolean strike(ServerPlayerEntity p, ServerWorld w, LivingEntity e, double dmg, Vec3d dir,
                                 double force, double up, int elem) {
        PState s = Manager.st(p);
        double mult = Status.isFrozen(e) ? 1.5 : 1.0;
        boolean ok = hurtDirect(p, w, e, dmg * mult);
        if (!ok && e.isAlive()) return false;
        long now = w.getTime();
        s.combo = now - s.lastHit > 60 ? 1 : s.combo + 1;
        s.lastHit = now;
        s.dirty = true;
        Manager.addEssence(p, (int) Math.round(2 * s.comboMult()));
        Manager.addXp(p, elem, 1);
        launch(p, w, e, dir, force, up);
        Vec3d c = center(e);
        Fx.hit(w, elem, c, Math.min(2.5, 0.6 + dmg / 8));
        elementHit(p, w, e, elem, Math.min(2.0, dmg / 6));
        if (!e.isAlive()) Manager.addEssence(p, 10);
        return true;
    }

    /** What the element does to whatever it touches. power ~ 0.5 (light) .. 2 (heavy). */
    public static void elementHit(ServerPlayerEntity p, ServerWorld w, LivingEntity e, int elem, double power) {
        Vec3d c = center(e);
        switch (elem) {
            case Elem.FIRE -> {
                e.setOnFireFor((float) (3 + power * 2));
                Fx.burst(w, "flame", c.x, c.y, c.z, 5 + (int) (power * 4), 0.3, 0.5, 0.3, 0.05);
                if (Forms.active(p) == Elem.FIRE) {
                    explosionLite(p, w, c, 2.6, 4.0, Elem.FIRE);
                }
            }
            case Elem.LIGHTNING -> {
                Status.paralyze(e, (int) (14 + power * 10));
                for (LivingEntity o : around(p, w, c, 4.5)) {
                    if (o == e) continue;
                    Fx.arc(w, c, center(o));
                    hurtDirect(p, w, o, 2.5);
                    Status.paralyze(o, 10);
                    break;
                }
                Fx.arc(w, c.add(0, 1.2, 0), c.add(R.nextGaussian() * 0.5, -0.8, R.nextGaussian() * 0.5));
            }
            case Elem.ICE -> {
                Status.addFreeze(e, power > 1.4 ? 2 : 1);
                Fx.burst(w, "shard", c, 4, 0.3, 0.15);
            }
            case Elem.VOID -> {
                hurtMagic(p, w, e, 1.5 + power * 2.0);
                Fx.burst(w, "void_orb", c, 3, 0.3, 0.03);
                Fx.ring(w, Elem.VOID, c, 0.45);
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------ explosions & terrain
    /** Small elemental blast (no crater) used by transformations. */
    public static void explosionLite(ServerPlayerEntity p, ServerWorld w, Vec3d at, double r, double dmg, int elem) {
        Fx.explode(w, elem, at, 14, 0.25);
        sound(w, at, SoundEvents.ENTITY_GENERIC_EXPLODE, 0.5f, 1.3f);
        for (LivingEntity e : around(p, w, at, r)) {
            hurtDirect(p, w, e, dmg);
            Vec3d d = center(e).subtract(at);
            launch(p, w, e, d, 0.45, 0.2);
        }
    }

    /** Full elemental explosion with falling-off damage, physical push and an optional crater. */
    public static void explosion(ServerPlayerEntity p, ServerWorld w, Vec3d at, double r, double dmg, int elem,
                                 double force, int craterR) {
        Fx.explode(w, elem, at, (int) (24 + r * 10), 0.35 + r * 0.05);
        sound(w, at, SoundEvents.ENTITY_GENERIC_EXPLODE, (float) Math.min(2.0, 0.8 + r * 0.1), 0.8f);
        for (LivingEntity e : around(p, w, at, r)) {
            Vec3d c = center(e);
            double f = Math.max(0.25, 1 - c.distanceTo(at) / r);
            Vec3d dir = c.subtract(at);
            if (dir.lengthSquared() < 1.0E-4) dir = new Vec3d(0, 1, 0);
            hurtDirect(p, w, e, dmg * f);
            launch(p, w, e, dir, force * (0.5 + f), 0.3 + 0.4 * f);
            elementHit(p, w, e, elem, 0.7 + f);
        }
        if (craterR > 0) crater(w, at, craterR);
        for (ServerPlayerEntity sp : w.getPlayers()) {
            if (sp.squaredDistanceTo(at) < 900) shake(sp, (float) Math.min(2.5, 0.8 + r * 0.15), 8);
        }
    }

    /** Temporary hole + flying debris (terrain returns by itself). */
    public static void crater(ServerWorld w, Vec3d at, int radius) {
        if (!cfg().allowBlockDestruction) return;
        int r = Math.min(radius, cfg().maxCraterRadius);
        BlockPos c = BlockPos.ofFloored(at);
        int debris = 0;
        for (int x = -r; x <= r; x++)
            for (int y = -r; y <= r; y++)
                for (int z = -r; z <= r; z++) {
                    if (x * x + y * y * 1.5 + z * z > r * r) continue;
                    BlockPos pos = c.add(x, y, z);
                    BlockState st = w.getBlockState(pos);
                    if (TempBlocks.dig(w, pos, cfg().blockLifetimeTicks) && debris < 48 && R.nextInt(3) == 0) {
                        debris++;
                        w.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, st), pos.getX() + 0.5, pos.getY() + 0.5,
                                pos.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.3);
                    }
                }
    }

    /** Real vanilla lightning bolt (visual only: damage is handled by the ability). */
    public static void bolt(ServerWorld w, Vec3d at) {
        LightningEntity b = EntityType.LIGHTNING_BOLT.create(w);
        if (b == null) return;
        b.refreshPositionAfterTeleport(at.x, at.y, at.z);
        b.setCosmetic(true);
        w.spawnEntity(b);
    }

    // ------------------------------------------------------------ movement helpers
    public static void faceAndTeleport(ServerPlayerEntity p, Vec3d to, Vec3d lookAt) {
        double dx = lookAt.x - to.x, dz = lookAt.z - to.z, dy = lookAt.y - (to.y + 1.6);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        p.networkHandler.requestTeleport(to.x, to.y, to.z, yaw, pitch);
        p.fallDistance = 0;
    }

    /** Position just behind a living target (falls back to its side if blocked). */
    public static Vec3d behind(ServerWorld w, LivingEntity e) {
        Vec3d fwd = flat(Vec3d.fromPolar(0, e.getYaw()));
        for (double dist : new double[]{1.4, 1.0, -1.4}) {
            Vec3d q = e.getPos().subtract(fwd.multiply(dist));
            BlockPos feet = BlockPos.ofFloored(q);
            if (w.getBlockState(feet).getCollisionShape(w, feet).isEmpty()
                    && w.getBlockState(feet.up()).getCollisionShape(w, feet.up()).isEmpty()) return q;
        }
        return e.getPos();
    }

    public static void trail(ServerWorld w, int elem, Vec3d a, Vec3d b) {
        Fx.line(w, Elem.GLOW[elem], a.add(0, 0.9, 0), b.add(0, 0.9, 0), 0.5);
        Fx.line(w, Elem.SPARK[elem], a.add(0, 1.3, 0), b.add(0, 1.3, 0), 0.8);
        Fx.line(w, Elem.BODY[elem], a.add(0, 0.4, 0), b.add(0, 0.4, 0), 1.2);
    }

    // ------------------------------------------------------------ basic combat
    /** Left-click with an empty hand. Chains: 1, 2, 3 = Barrage; Uppercut + (air) + punch = Aerial barrage. */
    public static void punch(ServerPlayerEntity p, ServerWorld w, LivingEntity e) {
        PState s = Manager.st(p);
        long now = w.getTime();
        if (now < s.punchCd) return;
        s.punchCd = now + 6;
        if (now - s.lastPunch > 24) s.chain = 0;
        s.chain++;
        s.lastPunch = now;
        int elem = styleElem(p);
        boolean air = !p.isOnGround() && !e.isOnGround();
        boolean aerialCombo = air && now - s.lastUppercut < 80 && e.getUuid().equals(s.uppercutTarget);
        p.swingHand(Hand.MAIN_HAND, true);
        if (aerialCombo) {
            s.chain = 0;
            barrage(p, w, e, elem, 9, 3.2, true);
            return;
        }
        if (s.chain >= 3) {
            s.chain = 0;
            barrage(p, w, e, elem, 7, 2.6, false);
            return;
        }
        Vec3d dir = flat(e.getPos().subtract(p.getPos()));
        strike(p, w, e, 3.5 + s.chain, dir, 0.55 + 0.1 * s.chain, 0.12, elem);
        sound(w, e.getPos(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 0.8f, 0.9f + 0.1f * s.chain);
        shake(p, 0.35f, 3);
    }

    /** Rapid chain of punches; the last one sends the target flying. */
    public static void barrage(ServerPlayerEntity p, ServerWorld w, LivingEntity e, int elem, int hits, double dmgEach, boolean hover) {
        PState s = Manager.st(p);
        s.lastBarrageEnd = w.getTime() + hits * 2L;
        zoom(p, 0.92f, hits * 2 + 6);
        for (int i = 0; i < hits; i++) {
            final int k = i;
            Scheduler.later(1 + i * 2, () -> {
                if (p.isRemoved() || !p.isAlive() || !e.isAlive() || p.squaredDistanceTo(e) > 64) return;
                boolean last = k == hits - 1;
                Vec3d dir = flat(e.getPos().subtract(p.getPos()));
                if (p.squaredDistanceTo(e) > 6) setVel(p, dir.multiply(0.55).add(0, hover ? 0.1 : p.getVelocity().y, 0));
                else setVel(p, new Vec3d(0, hover ? 0.05 : Math.min(0, p.getVelocity().y), 0));
                if (hover) p.fallDistance = 0;
                p.swingHand(k % 2 == 0 ? Hand.MAIN_HAND : Hand.OFF_HAND, true);
                Vec3d c = center(e).add(R.nextGaussian() * 0.25, R.nextGaussian() * 0.25, R.nextGaussian() * 0.25);
                Fx.flash(w, elem, c, last ? 1.6 : 0.6);
                strike(p, w, e, last ? dmgEach * 2.2 : dmgEach, dir.add(0, hover && last ? -0.3 : 0, 0), last ? 1.7 : 0.1, last ? 0.5 : 0.04, elem);
                sound(w, e.getPos(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 0.7f, 0.8f + (k % 4) * 0.1f);
                shake(p, last ? 2.2f : 0.5f, last ? 8 : 2);
                if (last) {
                    Fx.ring(w, elem, c, 0.9);
                    sound(w, e.getPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, 0.6f, 1.2f);
                }
            });
        }
    }

    /** Key G. Launches the target upward; right after a punch it becomes an aerial launch. */
    public static void uppercut(ServerPlayerEntity p, ServerWorld w) {
        PState s = Manager.st(p);
        long now = w.getTime();
        if (now < s.punchCd) return;
        s.punchCd = now + 14;
        p.swingHand(Hand.MAIN_HAND, true);
        LivingEntity e = target(p, w, 4.8);
        int elem = styleElem(p);
        if (e == null) {
            Vec3d at = p.getEyePos().add(p.getRotationVec(1f).multiply(1.6));
            Fx.burst(w, Elem.SPARK[elem], at, 6, 0.3, 0.1);
            return;
        }
        boolean aerial = now - s.lastPunch < 26;
        double up = aerial ? 1.15 : 0.8;
        Vec3d dir = flat(e.getPos().subtract(p.getPos()));
        strike(p, w, e, aerial ? 7 : 5.5, dir.multiply(0.3), 0.2, up, elem);
        s.lastUppercut = now;
        s.uppercutTarget = e.getUuid();
        // the fighter leaps after the target so the combo can continue in the air
        setVel(p, new Vec3d(dir.x * 0.15, 0.62, dir.z * 0.15));
        Vec3d a = e.getPos();
        for (int i = 0; i < 10; i++) {
            Fx.vel(w, Elem.BODY[elem], a.x + R.nextGaussian() * 0.3, a.y + i * 0.4, a.z + R.nextGaussian() * 0.3, 0, 0.15, 0);
            if (i % 2 == 0) Fx.vel(w, Elem.GLOW[elem], a.x, a.y + i * 0.4, a.z, 0, 0.1, 0);
        }
        Fx.ring(w, elem, a.add(0, 0.2, 0), 0.7);
        sound(w, a, SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.7f);
        shake(p, 1.0f, 5);
    }

    /** Key H. A heavy kick; right after a barrage it is the Finisher: an impact explosion. */
    public static void kick(ServerPlayerEntity p, ServerWorld w) {
        PState s = Manager.st(p);
        long now = w.getTime();
        if (now < s.punchCd) return;
        s.punchCd = now + 12;
        p.swingHand(Hand.OFF_HAND, true);
        LivingEntity e = target(p, w, 4.8);
        int elem = styleElem(p);
        if (e == null) return;
        Vec3d dir = flat(e.getPos().subtract(p.getPos()));
        if (now - s.lastBarrageEnd < 45 && now - s.lastBarrageEnd > -12) {
            s.lastBarrageEnd = -1000;
            explosion(p, w, center(e), 4.6, 12, elem, 1.3, 2);
            zoom(p, 0.88f, 10);
            shake(p, 2.6f, 10);
            return;
        }
        strike(p, w, e, 7, dir, 1.3, 0.25, elem);
        sound(w, e.getPos(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.9f);
        shake(p, 1.0f, 5);
    }

    /** Key B: side-step with brief invulnerability. Getting "hit" during it triggers a counter-attack. */
    public static void dodge(ServerPlayerEntity p, ServerWorld w, int dirArg) {
        PState s = Manager.st(p);
        long now = w.getTime();
        if (now < s.dodgeCd) return;
        if (Manager.get(p).essence() < 6) return;
        Manager.addEssence(p, -6);
        s.dodgeCd = now + 34;
        s.dodgeUntil = now + 10;
        Vec3d fwd = flat(Vec3d.fromPolar(0, p.getYaw()));
        Vec3d right = new Vec3d(-fwd.z, 0, fwd.x);
        Vec3d v = switch (dirArg) {
            case 0 -> right.multiply(-1);
            case 1 -> right;
            default -> fwd.multiply(-1);
        };
        int elem = styleElem(p);
        Vec3d from = p.getPos();
        setVel(p, v.multiply(1.35).add(0, 0.15, 0));
        trail(w, elem, from, from.add(v.multiply(3)));
        Fx.flash(w, elem, from.add(0, 1, 0), 0.8);
        sound(w, from, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.5f);
        zoom(p, 1.06f, 6);
    }

    /** Called when a dodge window absorbs an attack: teleport behind the attacker and punch back. */
    public static void counter(ServerPlayerEntity p, ServerWorld w, LivingEntity attacker) {
        PState s = Manager.st(p);
        s.dodgeUntil = 0;
        int elem = styleElem(p);
        Vec3d from = p.getPos();
        Vec3d to = behind(w, attacker);
        faceAndTeleport(p, to, attacker.getPos());
        trail(w, elem, from, to);
        Fx.flash(w, elem, to.add(0, 1, 0), 1.0);
        p.swingHand(Hand.MAIN_HAND, true);
        Manager.addEssence(p, 14);
        Scheduler.later(2, () -> {
            if (p.isRemoved() || !attacker.isAlive()) return;
            strike(p, w, attacker, 9, flat(attacker.getPos().subtract(p.getPos())), 1.2, 0.35, elem);
            sound(w, attacker.getPos(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.9f);
            shake(p, 1.6f, 6);
        });
    }
}
