package com.flex.client.mixin;

  import com.flex.client.module.ModuleManager;
  import net.minecraft.client.MinecraftClient;
  import net.minecraft.client.render.GameRenderer;
  import net.minecraft.client.util.math.MatrixStack;
  import org.spongepowered.asm.mixin.Mixin;
  import org.spongepowered.asm.mixin.injection.At;
  import org.spongepowered.asm.mixin.injection.Inject;
  import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

  /**
   * GameRendererMixin — MC 1.20.1 uyumlu.
   * Tam imza: renderWorld(FJLnet/minecraft/client/util/math/MatrixStack;)V
   */
  @Mixin(GameRenderer.class)
  public class GameRendererMixin {

      @Inject(
          method = "renderWorld(FJLnet/minecraft/client/util/math/MatrixStack;)V",
          at = @At("HEAD")
      )
      private void onRenderWorld(float tickDelta, long limitTime,
                                 MatrixStack matrix, CallbackInfo ci) {
          if (!ModuleManager.isEnabled("NoHurtCam")) return;
          MinecraftClient mc = MinecraftClient.getInstance();
          if (mc.player != null) {
              mc.player.hurtTime = 0;
          }
      }
  }
  