package com.flex.client.render;

import com.flex.client.module.Module;
import com.flex.client.module.ModuleManager;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.entity.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;

import java.util.List;

/**
 * ESPRenderer — WorldRenderEvents.AFTER_ENTITIES'e bağlı render sistemi.
 * ESP (Entity kutu), Tracers (çizgi), StorageESP (sandık vb.), NameTags, Chams renkli outline
 */
public class ESPRenderer {

    // Renk sabitleri (ARGB)
    private static final int COLOR_PLAYER   = 0xBB00FFFF;
    private static final int COLOR_MOB      = 0xBBFF4444;
    private static final int COLOR_ANIMAL   = 0xBB44FF44;
    private static final int COLOR_CHEST    = 0xBBFFAA00;
    private static final int COLOR_BARREL   = 0xBBFF8833;
    private static final int COLOR_SHULKER  = 0xBBAA44FF;
    private static final int COLOR_FURNACE  = 0xBBFF6600;
    private static final int COLOR_TRACER_P = 0xBB00FFFF;
    private static final int COLOR_TRACER_C = 0xBBFFAA00;
    private static final int COLOR_OUTLINE  = 0xFF00FF88;
    private static final int COLOR_FILL     = 0x2200FF88;

    public static void render(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        MatrixStack matrices = ctx.matrixStack();
        Camera camera = ctx.camera();
        Vec3d camPos = camera.getPos();

        boolean espEnabled       = ModuleManager.isEnabled("ESP");
        boolean tracersEnabled   = ModuleManager.isEnabled("Tracers");
        boolean storageEnabled   = ModuleManager.isEnabled("StorageESP");
        boolean nameTagsEnabled  = ModuleManager.isEnabled("NameTags");

        if (!espEnabled && !tracersEnabled && !storageEnabled && !nameTagsEnabled) return;

        Module espMod     = ModuleManager.get("ESP");
        Module tracerMod  = ModuleManager.get("Tracers");
        Module storageMod = ModuleManager.get("StorageESP");
        Module nameMod    = ModuleManager.get("NameTags");

        // Camera forward direction for tracers
        Vec3d forward = new Vec3d(0, 0, 1).rotateY(-(float) Math.toRadians(mc.player.getYaw()))
                .rotateX((float) Math.toRadians(mc.player.getPitch()));

        // ── ENTITY ESP + TRACERS + NAMETAGS ──────────────────────
        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player) continue;
            if (!(entity instanceof LivingEntity le) || le.isDead()) continue;

            boolean isPlayer = entity instanceof PlayerEntity;
            boolean isMob    = entity instanceof Monster;
            boolean isAnimal = entity instanceof AnimalEntity;

            // ESP
            if (espEnabled && espMod != null) {
                boolean show = (isPlayer && espMod.getSetting("showPlayers"))
                            || (isMob    && espMod.getSetting("showMobs"))
                            || (isAnimal && espMod.getSetting("showAnimals"));
                if (show) {
                    Box box = entity.getBoundingBox().offset(-camPos.x, -camPos.y, -camPos.z);
                    int color = isPlayer ? COLOR_PLAYER : isMob ? COLOR_MOB : COLOR_ANIMAL;
                    String mode = espMod.getStringSetting("mode", "Box");
                    if ("Box".equals(mode) || "Outline".equals(mode)) {
                        RenderUtils.drawBoxOutline(matrices, box, color, 1.5f);
                        if ("Box".equals(mode)) {
                            int fillColor = (color & 0x00FFFFFF) | 0x22000000;
                            RenderUtils.drawBoxFilled(matrices, box, fillColor);
                        }
                    } else if ("Corner".equals(mode)) {
                        drawCornerBox(matrices, box, color);
                    }
                }
            }

            // TRACERS
            if (tracersEnabled && tracerMod != null) {
                boolean show = (isPlayer && tracerMod.getSetting("players"))
                            || (isMob    && tracerMod.getSetting("mobs"));
                if (show) {
                    Vec3d entityCenter = entity.getBoundingBox().getCenter().subtract(camPos);
                    Vec3d tracerOrigin;
                    String origin = tracerMod.getStringSetting("origin", "Eyes");
                    if ("Crosshair".equals(origin)) {
                        tracerOrigin = forward.multiply(0.1);
                    } else {
                        tracerOrigin = Vec3d.ZERO.add(0, 0, 0);
                    }
                    int tc = isPlayer ? COLOR_TRACER_P : COLOR_MOB;
                    RenderUtils.drawTracer(matrices, tracerOrigin, entityCenter, tc, 1.0f);
                }
            }

            // NAMETAGS (rendered via HUD in FlexClient, skip here)
        }

        // ── STORAGE ESP ───────────────────────────────────────────
        if (storageEnabled && storageMod != null) {
            ClientWorld clientWorld = mc.world;
            int playerCX = (int) mc.player.getX() >> 4;
            int playerCZ = (int) mc.player.getZ() >> 4;
            int radius = 8;
            for (int cx = playerCX - radius; cx <= playerCX + radius; cx++) {
                for (int cz = playerCZ - radius; cz <= playerCZ + radius; cz++) {
                    WorldChunk chunk = clientWorld.getChunk(cx, cz, ChunkStatus.FULL, false);
                    if (chunk == null) continue;
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        boolean show = false;
                        int color = COLOR_CHEST;

                        if (be instanceof ChestBlockEntity && storageMod.getSetting("chests")) {
                            show = true; color = COLOR_CHEST;
                        } else if (be instanceof BarrelBlockEntity && storageMod.getSetting("barrels")) {
                            show = true; color = COLOR_BARREL;
                        } else if (be instanceof ShulkerBoxBlockEntity && storageMod.getSetting("shulkers")) {
                            show = true; color = COLOR_SHULKER;
                        } else if (be instanceof AbstractFurnaceBlockEntity && storageMod.getSetting("furnaces")) {
                            show = true; color = COLOR_FURNACE;
                        } else if (be instanceof HopperBlockEntity && storageMod.getSetting("droppers")) {
                            show = true; color = COLOR_BARREL;
                        }

                        if (!show) continue;
                        Vec3d bePos = Vec3d.ofCenter(be.getPos());
                        Box beBox = new Box(bePos.x - 0.5, bePos.y - 0.5, bePos.z - 0.5,
                                            bePos.x + 0.5, bePos.y + 0.5, bePos.z + 0.5)
                                .offset(-camPos.x, -camPos.y, -camPos.z);
                        RenderUtils.drawBoxOutline(matrices, beBox, color, 1.5f);

                        if (tracersEnabled && tracerMod != null && tracerMod.getSetting("chests")) {
                            Vec3d beCenter = bePos.subtract(camPos);
                            RenderUtils.drawTracer(matrices, Vec3d.ZERO, beCenter, COLOR_TRACER_C, 0.8f);
                        }
                    }
                }
            }
        }
    }

    /**
     * Corner-box: sadece köşelerde kısa çizgiler çizer (Doomsday tarzı).
     */
    private static void drawCornerBox(MatrixStack matrices, Box box, int color) {
        float len = (float)((box.maxX - box.minX) * 0.25);
        float hlen = (float)((box.maxY - box.minY) * 0.25);
        float[] c = RenderUtils.argbToFloat(color);

        com.mojang.blaze3d.systems.RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        com.mojang.blaze3d.systems.RenderSystem.lineWidth(2.0f);
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buf = tessellator.getBuffer();
        buf.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        Matrix4f m = matrices.peek().getPositionMatrix();

        double x0=box.minX, y0=box.minY, z0=box.minZ;
        double x1=box.maxX, y1=box.maxY, z1=box.maxZ;

        // Bottom corners
        cornerLines(buf, m, x0, y0, z0,  len,  hlen,  len, c);
        cornerLines(buf, m, x1, y0, z0, -len,  hlen,  len, c);
        cornerLines(buf, m, x0, y0, z1,  len,  hlen, -len, c);
        cornerLines(buf, m, x1, y0, z1, -len,  hlen, -len, c);
        // Top corners
        cornerLines(buf, m, x0, y1, z0,  len, -hlen,  len, c);
        cornerLines(buf, m, x1, y1, z0, -len, -hlen,  len, c);
        cornerLines(buf, m, x0, y1, z1,  len, -hlen, -len, c);
        cornerLines(buf, m, x1, y1, z1, -len, -hlen, -len, c);

        tessellator.draw();
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    private static void cornerLines(BufferBuilder buf, Matrix4f m,
            double x, double y, double z, float dx, float dy, float dz, float[] c) {
        buf.vertex(m,(float)x,(float)y,(float)z).color(c[0],c[1],c[2],c[3]).next();
        buf.vertex(m,(float)(x+dx),(float)y,(float)z).color(c[0],c[1],c[2],c[3]).next();
        buf.vertex(m,(float)x,(float)y,(float)z).color(c[0],c[1],c[2],c[3]).next();
        buf.vertex(m,(float)x,(float)(y+dy),(float)z).color(c[0],c[1],c[2],c[3]).next();
        buf.vertex(m,(float)x,(float)y,(float)z).color(c[0],c[1],c[2],c[3]).next();
        buf.vertex(m,(float)x,(float)y,(float)(z+dz)).color(c[0],c[1],c[2],c[3]).next();
    }
}
