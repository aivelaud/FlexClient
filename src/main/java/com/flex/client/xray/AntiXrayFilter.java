package com.flex.client.xray;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AntiXrayFilter — Paper Engine Mode 2 Anti-Xray Bypass Filtresi
 *
 * Katmanlı bir skor sistemi kullanarak sahte blokları tespit eder:
 *
 * | Kontrol                         | Sahte Skor |
 * |---------------------------------|------------|
 * | Tüm 6 komşu solid              | +10        |
 * | Y seviyesi uyumsuz              | +5         |
 * | Sky light > 0 ve Y < 60        | +3         |
 * | Tamamen izole blok (BFS boyut=1)| +4         |
 * | Chunk'ta ilk kez görüldü        | +2         |
 * | BLOCK_UPDATE ile doğrulandı     | -15        |
 *
 * Eşik: skor >= 10 → sahte (gösterme), skor < 10 → gerçek (göster)
 *
 * Performans optimizasyonları:
 *  - BlockPos → Integer cache (her blok bir kez hesaplanır)
 *  - Chunk unload'da cache temizlenir
 *  - BlockPos.Mutable kullanımı (GC baskısını azaltır)
 */
public final class AntiXrayFilter {

    // Skor eşiği: bu değer ve üzeri → sahte
    private static final int FAKE_THRESHOLD = 10;

    // Komşu yönleri (6 yön)
    private static final int[][] DIRS = {
        { 1,  0,  0}, {-1,  0,  0},
        { 0,  1,  0}, { 0, -1,  0},
        { 0,  0,  1}, { 0,  0, -1}
    };

    // Chunk key → (BlockPos long → sahte skor) cache
    private static final ConcurrentHashMap<Long, ConcurrentHashMap<Long, Integer>> SCORE_CACHE =
            new ConcurrentHashMap<>();

    // ── Ana Filtre Metodu ─────────────────────────────────────────────────────

    /**
     * Verilen bloğun sahte olup olmadığını katmanlı analiz ile belirler.
     *
     * @param world  Minecraft client dünyası
     * @param pos    Kontrol edilecek blok konumu
     * @param block  Blok türü (Block nesnesi)
     * @return true → sahte (gösterme), false → muhtemelen gerçek (göster)
     */
    public static boolean isFakeBlock(World world, BlockPos pos, Block block) {
        if (world == null || pos == null || block == null) return false;

        try {
            // Cache'e bak
            long ck = chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
            long bk = BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ());
            ConcurrentHashMap<Long, Integer> chunkScores =
                    SCORE_CACHE.computeIfAbsent(ck, k -> new ConcurrentHashMap<>());

            Integer cached = chunkScores.get(bk);
            if (cached != null) return cached >= FAKE_THRESHOLD;

            int score = computeScore(world, pos, block);
            chunkScores.put(bk, score);
            return score >= FAKE_THRESHOLD;

        } catch (Exception e) {
            // Hata durumunda sessizce gerçek kabul et (oyunu çökertme)
            return false;
        }
    }

    /**
     * Bloğun sahte skoru hesaplar (detaylı analiz).
     * Her katman kendi puanını ekler veya çıkarır.
     *
     * @param world  Dünya
     * @param pos    Blok konumu
     * @param block  Blok türü
     * @return Hesaplanan sahte skoru (yüksek = daha sahte)
     */
    public static int computeScore(World world, BlockPos pos, Block block) {
        int score = 0;

        // ── KATMAN 5: Block Update Doğrulama (en yüksek öncelik) ────────────
        // Sunucu BLOCK_UPDATE paketi gönderdi → kesinlikle gerçek
        if (BlockVerificationCache.isVerified(pos)) {
            score -= 15;
            return score; // Negatif skor = kesinlikle gerçek, erken çık
        }

        // ── KATMAN 1: Komşu Blok Analizi (en kritik) ─────────────────────────
        // Tüm 6 komşu solid → sahte işareti
        boolean allSolid = areAllNeighborsSolid(world, pos);
        if (allSolid) {
            score += 10;
        }

        // ── KATMAN 2: Işık Seviyesi Kontrolü ─────────────────────────────────
        // Sky light > 0 ve derin yeraltında → şüpheli
        try {
            int skyLight   = world.getLightLevel(LightType.SKY, pos);
            int blockLight = world.getLightLevel(LightType.BLOCK, pos);
            if (skyLight > 0 && pos.getY() < 60) {
                score += 3;
            }
            // Yeraltında olağandışı block ışığı (lav/torç komşusu yoksa)
            if (blockLight > 4 && pos.getY() < 0 && !hasLightSource(world, pos)) {
                score += 2;
            }
        } catch (Exception ignored) {}

        // ── KATMAN 3: Y Seviyesi + Blok Türü Uyumu ───────────────────────────
        if (!OreSpawnRange.isInRange(block, pos.getY())) {
            score += 5;
        }

        // ── KATMAN 4: Küme Yoğunluğu (BFS boyut 1 = izole blok) ─────────────
        if (allSolid && isIsolatedBlock(world, pos, block)) {
            score += 4;
        }

        // ── KATMAN 5 (devam): İlk kez chunk'ta görüldü ───────────────────────
        if (BlockVerificationCache.isUnverified(pos)) {
            score += 2;
        }

        return score;
    }

    // ── Katman Yardımcı Metodları ─────────────────────────────────────────────

    /**
     * 6 komşu yönün tamamının solid (geçirimsiz) blok olup olmadığını kontrol eder.
     * Gerçek cevherler daima en az bir komşusuna hava/su/lav ile temas eder.
     *
     * @param world Dünya
     * @param pos   Blok konumu
     * @return true → tüm komşular solid (sahte işareti)
     */
    private static boolean areAllNeighborsSolid(World world, BlockPos pos) {
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int[] dir : DIRS) {
            mutable.set(pos.getX() + dir[0], pos.getY() + dir[1], pos.getZ() + dir[2]);
            BlockState neighborState = world.getBlockState(mutable);
            Block nb = neighborState.getBlock();

            // Hava, su, lav, boşluk → solid değil → gerçek komşu var
            if (nb == Blocks.AIR
                    || nb == Blocks.CAVE_AIR
                    || nb == Blocks.VOID_AIR
                    || nb == Blocks.WATER
                    || nb == Blocks.LAVA) {
                return false; // En az bir açık komşu var → gerçek olabilir
            }

            // İsSolidBlock kontrolü (özel şekle sahip bloklar için)
            try {
                if (!neighborState.isSolidBlock(world, mutable)) {
                    return false;
                }
            } catch (Exception ignored) {
                // Chunk sınırı veya null state — güvenli taraf: solid değil say
                return false;
            }
        }
        return true; // Tüm komşular solid
    }

    /**
     * Bloğun aynı türde hiçbir komşusu olmadığını (izole = BFS boyut 1) kontrol eder.
     * Sahte cevherler genellikle chunk geneline dağılmış tekil bloklardır.
     *
     * @param world Dünya
     * @param pos   Blok konumu
     * @param block Blok türü
     * @return true → tamamen izole (komşu cevher yok)
     */
    private static boolean isIsolatedBlock(World world, BlockPos pos, Block block) {
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int[] dir : DIRS) {
            mutable.set(pos.getX() + dir[0], pos.getY() + dir[1], pos.getZ() + dir[2]);
            Block nb = world.getBlockState(mutable).getBlock();
            // Aynı blok veya deepslate versiyonu → damar parçası
            if (nb == block || isSameOreFamily(block, nb)) {
                return false; // Komşu cevher bulundu → damar var → gerçek olabilir
            }
        }
        return true; // Tek başına izole blok
    }

    /**
     * İki bloğun aynı cevher ailesine (normal + deepslate versiyonu) ait olup
     * olmadığını kontrol eder.
     *
     * @param a Birinci blok
     * @param b İkinci blok
     * @return true → aynı aile
     */
    private static boolean isSameOreFamily(Block a, Block b) {
        if (a == b) return true;
        // Normal ↔ Deepslate çiftleri
        if ((a == Blocks.DIAMOND_ORE && b == Blocks.DEEPSLATE_DIAMOND_ORE)
         || (a == Blocks.DEEPSLATE_DIAMOND_ORE && b == Blocks.DIAMOND_ORE)) return true;
        if ((a == Blocks.IRON_ORE && b == Blocks.DEEPSLATE_IRON_ORE)
         || (a == Blocks.DEEPSLATE_IRON_ORE && b == Blocks.IRON_ORE)) return true;
        if ((a == Blocks.GOLD_ORE && b == Blocks.DEEPSLATE_GOLD_ORE)
         || (a == Blocks.DEEPSLATE_GOLD_ORE && b == Blocks.GOLD_ORE)) return true;
        if ((a == Blocks.COPPER_ORE && b == Blocks.DEEPSLATE_COPPER_ORE)
         || (a == Blocks.DEEPSLATE_COPPER_ORE && b == Blocks.COPPER_ORE)) return true;
        if ((a == Blocks.LAPIS_ORE && b == Blocks.DEEPSLATE_LAPIS_ORE)
         || (a == Blocks.DEEPSLATE_LAPIS_ORE && b == Blocks.LAPIS_ORE)) return true;
        if ((a == Blocks.REDSTONE_ORE && b == Blocks.DEEPSLATE_REDSTONE_ORE)
         || (a == Blocks.DEEPSLATE_REDSTONE_ORE && b == Blocks.REDSTONE_ORE)) return true;
        if ((a == Blocks.COAL_ORE && b == Blocks.DEEPSLATE_COAL_ORE)
         || (a == Blocks.DEEPSLATE_COAL_ORE && b == Blocks.COAL_ORE)) return true;
        if ((a == Blocks.EMERALD_ORE && b == Blocks.DEEPSLATE_EMERALD_ORE)
         || (a == Blocks.DEEPSLATE_EMERALD_ORE && b == Blocks.EMERALD_ORE)) return true;
        return false;
    }

    /**
     * Bloğun yakınında bir ışık kaynağı olup olmadığını kontrol eder.
     * Block ışığı yüksekse ama ışık kaynağı yoksa sahte işareti olabilir.
     *
     * @param world Dünya
     * @param pos   Blok konumu
     * @return true → yakında ışık kaynağı var
     */
    private static boolean hasLightSource(World world, BlockPos pos) {
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    mutable.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    Block nb = world.getBlockState(mutable).getBlock();
                    if (nb == Blocks.LAVA || nb == Blocks.TORCH
                            || nb == Blocks.WALL_TORCH || nb == Blocks.GLOWSTONE
                            || nb == Blocks.SEA_LANTERN || nb == Blocks.LANTERN) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ── Toplu Filtreleme (Chunk Seviyesi) ─────────────────────────────────────

    /**
     * Bir pozisyon listesini filtreler, sahte olanları çıkarır.
     * Chunk tarama sırasında kullanmak için optimize edilmiştir.
     *
     * @param world     Dünya
     * @param positions Filtre uygulanacak blok konumları
     * @param block     Ortak blok türü
     * @return Sahte olmayanlar (gerçek adaylar) listesi
     */
    public static List<BlockPos> filterFakes(World world, Collection<BlockPos> positions, Block block) {
        List<BlockPos> real = new ArrayList<>();
        for (BlockPos pos : positions) {
            if (!isFakeBlock(world, pos, block)) {
                real.add(pos);
            }
        }
        return real;
    }

    /**
     * Blok-tür haritasını filtreler.
     * Her blok türü için sahteleri çıkarır.
     *
     * @param world      Dünya
     * @param oresByType Block → pozisyonlar haritası
     * @return Filtrelenmiş harita (sahte bloklar çıkarılmış)
     */
    public static Map<Block, List<BlockPos>> filterAll(World world,
            Map<Block, List<BlockPos>> oresByType) {
        Map<Block, List<BlockPos>> result = new LinkedHashMap<>();
        for (Map.Entry<Block, List<BlockPos>> entry : oresByType.entrySet()) {
            List<BlockPos> filtered = filterFakes(world, entry.getValue(), entry.getKey());
            if (!filtered.isEmpty()) {
                result.put(entry.getKey(), filtered);
            }
        }
        return result;
    }

    // ── Cache Yönetimi ────────────────────────────────────────────────────────

    /**
     * Chunk boşaltıldığında o chunk'a ait skor cache'ini temizler.
     *
     * @param cx Chunk X koordinatı
     * @param cz Chunk Z koordinatı
     */
    public static void invalidateChunk(int cx, int cz) {
        SCORE_CACHE.remove(chunkKey(cx, cz));
        BlockVerificationCache.clearChunk(cx, cz);
    }

    /**
     * Tüm cache'i temizler (Xray kapatılınca veya dünya değişince).
     */
    public static void clearAll() {
        SCORE_CACHE.clear();
        BlockVerificationCache.clearAll();
    }

    /** Toplam cache'deki blok sayısını döner (debug). */
    public static int getCacheSize() {
        return SCORE_CACHE.values().stream().mapToInt(ConcurrentHashMap::size).sum();
    }

    // ── Yardımcı ─────────────────────────────────────────────────────────────

    private static long chunkKey(int cx, int cz) {
        return ((long) cx & 0xFFFFFFFFL) | (((long) cz & 0xFFFFFFFFL) << 32);
    }

    private AntiXrayFilter() {}
}
