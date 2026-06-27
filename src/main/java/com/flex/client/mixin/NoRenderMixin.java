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
   *
   * NOT: method = "renderFireOverlay" yeterlidir.
   * Full descriptor (FLnet/.../MatrixStack;)V yazildiginda Mixin AP,
   * icindeki Yarn tiplerini refmap'e isleme sirasinda cozemedigi icin
   * InvalidInjectionException atar. Sadece metot adi kullanilinca
   * Loom refmap'i otomatik uretir.
   */
  @Mixin(GameRenderer.class)
  public abstract class NoRenderMixin {

      @Inject(
          method = "renderFireOverlay",
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
  