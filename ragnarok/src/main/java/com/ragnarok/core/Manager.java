package com.ragnarok.core;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.ragnarok.combat.Combat;
import com.ragnarok.combat.Status;
import com.ragnarok.abilities.Abilities;
import com.ragnarok.config.RagnarokConfig;
import com.ragnarok.net.SyncPayload;
import com.ragnarok.particle.Fx;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server brain: saved data, Essence, casting rules, regeneration, overload, HUD sync, input hooks. */
public final class Manager {
    private Manager() {}

    public static final AttachmentType<PlayerData> DATA = AttachmentRegistry.<PlayerData>builder()
            .persistent(PlayerData.CODEC)
            .copyOnDeath()
            .buildAndRegister(com.ragnarok.Ragnarok.id("fighter"));

    private static final Map<UUID, PState> STATES = new HashMap<>();

    public static RagnarokConfig cfg() {
        return RagnarokConfig.INSTANCE;
    }

    public static PState st(ServerPlayerEntity p) {
        return STATES.computeIfAbsent(p.getUuid(), k -> new PState());
    }

    public static PlayerData get(ServerPlayerEntity p) {
        return p.getAttachedOrElse(DATA, PlayerData.DEFAULT);
    }

    public static void set(ServerPlayerEntity p, PlayerData d) {
        p.setAttached(DATA, d);
        st(p).dirty = true;
    }

    private static void note(ServerPlayerEntity p, String key, Formatting color, Object... args) {
        p.sendMessage(Text.translatable(key, args).formatted(color), true);
    }

    // ------------------------------------------------------------ init
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Manager::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            TempBlocks.restoreAll();
            STATES.clear();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.remove(handler.getPlayer().getUuid()));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldP, newP, alive) -> {
            STATES.remove(newP.getUuid());
            sync(newP);
        });

        // Empty-hand left click on a creature = our own punch system (vanilla damage is cancelled)
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity sp) || hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (!sp.getMainHandStack().isEmpty() || sp.isSpectator() || !get(sp).any()) return ActionResult.PASS;
            if (!(entity instanceof LivingEntity le) || !le.isAlive()) return ActionResult.PASS;
            if (!cfg().allowPvp && le instanceof ServerPlayerEntity) return ActionResult.PASS;
            Combat.punch(sp, (ServerWorld) sp.getWorld(), le);
            return ActionResult.FAIL;
        });

        // Dodge window: the attack is cancelled and we counter
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity sp)) return true;
            PState s = STATES.get(sp.getUuid());
            if (s == null || sp.getWorld().getTime() >= s.dodgeUntil) return true;
            if (source.getAttacker() instanceof LivingEntity att && att != sp) {
                Combat.counter(sp, (ServerWorld) sp.getWorld(), att);
                return false;
            }
            if (source.getSource() instanceof ProjectileEntity) return false;
            return true;
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("ragnarok")
                        .requires(src -> src.hasPermissionLevel(2))
                        .then(CommandManager.literal("master").executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            PlayerData d = get(p).withUnlocked(15).withXp(List.of(1000, 1000, 1000, 1000))
                                    .withEssence(cfg().maxEssence);
                            set(p, d);
                            sync(p);
                            note(p, "message.ragnarok.master", Formatting.GOLD);
                            return 1;
                        }))
                        .then(CommandManager.literal("essence").then(CommandManager.argument("amount", IntegerArgumentType.integer(0, 5000))
                                .executes(ctx -> {
                                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                    set(p, get(p).withEssence(Math.min(cfg().maxEssence, IntegerArgumentType.getInteger(ctx, "amount"))));
                                    sync(p);
                                    return 1;
                                })))));
    }

    // ------------------------------------------------------------ essence & xp
    public static void addEssence(ServerPlayerEntity p, int n) {
        PlayerData d = get(p);
        int v = Math.max(0, Math.min(cfg().maxEssence, d.essence() + n));
        if (v != d.essence()) set(p, d.withEssence(v));
    }

    /** XP always goes to an element (0-3). */
    public static void addXp(ServerPlayerEntity p, int elem, int n) {
        PlayerData d = get(p);
        if (!d.has(elem)) return;
        int before = d.level(elem);
        PlayerData nd = d.addXp(elem, n);
        set(p, nd);
        int after = nd.level(elem);
        if (after > before) {
            ServerWorld w = (ServerWorld) p.getWorld();
            Vec3d c = p.getPos().add(0, 1, 0);
            Fx.explode(w, elem, c, 40, 0.5);
            Combat.sound(w, c, SoundEvents.ENTITY_PLAYER_LEVELUP, 1f, 0.7f);
            p.sendMessage(Text.translatable("message.ragnarok.levelup", Text.translatable("element.ragnarok." + elem), Ability.levelName(after))
                    .formatted(Formatting.GOLD), false);
        }
    }

    public static void unlock(ServerPlayerEntity p, int elem) {
        PlayerData d = get(p);
        boolean first = !d.any();
        PlayerData nd = d.withUnlocked(d.unlocked() | (1 << elem));
        if (first) nd = nd.withStyle(elem).withEssence(cfg().maxEssence / 2);
        set(p, nd);
        sync(p);
        ServerWorld w = (ServerWorld) p.getWorld();
        Vec3d c = p.getPos().add(0, 1, 0);
        Fx.explode(w, elem, c, 60, 0.6);
        Combat.sound(w, c, SoundEvents.BLOCK_BEACON_ACTIVATE, 1f, 0.6f);
        p.sendMessage(Text.translatable("message.ragnarok.unlocked", Text.translatable("element.ragnarok." + elem)).formatted(Formatting.GOLD), false);
    }

    // ------------------------------------------------------------ selection
    public static void cycleStyle(ServerPlayerEntity p) {
        PlayerData d = get(p);
        if (!d.any()) return;
        int s = d.style();
        for (int i = 1; i <= 8; i++) {
            int n = (s + i) % 8;
            if (d.styleUnlocked(n)) {
                set(p, d.withStyle(n));
                announce(p);
                return;
            }
        }
    }

    public static void cycleAbility(ServerPlayerEntity p, int dir) {
        PlayerData d = get(p);
        if (!d.any()) return;
        int n = Ability.of(d.style()).size();
        set(p, d.withAbility(Math.floorMod(d.ability() + dir, n)));
        announce(p);
    }

    public static void select(ServerPlayerEntity p, int style, int ability) {
        PlayerData d = get(p);
        if (style < 0 || style > 7 || !d.styleUnlocked(style)) return;
        PlayerData nd = d.withStyle(style).withAbility(Math.floorMod(ability, Ability.of(style).size()));
        set(p, nd);
        announce(p);
    }

    private static void announce(ServerPlayerEntity p) {
        PlayerData d = get(p);
        Ability a = Ability.get(d.style(), d.ability());
        boolean locked = d.styleLevel(a.style) < a.level;
        p.sendMessage(Text.translatable(locked ? "message.ragnarok.selected_locked" : "message.ragnarok.selected",
                a.displayName(), a.cost, Ability.levelName(a.level)).formatted(locked ? Formatting.RED : Formatting.AQUA), true);
    }

    // ------------------------------------------------------------ casting
    public static void cast(ServerPlayerEntity p) {
        PlayerData d = get(p);
        if (!d.any()) {
            note(p, "message.ragnarok.not_awake", Formatting.RED);
            return;
        }
        ServerWorld w = (ServerWorld) p.getWorld();
        long now = w.getTime();
        PState s = st(p);
        Ability a = Ability.get(d.style(), d.ability());
        if (!d.styleUnlocked(a.style)) return;
        int level = d.styleLevel(a.style);
        if (level < a.level) {
            note(p, "message.ragnarok.locked", Formatting.RED, Ability.levelName(a.level));
            return;
        }
        if (now < s.overloadUntil) {
            note(p, "message.ragnarok.overloaded", Formatting.RED);
            return;
        }
        if (now < s.globalCd || now < s.cooldownEnd.getOrDefault(a, 0L)) return;
        int cost = (int) Math.round(a.cost * cfg().costMultiplier * (Forms.active(p) >= 0 ? 0.8 : 1.0));
        if (d.essence() < cost) {
            note(p, "message.ragnarok.low", Formatting.RED, cost);
            Combat.sound(w, p.getPos(), SoundEvents.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.5f);
            return;
        }
        if (!Abilities.run(a, p, w, level)) {
            note(p, "message.ragnarok.no_target", Formatting.YELLOW);
            return;
        }
        addEssence(p, -cost);
        int cd = (int) Math.max(10, Math.round(a.cooldown * cfg().cooldownMultiplier * Forms.cooldownFactor(p, a)));
        s.cooldownEnd.put(a, now + cd);
        s.lastCdMax = cd;
        s.globalCd = now + 8;
        s.lastUse = now;
        // xp: the elements involved
        if (a.style < 4) addXp(p, a.style, a.ultimate() ? 20 : 8);
        else {
            int[] c = Elem.COMBO[a.style - 4];
            addXp(p, c[0], 6);
            addXp(p, c[1], 6);
        }
        // overload: too many consecutive abilities
        s.heat += cost * cfg().heatPerEssence;
        if (s.heat >= cfg().overloadHeat) {
            s.heat = cfg().overloadHeat / 2.0;
            s.overloadUntil = now + cfg().overloadTicks;
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, cfg().overloadTicks, 1));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, cfg().overloadTicks, 0));
            note(p, "message.ragnarok.overload", Formatting.RED);
            Fx.burst(w, "smoke", p.getX(), p.getBodyY(0.6), p.getZ(), 14, 0.4, 0.6, 0.4, 0.05);
            Fx.burst(w, "ember", p.getX(), p.getBodyY(0.6), p.getZ(), 10, 0.4, 0.6, 0.4, 0.15);
        }
        s.dirty = true;
        sync(p);
    }

    /** Key F (held): manual Essence charge. */
    public static void charge(ServerPlayerEntity p) {
        PlayerData d = get(p);
        if (!d.any() || p.isSpectator()) return;
        ServerWorld w = (ServerWorld) p.getWorld();
        long now = w.getTime();
        PState s = st(p);
        s.lastUse = now;
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 6, 2, false, false, false));
        if (d.essence() < cfg().maxEssence) addEssence(p, 1);
        int elem = d.style() < 4 ? d.style() : Elem.COMBO[d.style() - 4][(int) (now / 10 % 2)];
        if (now % 2 == 0) Fx.aura(w, elem, p.getX(), p.getY(), p.getZ(), 0.9, 1.9, 2);
        if (now % 20 == 0) {
            Fx.ring(w, elem, p.getPos().add(0, 0.2, 0), 0.5);
            Combat.sound(w, p.getPos(), SoundEvents.BLOCK_BEACON_AMBIENT, 0.5f, 0.6f + (float) d.essence() / cfg().maxEssence);
        }
        s.heat = Math.max(0, s.heat - 2); // charging also cools the body
    }

    // ------------------------------------------------------------ tick & sync
    private static void tick(MinecraftServer server) {
        TempBlocks.tick(server);
        Status.tick(server);
        long now = server.getOverworld().getTime();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            PlayerData d = get(p);
            if (!d.any()) continue;
            PState s = st(p);
            Forms.tick(p, s, now);
            boolean over = now < s.overloadUntil;
            if (now % 5 == 0) {
                if (!over && now - s.lastUse > cfg().regenDelayTicks && d.essence() < cfg().maxEssence) addEssence(p, cfg().regenPer5Ticks);
                s.heat = Math.max(0, s.heat - 3);
                if (s.combo > 0 && now - s.lastHit > 60) {
                    s.combo = 0;
                    s.dirty = true;
                }
            }
            if (over && now % 4 == 0) {
                ServerWorld w = (ServerWorld) p.getWorld();
                Fx.burst(w, "spark_e", p.getX(), p.getBodyY(0.5), p.getZ(), 2, 0.4, 0.6, 0.4, 0.1);
                Fx.burst(w, "smoke", p.getX(), p.getBodyY(0.9), p.getZ(), 1, 0.3, 0.3, 0.3, 0.02);
                s.dirty = true;
            }
            if ((s.dirty && now % 2 == 0) || now % 20 == 0) sync(p);
        }
    }

    public static void sync(ServerPlayerEntity p) {
        PlayerData d = get(p);
        PState s = st(p);
        long now = p.getWorld().getTime();
        Ability a = Ability.get(d.style(), d.ability());
        int cd = (int) Math.max(0, s.cooldownEnd.getOrDefault(a, 0L) - now);
        int formLeft = s.formElem >= 0 ? (int) Math.max(0, s.formEnd - now) : 0;
        int formMax = s.formElem >= 0 ? (int) Math.max(1, s.formEnd - s.formStart) : 1;
        s.dirty = false;
        ServerPlayNetworking.send(p, new SyncPayload(d.unlocked(), d.essence(), cfg().maxEssence, d.style(), d.ability(),
                new ArrayList<>(d.xp()), cd, Math.max(1, s.lastCdMax), s.formElem, formLeft, formMax,
                (int) Math.round(s.comboMult() * 100), now < s.overloadUntil));
    }
}
