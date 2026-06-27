package com.flex.client.mixin;

import com.flex.client.module.ModuleManager;
import com.flex.client.xray.AntiXrayBypass;
import com.flex.client.xray.XrayConfig;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * FlexClient Ultra Xray — Block Opacity Mixin
 *
 * Bu mixin, Xray aktifken hedef olmayan blokların "opak" olduğunu bildirmez.
 * Sonuç: Minecraft renderer, cevher bloklarının çevrili olduğunu görmez ve
 * tüm yüzlerini render eder — cevherler duvarlar arkasından görünür hale gelir.
 *
 * Ayrıca Anti-Xray bypass ile tespit edilen sahte blokları gizler.
 */
@Mixin(AbstractBlock.AbstractBlockState.class)
public class MixinBlockOpacity {

    /**
     * isOpaqueFullCube — Xray aktifken non-target bloklar için false döner.
     * Bu, chunk render sırasında target blokların çevre yüzlerinin çizilmesini sağlar.
     */
    @Inject(at = @At("HEAD"), method = "isOpaqueFullCube", cancellable = true)
    private void flexXrayOpaqueCheck(BlockView world, BlockPos pos,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (!ModuleManager.isEnabled("Xray")) return;

        BlockState state = (BlockState)(Object)this;
        Block b = state.getBlock();

        // Hedef blok (cevher): opak olarak kal — render edilsin
        if (XrayConfig.isTargetBlock(b)) {
            // Ama sahte ise gizle
            if (world != null && pos != null) {
                ChunkPos cp = new ChunkPos(pos);
                if (AntiXrayBypass.isFakeBlock(pos, cp)) {
                    cir.setReturnValue(false); // Sahte cevher — gizle
                }
            }
            return; // Gerçek cevher — doğal opak durumda bırak
        }

        // Non-target: opak değil döndür → renderer tüm komşu yüzleri çizer
        // Bu sayede cevherler etraflarındaki stone/deepslate arkasından görünür
        cir.setReturnValue(false);
    }

    /**
     * isSolidBlock — Renderer'ın face culling kararı için kullanılır.
     * Xray aktifken non-target bloklar solid değil sayılır.
     */
    @Inject(at = @At("HEAD"), method = "isSolidBlock", cancellable = true)
    private void flexXraySolidCheck(BlockView world, BlockPos pos,
                                     CallbackInfoReturnable<Boolean> cir) {
        if (!ModuleManager.isEnabled("Xray")) return;

        BlockState state = (BlockState)(Object)this;
        Block b = state.getBlock();

        if (XrayConfig.isTargetBlock(b)) return; // Cevherlere dokunma

        // Non-target bloklar solid değil — yüz culling devre dışı
        cir.setReturnValue(false);
    }

      /**
       * getLuminance — Xray aktifken hedef cevher blokları max ışık yayar (15).
       * Bu sayede cevherler uzaktan siyah görünmez, kendi ışıklarıyla parlayarak
       * çevre aydınlatmasından bağımsız görünür hale gelir.
       */
      @Inject(at = @At("HEAD"), method = "getLuminance", cancellable = true)
      private void flexXrayLuminance(CallbackInfoReturnable<Integer> cir) {
          if (!ModuleManager.isEnabled("Xray")) return;
          BlockState state = (BlockState)(Object)this;
          if (XrayConfig.isTargetBlock(state.getBlock())) {
              cir.setReturnValue(15); // Maksimum parlaklık — cevherler her zaman görünür
          }
      }
  
}