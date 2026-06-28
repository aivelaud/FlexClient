package com.flex.client.xray;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.Set;

/**
 * DimensionBlockValidator — Boyut-Blok Uyumluluk Filtresi
 *
 * Anti-xray Engine Mode 2, sahte blok olarak Nether cevherlerini
 * Overworld chunk'larına yerleştirebilir (Ancient Debris, Nether Gold, Quartz vb.).
 * Bu bloklar Overworld'de DOĞAL OLARAK HİÇBİR ZAMAN oluşmaz → %100 sahte.
 *
 * Bu filtre en hızlı kontrol olduğu için AntiXrayFilter'ın ilk katmanıdır.
 * O(1) complexity — hash set lookup.
 *
 * Sonuç:
 *  - Overworld'deki Nether bloklarını tek seferde temizler
 *  - AncientDebris, NetherGold, Quartz → Overworld'de görünüyorsa kesin sahte
 */
public final class DimensionBlockValidator {

    // ── Overworld'de OLMAMASI gereken bloklar ────────────────────────────────
    private static final Set<Block> NETHER_ONLY = Set.of(
        Blocks.ANCIENT_DEBRIS,
        Blocks.NETHER_GOLD_ORE,
        Blocks.NETHER_QUARTZ_ORE,
        Blocks.MAGMA_BLOCK,
        Blocks.NETHER_BRICK,
        Blocks.NETHER_BRICKS,
        Blocks.BASALT,
        Blocks.BLACKSTONE,
        Blocks.SOUL_SAND,
        Blocks.SOUL_SOIL,
        Blocks.GLOWSTONE,
        Blocks.CRIMSON_NYLIUM,
        Blocks.WARPED_NYLIUM
    );

    // ── Nether'de OLMAMASI gereken Overworld cevherleri ──────────────────────
    private static final Set<Block> OVERWORLD_ORE_ONLY = Set.of(
        Blocks.DIAMOND_ORE,
        Blocks.DEEPSLATE_DIAMOND_ORE,
        Blocks.EMERALD_ORE,
        Blocks.DEEPSLATE_EMERALD_ORE,
        Blocks.COAL_ORE,
        Blocks.DEEPSLATE_COAL_ORE,
        Blocks.IRON_ORE,
        Blocks.DEEPSLATE_IRON_ORE,
        Blocks.GOLD_ORE,
        Blocks.DEEPSLATE_GOLD_ORE,
        Blocks.COPPER_ORE,
        Blocks.DEEPSLATE_COPPER_ORE,
        Blocks.LAPIS_ORE,
        Blocks.DEEPSLATE_LAPIS_ORE,
        Blocks.REDSTONE_ORE,
        Blocks.DEEPSLATE_REDSTONE_ORE,
        Blocks.AMETHYST_CLUSTER
    );

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Verilen bloğun bu boyutta doğal olarak bulunup bulunamayacağını kontrol eder.
     *
     * @param block     Kontrol edilecek blok türü
     * @param dimension Boyut registry key (World.OVERWORLD, World.NETHER, World.END)
     * @return true → bu blok bu boyutta geçerli (gerçek olabilir)
     *         false → bu blok bu boyutta OLMAZ, kesinlikle sahte
     */
    public static boolean isValid(Block block, RegistryKey<World> dimension) {
        try {
            if (dimension == World.OVERWORLD) {
                // Overworld'de Nether bloğu → kesinlikle sahte
                if (NETHER_ONLY.contains(block)) return false;
            } else if (dimension == World.NETHER) {
                // Nether'de Overworld cevheri → kesinlikle sahte
                if (OVERWORLD_ORE_ONLY.contains(block)) return false;
            }
            // END boyutunda herhangi bir cevher → sahte
            else if (dimension == World.END) {
                if (OVERWORLD_ORE_ONLY.contains(block) || NETHER_ONLY.contains(block)) {
                    return false;
                }
            }
        } catch (Exception ignored) {
            // Null veya bilinmeyen boyut → güvenli taraf: geçerli say
        }
        return true;
    }

    /**
     * Blok Nether'e özgü mü?
     *
     * @param block Kontrol edilecek blok
     * @return true → Nether'e özgü
     */
    public static boolean isNetherOnly(Block block) {
        return NETHER_ONLY.contains(block);
    }

    /**
     * Blok Overworld cevheri mi?
     *
     * @param block Kontrol edilecek blok
     * @return true → Overworld cevheri
     */
    public static boolean isOverworldOre(Block block) {
        return OVERWORLD_ORE_ONLY.contains(block);
    }

    private DimensionBlockValidator() {}
}
