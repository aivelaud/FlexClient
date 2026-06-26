package com.flex.client.gui;

import com.flex.client.module.Module;
import com.flex.client.module.ModuleManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import java.util.Arrays;
import java.util.List;

public class ClickGui extends Screen {

    private static final List<String> CATEGORIES = Arrays.asList("Combat", "Movement", "Render", "Player");
    private static final int COL_W   = 150;
    private static final int COL_H   = 26;
    private static final int SUB_H   = 22;
    private static final int COL_X0  = 8;
    private static final int COL_Y0  = 8;
    private static final int COL_GAP = 6;

    // Kategori renkleri
    private static final int[] CAT_COLORS = {
        0xFFFF4444, // Combat  - kirmizi
        0xFF44AAFF, // Movement - mavi
        0xFF44FF88, // Render  - yesil
        0xFFFFAA00  // Player  - turuncu
    };

    private double lastTX = -1, lastTY = -1;
    private int feedbackTicks = 0;

    public ClickGui() {
        super(Text.literal("FlexClient"));
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        // Koyu degrade arka plan
        ctx.fillGradient(0, 0, this.width, this.height, 0xDD000011, 0xEE000022);

        // Dokunma parlama efekti
        if (feedbackTicks > 0) {
            feedbackTicks--;
            int a = Math.min(feedbackTicks * 15, 160);
            ctx.fill((int)lastTX - 18, (int)lastTY - 18,
                     (int)lastTX + 18, (int)lastTY + 18,
                     (a << 24) | 0x0000FFAA);
        }

        int cx = COL_X0;
        for (int ci = 0; ci < CATEGORIES.size(); ci++) {
            String cat = CATEGORIES.get(ci);
            int catColor = CAT_COLORS[ci];

            // Kategori baslik
            ctx.fill(cx, COL_Y0, cx + COL_W, COL_Y0 + COL_H, 0xFF0D0D1F);
            ctx.fill(cx, COL_Y0, cx + 3, COL_Y0 + COL_H, catColor);
            ctx.drawTextWithShadow(textRenderer, cat, cx + 8, COL_Y0 + (COL_H/2) - 4, catColor);

            // Modul sayisi badge
            int count = ModuleManager.getByCategory(cat).size();
            String badge = String.valueOf(count);
            int bw = textRenderer.getWidth(badge) + 6;
            ctx.fill(cx + COL_W - bw - 2, COL_Y0 + 5, cx + COL_W - 2, COL_Y0 + COL_H - 5, 0xFF1A1A3A);
            ctx.drawTextWithShadow(textRenderer, badge, cx + COL_W - bw + 1, COL_Y0 + (COL_H/2) - 4, catColor);

            int cy = COL_Y0 + COL_H + 2;
            for (Module m : ModuleManager.modules) {
                if (!m.getCategory().equals(cat)) continue;

                boolean hov = mx >= cx && mx <= cx + COL_W && my >= cy && my <= cy + COL_H;
                boolean on  = m.isEnabled();

                int bg = on  ? 0xFF111133
                       : hov ? 0xFF1A1A2E
                       :       0xFF0A0A1A;

                ctx.fill(cx, cy, cx + COL_W, cy + COL_H, bg);

                // Sol serit
                int stripColor = on ? catColor : 0xFF333344;
                ctx.fill(cx, cy, cx + 3, cy + COL_H, stripColor);

                // Alt cizgi (ince ayirici)
                ctx.fill(cx + 3, cy + COL_H - 1, cx + COL_W, cy + COL_H, 0xFF1A1A2E);

                int tc = on ? 0xFFFFFFFF : 0xFF888899;
                ctx.drawTextWithShadow(textRenderer, m.getName(), cx + 8, cy + (COL_H/2) - 4, tc);

                if (on) {
                    ctx.drawTextWithShadow(textRenderer, "§a■", cx + COL_W - 12, cy + (COL_H/2) - 4, 0xFF00FFAA);
                }

                // KillAura alt ayarlari
                if (m.getName().equals("KillAura") && on) {
                    cy += COL_H + 1;
                    renderSubToggle(ctx, mx, my, cx, cy, "Hayvanlara vur", m.isHitAnimals());
                    cy += SUB_H;
                    renderSubToggle(ctx, mx, my, cx, cy, "Oyunculara vur", m.isHitPlayers());
                    cy += SUB_H + 1;
                    continue;
                }

                cy += COL_H + 1;
            }
            cx += COL_W + COL_GAP;
        }

        // Alt bilgi cubu
        String hint = "§7[FC] §fTikla: Ac/Kapat  |  ESC: Kapat  |  §aFlexClient v2.0";
        ctx.fill(0, this.height - 14, this.width, this.height, 0xDD000011);
        ctx.drawTextWithShadow(textRenderer, hint, 4, this.height - 10, 0xFFAAAAAA);

        // Ust watermark
        ctx.fill(0, 0, this.width, 12, 0xAA000011);
        ctx.drawTextWithShadow(textRenderer, "§a§lFlex§f§lClient §72.0 §f| §7" + ModuleManager.modules.stream().filter(Module::isEnabled).count() + " aktif modul", 4, 2, 0xFFFFFFFF);

        super.render(ctx, mx, my, delta);
    }

    private void renderSubToggle(DrawContext ctx, int mx, int my, int cx, int cy, String label, boolean val) {
        boolean hov = mx >= cx + 10 && mx <= cx + COL_W && my >= cy && my <= cy + SUB_H;
        ctx.fill(cx + 10, cy, cx + COL_W, cy + SUB_H, hov ? 0xFF1E1E3E : 0xFF101024);
        String check = val ? "§a[✔] §f" : "§7[✘] §8";
        ctx.drawTextWithShadow(textRenderer, check + label, cx + 16, cy + (SUB_H/2) - 4, 0xFFAAAAAA);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return super.mouseClicked(mx, my, btn);

        lastTX = mx; lastTY = my; feedbackTicks = 10;

        int cx = COL_X0;
        for (String cat : CATEGORIES) {
            int cy = COL_Y0 + COL_H + 2;
            for (Module m : ModuleManager.modules) {
                if (!m.getCategory().equals(cat)) continue;

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
                    cy += SUB_H;
                    if (mx >= cx + 10 && mx <= cx + COL_W && my >= cy && my <= cy + SUB_H) {
                        m.setSetting("hitPlayers", !m.isHitPlayers());
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

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 || keyCode == 260) { this.close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean shouldPause() { return false; }
}
