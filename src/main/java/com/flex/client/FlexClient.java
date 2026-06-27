package com.flex.client;

import com.flex.client.gui.ClickGui;
import com.flex.client.module.Module;
import com.flex.client.module.ModuleManager;
import com.flex.client.render.ESPRenderer;
import com.flex.client.xray.AntiXrayBypass;
import com.flex.client.xray.ChunkOreScanner;
import com.flex.client.xray.XrayHUD;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class FlexClient implements ClientModInitializer {

    public static final String MOD_ID  = "flexclient";
    public static final String VERSION = "4.0";
    public static FlexClient INSTANCE;

    private static final int GUI_KEY    = GLFW.GLFW_KEY_RIGHT_SHIFT;
    private static boolean wasGuiKey    = false;
    private static boolean wasMouseDown = false;

    private static final int BTN_X = 4, BTN_Y = 4, BTN_W = 72, BTN_H = 14;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        ModuleManager.init();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            client.execute(() -> client.setScreen(new ClickGui()));
            ChunkOreScanner.clearAll();
            AntiXrayBypass.reset();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ChunkOreScanner.clearAll();
            AntiXrayBypass.reset();
        });

        // ── WorldRender: ESP + Tracers + StorageESP ──────────────────
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            try {
                ESPRenderer.render(context);
            } catch (Exception ignored) {}
        });

        // ── HUD Render ────────────────────────────────────────────────
        HudRenderCallback.EVENT.register((DrawContext ctx, float tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.currentScreen != null || client.world == null) return;

            renderWatermark(ctx, client);
            renderModuleList(ctx, client);
            renderNameTags(ctx, client, tickDelta);

            XrayHUD.render(ctx, tickDelta);
            XrayHUD.renderYLevelGuide(ctx, client.textRenderer,
                client.getWindow().getScaledWidth(),
                client.player != null ? client.player.getBlockPos().getY() : 0);
        });

        // ── Client Tick ───────────────────────────────────────────────
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) return;
            XrayHUD.onTick();
            if (client.currentScreen != null) { wasMouseDown = false; wasGuiKey = false; return; }

            long win = client.getWindow().getHandle();

            boolean kd = GLFW.glfwGetKey(win, GUI_KEY) == GLFW.GLFW_PRESS;
            if (kd && !wasGuiKey) client.execute(() -> client.setScreen(new ClickGui()));
            wasGuiKey = kd;

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

        System.out.println("[FlexClient " + VERSION + "] Yuklendi! " + ModuleManager.modules.size() + " modul hazir.");
    }

    // ── Watermark ─────────────────────────────────────────────────────
    private void renderWatermark(DrawContext ctx, MinecraftClient client) {
        ctx.fill(BTN_X - 2, BTN_Y - 2, BTN_X + BTN_W + 2, BTN_Y + BTN_H + 2, 0xBB000015);
        ctx.fill(BTN_X - 2, BTN_Y - 2, BTN_X, BTN_Y + BTN_H + 2, 0xFF00FFCC);
        ctx.drawTextWithShadow(client.textRenderer,
            "\u00a7bFlex\u00a7fClient \u00a774.0",
            BTN_X + 3, BTN_Y + 3, 0xFFFFFFFF);
    }

    // ── Module List (ActiveModulesHUD) ─────────────────────────────────
    private void renderModuleList(DrawContext ctx, MinecraftClient client) {
        int sw = client.getWindow().getScaledWidth();
        int y = 2;
        List<Module> enabled = ModuleManager.modules.stream()
            .filter(Module::isEnabled)
            .sorted((a, b) -> b.getName().length() - a.getName().length())
            .toList();
        for (Module m : enabled) {
            String name = m.getName();
            int w = client.textRenderer.getWidth(name);
            int x = sw - w - 4;
            ctx.fill(x - 2, y - 1, sw - 2, y + 9, 0x88000000);
            ctx.fill(sw - 3, y - 1, sw - 2, y + 9, getCatColor(m.getCategory()));
            ctx.drawTextWithShadow(client.textRenderer, name, x, y, 0xFFFFFFFF);
            y += 11;
        }
    }

    // ── NameTags (2D HUD overlay) ──────────────────────────────────────
    private void renderNameTags(DrawContext ctx, MinecraftClient client, float tickDelta) {
        if (!ModuleManager.isEnabled("NameTags") || client.player == null || client.world == null) return;
        Module nt = ModuleManager.get("NameTags");

        for (Entity entity : client.world.getEntities()) {
            if (entity == client.player) continue;
            if (!(entity instanceof LivingEntity le) || le.isDead()) continue;
            if (!(entity instanceof PlayerEntity)) continue;

            // Project 3D world position to 2D screen
            Vec3d entityPos = entity.getPos().add(0, entity.getHeight() + 0.3, 0);
            double[] screen = worldToScreen(entityPos, client, tickDelta);
            if (screen == null) continue;

            int sx = (int) screen[0], sy = (int) screen[1];
            String name = entity.getName().getString();
            String healthStr = nt.getSetting("health")
                ? String.format(" \u00a7c%.0f\u00a7f HP", ((LivingEntity) entity).getHealth()) : "";
            String distStr = nt.getSetting("distance")
                ? String.format(" \u00a77%.0fm", client.player.distanceTo(entity)) : "";
            String label = "\u00a7f" + name + healthStr + distStr;

            int tw = client.textRenderer.getWidth(label);
            ctx.fill(sx - tw / 2 - 2, sy - 2, sx + tw / 2 + 2, sy + 10, 0xAA000000);
            ctx.drawTextWithShadow(client.textRenderer, label, sx - tw / 2, sy, 0xFFFFFFFF);
        }
    }

    private double[] worldToScreen(Vec3d worldPos, MinecraftClient mc, float tickDelta) {
        try {
            net.minecraft.client.render.Camera camera = mc.gameRenderer.getCamera();
            Vec3d camPos = camera.getPos();
            Vec3d rel = worldPos.subtract(camPos);

            // Simple projection using camera rotation
            float yawRad = (float) Math.toRadians(camera.getYaw());
            float pitchRad = (float) Math.toRadians(camera.getPitch());

            double rx = rel.x * Math.cos(yawRad) + rel.z * Math.sin(yawRad);
            double ry = -rel.y * Math.cos(pitchRad) - (rel.x * Math.sin(yawRad) * Math.sin(pitchRad)) + (rel.z * Math.cos(yawRad) * Math.sin(pitchRad)) * (-1);
            double rz = -rel.x * Math.sin(yawRad) * Math.cos(pitchRad) + rel.y * Math.sin(pitchRad) + rel.z * Math.cos(yawRad) * Math.cos(pitchRad);

            if (rz >= 0) return null; // behind camera

            double fov = mc.options.getFov().getValue();
            double aspect = (double) mc.getWindow().getScaledWidth() / mc.getWindow().getScaledHeight();
            double f = 1.0 / Math.tan(Math.toRadians(fov / 2.0));

            double screenX = (rx / (-rz)) * f / aspect * mc.getWindow().getScaledWidth() / 2.0 + mc.getWindow().getScaledWidth() / 2.0;
            double screenY = (ry / (-rz)) * f * mc.getWindow().getScaledHeight() / 2.0 + mc.getWindow().getScaledHeight() / 2.0;

            return new double[]{screenX, screenY};
        } catch (Exception e) {
            return null;
        }
    }

    private int getCatColor(String cat) {
        return switch (cat) {
            case "Combat"   -> 0xFFFF4466;
            case "Movement" -> 0xFF3399FF;
            case "Render"   -> 0xFF33FF99;
            case "Player"   -> 0xFFFF9922;
            default         -> 0xFF00FFCC;
        };
    }
}
