package com.flex.client.mixin;

import com.flex.client.module.Module;
import com.flex.client.module.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * SpeedModule — Sunucu anticheat bypass için hareket paketi manipülasyonu.
 *
 * Paper ve Spigot sunucuları oyuncunun bir tick'te çok fazla hareket etmesini
 * algıladığında pozisyonu sıfırlar (rubber-banding). Bu mixin iki strateji ile
 * bunu önler:
 *
 * 1) Strafe / Ground modu → Büyük hareket delta'larını böler (step injection):
 *    Tek tick'te MAX_SAFE_H'den fazla yatay hareket algılanırsa, hedef pozisyona
 *    giden yol eşit adımlara bölünür ve her adım için ayrı bir
 *    PlayerMoveC2SPacket gönderilir. Böylece sunucu hiçbir zaman eşik üstü
 *    delta görmez.
 *
 * 2) YPort modu → Çift/tek tick paterni:
 *    - Çift tick: y += 0.42 (onGround=false) → sunucu bunu zıplama başı sayar.
 *    - Tek  tick: y = zemin (onGround=true)  → normal iniş.
 *    Bu desen anticheat'in hız cezası uygulamasını engeller çünkü sistem
 *    hareketi "tekrarlayan zıplama" olarak değerlendirir.
 *
 * Güvenli yatay hız eşiği: ~0.42 blok/tick (sprint + zıplama normali).
 */
@Mixin(ClientPlayerEntity.class)
public abstract class SpeedModule {

    @Shadow public ClientPlayNetworkHandler networkHandler;

    @Unique private static final double MAX_SAFE_H  = 0.42;
    @Unique private static final double YPORT_JUMP  = 0.42;

    @Unique private int    yportTick       = 0;
    @Unique private double yportGroundY    = Double.NaN;
    @Unique private double flexLastSentX   = Double.NaN;
    @Unique private double flexLastSentZ   = Double.NaN;
    @Unique private boolean stepInjecting  = false;

    // ── Paket gönderme yardımcısı ─────────────────────────────────────────
    @Unique
    private void flexSend(PlayerMoveC2SPacket pkt) {
        if (networkHandler != null) networkHandler.sendPacket(pkt);
    }

    // ── sendMovementPackets hook ──────────────────────────────────────────
    @Inject(method = "sendMovementPackets", at = @At("HEAD"), cancellable = true)
    private void onSendMovementPackets(CallbackInfo ci) {
        if (!ModuleManager.isEnabled("Speed")) return;
        Module speed = ModuleManager.get("Speed");
        if (speed == null) return;

        ClientPlayerEntity player = (ClientPlayerEntity)(Object)this;
        if (player.getWorld() == null || networkHandler == null) return;

        String mode = speed.getStringSetting("mode", "Strafe");

        if ("YPort".equals(mode)) {
            handleYPort(player, ci);
        } else {
            handleStepInjection(player, ci);
        }
    }

    /**
     * YPort bypass:
     * Zemin Y değerini takip eder. Çift tiklerde sahte zıplama paketi,
     * tek tiklerde gerçek iniş paketi gönderir; normal paket gönderimini iptal eder.
     */
    @Unique
    private void handleYPort(ClientPlayerEntity player, CallbackInfo ci) {
        if (player.isOnGround()) {
            yportGroundY = player.getY();
        }
        if (Double.isNaN(yportGroundY)) return;

        yportTick++;
        boolean jumpPhase = (yportTick & 1) == 0;

        if (jumpPhase) {
            flexSend(new PlayerMoveC2SPacket.PositionAndOnGround(
                player.getX(), yportGroundY + YPORT_JUMP, player.getZ(), false
            ));
        } else {
            flexSend(new PlayerMoveC2SPacket.PositionAndOnGround(
                player.getX(), yportGroundY, player.getZ(), true
            ));
        }
        ci.cancel();
    }

    /**
     * Step injection bypass (Strafe / Ground modu):
     * Hareket deltası MAX_SAFE_H'yi aşarsa hareketi N adıma böler ve
     * her adım için ayrı bir konum paketi gönderir.
     * Normal paket gönderimi devam eder (ci.cancel() çağrılmaz),
     * sadece öncesine ara paketler enjekte edilir.
     */
    @Unique
    private void handleStepInjection(ClientPlayerEntity player, CallbackInfo ci) {
        if (stepInjecting) return;

        double curX = player.getX();
        double curZ = player.getZ();

        if (Double.isNaN(flexLastSentX)) {
            flexLastSentX = curX;
            flexLastSentZ = curZ;
            return;
        }

        double dx = curX - flexLastSentX;
        double dz = curZ - flexLastSentZ;
        double dist = Math.sqrt(dx * dx + dz * dz);

        if (dist <= MAX_SAFE_H) {
            flexLastSentX = curX;
            flexLastSentZ = curZ;
            return;
        }

        int steps = (int) Math.ceil(dist / MAX_SAFE_H);
        double stepX = dx / steps;
        double stepZ = dz / steps;

        stepInjecting = true;
        try {
            for (int i = 1; i < steps; i++) {
                double ix = flexLastSentX + stepX * i;
                double iz = flexLastSentZ + stepZ * i;
                flexSend(new PlayerMoveC2SPacket.PositionAndOnGround(
                    ix, player.getY(), iz, player.isOnGround()
                ));
            }
        } finally {
            stepInjecting = false;
        }

        flexLastSentX = curX;
        flexLastSentZ = curZ;
        // Normal paket gönderimi devam eder (son adım için)
    }

    // ── Tick hook: YPort zemin Y takibi + hız sınırlama ─────────────────
    @Inject(method = "tick", at = @At("TAIL"))
    private void onSpeedTick(CallbackInfo ci) {
        if (!ModuleManager.isEnabled("Speed")) return;
        Module speed = ModuleManager.get("Speed");
        if (speed == null) return;

        ClientPlayerEntity player = (ClientPlayerEntity)(Object)this;
        String mode = speed.getStringSetting("mode", "Strafe");

        if ("YPort".equals(mode) && player.isOnGround()) {
            yportGroundY = player.getY();
        }

        // Yatay hızı güvenli eşiğin altında tut (rubber-band önlemi)
        float configSpeed = speed.getFloatSetting("speed", 0.35f);
        double cap = Math.min((double) configSpeed, MAX_SAFE_H);
        Vec3d vel = player.getVelocity();
        double h = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        if (h > cap + 0.01) {
            double factor = cap / h;
            player.setVelocity(vel.x * factor, vel.y, vel.z * factor);
        }
    }
}
