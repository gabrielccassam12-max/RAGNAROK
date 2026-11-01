package com.ragnarok;

import com.ragnarok.combat.Combat;
import com.ragnarok.config.RagnarokConfig;
import com.ragnarok.core.Forms;
import com.ragnarok.core.Manager;
import com.ragnarok.core.Scheduler;
import com.ragnarok.item.ModItems;
import com.ragnarok.net.ActionPayload;
import com.ragnarok.net.FxPayload;
import com.ragnarok.net.SelectPayload;
import com.ragnarok.net.SyncPayload;
import com.ragnarok.particle.ModParticles;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Ragnarok implements ModInitializer {
    public static final String ID = "ragnarok";
    public static final Logger LOG = LoggerFactory.getLogger(ID);

    public static Identifier id(String path) {
        return Identifier.of(ID, path);
    }

    @Override
    public void onInitialize() {
        RagnarokConfig.load();
        ModParticles.init();
        ModItems.init();
        Scheduler.init();
        Manager.init();

        PayloadTypeRegistry.playC2S().register(ActionPayload.ID, ActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SelectPayload.ID, SelectPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncPayload.ID, SyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FxPayload.ID, FxPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ActionPayload.ID, (payload, context) -> {
            ServerPlayerEntity p = context.player();
            if (p.isSpectator() || !p.isAlive() || !Manager.get(p).any()) return;
            ServerWorld w = (ServerWorld) p.getWorld();
            switch (payload.action()) {
                case ActionPayload.CAST -> Manager.cast(p);
                case ActionPayload.NEXT -> Manager.cycleAbility(p, 1);
                case ActionPayload.PREV -> Manager.cycleAbility(p, -1);
                case ActionPayload.STYLE -> Manager.cycleStyle(p);
                case ActionPayload.UPPERCUT -> Combat.uppercut(p, w);
                case ActionPayload.KICK -> Combat.kick(p, w);
                case ActionPayload.DODGE -> Combat.dodge(p, w, payload.arg());
                case ActionPayload.TRANSFORM -> Forms.toggle(p);
                case ActionPayload.CHARGE -> Manager.charge(p);
                default -> { }
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(SelectPayload.ID, (payload, context) -> {
            ServerPlayerEntity p = context.player();
            if (Manager.get(p).any()) Manager.select(p, payload.style(), payload.ability());
        });
        LOG.info("Ragnarok: Punhos do Apocalipse loaded.");
    }
}
