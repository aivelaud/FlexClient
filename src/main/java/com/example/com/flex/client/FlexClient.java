package com.flex.client;

import com.flex.client.gui.ClickGui;
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

    // HUD buton konumu (sol ust kose)
    private static final int BTN_X = 4;
    private static final int BTN_Y = 4;
    private static final int BTN_W = 60;
    private static final int BTN_H = 14;

    // Tiklanma takibi (her tick degil, sadece basin aninda)
    private static boolean wasMouseDown = false;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        ModuleManager.init();

        // Dunya yuklendikce GUI'yi otomatik ac
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            client.execute(() -> client.setScreen(new ClickGui()));
        });

        // HUD uzerine "FC" butonu ciz
        HudRenderCallback.EVENT.register((DrawContext ctx, float tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();

            // Sadece oyun icindeyken goster (baska ekran acikken gizle)
            if (client.currentScreen != null) return;
            if (client.world == null) return;

            // Buton arkaplan
            ctx.fill(BTN_X, BTN_Y, BTN_X + BTN_W, BTN_Y + BTN_H, 0xCC111122);
            // Sol serit
            ctx.fill(BTN_X, BTN_Y, BTN_X + 2, BTN_Y + BTN_H, 0xFF00FFAA);
            // Yazi
            ctx.drawTextWithShadow(
                client.textRenderer,
                "§aFlex§fClient",
                BTN_X + 5, BTN_Y + 3,
                0xFFFFFFFF
            );
        });

        // Tiklama algilama
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) return;
            if (client.currentScreen != null) {
                wasMouseDown = false;
                return;
            }

            long window = client.getWindow().getHandle();
            boolean isMouseDown = GLFW.glfwGetMouseButton(window,
                GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

            // Sadece basman aninda tetikle (basili tutunca tekrar tekrar acmasin)
            if (isMouseDown && !wasMouseDown) {
                double[] mx = new double[1], my = new double[1];
                GLFW.glfwGetCursorPos(window, mx, my);

                // Ekran olcegi (Retina ekranlar icin)
                double scale = client.getWindow().getScaleFactor();
                double scaledX = mx[0] / scale;
                double scaledY = my[0] / scale;

                // Buton alani icinde mi?
                if (scaledX >= BTN_X && scaledX <= BTN_X + BTN_W
                        && scaledY >= BTN_Y && scaledY <= BTN_Y + BTN_H) {
                    client.setScreen(new ClickGui());
                }
            }

            wasMouseDown = isMouseDown;
        });

        System.out.println("[FlexClient] Yuklendi! Sol ustteki FC butonuna dokun.");
    }
}
