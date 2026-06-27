package com.flex.client.mixin;

import com.flex.client.module.Module;
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

/**
 * BlockMixin — shouldDrawSide ile Xray blok filtresi.
 * Aktif Xray modülünün seçili cevher ayarlarını (showDiamond, showGold…) okur.
 */
@Mixin(net.minecraft.block.Block.class)
public class BlockMixin {

    // ── Cevher grup kontrolcüleri ────────────────────────────────

    private static boolean isDiamond(Block b) {
        return b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE;
    }
    private static boolean isAncientDebris(Block b) {
        return b == Blocks.ANCIENT_DEBRIS;
    }
    private static boolean isEmerald(Block b) {
        return b == Blocks.EMERALD_ORE || b == Blocks.DEEPSLATE_EMERALD_ORE;
    }
    private static boolean isGold(Block b) {
        return b == Blocks.GOLD_ORE || b == Blocks.DEEPSLATE_GOLD_ORE
            || b == Blocks.NETHER_GOLD_ORE || b == Blocks.GILDED_BLACKSTONE;
    }
    private static boolean isIron(Block b) {
        return b == Blocks.IRON_ORE || b == Blocks.DEEPSLATE_IRON_ORE;
    }
    private static boolean isLapis(Block b) {
        return b == Blocks.LAPIS_ORE || b == Blocks.DEEPSLATE_LAPIS_ORE;
    }
    private static boolean isRedstone(Block b) {
        return b == Blocks.REDSTONE_ORE || b == Blocks.DEEPSLATE_REDSTONE_ORE;
    }
    private static boolean isCopper(Block b) {
        return b == Blocks.COPPER_ORE || b == Blocks.DEEPSLATE_COPPER_ORE;
    }
    private static boolean isCoal(Block b) {
        return b == Blocks.COAL_ORE || b == Blocks.DEEPSLATE_COAL_ORE;
    }
    private static boolean isChest(Block b) {
        return b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST
            || b == Blocks.ENDER_CHEST || b == Blocks.BARREL;
    }
    private static boolean isSpawner(Block b) {
        return b == Blocks.SPAWNER;
    }

    /**
     * Module ayarlarına göre bu bloğun Xray'de gösterilip gösterilmeyeceğini belirler.
     */
    private static boolean isXrayVisible(Block b, Module xray) {
        if (xray == null) return false;
        if (isDiamond(b))      return xray.getSetting("showDiamond");
        if (isAncientDebris(b))return xray.getSetting("showAncientDebris");
        if (isEmerald(b))      return xray.getSetting("showEmerald");
        if (isGold(b))         return xray.getSetting("showGold");
        if (isIron(b))         return xray.getSetting("showIron");
        if (isLapis(b))        return xray.getSetting("showLapis");
        if (isRedstone(b))     return xray.getSetting("showRedstone");
        if (isCopper(b))       return xray.getSetting("showCopper");
        if (isCoal(b))         return xray.getSetting("showCoal");
        if (isChest(b))        return xray.getSetting("showChests");
        if (isSpawner(b))      return xray.getSetting("showSpawner");
        return false;
    }

    // ── shouldDrawSide inject ────────────────────────────────────

    @Inject(at = @At("HEAD"), method = "shouldDrawSide", cancellable = true)
    private static void onShouldDrawSide(
            BlockState state,
            BlockView world,
            BlockPos pos,
            Direction side,
            BlockPos otherPos,
            CallbackInfoReturnable<Boolean> cir) {

        if (state == null || world == null || pos == null) return;
        if (!ModuleManager.isEnabled("Xray")) return;

        Module xray = ModuleManager.get("Xray");
        Block block = state.getBlock();

        if (isXrayVisible(block, xray)) {
            cir.setReturnValue(true);  // Hedef blok — her zaman çiz
        } else {
            cir.setReturnValue(false); // Diğerleri — gizle (saydam)
        }
    }
}
