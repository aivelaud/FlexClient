package com.flex.client.mixin;

import com.flex.client.module.ModuleManager;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerEntity.class)
public abstract class JesusMixin {

    @Inject(method = "isInsideWall", at = @At("HEAD"), cancellable = true)
    private void onIsInsideWall(CallbackInfoReturnable<Boolean> cir) {
        // Sadece çerçeveleme için — bloklama yok
    }

    @Inject(method = "getFluidHeight", at = @At("HEAD"), cancellable = true)
    private void onGetFluidHeight(net.minecraft.registry.tag.TagKey<net.minecraft.fluid.Fluid> fluid,
                                   CallbackInfoReturnable<Double> cir) {
        if (!ModuleManager.isEnabled("Jesus")) return;

        ClientPlayerEntity player = (ClientPlayerEntity)(Object)this;
        if (player.isSneaking()) return;

        // Suda yüzey üstünde ise sıfır yükseklik döndür — katı zemin gibi davran
        if (fluid == FluidTags.WATER && player.getVelocity().y <= 0) {
            BlockPos pos = player.getBlockPos();
            FluidState fs = player.getWorld().getFluidState(pos);
            if (fs.isIn(FluidTags.WATER) && !fs.isIn(FluidTags.LAVA)) {
                cir.setReturnValue(0.0);
            }
        }
    }
}
