package com.flex.client.mixin;

import com.flex.client.module.ModuleManager;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * XRayModule — Paper engine-mode: 2 Anti-XRay bypass.
 *
 * Paper mode 2, hava erişimi olmayan cevherlerin yerine sahte taş/deepslate
 * blokları koyarak X-Ray hackleri engeller. Bu mixin, chunk yüklendiğinde
 * BFS flood-fill ile hava bağlantısı olmayan taş bloklarını tespit eder ve
 * "sahte" olarak işaretler. XRay render kodu bu listeyi kullanarak sahte
 * blokları yarı şeffaf şekilde gösterebilir.
 *
 * Algoritma:
 *  1) Chunk içindeki tüm hava bloklarından BFS başlat.
 *  2) Hava bağlantısı olan blokları "gerçek zona" say.
 *  3) Taş ailesi bloklardan hiçbir 6 komşusu hava bağlantılı değilse → sahte aday.
 *  4) Chunk kenar bloklarını atla (komşu chunk bilinmiyor).
 */
@Mixin(ClientPlayNetworkHandler.class)
public class XRayModule {

    private static final int[][] DIRS = {
        { 1, 0, 0}, {-1, 0, 0},
        { 0, 1, 0}, { 0,-1, 0},
        { 0, 0, 1}, { 0, 0,-1}
    };

    private static final Map<Long, Set<BlockPos>> SUSPECTED_FAKES = new ConcurrentHashMap<>();

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
        analyzeChunk(chunk, cx, cz, key, mc.world.getBottomY(), mc.world.getTopY());
    }

    private static void analyzeChunk(WorldChunk chunk, int cx, int cz,
                                     long key, int minY, int maxY) {
        int baseX = cx << 4;
        int baseZ = cz << 4;
        int height = maxY - minY;

        boolean[][][] airConn = new boolean[16][height][16];
        Queue<int[]> queue = new ArrayDeque<>();

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int y = minY; y < maxY; y++) {
                    BlockState bs = chunk.getBlockState(new BlockPos(baseX + lx, y, baseZ + lz));
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

        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            int lx = cur[0], y = cur[1], lz = cur[2];
            for (int[] d : DIRS) {
                int nx = lx + d[0], ny = y + d[1], nz = lz + d[2];
                if (nx < 0 || nx >= 16 || nz < 0 || nz >= 16 || ny < minY || ny >= maxY) continue;
                int nyi = ny - minY;
                if (airConn[nx][nyi][nz]) continue;
                BlockState ns = chunk.getBlockState(new BlockPos(baseX + nx, ny, baseZ + nz));
                if (ns.isAir()) {
                    airConn[nx][nyi][nz] = true;
                    queue.add(new int[]{nx, ny, nz});
                }
            }
        }

        Set<BlockPos> suspected = new HashSet<>();
        for (int lx = 1; lx < 15; lx++) {
            for (int lz = 1; lz < 15; lz++) {
                for (int y = minY + 1; y < Math.min(maxY - 1, minY + 56); y++) {
                    int yi = y - minY;
                    BlockPos pos = new BlockPos(baseX + lx, y, baseZ + lz);
                    BlockState bs = chunk.getBlockState(pos);
                    if (!isFakeCandidate(bs)) continue;

                    boolean hasAirNeighbor = false;
                    for (int[] d : DIRS) {
                        int nx = lx + d[0], ny = y + d[1], nz = lz + d[2];
                        if (nx < 0 || nx >= 16 || nz < 0 || nz >= 16 || ny < minY || ny >= maxY) {
                            hasAirNeighbor = true;
                            break;
                        }
                        if (airConn[nx][ny - minY][nz]) {
                            hasAirNeighbor = true;
                            break;
                        }
                    }

                    if (!hasAirNeighbor) {
                        suspected.add(pos.toImmutable());
                    }
                }
            }
        }

        SUSPECTED_FAKES.put(key, suspected);
    }

    /**
     * Paper mode 2'nin sahte blok olarak kullandığı taş ailesi blokları döner.
     * Bu bloklar gerçek cevherlerin yerine gönderilir.
     */
    private static boolean isFakeCandidate(BlockState state) {
        Block b = state.getBlock();
        return b == Blocks.STONE
            || b == Blocks.DEEPSLATE
            || b == Blocks.ANDESITE
            || b == Blocks.DIORITE
            || b == Blocks.GRANITE
            || b == Blocks.GRAVEL
            || b == Blocks.TUFF
            || b == Blocks.CALCITE
            || b == Blocks.DIRT
            || b == Blocks.NETHERRACK
            || b == Blocks.BASALT
            || b == Blocks.BLACKSTONE;
    }

    /**
     * Verilen pozisyonun sahte blok adayı olup olmadığını sorgular.
     * XRay render sistemi bu metodu kullanarak sahte taşları vurgulayabilir.
     */
    public static boolean isSuspectedFake(BlockPos pos) {
        long key = chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
        Set<BlockPos> set = SUSPECTED_FAKES.get(key);
        return set != null && set.contains(pos);
    }

    /** Chunk boşaltıldığında önbelleği temizler (memory leak önlemi). */
    public static void clearChunk(int cx, int cz) {
        SUSPECTED_FAKES.remove(chunkKey(cx, cz));
    }

    /** Sahte aday sayısını döner (debug). */
    public static int getSuspectedCount(int cx, int cz) {
        Set<BlockPos> s = SUSPECTED_FAKES.get(chunkKey(cx, cz));
        return s == null ? 0 : s.size();
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx & 0xFFFFFFFFL) | (((long) cz & 0xFFFFFFFFL) << 32);
    }
}
