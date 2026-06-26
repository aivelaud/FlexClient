package com.flex.client.xray;

import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FlexClient Ultra Xray — Ore Damar Analiz Motoru
 *
 * Gerçek cevher damarlarını tespit eder, boyutlarını ve şekillerini analiz eder.
 * Anti-xray bypass için kritik: gerçek damarlar doğal şekiller oluşturur.
 *
 * Özellikler:
 *  - BFS tabanlı damar keşfi
 *  - Damar şekil skoru (küresel = doğal, çizgisel = sahte)
 *  - Damar yoğunluk haritası
 *  - En yakın damar hesabı (navigate için)
 *  - Damar öncelik sıralaması (nadir = yüksek öncelik)
 */
public class OreVeinAnalyzer {

    public static class VeinData {
        public final Block block;
        public final List<BlockPos> positions;
        public final BlockPos center;
        public final double avgX, avgY, avgZ;
        public final int size;
        public final double naturalScore;   // 0.0-1.0 (1=çok doğal)
        public final int rarity;            // 1=çok nadir
        public final double spreadX, spreadY, spreadZ;
        public final boolean likelyReal;

        VeinData(Block block, List<BlockPos> positions) {
            this.block     = block;
            this.positions = Collections.unmodifiableList(positions);
            this.size      = positions.size();

            double sumX = 0, sumY = 0, sumZ = 0;
            for (BlockPos p : positions) { sumX += p.getX(); sumY += p.getY(); sumZ += p.getZ(); }
            this.avgX = sumX / size;
            this.avgY = sumY / size;
            this.avgZ = sumZ / size;
            this.center = new BlockPos((int)avgX, (int)avgY, (int)avgZ);

            double sX = 0, sY = 0, sZ = 0;
            for (BlockPos p : positions) {
                sX += (p.getX() - avgX) * (p.getX() - avgX);
                sY += (p.getY() - avgY) * (p.getY() - avgY);
                sZ += (p.getZ() - avgZ) * (p.getZ() - avgZ);
            }
            this.spreadX = Math.sqrt(sX / size);
            this.spreadY = Math.sqrt(sY / size);
            this.spreadZ = Math.sqrt(sZ / size);

            XrayConfig.OreEntry e = XrayConfig.getEntry(block);
            this.rarity      = e != null ? e.rarity : 5;
            this.naturalScore = computeNaturalScore(e);
            this.likelyReal   = naturalScore > 0.4;
        }

        private double computeNaturalScore(XrayConfig.OreEntry e) {
            double score = 1.0;

            // Boyut kontrolü
            if (e != null) {
                if (size > e.maxVeinSize * 2) score -= 0.4; // Çok büyük = sahte
                if (size > e.maxVeinSize)     score -= 0.2;
                if (size == 1)                score -= 0.1; // Tek blok biraz şüpheli
            }

            // Şekil kontrolü: Küresel dağılım daha doğal
            double maxSpread = Math.max(spreadX, Math.max(spreadY, spreadZ));
            double minSpread = Math.min(spreadX, Math.min(spreadY, spreadZ));
            if (maxSpread > 0) {
                double ratio = minSpread / maxSpread;
                // ratio yakın 1 = küresel (doğal), 0 = çizgisel (sahte)
                score += ratio * 0.3;
            }

            // Grid pozisyon kontrolü
            int cx = (int)(avgX), cz = (int)(avgZ);
            if (cx % 8 == 0 && cz % 8 == 0) score -= 0.3;
            if (cx % 4 == 0 && cz % 4 == 0) score -= 0.15;

            return Math.max(0, Math.min(1, score));
        }

        public double distanceTo(double x, double y, double z) {
            double dx = avgX - x, dy = avgY - y, dz = avgZ - z;
            return Math.sqrt(dx*dx + dy*dy + dz*dz);
        }

        public String getDisplayName() {
            XrayConfig.OreEntry e = XrayConfig.getEntry(block);
            return e != null ? e.name : block.getTranslationKey();
        }

        public int getColor() {
            return XrayConfig.getColorFor(block);
        }

        public int getGlowColor() {
            return XrayConfig.getGlowColorFor(block);
        }
    }

    // Bulunan damarların cache'i (chunkKey -> damar listesi)
    private static final ConcurrentHashMap<Long, List<VeinData>> veinCache = new ConcurrentHashMap<>();

    // ─── Damar Keşfi ─────────────────────────────────────────────

    /**
     * Verilen pozisyonlar listesinden bağlı damarları oluşturur
     */
    public static List<VeinData> buildVeins(Block block, Collection<BlockPos> positions) {
        if (positions.isEmpty()) return Collections.emptyList();

        Set<BlockPos> remaining = new HashSet<>(positions);
        List<VeinData> veins = new ArrayList<>();

        int[] dx = {1,-1,0,0,0,0};
        int[] dy = {0,0,1,-1,0,0};
        int[] dz = {0,0,0,0,1,-1};

        while (!remaining.isEmpty()) {
            List<BlockPos> veinBlocks = new ArrayList<>();
            Queue<BlockPos> queue = new LinkedList<>();
            BlockPos start = remaining.iterator().next();
            queue.add(start);
            remaining.remove(start);
            veinBlocks.add(start);

            while (!queue.isEmpty()) {
                BlockPos cur = queue.poll();
                for (int d = 0; d < 6; d++) {
                    BlockPos nb = new BlockPos(cur.getX()+dx[d], cur.getY()+dy[d], cur.getZ()+dz[d]);
                    if (remaining.contains(nb)) {
                        remaining.remove(nb);
                        queue.add(nb);
                        veinBlocks.add(nb);
                    }
                }
            }

            if (!veinBlocks.isEmpty()) {
                veins.add(new VeinData(block, veinBlocks));
            }
        }

        // Rarity'e göre sırala (nadir önce)
        veins.sort(Comparator.comparingInt(v -> v.rarity));
        return veins;
    }

    // ─── Bölgesel Tarama ─────────────────────────────────────────

    /**
     * Oyuncu etrafındaki dünyayı tarar, tüm hedef cevherleri damar olarak döner
     * @param world  Minecraft dünyası
     * @param center Merkez pozisyon
     * @param radius Tarama yarıçapı (blok)
     */
    public static List<VeinData> scanAroundPlayer(World world, BlockPos center, int radius) {
        Map<Block, List<BlockPos>> byBlock = new HashMap<>();

        int minY = Math.max(world.getBottomY(), center.getY() - radius);
        int maxY = Math.min(world.getTopY() - 1, center.getY() + radius);

        for (int x = center.getX() - radius; x <= center.getX() + radius; x += 1) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z += 1) {
                for (int y = minY; y <= maxY; y++) {
                    // Yarıçap kontrolü (küre tarama)
                    int dx = x - center.getX();
                    int dy = y - center.getY();
                    int dz = z - center.getZ();
                    if (dx*dx + dy*dy + dz*dz > radius*radius) continue;

                    BlockPos pos = new BlockPos(x, y, z);
                    Block b = world.getBlockState(pos).getBlock();
                    if (!XrayConfig.isTargetBlock(b)) continue;

                    // Anti-xray bypass kontrolü
                    net.minecraft.util.math.ChunkPos cp = new net.minecraft.util.math.ChunkPos(pos);
                    if (AntiXrayBypass.isFakeBlock(pos, cp)) continue;

                    byBlock.computeIfAbsent(b, k -> new ArrayList<>()).add(pos);
                }
            }
        }

        List<VeinData> allVeins = new ArrayList<>();
        for (Map.Entry<Block, List<BlockPos>> entry : byBlock.entrySet()) {
            allVeins.addAll(buildVeins(entry.getKey(), entry.getValue()));
        }

        // Uzaklık + nadir sıralaması
        allVeins.sort((a, b) -> {
            if (a.rarity != b.rarity) return Integer.compare(a.rarity, b.rarity);
            double da = a.distanceTo(center.getX(), center.getY(), center.getZ());
            double db = b.distanceTo(center.getX(), center.getY(), center.getZ());
            return Double.compare(da, db);
        });

        return allVeins;
    }

    // ─── Ore Sayım İstatistikleri ────────────────────────────────

    public static class OreStats {
        public final Map<Block, Integer> counts = new LinkedHashMap<>();
        public final Map<Block, Double>  avgDist = new LinkedHashMap<>();
        public final Map<Block, BlockPos> nearest = new LinkedHashMap<>();
        public int totalBlocks = 0;
        public int totalVeins  = 0;
    }

    public static OreStats computeStats(List<VeinData> veins, BlockPos playerPos) {
        OreStats stats = new OreStats();
        stats.totalVeins = veins.size();

        Map<Block, List<VeinData>> byBlock = new LinkedHashMap<>();
        for (VeinData v : veins) {
            byBlock.computeIfAbsent(v.block, k -> new ArrayList<>()).add(v);
        }

        for (Map.Entry<Block, List<VeinData>> entry : byBlock.entrySet()) {
            Block b = entry.getKey();
            List<VeinData> bVeins = entry.getValue();

            int count = bVeins.stream().mapToInt(v -> v.size).sum();
            stats.counts.put(b, count);
            stats.totalBlocks += count;

            double totalDist = 0;
            double minDist   = Double.MAX_VALUE;
            BlockPos nearestPos = playerPos;

            for (VeinData v : bVeins) {
                double d = v.distanceTo(playerPos.getX(), playerPos.getY(), playerPos.getZ());
                totalDist += d;
                if (d < minDist) {
                    minDist = d;
                    nearestPos = v.center;
                }
            }
            stats.avgDist.put(b, totalDist / bVeins.size());
            stats.nearest.put(b, nearestPos);
        }
        return stats;
    }

    // ─── En Yakın Damar ──────────────────────────────────────────

    public static VeinData findNearestVein(List<VeinData> veins, BlockPos player, Block filterBlock) {
        VeinData nearest = null;
        double minDist   = Double.MAX_VALUE;
        for (VeinData v : veins) {
            if (filterBlock != null && v.block != filterBlock) continue;
            double d = v.distanceTo(player.getX(), player.getY(), player.getZ());
            if (d < minDist) { minDist = d; nearest = v; }
        }
        return nearest;
    }

    // ─── Cache ───────────────────────────────────────────────────

    public static void cacheVeins(long chunkKey, List<VeinData> veins) {
        veinCache.put(chunkKey, veins);
    }

    public static List<VeinData> getCachedVeins(long chunkKey) {
        return veinCache.getOrDefault(chunkKey, Collections.emptyList());
    }

    public static void invalidate(long chunkKey) {
        veinCache.remove(chunkKey);
    }

    public static void clearAll() {
        veinCache.clear();
    }
}
