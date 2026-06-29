package com.flex.client.xray;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PlayerPathTracker — Oyuncunun ziyaret ettiği bölgeleri izler.
 *
 * Paper Engine Mode 2 mantığı:
 *  Sunucu, oyuncu gerçek bir cevhere ~32 blok yaklaşınca BlockUpdate gönderir.
 *  Yani oyuncunun GEÇTIĞI bölgelerdeki bloklar reveal edilmiş olabilir.
 *
 * Bölge granülaritesi: 16×16×16 blok (chunk boyutu).
 * Bu seviyede izleme, milisaniyelik hassasiyet olmadan yeterli doğruluk sağlar.
 *
 * AntiXrayFilter, "8-20m arası + doğrulanmamış + açık komşu yok" durumunda
 * bu cache'e bakarak bloğun oyuncunun geçtiği bir bölgede olup olmadığını
 * kontrol eder. Öyleyse → gerçek sayar (sunucu reveal etmiş olabilir).
 *
 * Bellek yönetimi: 20 000 bölge üstünde otomatik temizle (~5MB max).
 */
public final class PlayerPathTracker {

    private static final Set<Long> visitedZonePacked = ConcurrentHashMap.newKeySet();
    private static final int MEMORY_CAP = 20_000;

    /**
     * Her tick çağrılmalı. Oyuncunun bulunduğu 16×16×16 bölgeyi kaydeder.
     *
     * @param player Yerel oyuncu
     */
    public static void tick(PlayerEntity player) {
        if (player == null) return;
        BlockPos p = player.getBlockPos();
        // 16'ya yuvarla (bit shift ile hızlı)
        int gx = (p.getX() >> 4) << 4;
        int gy = (p.getY() >> 4) << 4;
        int gz = (p.getZ() >> 4) << 4;
        visitedZonePacked.add(BlockPos.asLong(gx, gy, gz));
        // Bellek güvenliği
        if (visitedZonePacked.size() > MEMORY_CAP) visitedZonePacked.clear();
    }

    /**
     * Verilen bloğun oyuncunun geçtiği bir bölgede olup olmadığını kontrol eder.
     *
     * @param pos Kontrol edilecek blok konumu
     * @return true → oyuncu bu bölgeden geçti (sunucu reveal etmiş olabilir)
     */
    public static boolean isInVisitedZone(BlockPos pos) {
        int gx = (pos.getX() >> 4) << 4;
        int gy = (pos.getY() >> 4) << 4;
        int gz = (pos.getZ() >> 4) << 4;
        return visitedZonePacked.contains(BlockPos.asLong(gx, gy, gz));
    }

    /** Tüm ziyaret geçmişini siler (ölüm, boyut değişimi, Xray kapatma). */
    public static void clearAll() {
        visitedZonePacked.clear();
    }

    /** Kaydedilen bölge sayısı (debug). */
    public static int getZoneCount() {
        return visitedZonePacked.size();
    }

    private PlayerPathTracker() {}
}
