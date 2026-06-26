package com.flex.client;

import com.flex.client.gui.ClickGui;
import com.flex.client.module.Module;
import com.flex.client.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

public class FlexClient implements ClientModInitializer {
    public static final String MOD_ID = "flexclient";
    public static FlexClient INSTANCE;

    private static final int BTN_X = 4, BTN_Y = 4, BTN_W = 72, BTN_H = 14;
    private static boolean wasMouseDown = false;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        ModuleManager.init();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            client.execute(() -> client.setScreen(new ClickGui()));
        });

        // HUD
        HudRenderCallback.EVENT.register((DrawContext ctx, float tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.currentScreen != null || client.world == null) return;

            // FC Butonu
            ctx.fill(BTN_X, BTN_Y, BTN_X + BTN_W, BTN_Y + BTN_H, 0xCC050510);
            ctx.fill(BTN_X, BTN_Y, BTN_X + 2, BTN_Y + BTN_H, 0xFF00FFAA);
            ctx.drawTextWithShadow(client.textRenderer,
                "\u00a7aFlex\u00a7fClient \u00a772.0",
                BTN_X + 5, BTN_Y + 3, 0xFFFFFFFF);

            // Aktif modul listesi (sag ust kose)
            int activeX = client.getWindow().getScaledWidth() - 4;
            int activeY = 4;
            for (Module m : ModuleManager.modules) {
                if (!m.isEnabled()) continue;
                int tw = client.textRenderer.getWidth(m.getName());
                ctx.fill(activeX - tw - 6, activeY - 1,
                         activeX + 2,       activeY + 9, 0xAA000011);
                ctx.fill(activeX,           activeY - 1,
                         activeX + 2,       activeY + 9, 0xFF00FFAA);
                ctx.drawTextWithShadow(client.textRenderer,
                    m.getName(), activeX - tw - 3, activeY, 0xFFFFFFFF);
                activeY += 11;
            }
        });

        // Tiklama algilama
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) return;
            if (client.currentScreen != null) { wasMouseDown = false; return; }

            long win = client.getWindow().getHandle();
            boolean down = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

            if (down && !wasMouseDown) {
                double[] mx = new double[1], my = new double[1];
                GLFW.glfwGetCursorPos(win, mx, my);
                double scale = client.getWindow().getScaleFactor();
                double sx = mx[0] / scale, sy = my[0] / scale;
                if (sx >= BTN_X && sx <= BTN_X + BTN_W && sy >= BTN_Y && sy <= BTN_Y + BTN_H) {
                    client.setScreen(new ClickGui());
                }
            }
            wasMouseDown = down;
        });

        System.out.println("[FlexClient 2.0] Yuklendi!");
    }
}
