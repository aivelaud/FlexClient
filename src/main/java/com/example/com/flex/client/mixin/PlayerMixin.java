package com.flex.client.mixin;

import com.flex.client.module.ModuleManager;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;

@Mixin(ClientPlayerEntity.class)
public class PlayerMixin {

    @Inject(at = @At("HEAD"), method = "tick()V")
    private void onTick(CallbackInfo ci) {
        ClientPlayerEntity player = (ClientPlayerEntity)(Object)this;

        if (ModuleManager.isEnabled("Fly")) {
            player.getAbilities().flying = true;
            player.getAbilities().setFlySpeed(0.1f);
        }

        if (ModuleManager.isEnabled("Speed")) {
            player.getAbilities().setWalkSpeed(0.2f);
        }

        if (ModuleManager.isEnabled("KillAura")) {
            boolean hitAnimals = ModuleManager.get("KillAura").isHitAnimals();
            Box box = player.getBoundingBox().expand(6);
            List<Entity> entities = player.getWorld().getOtherEntities(player, box);
            for (Entity entity : entities) {
                boolean isTarget = entity instanceof Monster ||
                    (hitAnimals && entity instanceof AnimalEntity);
                if (isTarget && !entity.isRemoved()) {
                    player.networkHandler.sendPacket(
                        PlayerInteractEntityC2SPacket.attack(entity, player.isSneaking())
                    );
                    player.swingHand(Hand.MAIN_HAND);
                    break;
                }
            }
        }
    }
}
