package com.flex.client.xray;

import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * FlexClient Ultra Xray — Asenkron Chunk Ore Tarayıcı
 *
 * Tüm yüklü chunk'ları arka planda tarar, cevher pozisyonlarını cache'ler.
 *
 * Anti-xray bypass entegrasyonu (katmanlı):
 *  1. AntiXrayBypass  — İstatistiksel + damar analizi (mevcut sistem)
 *  2. AntiXrayFilter  — Komşu/ışık/Y/izolasyon/paket skor sistemi (yeni sistem)
 *  3. XRayModule BFS  — Hava bağlantısı analizi (mixin)
 *
 * Performans:
 *  - Asenkron tarama (ana thread bloklanmaz)
 *  - Chunk başına bir kez tara, sonuç 60 sn cache'le
 *  - Oyuncu hareket ettikçe önce yakın chunk'ları tara
 *  - Boşaltılan chunk'ların cache'ini temizle
 */
public class ChunkOreScanner {

    private static final int SCAN_RADIUS_CHUNKS = 8;   // chunk yarıçapı
    private static final int MAX_CONCURRENT     = 3;   // eşzamanlı tarama limiti
    private static final long CACHE_VALID_MS    = 60_000; // 60s cache geçerliliği

    private static final ExecutorService executor =
        Executors.newFixedThreadPool(MAX_CONCURRENT, r -> {
            Thread t = new Thread(r, "FlexXray-Scanner");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });

    /** ChunkKey → Tarama sonucu */
    private static final ConcurrentHashMap<Long, ScanResult> scanCache = new ConcurrentHashMap<>();
    /** Şu an taranmakta olan chunk'lar */
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

    // ── Tarama Başlatma ───────────────────────────────────────────────────────

    /**
     * Oyuncu pozisyonu etrafındaki chunk'ları taramaya başlatır.
     * Ana thread'den her tick çağrılabilir.
     *
     * @param playerPos Oyuncunun mevcut konumu
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
                if (cached != null
                        && System.currentTimeMillis() - cached.timestamp < CACHE_VALID_MS) continue;

                if (!mc.world.isChunkLoaded(cp.x, cp.z)) continue;
                toScan.add(cp);
            }
        }

        // Yakın chunk'lar önce (manhattan distance sıralaması)
        toScan.sort(Comparator.comparingInt(cp ->
            Math.abs(cp.x - playerChunk.x) + Math.abs(cp.z - playerChunk.z)));

        int submitted = 0;
        for (ChunkPos cp : toScan) {
            if (submitted >= 6) break;
            if (inProgress.size() >= MAX_CONCURRENT * 4) break;

            long key = cp.toLong();
            if (inProgress.add(key)) {
                queueSize.incrementAndGet();
                executor.submit(() -> scanChunk(mc.world, cp, key));
                submitted++;
            }
        }
    }

    // ── Chunk Tarama ─────────────────────────────────────────────────────────

    /**
     * Tek bir chunk'ı tarar:
     *  1. Ham cevher konumlarını topla
     *  2. AntiXrayFilter ile sahteleri çıkar (katmanlı skor sistemi)
     *  3. AntiXrayBypass ile istatistiksel analizi uygula
     *  4. Damar verilerini oluştur ve cache'e kaydet
     *
     * @param world ClientWorld referansı
     * @param cp    Taranacak chunk pozisyonu
     * @param key   Chunk long key (cache için)
     */
    private static void scanChunk(ClientWorld world, ChunkPos cp, long key) {
        try {
            if (world == null || !world.isChunkLoaded(cp.x, cp.z)) return;

            WorldChunk chunk = world.getChunk(cp.x, cp.z);
            if (chunk == null) return;

            // Adım 1: Ham cevher taraması (tüm hedef bloklar)
            Map<Block, List<BlockPos>> rawOres = rawScanChunk(chunk, cp, world);

            // Adım 1b: ChunkOreCounter — ham sayıları kaydet (Katman 3 için gerekli)
            Map<Block, Integer> rawCounts = new HashMap<>();
            rawOres.forEach((b, positions) -> rawCounts.put(b, positions.size()));
            ChunkOreCounter.registerChunk(cp, rawCounts);

            // Adım 2: AntiXrayFilter — katmanlı skor filtresi (YENİ)
            // Komşu analizi + Y seviyesi + ışık + izolasyon + paket doğrulama
            Map<Block, List<BlockPos>> filteredOres = AntiXrayFilter.filterAll(world, rawOres);

            // Adım 3: AntiXrayBypass — istatistiksel + damar analizi (mevcut)
            AntiXrayBypass.ChunkAnalysisResult axResult =
                AntiXrayBypass.analyzeChunk(world, chunk);

            // Adım 4: İki sistemin sonuçlarını birleştir
            // AntiXrayBypass realPositions varsa → onları kullan (daha hassas)
            // Yoksa AntiXrayFilter sonuçlarını kullan
            Map<Block, List<BlockPos>> realOres = new LinkedHashMap<>();

            if (!axResult.realPositions.isEmpty()) {
                // AntiXrayBypass istatistiksel analizi yaptı → sonuçlarına güven
                // Ama AntiXrayFilter ile çapraz kontrol yap
                for (BlockPos pos : axResult.realPositions) {
                    Block b = world.getBlockState(pos).getBlock();
                    if (!XrayConfig.isTargetBlock(b)) continue;

                    // AntiXrayFilter'dan da geçmeli
                    if (!AntiXrayFilter.isFakeBlock(world, pos, b)) {
                        realOres.computeIfAbsent(b, k -> new ArrayList<>()).add(pos);
                    }
                }
            } else {
                // AntiXrayBypass analiz yapmadı (anti-xray yok) → AntiXrayFilter sonuçları
                realOres.putAll(filteredOres);
            }

            // Adım 5: Damar verilerini oluştur
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
            // Sessizce devam et — oyunu çökertme
        } finally {
            inProgress.remove(key);
            queueSize.decrementAndGet();
        }
    }

    /**
     * Ham chunk taraması — chunk section'larından tüm hedef blokları toplar.
     * Anti-xray filtreleme uygulanmaz; bu adım sadece ham konumları döner.
     *
     * @param chunk  Taranacak WorldChunk
     * @param cp     Chunk pozisyonu
     * @param world  ClientWorld
     * @return Block türüne göre gruplandırılmış ham blok konumları
     */
    private static Map<Block, List<BlockPos>> rawScanChunk(WorldChunk chunk, ChunkPos cp,
                                                            ClientWorld world) {
        Map<Block, List<BlockPos>> out = new HashMap<>();
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
                        BlockPos pos = new BlockPos(baseX + lx, baseY + ly, baseZ + lz);
                        out.computeIfAbsent(b, k -> new ArrayList<>()).add(pos);
                    }
                }
            }
        }
        return out;
    }

    // ── Sonuç Erişim API ──────────────────────────────────────────────────────

    /**
     * Belirtilen chunk'ın tarama sonucunu döner (cache'den).
     * Taranmamışsa null döner.
     *
     * @param cp Chunk pozisyonu
     * @return ScanResult veya null
     */
    public static ScanResult getResult(ChunkPos cp) {
        return scanCache.get(cp.toLong());
    }

    /**
     * Oyuncu etrafındaki tüm taranmış cevher damarlarını toplar.
     *
     * @param player      Oyuncu konumu
     * @param radiusChunks Chunk cinsinden yarıçap
     * @return Mesafeye göre sıralanmış damar listesi
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
     * Toplam bulunan cevher sayısı (görünür range).
     *
     * @param player      Oyuncu konumu
     * @param radiusChunks Chunk cinsinden yarıçap
     * @return Block türüne göre toplam sayı haritası
     */
    public static Map<Block, Integer> getTotalCounts(BlockPos player, int radiusChunks) {
        Map<Block, Integer> total = new LinkedHashMap<>();
        ChunkPos center = new ChunkPos(player);

        for (int cx = -radiusChunks; cx <= radiusChunks; cx++) {
            for (int cz = -radiusChunks; cz <= radiusChunks; cz++) {
                ScanResult r = scanCache.get(new ChunkPos(center.x + cx, center.z + cz).toLong());
                if (r == null) continue;
                r.counts.forEach((b, c) -> total.merge(b, c, Integer::sum));
            }
        }
        return total;
    }

    // ── Cache Yönetimi ────────────────────────────────────────────────────────

    /**
     * Chunk boşaltıldığında tüm ilgili cache'leri temizler.
     *
     * @param cp Boşaltılan chunk pozisyonu
     */
    public static void onChunkUnloaded(ChunkPos cp) {
        long key = cp.toLong();
        scanCache.remove(key);
        AntiXrayBypass.invalidateChunk(cp);
        OreVeinAnalyzer.invalidate(key);
        AntiXrayFilter.invalidateChunk(cp.x, cp.z); // AntiXrayFilter skor cache'i
        ChunkOreCounter.clearChunk(cp);              // ChunkOreCounter sayaçları
    }

    /**
     * Yeni chunk yüklendiğinde scan cache'ini geçersiz kıl.
     *
     * @param cp Yüklenen chunk pozisyonu
     */
    public static void onChunkLoaded(ChunkPos cp) {
        long key = cp.toLong();
        scanCache.remove(key);
    }

    /**
     * Tüm cache'leri temizler (Xray kapatılınca veya dünya değişince).
     */
    public static void clearAll() {
        scanCache.clear();
        inProgress.clear();
        AntiXrayBypass.clearCache();
        OreVeinAnalyzer.clearAll();
        AntiXrayFilter.clearAll(); // AntiXrayFilter + BlockVerificationCache
        ChunkOreCounter.clearAll(); // ChunkOreCounter sayaçları
    }

    // ── İstatistik ───────────────────────────────────────────────────────────

    public static int getTotalScanned()  { return totalScanned.get(); }
    public static int getQueueSize()     { return queueSize.get(); }
    public static int getCacheSize()     { return scanCache.size(); }
    public static boolean isScanning()  { return !inProgress.isEmpty(); }

    /** AntiXrayFilter cache boyutunu döner (debug). */
    public static int getFilterCacheSize() { return AntiXrayFilter.getCacheSize(); }

    /** BlockVerificationCache istatistikleri (debug). */
    public static String getVerificationStats() {
        return "verified=" + BlockVerificationCache.getVerifiedCount()
             + " unverified=" + BlockVerificationCache.getUnverifiedCount();
    }
}
