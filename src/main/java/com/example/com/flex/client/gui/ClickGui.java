package com.flex.client.gui;

import com.flex.client.module.Module;
import com.flex.client.module.ModuleManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import java.util.Arrays;
import java.util.List;

public class ClickGui extends Screen {
    private static final List<String> CATEGORIES = Arrays.asList("Combat","Movement","Render");
    private static final int COL_W = 120, COL_H = 20, COL_X_START = 10, COL_Y_START = 10;

    public ClickGui() {
        super(Text.literal("FlexClient"));
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fillGradient(0, 0, this.width, this.height, 0xAA000000, 0xAA000000);
        int cx = COL_X_START;
        for (String cat : CATEGORIES) {
            ctx.fill(cx, COL_Y_START, cx + COL_W, COL_Y_START + COL_H, 0xFF1a1a2e);
            ctx.drawTextWithShadow(textRenderer, cat, cx + 4, COL_Y_START + 6, 0xFF9900);
            int cy = COL_Y_START + COL_H + 2;
            for (Module m : ModuleManager.modules) {
                if (!m.getCategory().equals(cat)) continue;
                boolean hovered = mx >= cx && mx <= cx+COL_W && my >= cy && my <= cy+COL_H;
                int bg = m.isEnabled() ? 0xFF16213e : (hovered ? 0xFF2a2a3e : 0xFF0f0f1e);
                int textColor = m.isEnabled() ? 0xFF00FFAA : 0xFFCCCCCC;
                ctx.fill(cx, cy, cx+COL_W, cy+COL_H, bg);
                ctx.fill(cx, cy, cx+2, cy+COL_H, m.isEnabled() ? 0xFF00FFAA : 0xFF444444);
                ctx.drawTextWithShadow(textRenderer, m.getName(), cx+6, cy+6, textColor);
                if (m.getName().equals("KillAura") && m.isEnabled()) {
                    cy += COL_H + 1;
                    boolean anim = m.isHitAnimals();
                    boolean subHov = mx>=cx+10 && mx<=cx+COL_W && my>=cy && my<=cy+16;
                    ctx.fill(cx+10, cy, cx+COL_W, cy+16, subHov ? 0xFF1e1e3e : 0xFF141428);
                    ctx.drawTextWithShadow(textRenderer, (anim?"§a✔":"§7✘")+" Hayvanlara vur", cx+14, cy+4, 0xFFAAAAAA);
                }
                cy += COL_H + 1;
            }
            cx += COL_W + 5;
        }
        super.render(ctx, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return super.mouseClicked(mx, my, btn);
        int cx = COL_X_START;
        for (String cat : CATEGORIES) {
            int cy = COL_Y_START + COL_H + 2;
            for (Module m : ModuleManager.modules) {
                if (!m.getCategory().equals(cat)) continue;
                if (mx>=cx && mx<=cx+COL_W && my>=cy && my<=cy+COL_H) {
                    m.toggle(); return true;
                }
                if (m.getName().equals("KillAura") && m.isEnabled()) {
                    cy += COL_H + 1;
                    if (mx>=cx+10 && mx<=cx+COL_W && my>=cy && my<=cy+16) {
                        m.setSetting("hitAnimals", !m.isHitAnimals()); return true;
                    }
                }
                cy += COL_H + 1;
            }
            cx += COL_W + 5;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean shouldPause() { return false; }
}
