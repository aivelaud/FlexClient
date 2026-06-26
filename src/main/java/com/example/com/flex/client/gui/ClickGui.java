package com.flex.client.gui;

import com.flex.client.module.Module;
import com.flex.client.module.ModuleManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import java.util.Arrays;
import java.util.List;

public class ClickGui extends Screen {
    private static final List<String> CATEGORIES = Arrays.asList("Combat", "Movement", "Render");

    // PojavLauncher icin buton boyutlari BUYUTULDU (parmakla tıklamak icin)
    private static final int COL_W = 160;   // 120 -> 160
    private static final int COL_H = 28;    // 20 -> 28 (daha kolay tiklanir)
    private static final int SUB_H = 26;    // alt ayar satiri yuksekligi
    private static final int COL_X_START = 10;
    private static final int COL_Y_START = 10;
    private static final int COL_GAP = 8;   // sutunlar arasi bosluk

    // Dokunma icin son tiklanan alani tut (gorsel geri bildirim)
    private double lastTouchX = -1, lastTouchY = -1;
    private int touchFeedbackTicks = 0;

    public ClickGui() {
        super(Text.literal("FlexClient"));
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        // Arkaplan - biraz daha koyu ve net
        ctx.fillGradient(0, 0, this.width, this.height, 0xCC000000, 0xDD000000);

        // Dokunma geri bildirimi - tiklanan yerde parlama efekti
        if (touchFeedbackTicks > 0) {
            touchFeedbackTicks--;
            int alpha = (int)(touchFeedbackTicks * 12);
            if (alpha > 180) alpha = 180;
            ctx.fill((int)lastTouchX - 20, (int)lastTouchY - 20,
                     (int)lastTouchX + 20, (int)lastTouchY + 20,
                     (alpha << 24) | 0x00FFAA44);
        }

        int cx = COL_X_START;
        for (String cat : CATEGORIES) {
            // Kategori baslik kutusu
            ctx.fill(cx, COL_Y_START, cx + COL_W, COL_Y_START + COL_H, 0xFF1a1a2e);
            // Kategori sol serit
            ctx.fill(cx, COL_Y_START, cx + 3, COL_Y_START + COL_H, 0xFFFF9900);
            ctx.drawTextWithShadow(textRenderer, cat, cx + 8, COL_Y_START + (COL_H / 2) - 4, 0xFFFF9900);

            int cy = COL_Y_START + COL_H + 2;
            for (Module m : ModuleManager.modules) {
                if (!m.getCategory().equals(cat)) continue;

                // PojavLauncher'da mx/my her zaman dogru gelmeyebilir,
                // bu yuzden hover kontrolunu daha genis tutuyoruz
                boolean hovered = mx >= cx && mx <= cx + COL_W && my >= cy && my <= cy + COL_H;
                int bg = m.isEnabled() ? 0xFF16213e : (hovered ? 0xFF2a2a3e : 0xFF0f0f1e);
                int textColor = m.isEnabled() ? 0xFF00FFAA : 0xFFCCCCCC;

                // Modul arka plan
                ctx.fill(cx, cy, cx + COL_W, cy + COL_H, bg);
                // Sol renkli serit (aktif/pasif gostergesi)
                ctx.fill(cx, cy, cx + 3, cy + COL_H, m.isEnabled() ? 0xFF00FFAA : 0xFF444444);
                // Modul adi - dikey ortalanmis
                ctx.drawTextWithShadow(textRenderer, m.getName(), cx + 8, cy + (COL_H / 2) - 4, textColor);

                // Aktifse sag tarafta kucuk gosterge
                if (m.isEnabled()) {
                    ctx.drawTextWithShadow(textRenderer, "ON", cx + COL_W - 22, cy + (COL_H / 2) - 4, 0xFF00FFAA);
                }

                // KillAura alt ayarlari
                if (m.getName().equals("KillAura") && m.isEnabled()) {
                    cy += COL_H + 1;
                    boolean anim = m.isHitAnimals();
                    boolean subHov = mx >= cx + 10 && mx <= cx + COL_W && my >= cy && my <= cy + SUB_H;
                    ctx.fill(cx + 10, cy, cx + COL_W, cy + SUB_H,
                             subHov ? 0xFF1e1e3e : 0xFF141428);
                    // Checkbox gorsel
                    String checkmark = anim ? "§a[✔]" : "§7[✘]";
                    ctx.drawTextWithShadow(textRenderer,
                            checkmark + " §fHayvanlara vur",
                            cx + 16, cy + (SUB_H / 2) - 4, 0xFFAAAAAA);
                    cy += SUB_H + 1;
                    continue;
                }

                cy += COL_H + 1;
            }
            cx += COL_W + COL_GAP;
        }

        // Altta kucuk ipucu yazisi (PojavLauncher kullanicilari icin)
        ctx.drawTextWithShadow(textRenderer,
                "§7[FlexClient] §fDismiss: INSERT tus | Tikla: Ac/Kapat",
                4, this.height - 12, 0xFFAAAAAA);

        super.render(ctx, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // PojavLauncher'da touch, sol tik (0) olarak gelir - bu zaten dogru
        if (btn != 0) return super.mouseClicked(mx, my, btn);

        // Dokunma geri bildirimi kaydet
        lastTouchX = mx;
        lastTouchY = my;
        touchFeedbackTicks = 8;

        int cx = COL_X_START;
        for (String cat : CATEGORIES) {
            int cy = COL_Y_START + COL_H + 2;
            for (Module m : ModuleManager.modules) {
                if (!m.getCategory().equals(cat)) continue;

                // Tiklanabilir alan - biraz daha genis tutuldu (parmak hassasiyeti)
                if (mx >= cx && mx <= cx + COL_W && my >= cy && my <= cy + COL_H) {
                    m.toggle();
                    return true;
                }

                if (m.getName().equals("KillAura") && m.isEnabled()) {
                    cy += COL_H + 1;
                    if (mx >= cx + 10 && mx <= cx + COL_W && my >= cy && my <= cy + SUB_H) {
                        m.setSetting("hitAnimals", !m.isHitAnimals());
                        return true;
                    }
                    cy += SUB_H + 1;
                    continue;
                }

                cy += COL_H + 1;
            }
            cx += COL_W + COL_GAP;
        }
        return super.mouseClicked(mx, my, btn);
    }

    // PojavLauncher'da ESC ile de kapanabilmesi icin
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // ESC (256) veya INSERT (260) ile kapat
        if (keyCode == 256 || keyCode == 260) {
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() { return false; }
}
