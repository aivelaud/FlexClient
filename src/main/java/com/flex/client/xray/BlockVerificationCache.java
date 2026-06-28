package com.flex.client.xray;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * BlockVerificationCache — Sunucudan gelen blok güncelleme paketlerini izler.
 *
 * Paper Engine Mode 2'de sunucu:
 *  - Chunk ilk yüklendiğinde sahte cevherleri gönderir.
 *  - Oyuncu gerçek bir cevhere yaklaştığında S2C_BLOCK_UPDATE ile gerçeği gönderir.
 *
 * Bu sınıf iki durumu ayırt eder:
 *  - "unverified"  → Yalnızca chunk yüklemesinde gelen, doğrulanmamış blok
 *  - "verified"    → Ayrıca bir BLOCK_UPDATE paketi alan, sunucu-onaylı blok
 *
 * Thread-safe: ConcurrentHashMap + ConcurrentHashMap.newKeySet() kullanır.
 *
 * Kullanım:
 *  1. Chunk gelince → markChunkLoaded(cx, cz, positions)
 *  2. BlockUpdate gelince → markVerified(pos)
 *  3. Filtre sorgularken → isVerified(pos), isUnverified(pos)
 *  4. Chunk boşalınca → clearChunk(cx, cz)
 */
public final class BlockVerificationCache {

    /** Chunk key → O chunk'ta henüz doğrulanmamış bloklar */
    private static final ConcurrentMap<Long, Set<Long>> UNVERIFIED =
            new ConcurrentHashMap<>();

    /** Sunucu tarafından BLOCK_UPDATE ile doğrulanmış bloklar (tüm dünya) */
    private static final Set<Long> VERIFIED = ConcurrentHashMap.newKeySet();

    // ── İşaretleme ────────────────────────────────────────────────────────────

    /**
     * Chunk verisi geldiğinde, içindeki hedef blok konumlarını
     * "unverified" (doğrulanmamış) olarak işaretler.
     *
     * @param cx        Chunk X koordinatı
     * @param cz        Chunk Z koordinatı
     * @param positions Chunk içindeki hedef blok konumları
     */
    public static void markChunkLoaded(int cx, int cz, Iterable<BlockPos> positions) {
        long chunkKey = chunkKey(cx, cz);
        Set<Long> set = UNVERIFIED.computeIfAbsent(chunkKey, k -> ConcurrentHashMap.newKeySet());
        for (BlockPos pos : positions) {
            set.add(blockKey(pos));
        }
    }

    /**
     * Sunucu bir BLOCK_UPDATE paketi gönderdiğinde bu metodu çağır.
     * Blok "verified" (doğrulanmış) listesine alınır.
     *
     * @param pos Güncellenen blok konumu
     */
    public static void markVerified(BlockPos pos) {
        long key = blockKey(pos);
        VERIFIED.add(key);

        // Unverified listesinden çıkar (artık doğrulandı)
        long ck = chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
        Set<Long> set = UNVERIFIED.get(ck);
        if (set != null) set.remove(key);
    }

    // ── Sorgulama ─────────────────────────────────────────────────────────────

    /**
     * Bloğun sunucu tarafından doğrulanmış (BLOCK_UPDATE alan) olup olmadığını döner.
     * Doğrulanmış blok → %100 gerçek, göster.
     *
     * @param pos Sorgulanacak blok konumu
     * @return true → doğrulanmış (gerçek)
     */
    public static boolean isVerified(BlockPos pos) {
        return VERIFIED.contains(blockKey(pos));
    }

    /**
     * Bloğun yalnızca chunk yüklemesinden gelip gelmediğini döner.
     * Hiç görülmemişse de false döner.
     *
     * @param pos Sorgulanacak blok konumu
     * @return true → yalnızca chunk yüklemesinde geldi, şüpheli
     */
    public static boolean isUnverified(BlockPos pos) {
        if (VERIFIED.contains(blockKey(pos))) return false;
        long ck = chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
        Set<Long> set = UNVERIFIED.get(ck);
        return set != null && set.contains(blockKey(pos));
    }

    /**
     * Bloğun cache'te hiç kayıtlı olup olmadığını döner.
     *
     * @param pos Blok konumu
     * @return true → cache'te var (verified veya unverified)
     */
    public static boolean isKnown(BlockPos pos) {
        long key = blockKey(pos);
        if (VERIFIED.contains(key)) return true;
        long ck = chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
        Set<Long> set = UNVERIFIED.get(ck);
        return set != null && set.contains(key);
    }

    // ── Cache Temizleme ───────────────────────────────────────────────────────

    /**
     * Chunk boşaltıldığında ilgili kayıtları temizler (bellek sızıntısı önlemi).
     *
     * @param cx Chunk X koordinatı
     * @param cz Chunk Z koordinatı
     */
    public static void clearChunk(int cx, int cz) {
        long ck = chunkKey(cx, cz);
        Set<Long> set = UNVERIFIED.remove(ck);
        if (set != null) {
            // Verified listesinden de bu chunk'a ait kayıtları temizle
            int baseX = cx << 4;
            int baseZ = cz << 4;
            VERIFIED.removeIf(key -> {
                int bx = (int)(key >> 38) & 0x3FFFFFF;
                int bz = (int)(key >> 12) & 0x3FFFFFF;
                return (bx >> 4) == (baseX >> 4) && (bz >> 4) == (baseZ >> 4);
            });
        }
    }

    /**
     * Tüm cache'i temizler (Xray devre dışı bırakılınca veya dünya değişince).
     */
    public static void clearAll() {
        UNVERIFIED.clear();
        VERIFIED.clear();
    }

    /** Doğrulanmış blok sayısını döner (debug). */
    public static int getVerifiedCount() { return VERIFIED.size(); }

    /** Toplam doğrulanmamış blok sayısını döner (debug). */
    public static int getUnverifiedCount() {
        return UNVERIFIED.values().stream().mapToInt(Set::size).sum();
    }

    // ── Yardımcı Key Üreticiler ───────────────────────────────────────────────

    /**
     * BlockPos'u tek bir long'a sıkıştırır (Minecraft'ın kendi yöntemiyle uyumlu).
     */
    private static long blockKey(BlockPos pos) {
        return BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Chunk koordinatlarını tek bir long'a sıkıştırır.
     */
    private static long chunkKey(int cx, int cz) {
        return ((long) cx & 0xFFFFFFFFL) | (((long) cz & 0xFFFFFFFFL) << 32);
    }

    private BlockVerificationCache() {}
}
