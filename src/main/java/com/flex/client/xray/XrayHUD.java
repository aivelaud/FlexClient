package com.flex.client.xray;

import com.flex.client.module.ModuleManager;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * FlexClient Ultra Xray — HUD Overlay Sistemi
 *
 * Ekranda gösterilenler:
 *  1. Ore Sayım Paneli   — Her cevher türü için toplam adet + mesafe
 *  2. Anti-Xray Durumu   — Bypass aktif mi, hangi mode
 *  3. En Yakın Cevherler — Top 5 en yakın damar listesi
 *  4. Tarama Durumu      — Kaç chunk tarandı, sıra
 *  5. Y-Level Göstergesi — Cevher için optimal Y
 */
public class XrayHUD {

    // Renk sabitleri
    private static final int BG          = 0xBB000015;
    private static final int BORDER      = 0xFF00FFAA;
    private static final int TEXT_WHITE  = 0xFFFFFFFF;
    private static final int TEXT_GRAY   = 0xFFAAAAAA;
    private static final int TEXT_GREEN  = 0xFF00FF88;
    private static final int TEXT_RED    = 0xFFFF4444;
    private static final int TEXT_YELLOW = 0xFFFFDD00;
    private static final int TEXT_CYAN   = 0xFF00FFFF;

    // HUD pozisyonu
    private static final int HUD_X      = 4;
    private static final int HUD_Y_BASE = 30;

    // Yenileme limiti (her N tick bir kez hesapla)
    private static int ticksSinceUpdate = 0;
    private static final int UPDATE_INTERVAL = 10;

    // Önbelleğe alınmış veriler
    private static List<OreVeinAnalyzer.VeinData> cachedVeins = new ArrayList<>();
    private static Map<Block, Integer> cachedCounts = new LinkedHashMap<>();
    private static OreVeinAnalyzer.OreStats cachedStats = null;

    public static void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        if (!ModuleManager.isEnabled("Xray")) return;

        // V5: Her tick path tracker güncellenir (mesafe filtresi için kritik)
        PlayerPathTracker.tick(mc.player);

        ticksSinceUpdate++;
        if (ticksSinceUpdate < UPDATE_INTERVAL) return;
        ticksSinceUpdate = 0;

        BlockPos playerPos = mc.player.getBlockPos();

        // Chunk taramayı tetikle
        ChunkOreScanner.triggerScanAround(playerPos);

        // Verileri güncelle
        cachedVeins  = ChunkOreScanner.getAllVisibleVeins(playerPos, 6);
        cachedCounts = ChunkOreScanner.getTotalCounts(playerPos, 6);
        cachedStats  = OreVeinAnalyzer.computeStats(cachedVeins, playerPos);
    }

    /**
     * HUD çizer — HudRenderCallback içinde çağrılır
     */
    public static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        if (!ModuleManager.isEnabled("Xray")) return;

        TextRenderer tr = mc.textRenderer;
        int y = HUD_Y_BASE;

        // ── Panel 1: Xray başlık ────────────────────────────────
        y = drawTitle(ctx, tr, y);
        y += 2;

        // ── Panel 2: Anti-Xray Bypass durumu ────────────────────
        y = drawBypassStatus(ctx, tr, y);
        y += 3;

        // ── Panel 3: Ore sayımları ───────────────────────────────
        y = drawOreCounts(ctx, tr, y, mc.player.getBlockPos());
        y += 3;

        // ── Panel 4: En yakın cevherler ──────────────────────────
        y = drawNearestOres(ctx, tr, y, mc.player.getBlockPos());
        y += 3;

        // ── Panel 5: Tarama durumu ───────────────────────────────
        drawScanStatus(ctx, tr, y);
    }

    // ─── Panel Çiziciler ─────────────────────────────────────────

    private static int drawTitle(DrawContext ctx, TextRenderer tr, int y) {
        String title = "\u00a7a\u00a7lFLEX\u00a7f\u00a7lXRAY \u00a772.0";
        int w = tr.getWidth("\u00a7lFLEXXRAY 2.0") + 8;
        drawPanel(ctx, HUD_X, y-1, w, 12);
        ctx.drawTextWithShadow(tr, title, HUD_X + 3, y + 1, TEXT_WHITE);
        return y + 12;
    }

    private static int drawBypassStatus(DrawContext ctx, TextRenderer tr, int y) {
        boolean active = AntiXrayBypass.isBypassActive();
        int mode       = AntiXrayBypass.getDetectedMode();
        int fakes      = AntiXrayBypass.getTotalFakesRemoved();
        int axChunks   = AntiXrayBypass.getChunksWithAntiXray();

        int panelH = active ? 44 : 22;
        drawPanel(ctx, HUD_X, y, 160, panelH);

        int ly = y + 3;
        String statusLine = active
            ? "\u00a7c\u2716 AntiXray Tespit: Mode " + mode
            : "\u00a7a\u2714 AntiXray Yok (temiz sunucu)";
        ctx.drawTextWithShadow(tr, statusLine, HUD_X + 3, ly, TEXT_WHITE);
        ly += 11;

        if (active) {
            String fakeLine = "\u00a77Sahte blok: \u00a7c" + fakes + " \u00a77/ Chunk: \u00a7e" + axChunks;
            ctx.drawTextWithShadow(tr, fakeLine, HUD_X + 3, ly, TEXT_WHITE);
            ly += 11;

            String modeName = mode == 1 ? "Hava-gizleme (Mode 1)" :
                              mode == 2 ? "Sahte-serpme (Mode 2)" : "Bilinmiyor";
            ctx.drawTextWithShadow(tr, "\u00a77Mod: \u00a7e" + modeName, HUD_X + 3, ly, TEXT_WHITE);
            ly += 11;
        }
        return y + panelH + 1;
    }

    private static int drawOreCounts(DrawContext ctx, TextRenderer tr, int y, BlockPos player) {
        if (cachedCounts.isEmpty()) {
            drawPanel(ctx, HUD_X, y, 160, 22);
            ctx.drawTextWithShadow(tr, "\u00a77Cevher taraniyor...", HUD_X + 4, y + 7, TEXT_GRAY);
            return y + 23;
        }

        // Sadece etkin ve bulunan cevherleri göster
        List<Map.Entry<Block, Integer>> entries = new ArrayList<>();
        for (Map.Entry<Block, Integer> e : cachedCounts.entrySet()) {
            if (e.getValue() > 0 && XrayConfig.isTargetBlock(e.getKey())) {
                entries.add(e);
            }
        }
        // Nadir sıralaması
        entries.sort((a, b) -> {
            XrayConfig.OreEntry ea = XrayConfig.getEntry(a.getKey());
            XrayConfig.OreEntry eb = XrayConfig.getEntry(b.getKey());
            int ra = ea != null ? ea.rarity : 9;
            int rb = eb != null ? eb.rarity : 9;
            return Integer.compare(ra, rb);
        });

        int panelH = Math.max(22, 12 + entries.size() * 11);
        drawPanel(ctx, HUD_X, y, 185, panelH);

        int ly = y + 3;
        ctx.drawTextWithShadow(tr, "\u00a7f\u00a7lYakinda Cevherler \u00a77(r=6 chunk)", HUD_X + 3, ly, TEXT_CYAN);
        ly += 11;

        for (Map.Entry<Block, Integer> e : entries) {
            Block b = e.getKey();
            XrayConfig.OreEntry oe = XrayConfig.getEntry(b);
            if (oe == null) continue;

            int color = oe.color | 0xFF000000;
            String name = String.format("%-12s", oe.name);
            String count = String.valueOf(e.getValue());

            // Mesafe bilgisi
            String dist = "";
            if (cachedStats != null && cachedStats.nearest.containsKey(b)) {
                BlockPos near = cachedStats.nearest.get(b);
                int d = (int) Math.sqrt(player.getSquaredDistance(near));
                dist = " \u00a77(" + d + "m)";
            }

            String line = "\u00a7f" + name + " \u00a7e" + count + dist;
            ctx.drawTextWithShadow(tr, line, HUD_X + 3, ly, color);
            ly += 10;
        }

        return y + panelH + 1;
    }

    private static int drawNearestOres(DrawContext ctx, TextRenderer tr, int y, BlockPos player) {
        List<OreVeinAnalyzer.VeinData> top5 = new ArrayList<>();
        for (OreVeinAnalyzer.VeinData v : cachedVeins) {
            if (top5.size() >= 5) break;
            if (!v.likelyReal) continue;
            top5.add(v);
        }

        if (top5.isEmpty()) return y;

        int panelH = 12 + top5.size() * 11;
        drawPanel(ctx, HUD_X, y, 200, panelH);

        int ly = y + 3;
        ctx.drawTextWithShadow(tr, "\u00a7f\u00a7lEn Yakin 5 Damar", HUD_X + 3, ly, TEXT_YELLOW);
        ly += 11;

        for (int i = 0; i < top5.size(); i++) {
            OreVeinAnalyzer.VeinData v = top5.get(i);
            int dist = (int) v.distanceTo(player.getX(), player.getY(), player.getZ());
            int dy   = v.center.getY() - player.getY();
            String dir = dy > 0 ? "↑" + dy : "↓" + Math.abs(dy);
            String naturalStr = v.likelyReal ? "\u00a7a\u25cf" : "\u00a7c\u25cf";
            String line = naturalStr + " \u00a7f" + v.getDisplayName()
                + " \u00a77x" + v.size
                + " \u00a7e" + dist + "m " + dir
                + " \u00a77[" + v.center.getX() + "," + v.center.getY() + "," + v.center.getZ() + "]";
            ctx.drawTextWithShadow(tr, line, HUD_X + 3, ly, 0xFFFFFFFF);
            ly += 10;
        }
        return y + panelH + 1;
    }

    private static int drawScanStatus(DrawContext ctx, TextRenderer tr, int y) {
        int scanned = ChunkOreScanner.getTotalScanned();
        int queue   = ChunkOreScanner.getQueueSize();
        int cached  = ChunkOreScanner.getCacheSize();
        boolean scanning = ChunkOreScanner.isScanning();

        drawPanel(ctx, HUD_X, y, 180, 22);
        String spin = scanning ? "\u00a7e\u25D4 " : "\u00a7a\u25C9 ";
        String line = spin + "\u00a77Tara: \u00a7f" + scanned
            + " \u00a77| Sira: \u00a7e" + queue
            + " \u00a77| Cache: \u00a7f" + cached;
        ctx.drawTextWithShadow(tr, line, HUD_X + 3, y + 7, TEXT_WHITE);
        return y + 23;
    }

    // ─── Ortak Panel Çizici ──────────────────────────────────────

    private static void drawPanel(DrawContext ctx, int x, int y, int w, int h) {
        ctx.fill(x, y, x + w, y + h, BG);
        // Sol border
        ctx.fill(x, y, x + 2, y + h, BORDER);
        // Üst border (ince)
        ctx.fill(x + 2, y, x + w, y + 1, BORDER & 0x55FFFFFF);
    }

    // ─── Y-Level Göstergesi ──────────────────────────────────────

    /**
     * Ekranın sağ tarafına Y-level ve cevher optimal bilgisi çizer
     */
    public static void renderYLevelGuide(DrawContext ctx, TextRenderer tr, int screenW, int playerY) {
        if (!ModuleManager.isEnabled("Xray")) return;

        // Şu anki Y için hangi cevherler optimal?
        List<String> optimal = new ArrayList<>();
        for (XrayConfig.OreEntry e : XrayConfig.getAllEntries()) {
            if (!e.enabled) continue;
            if (Math.abs(playerY - e.bestY) <= 8) {
                optimal.add("\u00a7f" + e.name + " \u00a77(best:" + e.bestY + ")");
            }
        }

        int panelW = 150, panelH = 14 + optimal.size() * 10;
        int px = screenW - panelW - 4;
        int py = 4;

        ctx.fill(px, py, px + panelW, py + panelH, BG);
        ctx.fill(px + panelW - 2, py, px + panelW, py + panelH, BORDER);

        String yLine = "\u00a77Y: \u00a7e" + playerY + " \u00a77| Optimal:";
        ctx.drawTextWithShadow(tr, yLine, px + 3, py + 3, TEXT_WHITE);
        int ly = py + 13;
        for (String s : optimal) {
            ctx.drawTextWithShadow(tr, s, px + 3, ly, TEXT_WHITE);
            ly += 10;
        }
    }
}
