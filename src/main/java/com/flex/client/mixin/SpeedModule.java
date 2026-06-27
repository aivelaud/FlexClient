package com.flex.client.mixin;

import com.flex.client.module.Module;
import com.flex.client.module.ModuleManager;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * SpeedModule — Rubber-band bypass için hareket paketi manipülasyonu.
 *
 * İki mod:
 *  • Strafe / Ground — Step injection: büyük hareketi MAX_SAFE_H adımlarına böler.
 *  • YPort           — Çift tick: y+0.42 / onGround=false,
 *                      Tek  tick: y=zemin / onGround=true
 *                      Sunucu bunu "zıplama" olarak yorumlar.
 *
 * NOT: @Shadow kullanılmaz — tüm alan/metot erişimi (ClientPlayerEntity) cast ile yapılır.
 */
@Mixin(ClientPlayerEntity.class)
public class SpeedModule {

    @Unique private static final double MAX_SAFE_H = 0.42;
    @Unique private static final double YPORT_JUMP = 0.42;

    @Unique private int    yportTick      = 0;
    @Unique private double yportGroundY   = Double.NaN;
    @Unique private double flexLastSentX  = Double.NaN;
    @Unique private double flexLastSentZ  = Double.NaN;
    @Unique private boolean stepActive    = false;

    // ── sendMovementPackets ───────────────────────────────────────
    @Inject(method = "sendMovementPackets()V", at = @At("HEAD"), cancellable = true)
    private void onSendMovementPackets(CallbackInfo ci) {
        if (!ModuleManager.isEnabled("Speed")) return;
        Module speed = ModuleManager.get("Speed");
        if (speed == null) return;

        ClientPlayerEntity player = (ClientPlayerEntity)(Object)this;
        if (player.getWorld() == null || player.networkHandler == null) return;

        String mode = speed.getStringSetting("mode", "Strafe");
        if ("YPort".equals(mode)) {
            flexYPort(player, ci);
        } else {
            flexStepInject(player, ci);
        }
    }

    @Unique
    private void flexYPort(ClientPlayerEntity player, CallbackInfo ci) {
        if (player.isOnGround()) yportGroundY = player.getY();
        if (Double.isNaN(yportGroundY)) return;

        yportTick++;
        boolean jump = (yportTick & 1) == 0;
        double sendY = jump ? (yportGroundY + YPORT_JUMP) : yportGroundY;

        player.networkHandler.sendPacket(
            new PlayerMoveC2SPacket.PositionAndOnGround(
                player.getX(), sendY, player.getZ(), !jump
            )
        );
        ci.cancel();
    }

    @Unique
    private void flexStepInject(ClientPlayerEntity player, CallbackInfo ci) {
        if (stepActive) return;

        double cx = player.getX(), cz = player.getZ();
        if (Double.isNaN(flexLastSentX)) { flexLastSentX = cx; flexLastSentZ = cz; return; }

        double dx = cx - flexLastSentX, dz = cz - flexLastSentZ;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist <= MAX_SAFE_H) { flexLastSentX = cx; flexLastSentZ = cz; return; }

        int steps = (int) Math.ceil(dist / MAX_SAFE_H);
        double sx = dx / steps, sz = dz / steps;

        stepActive = true;
        try {
            for (int i = 1; i < steps; i++) {
                player.networkHandler.sendPacket(
                    new PlayerMoveC2SPacket.PositionAndOnGround(
                        flexLastSentX + sx * i, player.getY(),
                        flexLastSentZ + sz * i, player.isOnGround()
                    )
                );
            }
        } finally {
            stepActive = false;
        }
        flexLastSentX = cx;
        flexLastSentZ = cz;
    }

    // ── tick: YPort zemin takibi + hız sınırlama ─────────────────
    @Inject(method = "tick()V", at = @At("TAIL"))
    private void onSpeedTick(CallbackInfo ci) {
        if (!ModuleManager.isEnabled("Speed")) return;
        Module speed = ModuleManager.get("Speed");
        if (speed == null) return;

        ClientPlayerEntity player = (ClientPlayerEntity)(Object)this;
        String mode = speed.getStringSetting("mode", "Strafe");

        if ("YPort".equals(mode) && player.isOnGround()) yportGroundY = player.getY();

        float cfg = speed.getFloatSetting("speed", 0.35f);
        double cap = Math.min((double) cfg, MAX_SAFE_H);
        Vec3d vel = player.getVelocity();
        double h = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        if (h > cap + 0.01) {
            double f = cap / h;
            player.setVelocity(vel.x * f, vel.y, vel.z * f);
        }
    }
}
