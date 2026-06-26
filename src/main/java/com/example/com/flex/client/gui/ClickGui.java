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
    private static final int COL_W   = 130;
    private static final int MOD_H   = 22;
    private static final int CAT_H   = 24;
    private static final int SUB_H   = 18;
    private static final int COL_X0  = 6;
    private static final int COL_Y0  = 14;
    private static final int COL_GAP = 5;

    private static final int[] CAT_COLORS = {
        0xFFFF4455, // Combat   - kirmizi
        0xFF44AAFF, // Movement - mavi
        0xFF44FF88, // Render   - yesil
        0xFFFFAA00  // Player   - turuncu
    };

    private double lastTX = -1, lastTY = -1;
    private int feedbackTicks = 0;

    public ClickGui() {
        super(Text.literal("FlexClient"));
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        // Koyu degrade arka plan
        ctx.fillGradient(0, 0, this.width, this.height, 0xEE000011, 0xFF000022);

        // Dokunma parlama efekti
        if (feedbackTicks > 0) {
            feedbackTicks--;
            int a = Math.min(feedbackTicks * 12, 140);
            ctx.fill((int)lastTX - 20, (int)lastTY - 20,
                     (int)lastTX + 20, (int)lastTY + 20,
                     (a << 24) | 0x0000FFBB);
        }

        int cx = COL_X0;
        for (int ci = 0; ci < CATEGORIES.size(); ci++) {
            String cat = CATEGORIES.get(ci);
            int catColor = CAT_COLORS[ci];
            List<Module> catMods = ModuleManager.getByCategory(cat);

            // Kategori baslik kutusu
            ctx.fill(cx, COL_Y0, cx + COL_W, COL_Y0 + CAT_H, 0xFF0E0E20);
            ctx.fill(cx, COL_Y0, cx + 3, COL_Y0 + CAT_H, catColor);
            ctx.fill(cx, COL_Y0 + CAT_H - 1, cx + COL_W, COL_Y0 + CAT_H, catColor & 0x55FFFFFF);
            ctx.drawTextWithShadow(textRenderer, cat, cx + 7, COL_Y0 + (CAT_H / 2) - 4, catColor);

            // Aktif / toplam badge
            long activeCount = catMods.stream().filter(Module::isEnabled).count();
            String badge = activeCount + "/" + catMods.size();
            int bw = textRenderer.getWidth(badge) + 6;
            ctx.fill(cx + COL_W - bw - 2, COL_Y0 + 4, cx + COL_W - 2, COL_Y0 + CAT_H - 4, 0xFF161630);
            ctx.drawTextWithShadow(textRenderer, badge, cx + COL_W - bw + 1, COL_Y0 + (CAT_H / 2) - 4, catColor);

            int cy = COL_Y0 + CAT_H + 2;
            for (Module m : catMods) {
                boolean hov = mx >= cx && mx <= cx + COL_W && my >= cy && my <= cy + MOD_H;
                boolean on  = m.isEnabled();

                // Arkaplan
                int bg = on  ? 0xFF121230
                       : hov ? 0xFF1C1C38
                       :       0xFF0B0B1E;
                ctx.fill(cx, cy, cx + COL_W, cy + MOD_H, bg);

                // Sol serit
                ctx.fill(cx, cy, cx + 3, cy + MOD_H, on ? catColor : 0xFF2A2A44);

                // Alt ince cizgi
                ctx.fill(cx + 3, cy + MOD_H - 1, cx + COL_W, cy + MOD_H, 0xFF181830);

                // Modul adi
                int tc = on ? 0xFFFFFFFF : 0xFF7777AA;
                ctx.drawTextWithShadow(textRenderer, m.getName(), cx + 8, cy + (MOD_H / 2) - 4, tc);

                // ON gostergesi
                if (on) {
                    ctx.drawTextWithShadow(textRenderer, "\u00a7a\u25a0", cx + COL_W - 11, cy + (MOD_H / 2) - 4, 0xFF00FFAA);
                }

                // KillAura alt ayarlari
                if (m.getName().equals("KillAura") && on) {
                    cy += MOD_H;
                    renderSubToggle(ctx, mx, my, cx, cy, "Moblara vur",     m.isHitMonsters());
                    cy += SUB_H;
                    renderSubToggle(ctx, mx, my, cx, cy, "Oyunculara vur",  m.isHitPlayers());
                    cy += SUB_H;
                    renderSubToggle(ctx, mx, my, cx, cy, "Hayvanlar",        m.isHitAnimals());
                    cy += SUB_H;
                    renderSubToggle(ctx, mx, my, cx, cy, "Otomatik don",     m.isRotate());
                    cy += SUB_H + 2;
                    continue;
                }

                cy += MOD_H + 1;
            }

            cx += COL_W + COL_GAP;
        }

        // Ust watermark cubugu
        long enabled = ModuleManager.modules.stream().filter(Module::isEnabled).count();
        ctx.fill(0, 0, this.width, 12, 0xBB000014);
        ctx.drawTextWithShadow(textRenderer,
            "\u00a7a\u00a7lFlex\u00a7f\u00a7lClient \u00a772.0  \u00a77|\u00a7f  " + enabled + "/" + ModuleManager.modules.size() + " mod aktif",
            4, 2, 0xFFFFFFFF);

        // Alt bilgi cubugu
        ctx.fill(0, this.height - 12, this.width, this.height, 0xBB000014);
        ctx.drawTextWithShadow(textRenderer,
            "\u00a77Tikla: Ac/Kapat   ESC: Kapat   \u00a7aFlexClient v2.0 \u00a77by Flex",
            4, this.height - 9, 0xFF888899);

        super.render(ctx, mx, my, delta);
    }

    private void renderSubToggle(DrawContext ctx, int mx, int my, int cx, int cy, String label, boolean val) {
        boolean hov = mx >= cx + 8 && mx <= cx + COL_W && my >= cy && my <= cy + SUB_H;
        ctx.fill(cx + 8, cy, cx + COL_W, cy + SUB_H, hov ? 0xFF1E1E40 : 0xFF0E0E28);
        String check = val ? "\u00a7a[\u2714]\u00a7f " : "\u00a77[\u2718]\u00a78";
        ctx.drawTextWithShadow(textRenderer, check + label, cx + 14, cy + (SUB_H / 2) - 4, 0xFFAAAAAA);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return super.mouseClicked(mx, my, btn);
        lastTX = mx; lastTY = my; feedbackTicks = 10;

        int cx = COL_X0;
        for (String cat : CATEGORIES) {
            List<Module> catMods = ModuleManager.getByCategory(cat);
            int cy = COL_Y0 + CAT_H + 2;

            for (Module m : catMods) {
                if (mx >= cx && mx <= cx + COL_W && my >= cy && my <= cy + MOD_H) {
                    m.toggle();
                    return true;
                }

                if (m.getName().equals("KillAura") && m.isEnabled()) {
                    cy += MOD_H;
                    if (mx >= cx + 8 && mx <= cx + COL_W && my >= cy && my <= cy + SUB_H) { m.setSetting("hitMonsters", !m.isHitMonsters()); return true; }
                    cy += SUB_H;
                    if (mx >= cx + 8 && mx <= cx + COL_W && my >= cy && my <= cy + SUB_H) { m.setSetting("hitPlayers",  !m.isHitPlayers());  return true; }
                    cy += SUB_H;
                    if (mx >= cx + 8 && mx <= cx + COL_W && my >= cy && my <= cy + SUB_H) { m.setSetting("hitAnimals",  !m.isHitAnimals());  return true; }
                    cy += SUB_H;
                    if (mx >= cx + 8 && mx <= cx + COL_W && my >= cy && my <= cy + SUB_H) { m.setSetting("rotate",      !m.isRotate());      return true; }
                    cy += SUB_H + 2;
                    continue;
                }

                cy += MOD_H + 1;
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
