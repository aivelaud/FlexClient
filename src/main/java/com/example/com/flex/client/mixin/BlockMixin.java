package com.flex.client.mixin;

import com.flex.client.module.ModuleManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.block.Block.class)
public class BlockMixin {

    // ============================================================
    //  XRAY - Gosterilecek bloklar (tier'a gore gruplandı)
    // ============================================================

    // TIER 1 - En degerli (her zaman goster)
    private static boolean isDiamond(Block b) {
        return b == Blocks.DIAMOND_ORE
            || b == Blocks.DEEPSLATE_DIAMOND_ORE;
    }

    // TIER 2 - Cok degerli
    private static boolean isTopTier(Block b) {
        return b == Blocks.ANCIENT_DEBRIS          // Netherite ham maddesi
            || b == Blocks.NETHER_GOLD_ORE         // Nether altin
            || b == Blocks.GILDED_BLACKSTONE;      // Gilded blackstone
    }

    // TIER 3 - Degerli
    private static boolean isMidTier(Block b) {
        return b == Blocks.GOLD_ORE
            || b == Blocks.DEEPSLATE_GOLD_ORE
            || b == Blocks.EMERALD_ORE
            || b == Blocks.DEEPSLATE_EMERALD_ORE
            || b == Blocks.REDSTONE_ORE
            || b == Blocks.DEEPSLATE_REDSTONE_ORE;
    }

    // TIER 4 - Yaygin ama kullanisli
    private static boolean isLowTier(Block b) {
        return b == Blocks.IRON_ORE
            || b == Blocks.DEEPSLATE_IRON_ORE
            || b == Blocks.COPPER_ORE
            || b == Blocks.DEEPSLATE_COPPER_ORE
            || b == Blocks.LAPIS_ORE
            || b == Blocks.DEEPSLATE_LAPIS_ORE
            || b == Blocks.COAL_ORE
            || b == Blocks.DEEPSLATE_COAL_ORE;
    }

    // TIER 5 - Yapi / ozel bloklar
    private static boolean isStructure(Block b) {
        return b == Blocks.CHEST
            || b == Blocks.TRAPPED_CHEST
            || b == Blocks.ENDER_CHEST
            || b == Blocks.BARREL
            || b == Blocks.SPAWNER              // Mob spawner
            || b == Blocks.OBSIDIAN
            || b == Blocks.CRYING_OBSIDIAN
            || b == Blocks.BEDROCK;             // Bedrock seviyesi tespiti
            // NOT: TRIAL_SPAWNER ve VAULT 1.21+ icin, bu proje 1.20.1
    }

    // Tum gosterilecek bloklar
    private static boolean isXrayVisible(Block b) {
        return isDiamond(b)
            || isTopTier(b)
            || isMidTier(b)
            || isLowTier(b)
            || isStructure(b);
    }

    // ============================================================
    //  MIXIN - shouldDrawSide inject
    // ============================================================
    @Inject(at = @At("HEAD"), method = "shouldDrawSide", cancellable = true)
    private static void onShouldDrawSide(
            BlockState state,
            BlockView world,
            BlockPos pos,
            Direction side,
            BlockPos otherPos,
            CallbackInfoReturnable<Boolean> cir) {

        // Null guvenligi - PojavLauncher'da world yuklenmeden tetiklenebilir
        if (state == null || world == null || pos == null) return;

        if (ModuleManager.isEnabled("Xray")) {
            Block block = state.getBlock();

            if (isXrayVisible(block)) {
                // Bu blok degerli → her zaman ciz (duvardan gozuk)
                cir.setReturnValue(true);
            } else {
                // Diger her sey → gizle (saydam yap)
                cir.setReturnValue(false);
            }
        }
        // Xray kapali ise hicbir seye dokunma, vanilla davranis devam eder
    }
}

