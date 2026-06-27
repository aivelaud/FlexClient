package com.flex.client.mixin;

  import com.flex.client.module.ModuleManager;
  import net.minecraft.client.network.ClientPlayerEntity;
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
   * isInsideWall() inject'i kaldırıldı: 1.20.1'de ClientPlayerEntity
   * üzerinde bu metot override edilmez ve inject boştu — refmap hatası üretiyordu.
   *
   * getFluidHeight(TagKey<Fluid>) Entity'den miras alınan metottur,
   * Fabric Mixin bunu doğru çözümler.
   */
  @Mixin(ClientPlayerEntity.class)
  public abstract class JesusMixin {

      @Inject(method = "getFluidHeight", at = @At("HEAD"), cancellable = true)
      private void onGetFluidHeight(
              net.minecraft.registry.tag.TagKey<net.minecraft.fluid.Fluid> fluid,
              CallbackInfoReturnable<Double> cir) {

          if (!ModuleManager.isEnabled("Jesus")) return;

          ClientPlayerEntity player = (ClientPlayerEntity)(Object)this;
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
  