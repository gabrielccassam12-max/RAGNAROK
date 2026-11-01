package com.ragnarok.mixin;

import com.ragnarok.client.ClientState;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Subtle FOV zoom during barrages, transformations and ultimates. */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
    private void ragnarok$zoom(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        double f = ClientState.fovFactor(tickDelta);
        if (f != 1.0) cir.setReturnValue(cir.getReturnValue() * f);
    }
}
