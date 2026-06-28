package com.flex.client.xray;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AntiXrayFilter V3 — Whitelist Yaklaşımlı Agresif Filtre
 *
 * V2 sorunları:
 *  - Chunk yoğunluk eşikleri çok yüksekti (diamond: 24 olmaması gerekiyordu)
 *  - Komşu analizi yetersizdi
 *
 * V3 stratejisi — "sahteyi bul" yerine "gerçeği bul":
 *  Bir blok GERÇEK sayılır eğer:
 *    1. Boyut-blok uyumu var (Overworld'de Nether bloğu yok)
 *    2. Y aralığında (vanilla spawn tablosu)
 *    3. Deepslate varyantı doğru bölgede (Y ≤ 0)
 *    4. Chunk'ta bu türden anomali yok (count ≤ max/chunk)
 *    5. En az 1 açık komşu var (hava / sıvı / non-solid)
 *
 * Chunk yoğunluk eşikleri (V3 — daha sıkı):
 *   Diamond:  8    (V2: 24)
 *   Iron:    40    (V2: 180)
 *   Gold:    20    (V2: 100)
 *   Copper:  60    (V2: 240)
 *   Coal:    80    (V2: 300)
 *
 * Tek bir chunk'ta bu sayının üzerinde aynı tür varsa → TÜM O TÜR BLOKLARI SAHTE.
 */
public final class AntiXrayFilter {

    // ── Chunk başına mutlak maksimum (2x güvenlik payı dahil) ─────────────────
    private static final Map<Block, Integer> MAX_PER_CHUNK = new HashMap<>();

    static {
        MAX_PER_CHUNK.put(Blocks.DIAMOND_ORE,              8);
        MAX_PER_CHUNK.put(Blocks.DEEPSLATE_DIAMOND_ORE,    8);
        MAX_PER_CHUNK.put(Blocks.IRON_ORE,                40);
        MAX_PER_CHUNK.put(Blocks.DEEPSLATE_IRON_ORE,      40);
        MAX_PER_CHUNK.put(Blocks.GOLD_ORE,                20);
        MAX_PER_CHUNK.put(Blocks.DEEPSLATE_GOLD_ORE,      20);
        MAX_PER_CHUNK.put(Blocks.COPPER_ORE,              60);
        MAX_PER_CHUNK.put(Blocks.DEEPSLATE_COPPER_ORE,    20);
        MAX_PER_CHUNK.put(Blocks.COAL_ORE,                80);
        MAX_PER_CHUNK.put(Blocks.DEEPSLATE_COAL_ORE,      40);
        MAX_PER_CHUNK.put(Blocks.LAPIS_ORE,               12);
        MAX_PER_CHUNK.put(Blocks.DEEPSLATE_LAPIS_ORE,     12);
        MAX_PER_CHUNK.put(Blocks.REDSTONE_ORE,            20);
        MAX_PER_CHUNK.put(Blocks.DEEPSLATE_REDSTONE_ORE,  20);
        MAX_PER_CHUNK.put(Blocks.EMERALD_ORE,              8);
        MAX_PER_CHUNK.put(Blocks.DEEPSLATE_EMERALD_ORE,    8);
        MAX_PER_CHUNK.put(Blocks.ANCIENT_DEBRIS,           3);
        MAX_PER_CHUNK.put(Blocks.NETHER_GOLD_ORE,         30);
        MAX_PER_CHUNK.put(Blocks.NETHER_QUARTZ_ORE,       30);
    }

    // ── Chunk contamination: chunkKey → kirlenmiş blok türleri ────────────────
    // Bir chunk'ta bir tür için count > max ise o tür "contaminated" sayılır.
    // Contaminated chunk'ta o türün TÜM bloğu sahte.
    private static final ConcurrentHashMap<Long, Set<Block>> CONTAMINATED =
            new ConcurrentHashMap<>();

    // ── Chunk ore sayaçları: chunkKey → (Block → count) ──────────────────────
    private static final ConcurrentHashMap<Long, ConcurrentHashMap<Block, Integer>> CHUNK_COUNTS =
            new ConcurrentHashMap<>();

    // ── Deepslate varyantları sadece Y ≤ 0'da oluşur ─────────────────────────
    private static final int DEEPSLATE_MAX_Y = 0;

    // ── Tarama yönleri (6 yön) ───────────────────────────────────────────────
    private static final Direction[] DIRS = Direction.values();

    // =========================================================================
    // Ana Filtre Metodu
    // =========================================================================

    /**
     * Verilen bloğun sahte olup olmadığını 5 katmanlı whitelist analizi ile belirler.
     *
     * @param world  Minecraft client dünyası
     * @param pos    Blok konumu
     * @param block  Blok türü
     * @return true → sahte (gösterme), false → gerçek (göster)
     */
    public static boolean isFake(ClientWorld world, BlockPos pos, Block block) {
        if (world == null || pos == null || block == null) return false;

        try {
            // Server S2C paketi ile doğrulanmış → kesinlikle gerçek
            if (BlockVerificationCache.isVerified(pos)) return false;

            // ── Katman 0: Boyut-Blok Uyumu ───────────────────────────────────
            if (!isDimensionValid(block, world)) return true;

            // ── Katman 1: Y Seviyesi ──────────────────────────────────────────
            if (!isYValid(block, pos.getY())) return true;

            // ── Katman 2: Deepslate Çakışması ────────────────────────────────
            if (isDeepslateConflict(block, pos.getY())) return true;

            // ── Katman 3: Chunk Yoğunluk Anomalisi ───────────────────────────
            long chunkKey = new ChunkPos(pos).toLong();
            if (isContaminated(chunkKey, block)) return true;

            // ── Katman 4: Açık Komşu Kontrolü ────────────────────────────────
            if (!hasOpenNeighbor(world, pos)) return true;

            return false; // Tüm katmanları geçti → gerçek

        } catch (Exception e) {
            return false; // Hata → güvenli taraf: göster
        }
    }

    /**
     * World parametreli geriye uyumluluk wrapper (ChunkOreScanner için).
     * ClientWorld olmayan World'ler için güvenli taraf döner.
     */
    public static boolean isFakeBlock(World world, BlockPos pos, Block block) {
        if (world instanceof ClientWorld cw) return isFake(cw, pos, block);
        return false;
    }

    // =========================================================================
    // Katman 0: Boyut-Blok Uyumu
    // =========================================================================

    private static boolean isDimensionValid(Block block, ClientWorld world) {
        boolean isOverworld = world.getRegistryKey() == World.OVERWORLD;
        boolean isNether    = world.getRegistryKey() == World.NETHER;

        if (isOverworld) {
            // Nether bloğu Overworld'de → kesinlikle sahte
            if (block == Blocks.ANCIENT_DEBRIS)     return false;
            if (block == Blocks.NETHER_GOLD_ORE)    return false;
            if (block == Blocks.NETHER_QUARTZ_ORE)  return false;
        }

        if (isNether) {
            // Overworld cevheri Nether'de → kesinlikle sahte
            if (block == Blocks.DIAMOND_ORE    || block == Blocks.DEEPSLATE_DIAMOND_ORE)  return false;
            if (block == Blocks.IRON_ORE       || block == Blocks.DEEPSLATE_IRON_ORE)     return false;
            if (block == Blocks.GOLD_ORE       || block == Blocks.DEEPSLATE_GOLD_ORE)     return false;
            if (block == Blocks.COAL_ORE       || block == Blocks.DEEPSLATE_COAL_ORE)     return false;
            if (block == Blocks.LAPIS_ORE      || block == Blocks.DEEPSLATE_LAPIS_ORE)    return false;
            if (block == Blocks.COPPER_ORE     || block == Blocks.DEEPSLATE_COPPER_ORE)   return false;
            if (block == Blocks.REDSTONE_ORE   || block == Blocks.DEEPSLATE_REDSTONE_ORE) return false;
            if (block == Blocks.EMERALD_ORE    || block == Blocks.DEEPSLATE_EMERALD_ORE)  return false;
        }

        return true;
    }

    // =========================================================================
    // Katman 1: Y Seviyesi Kontrolü
    // =========================================================================

    private static boolean isYValid(Block block, int y) {
        // Coal
        if (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE)
            return y >= 0 && y <= 192;
        // Iron
        if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE)
            return y >= -64 && y <= 72;
        // Copper
        if (block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE)
            return y >= -16 && y <= 112;
        // Gold
        if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE)
            return y >= -64 && y <= 32;
        // Diamond
        if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE)
            return y >= -64 && y <= 16;
        // Lapis
        if (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE)
            return y >= -64 && y <= 64;
        // Redstone
        if (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE)
            return y >= -64 && y <= 16;
        // Emerald
        if (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE)
            return y >= -16 && y <= 320;
        // Ancient Debris
        if (block == Blocks.ANCIENT_DEBRIS)
            return y >= 8 && y <= 119;
        // Nether Gold / Quartz
        if (block == Blocks.NETHER_GOLD_ORE || block == Blocks.NETHER_QUARTZ_ORE)
            return y >= 10 && y <= 117;
        // Bilinmeyen blok → geçir (Lapis, Amethyst, Spawner vb. bozulmasın)
        return true;
    }

    // =========================================================================
    // Katman 2: Deepslate Çakışması
    // =========================================================================

    private static boolean isDeepslateConflict(Block block, int y) {
        if (y > DEEPSLATE_MAX_Y) {
            if (block == Blocks.DEEPSLATE_DIAMOND_ORE
             || block == Blocks.DEEPSLATE_IRON_ORE
             || block == Blocks.DEEPSLATE_GOLD_ORE
             || block == Blocks.DEEPSLATE_COAL_ORE
             || block == Blocks.DEEPSLATE_COPPER_ORE
             || block == Blocks.DEEPSLATE_LAPIS_ORE
             || block == Blocks.DEEPSLATE_REDSTONE_ORE
             || block == Blocks.DEEPSLATE_EMERALD_ORE) {
                return true; // Deepslate ore Y > 0 → sahte
            }
        }
        return false;
    }

    // =========================================================================
    // Katman 3: Chunk Yoğunluk Anomalisi
    // =========================================================================

    /**
     * Chunk ham tarama sayılarını kayıt eder ve contamination'ı hesaplar.
     * ChunkOreScanner tarafından raw scan sonrası çağrılır.
     *
     * @param cp         Chunk pozisyonu
     * @param rawCounts  Block → ham blok sayısı
     */
    public static void registerChunkCounts(ChunkPos cp, Map<Block, Integer> rawCounts) {
        long ck = cp.toLong();

        // Önceki veriyi temizle (re-scan durumunda çift sayım önle)
        CHUNK_COUNTS.remove(ck);
        CONTAMINATED.remove(ck);

        if (rawCounts.isEmpty()) return;

        ConcurrentHashMap<Block, Integer> counts = new ConcurrentHashMap<>(rawCounts);
        CHUNK_COUNTS.put(ck, counts);

        // Contamination kontrolü
        Set<Block> contaminated = ConcurrentHashMap.newKeySet();
        rawCounts.forEach((b, c) -> {
            Integer max = MAX_PER_CHUNK.get(b);
            if (max != null && c > max) {
                contaminated.add(b);
            }
        });
        if (!contaminated.isEmpty()) {
            CONTAMINATED.put(ck, contaminated);
        }
    }

    private static boolean isContaminated(long chunkKey, Block block) {
        Set<Block> set = CONTAMINATED.get(chunkKey);
        return set != null && set.contains(block);
    }

    // =========================================================================
    // Katman 4: Açık Komşu Kontrolü
    // =========================================================================

    /**
     * Bloğun 6 doğrudan komşusundan en az birinin "açık" olup olmadığını kontrol eder.
     *
     * Açık sayılan:
     *  - Hava (air, cave_air, void_air)
     *  - Su / lav
     *  - Solid olmayan bloklar (bitkiler, rail, vs.)
     *  - Işık yayan bloklar (torç vb. → mağara işareti)
     *
     * @param world Dünya
     * @param pos   Kontrol edilecek blok konumu
     * @return true → en az 1 açık komşu var (gerçek olabilir)
     */
    private static boolean hasOpenNeighbor(ClientWorld world, BlockPos pos) {
        for (Direction dir : DIRS) {
            BlockPos neighbor = pos.offset(dir);
            try {
                BlockState state = world.getBlockState(neighbor);
                Block b = state.getBlock();

                // Hava türleri
                if (b == Blocks.AIR || b == Blocks.CAVE_AIR || b == Blocks.VOID_AIR) return true;
                // Sıvılar
                if (b == Blocks.WATER || b == Blocks.LAVA) return true;
                // Solid olmayan (bitki, deco, vs.)
                if (!state.isSolidBlock(world, neighbor)) return true;
                // Işık kaynakları (torç, lamba → mağara var)
                if (state.getLuminance() > 0) return true;

            } catch (Exception ignored) {
                // Chunk sınırı veya yüklenmemiş komşu → açık say (güvenli taraf)
                return true;
            }
        }
        return false; // Tüm 6 komşu solid → sahte
    }

    // =========================================================================
    // Toplu Filtreleme (ChunkOreScanner API'si)
    // =========================================================================

    /**
     * Block → pozisyonlar haritasını filtreler.
     * ChunkOreScanner tarafından çağrılır.
     * NOT: Counts ÖNCEDEN registerChunkCounts() ile kaydedilmiş olmalı.
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
                Block block = entry.getKey();
                // ConcurrentModificationException önlemi
                List<BlockPos> copy = new ArrayList<>(entry.getValue());
                List<BlockPos> real = new ArrayList<>();
                for (BlockPos pos : copy) {
                    if (!isFakeBlock(world, pos, block)) {
                        real.add(pos);
                    }
                }
                if (!real.isEmpty()) result.put(block, real);
            } catch (Exception ignored) {}
        }
        return result;
    }

    // =========================================================================
    // Chunk Yaşam Döngüsü (Mixin ve ChunkOreScanner tarafından çağrılır)
    // =========================================================================

    /**
     * Chunk yüklendiğinde veya yeniden tarandığında çağrılır.
     * Eski contamination ve count verilerini temizler.
     *
     * @param cp Yüklenen chunk
     */
    public static void onChunkLoad(ChunkPos cp) {
        long ck = cp.toLong();
        CHUNK_COUNTS.remove(ck);
        CONTAMINATED.remove(ck);
        BlockVerificationCache.clearChunk(cp.x, cp.z);
    }

    /**
     * Chunk boşaltıldığında çağrılır.
     *
     * @param cp Boşaltılan chunk
     */
    public static void onChunkUnload(ChunkPos cp) {
        long ck = cp.toLong();
        CHUNK_COUNTS.remove(ck);
        CONTAMINATED.remove(ck);
        BlockVerificationCache.clearChunk(cp.x, cp.z);
    }

    /**
     * Geriye uyumluluk: invalidateChunk → onChunkUnload.
     * XRayModule.onBlockUpdate ve ChunkOreScanner.onChunkUnloaded tarafından çağrılır.
     */
    public static void invalidateChunk(int cx, int cz) {
        onChunkUnload(new ChunkPos(cx, cz));
    }

    /**
     * Tüm cache'leri temizler (Xray kapatılınca / dünya değişince).
     */
    public static void clearAll() {
        CHUNK_COUNTS.clear();
        CONTAMINATED.clear();
        BlockVerificationCache.clearAll();
    }

    // =========================================================================
    // Debug / İstatistik
    // =========================================================================

    /** Contamination tespiti yapılan chunk sayısı. */
    public static int getCacheSize() {
        return CONTAMINATED.size();
    }

    /** Verilen chunk'ta contaminated blok türlerini döner (debug). */
    public static Set<Block> getContaminatedTypes(ChunkPos cp) {
        Set<Block> set = CONTAMINATED.get(cp.toLong());
        return set == null ? Collections.emptySet() : Collections.unmodifiableSet(set);
    }

    /** Verilen chunk'ta belirli blok türünün ham sayısını döner (debug). */
    public static int getRawCount(ChunkPos cp, Block block) {
        ConcurrentHashMap<Block, Integer> counts = CHUNK_COUNTS.get(cp.toLong());
        return counts == null ? 0 : counts.getOrDefault(block, 0);
    }

    private AntiXrayFilter() {}
}
