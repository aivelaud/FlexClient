package com.flex.client.mixin;

  import com.flex.client.module.ModuleManager;
  import net.minecraft.client.MinecraftClient;
  import net.minecraft.client.render.Camera;
  import net.minecraft.client.render.GameRenderer;
  import org.spongepowered.asm.mixin.Mixin;
  import org.spongepowered.asm.mixin.injection.At;
  import org.spongepowered.asm.mixin.injection.Inject;
  import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
  import org.lwjgl.glfw.GLFW;

  /**
   * FovMixin — MC 1.20.1 uyumlu Smooth Zoom implementasyonu.
   *
   * Hedef: GameRenderer.getFov(Camera, float, boolean) -> double
   * (Yarn 1.20.1+build.10)
   *
   * Zoom aktifken FOV, ani kesme yerine lerp (doğrusal interpolasyon)
   * ile hedef değere yumuşakça geçer.
   */
  @Mixin(GameRenderer.class)
  public class FovMixin {

      private static final double ZOOM_FOV    = 10.0;
      private static final double ZOOM_SPEED  = 0.15; // Lerp faktörü (0=anında, 1=çok yavaş)

      private double currentFov = -1.0; // -1 = henüz başlatılmadı

      @Inject(at = @At("RETURN"), method = "getFov", cancellable = true)
      private void onGetFov(Camera camera, float tickDelta, boolean changingFov,
                            CallbackInfoReturnable<Double> cir) {
          MinecraftClient mc = MinecraftClient.getInstance();
          if (mc == null) return;

          double originalFov = cir.getReturnValue();

          if (currentFov < 0) {
              currentFov = originalFov;
          }

          long win = mc.getWindow().getHandle();
          boolean zHeld = ModuleManager.isEnabled("Zoom")
                       && GLFW.glfwGetKey(win, GLFW.GLFW_KEY_Z) == GLFW.GLFW_PRESS;

          double targetFov = zHeld ? ZOOM_FOV : originalFov;

          // Lerp: currentFov + (target - currentFov) * speed
          currentFov = currentFov + (targetFov - currentFov) * ZOOM_SPEED;

          // Hedeften 0.05 dereceden yakınsa sabitle (titreme önleme)
          if (Math.abs(currentFov - targetFov) < 0.05) {
              currentFov = targetFov;
          }

          // Sadece zoom aktifse veya hâlâ animasyon devam ediyorsa override et
          if (zHeld || Math.abs(currentFov - originalFov) > 0.05) {
              cir.setReturnValue(currentFov);
          }
      }
  }
  