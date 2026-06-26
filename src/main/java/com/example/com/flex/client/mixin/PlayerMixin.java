package com.flex.client.mixin;

import com.flex.client.module.Module;
import com.flex.client.module.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Mixin(ClientPlayerEntity.class)
public abstract class PlayerMixin {

    // stepHeight Entity'de protected — Shadow ile eriş
    @Shadow protected float stepHeight;

    private int killAuraCooldown = 0;
    private int autoEatCooldown  = 0;
    private boolean wasOnGround  = false;
    private boolean longJumpReady = false;

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
            player.fallDistance = 0f;
        }

        // ── SAFE WALK ────────────────────────────────────────────
        if (ModuleManager.isEnabled("SafeWalk")) {
            player.setSneaking(true);
        }

        // ── STEP (Shadow ile stepHeight erişimi) ──────────────────
        if (ModuleManager.isEnabled("Step")) {
            this.stepHeight = ModuleManager.get("Step").getFloatSetting("height", 2.5f);
        } else {
            this.stepHeight = 0.6f;
        }

        // ── BUNNY HOP ────────────────────────────────────────────
        if (ModuleManager.isEnabled("BunnyHop")) {
            if (player.isOnGround()) {
                float boost = ModuleManager.get("BunnyHop").getFloatSetting("boost", 0.08f);
                player.addVelocity(0, 0.42 + boost, 0);
                player.setSprinting(true);
            }
        }

        // ── LONG JUMP ────────────────────────────────────────────
        if (ModuleManager.isEnabled("LongJump")) {
            // Yerde iken sonraki zıplamaya hazırlan
            if (player.isOnGround()) {
                longJumpReady = true;
            }
            // Havalanır havalanmaz ileri fırla
            if (longJumpReady && !player.isOnGround() && wasOnGround) {
                float boost = ModuleManager.get("LongJump").getFloatSetting("boost", 0.8f);
                double yaw  = Math.toRadians(player.getYaw());
                player.addVelocity(-Math.sin(yaw) * boost, 0, Math.cos(yaw) * boost);
                longJumpReady = false;
            }
            // Havada iken ekstra iterek hız koru
            if (!player.isOnGround() && !wasOnGround) {
                double yaw = Math.toRadians(player.getYaw());
                Vec3d vel  = player.getVelocity();
                if (Math.abs(vel.x) + Math.abs(vel.z) < 1.5) {
                    player.addVelocity(-Math.sin(yaw) * 0.04, 0, Math.cos(yaw) * 0.04);
                }
            }
        }
        wasOnGround = player.isOnGround();

        // ── VELOCITY (knockback azaltıcı) ────────────────────────
        if (ModuleManager.isEnabled("Velocity")) {
            float hFactor = ModuleManager.get("Velocity").getFloatSetting("horizontal", 0.15f);
            Vec3d vel     = player.getVelocity();
            double horiz  = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
            if (horiz > 0.28 && !ModuleManager.isEnabled("Fly") && !ModuleManager.isEnabled("Speed")) {
                player.setVelocity(vel.x * hFactor, vel.y, vel.z * hFactor);
            }
        }

        // ── ANTI KNOCKBACK ───────────────────────────────────────
        if (ModuleManager.isEnabled("AntiKnockback")) {
            Vec3d vel    = player.getVelocity();
            double horiz = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
            if (horiz > 0.28 && !ModuleManager.isEnabled("Fly") && !ModuleManager.isEnabled("Speed")) {
                player.setVelocity(0, vel.y, 0);
            }
        }

        // ── CRITICALS ────────────────────────────────────────────
        if (ModuleManager.isEnabled("Criticals")) {
            if (player.isOnGround() && !player.isTouchingWater() && !player.isInLava()) {
                player.addVelocity(0, 0.11, 0);
            }
        }

        // ── AIM ASSIST ───────────────────────────────────────────
        if (ModuleManager.isEnabled("AimAssist")) {
            Module aa   = ModuleManager.get("AimAssist");
            int range   = aa.getIntSetting("range", 5);
            float speed = aa.getFloatSetting("speed", 5.0f);

            Entity nearest    = null;
            double nearestDist = Double.MAX_VALUE;
            Box box = player.getBoundingBox().expand(range);

            for (Entity e : player.getWorld().getOtherEntities(player, box)) {
                if (!(e instanceof LivingEntity)) continue;
                if (((LivingEntity) e).isDead()) continue;
                boolean isPlayer = e instanceof AbstractClientPlayerEntity;
                boolean isMob    = e instanceof Monster;
                if (!isPlayer && !isMob) continue;
                double d = player.squaredDistanceTo(e);
                if (d < nearestDist) { nearestDist = d; nearest = e; }
            }

            if (nearest != null) {
                double dx   = nearest.getX() - player.getX();
                double dy   = nearest.getEyeY() - player.getEyeY();
                double dz   = nearest.getZ() - player.getZ();
                double horz = Math.sqrt(dx * dx + dz * dz);
                float tYaw  = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90);
                float tPitch = (float)(-Math.toDegrees(Math.atan2(dy, horz)));
                float dYaw  = wrapAngle(tYaw - player.getYaw());
                float dPitch = tPitch - player.getPitch();
                player.setYaw(player.getYaw() + Math.max(-speed, Math.min(speed, dYaw)));
                player.setPitch(player.getPitch() + Math.max(-speed, Math.min(speed, dPitch)));
            }
        }

        // ── KILL AURA ────────────────────────────────────────────
        if (killAuraCooldown > 0) killAuraCooldown--;

        if (ModuleManager.isEnabled("KillAura") && killAuraCooldown == 0) {
            Module ka       = ModuleManager.get("KillAura");
            boolean hitAni  = ka.isHitAnimals();
            boolean hitPlr  = ka.isHitPlayers();
            boolean hitMob  = ka.isHitMonsters();
            int range       = ka.getKillAuraRange();
            int delay       = ka.getKillAuraDelay();

            Box box = player.getBoundingBox().expand(range);
            List<Entity> targets = player.getWorld().getOtherEntities(player, box)
                .stream()
                .filter(e -> {
                    if (!(e instanceof LivingEntity)) return false;
                    if (((LivingEntity) e).isDead()) return false;
                    return (hitMob && e instanceof Monster)
                        || (hitAni && e instanceof AnimalEntity)
                        || (hitPlr && e instanceof AbstractClientPlayerEntity);
                })
                .sorted(Comparator.comparingDouble(e -> player.squaredDistanceTo(e)))
                .collect(Collectors.toList());

            if (!targets.isEmpty()) {
                Entity target = targets.get(0);
                if (ka.isRotate()) {
                    double dx   = target.getX() - player.getX();
                    double dy   = target.getEyeY() - player.getEyeY();
                    double dz   = target.getZ() - player.getZ();
                    double horz = Math.sqrt(dx * dx + dz * dz);
                    float yaw   = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90);
                    float pitch = (float)(-Math.toDegrees(Math.atan2(dy, horz)));
                    player.setYaw(yaw);
                    player.setPitch(pitch);
                    player.networkHandler.sendPacket(
                        new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, player.isOnGround()));
                }
                player.networkHandler.sendPacket(
                    PlayerInteractEntityC2SPacket.attack(target, player.isSneaking()));
                player.swingHand(Hand.MAIN_HAND);
                killAuraCooldown = delay;
            }
        }

        // ── AUTO EAT ─────────────────────────────────────────────
        if (autoEatCooldown > 0) autoEatCooldown--;
        if (ModuleManager.isEnabled("AutoEat") && autoEatCooldown == 0) {
            int threshold = ModuleManager.get("AutoEat").getIntSetting("threshold", 16);
            if (player.getHungerManager().getFoodLevel() < threshold) {
                ItemStack held = player.getMainHandStack();
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

        // ── REGEN ────────────────────────────────────────────────
        if (ModuleManager.isEnabled("Regen")) {
            float rate = ModuleManager.get("Regen").getFloatSetting("rate", 0.3f);
            if (player.getHealth() < player.getMaxHealth()) {
                player.setHealth(Math.min(player.getHealth() + rate, player.getMaxHealth()));
            }
        }

        // ── AUTO TOTEM ───────────────────────────────────────────
        if (ModuleManager.isEnabled("AutoTotem")) {
            int threshold = ModuleManager.get("AutoTotem").getIntSetting("threshold", 8);
            if (player.getHealth() <= threshold) {
                ItemStack offhand = player.getInventory().offHand.get(0);
                // Items.TOTEM_OF_UNDYING kullan (class yerine)
                if (offhand.getItem() != Items.TOTEM_OF_UNDYING) {
                    for (int i = 0; i < player.getInventory().main.size(); i++) {
                        ItemStack s = player.getInventory().main.get(i);
                        if (s.getItem() == Items.TOTEM_OF_UNDYING) {
                            player.getInventory().offHand.set(0, s.copy());
                            player.getInventory().main.set(i, offhand.copy());
                            break;
                        }
                    }
                }
            }
        }

        // ── FULLBRIGHT ───────────────────────────────────────────
        if (ModuleManager.isEnabled("Fullbright")) {
            try {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.options != null) mc.options.getGamma().setValue(16.0);
            } catch (Exception ignored) {}
        }
    }

    private static float wrapAngle(float a) {
        while (a >  180) a -= 360;
        while (a < -180) a += 360;
        return a;
    }
}
