package com.flex.client.mixin;

import com.flex.client.module.ModuleManager;
import net.minecraft.entity.player.HungerManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HungerManager.class)
public abstract class AntiHungerMixin {

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void onUpdate(net.minecraft.entity.player.PlayerEntity player, CallbackInfo ci) {
        if (ModuleManager.isEnabled("AntiHunger")) {
            ci.cancel(); // Açlık tüketimini tamamen durdur
        }
    }
}
