package com.flex.client.mixin;

import com.flex.client.module.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(at = @At("HEAD"), method = "getNightVisionStrength", cancellable = true)
    private static void onNightVision(LivingEntity entity, float tickDelta,
                                      CallbackInfoReturnable<Float> cir) {
        // Fullbright gamma ile yonetiliyor
    }

    /**
     * NoHurtCam: renderWorld basinda hurtTime=0 yapilarak MC'nin
     * kamera egimi hic hesaplanmaz.
     */
    @Inject(at = @At("HEAD"), method = "renderWorld")
    private void onRenderWorld(float tickDelta, long limitTime,
                               MatrixStack matrix, CallbackInfo ci) {
        if (!ModuleManager.isEnabled("NoHurtCam")) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.hurtTime = 0;
        }
    }
}
