package com.flex.client.gui;

import com.flex.client.module.Module;
import com.flex.client.module.ModuleManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * FlexClient 3.0 — Profesyonel ClickGUI
 *
 * Özellikler:
 *  - Sol panel: Kategori sekmeleri + Modül listesi (scroll destekli)
 *  - Sağ panel: Seçili modülün detayları, ayarları, keybind
 *  - Arama çubuğu
 *  - Sol tık: aç/kapat  |  Sağ tık (PojavLauncher SEC): detay paneli
 *  - Mobil dokunma dostu (büyük hedef alanları)
 */
public class ClickGui extends Screen {

    // ── Renk Paleti ──────────────────────────────────────────────
    private static final int BG_DARK      = 0xF0050510;
    private static final int BG_PANEL     = 0xF00A0A1E;
    private static final int BG_ITEM      = 0xFF0D0D22;
    private static final int BG_ITEM_HOV  = 0xFF16163A;
    private static final int BG_ITEM_ON   = 0xFF0F1030;
    private static final int ACCENT_CYAN  = 0xFF00FFCC;
    private static final int ACCENT_PURP  = 0xFF9933FF;
    private static final int ACCENT_RED   = 0xFFFF4466;
    private static final int ACCENT_BLUE  = 0xFF3399FF;
    private static final int ACCENT_GREEN = 0xFF33FF99;
    private static final int ACCENT_ORG   = 0xFFFF9922;
    private static final int TEXT_WHITE   = 0xFFFFFFFF;
    private static final int TEXT_GRAY    = 0xFF8888AA;
    private static final int TEXT_DIM     = 0xFF555577;
    private static final int DIVIDER      = 0xFF1A1A3A;

    // Kategori renkleri
    private static final int[] CAT_COLORS = {
        ACCENT_RED,   // Combat
        ACCENT_BLUE,  // Movement
        ACCENT_GREEN, // Render
        ACCENT_ORG,   // Player
    };
    private static final String[] CATEGORIES = {"Combat", "Movement", "Render", "Player"};
    private static final String[] CAT_ICONS  = {"\u2694", "\u26A1", "\u25A6", "\u2764"};

    // ── Layout ───────────────────────────────────────────────────
    private static final int TAB_H       = 36;
    private static final int LEFT_W      = 200;
    private static final int RIGHT_W     = 220;
    private static final int ITEM_H      = 28;
    private static final int SEARCH_H    = 28;
    private static final int PADDING     = 8;

    // ── State ────────────────────────────────────────────────────
    private int selectedCat   = 0;      // Seçili kategori indeksi
    private Module selectedMod = null;  // Detay panelinde gösterilen modül
    private String searchQuery = "";
    private boolean searchFocused = false;

    // Scroll
    private int listScrollY  = 0;
    private int maxScrollY   = 0;

    // Animasyon
    private float openAnim   = 0f;     // 0→1 açılış animasyonu

    // Touch feedback
    private double touchX = -1, touchY = -1;
    private int touchTicks = 0;

    public ClickGui() {
        super(Text.literal("FlexClient"));
    }

    // ─────────────────────────────────────────────────────────────
    //  RENDER
    // ─────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        // Açılış animasyonu
        openAnim = Math.min(1f, openAnim + delta * 0.15f);
        float anim = easeOut(openAnim);

        int screenW = this.width;
        int screenH = this.height;

        // Toplam GUI genişliği
        int guiW  = LEFT_W + RIGHT_W + 4;
        int guiH  = screenH - 20;
        int guiX  = (screenW - guiW) / 2;
        int guiY  = 10;

        // Animasyonlu konum
        int drawY = guiY + (int)((1 - anim) * 30);
        int alpha = (int)(anim * 255);

        // ── Koyu arka plan overlay ──
        ctx.fillGradient(0, 0, screenW, screenH, 0xCC000008, 0xDD000015);

        // Touch ripple
        if (touchTicks > 0) {
            touchTicks--;
            int r = 18 - touchTicks;
            int ta = Math.min(touchTicks * 20, 120);
            ctx.fill((int)touchX - r, (int)touchY - r, (int)touchX + r, (int)touchY + r, (ta << 24) | 0x00AAFFCC);
        }

        // ── Sol Panel ──────────────────────────────────────────
        int lx = guiX;
        int ly = drawY;

        // Sol panel arka plan
        ctx.fill(lx, ly, lx + LEFT_W, ly + guiH, applyAlpha(BG_PANEL, alpha));
        // Sol border (neon)
        ctx.fill(lx, ly, lx + 2, ly + guiH, applyAlpha(ACCENT_CYAN, alpha));

        // Watermark / başlık
        ctx.fill(lx + 2, ly, lx + LEFT_W, ly + 30, applyAlpha(BG_DARK, alpha));
        ctx.fill(lx + 2, ly + 30, lx + LEFT_W, ly + 31, applyAlpha(ACCENT_CYAN & 0x00FFFFFF | 0x44000000, alpha));
        ctx.drawTextWithShadow(textRenderer,
            "\u00a7bFlex\u00a7fClient \u00a773.0",
            lx + PADDING, ly + 9, applyAlpha(TEXT_WHITE, alpha));
        long enabled = ModuleManager.modules.stream().filter(Module::isEnabled).count();
        ctx.drawTextWithShadow(textRenderer,
            "\u00a77" + enabled + "/" + ModuleManager.modules.size() + " aktif",
            lx + LEFT_W - textRenderer.getWidth("\u00a77" + enabled + "/" + ModuleManager.modules.size() + " aktif") - PADDING,
            ly + 9, applyAlpha(TEXT_DIM, alpha));

        // ── Kategori sekmeleri ──────────────────────────────
        int tabY = ly + 31;
        int tabW = (LEFT_W - 2) / CATEGORIES.length;
        for (int i = 0; i < CATEGORIES.length; i++) {
            int tx = lx + 2 + i * tabW;
            boolean sel = i == selectedCat;
            boolean hov = mx >= tx && mx < tx + tabW && my >= tabY && my < tabY + TAB_H;
            int catC = CAT_COLORS[i];

            ctx.fill(tx, tabY, tx + tabW, tabY + TAB_H,
                sel ? applyAlpha(catC & 0x00FFFFFF | 0x22000000, alpha)
                    : hov ? applyAlpha(BG_ITEM_HOV, alpha)
                    :       applyAlpha(BG_ITEM, alpha));

            // Alt border (seçili = renkli)
            ctx.fill(tx, tabY + TAB_H - 2, tx + tabW, tabY + TAB_H,
                sel ? applyAlpha(catC, alpha) : applyAlpha(DIVIDER, alpha));

            // İkon + kısa isim
            String icon = CAT_ICONS[i];
            String lbl  = CATEGORIES[i].substring(0, Math.min(3, CATEGORIES[i].length()));
            int icW = textRenderer.getWidth(icon);
            int lbW = textRenderer.getWidth(lbl);
            int cx2 = tx + tabW / 2;
            ctx.drawTextWithShadow(textRenderer, icon, cx2 - icW / 2, tabY + 6,
                sel ? applyAlpha(catC, alpha) : applyAlpha(TEXT_GRAY, alpha));
            ctx.drawTextWithShadow(textRenderer, lbl, cx2 - lbW / 2, tabY + 17,
                sel ? applyAlpha(TEXT_WHITE, alpha) : applyAlpha(TEXT_GRAY, alpha));
        }

        // ── Arama çubuğu ──────────────────────────────────
        int searchY = tabY + TAB_H + 2;
        ctx.fill(lx + 2, searchY, lx + LEFT_W, searchY + SEARCH_H, applyAlpha(BG_DARK, alpha));
        ctx.fill(lx + 2, searchY + SEARCH_H - 1, lx + LEFT_W, searchY + SEARCH_H,
            searchFocused ? applyAlpha(ACCENT_CYAN, alpha) : applyAlpha(DIVIDER, alpha));
        String searchDisplay = searchQuery.isEmpty()
            ? (searchFocused ? "\u00a7b|\u00a77 Ara..." : "\u00a77\uD83D\uDD0D Ara...")
            : "\u00a7f" + searchQuery + (searchFocused ? "\u00a7b|" : "");
        ctx.drawTextWithShadow(textRenderer, searchDisplay, lx + PADDING, searchY + 9, TEXT_WHITE);

        // ── Modül listesi ──────────────────────────────────
        int listY     = searchY + SEARCH_H + 2;
        int listH     = ly + guiH - listY;
        int listBotY  = ly + guiH;

        // Scissor (kırpma) — Minecraft'ın GuiGraphics scissor sistemi yerine manuel kontrol
        List<Module> filteredMods = getFilteredModules();
        maxScrollY = Math.max(0, filteredMods.size() * (ITEM_H + 1) - listH);
        listScrollY = Math.max(0, Math.min(listScrollY, maxScrollY));

        int itemY = listY - listScrollY;
        int catC = CAT_COLORS[selectedCat];

        for (Module m : filteredMods) {
            if (itemY + ITEM_H < listY) { itemY += ITEM_H + 1; continue; }
            if (itemY > listBotY) break;

            boolean hov = mx >= lx && mx < lx + LEFT_W && my >= itemY && my < itemY + ITEM_H;
            boolean on  = m.isEnabled();
            boolean sel = m == selectedMod;

            int bg = sel ? applyAlpha(catC & 0x00FFFFFF | 0x18000000, alpha)
                    : on  ? applyAlpha(BG_ITEM_ON, alpha)
                    : hov ? applyAlpha(BG_ITEM_HOV, alpha)
                    :       applyAlpha(BG_ITEM, alpha);
            ctx.fill(lx + 2, itemY, lx + LEFT_W, itemY + ITEM_H, bg);
            // Sol serit
            ctx.fill(lx + 2, itemY, lx + 4, itemY + ITEM_H,
                on ? applyAlpha(catC, alpha) : applyAlpha(DIVIDER, alpha));
            // Alt ince çizgi
            ctx.fill(lx + 4, itemY + ITEM_H - 1, lx + LEFT_W, itemY + ITEM_H, applyAlpha(DIVIDER, alpha));

            // Modül adı
            ctx.drawTextWithShadow(textRenderer, m.getName(),
                lx + PADDING + 2, itemY + (ITEM_H / 2) - 4,
                applyAlpha(on ? TEXT_WHITE : TEXT_GRAY, alpha));

            // ON göstergesi (sağ taraf - küçük yeşil kare)
            if (on) {
                ctx.fill(lx + LEFT_W - 10, itemY + ITEM_H/2 - 3, lx + LEFT_W - 4, itemY + ITEM_H/2 + 3,
                    applyAlpha(catC, alpha));
            }

            // Sağ ok (detay için)
            ctx.drawTextWithShadow(textRenderer, "\u00a77>",
                lx + LEFT_W - (on ? 20 : 12), itemY + (ITEM_H / 2) - 4,
                applyAlpha(sel ? TEXT_WHITE : TEXT_DIM, alpha));

            itemY += ITEM_H + 1;
        }

        // Scroll bar
        if (maxScrollY > 0) {
            float scrollRatio = (float)listScrollY / maxScrollY;
            int sbH = Math.max(20, listH * listH / (filteredMods.size() * (ITEM_H + 1)));
            int sbY = listY + (int)(scrollRatio * (listH - sbH));
            ctx.fill(lx + LEFT_W - 3, listY, lx + LEFT_W - 1, listBotY, applyAlpha(DIVIDER, alpha));
            ctx.fill(lx + LEFT_W - 3, sbY, lx + LEFT_W - 1, sbY + sbH, applyAlpha(ACCENT_CYAN, alpha));
        }

        // ── Sağ Panel (Modül Detayı) ───────────────────────
        int rx = lx + LEFT_W + 4;
        int ry = drawY;
        ctx.fill(rx, ry, rx + RIGHT_W, ry + guiH, applyAlpha(BG_PANEL, alpha));
        ctx.fill(rx + RIGHT_W - 2, ry, rx + RIGHT_W, ry + guiH, applyAlpha(ACCENT_PURP, alpha));

        if (selectedMod != null) {
            renderDetailPanel(ctx, rx, ry, guiH, selectedMod, alpha, mx, my);
        } else {
            renderEmptyDetail(ctx, rx, ry, guiH, alpha);
        }

        // ── Alt bilgi çubuğu ──────────────────────────────
        ctx.fill(0, screenH - 14, screenW, screenH, applyAlpha(BG_DARK, alpha));
        ctx.fill(0, screenH - 14, screenW, screenH - 13, applyAlpha(ACCENT_CYAN & 0x00FFFFFF | 0x33000000, alpha));
        ctx.drawTextWithShadow(textRenderer,
            "\u00a77[L.Tık]\u00a7f Aç/Kapat  \u00a77[R.Tık/SEC]\u00a7f Detay  \u00a77[ESC]\u00a7f Kapat  \u00a77[Kaydır]\u00a7f Scroll",
            4, screenH - 10, applyAlpha(TEXT_DIM, alpha));

        super.render(ctx, mx, my, delta);
    }

    // ─────────────────────────────────────────────────────────────
    //  DETAIL PANEL
    // ─────────────────────────────────────────────────────────────

    private void renderDetailPanel(DrawContext ctx, int rx, int ry, int guiH,
                                   Module mod, int alpha, int mx, int my) {
        int catIdx  = getCatIndex(mod.getCategory());
        int catC    = catIdx >= 0 ? CAT_COLORS[catIdx] : ACCENT_CYAN;
        int padding = PADDING + 4;

        // Başlık bölgesi
        ctx.fill(rx, ry, rx + RIGHT_W - 2, ry + 50, applyAlpha(BG_DARK, alpha));
        ctx.fill(rx, ry + 50, rx + RIGHT_W - 2, ry + 51, applyAlpha(catC, alpha));

        // Modül adı (büyük)
        ctx.drawTextWithShadow(textRenderer, mod.getName(),
            rx + padding, ry + 10, applyAlpha(catC, alpha));
        ctx.drawTextWithShadow(textRenderer, "\u00a77" + mod.getCategory(),
            rx + padding, ry + 22, applyAlpha(TEXT_GRAY, alpha));

        // ON/OFF toggle büyük buton
        boolean on  = mod.isEnabled();
        int btnX = rx + RIGHT_W - 58;
        int btnY = ry + 12;
        int btnW = 50, btnH = 20;
        boolean btnHov = mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + btnH;
        ctx.fill(btnX, btnY, btnX + btnW, btnY + btnH,
            applyAlpha(on ? catC & 0x00FFFFFF | 0x33000000 : BG_ITEM, alpha));
        ctx.fill(btnX, btnY, btnX + 2, btnY + btnH, applyAlpha(on ? catC : ACCENT_RED, alpha));
        String toggleLbl = on ? "\u00a7a ON" : "\u00a7c OFF";
        ctx.drawTextWithShadow(textRenderer, toggleLbl,
            btnX + btnW / 2 - textRenderer.getWidth(on ? " ON" : " OFF") / 2,
            btnY + btnH / 2 - 4, TEXT_WHITE);

        int py = ry + 60;

        // Açıklama
        ctx.drawTextWithShadow(textRenderer, "\u00a77Açıklama", rx + padding, py, applyAlpha(TEXT_DIM, alpha));
        py += 12;
        String desc = mod.getDescription().isEmpty() ? "Açıklama yok" : mod.getDescription();
        // Metin sarma (basit)
        for (String line : wrapText(desc, RIGHT_W - padding * 2 - 8)) {
            ctx.drawTextWithShadow(textRenderer, "\u00a7f" + line, rx + padding, py, applyAlpha(TEXT_WHITE, alpha));
            py += 11;
        }
        py += 6;

        // Ayırıcı çizgi
        ctx.fill(rx + padding, py, rx + RIGHT_W - padding - 8, py + 1, applyAlpha(DIVIDER, alpha));
        py += 8;

        // PojavLauncher bilgisi
        ctx.fill(rx + padding - 2, py - 2, rx + RIGHT_W - padding - 8, py + 22, applyAlpha(0xFF080820, alpha));
        ctx.fill(rx + padding - 2, py - 2, rx + padding, py + 22, applyAlpha(ACCENT_PURP, alpha));
        ctx.drawTextWithShadow(textRenderer, "\u00a7d\u2605 PojavLauncher SEC Detayı", rx + padding + 2, py + 2, applyAlpha(ACCENT_PURP, alpha));
        ctx.drawTextWithShadow(textRenderer, "\u00a77Bu modüle sağ tık = detay", rx + padding + 2, py + 12, applyAlpha(TEXT_DIM, alpha));
        py += 28;

        // Ayırıcı
        ctx.fill(rx + padding, py, rx + RIGHT_W - padding - 8, py + 1, applyAlpha(DIVIDER, alpha));
        py += 8;

        // Tuş bağlama
        ctx.drawTextWithShadow(textRenderer, "\u00a77Tuş Bağlama", rx + padding, py, applyAlpha(TEXT_DIM, alpha));
        py += 12;
        int kb = mod.getKeybind();
        String kbTxt = kb == -1 ? "\u00a78[Bağlı değil]" : "\u00a7f[" + getKeyName(kb) + "]";
        ctx.fill(rx + padding, py, rx + padding + 100, py + 16,
            applyAlpha(BG_ITEM, alpha));
        ctx.drawTextWithShadow(textRenderer, kbTxt, rx + padding + 4, py + 4,
            applyAlpha(TEXT_WHITE, alpha));
        py += 22;

        // Ayırıcı
        ctx.fill(rx + padding, py, rx + RIGHT_W - padding - 8, py + 1, applyAlpha(DIVIDER, alpha));
        py += 8;

        // Modüle özgü hızlı ayarlar
        py = renderModuleSettings(ctx, rx, py, mod, catC, alpha, padding, mx, my);
    }

    private int renderModuleSettings(DrawContext ctx, int rx, int py, Module mod,
                                     int catC, int alpha, int padding, int mx, int my) {
        ctx.drawTextWithShadow(textRenderer, "\u00a77Hızlı Ayarlar", rx + padding, py, applyAlpha(TEXT_DIM, alpha));
        py += 12;

        switch (mod.getName()) {
            case "KillAura":
                py = renderBoolSetting(ctx, rx, py, mod, "hitMonsters", "Moblara vur", catC, alpha, padding, mx, my);
                py = renderBoolSetting(ctx, rx, py, mod, "hitPlayers", "Oyunculara vur", catC, alpha, padding, mx, my);
                py = renderBoolSetting(ctx, rx, py, mod, "hitAnimals", "Hayvanlara vur", catC, alpha, padding, mx, my);
                py = renderBoolSetting(ctx, rx, py, mod, "rotate", "Otomatik döndür", catC, alpha, padding, mx, my);
                break;
            case "Fly":
                py = renderBoolSetting(ctx, rx, py, mod, "noFall", "NoFall dahil", catC, alpha, padding, mx, my);
                break;
            case "ESP":
                py = renderBoolSetting(ctx, rx, py, mod, "showPlayers", "Oyuncuları göster", catC, alpha, padding, mx, my);
                py = renderBoolSetting(ctx, rx, py, mod, "showMobs", "Mobları göster", catC, alpha, padding, mx, my);
                py = renderBoolSetting(ctx, rx, py, mod, "showAnimals", "Hayvanları göster", catC, alpha, padding, mx, my);
                break;
            case "Xray":
                py = renderBoolSetting(ctx, rx, py, mod, "showDiamond", "Diamond göster", catC, alpha, padding, mx, my);
                py = renderBoolSetting(ctx, rx, py, mod, "showGold", "Gold göster", catC, alpha, padding, mx, my);
                py = renderBoolSetting(ctx, rx, py, mod, "showAncientDebris", "Ancient Debris göster", catC, alpha, padding, mx, my);
                py = renderBoolSetting(ctx, rx, py, mod, "showChests", "Sandıkları göster", catC, alpha, padding, mx, my);
                break;
            case "AimAssist":
                py = renderBoolSetting(ctx, rx, py, mod, "players", "Oyuncuları hedefle", catC, alpha, padding, mx, my);
                py = renderBoolSetting(ctx, rx, py, mod, "mobs", "Mobları hedefle", catC, alpha, padding, mx, my);
                break;
            case "Scaffold":
                py = renderBoolSetting(ctx, rx, py, mod, "tower", "Tower modu", catC, alpha, padding, mx, my);
                py = renderBoolSetting(ctx, rx, py, mod, "safe", "Güvenli zemin", catC, alpha, padding, mx, my);
                break;
            default:
                ctx.drawTextWithShadow(textRenderer, "\u00a78Bu modül için ek ayar yok.",
                    rx + padding, py, applyAlpha(TEXT_DIM, alpha));
                py += 11;
                break;
        }
        return py;
    }

    private int renderBoolSetting(DrawContext ctx, int rx, int py, Module mod,
                                   String key, String label, int catC,
                                   int alpha, int padding, int mx, int my) {
        boolean val = mod.getSetting(key);
        boolean hov = mx >= rx + padding && mx <= rx + RIGHT_W - 10 && my >= py && my <= py + 16;
        ctx.fill(rx + padding, py, rx + RIGHT_W - padding - 8, py + 16,
            applyAlpha(hov ? BG_ITEM_HOV : BG_ITEM, alpha));
        String check = val ? "\u00a7a\u25A0 " : "\u00a78\u25A1 ";
        ctx.drawTextWithShadow(textRenderer, check + "\u00a7f" + label,
            rx + padding + 4, py + 4, applyAlpha(TEXT_WHITE, alpha));
        return py + 18;
    }

    private void renderEmptyDetail(DrawContext ctx, int rx, int ry, int guiH, int alpha) {
        String msg1 = "\u00a7bModül Seç";
        String msg2 = "\u00a77Detaylar için modüle";
        String msg3 = "\u00a77sağ tık (SEC) veya tıkla";
        int mx2 = rx + RIGHT_W / 2;
        int my2 = ry + guiH / 2;
        ctx.drawTextWithShadow(textRenderer, msg1, mx2 - textRenderer.getWidth(msg1) / 2, my2 - 16, applyAlpha(TEXT_WHITE, alpha));
        ctx.drawTextWithShadow(textRenderer, msg2, mx2 - textRenderer.getWidth(msg2) / 2, my2 - 4, applyAlpha(TEXT_GRAY, alpha));
        ctx.drawTextWithShadow(textRenderer, msg3, mx2 - textRenderer.getWidth(msg3) / 2, my2 + 6, applyAlpha(TEXT_GRAY, alpha));
        // Logo
        ctx.drawTextWithShadow(textRenderer, "\u00a7b\u00a7l{ FC }",
            mx2 - textRenderer.getWidth("\u00a7b\u00a7l{ FC }") / 2, my2 - 40, applyAlpha(ACCENT_CYAN, alpha));
    }

    // ─────────────────────────────────────────────────────────────
    //  MOUSE CLICKS
    // ─────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int screenW = this.width;
        int screenH = this.height;
        int guiW    = LEFT_W + RIGHT_W + 4;
        int guiH    = screenH - 20;
        int guiX    = (screenW - guiW) / 2;
        int guiY    = 10;

        touchX = mx; touchY = my; touchTicks = 8;

        // Arama çubuğu tıklama
        int tabY    = guiY + 31;
        int searchY = tabY + TAB_H + 2;
        if (mx >= guiX + 2 && mx <= guiX + LEFT_W && my >= searchY && my <= searchY + SEARCH_H) {
            searchFocused = true;
            return true;
        } else {
            searchFocused = false;
        }

        // Kategori sekmeleri tıklama
        int tabW = (LEFT_W - 2) / CATEGORIES.length;
        for (int i = 0; i < CATEGORIES.length; i++) {
            int tx = guiX + 2 + i * tabW;
            if (mx >= tx && mx <= tx + tabW && my >= tabY && my <= tabY + TAB_H) {
                selectedCat = i;
                listScrollY = 0;
                return true;
            }
        }

        // Modül listesi tıklama
        int listY = searchY + SEARCH_H + 2;
        List<Module> filtered = getFilteredModules();
        int itemY  = listY - listScrollY;
        for (Module m : filtered) {
            if (itemY + ITEM_H > listY && itemY < guiY + guiH) {
                if (mx >= guiX && mx <= guiX + LEFT_W && my >= itemY && my <= itemY + ITEM_H) {
                    if (btn == 0) {
                        // Sol tık: aç/kapat
                        m.toggle();
                    } else if (btn == 1) {
                        // Sağ tık (PojavLauncher SEC): detay paneli
                        selectedMod = (selectedMod == m) ? null : m;
                    }
                    return true;
                }
            }
            itemY += ITEM_H + 1;
        }

        // Sağ panel toggle butonu
        int rx = guiX + LEFT_W + 4;
        int ry = guiY;
        if (selectedMod != null) {
            int btnX = rx + RIGHT_W - 58;
            int btnY = ry + 12;
            if (mx >= btnX && mx <= btnX + 50 && my >= btnY && my <= btnY + 20) {
                selectedMod.toggle();
                return true;
            }
            // Bool ayar tıklamaları sağ panel
            int catIdx  = getCatIndex(selectedMod.getCategory());
            int catC    = catIdx >= 0 ? CAT_COLORS[catIdx] : ACCENT_CYAN;
            int padding = PADDING + 4;
            int py = ry + 60 + 12;
            // Açıklama satırları
            py += wrapText(selectedMod.getDescription().isEmpty() ? "Açıklama yok" : selectedMod.getDescription(),
                RIGHT_W - padding * 2 - 8).size() * 11 + 6 + 1 + 8 + 28 + 1 + 8 + 22 + 1 + 8 + 12;

            switch (selectedMod.getName()) {
                case "KillAura": py = checkBoolClick(mx, my, rx, py, selectedMod, "hitMonsters", padding, guiX); py = checkBoolClick(mx, my, rx, py, selectedMod, "hitPlayers", padding, guiX); py = checkBoolClick(mx, my, rx, py, selectedMod, "hitAnimals", padding, guiX); checkBoolClick(mx, my, rx, py, selectedMod, "rotate", padding, guiX); break;
                case "Fly":     checkBoolClick(mx, my, rx, py, selectedMod, "noFall", padding, guiX); break;
                case "ESP":     py = checkBoolClick(mx, my, rx, py, selectedMod, "showPlayers", padding, guiX); py = checkBoolClick(mx, my, rx, py, selectedMod, "showMobs", padding, guiX); checkBoolClick(mx, my, rx, py, selectedMod, "showAnimals", padding, guiX); break;
                case "Xray":    py = checkBoolClick(mx, my, rx, py, selectedMod, "showDiamond", padding, guiX); py = checkBoolClick(mx, my, rx, py, selectedMod, "showGold", padding, guiX); py = checkBoolClick(mx, my, rx, py, selectedMod, "showAncientDebris", padding, guiX); checkBoolClick(mx, my, rx, py, selectedMod, "showChests", padding, guiX); break;
                case "AimAssist": py = checkBoolClick(mx, my, rx, py, selectedMod, "players", padding, guiX); checkBoolClick(mx, my, rx, py, selectedMod, "mobs", padding, guiX); break;
                case "Scaffold": py = checkBoolClick(mx, my, rx, py, selectedMod, "tower", padding, guiX); checkBoolClick(mx, my, rx, py, selectedMod, "safe", padding, guiX); break;
            }
        }

        return super.mouseClicked(mx, my, btn);
    }

    private int checkBoolClick(double mx, double my, int rx, int py, Module mod, String key, int padding, int guiX) {
        if (mx >= rx + padding && mx <= rx + RIGHT_W - 10 && my >= py && my <= py + 16) {
            mod.setSetting(key, !mod.getSetting(key));
        }
        return py + 18;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hScroll, double vScroll) {
        listScrollY = Math.max(0, Math.min(maxScrollY, listScrollY - (int)(vScroll * 12)));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { this.close(); return true; } // ESC

        if (searchFocused) {
            if (keyCode == 259 && !searchQuery.isEmpty()) {
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                listScrollY = 0;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (searchFocused && c >= 32 && searchQuery.length() < 20) {
            searchQuery += c;
            listScrollY = 0;
            return true;
        }
        return false;
    }

    @Override
    public boolean shouldPause() { return false; }

    // ─────────────────────────────────────────────────────────────
    //  YARDIMCI METODLAR
    // ─────────────────────────────────────────────────────────────

    private List<Module> getFilteredModules() {
        List<Module> cat = ModuleManager.getByCategory(CATEGORIES[selectedCat]);
        if (searchQuery.isEmpty()) return cat;
        List<Module> result = new ArrayList<>();
        String q = searchQuery.toLowerCase();
        for (Module m : cat) {
            if (m.getName().toLowerCase().contains(q)) result.add(m);
        }
        return result;
    }

    private int getCatIndex(String cat) {
        for (int i = 0; i < CATEGORIES.length; i++) {
            if (CATEGORIES[i].equals(cat)) return i;
        }
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
            } else {
                cur = new StringBuilder(test);
            }
        }
        if (cur.length() > 0) lines.add(cur.toString());
        if (lines.isEmpty()) lines.add("");
        return lines;
    }

    private String getKeyName(int keyCode) {
        if (keyCode == -1) return "Yok";
        // Basit tuş adları
        if (keyCode >= 65 && keyCode <= 90) return String.valueOf((char)keyCode);
        if (keyCode >= 48 && keyCode <= 57) return String.valueOf((char)keyCode);
        switch (keyCode) {
            case 290: return "F1"; case 291: return "F2"; case 292: return "F3";
            case 340: return "LShift"; case 341: return "LCtrl";
            case 344: return "RShift"; case 345: return "RCtrl";
            default: return "KEY_" + keyCode;
        }
    }

    private static int applyAlpha(int color, int alpha) {
        int a = ((color >> 24) & 0xFF) * alpha / 255;
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private static float easeOut(float t) {
        return 1f - (1f - t) * (1f - t);
    }
}
