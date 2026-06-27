package com.flex.client.mixin;

  import com.flex.client.module.ModuleManager;
  import net.minecraft.client.render.GameRenderer;
  import net.minecraft.client.util.math.MatrixStack;
  import org.spongepowered.asm.mixin.Mixin;
  import org.spongepowered.asm.mixin.injection.At;
  import org.spongepowered.asm.mixin.injection.Inject;
  import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

  /**
   * NoRenderMixin — MC 1.20.1 uyumlu.
   * Tam imza: renderFireOverlay(FLnet/minecraft/client/util/math/MatrixStack;)V
   */
  @Mixin(GameRenderer.class)
  public abstract class NoRenderMixin {

      @Inject(
          method = "renderFireOverlay(FLnet/minecraft/client/util/math/MatrixStack;)V",
          at = @At("HEAD"),
          cancellable = true
      )
      private void onRenderFire(float tickDelta, MatrixStack matrices, CallbackInfo ci) {
          if (ModuleManager.isEnabled("NoRender")
                  && ModuleManager.get("NoRender").getSetting("fire")) {
              ci.cancel();
          }
      }
  }
  