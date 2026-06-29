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

/**
 * AntiXrayFilter V4 — Paket Doğrulama Tabanlı Filtre
 *
 * V3 sorunları (neden geri alındı):
 *  - Chunk yoğunluk sayacı (max=8) gerçek diamondları da siliyordu
 *  - "Komşu yok → SAHTE" kuralı taş içindeki gerçek cevherleri eliyordu
 *  - Sonuç: Diamond=0, Iron=0 (false positive patlaması)
 *
 * V4 stratejisi — karar ağacı (hızlıdan yavaşa):
 *
 *  1. Boyut geçersiz mi?      → SAHTE  (Nether bloğu Overworld'de)
 *  2. Y aralığı geçersiz mi?  → SAHTE  (vanilla spawn tablosu dışı)
 *  3. Server BLOCK_UPDATE ile doğrulandı mı?
 *                              → GERÇEK (en güvenilir sinyal)
 *  4. Açık komşusu var mı?    → GERÇEK (mağara yüzeyine yakın)
 *  5. Hiçbiri                 → SAHTE  (tamamen gömülü + doğrulanmamış)
 *
 * Neden bu çalışır:
 *  - Paper Mode 2 sahteleri TAMAMEN TAŞIN içine gömer (tüm komşular solid)
 *  - Gerçek cevherler MAĞARA YÜZEYİNDE olur (en az 1 hava komşusu)
 *  - Yaklaşınca server BLOCK_UPDATE gönderir → verified → hep göster
 *
 * Chunk yoğunluk sayacı V4'te YOK — false positive üretiyordu.
 * Boyut ve Y filtresi Nether bloklarını %100 eler.
 * Geri kalan gerçek cevherler verified veya açık komşu ile geçer.
 */
public final class AntiXrayFilter {

    // ── Y-aralığı tablosu {minY, maxY} ────────────────────────────────────────
    // Minecraft 1.20.1 vanilla spawn tablosu — kesin sınırlar, tolerans yok
    private static final Map<Block, int[]> Y_RANGES = new HashMap<>();

    static {
        Y_RANGES.put(Blocks.DIAMOND_ORE,              new int[]{-64,  16});
        Y_RANGES.put(Blocks.DEEPSLATE_DIAMOND_ORE,    new int[]{-64,  16});
        Y_RANGES.put(Blocks.IRON_ORE,                 new int[]{-64,  72});
        Y_RANGES.put(Blocks.DEEPSLATE_IRON_ORE,       new int[]{-64,  72});
        Y_RANGES.put(Blocks.GOLD_ORE,                 new int[]{-64,  32});
        Y_RANGES.put(Blocks.DEEPSLATE_GOLD_ORE,       new int[]{-64,  32});
        Y_RANGES.put(Blocks.COPPER_ORE,               new int[]{ -16, 112});
        Y_RANGES.put(Blocks.DEEPSLATE_COPPER_ORE,     new int[]{ -16,  16});
        Y_RANGES.put(Blocks.COAL_ORE,                 new int[]{   0, 192});
        Y_RANGES.put(Blocks.DEEPSLATE_COAL_ORE,       new int[]{-64,   0});
        Y_RANGES.put(Blocks.LAPIS_ORE,                new int[]{-64,  64});
        Y_RANGES.put(Blocks.DEEPSLATE_LAPIS_ORE,      new int[]{-64,   0});
        Y_RANGES.put(Blocks.REDSTONE_ORE,             new int[]{-64,  16});
        Y_RANGES.put(Blocks.DEEPSLATE_REDSTONE_ORE,   new int[]{-64,   0});
        Y_RANGES.put(Blocks.EMERALD_ORE,              new int[]{ -16, 320});
        Y_RANGES.put(Blocks.DEEPSLATE_EMERALD_ORE,    new int[]{ -16,   0});
        Y_RANGES.put(Blocks.ANCIENT_DEBRIS,           new int[]{   8, 119});
        Y_RANGES.put(Blocks.NETHER_GOLD_ORE,          new int[]{  10, 117});
        Y_RANGES.put(Blocks.NETHER_QUARTZ_ORE,        new int[]{  10, 117});
        // Özel bloklar — Y kısıtlaması yok
        Y_RANGES.put(Blocks.AMETHYST_CLUSTER,         null);
        Y_RANGES.put(Blocks.SPAWNER,                  null);
        Y_RANGES.put(Blocks.CHEST,                    null);
        Y_RANGES.put(Blocks.OBSIDIAN,                 null);
    }

    private static final Direction[] DIRS = Direction.values();

    // =========================================================================
    // Ana Filtre — Karar Ağacı
    // =========================================================================

    /**
     * V4 karar ağacı:
     *  SAHTE  → boyut geçersiz VEYA Y geçersiz VEYA (doğrulanmamış VE açık komşu yok)
     *  GERÇEK → boyut+Y geçerli VE (doğrulanmış VEYA açık komşu var)
     *
     * @param world  ClientWorld
     * @param pos    Blok konumu
     * @param block  Blok türü
     * @return true → sahte (gösterme), false → gerçek (göster)
     */
    public static boolean isFake(ClientWorld world, BlockPos pos, Block block) {
        if (world == null || pos == null || block == null) return false;

        try {
            // ── 1. Boyut-Blok Uyumu ───────────────────────────────────────────
            if (!isDimensionValid(block, world)) return true;

            // ── 2. Y-Seviyesi ─────────────────────────────────────────────────
            if (!isYValid(block, pos.getY())) return true;

            // ── 3. Server Doğrulama (BLOCK_UPDATE paketi aldı mı?) ────────────
            // Doğrulanmış → %100 gerçek, hemen göster
            if (BlockVerificationCache.isVerified(pos)) return false;

            // ── 4. Açık Komşu Kontrolü ────────────────────────────────────────
            // Hava / sıvı / non-solid komşu → mağara yüzeyinde → gerçek
            if (hasOpenNeighbor(world, pos)) return false;

            // ── 5. Hiçbiri → SAHTE ───────────────────────────────────────────
            // Tamamen taşa gömülü + server doğrulamadı = Paper sahte bloğu
            return true;

        } catch (Exception e) {
            return false; // Hata → güvenli taraf: göster
        }
    }

    /**
     * World parametreli wrapper — ChunkOreScanner geriye uyumluluk için.
     */
    public static boolean isFakeBlock(World world, BlockPos pos, Block block) {
        if (world instanceof ClientWorld cw) return isFake(cw, pos, block);
        return false;
    }

    // =========================================================================
    // Katman 1: Boyut-Blok Uyumu
    // =========================================================================

    private static boolean isDimensionValid(Block block, ClientWorld world) {
        boolean isOverworld = world.getRegistryKey() == World.OVERWORLD;
        boolean isNether    = world.getRegistryKey() == World.NETHER;

        if (isOverworld) {
            // Nether bloğu Overworld'de → kesinlikle sahte
            if (block == Blocks.ANCIENT_DEBRIS)    return false;
            if (block == Blocks.NETHER_GOLD_ORE)   return false;
            if (block == Blocks.NETHER_QUARTZ_ORE) return false;
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
    // Katman 2: Y-Seviyesi
    // =========================================================================

    private static boolean isYValid(Block block, int y) {
        // null kaydı = Y kısıtlaması yok (Amethyst, Spawner vb.)
        if (!Y_RANGES.containsKey(block)) return true; // Bilinmeyen blok → geçir
        int[] range = Y_RANGES.get(block);
        if (range == null) return true; // Özel blok, her Y geçerli
        return y >= range[0] && y <= range[1];
    }

    // =========================================================================
    // Katman 4: Açık Komşu Kontrolü
    // =========================================================================

    /**
     * Bloğun 6 doğrudan komşusundan en az birinin "açık" olup olmadığını kontrol eder.
     *
     * Açık sayılanlar:
     *  - Hava (air, cave_air, void_air)
     *  - Su / lav
     *  - Solid olmayan herhangi bir blok (bitki, rail, torç, merdiven…)
     *
     * NOT: Bu kontrol "açık komşu var → GERÇEK" mantığıyla çalışır.
     * "Açık komşu yok" tek başına sahte sayılmaz (V3'teki hata).
     * Sadece verified değilse VE açık komşu da yoksa SAHTE sayılır.
     *
     * @param world Dünya
     * @param pos   Kontrol edilecek blok konumu
     * @return true → en az 1 açık komşu var
     */
    private static boolean hasOpenNeighbor(ClientWorld world, BlockPos pos) {
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (Direction dir : DIRS) {
            mutable.set(pos, dir);
            try {
                BlockState state = world.getBlockState(mutable);
                // Hava türleri
                if (state.isAir()) return true;
                // Sıvılar
                Block b = state.getBlock();
                if (b == Blocks.WATER || b == Blocks.LAVA) return true;
                // Solid olmayan her blok (torç, bitki, rail, vb.)
                if (!state.isSolidBlock(world, mutable)) return true;
            } catch (Exception ignored) {
                // Yüklenmemiş komşu chunk → açık say (güvenli taraf)
                return true;
            }
        }
        return false;
    }

    // =========================================================================
    // Toplu Filtreleme (ChunkOreScanner API)
    // =========================================================================

    /**
     * Block → pozisyonlar haritasını filtreler.
     * ChunkOreScanner.scanChunk() tarafından çağrılır.
     *
     * @param world      Dünya
     * @param oresByType Block türüne göre pozisyon haritası
     * @return Filtrelenmiş harita
     */
    public static Map<Block, List<BlockPos>> filterAll(World world,
            Map<Block, List<BlockPos>> oresByType) {
        Map<Block, List<BlockPos>> result = new LinkedHashMap<>();
        for (Map.Entry<Block, List<BlockPos>> entry : oresByType.entrySet()) {
            try {
                Block block = entry.getKey();
                List<BlockPos> copy = new ArrayList<>(entry.getValue()); // CME önlemi
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
    // Chunk Yaşam Döngüsü
    // =========================================================================

    /**
     * Chunk yüklendiğinde verification cache'ini temizler.
     * XRayModule.onChunkData() tarafından çağrılır.
     */
    public static void onChunkLoad(ChunkPos cp) {
        BlockVerificationCache.clearChunk(cp.x, cp.z);
    }

    /**
     * Chunk boşaltıldığında verification cache'ini temizler.
     * ChunkOreScanner.onChunkUnloaded() tarafından çağrılır.
     */
    public static void onChunkUnload(ChunkPos cp) {
        BlockVerificationCache.clearChunk(cp.x, cp.z);
    }

    /**
     * Geriye uyumluluk: XRayModule.onBlockUpdate() tarafından çağrılır.
     */
    public static void invalidateChunk(int cx, int cz) {
        // V4: invalidate artık yalnızca verification cache'ini etkiler
        // BlockVerificationCache chunk'a göre otomatik yönetir
    }

    /**
     * Tüm cache temizleme (Xray kapatılınca / dünya değişince).
     */
    public static void clearAll() {
        BlockVerificationCache.clearAll();
    }

    /**
     * Debug: verified blok sayısı.
     */
    public static int getCacheSize() {
        return BlockVerificationCache.getVerifiedCount();
    }

    // V3 ile uyumluluk — chunk counts artık yok, no-op
    public static void registerChunkCounts(ChunkPos cp, Map<Block, Integer> rawCounts) { /* V4: removed */ }

    private AntiXrayFilter() {}
}
