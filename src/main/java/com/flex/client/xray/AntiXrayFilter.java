package com.flex.client.xray;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;

/**
 * AntiXrayFilter V5 — Mesafe Tabanlı Filtre
 *
 * ───────────────────────────────────────────────────────────────────
 * NEDEN V1-V4 YETERSİZ KALDI
 * ───────────────────────────────────────────────────────────────────
 *
 * V1-V3: Komşu analizi + chunk yoğunluğu
 *   Sorun: Paper sahtelerini de taşın içine gömer.
 *   Komşu yok = sahte mantığı GERÇEK cevherleri de sildi (Diamond=0).
 *
 * V4: Verified → REAL, open neighbor → REAL, else → FAKE
 *   Sorun: 47m uzaktaki Diamond'un komşusu TAMAMEN TAŞ çünkü Paper
 *   o chunk'ta sahte taş da gömer. Komşu analizi başarısız.
 *   Ekran: 14 745 diamond (hepsi sahte, V4 geçirdi).
 *
 * ───────────────────────────────────────────────────────────────────
 * V5 TEMEL İÇGÖRÜSÜ
 * ───────────────────────────────────────────────────────────────────
 *
 * Paper Engine Mode 2 davranışı:
 *   1. Chunk yüklenince → sahte cevherler gönderir (S2C ChunkData)
 *   2. Oyuncu ~32m mesafeye gelince → GERÇEĞI gönderir (S2C BlockUpdate)
 *
 * Bu yüzden:
 *   - 20m+ uzaktaki reveal edilmemiş cevher → %100 SAHTE
 *   - BlockUpdate alan cevher → %100 GERÇEK
 *   - 20m içindeki cevher → komşu veya visited zone ile kontrol
 *
 * Ekran analizi (mc.craftlime.net):
 *   Diamond:   14 745 adet, 47m uzakta → hepsi 20m filtresiyle silinir
 *   D.Diamond: 13 302 adet, 63m uzakta → hepsi 20m filtresiyle silinir
 *   Iron:      31 639 adet, 4m uzakta  → komşu/path ile filtrele
 *
 * ───────────────────────────────────────────────────────────────────
 * V5 KARAR AĞACI (sırayla, hızlıdan yavaşa)
 * ───────────────────────────────────────────────────────────────────
 *
 *  1. Boyut uyumsuz?           → SAHTE  (Overworld'de Ancient Debris)
 *  2. Y aralığı dışı?          → SAHTE  (Diamond Y=200)
 *  3. BlockUpdate ile verified? → GERÇEK (Server %100 onayladı)
 *  4. Mesafe > 20m?            → SAHTE  (Paper bu mesafede reveal etmez)
 *  5. Mesafe ≤ 8m?             → sadece open neighbor kontrolü
 *     5a. Açık komşu var?      → GERÇEK
 *     5b. Yok                  → SAHTE
 *  6. Mesafe 8-20m:
 *     6a. Açık komşu var?      → GERÇEK
 *     6b. Ziyaret edilen bölge?→ GERÇEK
 *     6c. Hiçbiri              → SAHTE
 *
 * ───────────────────────────────────────────────────────────────────
 */
public final class AntiXrayFilter {

    // Mesafe eşikleri (kare olarak sakla — sqrt pahalı)
    private static final double REVEAL_DIST_SQ = 20.0 * 20.0; // 400 — paper reveal sınırı
    private static final double NEAR_DIST_SQ   =  8.0 *  8.0; // 64  — yakın zon

    private static final Direction[] DIRS = Direction.values();

    // =========================================================================
    // ANA FİLTRE
    // =========================================================================

    /**
     * V5 karar ağacı. true = SAHTE (gösterme), false = GERÇEK (göster).
     *
     * @param world     ClientWorld
     * @param pos       Blok konumu
     * @param block     Blok türü
     * @param playerPos Oyuncu pozisyonu (mesafe hesabı için)
     */
    public static boolean isFake(ClientWorld world, BlockPos pos, Block block, Vec3d playerPos) {
        if (world == null || pos == null || block == null) return false;

        try {
            // ── 1. Boyut ─────────────────────────────────────────────────────
            if (!OreValidator.isDimensionValid(block, world)) return true;

            // ── 2. Y aralığı ─────────────────────────────────────────────────
            if (!OreValidator.isYValid(block, pos.getY())) return true;

            // ── 3. Server doğrulaması (BlockUpdate paketi) ───────────────────
            // En güvenilir sinyal: server bu bloğun gerçek olduğunu söyledi
            if (BlockVerificationCache.isVerified(pos)) return false;

            // ── 4. Mesafe filtresi (V5'in temel silahı) ──────────────────────
            // Paper, 32 blok (yaklaşık 20m) içindeyken reveal yapar.
            // 20m+ uzaktaki reveal edilmemiş blok → kesinlikle sahte.
            double distSq = (playerPos != null)
                ? playerPos.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                : Double.MAX_VALUE;

            if (distSq > REVEAL_DIST_SQ) return true; // 20m+ → SAHTE

            // ── 5. Yakın zon (0-8m): sadece komşu kontrolü ──────────────────
            if (distSq <= NEAR_DIST_SQ) {
                return !hasOpenNeighbor(world, pos);
            }

            // ── 6. Orta zon (8-20m): komşu VEYA ziyaret edilen bölge ─────────
            if (hasOpenNeighbor(world, pos)) return false;
            if (PlayerPathTracker.isInVisitedZone(pos)) return false;
            return true; // Orta zon, gömülü, ziyaret edilmemiş → SAHTE

        } catch (Exception e) {
            return false; // Hata → güvenli taraf: göster
        }
    }

    /**
     * World + Vec3d parametresiz wrapper — playerPos'u MinecraftClient'tan alır.
     * ChunkOreScanner, XrayRealOreRenderer gibi legacy çağrılar için.
     */
    public static boolean isFake(ClientWorld world, BlockPos pos, Block block) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Vec3d pPos = (mc.player != null) ? mc.player.getPos() : null;
        return isFake(world, pos, block, pPos);
    }

    /**
     * World → ClientWorld dönüşüm wrapper. ChunkOreScanner.filterAll() için.
     */
    public static boolean isFakeBlock(World world, BlockPos pos, Block block) {
        if (world instanceof ClientWorld cw) return isFake(cw, pos, block);
        return false;
    }

    // =========================================================================
    // AÇIK KOMŞU KONTROLÜ
    // =========================================================================

    /**
     * Bloğun 6 doğrudan komşusundan en az birinin "açık" olup olmadığını kontrol eder.
     *
     * Açık sayılanlar: hava, su, lav, non-solid blok, ışık veren blok.
     * Yüklenmemiş chunk komşusu → güvenli say (açık kabul et).
     */
    private static boolean hasOpenNeighbor(ClientWorld world, BlockPos pos) {
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (Direction dir : DIRS) {
            m.set(pos, dir);
            try {
                BlockState state = world.getBlockState(m);
                if (state.isAir()) return true;
                Block b = state.getBlock();
                if (b == Blocks.WATER || b == Blocks.LAVA) return true;
                if (!state.isSolidBlock(world, m)) return true;
                if (state.getLuminance() > 0) return true;
            } catch (Exception ignored) {
                return true; // Yüklenmemiş chunk → açık say
            }
        }
        return false;
    }

    // =========================================================================
    // TOPLU FİLTRELEME (ChunkOreScanner API)
    // =========================================================================

    /**
     * Blok → pozisyon haritasını filtreler.
     * Player pozisyonunu MinecraftClient'tan alır (signature değişmez).
     *
     * @param world      Dünya
     * @param oresByType Blok türüne göre pozisyon haritası
     * @return Filtrelenmiş harita
     */
    public static Map<Block, List<BlockPos>> filterAll(World world,
            Map<Block, List<BlockPos>> oresByType) {
        if (!(world instanceof ClientWorld cw)) return oresByType;

        MinecraftClient mc = MinecraftClient.getInstance();
        Vec3d playerPos = (mc.player != null) ? mc.player.getPos() : null;

        Map<Block, List<BlockPos>> result = new LinkedHashMap<>();
        for (Map.Entry<Block, List<BlockPos>> entry : oresByType.entrySet()) {
            try {
                Block block = entry.getKey();
                List<BlockPos> copy = new ArrayList<>(entry.getValue()); // CME güvenliği
                List<BlockPos> real = new ArrayList<>();
                for (BlockPos pos : copy) {
                    if (!isFake(cw, pos, block, playerPos)) real.add(pos);
                }
                if (!real.isEmpty()) result.put(block, real);
            } catch (Exception ignored) {}
        }
        return result;
    }

    // =========================================================================
    // CHUNK YAŞAM DÖNGÜSÜ
    // =========================================================================

    /** Chunk yüklenince verification cache'ini temizle. */
    public static void onChunkLoad(ChunkPos cp) {
        BlockVerificationCache.clearChunk(cp.x, cp.z);
    }

    /** Chunk boşaltılınca verification cache'ini temizle. */
    public static void onChunkUnload(ChunkPos cp) {
        BlockVerificationCache.clearChunk(cp.x, cp.z);
    }

    /** Geriye uyumluluk — V4 çağrıları için no-op. */
    public static void invalidateChunk(int cx, int cz) { /* V5: mesafe filtresi kullanır */ }

    /** Tüm cache + path tracker temizleme. */
    public static void clearAll() {
        BlockVerificationCache.clearAll();
        PlayerPathTracker.clearAll();
    }

    /** V3 uyumluluk stub. */
    public static void registerChunkCounts(ChunkPos cp, Map<Block, Integer> rawCounts) { /* removed in V5 */ }

    /** Debug: verified blok sayısı. */
    public static int getCacheSize() { return BlockVerificationCache.getVerifiedCount(); }

    private AntiXrayFilter() {}
}
