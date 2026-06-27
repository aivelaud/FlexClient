package com.flex.client.mixin;

  import net.minecraft.client.render.Camera;
  import net.minecraft.client.render.GameRenderer;
  import org.spongepowered.asm.mixin.Mixin;
  import org.spongepowered.asm.mixin.injection.At;
  import org.spongepowered.asm.mixin.injection.Inject;
  import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

  /**
   * FovMixin — MC 1.20.1 Yarn 1.20.1+build.10 uyumlu.
   * Tam metot imzası: getFov(Lnet/minecraft/client/render/Camera;FZ)D
   */
  @Mixin(GameRenderer.class)
  public class FovMixin {

      private static boolean zoomActive = false;
      private static double  currentFov = -1.0;

      @Inject(
          method = "getFov(Lnet/minecraft/client/render/Camera;FZ)D",
          at = @At("RETURN"),
          cancellable = true
      )
      private void onGetFov(Camera camera, float tickDelta, boolean changingFov,
                            CallbackInfoReturnable<Double> cir) {
          double orig = cir.getReturnValue();
          if (currentFov < 0) currentFov = orig;

          double target = zoomActive ? 10.0 : orig;
          currentFov += (target - currentFov) * 0.15;
          if (Math.abs(currentFov - target) < 0.05) currentFov = target;

          if (zoomActive || Math.abs(currentFov - orig) > 0.05) {
              cir.setReturnValue(currentFov);
          }
      }

      public static void setZoom(boolean active) {
          zoomActive = active;
      }
  }
  