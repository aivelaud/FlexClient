package com.flex.client.mixin;

import com.flex.client.module.ModuleManager;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.lwjgl.glfw.GLFW;

@Mixin(MinecraftClient.class)
public class FovMixin {

    @Inject(at = @At("RETURN"), method = "getFov", cancellable = true)
    private void onGetFov(net.minecraft.client.option.GameOptions options, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        if (!ModuleManager.isEnabled("Zoom")) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        // Z tuşu basılıysa yakınlaştır
        long win = mc.getWindow().getHandle();
        boolean zHeld = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_Z) == GLFW.GLFW_PRESS;
        if (zHeld) {
            cir.setReturnValue(10.0);
        }
    }
}
