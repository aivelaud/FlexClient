package com.flex.client.mixin;

import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * NoFall paketi için PlayerMoveC2SPacket.onGround alanına erişim sağlar.
 */
@Mixin(PlayerMoveC2SPacket.class)
public interface PlayerMovePacketAccessor {
    @Accessor("onGround")
    boolean isOnGround();

    @Accessor("onGround")
    void setOnGround(boolean onGround);
}
