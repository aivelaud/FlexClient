package com.flex.client.mixin;

import com.flex.client.module.ModuleManager;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class NoRenderMixin {

    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
    private void onRenderFire(float tickDelta, net.minecraft.client.util.math.MatrixStack matrices,
                               CallbackInfo ci) {
        if (ModuleManager.isEnabled("NoRender")) {
            if (ModuleManager.get("NoRender").getSetting("fire")) ci.cancel();
        }
    }

    @Inject(method = "renderWorld", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;FJZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;)V"),
        cancellable = false)
    private void onRenderWorld(CallbackInfo ci) {
        // Hook point for future render modifications
    }
}
