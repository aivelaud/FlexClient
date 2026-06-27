package com.flex.client.gui;

import com.flex.client.CrashGuard;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * CrashGuardScreen — Bir önceki oyun oturumunda modül çökmesi yaşandıysa
 * ClickGui açılmadan önce gösterilen bilgi ekranı.
 *
 * Gösterilenler:
 *  • Hangi modüllerin oyunu çökertiğini / donattığını
 *  • Her modülün devre dışı bırakıldığını
 *  • "Devam Et" butonu → ClickGui açılır
 *  • "Temizle" butonu  → Kara liste sıfırlanır, ClickGui açılır
 */
public class CrashGuardScreen extends Screen {

    private static final int BG_DARK    = 0xF2050510;
    private static final int BG_PANEL   = 0xF00A0A20;
    private static final int RED        = 0xFFFF4466;
    private static final int YELLOW     = 0xFFFFDD00;
    private static final int GREEN      = 0xFF33FF99;
    private static final int CYAN       = 0xFF00FFCC;
    private static final int GRAY       = 0xFF8888AA;
    private static final int WHITE      = 0xFFFFFFFF;
    private static final int DIVIDER    = 0xFF1A1A3A;

    private final List<String> crashed;

    private int btnContY, btnClrY, btnW;

    public CrashGuardScreen() {
        super(Text.literal("FlexClient — Çökme Uyarısı"));
        this.crashed = new ArrayList<>(CrashGuard.getBlacklist());
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        int sw = this.width, sh = this.height;
        int pw = 320, ph = 80 + crashed.size() * 12 + 60;
        int px = (sw - pw) / 2, py = (sh - ph) / 2;

        // Koyu arka plan
        ctx.fill(0, 0, sw, sh, 0xCC000022);
        ctx.fill(px, py, px + pw, py + ph, BG_DARK);
        ctx.fill(px, py, px + 2, py + ph, RED);
        ctx.fill(px, py, px + pw, py + 1, RED & 0x55FFFFFF | 0x55000000);

        int y = py + 10;

        // Başlık
        ctx.drawTextWithShadow(textRenderer,
            "\u00a74\u00a7l\u26A0 Modül Çökmesi Tespit Edildi",
            px + 12, y, WHITE);
        y += 14;

        ctx.drawTextWithShadow(textRenderer,
            "\u00a77Bir önceki oturumda aşağıdaki modüller oyunu dondurdu:",
            px + 12, y, GRAY);
        y += 12;

        // Çizgi
        ctx.fill(px + 10, y, px + pw - 10, y + 1, DIVIDER);
        y += 6;

        // Çöken modüller
        for (String mod : crashed) {
            ctx.fill(px + 10, y - 1, px + pw - 10, y + 11, 0xFF0D0D22);
            ctx.fill(px + 10, y - 1, px + 12, y + 11, RED);
            ctx.drawTextWithShadow(textRenderer,
                "\u00a7c\u25CF \u00a7f" + mod + " \u00a77\u2192 devre dışı bırakıldı",
                px + 16, y + 1, WHITE);
            y += 13;
        }

        y += 6;
        ctx.fill(px + 10, y, px + pw - 10, y + 1, DIVIDER);
        y += 8;

        ctx.drawTextWithShadow(textRenderer,
            "\u00a77Bu modüller kara listeye alındı. Güvenli açılış için:",
            px + 12, y, GRAY);
        y += 10;
        ctx.drawTextWithShadow(textRenderer,
            "\u00a7e\u2022 Devam Et: Kapalı modüllerle devam et (güvenli)",
            px + 14, y, WHITE);
        y += 10;
        ctx.drawTextWithShadow(textRenderer,
            "\u00a7c\u2022 Temizle: Kara listeyi sil, tüm modüller tekrar açılabilir",
            px + 14, y, WHITE);
        y += 16;

        // Butonlar
        btnW = 120;
        btnContY = y;
        int btnH = 18;
        int bx1 = px + pw / 2 - btnW - 4;
        int bx2 = px + pw / 2 + 4;
        btnClrY  = y;

        boolean hovCont = mx >= bx1 && mx <= bx1 + btnW && my >= y && my <= y + btnH;
        boolean hovClr  = mx >= bx2 && mx <= bx2 + btnW && my >= y && my <= y + btnH;

        // Devam Et butonu (yeşil)
        ctx.fill(bx1, y, bx1 + btnW, y + btnH, hovCont ? 0xFF1A3A1A : 0xFF0D220D);
        ctx.fill(bx1, y, bx1 + 2, y + btnH, GREEN);
        String contLbl = "\u00a7a\u25BA Devam Et";
        ctx.drawTextWithShadow(textRenderer, contLbl,
            bx1 + btnW / 2 - textRenderer.getWidth(contLbl) / 2, y + 5, WHITE);

        // Temizle butonu (kırmızı)
        ctx.fill(bx2, y, bx2 + btnW, y + btnH, hovClr ? 0xFF3A0D0D : 0xFF220A0A);
        ctx.fill(bx2, y, bx2 + 2, y + btnH, RED);
        String clrLbl = "\u00a7c\u2716 Temizle";
        ctx.drawTextWithShadow(textRenderer, clrLbl,
            bx2 + btnW / 2 - textRenderer.getWidth(clrLbl) / 2, y + 5, WHITE);

        super.render(ctx, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int sw = this.width, sh = this.height;
        int pw = 320, ph = 80 + crashed.size() * 12 + 60;
        int px = (sw - pw) / 2, py = (sh - ph) / 2;
        int y = btnContY;
        int btnH = 18;
        int bx1 = px + pw / 2 - btnW - 4;
        int bx2 = px + pw / 2 + 4;

        if (mx >= bx1 && mx <= bx1 + btnW && my >= y && my <= y + btnH) {
            openClickGui();
            return true;
        }
        if (mx >= bx2 && mx <= bx2 + btnW && my >= y && my <= y + btnH) {
            CrashGuard.clearBlacklist();
            openClickGui();
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    private void openClickGui() {
        if (this.client != null) {
            this.client.execute(() -> this.client.setScreen(new ClickGui()));
        }
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public boolean shouldPause() { return false; }
}
