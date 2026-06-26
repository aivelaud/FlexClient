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

    // KillAura saldiri hizi siniri - her tick saldirir ise sunucu banlar
    // Her 8 tick'te bir saldir (yaklasik 2.5 saldiri/saniye)
    private int killAuraCooldown = 0;
    private static final int KILL_AURA_DELAY = 8;

    @Inject(at = @At("HEAD"), method = "tick()V")
    private void onTick(CallbackInfo ci) {
        ClientPlayerEntity player = (ClientPlayerEntity)(Object)this;

        // --- Null guvenligi ---
        // PojavLauncher'da dunya yuklenmeden mixin tetiklenebilir, cokuyor
        if (player == null) return;
        if (player.getWorld() == null) return;
        if (player.networkHandler == null) return;

        // --- FLY ---
        if (ModuleManager.isEnabled("Fly")) {
            player.getAbilities().flying = true;
            player.getAbilities().setFlySpeed(0.1f);
            // PojavLauncher'da uygulama olmadan flying bayragi sifirlanir,
            // her tick'te tekrar set etmek bunu onler
        } else {
            // Fly kapilinca uciyor kalmasin, normal hiza don
            if (player.getAbilities().flying
                    && !player.getAbilities().allowFlying) {
                player.getAbilities().flying = false;
                player.getAbilities().setFlySpeed(0.05f); // varsayilan
            }
        }

        // --- SPEED ---
        if (ModuleManager.isEnabled("Speed")) {
            player.getAbilities().setWalkSpeed(0.2f);
        } else {
            // Speed kapilinca normal hiza don
            player.getAbilities().setWalkSpeed(0.1f); // varsayilan
        }

        // --- KILLAURA ---
        if (killAuraCooldown > 0) {
            killAuraCooldown--;
        }

        if (ModuleManager.isEnabled("KillAura") && killAuraCooldown == 0) {
            boolean hitAnimals = ModuleManager.get("KillAura").isHitAnimals();

            Box box = player.getBoundingBox().expand(6);
            List<Entity> entities = player.getWorld().getOtherEntities(player, box);

            for (Entity entity : entities) {
                if (entity == null || entity.isRemoved()) continue;

                boolean isTarget = entity instanceof Monster ||
                    (hitAnimals && entity instanceof AnimalEntity);

                if (isTarget) {
                    // Paketi gonder
                    player.networkHandler.sendPacket(
                        PlayerInteractEntityC2SPacket.attack(entity, player.isSneaking())
                    );
                    player.swingHand(Hand.MAIN_HAND);

                    // Cooldown baslat - sunucu bypass ve banlanmama icin
                    killAuraCooldown = KILL_AURA_DELAY;
                    break;
                }
            }
        }
    }
}
