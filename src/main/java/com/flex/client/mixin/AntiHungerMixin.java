package com.flex.client.mixin;

import com.flex.client.module.ModuleManager;
import net.minecraft.entity.player.HungerManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * AntiHungerMixin — 1.20.1 Fabric uyumlu.
 * HungerManager.addExhaustion(float) metoduna inject ederek açlık tüketimini iptal eder.
 */
@Mixin(HungerManager.class)
public class AntiHungerMixin {

    @Inject(method = "addExhaustion", at = @At("HEAD"), cancellable = true)
    private void onAddExhaustion(float exhaustion, CallbackInfo ci) {
        if (ModuleManager.isEnabled("AntiHunger")) {
            ci.cancel();
        }
    }

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void onUpdate(net.minecraft.entity.player.PlayerEntity player, CallbackInfo ci) {
        if (ModuleManager.isEnabled("AntiHunger")) {
            HungerManager self = (HungerManager)(Object)this;
            if (self.getFoodLevel() < 20) self.setFoodLevel(20);
            ci.cancel();
        }
    }
}
