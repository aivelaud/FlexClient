package com.flex.client.xray;

import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FlexClient Ultra Xray — Anti-XRay Bypass Motoru
 *
 * Desteklenen anti-xray sistemleri:
 *   • Paper Engine Mode 1  — Hava yanındaki blokları gizler
 *   • Paper Engine Mode 2  — Sahte cevher serper
 *   • OreObfuscator        — Gelişmiş obfuscation eklentisi
 *   • CustomAntiXray       — Özel sunucu eklentileri
 *
 * Bypass teknikleri:
 *   1. İstatistiksel analiz — Chunk başına doğal maksimum aşıldıysa bypass açılır
 *   2. Y-seviyesi doğrulama — Cevherler yanlış Y'de ise sahte sayılır
 *   3. Damar bağlantı analizi — Sahte cevherler izole noktalar olarak görünür
 *   4. Yoğunluk haritası — Bölgesel yoğunluk sapmaları sahteleri işaretler
 *   5. Hava yakınlığı izi — Mode 1 bypass: hava bloklarından geriye iz sürer
 */
public class AntiXrayBypass {

    // Chunk başına kaç sahte blok tespit edildi
    private static final ConcurrentHashMap<Long, ChunkAnalysisResult> analysisCache = new ConcurrentHashMap<>();

    // Bypass aktif mi (sunucu anti-xray kullanıyor mu)
    private static volatile boolean bypassActive = false;
    private static volatile int detectedMode = 0; // 0=yok, 1=mode1, 2=mode2

    // İstatistikler
    private static int totalFakesRemoved   = 0;
    private static int totalChunksAnalyzed = 0;
    private static int totalChunksWithAntiXray = 0;

    public static class ChunkAnalysisResult {
        public final ChunkPos pos;
        public final Map<Block, Integer> naturalCount  = new HashMap<>();
        public final Map<Block, Integer> fakeCount     = new HashMap<>();
        public final Set<BlockPos>       fakePositions = new HashSet<>();
        public final Set<BlockPos>       realPositions = new HashSet<>();
        public boolean antiXrayDetected = false;
        public int mode = 0;
        public long timestamp;

        ChunkAnalysisResult(ChunkPos pos) {
            this.pos = pos;
            this.timestamp = System.currentTimeMillis();
        }
    }

    // ─── Ana Analiz ─────────────────────────────────────────────

    /**
     * Bir chunk'ı analiz eder, sahte cevherleri belirler
     */
    public static ChunkAnalysisResult analyzeChunk(World world, Chunk chunk) {
        ChunkPos cPos = chunk.getPos();
        long key = cPos.toLong();

        // Cache kontrolü (30 saniye geçerli)
        ChunkAnalysisResult cached = analysisCache.get(key);
        if (cached != null && System.currentTimeMillis() - cached.timestamp < 30_000) {
            return cached;
        }

        ChunkAnalysisResult result = new ChunkAnalysisResult(cPos);
        totalChunksAnalyzed++;

        // Adım 1: Tüm cevherleri say
        Map<Block, List<BlockPos>> orePositions = scanChunkOres(world, chunk, cPos);

        // Adım 2: Y-seviyesi filtreleme
        Map<Block, List<BlockPos>> yFiltered = filterByYLevel(orePositions, result);

        // Adım 3: Chunk başına doğal limit kontrolü
        boolean limitExceeded = checkDensityLimits(yFiltered, result);

        // Adım 4: Mode tespiti
        if (limitExceeded) {
            result.antiXrayDetected = true;
            result.mode = detectMode(world, chunk, yFiltered);
            bypassActive = true;
            detectedMode = result.mode;
            totalChunksWithAntiXray++;
        }

        // Adım 5: Sahte blokları filtrele
        if (result.antiXrayDetected) {
            if (result.mode == 2) {
                filterFakeOresMode2(yFiltered, result, world, chunk);
            } else if (result.mode == 1) {
                filterFakeOresMode1(yFiltered, result, world, chunk);
            } else {
                filterFakeOresGeneric(yFiltered, result, world, chunk);
            }
        } else {
            // Sahte yok, hepsi gerçek
            for (Map.Entry<Block, List<BlockPos>> entry : yFiltered.entrySet()) {
                result.realPositions.addAll(entry.getValue());
            }
        }

        totalFakesRemoved += result.fakePositions.size();
        analysisCache.put(key, result);
        return result;
    }

    // ─── Chunk Tarama ────────────────────────────────────────────

    private static Map<Block, List<BlockPos>> scanChunkOres(World world, Chunk chunk, ChunkPos cPos) {
        Map<Block, List<BlockPos>> found = new HashMap<>();
        int baseX = cPos.getStartX();
        int baseZ = cPos.getStartZ();

        ChunkSection[] sections = chunk.getSectionArray();
        int minY = world.getBottomY();

        for (int si = 0; si < sections.length; si++) {
            ChunkSection section = sections[si];
            if (section == null || section.isEmpty()) continue;
            int secBaseY = minY + si * 16;

            for (int lx = 0; lx < 16; lx++) {
                for (int ly = 0; ly < 16; ly++) {
                    for (int lz = 0; lz < 16; lz++) {
                        Block b = section.getBlockState(lx, ly, lz).getBlock();
                        if (!XrayConfig.isTargetBlock(b)) continue;
                        BlockPos pos = new BlockPos(baseX + lx, secBaseY + ly, baseZ + lz);
                        found.computeIfAbsent(b, k -> new ArrayList<>()).add(pos);
                    }
                }
            }
        }
        return found;
    }

    // ─── Y-Seviyesi Filtreleme ───────────────────────────────────

    private static Map<Block, List<BlockPos>> filterByYLevel(
            Map<Block, List<BlockPos>> orePositions, ChunkAnalysisResult result) {

        Map<Block, List<BlockPos>> filtered = new HashMap<>();
        for (Map.Entry<Block, List<BlockPos>> entry : orePositions.entrySet()) {
            Block b = entry.getKey();
            List<BlockPos> valid   = new ArrayList<>();
            List<BlockPos> invalid = new ArrayList<>();

            for (BlockPos pos : entry.getValue()) {
                if (XrayConfig.isValidYLevel(b, pos.getY())) {
                    valid.add(pos);
                } else {
                    invalid.add(pos);
                    result.fakePositions.add(pos);
                }
            }

            if (!invalid.isEmpty()) {
                result.fakeCount.merge(b, invalid.size(), Integer::sum);
                result.antiXrayDetected = true; // Y dışı = kesinlikle sahte
            }
            if (!valid.isEmpty()) {
                filtered.put(b, valid);
            }
        }
        return filtered;
    }

    // ─── Yoğunluk Limit Kontrolü ────────────────────────────────

    private static boolean checkDensityLimits(
            Map<Block, List<BlockPos>> orePositions, ChunkAnalysisResult result) {

        boolean exceeded = false;
        for (Map.Entry<Block, List<BlockPos>> entry : orePositions.entrySet()) {
            Block b = entry.getKey();
            int count   = entry.getValue().size();
            int maxNat  = XrayConfig.getMaxPerChunk(b);

            result.naturalCount.put(b, count);

            if (count > maxNat * 2) { // 2x katında sahte var demektir
                exceeded = true;
            }
        }
        return exceeded;
    }

    // ─── Mode Tespiti ────────────────────────────────────────────

    /**
     * Anti-Xray engine mode'unu tespit eder.
     * Mode 1: Hava yanındakiler görünür, geri kalanlar gizli (stone ile değiştirilmiş)
     * Mode 2: Gerçek cevherler saklanır, sahte cevherler eklenir
     */
    private static int detectMode(World world, Chunk chunk, Map<Block, List<BlockPos>> orePositions) {
        // Mode 2 ipucu: Çok fazla cevher, uniform dağılım
        int totalOres = orePositions.values().stream().mapToInt(List::size).sum();
        if (totalOres > 200) return 2; // Mode 2'de chunk başına 200+ sahte blok görünür

        // Mode 1 ipucu: Sadece hava yanındaki cevherler görünüyor
        // Hava yanındaki cevher oranını hesapla
        int airAdjacent = 0;
        int total = 0;
        for (List<BlockPos> positions : orePositions.values()) {
            for (BlockPos pos : positions) {
                total++;
                if (hasAirNeighbor(world, pos)) airAdjacent++;
            }
        }

        if (total > 0) {
            double airRatio = (double) airAdjacent / total;
            if (airRatio > 0.8) return 1; // %80+ hava yanında = Mode 1
        }

        return 2; // Varsayılan mode 2
    }

    // ─── Mode 2 Bypass ───────────────────────────────────────────

    /**
     * Mode 2 bypass: Sahte cevherleri istatistiksel analiz ile tespit eder
     * Sahte belirtiler:
     *  - İzole (komşu cevher yok)
     *  - Chunk'ta aşırı fazla aynı blok
     *  - Uniform grid benzeri dağılım
     *  - Tek blok boyutlu, damar oluşturmuyor
     */
    private static void filterFakeOresMode2(
            Map<Block, List<BlockPos>> orePositions, ChunkAnalysisResult result,
            World world, Chunk chunk) {

        for (Map.Entry<Block, List<BlockPos>> entry : orePositions.entrySet()) {
            Block b = entry.getKey();
            List<BlockPos> positions = entry.getValue();
            int maxNat = XrayConfig.getMaxPerChunk(b);

            if (positions.size() <= maxNat) {
                // Limit içinde, hepsi gerçek
                result.realPositions.addAll(positions);
                continue;
            }

            // Damar bağlantı analizi ile gerçek vs sahte ayırt et
            List<Set<BlockPos>> veins = findConnectedVeins(positions);
            XrayConfig.OreEntry oreEntry = XrayConfig.getEntry(b);
            int maxVein = oreEntry != null ? oreEntry.maxVeinSize : 8;

            List<BlockPos> real  = new ArrayList<>();
            List<BlockPos> fake  = new ArrayList<>();

            for (Set<BlockPos> vein : veins) {
                if (vein.size() > maxVein * 2) {
                    // Damar çok büyük = sahte bloklar birleşmiş
                    // En doğal görünenleri tut
                    List<BlockPos> sorted = new ArrayList<>(vein);
                    sorted.sort(Comparator.comparingInt(p -> scoreOreNaturalness(p, world)));
                    // İlk maxVein kadarını gerçek say
                    for (int i = 0; i < sorted.size(); i++) {
                        if (i < maxVein) real.add(sorted.get(i));
                        else fake.add(sorted.get(i));
                    }
                } else if (vein.size() == 1) {
                    // Tek blok: istatistiksel olarak %70 sahte
                    BlockPos pos = vein.iterator().next();
                    if (isLikelyFakeSingle(pos, world, b)) {
                        fake.add(pos);
                    } else {
                        real.add(pos);
                    }
                } else {
                    // Normal damar boyutu, gerçek
                    real.addAll(vein);
                }
            }

            // Hala limit aşılıyorsa ek filtreleme
            if (real.size() > maxNat * 3) {
                real.sort(Comparator.comparingInt(p -> -scoreOreNaturalness(p, world)));
                while (real.size() > maxNat * 2) {
                    fake.add(real.remove(real.size() - 1));
                }
            }

            result.realPositions.addAll(real);
            result.fakePositions.addAll(fake);
            result.fakeCount.merge(b, fake.size(), Integer::sum);
        }
    }

    // ─── Mode 1 Bypass ───────────────────────────────────────────

    /**
     * Mode 1 bypass: Hava yanındakileri ve hava'dan erişilebilir olanları göster.
     * Tünel kazan oyuncu etrafında arama yaparak gerçek cevherleri bulur.
     */
    private static void filterFakeOresMode1(
            Map<Block, List<BlockPos>> orePositions, ChunkAnalysisResult result,
            World world, Chunk chunk) {

        for (Map.Entry<Block, List<BlockPos>> entry : orePositions.entrySet()) {
            List<BlockPos> positions = entry.getValue();
            for (BlockPos pos : positions) {
                // Mode 1'de görünen cevher = hava bitişiğindeki gerçek cevher
                // Bunları zaten görebiliyoruz. Diğerlerini BFS ile izle
                if (hasAirNeighbor(world, pos)) {
                    result.realPositions.add(pos);
                    // Komşu cevherleri de gerçek say (damar devamı)
                    expandVeinFromAir(pos, world, result);
                }
                // Hava erişimi yoksa gizlenmiş cevher — göster de
                else {
                    // Mode 1'de bunlar stone ile değiştirilmiş, ama biz tahmin ederiz
                    result.realPositions.add(pos); // Göster (Y doğruysa gerçek olabilir)
                }
            }
        }
    }

    // ─── Generic Bypass ──────────────────────────────────────────

    private static void filterFakeOresGeneric(
            Map<Block, List<BlockPos>> orePositions, ChunkAnalysisResult result,
            World world, Chunk chunk) {

        for (Map.Entry<Block, List<BlockPos>> entry : orePositions.entrySet()) {
            Block b = entry.getKey();
            List<BlockPos> positions = entry.getValue();
            int maxNat = XrayConfig.getMaxPerChunk(b);

            if (positions.size() <= maxNat) {
                result.realPositions.addAll(positions);
            } else {
                // Skor tabanlı sıralama
                positions.sort(Comparator.comparingInt(p -> -scoreOreNaturalness(p, world)));
                for (int i = 0; i < positions.size(); i++) {
                    if (i < maxNat) result.realPositions.add(positions.get(i));
                    else result.fakePositions.add(positions.get(i));
                }
                result.fakeCount.merge(b, positions.size() - maxNat, Integer::sum);
            }
        }
    }

    // ─── Yardımcı Algoritmalar ───────────────────────────────────

    /**
     * Bağlı cevher damarlarını BFS ile bulur
     */
    private static List<Set<BlockPos>> findConnectedVeins(List<BlockPos> positions) {
        Set<BlockPos> remaining = new HashSet<>(positions);
        List<Set<BlockPos>> veins = new ArrayList<>();

        int[] dx = {1,-1,0,0,0,0};
        int[] dy = {0,0,1,-1,0,0};
        int[] dz = {0,0,0,0,1,-1};

        while (!remaining.isEmpty()) {
            Set<BlockPos> vein  = new HashSet<>();
            Queue<BlockPos> queue = new LinkedList<>();
            BlockPos start = remaining.iterator().next();
            queue.add(start);
            remaining.remove(start);
            vein.add(start);

            while (!queue.isEmpty()) {
                BlockPos cur = queue.poll();
                for (int d = 0; d < 6; d++) {
                    BlockPos neighbor = new BlockPos(cur.getX()+dx[d], cur.getY()+dy[d], cur.getZ()+dz[d]);
                    if (remaining.contains(neighbor)) {
                        remaining.remove(neighbor);
                        queue.add(neighbor);
                        vein.add(neighbor);
                    }
                }
            }
            veins.add(vein);
        }
        return veins;
    }

    /**
     * Bir cevher bloğunun ne kadar "doğal" göründüğünü puanlar (yüksek = daha doğal)
     * Puanlama kriterleri:
     *  +10 Doğru Y seviyesinde (optimal yakınında)
     *  +5  Komşu taş/derinlik taşı var
     *  +3  Diğer cevherlere yakın (damar)
     *  -5  Düz bir grid üzerinde
     *  -3  Çok izole
     */
    private static int scoreOreNaturalness(BlockPos pos, World world) {
        int score = 0;
        Block b = world.getBlockState(pos).getBlock();
        XrayConfig.OreEntry e = XrayConfig.getEntry(b);
        if (e == null) return 0;

        // Y yakınlığı puanı
        int yDist = Math.abs(pos.getY() - e.bestY);
        score += Math.max(0, 10 - yDist / 4);

        // Taş komşuluğu
        int stoneNeighbors = countStoneNeighbors(pos, world);
        score += stoneNeighbors;

        // Düz grid tespiti (sahte cevherler genellikle her X blokta bir yerleştirilir)
        int x = pos.getX(), z = pos.getZ();
        if (x % 8 == 0 && z % 8 == 0) score -= 5; // Mode 2 tipik grid
        if (x % 4 == 0 && z % 4 == 0) score -= 3;

        return score;
    }

    private static int countStoneNeighbors(BlockPos pos, World world) {
        int count = 0;
        int[] d = {-1,0,1};
        for (int dx : d) for (int dy : d) for (int dz : d) {
            if (dx == 0 && dy == 0 && dz == 0) continue;
            Block nb = world.getBlockState(pos.add(dx, dy, dz)).getBlock();
            if (nb == net.minecraft.block.Blocks.STONE
             || nb == net.minecraft.block.Blocks.DEEPSLATE
             || nb == net.minecraft.block.Blocks.NETHERRACK
             || nb == net.minecraft.block.Blocks.TUFF) {
                count++;
            }
        }
        return count;
    }

    /**
     * Tek bir cevher bloğunun sahte olup olmadığını tahmin eder
     * (Mode 2: izole sahte cevherler için)
     */
    private static boolean isLikelyFakeSingle(BlockPos pos, World world, Block b) {
        // Grid pozisyonunda mı?
        if (pos.getX() % 8 == 0 || pos.getZ() % 8 == 0) return true;
        // Çevresinde hiç taş yok mu? (havada — imkansız doğal durum)
        if (countStoneNeighbors(pos, world) == 0) return true;
        // Y çok uygunsuz mu?
        XrayConfig.OreEntry e = XrayConfig.getEntry(b);
        if (e != null && Math.abs(pos.getY() - e.bestY) > 30) return true;
        return false;
    }

    private static boolean hasAirNeighbor(World world, BlockPos pos) {
        int[] dx = {1,-1,0,0,0,0};
        int[] dy = {0,0,1,-1,0,0};
        int[] dz = {0,0,0,0,1,-1};
        for (int d = 0; d < 6; d++) {
            Block nb = world.getBlockState(pos.add(dx[d], dy[d], dz[d])).getBlock();
            if (nb == net.minecraft.block.Blocks.AIR
             || nb == net.minecraft.block.Blocks.CAVE_AIR
             || nb == net.minecraft.block.Blocks.VOID_AIR) {
                return true;
            }
        }
        return false;
    }

    /**
     * Hava'ya bitişik cevherden başlayarak komşu cevherleri gerçek olarak işaretle
     */
    private static void expandVeinFromAir(BlockPos start, World world, ChunkAnalysisResult result) {
        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(start);
        visited.add(start);
        int[] dx = {1,-1,0,0,0,0};
        int[] dy = {0,0,1,-1,0,0};
        int[] dz = {0,0,0,0,1,-1};

        while (!queue.isEmpty() && visited.size() < 64) {
            BlockPos cur = queue.poll();
            for (int d = 0; d < 6; d++) {
                BlockPos nb = cur.add(dx[d], dy[d], dz[d]);
                if (visited.contains(nb)) continue;
                Block b = world.getBlockState(nb).getBlock();
                if (XrayConfig.isTargetBlock(b)) {
                    visited.add(nb);
                    result.realPositions.add(nb);
                    queue.add(nb);
                }
            }
        }
    }

    // ─── Cache Yönetimi ──────────────────────────────────────────

    public static void invalidateChunk(ChunkPos pos) {
        analysisCache.remove(pos.toLong());
    }

    public static void clearCache() {
        analysisCache.clear();
    }

    public static boolean isFakeBlock(BlockPos pos, ChunkPos chunkPos) {
        ChunkAnalysisResult r = analysisCache.get(chunkPos.toLong());
        if (r == null) return false;
        return r.fakePositions.contains(pos);
    }

    public static boolean isRealBlock(BlockPos pos, ChunkPos chunkPos) {
        ChunkAnalysisResult r = analysisCache.get(chunkPos.toLong());
        if (r == null) return true; // Analiz yok, varsayılan: göster
        return r.realPositions.contains(pos);
    }

    // ─── İstatistik API ──────────────────────────────────────────

    public static boolean isBypassActive()        { return bypassActive; }
    public static int     getDetectedMode()       { return detectedMode; }
    public static int     getTotalFakesRemoved()  { return totalFakesRemoved; }
    public static int     getChunksAnalyzed()     { return totalChunksAnalyzed; }
    public static int     getChunksWithAntiXray() { return totalChunksWithAntiXray; }
    public static void    reset() {
        bypassActive   = false;
        detectedMode   = 0;
        totalFakesRemoved   = 0;
        totalChunksAnalyzed = 0;
        totalChunksWithAntiXray = 0;
        analysisCache.clear();
    }
}
