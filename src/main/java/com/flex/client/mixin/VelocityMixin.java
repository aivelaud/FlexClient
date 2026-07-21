package com.flex.client.mixin;

import com.flex.client.module.Module;
import com.flex.client.module.ModuleManager;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.network.ClientPlayNetworkHandler;

/**
 * VelocityMixin — 1.20.1 Fabric uyumlu.
 *
 * EntityVelocityUpdateS2CPacket geldiğinde Velocity/AntiKnockback aktifse
 * hız değerlerini modifiye eder veya iptal eder.
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class VelocityMixin {

    @Inject(
        method = "onEntityVelocityUpdate",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onVelocityUpdate(EntityVelocityUpdateS2CPacket packet, CallbackInfo ci) {
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (packet.getId() != mc.player.getId()) return;

        if (ModuleManager.isEnabled("AntiKnockback")) {
            ci.cancel();
            return;
        }

        if (ModuleManager.isEnabled("Velocity")) {
            Module vel = ModuleManager.get("Velocity");
            String mode = vel.getStringSetting("mode", "Reduce");
            float h = vel.getFloatSetting("horizontal", 0.15f);
            float v = vel.getFloatSetting("vertical", 1.0f);

            if ("Cancel".equals(mode)) {
                ci.cancel();
                return;
            }
            if ("Reduce".equals(mode)) {
                // Packet'taki hız değerleri 8000 = 1.0 birim
                int nx = (int)(packet.getVelocityX() * h);
                int ny = (int)(packet.getVelocityY() * v);
                int nz = (int)(packet.getVelocityZ() * h);
                mc.player.setVelocity(nx / 8000.0, ny / 8000.0, nz / 8000.0);
                ci.cancel();
            }
        }
    }
}
