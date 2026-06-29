package com.flex.client.xray;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * OreValidator — Y aralığı ve boyut doğrulama.
 *
 * AntiXrayFilter'dan bağımsız tutulan bu sınıf, bir bloğun
 * doğal spawn koşullarına uyup uymadığını kontrol eder.
 *
 * Bu iki kural Paper Mode 2 sahtelerinin büyük çoğunluğunu eler:
 *  - Overworld'de Ancient Debris → kesinlikle sahte
 *  - Nether'de Diamond → kesinlikle sahte
 *  - Diamond Y=200'de → kesinlikle sahte
 */
public final class OreValidator {

    // ── Y aralığı tablosu {minY, maxY} — Minecraft 1.20.1 vanilla ────────────
    private static final Map<Block, int[]> Y_RANGES = new HashMap<>();

    static {
        Y_RANGES.put(Blocks.COAL_ORE,               new int[]{   0, 192});
        Y_RANGES.put(Blocks.DEEPSLATE_COAL_ORE,     new int[]{ -64,   0});
        Y_RANGES.put(Blocks.IRON_ORE,               new int[]{ -64,  72});
        Y_RANGES.put(Blocks.DEEPSLATE_IRON_ORE,     new int[]{ -64,  72});
        Y_RANGES.put(Blocks.COPPER_ORE,             new int[]{ -16, 112});
        Y_RANGES.put(Blocks.DEEPSLATE_COPPER_ORE,   new int[]{ -16,  16});
        Y_RANGES.put(Blocks.GOLD_ORE,               new int[]{ -64,  32});
        Y_RANGES.put(Blocks.DEEPSLATE_GOLD_ORE,     new int[]{ -64,  32});
        Y_RANGES.put(Blocks.REDSTONE_ORE,           new int[]{ -64,  16});
        Y_RANGES.put(Blocks.DEEPSLATE_REDSTONE_ORE, new int[]{ -64,  16});
        Y_RANGES.put(Blocks.LAPIS_ORE,              new int[]{ -64,  64});
        Y_RANGES.put(Blocks.DEEPSLATE_LAPIS_ORE,    new int[]{ -64,  64});
        Y_RANGES.put(Blocks.DIAMOND_ORE,            new int[]{ -64,  16});
        Y_RANGES.put(Blocks.DEEPSLATE_DIAMOND_ORE,  new int[]{ -64,  16});
        Y_RANGES.put(Blocks.EMERALD_ORE,            new int[]{ -16, 320});
        Y_RANGES.put(Blocks.DEEPSLATE_EMERALD_ORE,  new int[]{ -16, 320});
        Y_RANGES.put(Blocks.ANCIENT_DEBRIS,         new int[]{   8, 119});
        Y_RANGES.put(Blocks.NETHER_GOLD_ORE,        new int[]{  10, 117});
        Y_RANGES.put(Blocks.NETHER_QUARTZ_ORE,      new int[]{  10, 117});
        // Y kısıtlaması olmayan bloklar → null kayıt yok, haritada olmayanlar geçer
    }

    // ── Boyut setleri ─────────────────────────────────────────────────────────

    private static final Set<Block> NETHER_ONLY = Set.of(
        Blocks.ANCIENT_DEBRIS,
        Blocks.NETHER_GOLD_ORE,
        Blocks.NETHER_QUARTZ_ORE
    );

    private static final Set<Block> OVERWORLD_ONLY = Set.of(
        Blocks.DIAMOND_ORE,          Blocks.DEEPSLATE_DIAMOND_ORE,
        Blocks.IRON_ORE,             Blocks.DEEPSLATE_IRON_ORE,
        Blocks.GOLD_ORE,             Blocks.DEEPSLATE_GOLD_ORE,
        Blocks.COAL_ORE,             Blocks.DEEPSLATE_COAL_ORE,
        Blocks.COPPER_ORE,           Blocks.DEEPSLATE_COPPER_ORE,
        Blocks.LAPIS_ORE,            Blocks.DEEPSLATE_LAPIS_ORE,
        Blocks.REDSTONE_ORE,         Blocks.DEEPSLATE_REDSTONE_ORE,
        Blocks.EMERALD_ORE,          Blocks.DEEPSLATE_EMERALD_ORE
    );

    // ── API ───────────────────────────────────────────────────────────────────

    /**
     * Bloğun mevcut dünya boyutunda var olmasının mümkün olup olmadığını kontrol eder.
     *
     * @param block Kontrol edilecek blok
     * @param world Mevcut dünya
     * @return true → boyut uyumlu (geçerli), false → imkansız (sahte)
     */
    public static boolean isDimensionValid(Block block, ClientWorld world) {
        RegistryKey<World> dim = world.getRegistryKey();
        if (dim == World.OVERWORLD && NETHER_ONLY.contains(block))   return false;
        if (dim == World.NETHER    && OVERWORLD_ONLY.contains(block)) return false;
        return true;
    }

    /**
     * Bloğun verilen Y koordinatında doğal olarak spawn olmasının mümkün
     * olup olmadığını kontrol eder.
     *
     * @param block Kontrol edilecek blok
     * @param y     Y koordinatı
     * @return true → Y geçerli, false → vanilla spawn aralığı dışı (sahte)
     */
    public static boolean isYValid(Block block, int y) {
        int[] range = Y_RANGES.get(block);
        if (range == null) return true; // Bilinmeyen blok → geçir (güvenli taraf)
        return y >= range[0] && y <= range[1];
    }

    private OreValidator() {}
}
