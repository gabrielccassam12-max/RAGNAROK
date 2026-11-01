package com.ragnarok.client;

import com.ragnarok.net.ActionPayload;
import com.ragnarok.net.FxPayload;
import com.ragnarok.net.SyncPayload;
import com.ragnarok.particle.ModParticles;
import com.ragnarok.particle.ParticleProfile;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.util.InputUtil;
import net.minecraft.particle.SimpleParticleType;
import org.lwjgl.glfw.GLFW;

import java.util.Map;

public class RagnarokClient implements ClientModInitializer {
    private static final String CAT = "key.categories.ragnarok";
    public static KeyBinding CAST, NEXT, PREV, STYLE, UPPERCUT, KICK, DODGE, TRANSFORM, CHARGE, MENU;

    private static KeyBinding key(String name, int code) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding("key.ragnarok." + name, InputUtil.Type.KEYSYM, code, CAT));
    }

    @Override
    public void onInitializeClient() {
        // ---- the mod's own particles
        for (Map.Entry<String, SimpleParticleType> en : ModParticles.TYPES.entrySet()) {
            ParticleProfile pf = ModParticles.PROFILES.get(en.getKey());
            ParticleFactoryRegistry.getInstance().register(en.getValue(), sprites ->
                    (ParticleFactory<SimpleParticleType>) (type, world, x, y, z, vx, vy, vz) ->
                            new GlowParticle(world, x, y, z, vx, vy, vz, sprites, pf));
        }

        // ---- keys
        CAST = key("cast", GLFW.GLFW_KEY_R);
        NEXT = key("next", GLFW.GLFW_KEY_X);
        PREV = key("prev", GLFW.GLFW_KEY_Z);
        STYLE = key("style", GLFW.GLFW_KEY_V);
        UPPERCUT = key("uppercut", GLFW.GLFW_KEY_G);
        KICK = key("kick", GLFW.GLFW_KEY_H);
        DODGE = key("dodge", GLFW.GLFW_KEY_B);
        TRANSFORM = key("transform", GLFW.GLFW_KEY_C);
        CHARGE = key("charge", GLFW.GLFW_KEY_F);
        MENU = key("menu", GLFW.GLFW_KEY_M);

        // ---- server -> client
        ClientPlayNetworking.registerGlobalReceiver(SyncPayload.ID, (payload, context) -> ClientState.set(payload));
        ClientPlayNetworking.registerGlobalReceiver(FxPayload.ID, (payload, context) -> {
            if (payload.kind() == FxPayload.SHAKE) ClientState.shake(payload.amount(), payload.ticks());
            else ClientState.zoom(payload.amount(), payload.ticks());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientState.set(null));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientState.tick(client);
            if (client.player == null) return;
            if (client.currentScreen == null) {
                while (CAST.wasPressed()) send(ActionPayload.CAST, 0);
                while (NEXT.wasPressed()) send(ActionPayload.NEXT, 0);
                while (PREV.wasPressed()) send(ActionPayload.PREV, 0);
                while (STYLE.wasPressed()) send(ActionPayload.STYLE, 0);
                while (UPPERCUT.wasPressed()) send(ActionPayload.UPPERCUT, 0);
                while (KICK.wasPressed()) send(ActionPayload.KICK, 0);
                while (TRANSFORM.wasPressed()) send(ActionPayload.TRANSFORM, 0);
                while (DODGE.wasPressed()) {
                    int dir = client.options.leftKey.isPressed() ? 0 : client.options.rightKey.isPressed() ? 1 : 2;
                    send(ActionPayload.DODGE, dir);
                }
                if (CHARGE.isPressed()) send(ActionPayload.CHARGE, 0);
                while (MENU.wasPressed()) client.setScreen(new RagnarokScreen());
            }
        });

        HudRenderCallback.EVENT.register(RagnarokHud::render);
    }

    private static void send(int action, int arg) {
        ClientPlayNetworking.send(new ActionPayload(action, arg));
    }
}
