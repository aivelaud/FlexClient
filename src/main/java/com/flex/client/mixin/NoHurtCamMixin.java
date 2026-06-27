package com.flex.client.mixin;

import com.flex.client.module.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NoHurtCamMixin — Hasar alınca kamera titremesini tamamen iptal eder.
 * bobViewWhenHurt metodunu doğrudan hedefler.
 */
@Mixin(GameRenderer.class)
public class NoHurtCamMixin {

    @Inject(method = "bobViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void onBobViewWhenHurt(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (ModuleManager.isEnabled("NoHurtCam")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderWorld(FJLnet/minecraft/client/util/math/MatrixStack;)V", at = @At("HEAD"))
    private void onRenderWorld(float tickDelta, long limitTime, MatrixStack matrix, CallbackInfo ci) {
        if (!ModuleManager.isEnabled("NoHurtCam")) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.hurtTime = 0;
            mc.player.hurtPitch = 0;
        }
    }
}
