package com.flex.client.xray;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.ChunkPos;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ChunkOreCounter — Chunk Başına Cevher Yoğunluk Takibi
 *
 * Anti-xray Engine Mode 2, chunk başına sabit miktarda sahte blok yerleştirir.
 * Bir chunk'ta 500 Diamond varsa bu matematiksel olarak imkansız → hepsi sahte.
 *
 * Bu sınıf her chunk için blok sayılarını tutar ve doğal maksimum değerlerle
 * karşılaştırarak anomali tespiti yapar.
 *
 * Doğal maksimum değerler (Minecraft 1.20.1 spawn tablosu — chunk başına):
 *  - Diamond:     12 blok/chunk
 *  - Iron:        90 blok/chunk
 *  - Gold:        50 blok/chunk
 *  - Coal:       150 blok/chunk
 *  - Copper:     120 blok/chunk
 *  - Lapis:       30 blok/chunk
 *  - Redstone:    50 blok/chunk
 *  - Emerald:     20 blok/chunk
 *  - AncientDebris: 5 blok/chunk
 *
 * Eşik: count > maxNatural * 2 → anomali (sahte bloklar var)
 */
public final class ChunkOreCounter {

    /** Chunk başına doğal maksimum blok sayısı */
    private static final Map<Block, Integer> MAX_NATURAL = new HashMap<>();

    static {
        // Overworld
        MAX_NATURAL.put(Blocks.DIAMOND_ORE,              12);
        MAX_NATURAL.put(Blocks.DEEPSLATE_DIAMOND_ORE,    12);
        MAX_NATURAL.put(Blocks.IRON_ORE,                 90);
        MAX_NATURAL.put(Blocks.DEEPSLATE_IRON_ORE,       90);
        MAX_NATURAL.put(Blocks.GOLD_ORE,                 50);
        MAX_NATURAL.put(Blocks.DEEPSLATE_GOLD_ORE,       50);
        MAX_NATURAL.put(Blocks.COAL_ORE,                150);
        MAX_NATURAL.put(Blocks.DEEPSLATE_COAL_ORE,      150);
        MAX_NATURAL.put(Blocks.COPPER_ORE,              120);
        MAX_NATURAL.put(Blocks.DEEPSLATE_COPPER_ORE,    120);
        MAX_NATURAL.put(Blocks.LAPIS_ORE,                30);
        MAX_NATURAL.put(Blocks.DEEPSLATE_LAPIS_ORE,      30);
        MAX_NATURAL.put(Blocks.REDSTONE_ORE,             50);
        MAX_NATURAL.put(Blocks.DEEPSLATE_REDSTONE_ORE,   50);
        MAX_NATURAL.put(Blocks.EMERALD_ORE,              20);
        MAX_NATURAL.put(Blocks.DEEPSLATE_EMERALD_ORE,    20);
        // Nether
        MAX_NATURAL.put(Blocks.ANCIENT_DEBRIS,            5);
        MAX_NATURAL.put(Blocks.NETHER_GOLD_ORE,          32);
        MAX_NATURAL.put(Blocks.NETHER_QUARTZ_ORE,        32);
    }

    // Chunk key → (Block → sayı) haritası
    private static final ConcurrentHashMap<Long, ConcurrentHashMap<Block, Integer>> CHUNK_COUNTS =
            new ConcurrentHashMap<>();

    // Anomali tespitinde kullanılan çarpan
    // count > maxNatural * ANOMALY_FACTOR → anomali
    private static final int ANOMALY_FACTOR = 2;

    // ── Sayaç Güncelleme ──────────────────────────────────────────────────────

    /**
     * Bir cevher bloğunu chunk sayacına ekler.
     * Chunk taraması sırasında her hedef blok için çağrılır.
     *
     * @param cp    Chunk pozisyonu
     * @param block Bulunan blok türü
     */
    public static void registerOre(ChunkPos cp, Block block) {
        long key = cp.toLong();
        CHUNK_COUNTS
            .computeIfAbsent(key, k -> new ConcurrentHashMap<>())
            .merge(block, 1, Integer::sum);
    }

    /**
     * Verilen chunk'ın blok sayaçlarını toplu olarak yükler.
     * Chunk taraması tamamlandığında bir kez çağrılır (registerOre'dan daha verimli).
     *
     * @param cp     Chunk pozisyonu
     * @param counts Block → adet haritası
     */
    public static void registerChunk(ChunkPos cp, Map<Block, Integer> counts) {
        long key = cp.toLong();
        ConcurrentHashMap<Block, Integer> chunkMap =
            CHUNK_COUNTS.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
        counts.forEach((b, c) -> chunkMap.merge(b, c, Integer::sum));
    }

    // ── Anomali Tespiti ───────────────────────────────────────────────────────

    /**
     * Verilen chunk'ta bu blok türü için anomali var mı?
     * Anomali → doğal maksimumun ANOMALY_FACTOR katından fazla blok var.
     *
     * @param cp    Chunk pozisyonu
     * @param block Kontrol edilecek blok türü
     * @return true → anomali tespit edildi (bu chunk'taki bu tür bloklar sahte)
     */
    public static boolean isChunkAnomaly(ChunkPos cp, Block block) {
        Integer maxNatural = MAX_NATURAL.get(block);
        if (maxNatural == null) return false; // Bilinmeyen blok → kontrol etme

        long key = cp.toLong();
        ConcurrentHashMap<Block, Integer> chunkMap = CHUNK_COUNTS.get(key);
        if (chunkMap == null) return false; // Sayım yok → anomali yok

        Integer count = chunkMap.get(block);
        if (count == null) return false;

        return count > maxNatural * ANOMALY_FACTOR;
    }

    /**
     * Chunk'taki bu blok türünün sayısını döner.
     *
     * @param cp    Chunk pozisyonu
     * @param block Blok türü
     * @return Blok sayısı, bilinmiyorsa 0
     */
    public static int getCount(ChunkPos cp, Block block) {
        long key = cp.toLong();
        ConcurrentHashMap<Block, Integer> chunkMap = CHUNK_COUNTS.get(key);
        if (chunkMap == null) return 0;
        return chunkMap.getOrDefault(block, 0);
    }

    /**
     * Bu blok türü için doğal maksimum değeri döner.
     *
     * @param block Blok türü
     * @return Chunk başına maksimum doğal adet, bilinmiyorsa -1
     */
    public static int getMaxNatural(Block block) {
        return MAX_NATURAL.getOrDefault(block, -1);
    }

    // ── Cache Yönetimi ────────────────────────────────────────────────────────

    /**
     * Chunk boşaltıldığında sayaçları temizler.
     *
     * @param cp Boşaltılan chunk
     */
    public static void clearChunk(ChunkPos cp) {
        CHUNK_COUNTS.remove(cp.toLong());
    }

    /**
     * Tüm sayaçları temizler.
     */
    public static void clearAll() {
        CHUNK_COUNTS.clear();
    }

    /** Toplam takip edilen chunk sayısını döner (debug). */
    public static int getTrackedChunkCount() {
        return CHUNK_COUNTS.size();
    }

    private ChunkOreCounter() {}
}
