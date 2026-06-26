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

    private int killAuraCooldown = 0;
    private int autoEatCooldown  = 0;

    @Inject(at = @At("HEAD"), method = "tick()V")
    private void onTick(CallbackInfo ci) {
        ClientPlayerEntity player = (ClientPlayerEntity)(Object)this;
        if (player == null || player.getWorld() == null || player.networkHandler == null) return;

        // ── FLY ─────────────────────────────────────────────────
        if (ModuleManager.isEnabled("Fly")) {
            player.getAbilities().flying = true;
            float spd = ModuleManager.get("Fly").getFlySpeed();
            player.getAbilities().setFlySpeed(spd);
        } else {
            if (player.getAbilities().flying && !player.getAbilities().allowFlying) {
                player.getAbilities().flying = false;
                player.getAbilities().setFlySpeed(0.05f);
            }
        }

        // ── SPEED ────────────────────────────────────────────────
        if (ModuleManager.isEnabled("Speed")) {
            float spd = ModuleManager.get("Speed").getWalkSpeed();
            player.getAbilities().setWalkSpeed(spd);
        } else {
            player.getAbilities().setWalkSpeed(0.1f);
        }

        // ── SPRINT ───────────────────────────────────────────────
        if (ModuleManager.isEnabled("Sprint")) {
            player.setSprinting(true);
        }

        // ── NO FALL ──────────────────────────────────────────────
        if (ModuleManager.isEnabled("NoFall")) {
            if (player.fallDistance > 2.0f) {
                player.fallDistance = 0f;
            }
        }

        // ── ANTI KNOCKBACK ───────────────────────────────────────
        if (ModuleManager.isEnabled("AntiKnockback")) {
            player.setVelocity(player.getVelocity().multiply(1, 1, 1));
        }

        // ── CRITICALS ────────────────────────────────────────────
        // Kritik vurus: havada oldugun simule et (her saldiri oncesi kucuk ziplama)
        if (ModuleManager.isEnabled("Criticals")) {
            if (!player.isOnGround() || player.isTouchingWater()) {
                // Surekli hafif dusturme ile kritik durumu koruyoruz
                player.addVelocity(0, 0.001, 0);
            }
        }

        // ── KILL AURA ────────────────────────────────────────────
        if (killAuraCooldown > 0) killAuraCooldown--;

        if (ModuleManager.isEnabled("KillAura") && killAuraCooldown == 0) {
            com.flex.client.module.Module ka = ModuleManager.get("KillAura");
            boolean hitAnimals  = ka.isHitAnimals();
            boolean hitPlayers  = ka.isHitPlayers();
            int range           = ka.getKillAuraRange();
            int delay           = ka.getKillAuraDelay();

            Box box = player.getBoundingBox().expand(range);
            List<Entity> entities = player.getWorld().getOtherEntities(player, box);

            for (Entity e : entities) {
                if (e == null || e.isRemoved()) continue;
                boolean target = (e instanceof Monster)
                    || (hitAnimals && e instanceof AnimalEntity)
                    || (hitPlayers && e instanceof net.minecraft.client.network.AbstractClientPlayerEntity);
                if (target) {
                    player.networkHandler.sendPacket(
                        PlayerInteractEntityC2SPacket.attack(e, player.isSneaking()));
                    player.swingHand(Hand.MAIN_HAND);
                    killAuraCooldown = delay;
                    break;
                }
            }
        }

        // ── AUTO EAT ─────────────────────────────────────────────
        if (autoEatCooldown > 0) autoEatCooldown--;

        if (ModuleManager.isEnabled("AutoEat") && autoEatCooldown == 0) {
            if (player.getHungerManager().getFoodLevel() < 18) {
                net.minecraft.item.ItemStack held = player.getMainHandStack();
                if (held.getItem().isFood()) {
                    player.networkHandler.sendPacket(
                        new net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket(
                            net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action.RELEASE_USE_ITEM,
                            net.minecraft.util.math.BlockPos.ORIGIN,
                            net.minecraft.util.math.Direction.DOWN, 0));
                    autoEatCooldown = 20;
                }
            }
        }

        // ── FULLBRIGHT ───────────────────────────────────────────
        if (ModuleManager.isEnabled("Fullbright")) {
            try {
                net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
                if (mc.options != null) {
                    mc.options.getGamma().setValue(16.0);
                }
            } catch (Exception ignored) {}
        }
    }
}
