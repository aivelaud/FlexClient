package com.flex.client.mixin;

import com.flex.client.module.Module;
import com.flex.client.module.ModuleManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class SpeedMineMixin {

    @ModifyVariable(
        method = "getBlockBreakingSpeed",
        at = @At("RETURN"),
        ordinal = 0
    )
    private float modifyBreakSpeed(float speed) {
        if (ModuleManager.isEnabled("SpeedMine")) {
            Module m = ModuleManager.get("SpeedMine");
            float mod = m != null ? m.getFloatSetting("modifier", 0.6f) : 0.6f;
            return speed + (float)(speed * (10.0f * mod));
        }
        return speed;
    }
}
