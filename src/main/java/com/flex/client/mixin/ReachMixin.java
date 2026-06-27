package com.flex.client.mixin;

import com.flex.client.module.Module;
import com.flex.client.module.ModuleManager;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ReachMixin — MC 1.20.1 uyumlu uzatilmis erisim mesafesi.
 *
 * MC 1.20.1'de getBlockInteractionRange / getEntityInteractionRange
 * YOKTUR (1.20.5+ ile geldi). Dogru hedef:
 *   ClientPlayerInteractionManager.hasExtendedReach()
 *   -> true donunce creative-mode uzakligi (5 blok) aktif olur.
 *
 * Ek olarak getBlockBreakingSpeed uzerinden SpeedMine ile entegrasyon
 * saglanabilir. Reach modulu icin float "range" ayari Module.java'da tanimli.
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class ReachMixin {

    /**
     * hasExtendedReach() true donunce MC, blok/entity erisimine
     * creative-mode mesafesini (5.0 blok) uygular.
     * Reach modulu aktifken her zaman true donduruyoruz.
     */
    @Inject(method = "hasExtendedReach", at = @At("HEAD"), cancellable = true)
    private void onHasExtendedReach(CallbackInfoReturnable<Boolean> cir) {
        if (ModuleManager.isEnabled("Reach")) {
            cir.setReturnValue(true);
        }
    }
}
