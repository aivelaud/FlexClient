package com.flex.client.mixin;

import com.flex.client.module.ModuleManager;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(at = @At("HEAD"), method = "getNightVisionStrength", cancellable = true)
    private static void onNightVision(net.minecraft.entity.LivingEntity entity, float tickDelta,
                                       CallbackInfoReturnable<Float> cir) {
        // Fullbright zaten options.gamma ile yapılıyor
        // Burada ek Night Vision boost yapabiliriz
    }
}
