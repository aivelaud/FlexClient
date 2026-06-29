package com.flex.client.xray;

import com.flex.client.module.ModuleManager;
import com.flex.client.render.RenderUtils;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

import java.util.Collection;

/**
 * XrayRealOreRenderer — Gerçek cevherleri sarı kutu ile vurgular.
 *
 * V4 değişikliği:
 *  AntiXrayBypass.getRealPositionsNear() → yalnızca listeyi sağlar.
 *  Her blok aynı AntiXrayFilter.isFake() filtresiyle kontrol edilir.
 *  Bu sayede ESP render ve HUD listesi TUTARLI olur:
 *  Listede ne varsa ekranda da o görünür.
 *
 * V3 hatası: Filtre yalnızca listeye uygulanıyordu, ESP render
 * filtresiz çalışıyordu → tüm dünya diamond/iron rengine bürünüyordu.
 */
public class XrayRealOreRenderer {

    private static final int COLOR_REAL_ORE_OUTLINE = 0xCCFFDD00; // opak sarı
    private static final int COLOR_REAL_ORE_FILL    = 0x22FFDD00; // şeffaf sarı dolgu
    private static final int MAX_BLOCKS_TO_RENDER   = 400;
    private static final int CHUNK_RADIUS           = 4;

    public static void render(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        if (!ModuleManager.isEnabled("Xray")) return;
        if (!AntiXrayBypass.isBypassActive()) return;

        ClientWorld world = mc.world;
        MatrixStack matrices = ctx.matrixStack();
        Camera camera = ctx.camera();
        Vec3d camPos = camera.getPos();

        BlockPos playerPos = mc.player.getBlockPos();

        // AntiXrayBypass'ın "gerçek" aday listesini al
        Collection<BlockPos> candidates = AntiXrayBypass.getRealPositionsNear(playerPos, CHUNK_RADIUS);

        int drawn = 0;
        for (BlockPos pos : candidates) {
            if (drawn >= MAX_BLOCKS_TO_RENDER) break;

            // ── V4 ESP FİLTRESİ ───────────────────────────────────────────────
            // Listede ne varsa render'da da aynı filtre uygulanır.
            // Bu sayede "tüm dünya diamond" sorunu ortadan kalkar.
            try {
                Block block = world.getBlockState(pos).getBlock();
                if (AntiXrayFilter.isFake(world, pos, block)) continue;
            } catch (Exception ignored) {
                continue; // Erişilemeyen blok → atla
            }
            // ─────────────────────────────────────────────────────────────────

            Box box = new Box(
                pos.getX()       - camPos.x,
                pos.getY()       - camPos.y,
                pos.getZ()       - camPos.z,
                pos.getX() + 1.0 - camPos.x,
                pos.getY() + 1.0 - camPos.y,
                pos.getZ() + 1.0 - camPos.z
            );

            RenderUtils.drawBoxOutline(matrices, box, COLOR_REAL_ORE_OUTLINE, 2.0f);
            RenderUtils.drawBoxFilled(matrices, box, COLOR_REAL_ORE_FILL);
            drawn++;
        }
    }
}
