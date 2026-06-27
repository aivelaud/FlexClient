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
   * FovMixin — MC 1.20.1 uyumlu Zoom implementasyonu.
   *
   * MC 1.20.1'de getFov metodu MinecraftClient'ta YOKTUR.
   * Dogru hedef: GameRenderer.getFov(Camera, float, boolean) -> double
   * (Yarn 1.20.1+build.10)
   */
  @Mixin(GameRenderer.class)
  public class FovMixin {

      @Inject(at = @At("RETURN"), method = "getFov", cancellable = true)
      private void onGetFov(Camera camera, float tickDelta, boolean changingFov,
                            CallbackInfoReturnable<Double> cir) {
          if (!ModuleManager.isEnabled("Zoom")) return;
          MinecraftClient mc = MinecraftClient.getInstance();
          if (mc == null) return;
          long win = mc.getWindow().getHandle();
          boolean zHeld = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_Z) == GLFW.GLFW_PRESS;
          if (zHeld) {
              cir.setReturnValue(10.0);
          }
      }
  }
  