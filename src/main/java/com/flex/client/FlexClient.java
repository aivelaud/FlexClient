package com.flex.client;

import com.flex.client.gui.ClickGui;
import com.flex.client.module.Module;
import com.flex.client.module.ModuleManager;
import com.flex.client.xray.AntiXrayBypass;
import com.flex.client.xray.ChunkOreScanner;
import com.flex.client.xray.XrayHUD;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

public class FlexClient implements ClientModInitializer {

    public static final String MOD_ID   = "flexclient";
    public static final String VERSION  = "3.0";
    public static FlexClient INSTANCE;

    // Kısayol tuşları
    private static final int GUI_KEY    = GLFW.GLFW_KEY_RIGHT_SHIFT;
    private static boolean wasGuiKey    = false;
    private static boolean wasMouseDown = false;

    // HUD butonu (PojavLauncher dokunmatik)
    private static final int BTN_X = 4, BTN_Y = 4, BTN_W = 72, BTN_H = 14;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        ModuleManager.init();

        // Dünyaya girildiğinde
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            client.execute(() -> client.setScreen(new ClickGui()));
            ChunkOreScanner.clearAll();
            AntiXrayBypass.reset();
        });

        // Dünyadan ayrılırken cache temizle
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ChunkOreScanner.clearAll();
            AntiXrayBypass.reset();
        });

        // ── HUD Render ──────────────────────────────────────────
        HudRenderCallback.EVENT.register((DrawContext ctx, float tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.currentScreen != null || client.world == null) return;

            renderWatermark(ctx, client);
            renderModuleList(ctx, client);

            XrayHUD.render(ctx, tickDelta);
            XrayHUD.renderYLevelGuide(ctx, client.textRenderer,
                client.getWindow().getScaledWidth(),
                client.player != null ? client.player.getBlockPos().getY() : 0);
        });

        // ── Tick ────────────────────────────────────────────────
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) return;
            XrayHUD.onTick();
            if (client.currentScreen != null) { wasMouseDown = false; wasGuiKey = false; return; }

            long win = client.getWindow().getHandle();

            // Sağ Shift → GUI aç
            boolean kd = GLFW.glfwGetKey(win, GUI_KEY) == GLFW.GLFW_PRESS;
            if (kd && !wasGuiKey) client.execute(() -> client.setScreen(new ClickGui()));
            wasGuiKey = kd;

            // Dokunmatik: FC butonuna tıklama
            boolean md = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            if (md && !wasMouseDown) {
                double[] mxArr = new double[1], myArr = new double[1];
                GLFW.glfwGetCursorPos(win, mxArr, myArr);
                double scale = client.getWindow().getScaleFactor();
                double sx = mxArr[0] / scale, sy = myArr[0] / scale;
                if (sx >= BTN_X && sx <= BTN_X + BTN_W && sy >= BTN_Y && sy <= BTN_Y + BTN_H) {
                    client.execute(() -> client.setScreen(new ClickGui()));
                }
            }
            wasMouseDown = md;
        });

        System.out.println("[FlexClient " + VERSION + "] Yuklendi! GUI: Sag Shift | " + ModuleManager.modules.size() + " modul hazir");
    }

    // ── Watermark HUD ────────────────────────────────────────────
    private void renderWatermark(DrawContext ctx, MinecraftClient client) {
        ctx.fill(BTN_X - 2, BTN_Y - 2, BTN_X + BTN_W + 2, BTN_Y + BTN_H + 2, 0xBB000015);
        ctx.fill(BTN_X - 2, BTN_Y - 2, BTN_X, BTN_Y + BTN_H + 2, 0xFF00FFCC);
        ctx.drawTextWithShadow(client.textRenderer,
            "\u00a7bFlex\u00a7fClient \u00a773.0",
            BTN_X + 3, BTN_Y + 3, 0xFFFFFFFF);
    }

    // ── Sağ üst aktif modül listesi ──────────────────────────────
    private void renderModuleList(DrawContext ctx, MinecraftClient client) {
        int x = client.getWindow().getScaledWidth() - 4;
        int y = 4;
        for (Module m : ModuleManager.modules) {
            if (!m.isEnabled()) continue;
            int tw = client.textRenderer.getWidth(m.getName());
            ctx.fill(x - tw - 7, y - 1, x + 3, y + 10, 0xAA000012);
            ctx.fill(x + 1, y - 1, x + 3, y + 10, 0xFF00FFCC);
            ctx.drawTextWithShadow(client.textRenderer, m.getName(), x - tw - 3, y, 0xFFFFFFFF);
            y += 12;
        }
    }
}
