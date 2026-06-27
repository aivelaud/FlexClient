package com.flex.client.mixin;

  import net.minecraft.client.render.Camera;
  import org.spongepowered.asm.mixin.Mixin;

  /**
   * CameraMixin — Şu an aktif inject yok.
   *
   * update() inject'i kaldırıldı: gövdesi tamamen boştu ve
   * 1.20.1'de Camera.update descriptor'ı refmap hatasına yol açıyordu.
   * flexclient.mixins.json'dan da çıkarıldı.
   *
   * İleride FreeLook için buraya inject eklenebilir.
   */
  @Mixin(Camera.class)
  public abstract class CameraMixin {
      // Aktif inject yok — ileriki geliştirmeler için ayrılmıştır
  }
  