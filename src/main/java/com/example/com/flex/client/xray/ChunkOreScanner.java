package com.flex.client.xray;

import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * FlexClient Ultra Xray — Asenkron Chunk Ore Tarayıcı
 *
 * Tüm yüklü chunk'ları arka planda tarar, cevher pozisyonlarını cache'ler.
 * Performans için:
 *  - Asenkron tarama (ana thread bloklanmaz)
 *  - Chunk başına bir kez tara, sonuç cache'le
 *  - Oyuncu hareket ettikçe önce yakın chunk'ları tara
 *  - Boşaltılan chunk'ların cache'ini temizle
 *  - Maksimum eşzamanlı tarama limiti
 *
 * Anti-xray bypass entegrasyonu:
 *  - Her chunk analiz edildiğinde AntiXrayBypass çağrılır
 *  - Sahte bloklar filtrelenir
 *  - Gerçek cevherler VeinData olarak saklanır
 */
public class ChunkOreScanner {

    private static final int SCAN_RADIUS_CHUNKS = 8;   // chunk yarıçapı (x/z)
    private static final int MAX_CONCURRENT    = 3;     // eşzamanlı tarama
    private static final long CACHE_VALID_MS   = 60_000; // 60s cache

    private static final ExecutorService executor =
        Executors.newFixedThreadPool(MAX_CONCURRENT, r -> {
            Thread t = new Thread(r, "FlexXray-Scanner");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });

    // ChunkKey -> tarama sonucu
    private static final ConcurrentHashMap<Long, ScanResult> scanCache = new ConcurrentHashMap<>();
    // Şu an taranan chunk'lar
    private static final Set<Long> inProgress = ConcurrentHashMap.newKeySet();

    private static final AtomicInteger totalScanned = new AtomicInteger(0);
    private static final AtomicInteger queueSize    = new AtomicInteger(0);

    public static class ScanResult {
        public final ChunkPos chunkPos;
        public final List<OreVeinAnalyzer.VeinData> veins;
        public final Map<Block, Integer> counts;
        public final AntiXrayBypass.ChunkAnalysisResult antiXrayResult;
        public final long timestamp;
        public final boolean hadAntiXray;

        ScanResult(ChunkPos cp, List<OreVeinAnalyzer.VeinData> veins,
                   Map<Block, Integer> counts,
                   AntiXrayBypass.ChunkAnalysisResult axResult) {
            this.chunkPos       = cp;
            this.veins          = Collections.unmodifiableList(veins);
            this.counts         = Collections.unmodifiableMap(counts);
            this.antiXrayResult = axResult;
            this.timestamp      = System.currentTimeMillis();
            this.hadAntiXray    = axResult != null && axResult.antiXrayDetected;
        }
    }

    // ─── Tarama Başlatma ─────────────────────────────────────────

    /**
     * Oyuncu pozisyonu etrafındaki chunk'ları taramaya başlatır.
     * Ana thread'den her tick çağrılabilir.
     */
    public static void triggerScanAround(BlockPos playerPos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        ChunkPos playerChunk = new ChunkPos(playerPos);
        List<ChunkPos> toScan = new ArrayList<>();

        for (int cx = -SCAN_RADIUS_CHUNKS; cx <= SCAN_RADIUS_CHUNKS; cx++) {
            for (int cz = -SCAN_RADIUS_CHUNKS; cz <= SCAN_RADIUS_CHUNKS; cz++) {
                ChunkPos cp = new ChunkPos(playerChunk.x + cx, playerChunk.z + cz);
                long key = cp.toLong();

                if (inProgress.contains(key)) continue;

                ScanResult cached = scanCache.get(key);
                if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_VALID_MS) continue;

                // Chunk yüklü mü?
                if (!mc.world.isChunkLoaded(cp.x, cp.z)) continue;

                toScan.add(cp);
            }
        }

        // Yakın chunk'lar önce (manhatttan distance sıralaması)
        toScan.sort(Comparator.comparingInt(cp ->
            Math.abs(cp.x - playerChunk.x) + Math.abs(cp.z - playerChunk.z)));

        int submitted = 0;
        for (ChunkPos cp : toScan) {
            if (submitted >= 6) break; // Her tick max 6 submit
            if (inProgress.size() >= MAX_CONCURRENT * 4) break;

            long key = cp.toLong();
            if (inProgress.add(key)) {
                queueSize.incrementAndGet();
                executor.submit(() -> scanChunk(mc.world, cp, key));
                submitted++;
            }
        }
    }

    // ─── Chunk Tarama ────────────────────────────────────────────

    private static void scanChunk(ClientWorld world, ChunkPos cp, long key) {
        try {
            if (world == null || !world.isChunkLoaded(cp.x, cp.z)) return;

            WorldChunk chunk = world.getChunk(cp.x, cp.z);
            if (chunk == null) return;

            // Anti-xray bypass analizi
            AntiXrayBypass.ChunkAnalysisResult axResult =
                AntiXrayBypass.analyzeChunk(world, chunk);

            // Gerçek cevherleri topla
            Map<Block, List<BlockPos>> realOres = new HashMap<>();
            for (BlockPos pos : axResult.realPositions) {
                Block b = world.getBlockState(pos).getBlock();
                if (XrayConfig.isTargetBlock(b)) {
                    realOres.computeIfAbsent(b, k -> new ArrayList<>()).add(pos);
                }
            }

            // Eğer AntiXray analizi boş bıraktıysa (anti-xray yok), tüm cevherleri tara
            if (axResult.realPositions.isEmpty()) {
                rawScanChunk(chunk, cp, world, realOres);
            }

            // Damarları oluştur
            List<OreVeinAnalyzer.VeinData> veins = new ArrayList<>();
            Map<Block, Integer> counts = new LinkedHashMap<>();

            for (Map.Entry<Block, List<BlockPos>> e : realOres.entrySet()) {
                List<OreVeinAnalyzer.VeinData> bVeins =
                    OreVeinAnalyzer.buildVeins(e.getKey(), e.getValue());
                veins.addAll(bVeins);
                counts.put(e.getKey(), e.getValue().size());
            }

            ScanResult result = new ScanResult(cp, veins, counts, axResult);
            scanCache.put(key, result);
            totalScanned.incrementAndGet();

        } catch (Exception ex) {
            // Sessizce devam et
        } finally {
            inProgress.remove(key);
            queueSize.decrementAndGet();
        }
    }

    /** Ham chunk taraması (anti-xray olmayan sunucular için) */
    private static void rawScanChunk(WorldChunk chunk, ChunkPos cp, ClientWorld world,
                                      Map<Block, List<BlockPos>> out) {
        int baseX = cp.getStartX();
        int baseZ = cp.getStartZ();
        ChunkSection[] sections = chunk.getSectionArray();
        int minY = world.getBottomY();

        for (int si = 0; si < sections.length; si++) {
            ChunkSection sec = sections[si];
            if (sec == null || sec.isEmpty()) continue;
            int baseY = minY + si * 16;
            for (int lx = 0; lx < 16; lx++) {
                for (int ly = 0; ly < 16; ly++) {
                    for (int lz = 0; lz < 16; lz++) {
                        Block b = sec.getBlockState(lx, ly, lz).getBlock();
                        if (!XrayConfig.isTargetBlock(b)) continue;
                        BlockPos pos = new BlockPos(baseX+lx, baseY+ly, baseZ+lz);
                        out.computeIfAbsent(b, k -> new ArrayList<>()).add(pos);
                    }
                }
            }
        }
    }

    // ─── Sonuç Erişim API ────────────────────────────────────────

    /**
     * Belirtilen chunk'ın tarama sonucunu döner (cache'den).
     * Taranmamışsa null döner.
     */
    public static ScanResult getResult(ChunkPos cp) {
        return scanCache.get(cp.toLong());
    }

    /**
     * Oyuncu etrafındaki tüm taranmış cevher damarlarını toplar.
     */
    public static List<OreVeinAnalyzer.VeinData> getAllVisibleVeins(BlockPos player, int radiusChunks) {
        List<OreVeinAnalyzer.VeinData> all = new ArrayList<>();
        ChunkPos center = new ChunkPos(player);

        for (int cx = -radiusChunks; cx <= radiusChunks; cx++) {
            for (int cz = -radiusChunks; cz <= radiusChunks; cz++) {
                ChunkPos cp = new ChunkPos(center.x + cx, center.z + cz);
                ScanResult r = scanCache.get(cp.toLong());
                if (r != null) all.addAll(r.veins);
            }
        }

        // Oyuncuya mesafeye göre sırala
        all.sort(Comparator.comparingDouble(v ->
            v.distanceTo(player.getX(), player.getY(), player.getZ())));
        return all;
    }

    /**
     * Toplam bulunan cevher sayısı (görünür range)
     */
    public static Map<Block, Integer> getTotalCounts(BlockPos player, int radiusChunks) {
        Map<Block, Integer> total = new LinkedHashMap<>();
        ChunkPos center = new ChunkPos(player);

        for (int cx = -radiusChunks; cx <= radiusChunks; cx++) {
            for (int cz = -radiusChunks; cz <= radiusChunks; cz++) {
                ScanResult r = scanCache.get(new ChunkPos(center.x+cx, center.z+cz).toLong());
                if (r == null) continue;
                r.counts.forEach((b, c) -> total.merge(b, c, Integer::sum));
            }
        }
        return total;
    }

    // ─── Cache Yönetimi ──────────────────────────────────────────

    public static void onChunkUnloaded(ChunkPos cp) {
        long key = cp.toLong();
        scanCache.remove(key);
        AntiXrayBypass.invalidateChunk(cp);
        OreVeinAnalyzer.invalidate(key);
    }

    public static void onChunkLoaded(ChunkPos cp) {
        // Yeni chunk yüklendiğinde cache'i geçersiz kıl
        long key = cp.toLong();
        scanCache.remove(key);
    }

    public static void clearAll() {
        scanCache.clear();
        inProgress.clear();
        AntiXrayBypass.clearCache();
        OreVeinAnalyzer.clearAll();
    }

    // ─── İstatistik ──────────────────────────────────────────────

    public static int getTotalScanned()   { return totalScanned.get(); }
    public static int getQueueSize()      { return queueSize.get(); }
    public static int getCacheSize()      { return scanCache.size(); }
    public static boolean isScanning()    { return !inProgress.isEmpty(); }
}
