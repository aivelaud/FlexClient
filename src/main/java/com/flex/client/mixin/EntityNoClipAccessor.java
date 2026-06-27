package com.flex.client.mixin;

  import net.minecraft.entity.Entity;
  import org.spongepowered.asm.mixin.Mixin;
  import org.spongepowered.asm.mixin.gen.Accessor;

  /**
   * Entity.noClip alanına @Shadow yerine @Accessor ile güvenli erişim.
   * @Shadow refmap sorunlarını tamamen ortadan kaldırır.
   */
  @Mixin(Entity.class)
  public interface EntityNoClipAccessor {
      @Accessor("noClip")
      void setNoClip(boolean noClip);

      @Accessor("noClip")
      boolean isNoClip();
  }
  