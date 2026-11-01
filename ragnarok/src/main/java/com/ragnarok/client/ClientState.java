package com.ragnarok.client;

import com.ragnarok.net.SyncPayload;
import net.minecraft.client.MinecraftClient;

public final class ClientState {
    private ClientState() {}

    public static volatile SyncPayload last = null;
    public static long cdEnd = 0, formEnd = 0;
    public static float shownEssence = 0;

    public static float shakeAmp = 0;
    public static int shakeTicks = 0;
    public static float zoomTarget = 1f;
    public static int zoomTotal = 0, zoomLeft = 0;

    public static long time() {
        var w = MinecraftClient.getInstance().world;
        return w != null ? w.getTime() : 0;
    }

    public static void set(SyncPayload p) {
        last = p;
        if (p != null) {
            cdEnd = time() + p.cdLeft();
            formEnd = time() + p.formLeft();
        }
    }

    public static void shake(float amp, int ticks) {
        if (amp >= shakeAmp || shakeTicks <= 0) {
            shakeAmp = amp;
            shakeTicks = ticks;
        }
    }

    public static void zoom(float factor, int ticks) {
        zoomTarget = factor;
        zoomTotal = Math.max(2, ticks);
        zoomLeft = zoomTotal;
    }

    /** FOV multiplier: eases into the zoom and back out. */
    public static double fovFactor(float tickDelta) {
        if (zoomLeft <= 0) return 1.0;
        double t = 1.0 - (zoomLeft - tickDelta) / zoomTotal;
        t = Math.max(0, Math.min(1, t));
        return 1.0 + (zoomTarget - 1.0) * Math.sin(Math.PI * t);
    }

    public static void tick(MinecraftClient mc) {
        if (zoomLeft > 0) zoomLeft--;
        if (shakeTicks > 0 && mc.player != null) {
            float a = shakeAmp * Math.min(1f, shakeTicks / 4f);
            mc.player.setYaw(mc.player.getYaw() + (float) (Math.random() - 0.5) * a * 1.4f);
            mc.player.setPitch(mc.player.getPitch() + (float) (Math.random() - 0.5) * a);
            shakeTicks--;
            if (shakeTicks == 0) shakeAmp = 0;
        }
    }
}
