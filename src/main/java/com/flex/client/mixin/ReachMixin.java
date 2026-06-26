package com.flex.client.mixin;

import com.flex.client.module.Module;
import com.flex.client.module.ModuleManager;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class ReachMixin {

    @Inject(method = "getAttackCooldownProgress", at = @At("HEAD"), cancellable = true)
    private void onGetAttackCooldown(float baseTime, CallbackInfoReturnable<Float> cir) {
        // AttackCooldown'ı etkilemeden sadece reach için hook
    }

    @Inject(method = "getBlockInteractionRange", at = @At("HEAD"), cancellable = true)
    private void onBlockInteractionRange(CallbackInfoReturnable<Double> cir) {
        if (ModuleManager.isEnabled("Reach")) {
            Module m = ModuleManager.get("Reach");
            if (m != null && m.getSetting("blockReach")) {
                cir.setReturnValue((double) m.getFloatSetting("range", 6.0f));
            }
        }
    }

    @Inject(method = "getEntityInteractionRange", at = @At("HEAD"), cancellable = true)
    private void onEntityInteractionRange(CallbackInfoReturnable<Double> cir) {
        if (ModuleManager.isEnabled("Reach")) {
            Module m = ModuleManager.get("Reach");
            if (m != null && m.getSetting("entityReach")) {
                cir.setReturnValue((double) m.getFloatSetting("range", 6.0f));
            }
        }
    }
}
