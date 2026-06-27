package com.flex.client.xray;

import com.flex.client.module.ModuleManager;
import com.flex.client.render.RenderUtils;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

import java.util.Collection;

/**
 * XrayRealOreRenderer — AntiXray bypass'ın "gerçek" olarak tanımladığı cevherleri
 * sarı kutu ile vurgular.
 *
 * Yalnızca Xray modülü aktifken ve AntiXray bypass etkinken çalışır.
 * Oyuncu etrafındaki ~4 chunk içindeki gerçek cevherlere sarı outline çizer.
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

        MatrixStack matrices = ctx.matrixStack();
        Camera camera = ctx.camera();
        Vec3d camPos = camera.getPos();

        BlockPos playerPos = mc.player.getBlockPos();
        Collection<BlockPos> realPositions = AntiXrayBypass.getRealPositionsNear(playerPos, CHUNK_RADIUS);

        int drawn = 0;
        for (BlockPos pos : realPositions) {
            if (drawn >= MAX_BLOCKS_TO_RENDER) break;

            Box box = new Box(
                pos.getX() - camPos.x,
                pos.getY() - camPos.y,
                pos.getZ() - camPos.z,
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
