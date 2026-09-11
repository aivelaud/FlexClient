package com.flex.client.gui;

import com.flex.client.module.Module;
import com.flex.client.module.ModuleManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import java.util.*;

/**
 * FlexClient 3.2 — Universal ClickGUI
 * Sol: Kategori + Modül listesi  |  Sağ: Tam ayar paneli (bool/int/float/string otomatik)
 * Sol tık = toggle  |  Sağ tık (SEC/PojavLauncher) = detay paneli
 */
public class ClickGui extends Screen {

    // ── Renk Paleti ──────────────────────────────────────────────
    private static final int BG_DARK     = 0xF0050510;
    private static final int BG_PANEL    = 0xF00A0A1E;
    private static final int BG_ITEM     = 0xFF0D0D22;
    private static final int BG_ITEM_HOV = 0xFF16163A;
    private static final int ACCENT_CYAN = 0xFF00FFCC;
    private static final int ACCENT_PURP = 0xFF9933FF;
    private static final int ACCENT_RED  = 0xFFFF4466;
    private static final int ACCENT_BLUE = 0xFF3399FF;
    private static final int ACCENT_GRN  = 0xFF33FF99;
    private static final int ACCENT_ORG  = 0xFFFF9922;
    private static final int ACCENT_YELL = 0xFFFFDD00;
    private static final int TEXT_WHITE  = 0xFFFFFFFF;
    private static final int TEXT_GRAY   = 0xFF8888AA;
    private static final int TEXT_DIM    = 0xFF555577;
    private static final int DIVIDER     = 0xFF1A1A3A;

    private static final int[] CAT_COLORS = { ACCENT_RED, ACCENT_BLUE, ACCENT_GRN, ACCENT_ORG, ACCENT_CYAN };
    private static final String[] CATEGORIES = { "Combat", "Movement", "Render", "Player", "World" };
    private static final String[] CAT_ICONS  = { "\u2694", "\u26A1", "\u25A6", "\u2764", "\u25C9" };

    // Mode seçenekleri — her modül için
    private static final Map<String, String[]> MODE_OPTIONS = new HashMap<>();
    static {
        MODE_OPTIONS.put("KillAura",    new String[]{"Single","Multi","Switch"});
        MODE_OPTIONS.put("CrystalAura", new String[]{"Smart","Suicide","Safe"});
        MODE_OPTIONS.put("Fly",         new String[]{"Vanilla","Packet","Creative"});
        MODE_OPTIONS.put("Speed",       new String[]{"Strafe","Ground","YPort"});
        MODE_OPTIONS.put("Criticals",   new String[]{"Jump","Packet","Always"});
        MODE_OPTIONS.put("Nuker",       new String[]{"Sphere","Flat","ID"});
        MODE_OPTIONS.put("Jesus",       new String[]{"Solid","Bounce","Sneak"});
        MODE_OPTIONS.put("ESP",         new String[]{"Box","Outline","Corner"});
        MODE_OPTIONS.put("Tracers",     new String[]{"Eyes","Crosshair"});
        MODE_OPTIONS.put("WorldCopy",   new String[]{"Full","BlocksOnly","EntitiesOnly"});
        MODE_OPTIONS.put("LobbyCopy",   new String[]{"Area","Full","Structure"});
        MODE_OPTIONS.put("Mix",         new String[]{"Both","WorldOnly","LobbyOnly"});
    }

    /**
     * Xray modülü için özel cevher seçici verileri.
     * {settingKey, displayName, 0xRRGGBB renk}
     */
    private static final Object[][] XRAY_ORES = {
        {"showDiamond",       "Elmas",        0x55FFFF},
        {"showGold",          "Altin",        0xFFD700},
        {"showIron",          "Demir",        0xBBBBBB},
        {"showAncientDebris", "Eski Kalinti", 0xCC5500},
        {"showEmerald",       "Zumrut",       0x00CC44},
        {"showChests",        "Sandik",       0xAA7733},
        {"showSpawner",       "Spawner",      0x8888FF},
        {"showCoal",          "Komur",        0x555555},
        {"showLapis",         "Lapis",        0x2266CC},
        {"showRedstone",      "Redstone",     0xFF3333},
        {"showCopper",        "Bakir",        0xCC7722},
    };

    private static final int XRAY_TILE_H = 20;

    // ── Layout ───────────────────────────────────────────────────
    private static final int LEFT_W = 200;
    private static final int RIGHT_W = 232;
    private static final int TAB_H  = 36;
    private static final int ITEM_H = 28;
    private static final int SRCH_H = 26;
    private static final int PAD    = 8;

    // ── State ────────────────────────────────────────────────────
    private int    selectedCat   = 0;
    private Module selectedMod   = null;
    private String searchQuery   = "";
    private boolean searchFocused = false;
    private int    listScrollY   = 0;
    private int    maxScrollY    = 0;
    private int    listAreaY     = 0;
    private int    listAreaBotY  = 0;
    private float  openAnim      = 0f;

    public ClickGui() { super(Text.literal("FlexClient")); }

    // ═════════════════════════════════════════════════════════════
    //  RENDER
    // ═════════════════════════════════════════════════════════════
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        openAnim = Math.min(1f, openAnim + delta * 0.15f);
        float anim = easeOut(openAnim);
        int alpha  = (int)(anim * 255);

        int sW = this.width, sH = this.height;
        int guiW = LEFT_W + RIGHT_W + 4;
        int guiH = sH - 20;
        int guiX = (sW - guiW) / 2;
        int guiY = 10 + (int)((1 - anim) * 30);

        ctx.fill(0, 0, sW, sH, applyAlpha(0xFF000022, alpha / 2));
        ctx.fill(guiX, guiY, guiX + guiW, guiY + guiH, applyAlpha(BG_DARK, alpha));

        // ── Sol Panel ─────────────────────────────────────────────
        int lx = guiX;
        ctx.fill(lx, guiY, lx + LEFT_W, guiY + guiH, applyAlpha(BG_PANEL, alpha));
        ctx.fill(lx, guiY, lx + 2, guiY + guiH, applyAlpha(ACCENT_CYAN, alpha));

        ctx.fill(lx + 2, guiY, lx + LEFT_W, guiY + 30, applyAlpha(BG_DARK, alpha));
        ctx.drawTextWithShadow(textRenderer,
            "\u00a7b\u00a7lFlex\u00a7f\u00a7lClient \u00a783.2",
            lx + PAD, guiY + 11, applyAlpha(TEXT_WHITE, alpha));
        ctx.fill(lx + 2, guiY + 29, lx + LEFT_W, guiY + 30,
            applyAlpha(ACCENT_CYAN & 0x00FFFFFF | 0x55000000, alpha));

        int tabY = guiY + 31;
        int tabW = (LEFT_W - 2) / CATEGORIES.length;
        for (int i = 0; i < CATEGORIES.length; i++) {
            int tx  = lx + 2 + i * tabW;
            boolean sel = (i == selectedCat);
            int tc  = CAT_COLORS[i];
            ctx.fill(tx, tabY, tx + tabW - 1, tabY + TAB_H,
                applyAlpha(sel ? tc & 0x00FFFFFF | 0x22000000 : BG_ITEM, alpha));
            if (sel)
                ctx.fill(tx, tabY + TAB_H - 2, tx + tabW - 1, tabY + TAB_H, applyAlpha(tc, alpha));
            ctx.drawTextWithShadow(textRenderer, CAT_ICONS[i],
                tx + tabW / 2 - 3, tabY + 6, applyAlpha(sel ? tc : TEXT_GRAY, alpha));
            String short4 = CATEGORIES[i].substring(0, Math.min(4, CATEGORIES[i].length()));
            ctx.drawTextWithShadow(textRenderer, "\u00a77" + short4,
                tx + tabW / 2 - textRenderer.getWidth(short4) / 2, tabY + 18,
                applyAlpha(sel ? TEXT_WHITE : TEXT_DIM, alpha));
        }

        int searchY = tabY + TAB_H + 2;
        ctx.fill(lx + 2, searchY, lx + LEFT_W, searchY + SRCH_H, applyAlpha(BG_ITEM, alpha));
        ctx.fill(lx + 2, searchY, lx + 4, searchY + SRCH_H,
            applyAlpha(searchFocused ? ACCENT_CYAN : DIVIDER, alpha));
        String srch = searchQuery.isEmpty() ? "\u00a77Ara..." : "\u00a7f" + searchQuery + (searchFocused ? "\u00a7e|" : "");
        ctx.drawTextWithShadow(textRenderer, srch, lx + PAD, searchY + 8, applyAlpha(TEXT_WHITE, alpha));

        int listY    = searchY + SRCH_H + 2;
        int listBotY = guiY + guiH - 2;
        int listH    = listBotY - listY;
        ctx.fill(lx + 2, listY, lx + LEFT_W, listBotY,
            applyAlpha(BG_ITEM & 0x00FFFFFF | 0x77000000, alpha));

        List<Module> mods = getFilteredModules();
        maxScrollY  = Math.max(0, mods.size() * (ITEM_H + 1) - listH);
        listScrollY  = Math.min(listScrollY, maxScrollY);
        listAreaY    = listY;
        listAreaBotY = listBotY;
        int catC = CAT_COLORS[selectedCat];
        int iy   = listY - listScrollY;

        for (Module mod : mods) {
            if (iy + ITEM_H < listY) { iy += ITEM_H + 1; continue; }
            if (iy > listBotY) break;
            boolean on  = mod.isEnabled();
            boolean sel = (mod == selectedMod);
            boolean hov = mx >= lx && mx <= lx + LEFT_W && my >= iy && my <= iy + ITEM_H;
            int bg = sel ? applyAlpha(catC & 0x00FFFFFF | 0x33000000, alpha)
                  : hov  ? applyAlpha(BG_ITEM_HOV, alpha)
                  :        applyAlpha(BG_ITEM, alpha);
            ctx.fill(lx + 2, iy, lx + LEFT_W, iy + ITEM_H, bg);
            ctx.fill(lx + 2, iy, lx + 4, iy + ITEM_H, applyAlpha(on ? catC : DIVIDER, alpha));
            ctx.fill(lx + 4, iy + ITEM_H - 1, lx + LEFT_W, iy + ITEM_H, applyAlpha(DIVIDER, alpha));
            ctx.drawTextWithShadow(textRenderer, mod.getName(),
                lx + PAD + 2, iy + ITEM_H / 2 - 4,
                applyAlpha(on ? TEXT_WHITE : TEXT_GRAY, alpha));
            if (on)
                ctx.fill(lx + LEFT_W - 10, iy + ITEM_H / 2 - 3,
                         lx + LEFT_W - 4,  iy + ITEM_H / 2 + 3, applyAlpha(catC, alpha));
            ctx.drawTextWithShadow(textRenderer, sel ? "\u00a7f>" : "\u00a77>",
                lx + LEFT_W - (on ? 20 : 12), iy + ITEM_H / 2 - 4, applyAlpha(TEXT_WHITE, alpha));
            iy += ITEM_H + 1;
        }

        if (maxScrollY > 0 && mods.size() > 0) {
            int total = mods.size() * (ITEM_H + 1);
            float ratio = (float) listScrollY / maxScrollY;
            int sbH = Math.max(16, listH * listH / total);
            int sbY = listY + (int)(ratio * (listH - sbH));
            ctx.fill(lx + LEFT_W - 3, listY, lx + LEFT_W - 1, listBotY, applyAlpha(DIVIDER, alpha));
            ctx.fill(lx + LEFT_W - 3, sbY, lx + LEFT_W - 1, sbY + sbH, applyAlpha(ACCENT_CYAN, alpha));
        }

        // ── Sağ Panel ─────────────────────────────────────────────
        int rx = lx + LEFT_W + 4;
        int ry = guiY;
        ctx.fill(rx, ry, rx + RIGHT_W, ry + guiH, applyAlpha(BG_PANEL, alpha));
        ctx.fill(rx + RIGHT_W - 2, ry, rx + RIGHT_W, ry + guiH, applyAlpha(ACCENT_PURP, alpha));

        if (selectedMod != null)
            renderDetailPanel(ctx, rx, ry, guiH, selectedMod, alpha, mx, my);
        else
            renderEmptyPanel(ctx, rx, ry, guiH, alpha);

        ctx.fill(0, sH - 14, sW, sH, applyAlpha(BG_DARK, alpha));
        ctx.drawTextWithShadow(textRenderer,
            "\u00a77[L.Tık]\u00a7f Toggle  \u00a77[R.Tık/SEC]\u00a7f Detay  \u00a77[Scroll]\u00a7f Kaydır  \u00a77[ESC]\u00a7f Kapat",
            4, sH - 10, applyAlpha(TEXT_DIM, alpha));

        super.render(ctx, mx, my, delta);
    }

    // ═════════════════════════════════════════════════════════════
    //  DETAY PANELİ
    // ═════════════════════════════════════════════════════════════
    private void renderDetailPanel(DrawContext ctx, int rx, int ry, int guiH,
                                   Module mod, int alpha, int mx, int my) {
        int ci   = getCatIndex(mod.getCategory());
        int catC = ci >= 0 ? CAT_COLORS[ci] : ACCENT_CYAN;
        int pad  = PAD + 4;

        // Başlık
        ctx.fill(rx, ry, rx + RIGHT_W - 2, ry + 52, applyAlpha(BG_DARK, alpha));
        ctx.fill(rx, ry + 52, rx + RIGHT_W - 2, ry + 53, applyAlpha(catC, alpha));
        ctx.drawTextWithShadow(textRenderer, "\u00a7l" + mod.getName(),
            rx + pad, ry + 8, applyAlpha(catC, alpha));
        boolean on = mod.isEnabled();
        ctx.drawTextWithShadow(textRenderer,
            "\u00a77" + mod.getCategory() + "  " + (on ? "\u00a7a\u25CF AÇIK" : "\u00a7c\u25CF KAPALI"),
            rx + pad, ry + 20, applyAlpha(TEXT_GRAY, alpha));

        // Toggle butonu
        int bx = rx + RIGHT_W - 62, by = ry + 10;
        ctx.fill(bx, by, bx + 54, by + 22,
            applyAlpha(on ? catC & 0x00FFFFFF | 0x44000000 : 0xFF1A0011, alpha));
        ctx.fill(bx, by, bx + 2, by + 22, applyAlpha(on ? catC : ACCENT_RED, alpha));
        String lbl = on ? "\u00a7a AÇIK" : "\u00a7c KAPALI";
        ctx.drawTextWithShadow(textRenderer, lbl,
            bx + 27 - textRenderer.getWidth(on ? " AÇIK" : " KAPALI") / 2, by + 7, TEXT_WHITE);

        // Açıklama
        int py = ry + 58;
        ctx.drawTextWithShadow(textRenderer, "\u00a77Açıklama", rx + pad, py, applyAlpha(TEXT_DIM, alpha));
        py += 12;
        String desc = mod.getDescription().isEmpty() ? "Açıklama yok." : mod.getDescription();
        for (String line : wrapText(desc, RIGHT_W - pad * 2 - 10)) {
            ctx.drawTextWithShadow(textRenderer, "\u00a7f" + line, rx + pad, py, applyAlpha(TEXT_GRAY, alpha));
            py += 10;
        }
        py += 4;
        divider(ctx, rx, rx + RIGHT_W - 2, py, alpha); py += 8;

        // ── MODE seçici ───────────────────────────────────────────
        String[] modes = MODE_OPTIONS.get(mod.getName());
        if (modes != null) {
            sectionLabel(ctx, rx, py, pad, alpha, "\u00a7eMode Seç:", ACCENT_YELL);
            py += 13;
            String cur = mod.getMode();
            int bx2 = rx + pad;
            for (String mode : modes) {
                boolean selM = mode.equals(cur);
                int mw = textRenderer.getWidth(mode) + 16;
                ctx.fill(bx2, py, bx2 + mw, py + 16,
                    applyAlpha(selM ? catC & 0x00FFFFFF | 0x55000000 : BG_ITEM, alpha));
                if (selM)
                    ctx.fill(bx2, py + 14, bx2 + mw, py + 16, applyAlpha(catC, alpha));
                ctx.drawTextWithShadow(textRenderer,
                    selM ? "\u00a7l" + mode : "\u00a77" + mode,
                    bx2 + 8, py + 4, applyAlpha(selM ? catC : TEXT_GRAY, alpha));
                bx2 += mw + 3;
            }
            py += 22;
            divider(ctx, rx, rx + RIGHT_W - 2, py, alpha); py += 8;
        }

        // ── XRAY CEVHER SEÇİCİ (özel render) ────────────────────
        if ("Xray".equals(mod.getName())) {
            py = renderXrayOreSelector(ctx, rx, py, pad, alpha, mod, mx, my);
        } else {
            // ── BOOL Ayarlar (diğer modüller) ─────────────────────
            Map<String, Boolean> bools = mod.getBoolSettings();
            if (!bools.isEmpty()) {
                sectionLabel(ctx, rx, py, pad, alpha, "\u00a7fAnahtarlar", TEXT_DIM);
                py += 13;
                List<String> keys = new ArrayList<>(bools.keySet());
                Collections.sort(keys);
                for (String key : keys) {
                    boolean val = mod.getSetting(key);
                    boolean hov = mx >= rx + pad && mx <= rx + RIGHT_W - pad && my >= py && my <= py + 16;
                    ctx.fill(rx + pad, py, rx + RIGHT_W - pad, py + 16,
                        applyAlpha(hov ? BG_ITEM_HOV : BG_ITEM, alpha));
                    ctx.fill(rx + pad + 2, py + 3, rx + pad + 12, py + 13,
                        applyAlpha(val ? catC & 0x00FFFFFF | 0x88000000 : DIVIDER, alpha));
                    if (val)
                        ctx.fill(rx + pad + 4, py + 5, rx + pad + 10, py + 11, applyAlpha(catC, alpha));
                    ctx.drawTextWithShadow(textRenderer, "\u00a7f" + camelToNice(key),
                        rx + pad + 16, py + 4, applyAlpha(val ? TEXT_WHITE : TEXT_GRAY, alpha));
                    String vs = val ? "\u00a7aAçık" : "\u00a7cKapalı";
                    ctx.drawTextWithShadow(textRenderer, vs,
                        rx + RIGHT_W - pad - textRenderer.getWidth(val ? "Açık" : "Kapalı") - 2, py + 4,
                        applyAlpha(TEXT_WHITE, alpha));
                    py += 18;
                }
            }
        }

        // ── INT Ayarlar ───────────────────────────────────────────
        Map<String, Integer> ints = mod.getIntSettings();
        if (!ints.isEmpty()) {
            py += 2; divider(ctx, rx, rx + RIGHT_W - 2, py, alpha); py += 8;
            sectionLabel(ctx, rx, py, pad, alpha, "\u00a7fSayılar", TEXT_DIM);
            py += 13;
            for (Map.Entry<String, Integer> e : ints.entrySet()) {
                ctx.fill(rx + pad, py, rx + RIGHT_W - pad, py + 16, applyAlpha(BG_ITEM, alpha));
                ctx.drawTextWithShadow(textRenderer, "\u00a77" + camelToNice(e.getKey()),
                    rx + pad + 4, py + 4, applyAlpha(TEXT_GRAY, alpha));
                int vx = rx + RIGHT_W - pad - 52;
                ctx.fill(vx, py + 2, vx + 14, py + 14, applyAlpha(0xFF1A0A0A, alpha));
                ctx.fill(vx + 18, py + 2, vx + 36, py + 14, applyAlpha(0xFF0A1A0A, alpha));
                ctx.drawTextWithShadow(textRenderer, "\u00a7c-", vx + 4, py + 4, applyAlpha(ACCENT_RED, alpha));
                ctx.drawTextWithShadow(textRenderer, "\u00a7e" + e.getValue(),
                    rx + RIGHT_W - pad - 60, py + 4, applyAlpha(ACCENT_YELL, alpha));
                ctx.drawTextWithShadow(textRenderer, "\u00a7a+", vx + 22, py + 4, applyAlpha(ACCENT_GRN, alpha));
                py += 18;
            }
        }

        // ── FLOAT Ayarlar ─────────────────────────────────────────
        Map<String, Float> floats = mod.getFloatSettings();
        if (!floats.isEmpty()) {
            py += 2; divider(ctx, rx, rx + RIGHT_W - 2, py, alpha); py += 8;
            sectionLabel(ctx, rx, py, pad, alpha, "\u00a7fDeğerler", TEXT_DIM);
            py += 13;
            for (Map.Entry<String, Float> e : floats.entrySet()) {
                ctx.fill(rx + pad, py, rx + RIGHT_W - pad, py + 16, applyAlpha(BG_ITEM, alpha));
                ctx.drawTextWithShadow(textRenderer, "\u00a77" + camelToNice(e.getKey()),
                    rx + pad + 4, py + 4, applyAlpha(TEXT_GRAY, alpha));
                int vx = rx + RIGHT_W - pad - 58;
                ctx.fill(vx, py + 2, vx + 14, py + 14, applyAlpha(0xFF1A0A0A, alpha));
                ctx.fill(vx + 18, py + 2, vx + 36, py + 14, applyAlpha(0xFF0A1A0A, alpha));
                ctx.drawTextWithShadow(textRenderer, "\u00a7c-", vx + 4, py + 4, applyAlpha(ACCENT_RED, alpha));
                ctx.drawTextWithShadow(textRenderer, "\u00a7b" + String.format("%.2f", e.getValue()),
                    rx + RIGHT_W - pad - 68, py + 4, applyAlpha(ACCENT_BLUE, alpha));
                ctx.drawTextWithShadow(textRenderer, "\u00a7a+", vx + 22, py + 4, applyAlpha(ACCENT_GRN, alpha));
                py += 18;
            }
        }

        // PojavLauncher SEC bandı
        py += 4; divider(ctx, rx, rx + RIGHT_W - 2, py, alpha); py += 6;
        ctx.fill(rx + pad - 2, py, rx + RIGHT_W - pad, py + 22, applyAlpha(0xFF08081E, alpha));
        ctx.fill(rx + pad - 2, py, rx + pad, py + 22, applyAlpha(ACCENT_PURP, alpha));
        ctx.drawTextWithShadow(textRenderer, "\u00a7d\u2605 PojavLauncher SEC Detayı",
            rx + pad + 2, py + 3, applyAlpha(ACCENT_PURP, alpha));
        ctx.drawTextWithShadow(textRenderer, "\u00a77Sağ tık = detay  |  Sol tık = toggle",
            rx + pad + 2, py + 13, applyAlpha(TEXT_DIM, alpha));
    }

    /**
     * Xray modülü için renkli cevher seçici kartlar çizer.
     * Her kart: renk karesi + cevher adı + açık/kapalı durumu.
     * @return güncellenen py
     */
    private int renderXrayOreSelector(DrawContext ctx, int rx, int py, int pad, int alpha,
                                      Module mod, int mx, int my) {
        // Başlık
        sectionLabel(ctx, rx, py, pad, alpha, "\u00a7e\u25C6 Cevher Sec\u0327ic\u0327i", ACCENT_YELL);
        py += 2;
        ctx.drawTextWithShadow(textRenderer,
            "\u00a77Sarı kutu = AntiXray onaylı gerçek cevher",
            rx + pad, py + 10, applyAlpha(TEXT_DIM, alpha));
        py += 20;

        int tileW = (RIGHT_W - pad * 2 - 4) / 2;

        for (int i = 0; i < XRAY_ORES.length; i++) {
            String key  = (String)  XRAY_ORES[i][0];
            String name = (String)  XRAY_ORES[i][1];
            int    rgb  = (Integer) XRAY_ORES[i][2];
            int oreColor = 0xFF000000 | rgb;

            int col  = i % 2;
            int row  = i / 2;
            int tx   = rx + pad + col * (tileW + 4);
            int ty   = py + row * XRAY_TILE_H;

            boolean val = mod.getSetting(key);
            boolean hov = mx >= tx && mx <= tx + tileW && my >= ty && my <= ty + XRAY_TILE_H - 2;

            // Kart arka planı
            ctx.fill(tx, ty, tx + tileW, ty + XRAY_TILE_H - 2,
                applyAlpha(hov ? BG_ITEM_HOV : BG_ITEM, alpha));
            // Sol renk şeridi
            ctx.fill(tx, ty, tx + 2, ty + XRAY_TILE_H - 2,
                applyAlpha(val ? oreColor : DIVIDER, alpha));
            // Renk karesi
            ctx.fill(tx + 4, ty + 3, tx + 13, ty + XRAY_TILE_H - 5,
                applyAlpha(oreColor, val ? alpha : alpha / 3));
            // Cevher adı
            ctx.drawTextWithShadow(textRenderer,
                val ? "\u00a7f" + name : "\u00a78" + name,
                tx + 16, ty + 5, applyAlpha(val ? oreColor : TEXT_DIM, alpha));
            // Durum sembolü
            String sym = val ? "\u00a7a\u2714" : "\u00a7c\u2716";
            ctx.drawTextWithShadow(textRenderer, sym,
                tx + tileW - 10, ty + 5, applyAlpha(TEXT_WHITE, alpha));
        }

        // Çift sütunlu grid için toplam satır sayısını hesapla
        int rows = (XRAY_ORES.length + 1) / 2;
        py += rows * XRAY_TILE_H + 4;

        divider(ctx, rx, rx + RIGHT_W - 2, py, alpha);
        py += 8;
        return py;
    }

    private void renderEmptyPanel(DrawContext ctx, int rx, int ry, int guiH, int alpha) {
        int cx = rx + RIGHT_W / 2, cy = ry + guiH / 2;
        ctx.drawTextWithShadow(textRenderer, "\u00a7b\u00a7l{ FlexClient }",
            cx - textRenderer.getWidth("{ FlexClient }") / 2, cy - 40, applyAlpha(ACCENT_CYAN, alpha));
        ctx.drawTextWithShadow(textRenderer, "\u00a7fModül Seçilmedi",
            cx - textRenderer.getWidth("Modül Seçilmedi") / 2, cy - 8, applyAlpha(TEXT_WHITE, alpha));
        ctx.drawTextWithShadow(textRenderer, "\u00a77Detay için sağ tık (SEC)",
            cx - textRenderer.getWidth("Detay için sağ tık (SEC)") / 2, cy + 4, applyAlpha(TEXT_GRAY, alpha));
        ctx.drawTextWithShadow(textRenderer, "\u00a77Toggle için sol tık",
            cx - textRenderer.getWidth("Toggle için sol tık") / 2, cy + 16, applyAlpha(TEXT_GRAY, alpha));
    }

    // ═════════════════════════════════════════════════════════════
    //  MOUSE CLICKS
    // ═════════════════════════════════════════════════════════════
    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int sH = this.height, sW = this.width;
        int guiH = sH - 20, guiX = (sW - LEFT_W - RIGHT_W - 4) / 2, guiY = 10;
        int tabY = guiY + 31, tabW = (LEFT_W - 2) / CATEGORIES.length;
        int lx = guiX;

        // Kategori
        for (int i = 0; i < CATEGORIES.length; i++) {
            int tx = lx + 2 + i * tabW;
            if (mx >= tx && mx <= tx + tabW && my >= tabY && my <= tabY + TAB_H) {
                selectedCat = i; listScrollY = 0; selectedMod = null; return true;
            }
        }

        // Arama
        int searchY = tabY + TAB_H + 2;
        searchFocused = (mx >= lx + 2 && mx <= lx + LEFT_W && my >= searchY && my <= searchY + SRCH_H);

        // Modül listesi
        int listY = searchY + SRCH_H + 2;
        List<Module> mods = getFilteredModules();
        int iy = listY - listScrollY;
        for (Module m : mods) {
            if (mx >= lx + 2 && mx <= lx + LEFT_W && my >= iy && my <= iy + ITEM_H) {
                if (btn == 0) m.toggle();
                else if (btn == 1) selectedMod = (selectedMod == m) ? null : m;
                return true;
            }
            iy += ITEM_H + 1;
        }

        // Sağ panel
        int rx = lx + LEFT_W + 4, ry = guiY, pad = PAD + 4;
        if (selectedMod != null) {
            // Toggle butonu
            int bx = rx + RIGHT_W - 62, by = ry + 10;
            if (mx >= bx && mx <= bx + 54 && my >= by && my <= by + 22) {
                selectedMod.toggle(); return true;
            }
            // Mode seçici
            String[] modes = MODE_OPTIONS.get(selectedMod.getName());
            if (modes != null) {
                String desc2 = selectedMod.getDescription().isEmpty() ? "Açıklama yok." : selectedMod.getDescription();
                int py = ry + 58 + 12 + wrapText(desc2, RIGHT_W - pad * 2 - 10).size() * 10 + 4 + 8 + 13;
                int bx2 = rx + pad;
                for (String mode : modes) {
                    int mw = textRenderer.getWidth(mode) + 16;
                    if (mx >= bx2 && mx <= bx2 + mw && my >= py && my <= py + 16) {
                        selectedMod.setStringSetting("mode", mode); return true;
                    }
                    bx2 += mw + 3;
                }
            }
            // Detay tıklamaları
            handleDetailClicks(mx, my, rx, ry, pad, selectedMod);
        }
        return super.mouseClicked(mx, my, btn);
    }

    private void handleDetailClicks(double mx, double my, int rx, int ry, int pad, Module mod) {
        String desc = mod.getDescription().isEmpty() ? "Açıklama yok." : mod.getDescription();
        int descH = wrapText(desc, RIGHT_W - pad * 2 - 10).size() * 10;
        boolean hasMode = MODE_OPTIONS.containsKey(mod.getName());
        int py = ry + 58 + 12 + descH + 4 + 8 + (hasMode ? 35 : 0);

        // ── XRAY özel: cevher seçici tıklama ─────────────────────
        if ("Xray".equals(mod.getName())) {
            py += 22; // sectionLabel (13) + alt başlık satırı (20) - 11
            int tileW = (RIGHT_W - pad * 2 - 4) / 2;
            for (int i = 0; i < XRAY_ORES.length; i++) {
                String key = (String) XRAY_ORES[i][0];
                int col = i % 2;
                int row = i / 2;
                int tx  = rx + pad + col * (tileW + 4);
                int ty  = py + row * XRAY_TILE_H;
                if (mx >= tx && mx <= tx + tileW && my >= ty && my <= ty + XRAY_TILE_H - 2) {
                    mod.setSetting(key, !mod.getSetting(key));
                    return;
                }
            }
            return;
        }

        // ── Bool (diğer modüller) ─────────────────────────────────
        Map<String, Boolean> bools = mod.getBoolSettings();
        if (!bools.isEmpty()) {
            py += 13;
            List<String> keys = new ArrayList<>(bools.keySet()); Collections.sort(keys);
            for (String key : keys) {
                if (mx >= rx + pad && mx <= rx + RIGHT_W - pad && my >= py && my <= py + 16) {
                    mod.setSetting(key, !mod.getSetting(key)); return;
                }
                py += 18;
            }
        }

        // Int
        Map<String, Integer> ints = mod.getIntSettings();
        if (!ints.isEmpty()) {
            py += 2 + 8 + 13;
            for (Map.Entry<String, Integer> e : ints.entrySet()) {
                int vx = rx + RIGHT_W - pad - 52;
                if (my >= py + 2 && my <= py + 14) {
                    if (mx >= vx && mx <= vx + 14)
                        { mod.setIntSetting(e.getKey(), Math.max(0, e.getValue() - 1)); return; }
                    if (mx >= vx + 18 && mx <= vx + 36)
                        { mod.setIntSetting(e.getKey(), e.getValue() + 1); return; }
                }
                py += 18;
            }
        }

        // Float
        Map<String, Float> floats = mod.getFloatSettings();
        if (!floats.isEmpty()) {
            py += 2 + 8 + 13;
            for (Map.Entry<String, Float> e : floats.entrySet()) {
                int vx = rx + RIGHT_W - pad - 58;
                if (my >= py + 2 && my <= py + 14) {
                    float step = e.getKey().contains("speed") || e.getKey().contains("Speed") ? 0.05f : 0.1f;
                    if (mx >= vx && mx <= vx + 14)
                        { mod.setFloatSetting(e.getKey(), Math.max(0f, e.getValue() - step)); return; }
                    if (mx >= vx + 18 && mx <= vx + 36)
                        { mod.setFloatSetting(e.getKey(), e.getValue() + step); return; }
                }
                py += 18;
            }
        }
    }

    /** PojavLauncher mobil: dokunmatik sürükleme ile kaydırma */
    @Override
    public boolean mouseDragged(double mx, double my, int button, double deltaX, double deltaY) {
        int lx = (this.width - LEFT_W - RIGHT_W - 4) / 2;
        if (mx >= lx && mx <= lx + LEFT_W - 4 && my >= listAreaY && my <= listAreaBotY) {
            listScrollY = Math.max(0, Math.min(maxScrollY, listScrollY - (int) deltaY));
            return true;
        }
        return super.mouseDragged(mx, my, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double vScr) {
        listScrollY = Math.max(0, Math.min(maxScrollY, listScrollY - (int)(vScr * 14)));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int mods) {
        if (keyCode == 256) { this.close(); return true; }
        if (searchFocused && keyCode == 259 && !searchQuery.isEmpty()) {
            searchQuery = searchQuery.substring(0, searchQuery.length() - 1); listScrollY = 0;
        }
        return super.keyPressed(keyCode, scanCode, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (searchFocused && c >= 32 && searchQuery.length() < 24) {
            searchQuery += c; listScrollY = 0; return true;
        }
        return false;
    }

    @Override public boolean shouldPause() { return false; }

    // ═════════════════════════════════════════════════════════════
    //  YARDIMCILAR
    // ═════════════════════════════════════════════════════════════
    private List<Module> getFilteredModules() {
        List<Module> cat = ModuleManager.getByCategory(CATEGORIES[selectedCat]);
        if (searchQuery.isEmpty()) return cat;
        List<Module> res = new ArrayList<>();
        String q = searchQuery.toLowerCase();
        for (Module m : cat) if (m.getName().toLowerCase().contains(q)) res.add(m);
        return res;
    }

    private int getCatIndex(String cat) {
        for (int i = 0; i < CATEGORIES.length; i++) if (CATEGORIES[i].equals(cat)) return i;
        return -1;
    }

    private List<String> wrapText(String text, int maxW) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder cur = new StringBuilder();
        for (String w : words) {
            String test = cur.length() == 0 ? w : cur + " " + w;
            if (textRenderer.getWidth(test) > maxW) {
                if (cur.length() > 0) lines.add(cur.toString());
                cur = new StringBuilder(w);
            } else cur = new StringBuilder(test);
        }
        if (cur.length() > 0) lines.add(cur.toString());
        if (lines.isEmpty()) lines.add("");
        return lines;
    }

    private static String camelToNice(String s) {
        if (s.isEmpty()) return s;
        StringBuilder sb = new StringBuilder();
        sb.append(Character.toUpperCase(s.charAt(0)));
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isUpperCase(c)) sb.append(' ').append(c);
            else sb.append(c);
        }
        return sb.toString();
    }

    private void sectionLabel(DrawContext ctx, int rx, int py, int pad, int alpha, String label, int color) {
        ctx.drawTextWithShadow(textRenderer, label, rx + pad, py, applyAlpha(color, alpha));
    }

    private void divider(DrawContext ctx, int x1, int x2, int y, int alpha) {
        ctx.fill(x1 + 4, y, x2 - 4, y + 1, applyAlpha(DIVIDER, alpha));
    }

    private static int applyAlpha(int color, int alpha) {
        int a = ((color >> 24) & 0xFF) * alpha / 255;
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private static float easeOut(float t) { return 1f - (1f - t) * (1f - t); }
}
