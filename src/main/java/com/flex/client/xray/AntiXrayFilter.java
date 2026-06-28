package com.flex.client.xray;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.LightType;
import net.minecraft.world.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AntiXrayFilter V2 — 6 Katmanlı Agresif Sahte Blok Filtresi
 *
 * V1'de sadece ~%35 sahte blok elenebiliyordu.
 * V2 hedefi: >%95 eliminasyon.
 *
 * Katman sırası (hızlıdan yavaşa):
 *
 *  KATMAN 0 — Boyut-Blok Uyumu         O(1)  Nether bloğu Overworld'de → sahte
 *  KATMAN 1 — Y-Seviyesi               O(1)  Aralık dışı → sahte
 *  KATMAN 2 — Deepslate Çakışması      O(1)  Deepslate ore Y>0 → sahte
 *  KATMAN 3 — Chunk Yoğunluk Anomalisi O(1)  Count > 2x max → sahte
 *  KATMAN 4 — Gelişmiş Komşu Analizi   O(26) 3x3x3 → açık komşu yok → sahte
 *  KATMAN 5 — BFS Küme İzolasyon       O(n)  Tamamen izole + solid → sahte
 *
 * BLOCK_UPDATE paketi alan bloklar her katmanı atlar (kesinlikle gerçek).
 */
public final class AntiXrayFilter {

    // Deepslate cevherlerin maksimum doğal Y seviyesi
    private static final int DEEPSLATE_MAX_Y = 0;

    // 3x3x3 komşu analizinde "açık" sayılmak için gereken minimum açık komşu
    // 26 komşudan en az MIN_OPEN_NEIGHBORS açık olmalı; yoksa sahte
    private static final int MIN_OPEN_NEIGHBORS_3X3 = 1;

    // BFS maksimum tarama boyutu (performans için sınır)
    private static final int BFS_MAX_SCAN = 64;

    // Doğal maksimum küme büyüklükleri (Block → max blok)
    private static final Map<Block, Integer> MAX_CLUSTER = new HashMap<>();

    static {
        MAX_CLUSTER.put(Blocks.DIAMOND_ORE,              10);
        MAX_CLUSTER.put(Blocks.DEEPSLATE_DIAMOND_ORE,    10);
        MAX_CLUSTER.put(Blocks.IRON_ORE,                 13);
        MAX_CLUSTER.put(Blocks.DEEPSLATE_IRON_ORE,       13);
        MAX_CLUSTER.put(Blocks.GOLD_ORE,                  9);
        MAX_CLUSTER.put(Blocks.DEEPSLATE_GOLD_ORE,        9);
        MAX_CLUSTER.put(Blocks.COAL_ORE,                 17);
        MAX_CLUSTER.put(Blocks.DEEPSLATE_COAL_ORE,       17);
        MAX_CLUSTER.put(Blocks.COPPER_ORE,               16);
        MAX_CLUSTER.put(Blocks.DEEPSLATE_COPPER_ORE,     16);
        MAX_CLUSTER.put(Blocks.LAPIS_ORE,                 7);
        MAX_CLUSTER.put(Blocks.DEEPSLATE_LAPIS_ORE,       7);
        MAX_CLUSTER.put(Blocks.REDSTONE_ORE,              8);
        MAX_CLUSTER.put(Blocks.DEEPSLATE_REDSTONE_ORE,    8);
        MAX_CLUSTER.put(Blocks.EMERALD_ORE,               1);
        MAX_CLUSTER.put(Blocks.DEEPSLATE_EMERALD_ORE,     1);
        MAX_CLUSTER.put(Blocks.ANCIENT_DEBRIS,            3);
        MAX_CLUSTER.put(Blocks.NETHER_GOLD_ORE,          10);
        MAX_CLUSTER.put(Blocks.NETHER_QUARTZ_ORE,        14);
    }

    // Chunk key → (BlockPos long → isFake boolean) skor cache'i
    private static final ConcurrentHashMap<Long, ConcurrentHashMap<Long, Boolean>> RESULT_CACHE =
            new ConcurrentHashMap<>();

    // BFS küme boyutu cache'i — aynı kümedeki her blok için BFS tekrar yapma
    // BlockPos long → küme boyutu
    private static final ConcurrentHashMap<Long, Integer> CLUSTER_CACHE =
            new ConcurrentHashMap<>();

    // 6 yön sabitleri
    private static final int[][] DIRS6 = {
        { 1, 0, 0}, {-1, 0, 0},
        { 0, 1, 0}, { 0,-1, 0},
        { 0, 0, 1}, { 0, 0,-1}
    };

    // ── Ana Filtre Metodu ─────────────────────────────────────────────────────

    /**
     * Verilen bloğun sahte olup olmadığını 6 katmanlı analiz ile belirler.
     *
     * @param world  Minecraft client dünyası (boyut bilgisi için ClientWorld gerekir)
     * @param pos    Kontrol edilecek blok konumu
     * @param block  Blok türü
     * @return true → sahte (gösterme), false → muhtemelen gerçek (göster)
     */
    public static boolean isFakeBlock(World world, BlockPos pos, Block block) {
        if (world == null || pos == null || block == null) return false;

        try {
            // Cache'e bak — aynı blok için tekrar hesaplama
            long ck = chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
            long bk = BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ());
            ConcurrentHashMap<Long, Boolean> chunkCache =
                    RESULT_CACHE.computeIfAbsent(ck, k -> new ConcurrentHashMap<>());
            Boolean cached = chunkCache.get(bk);
            if (cached != null) return cached;

            boolean result = computeFake(world, pos, block);
            chunkCache.put(bk, result);
            return result;

        } catch (Exception e) {
            return false; // Hata → güvenli taraf: göster
        }
    }

    /**
     * Sahte tespitini hesaplar — hiç cache kullanmaz.
     * isFakeBlock() tarafından çağrılır, direkt kullanmayın.
     */
    private static boolean computeFake(World world, BlockPos pos, Block block) {

        // ── BLOCK_UPDATE ile doğrulanmış → kesinlikle gerçek ────────────────
        if (BlockVerificationCache.isVerified(pos)) return false;

        // ── KATMAN 0: Boyut-Blok Uyumu (O(1)) ───────────────────────────────
        try {
            if (world instanceof ClientWorld cw) {
                if (!DimensionBlockValidator.isValid(block, cw.getRegistryKey())) {
                    return true; // Yanlış boyut → kesinlikle sahte
                }
            }
        } catch (Exception ignored) {}

        // ── KATMAN 1: Y-Seviyesi Kontrolü (O(1)) ────────────────────────────
        if (!OreSpawnRange.isInRange(block, pos.getY())) {
            return true; // Y aralığı dışı → sahte
        }

        // ── KATMAN 2: Deepslate Çakışma Kontrolü (O(1)) ─────────────────────
        if (isDeepslateConflict(block, pos.getY())) {
            return true; // Deepslate ore yanlış Y bölgesinde → sahte
        }

        // ── KATMAN 3: Chunk Yoğunluk Anomalisi (O(1) cache) ─────────────────
        ChunkPos cp = new ChunkPos(pos);
        if (ChunkOreCounter.isChunkAnomaly(cp, block)) {
            // Anomali var — sadece hava komşusu olan blokları koru
            if (!hasAnyAirNeighbor6(world, pos)) {
                return true; // Anomali + hiç açık komşu yok → sahte
            }
        }

        // ── KATMAN 4: 3x3x3 Komşu Analizi (O(26)) ───────────────────────────
        if (!hasOpenNeighbor3x3(world, pos)) {
            return true; // 3x3x3'te hiç açık blok yok → sahte
        }

        // ── KATMAN 5: BFS Küme İzolasyon Analizi (O(n) — cache'li) ──────────
        if (isIsolatedFake(world, pos, block)) {
            return true; // Tamamen izole tek blok → sahte
        }

        return false; // Tüm katmanları geçti → gerçek
    }

    // ── Katman 2: Deepslate Çakışma Kontrolü ─────────────────────────────────

    /**
     * Deepslate cevher varyantlarının yanlış Y bölgesinde olup olmadığını kontrol eder.
     * Deepslate sadece Y ≤ 0'da doğal oluşur.
     * Y > 0'da Deepslate Diamond/Iron/Gold vb. → sahte.
     *
     * @param block Blok türü
     * @param y     Y koordinatı
     * @return true → çakışma var (sahte)
     */
    private static boolean isDeepslateConflict(Block block, int y) {
        // Deepslate varyantı Y > 0'da → sahte
        if (y > DEEPSLATE_MAX_Y) {
            if (block == Blocks.DEEPSLATE_DIAMOND_ORE
             || block == Blocks.DEEPSLATE_IRON_ORE
             || block == Blocks.DEEPSLATE_GOLD_ORE
             || block == Blocks.DEEPSLATE_COAL_ORE
             || block == Blocks.DEEPSLATE_COPPER_ORE
             || block == Blocks.DEEPSLATE_LAPIS_ORE
             || block == Blocks.DEEPSLATE_REDSTONE_ORE
             || block == Blocks.DEEPSLATE_EMERALD_ORE) {
                return true; // Deepslate ore Y>0 → kesinlikle sahte
            }
        }
        return false;
    }

    // ── Katman 4: 3x3x3 Komşu Analizi ───────────────────────────────────────

    /**
     * 6 komşunun herhangi birinin "açık" (hava/su/lav) olup olmadığını hızlıca kontrol eder.
     * Katman 3 anomali tespitinde kullanılır.
     *
     * @param world Dünya
     * @param pos   Blok konumu
     * @return true → en az 1 açık komşu var
     */
    private static boolean hasAnyAirNeighbor6(World world, BlockPos pos) {
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int[] d : DIRS6) {
            m.set(pos.getX() + d[0], pos.getY() + d[1], pos.getZ() + d[2]);
            if (isOpenBlock(world.getBlockState(m).getBlock())) return true;
        }
        return false;
    }

    /**
     * 3x3x3 komşu analizini yapar (26 komşu).
     * En az MIN_OPEN_NEIGHBORS_3X3 açık blok bulunamazsa → sahte.
     *
     * @param world Dünya
     * @param pos   Blok konumu
     * @return true → yeterli açık komşu var (gerçek olabilir)
     */
    private static boolean hasOpenNeighbor3x3(World world, BlockPos pos) {
        BlockPos.Mutable m = new BlockPos.Mutable();
        int openCount = 0;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue; // Kendisi
                    m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    try {
                        Block nb = world.getBlockState(m).getBlock();
                        if (isOpenBlock(nb)) {
                            openCount++;
                            if (openCount >= MIN_OPEN_NEIGHBORS_3X3) return true;
                        }
                    } catch (Exception ignored) {
                        // Chunk sınırı → açık say (güvenli taraf)
                        return true;
                    }
                }
            }
        }
        return openCount >= MIN_OPEN_NEIGHBORS_3X3;
    }

    /**
     * Bloğun "açık" sayılıp sayılmayacağını belirler.
     * Hava, su, lav ve geçirimsiz olmayan bloklar açık sayılır.
     */
    private static boolean isOpenBlock(Block b) {
        return b == Blocks.AIR
            || b == Blocks.CAVE_AIR
            || b == Blocks.VOID_AIR
            || b == Blocks.WATER
            || b == Blocks.LAVA;
    }

    // ── Katman 5: BFS Küme İzolasyon ─────────────────────────────────────────

    /**
     * Bloğun tamamen izole (komşu cevher yok) ve solid çevrilmiş olup olmadığını
     * BFS ile kontrol eder.
     *
     * İzole + tüm 6 komşu solid → %100 sahte (doğal damar bu şekilde oluşmaz).
     * Küme boyutu doğal maksimumun 3 katından büyük → sahte.
     *
     * BFS sonuçları CLUSTER_CACHE'de saklanır; aynı kümedeki her blok için
     * BFS tekrar çalışmaz.
     *
     * @param world Dünya
     * @param pos   Blok konumu
     * @param block Blok türü
     * @return true → izole sahte blok veya anormal büyük küme
     */
    private static boolean isIsolatedFake(World world, BlockPos pos, Block block) {
        long bk = BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ());

        // Cache kontrolü
        Integer cachedSize = CLUSTER_CACHE.get(bk);
        int clusterSize;
        if (cachedSize != null) {
            clusterSize = cachedSize;
        } else {
            clusterSize = computeClusterSize(world, pos, block);
            // Kümedeki tüm blokların cache'ini güncelle
            // (performans: yalnızca bu blok için kaydet, BFS tekrar çalışmaz)
            CLUSTER_CACHE.put(bk, clusterSize);
        }

        // Tamamen izole (tek blok) + tüm 6 komşu solid → sahte
        if (clusterSize == 1) {
            return areAllDirectNeighborsSolid(world, pos);
        }

        // Küme anormal büyük → sahte bloklar birleşmiş
        Integer maxCluster = MAX_CLUSTER.get(block);
        if (maxCluster != null && clusterSize > maxCluster * 3) {
            return true;
        }

        return false;
    }

    /**
     * BFS ile aynı blok türündeki bağlı bloklarınızı sayar.
     * Maksimum BFS_MAX_SCAN blok tarar.
     *
     * @param world Dünya
     * @param start Başlangıç bloğu
     * @param block Hedef blok türü
     * @return Bağlı blok sayısı (1 = tamamen izole)
     */
    private static int computeClusterSize(World world, BlockPos start, Block block) {
        Set<Long> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(BlockPos.asLong(start.getX(), start.getY(), start.getZ()));

        BlockPos.Mutable m = new BlockPos.Mutable();

        while (!queue.isEmpty() && visited.size() < BFS_MAX_SCAN) {
            BlockPos cur = queue.poll();
            for (int[] d : DIRS6) {
                m.set(cur.getX() + d[0], cur.getY() + d[1], cur.getZ() + d[2]);
                long key = BlockPos.asLong(m.getX(), m.getY(), m.getZ());
                if (visited.contains(key)) continue;
                try {
                    Block nb = world.getBlockState(m).getBlock();
                    if (nb == block || isSameOreFamily(block, nb)) {
                        visited.add(key);
                        queue.add(m.toImmutable());
                        // Küme cache'ini bu blok için de güncelle
                        CLUSTER_CACHE.put(key, visited.size()); // tahmini boyut
                    }
                } catch (Exception ignored) {}
            }
        }
        return visited.size();
    }

    /**
     * Bloğun 6 doğrudan komşusunun hepsinin solid olup olmadığını kontrol eder.
     */
    private static boolean areAllDirectNeighborsSolid(World world, BlockPos pos) {
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int[] d : DIRS6) {
            m.set(pos.getX() + d[0], pos.getY() + d[1], pos.getZ() + d[2]);
            try {
                BlockState ns = world.getBlockState(m);
                Block nb = ns.getBlock();
                if (isOpenBlock(nb)) return false;
                if (!ns.isSolidBlock(world, m)) return false;
            } catch (Exception ignored) {
                return false; // Chunk sınırı → solid değil (güvenli taraf)
            }
        }
        return true;
    }

    /**
     * Normal ↔ Deepslate çiftlerini aynı aile olarak tanır (damar bütünlüğü).
     */
    private static boolean isSameOreFamily(Block a, Block b) {
        if (a == b) return true;
        if ((a == Blocks.DIAMOND_ORE        && b == Blocks.DEEPSLATE_DIAMOND_ORE) ||
            (a == Blocks.DEEPSLATE_DIAMOND_ORE && b == Blocks.DIAMOND_ORE)) return true;
        if ((a == Blocks.IRON_ORE           && b == Blocks.DEEPSLATE_IRON_ORE) ||
            (a == Blocks.DEEPSLATE_IRON_ORE && b == Blocks.IRON_ORE)) return true;
        if ((a == Blocks.GOLD_ORE           && b == Blocks.DEEPSLATE_GOLD_ORE) ||
            (a == Blocks.DEEPSLATE_GOLD_ORE && b == Blocks.GOLD_ORE)) return true;
        if ((a == Blocks.COPPER_ORE         && b == Blocks.DEEPSLATE_COPPER_ORE) ||
            (a == Blocks.DEEPSLATE_COPPER_ORE && b == Blocks.COPPER_ORE)) return true;
        if ((a == Blocks.LAPIS_ORE          && b == Blocks.DEEPSLATE_LAPIS_ORE) ||
            (a == Blocks.DEEPSLATE_LAPIS_ORE && b == Blocks.LAPIS_ORE)) return true;
        if ((a == Blocks.REDSTONE_ORE       && b == Blocks.DEEPSLATE_REDSTONE_ORE) ||
            (a == Blocks.DEEPSLATE_REDSTONE_ORE && b == Blocks.REDSTONE_ORE)) return true;
        if ((a == Blocks.COAL_ORE           && b == Blocks.DEEPSLATE_COAL_ORE) ||
            (a == Blocks.DEEPSLATE_COAL_ORE && b == Blocks.COAL_ORE)) return true;
        if ((a == Blocks.EMERALD_ORE        && b == Blocks.DEEPSLATE_EMERALD_ORE) ||
            (a == Blocks.DEEPSLATE_EMERALD_ORE && b == Blocks.EMERALD_ORE)) return true;
        return false;
    }

    // ── Toplu Filtreleme ─────────────────────────────────────────────────────

    /**
     * Pozisyon listesini filtreler — sahte blokları çıkarır.
     *
     * @param world     Dünya
     * @param positions Kontrol edilecek blok konumları
     * @param block     Ortak blok türü
     * @return Gerçek adaylar (sahte olmayanlar)
     */
    public static List<BlockPos> filterFakes(World world, Collection<BlockPos> positions,
                                              Block block) {
        List<BlockPos> real = new ArrayList<>();
        // ConcurrentModificationException önlemi: önce kopyala
        List<BlockPos> copy = new ArrayList<>(positions);
        for (BlockPos pos : copy) {
            if (!isFakeBlock(world, pos, block)) {
                real.add(pos);
            }
        }
        return real;
    }

    /**
     * Block → pozisyonlar haritasını filtreler.
     *
     * @param world      Dünya
     * @param oresByType Block türüne göre pozisyon haritası
     * @return Filtrelenmiş harita (sahte bloklar çıkarılmış)
     */
    public static Map<Block, List<BlockPos>> filterAll(World world,
            Map<Block, List<BlockPos>> oresByType) {
        Map<Block, List<BlockPos>> result = new LinkedHashMap<>();
        for (Map.Entry<Block, List<BlockPos>> entry : oresByType.entrySet()) {
            try {
                List<BlockPos> filtered = filterFakes(world, entry.getValue(), entry.getKey());
                if (!filtered.isEmpty()) {
                    result.put(entry.getKey(), filtered);
                }
            } catch (Exception ignored) {}
        }
        return result;
    }

    // ── Cache Yönetimi ────────────────────────────────────────────────────────

    /**
     * Chunk boşaltıldığında skor ve BFS cache'lerini temizler.
     *
     * @param cx Chunk X
     * @param cz Chunk Z
     */
    public static void invalidateChunk(int cx, int cz) {
        RESULT_CACHE.remove(chunkKey(cx, cz));
        BlockVerificationCache.clearChunk(cx, cz);
        // CLUSTER_CACHE: chunk sınırlı temizlik — packed long'u çözerek chunk eşleştir
        // Minecraft BlockPos.asLong: ((x & 0x3FFFFFFL) << 38) | ((y & 0xFFFL) << 12) | (z & 0x3FFFFFFL)
        CLUSTER_CACHE.keySet().removeIf(key -> {
            // X: bit 38-63 (26 bit, işaretli)
            long xRaw = (key >> 38);
            // Z: bit 0-25 (26 bit, işaretli)
            long zRaw = (key << 38) >> 38;
            int bx = (int) xRaw;
            int bz = (int) zRaw;
            return (bx >> 4) == cx && (bz >> 4) == cz;
        });
    }

    /**
     * Tüm cache'leri temizler (Xray kapatılınca veya dünya değişince).
     */
    public static void clearAll() {
        RESULT_CACHE.clear();
        CLUSTER_CACHE.clear();
        BlockVerificationCache.clearAll();
    }

    public static int getCacheSize() {
        return RESULT_CACHE.values().stream().mapToInt(ConcurrentHashMap::size).sum();
    }

    // ── Yardımcı ─────────────────────────────────────────────────────────────

    private static long chunkKey(int cx, int cz) {
        return ((long) cx & 0xFFFFFFFFL) | (((long) cz & 0xFFFFFFFFL) << 32);
    }

    private AntiXrayFilter() {}
}
