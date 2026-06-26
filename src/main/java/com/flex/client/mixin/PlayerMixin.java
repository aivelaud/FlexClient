package com.flex.client.mixin;

import com.flex.client.module.Module;
import com.flex.client.module.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Mixin(ClientPlayerEntity.class)
public abstract class PlayerMixin {

    private int killAuraCooldown  = 0;
    private int autoEatCooldown   = 0;
    private boolean wasOnGround   = false;
    private boolean longJumpReady = false;

    @Inject(at = @At("HEAD"), method = "tick()V")
    private void onTick(CallbackInfo ci) {
        ClientPlayerEntity player = (ClientPlayerEntity)(Object)this;
        if (player == null || player.getWorld() == null || player.networkHandler == null) return;

        // FLY
        if (ModuleManager.isEnabled("Fly")) {
            player.getAbilities().flying = true;
            player.getAbilities().setFlySpeed(ModuleManager.get("Fly").getFlySpeed());
        } else if (player.getAbilities().flying && !player.getAbilities().allowFlying) {
            player.getAbilities().flying = false;
            player.getAbilities().setFlySpeed(0.05f);
        }

        // SPEED
        if (ModuleManager.isEnabled("Speed")) {
            player.getAbilities().setWalkSpeed(ModuleManager.get("Speed").getWalkSpeed());
        } else {
            player.getAbilities().setWalkSpeed(0.1f);
        }

        // SPRINT
        if (ModuleManager.isEnabled("Sprint")) player.setSprinting(true);

        // NO FALL
        if (ModuleManager.isEnabled("NoFall")) player.fallDistance = 0f;

        // SAFE WALK
        if (ModuleManager.isEnabled("SafeWalk")) player.setSneaking(true);

        // NO SLOW
        if (ModuleManager.isEnabled("NoSlow") && player.isUsingItem()) {
            player.getAbilities().setWalkSpeed(
                ModuleManager.isEnabled("Speed") ? ModuleManager.get("Speed").getWalkSpeed() : 0.1f);
        }

        // STEP
        StepHeightAccessor acc = (StepHeightAccessor)(Object)this;
        acc.setStepHeight(ModuleManager.isEnabled("Step")
            ? ModuleManager.get("Step").getFloatSetting("height", 2.5f) : 0.6f);

        // BUNNY HOP
        if (ModuleManager.isEnabled("BunnyHop") && player.isOnGround()) {
            float boost = ModuleManager.get("BunnyHop").getFloatSetting("boost", 0.08f);
            player.addVelocity(0, 0.42 + boost, 0);
            player.setSprinting(true);
        }

        // LONG JUMP
        if (ModuleManager.isEnabled("LongJump")) {
            if (player.isOnGround()) longJumpReady = true;
            if (longJumpReady && !player.isOnGround() && wasOnGround) {
                float boost = ModuleManager.get("LongJump").getFloatSetting("boost", 0.8f);
                double yaw  = Math.toRadians(player.getYaw());
                player.addVelocity(-Math.sin(yaw) * boost, 0, Math.cos(yaw) * boost);
                longJumpReady = false;
            }
        }
        wasOnGround = player.isOnGround();

        // VELOCITY
        if (ModuleManager.isEnabled("Velocity")) {
            float h = ModuleManager.get("Velocity").getFloatSetting("horizontal", 0.15f);
            Vec3d vel = player.getVelocity();
            double hz = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
            if (hz > 0.28 && !ModuleManager.isEnabled("Fly") && !ModuleManager.isEnabled("Speed"))
                player.setVelocity(vel.x * h, vel.y, vel.z * h);
        }

        // ANTI KNOCKBACK
        if (ModuleManager.isEnabled("AntiKnockback")) {
            Vec3d vel = player.getVelocity();
            double hz = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
            if (hz > 0.28 && !ModuleManager.isEnabled("Fly") && !ModuleManager.isEnabled("Speed"))
                player.setVelocity(0, vel.y, 0);
        }

        // CRITICALS
        if (ModuleManager.isEnabled("Criticals") && player.isOnGround()
                && !player.isTouchingWater() && !player.isInLava()) {
            player.addVelocity(0, 0.11, 0);
        }

        // AIM ASSIST
        if (ModuleManager.isEnabled("AimAssist")) {
            Module aa   = ModuleManager.get("AimAssist");
            int range   = aa.getIntSetting("range", 5);
            float speed = aa.getFloatSetting("speed", 5.0f);
            boolean hitP = aa.getSetting("players");
            boolean hitM = aa.getSetting("mobs");

            Entity nearest = null;
            double nearestDist = Double.MAX_VALUE;
            Box box = player.getBoundingBox().expand(range);

            for (Entity e : player.getWorld().getOtherEntities(player, box)) {
                if (!(e instanceof LivingEntity le) || le.isDead()) continue;
                boolean isPlayer = e instanceof AbstractClientPlayerEntity;
                boolean isMob    = e instanceof Monster;
                if (isPlayer && !hitP) continue;
                if (isMob    && !hitM) continue;
                if (!isPlayer && !isMob) continue;
                double d = player.squaredDistanceTo(e);
                if (d < nearestDist) { nearestDist = d; nearest = e; }
            }

            if (nearest != null) {
                double dx   = nearest.getX() - player.getX();
                double dy   = nearest.getEyeY() - player.getEyeY();
                double dz   = nearest.getZ() - player.getZ();
                double horz = Math.sqrt(dx * dx + dz * dz);
                float tYaw   = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90);
                float tPitch = (float)(-Math.toDegrees(Math.atan2(dy, horz)));
                player.setYaw(player.getYaw() + clamp(wrapAngle(tYaw - player.getYaw()), -speed, speed));
                player.setPitch(player.getPitch() + clamp(tPitch - player.getPitch(), -speed, speed));
            }
        }

        // KILL AURA
        if (killAuraCooldown > 0) killAuraCooldown--;
        if (ModuleManager.isEnabled("KillAura") && killAuraCooldown == 0) {
            Module ka  = ModuleManager.get("KillAura");
            int range  = ka.getKillAuraRange();
            int delay  = ka.getKillAuraDelay();

            List<Entity> targets = player.getWorld()
                .getOtherEntities(player, player.getBoundingBox().expand(range))
                .stream()
                .filter(e -> {
                    if (!(e instanceof LivingEntity le) || le.isDead()) return false;
                    return (ka.isHitMonsters() && e instanceof Monster)
                        || (ka.isHitAnimals()  && e instanceof AnimalEntity)
                        || (ka.isHitPlayers()  && e instanceof AbstractClientPlayerEntity);
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

        // TRIGGER BOT
        if (ModuleManager.isEnabled("TriggerBot")) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.targetedEntity instanceof LivingEntity le && !le.isDead() && killAuraCooldown == 0) {
                player.networkHandler.sendPacket(
                    PlayerInteractEntityC2SPacket.attack(mc.targetedEntity, player.isSneaking()));
                player.swingHand(Hand.MAIN_HAND);
                killAuraCooldown = 4;
            }
        }

        // AUTO EAT
        if (autoEatCooldown > 0) autoEatCooldown--;
        if (ModuleManager.isEnabled("AutoEat") && autoEatCooldown == 0) {
            int threshold = ModuleManager.get("AutoEat").getIntSetting("threshold", 16);
            if (player.getHungerManager().getFoodLevel() < threshold) {
                for (int i = 0; i < 9; i++) {
                    ItemStack s = player.getInventory().main.get(i);
                    if (!s.isEmpty() && s.getItem().isFood()) {
                        player.getInventory().selectedSlot = i;
                        player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.RELEASE_USE_ITEM,
                            BlockPos.ORIGIN, Direction.DOWN, 0));
                        autoEatCooldown = 20;
                        break;
                    }
                }
            }
        }

        // REGEN
        if (ModuleManager.isEnabled("Regen")) {
            float rate = ModuleManager.get("Regen").getFloatSetting("rate", 0.3f);
            if (player.getHealth() < player.getMaxHealth())
                player.setHealth(Math.min(player.getHealth() + rate, player.getMaxHealth()));
        }

        // AUTO TOTEM
        if (ModuleManager.isEnabled("AutoTotem")) {
            int threshold = ModuleManager.get("AutoTotem").getIntSetting("threshold", 8);
            if (player.getHealth() <= threshold) {
                ItemStack offhand = player.getInventory().offHand.get(0);
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

        // FULLBRIGHT
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.options != null) {
                if (ModuleManager.isEnabled("Fullbright")) {
                    mc.options.getGamma().setValue(16.0);
                } else if ((double) mc.options.getGamma().getValue() > 1.1) {
                    mc.options.getGamma().setValue(1.0);
                }
            }
        } catch (Exception ignored) {}

        // NUKER
        if (ModuleManager.isEnabled("Nuker")) {
            int r = ModuleManager.get("Nuker").getIntSetting("range", 3);
            BlockPos center = player.getBlockPos();
            outer:
            for (int x = -r; x <= r; x++) {
                for (int y = -r; y <= r; y++) {
                    for (int z = -r; z <= r; z++) {
                        BlockPos bp = center.add(x, y, z);
                        if (!player.getWorld().getBlockState(bp).isAir()) {
                            player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                                PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, bp, Direction.UP, 0));
                            player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                                PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, bp, Direction.UP, 0));
                            break outer;
                        }
                    }
                }
            }
        }

        // SCAFFOLD
        if (ModuleManager.isEnabled("Scaffold") && !player.isOnGround()) {
            BlockPos below = player.getBlockPos().down();
            if (player.getWorld().getBlockState(below).isAir()) {
                for (int i = 0; i < 9; i++) {
                    ItemStack s = player.getInventory().main.get(i);
                    if (!s.isEmpty() && s.getItem() instanceof BlockItem) {
                        player.getInventory().selectedSlot = i;
                        player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(
                            Hand.MAIN_HAND,
                            new BlockHitResult(Vec3d.ofCenter(below.up()), Direction.UP, below, false),
                            0));
                        break;
                    }
                }
            }
        }
    }

    private static float wrapAngle(float a) {
        while (a >  180) a -= 360;
        while (a < -180) a += 360;
        return a;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
