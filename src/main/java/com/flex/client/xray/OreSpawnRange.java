package com.flex.client.xray;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

import java.util.HashMap;
import java.util.Map;

/**
 * OreSpawnRange — Cevher Doğal Y-Aralığı Filtresi (V2 — Daha Katı)
 *
 * Minecraft 1.20.1 vanilla spawn tablosundan alınan kesin Y aralıkları.
 * Bu aralık dışındaki bloklar %100 sahte olarak işaretlenir.
 *
 * V1'den farklar:
 *  - Daha dar aralıklar (tolerans kaldırıldı)
 *  - Deepslate varyantları için özel kontrol (Y < 0)
 *  - isInRange() direkt false döner aralık dışı için
 *  - Enum tabanlı yapı yerine Map kullanımı (daha hızlı lookup)
 */
public final class OreSpawnRange {

    public static final class Range {
        public final int minY;
        public final int maxY;
        public final int peakY;

        public Range(int minY, int maxY, int peakY) {
            this.minY  = minY;
            this.maxY  = maxY;
            this.peakY = peakY;
        }

        /**
         * Y seviyesinin bu aralıkta olup olmadığını döner.
         * Tolerans uygulanmaz — kesin sınır kontrolü.
         *
         * @param y Kontrol edilecek Y
         * @return true → geçerli, false → kesinlikle sahte
         */
        public boolean isInRange(int y) {
            return y >= minY && y <= maxY;
        }

        /**
         * Peak'e yakınlık skoru (0.0-1.0).
         * AntiXrayFilter'ın skor hesabında kullanılır.
         */
        public double peakProximity(int y) {
            if (!isInRange(y)) return 0.0;
            int span = Math.max(1, Math.max(Math.abs(maxY - peakY), Math.abs(peakY - minY)));
            return 1.0 - Math.min(1.0, (double) Math.abs(y - peakY) / span);
        }
    }

    private static final Map<Block, Range> RANGES = new HashMap<>();

    static {
        // ── Overworld ────────────────────────────────────────────────────────
        RANGES.put(Blocks.DIAMOND_ORE,              new Range(-64,  16, -58));
        RANGES.put(Blocks.DEEPSLATE_DIAMOND_ORE,    new Range(-64,   0, -58));
        RANGES.put(Blocks.IRON_ORE,                 new Range(-64,  72,  15));
        RANGES.put(Blocks.DEEPSLATE_IRON_ORE,       new Range(-64,   0,  15));
        RANGES.put(Blocks.GOLD_ORE,                 new Range(-64,  32, -16));
        RANGES.put(Blocks.DEEPSLATE_GOLD_ORE,       new Range(-64,   0, -16));
        RANGES.put(Blocks.COPPER_ORE,               new Range(-16, 112,  48));
        RANGES.put(Blocks.DEEPSLATE_COPPER_ORE,     new Range(-16,   0,  48));
        RANGES.put(Blocks.LAPIS_ORE,                new Range(-64,  64,   0));
        RANGES.put(Blocks.DEEPSLATE_LAPIS_ORE,      new Range(-64,   0,   0));
        RANGES.put(Blocks.REDSTONE_ORE,             new Range(-64,  16, -58));
        RANGES.put(Blocks.DEEPSLATE_REDSTONE_ORE,   new Range(-64,   0, -58));
        RANGES.put(Blocks.COAL_ORE,                 new Range(  0, 192,  96));
        RANGES.put(Blocks.DEEPSLATE_COAL_ORE,       new Range(  0,   0,  96));
        RANGES.put(Blocks.EMERALD_ORE,              new Range(-16, 320, 236));
        RANGES.put(Blocks.DEEPSLATE_EMERALD_ORE,    new Range(-16,   0, -16));

        // ── Nether ───────────────────────────────────────────────────────────
        RANGES.put(Blocks.ANCIENT_DEBRIS,           new Range(  8, 119,  15));
        RANGES.put(Blocks.NETHER_GOLD_ORE,          new Range( 10, 117,  20));
        RANGES.put(Blocks.NETHER_QUARTZ_ORE,        new Range( 10, 117,  25));

        // ── Özel (her Y'de olabilir) ──────────────────────────────────────────
        RANGES.put(Blocks.CHEST,                    new Range(-64, 320,   0));
        RANGES.put(Blocks.TRAPPED_CHEST,            new Range(-64, 320,   0));
        RANGES.put(Blocks.ENDER_CHEST,              new Range(-64, 320,   0));
        RANGES.put(Blocks.SPAWNER,                  new Range(-64, 320,   0));
        RANGES.put(Blocks.AMETHYST_CLUSTER,         new Range(-64, 320,   0));
        RANGES.put(Blocks.OBSIDIAN,                 new Range(-64,  20, -10));
        RANGES.put(Blocks.CRYING_OBSIDIAN,          new Range(-64, 320,   0));
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public static Range getRange(Block block) {
        return RANGES.get(block);
    }

    /**
     * Verilen blok/Y kombinasyonunun geçerli olup olmadığını döner.
     * Bilinmeyen bloklar için true döner (güvenli taraf).
     *
     * @param block Cevher bloğu
     * @param y     Y koordinatı
     * @return true → geçerli veya bilinmiyor, false → kesinlikle yanlış Y
     */
    public static boolean isInRange(Block block, int y) {
        Range r = RANGES.get(block);
        return r == null || r.isInRange(y);
    }

    public static double peakProximity(Block block, int y) {
        Range r = RANGES.get(block);
        return r == null ? 0.5 : r.peakProximity(y);
    }

    private OreSpawnRange() {}
}
