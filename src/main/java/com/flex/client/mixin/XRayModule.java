package com.flex.client.mixin;

import com.flex.client.module.ModuleManager;
import com.flex.client.xray.AntiXrayFilter;
import com.flex.client.xray.BlockVerificationCache;
import com.flex.client.xray.XrayConfig;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * XRayModule — Paper Engine Mode 2 Anti-XRay Bypass Mixin
 *
 * Bu mixin üç önemli paketi dinler:
 *  1. onChunkData   — Chunk yüklendiğinde cevherleri "unverified" olarak işaretler
 *                     ve BFS hava bağlantı analizini başlatır.
 *  2. onBlockUpdate — Tekil blok güncelleme paketi geldiğinde bloğu "verified" yapar.
 *                     Paper anti-xray, oyuncu gerçek cevhere yaklaşınca bunu gönderir.
 *  3. onChunkDelta  — Çoklu blok güncelleme paketi (örn. piston, TNT sonrası).
 *                     Her güncellenen bloğu "verified" olarak işaretler.
 *
 * Algoritma (BFS hava bağlantısı):
 *  1. Chunk içindeki tüm hava bloklarından BFS başlat.
 *  2. Hava bağlantısı olan blokları "gerçek zon" say.
 *  3. Cevher bloklarından hiçbir 6 komşusu hava bağlantılı değilse → sahte aday.
 *  4. Chunk kenar bloklarını atla (komşu chunk bilinmiyor).
 */
@Mixin(ClientPlayNetworkHandler.class)
public class XRayModule {

    private static final int[][] DIRS = {
        { 1, 0, 0}, {-1, 0, 0},
        { 0, 1, 0}, { 0,-1, 0},
        { 0, 0, 1}, { 0, 0,-1}
    };

    /** BFS analizi sonucu tespit edilen sahte aday konumlar (chunk key → set) */
    private static final Map<Long, Set<BlockPos>> SUSPECTED_FAKES = new ConcurrentHashMap<>();

    // ── Chunk Yükleme ─────────────────────────────────────────────────────────

    /**
     * Chunk verisi geldiğinde:
     *  - Cevher konumlarını BlockVerificationCache'e "unverified" olarak ekler
     *  - BFS hava bağlantı analizini yapar, sahte adayları tespit eder
     */
    @Inject(method = "onChunkData", at = @At("TAIL"))
    private void onChunkData(ChunkDataS2CPacket packet, CallbackInfo ci) {
        if (!ModuleManager.isEnabled("Xray")) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        int cx = packet.getX();
        int cz = packet.getZ();
        WorldChunk chunk = mc.world.getChunk(cx, cz);
        if (chunk == null) return;

        long key = chunkKey(cx, cz);
        int baseX = cx << 4;
        int baseZ = cz << 4;
        int minY  = mc.world.getBottomY();
        int maxY  = mc.world.getTopY();

        // V3: Chunk yüklenince AntiXrayFilter contamination cache'ini temizle
        AntiXrayFilter.onChunkLoad(new net.minecraft.util.math.ChunkPos(cx, cz));

        // Chunk içindeki tüm hedef cevherleri topla ve unverified olarak işaretle
        List<BlockPos> chunkOres = new ArrayList<>();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int y = minY; y < maxY; y++) {
                    BlockPos pos = new BlockPos(baseX + lx, y, baseZ + lz);
                    Block b = chunk.getBlockState(pos).getBlock();
                    if (XrayConfig.isTargetBlock(b)) {
                        chunkOres.add(pos.toImmutable());
                    }
                }
            }
        }
        // Chunk ilk kez yüklenince tüm cevherler "unverified"
        BlockVerificationCache.markChunkLoaded(cx, cz, chunkOres);

        // BFS hava bağlantı analizi (sahte aday tespiti)
        analyzeChunk(chunk, cx, cz, key, minY, maxY);
    }

    // ── Tekil Blok Güncelleme ─────────────────────────────────────────────────

    /**
     * Sunucu S2C_BLOCK_UPDATE gönderdiğinde bloğu "verified" olarak işaretler.
     * Paper anti-xray, oyuncu gerçek bir cevherin yakınına gelince bu paketi gönderir.
     * Verified blok → sahte değil, kesinlikle göster.
     */
    @Inject(method = "onBlockUpdate", at = @At("TAIL"))
    private void onBlockUpdate(BlockUpdateS2CPacket packet, CallbackInfo ci) {
        if (!ModuleManager.isEnabled("Xray")) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        BlockPos pos = packet.getPos();
        Block b = packet.getState().getBlock();

        // Sadece hedef cevherleri takip et
        if (XrayConfig.isTargetBlock(b)) {
            BlockVerificationCache.markVerified(pos);
            // AntiXrayFilter cache'ini bu blok için geçersiz kıl (yeniden hesaplansın)
            AntiXrayFilter.invalidateChunk(pos.getX() >> 4, pos.getZ() >> 4);
        }
    }

    // ── Çoklu Blok Güncelleme ─────────────────────────────────────────────────

    /**
     * Birden fazla bloğun aynı anda güncellendiği paketi dinler (örn. piston).
     * Her güncellenen cevher bloğu "verified" olarak işaretlenir.
     */
    @Inject(method = "onChunkDeltaUpdate", at = @At("TAIL"))
    private void onChunkDeltaUpdate(ChunkDeltaUpdateS2CPacket packet, CallbackInfo ci) {
        if (!ModuleManager.isEnabled("Xray")) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        try {
            // Paketteki her blok konumunu dolaş
            packet.visitUpdates((pos, state) -> {
                if (XrayConfig.isTargetBlock(state.getBlock())) {
                    BlockVerificationCache.markVerified(pos);
                }
            });
        } catch (Exception ignored) {
            // Null veya parse hatası → sessizce geç
        }
    }

    // ── BFS Hava Bağlantı Analizi ────────────────────────────────────────────

    /**
     * Chunk içinde BFS ile hava bağlantısı olmayan cevherleri tespit eder.
     * Bunlar XRayModule tarafından "sahte aday" olarak işaretlenir.
     * AntiXrayFilter bu bilgiyi SUSPECTED_FAKES üzerinden okuyabilir.
     *
     * @param chunk Analiz edilecek chunk
     * @param cx    Chunk X
     * @param cz    Chunk Z
     * @param key   Chunk long key
     * @param minY  Dünya alt sınırı
     * @param maxY  Dünya üst sınırı
     */
    private static void analyzeChunk(WorldChunk chunk, int cx, int cz,
                                     long key, int minY, int maxY) {
        int baseX = cx << 4;
        int baseZ = cz << 4;
        int height = maxY - minY;

        // Hava bağlantısı haritası: [lx][yi][lz]
        boolean[][][] airConn = new boolean[16][height][16];
        Queue<int[]> queue = new ArrayDeque<>();

        // Tüm hava bloklarını BFS başlangıç noktası yap
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int y = minY; y < maxY; y++) {
                    BlockState bs = chunk.getBlockState(
                            new BlockPos(baseX + lx, y, baseZ + lz));
                    if (bs.isAir()) {
                        int yi = y - minY;
                        if (!airConn[lx][yi][lz]) {
                            airConn[lx][yi][lz] = true;
                            queue.add(new int[]{lx, y, lz});
                        }
                    }
                }
            }
        }

        // BFS: Hava komşularına yayıl
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            int lx = cur[0], y = cur[1], lz = cur[2];
            for (int[] d : DIRS) {
                int nx = lx + d[0], ny = y + d[1], nz = lz + d[2];
                if (nx < 0 || nx >= 16 || nz < 0 || nz >= 16
                        || ny < minY || ny >= maxY) continue;
                int nyi = ny - minY;
                if (airConn[nx][nyi][nz]) continue;
                BlockState ns = chunk.getBlockState(
                        new BlockPos(baseX + nx, ny, baseZ + nz));
                if (ns.isAir()) {
                    airConn[nx][nyi][nz] = true;
                    queue.add(new int[]{nx, ny, nz});
                }
            }
        }

        // Hava bağlantısı olmayan cevherleri sahte aday listesine ekle
        // (Chunk kenar bloklarını atla — komşu chunk bilinmiyor)
        Set<BlockPos> suspected = new HashSet<>();
        int scanMaxY = Math.min(maxY - 1, minY + height - 1);

        for (int lx = 1; lx < 15; lx++) {
            for (int lz = 1; lz < 15; lz++) {
                for (int y = minY + 1; y < scanMaxY; y++) {
                    int yi = y - minY;
                    BlockPos pos = new BlockPos(baseX + lx, y, baseZ + lz);
                    BlockState bs = chunk.getBlockState(pos);

                    if (!XrayConfig.isTargetBlock(bs.getBlock())) continue;

                    // 6 komşunun herhangi biri hava bağlantılı mı?
                    boolean hasAirConn = false;
                    for (int[] d : DIRS) {
                        int nx = lx + d[0], ny = y + d[1], nz = lz + d[2];
                        if (nx < 0 || nx >= 16 || nz < 0 || nz >= 16
                                || ny < minY || ny >= maxY) {
                            // Chunk kenarı → bilinmiyor, güvenli say
                            hasAirConn = true;
                            break;
                        }
                        if (airConn[nx][ny - minY][nz]) {
                            hasAirConn = true;
                            break;
                        }
                    }

                    if (!hasAirConn) {
                        suspected.add(pos.toImmutable());
                    }
                }
            }
        }

        SUSPECTED_FAKES.put(key, suspected);
    }

    // ── Dahili Yardımcı Metodlar (private — Mixin kuralı) ────────────────────

    /**
     * Verilen pozisyonun BFS analizine göre sahte aday olup olmadığını döner.
     *
     * @param pos Sorgulanacak blok konumu
     * @return true → sahte aday (BFS hava bağlantısı yok)
     */
    private static boolean isBfsSuspectedFake(BlockPos pos) {
        long key = chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
        Set<BlockPos> set = SUSPECTED_FAKES.get(key);
        return set != null && set.contains(pos);
    }

    /**
     * Chunk boşaltıldığında BFS cache'i temizler (dahili kullanım).
     *
     * @param cx Chunk X koordinatı
     * @param cz Chunk Z koordinatı
     */
    private static void clearChunkInternal(int cx, int cz) {
        SUSPECTED_FAKES.remove(chunkKey(cx, cz));
    }

    /** Tüm BFS cache'ini temizler (dahili kullanım). */
    private static void clearAllInternal() {
        SUSPECTED_FAKES.clear();
    }

    /** Sahte aday sayısını döner (debug amaçlı). */
    private static int getSuspectedCount(int cx, int cz) {
        Set<BlockPos> s = SUSPECTED_FAKES.get(chunkKey(cx, cz));
        return s == null ? 0 : s.size();
    }

    // ── Yardımcı ─────────────────────────────────────────────────────────────

    private static long chunkKey(int cx, int cz) {
        return ((long) cx & 0xFFFFFFFFL) | (((long) cz & 0xFFFFFFFFL) << 32);
    }
}
