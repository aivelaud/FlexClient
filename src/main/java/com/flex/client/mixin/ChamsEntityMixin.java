package com.flex.client.mixin;

import com.flex.client.module.ModuleManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ChamsEntityMixin — Depth test'i devre dışı bırakarak varlıkları duvar arkasından gösterir.
 * Sadece Chams aktifken çalışır.
 */
@Mixin(EntityRenderDispatcher.class)
public class ChamsEntityMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private <E extends Entity> void preEntityRender(E entity, double x, double y, double z,
            float yaw, float tickDelta, MatrixStack matrices,
            VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (!ModuleManager.isEnabled("Chams")) return;
        if (!shouldChams(entity)) return;
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private <E extends Entity> void postEntityRender(E entity, double x, double y, double z,
            float yaw, float tickDelta, MatrixStack matrices,
            VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (!ModuleManager.isEnabled("Chams")) return;
        if (!shouldChams(entity)) return;
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
    }

    private boolean shouldChams(Entity entity) {
        if (entity == null) return false;
        com.flex.client.module.Module m = ModuleManager.get("Chams");
        if (m == null) return false;
        if (m.getSetting("players") && entity instanceof PlayerEntity) return true;
        if (m.getSetting("mobs") && entity instanceof LivingEntity && !(entity instanceof PlayerEntity)) return true;
        return false;
    }
}
