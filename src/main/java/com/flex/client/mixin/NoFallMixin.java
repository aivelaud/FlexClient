package com.flex.client.mixin;

import com.flex.client.module.ModuleManager;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NoFallMixin — PlayerMoveC2SPacket gönderimini yakalar,
 * NoFall aktifken onGround=true olarak işaretler.
 * Böylece sunucu oyuncunun her zaman yerde olduğunu sanır → hasar yok.
 */
@Mixin(ClientConnection.class)
public class NoFallMixin {

    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/PacketCallbacks;)V",
            at = @At("HEAD"))
    private void onSend(Packet<?> packet, PacketCallbacks callbacks, CallbackInfo ci) {
        if (!ModuleManager.isEnabled("NoFall")) return;
        if (packet instanceof PlayerMoveC2SPacket move) {
            ((PlayerMovePacketAccessor) move).setOnGround(true);
        }
    }
}
