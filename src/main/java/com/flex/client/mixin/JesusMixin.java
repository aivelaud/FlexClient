package com.flex.client.mixin;

import com.flex.client.module.ModuleManager;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * JesusMixin — MC 1.20.1 uyumlu su üzerinde yürüme.
 *
 * NEDEN Entity hedef alınıyor?
 * getFluidHeight(TagKey<Fluid>) metodu ClientPlayerEntity'de DEĞİL,
 * üst sınıf olan Entity'de (class_1542) tanımlıdır.
 * Mixin derleyicisi, hedef sınıfın kendi metodlarına bakarak refmap üretir;
 * ClientPlayerEntity bu metodu override etmediği için refmap'e girmez
 * ve runtime'da inject başarısız olur → crash.
 *
 * Çözüm: @Mixin hedefini Entity yapıp içeride instanceof kontrolü yapıyoruz.
 */
@Mixin(Entity.class)
public abstract class JesusMixin {

    @Inject(method = "getFluidHeight", at = @At("HEAD"), cancellable = true)
    private void onGetFluidHeight(
            net.minecraft.registry.tag.TagKey<net.minecraft.fluid.Fluid> fluid,
            CallbackInfoReturnable<Double> cir) {

        if (!((Object) this instanceof ClientPlayerEntity player)) return;
        if (!ModuleManager.isEnabled("Jesus")) return;
        if (player.isSneaking()) return;

        if (fluid == FluidTags.WATER && player.getVelocity().y <= 0) {
            BlockPos pos = player.getBlockPos();
            FluidState fs = player.getWorld().getFluidState(pos);
            if (fs.isIn(FluidTags.WATER)) {
                cir.setReturnValue(0.0);
            }
        }
    }
}
