package com.flex.client.xray;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

import java.util.HashMap;
import java.util.Map;

/**
 * OreSpawnRange — Her cevher türü için doğal spawn Y aralığını tutar.
 *
 * Minecraft 1.20.1 vanilla spawn tablosundan alınan değerler.
 * AntiXrayFilter tarafından Y-seviyesi doğrulama katmanında kullanılır.
 */
public final class OreSpawnRange {

    /** Bir cevher türü için Y aralığı verisi. */
    public static final class Range {
        /** Minimum doğal spawn Y (dahil). */
        public final int minY;
        /** Maximum doğal spawn Y (dahil). */
        public final int maxY;
        /** En yoğun oluşum Y seviyesi. */
        public final int peakY;
        /** Bir blok bu aralık dışındaysa sahte sayılır. */
        public final int tolerance; // aralık dışı tolerans (blok)

        public Range(int minY, int maxY, int peakY, int tolerance) {
            this.minY      = minY;
            this.maxY      = maxY;
            this.peakY     = peakY;
            this.tolerance = tolerance;
        }

        /**
         * Verilen Y seviyesinin bu cevher için geçerli olup olmadığını döner.
         * Tolerans değeri dahilinde sınır dışı Y'lere izin verilir.
         *
         * @param y Kontrol edilecek Y koordinatı
         * @return true → geçerli (gerçek olabilir), false → geçersiz (sahte aday)
         */
        public boolean isInRange(int y) {
            return y >= (minY - tolerance) && y <= (maxY + tolerance);
        }

        /**
         * Verilen Y'nin peak'e ne kadar yakın olduğunu 0-1 arasında döner.
         * 1.0 = tam peak'te, 0.0 = aralığın en ucunda.
         */
        public double peakProximity(int y) {
            if (!isInRange(y)) return 0.0;
            int span = Math.max(1, Math.max(Math.abs(maxY - peakY), Math.abs(peakY - minY)));
            return 1.0 - Math.min(1.0, (double) Math.abs(y - peakY) / span);
        }
    }

    // Block → Range haritası
    private static final Map<Block, Range> RANGES = new HashMap<>();

    static {
        // ── Overworld cevherleri ─────────────────────────────────────────────
        // Diamond (deepslate ve normal)
        RANGES.put(Blocks.DIAMOND_ORE,             new Range(-64,  16, -58, 4));
        RANGES.put(Blocks.DEEPSLATE_DIAMOND_ORE,   new Range(-64,   0, -58, 4));

        // Iron (tüm dünyada ama pik Y=15 ve Y=232)
        RANGES.put(Blocks.IRON_ORE,                new Range(-64, 320,  15, 8));
        RANGES.put(Blocks.DEEPSLATE_IRON_ORE,      new Range(-64,   0,  15, 8));

        // Gold
        RANGES.put(Blocks.GOLD_ORE,                new Range(-64,  32, -16, 4));
        RANGES.put(Blocks.DEEPSLATE_GOLD_ORE,      new Range(-64,   0, -16, 4));

        // Copper
        RANGES.put(Blocks.COPPER_ORE,              new Range(-16, 112,  48, 8));
        RANGES.put(Blocks.DEEPSLATE_COPPER_ORE,    new Range(-16,   0,  48, 8));

        // Lapis
        RANGES.put(Blocks.LAPIS_ORE,               new Range(-64,  64,   0, 4));
        RANGES.put(Blocks.DEEPSLATE_LAPIS_ORE,     new Range(-64,   0,   0, 4));

        // Redstone
        RANGES.put(Blocks.REDSTONE_ORE,            new Range(-64,  16, -58, 4));
        RANGES.put(Blocks.DEEPSLATE_REDSTONE_ORE,  new Range(-64,   0, -58, 4));

        // Emerald (sadece dağ biyomları — geniş tolerans)
        RANGES.put(Blocks.EMERALD_ORE,             new Range(-16, 320, 236, 16));
        RANGES.put(Blocks.DEEPSLATE_EMERALD_ORE,   new Range(-16,   0, -16, 16));

        // Coal
        RANGES.put(Blocks.COAL_ORE,                new Range(  0, 320,  96, 8));
        RANGES.put(Blocks.DEEPSLATE_COAL_ORE,      new Range(  0,   0,  96, 8));

        // ── Nether cevherleri ────────────────────────────────────────────────
        RANGES.put(Blocks.ANCIENT_DEBRIS,          new Range(  8, 119,  15, 2));
        RANGES.put(Blocks.NETHER_GOLD_ORE,         new Range( 10, 117,  20, 4));
        RANGES.put(Blocks.NETHER_QUARTZ_ORE,       new Range( 10, 117,  25, 4));

        // ── Özel bloklar (her Y'de olabilir — geniş tolerans) ───────────────
        RANGES.put(Blocks.CHEST,                   new Range(-64, 320,   0, 64));
        RANGES.put(Blocks.TRAPPED_CHEST,           new Range(-64, 320,   0, 64));
        RANGES.put(Blocks.ENDER_CHEST,             new Range(-64, 320,   0, 64));
        RANGES.put(Blocks.SPAWNER,                 new Range(-64, 320,   0, 64));
        RANGES.put(Blocks.AMETHYST_CLUSTER,        new Range(-64, 320,   0, 64));
        RANGES.put(Blocks.OBSIDIAN,                new Range(-64,  20, -10, 8));
        RANGES.put(Blocks.CRYING_OBSIDIAN,         new Range(-64, 320,   0, 64));
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Verilen blok için spawn aralığını döner.
     *
     * @param block Kontrol edilecek blok türü
     * @return Range nesnesi, bilinmiyorsa null
     */
    public static Range getRange(Block block) {
        return RANGES.get(block);
    }

    /**
     * Verilen blok/Y kombinasyonunun geçerli olup olmadığını döner.
     * Bilinmeyen bloklar için her zaman true döner (güvenli taraf).
     *
     * @param block Cevher bloğu
     * @param y     Y koordinatı
     * @return true → geçerli veya bilinmiyor, false → kesinlikle yanlış Y
     */
    public static boolean isInRange(Block block, int y) {
        Range r = RANGES.get(block);
        return r == null || r.isInRange(y);
    }

    /**
     * Peak Y yakınlığını döner (anti-xray skor hesabında kullanılır).
     *
     * @param block Cevher bloğu
     * @param y     Y koordinatı
     * @return 0.0-1.0 arası yakınlık, bilinmiyorsa 0.5
     */
    public static double peakProximity(Block block, int y) {
        Range r = RANGES.get(block);
        return r == null ? 0.5 : r.peakProximity(y);
    }

    private OreSpawnRange() {}
}
