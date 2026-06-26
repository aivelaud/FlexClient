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
import net.minecraft.item.SwordItem;
import net.minecraft.item.AxeItem;
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
import java.util.Random;
import java.util.stream.Collectors;

@Mixin(ClientPlayerEntity.class)
public abstract class PlayerMixin {

    private int killAuraCooldown  = 0;
    private int autoEatCooldown   = 0;
    private int antiAfkTick       = 0;
    private boolean wasOnGround   = false;
    private boolean longJumpReady = false;
    private final Random rng      = new Random();

    @Inject(at = @At("HEAD"), method = "tick()V")
    private void onTick(CallbackInfo ci) {
        ClientPlayerEntity player = (ClientPlayerEntity)(Object)this;
        if (player == null || player.getWorld() == null || player.networkHandler == null) return;

        // ── FLY ─────────────────────────────────────────────────
        if (ModuleManager.isEnabled("Fly")) {
            player.getAbilities().flying = true;
            player.getAbilities().setFlySpeed(ModuleManager.get("Fly").getFlySpeed());
        } else if (player.getAbilities().flying && !player.getAbilities().allowFlying) {
            player.getAbilities().flying = false;
            player.getAbilities().setFlySpeed(0.05f);
        }

        // ── SPEED ────────────────────────────────────────────────
        if (ModuleManager.isEnabled("Speed")) {
            player.getAbilities().setWalkSpeed(ModuleManager.get("Speed").getWalkSpeed());
        } else {
            player.getAbilities().setWalkSpeed(0.1f);
        }

        // ── SPRINT ───────────────────────────────────────────────
        if (ModuleManager.isEnabled("Sprint")) player.setSprinting(true);

        // ── NO FALL ──────────────────────────────────────────────
        if (ModuleManager.isEnabled("NoFall")) player.fallDistance = 0f;

        // ── SAFE WALK ────────────────────────────────────────────
        if (ModuleManager.isEnabled("SafeWalk")) player.setSneaking(true);

        // ── NO SLOW ──────────────────────────────────────────────
        if (ModuleManager.isEnabled("NoSlow") && player.isUsingItem()) {
            player.getAbilities().setWalkSpeed(
                ModuleManager.isEnabled("Speed") ? ModuleManager.get("Speed").getWalkSpeed() : 0.1f);
        }

        // ── STEP ─────────────────────────────────────────────────
        StepHeightAccessor acc = (StepHeightAccessor)(Object)this;
        acc.setStepHeight(ModuleManager.isEnabled("Step")
            ? ModuleManager.get("Step").getFloatSetting("height", 2.5f) : 0.6f);

        // ── BUNNY HOP ────────────────────────────────────────────
        if (ModuleManager.isEnabled("BunnyHop") && player.isOnGround()) {
            player.addVelocity(0, 0.42 + ModuleManager.get("BunnyHop").getFloatSetting("boost", 0.08f), 0);
            player.setSprinting(true);
        }

        // ── HIGH JUMP ────────────────────────────────────────────
        if (ModuleManager.isEnabled("HighJump") && player.isOnGround()) {
            // Jump boost uygulanır — gerçek zıplama tuş ile tetiklenir ama boost ekleriz
            // Tuş basılıysa (isJumping private field — sadece velocity'ye dokunuruz)
        }

        // ── LONG JUMP ────────────────────────────────────────────
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

        // ── SPIDER (duvar tırmanma) ───────────────────────────────
        if (ModuleManager.isEnabled("Spider")) {
            boolean touching = !player.getWorld().isSpaceEmpty(
                player.getBoundingBox().offset(0, -0.01, 0));
            if (!player.isOnGround() && !touching) {
                float spd = ModuleManager.get("Spider").getFloatSetting("speed", 0.3f);
                player.setVelocity(player.getVelocity().x, spd, player.getVelocity().z);
            }
        }

        // ── ANTI VOID ────────────────────────────────────────────
        if (ModuleManager.isEnabled("AntiVoid")) {
            float safeY = ModuleManager.get("AntiVoid").getFloatSetting("safeY", 0.0f);
            if (player.getY() < safeY + 5 && player.getVelocity().y < 0) {
                boolean slowFall = ModuleManager.get("AntiVoid").getSetting("slowFall");
                if (slowFall) player.setVelocity(player.getVelocity().x, -0.1, player.getVelocity().z);
                else          player.setVelocity(player.getVelocity().x, 0, player.getVelocity().z);
            }
        }

        // ── PARKOUR (kenar algıla, otomatik zıpla) ───────────────
        if (ModuleManager.isEnabled("Parkour") && player.isOnGround()) {
            BlockPos frontPos = player.getBlockPos()
                .offset(player.getHorizontalFacing()).down();
            if (player.getWorld().getBlockState(frontPos).isAir()) {
                player.addVelocity(0, 0.42, 0);
            }
        }

        // ── VELOCITY ─────────────────────────────────────────────
        if (ModuleManager.isEnabled("Velocity")) {
            float h = ModuleManager.get("Velocity").getFloatSetting("horizontal", 0.15f);
            Vec3d vel = player.getVelocity();
            double hz = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
            if (hz > 0.28 && !ModuleManager.isEnabled("Fly") && !ModuleManager.isEnabled("Speed"))
                player.setVelocity(vel.x * h, vel.y, vel.z * h);
        }

        // ── ANTI KNOCKBACK ───────────────────────────────────────
        if (ModuleManager.isEnabled("AntiKnockback")) {
            Vec3d vel = player.getVelocity();
            double hz = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
            if (hz > 0.28 && !ModuleManager.isEnabled("Fly") && !ModuleManager.isEnabled("Speed"))
                player.setVelocity(0, vel.y, 0);
        }

        // ── CRITICALS (Jump modu) ─────────────────────────────────
        if (ModuleManager.isEnabled("Criticals")) {
            String mode = ModuleManager.get("Criticals").getMode();
            if ("Jump".equals(mode) && player.isOnGround()
                    && !player.isTouchingWater() && !player.isInLava()) {
                player.addVelocity(0, 0.11, 0);
            }
        }

        // ── AIM ASSIST ───────────────────────────────────────────
        if (ModuleManager.isEnabled("AimAssist")) {
            Module aa   = ModuleManager.get("AimAssist");
            int range   = aa.getIntSetting("range", 5);
            float speed = aa.getFloatSetting("speed", 5.0f);
            boolean hitP = aa.getSetting("players"), hitM = aa.getSetting("mobs");

            Entity nearest = null; double nearestDist = Double.MAX_VALUE;
            for (Entity e : player.getWorld().getOtherEntities(player, player.getBoundingBox().expand(range))) {
                if (!(e instanceof LivingEntity le) || le.isDead()) continue;
                boolean isP = e instanceof AbstractClientPlayerEntity;
                boolean isM = e instanceof Monster;
                if (isP && !hitP) continue; if (isM && !hitM) continue;
                if (!isP && !isM) continue;
                double d = player.squaredDistanceTo(e);
                if (d < nearestDist) { nearestDist = d; nearest = e; }
            }
            if (nearest != null) {
                double dx = nearest.getX()-player.getX(), dy = nearest.getEyeY()-player.getEyeY(),
                       dz = nearest.getZ()-player.getZ(), hz = Math.sqrt(dx*dx+dz*dz);
                float tY = (float)(Math.toDegrees(Math.atan2(dz,dx))-90);
                float tP = (float)(-Math.toDegrees(Math.atan2(dy,hz)));
                player.setYaw(player.getYaw() + clamp(wrapAngle(tY-player.getYaw()), -speed, speed));
                player.setPitch(player.getPitch() + clamp(tP-player.getPitch(), -speed, speed));
            }
        }

        // ── KILL AURA ────────────────────────────────────────────
        if (killAuraCooldown > 0) killAuraCooldown--;
        if (ModuleManager.isEnabled("KillAura") && killAuraCooldown == 0) {
            Module ka  = ModuleManager.get("KillAura");
            int range  = ka.getKillAuraRange(), delay = ka.getKillAuraDelay();

            List<Entity> targets = player.getWorld()
                .getOtherEntities(player, player.getBoundingBox().expand(range)).stream()
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
                    double dx=target.getX()-player.getX(), dy=target.getEyeY()-player.getEyeY(),
                           dz=target.getZ()-player.getZ(), hz=Math.sqrt(dx*dx+dz*dz);
                    float yaw=(float)(Math.toDegrees(Math.atan2(dz,dx))-90),
                          pitch=(float)(-Math.toDegrees(Math.atan2(dy,hz)));
                    player.setYaw(yaw); player.setPitch(pitch);
                    player.networkHandler.sendPacket(
                        new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, player.isOnGround()));
                }
                player.networkHandler.sendPacket(
                    PlayerInteractEntityC2SPacket.attack(target, player.isSneaking()));
                player.swingHand(Hand.MAIN_HAND);
                killAuraCooldown = delay;
            }
        }

        // ── TRIGGER BOT ──────────────────────────────────────────
        if (ModuleManager.isEnabled("TriggerBot")) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.targetedEntity instanceof LivingEntity le && !le.isDead() && killAuraCooldown == 0) {
                player.networkHandler.sendPacket(
                    PlayerInteractEntityC2SPacket.attack(mc.targetedEntity, player.isSneaking()));
                player.swingHand(Hand.MAIN_HAND);
                killAuraCooldown = ModuleManager.get("TriggerBot").getIntSetting("delay", 4);
            }
        }

        // ── AUTO WEAPON ──────────────────────────────────────────
        if (ModuleManager.isEnabled("AutoWeapon") && killAuraCooldown == 0) {
            Module aw = ModuleManager.get("AutoWeapon");
            boolean preferSword = aw.getSetting("preferSword");
            for (int i = 0; i < 9; i++) {
                ItemStack s = player.getInventory().main.get(i);
                if (preferSword && s.getItem() instanceof SwordItem) {
                    player.getInventory().selectedSlot = i; break;
                } else if (!preferSword && s.getItem() instanceof AxeItem) {
                    player.getInventory().selectedSlot = i; break;
                }
            }
        }

        // ── AUTO GAP ─────────────────────────────────────────────
        if (ModuleManager.isEnabled("AutoGap")) {
            float thr = ModuleManager.get("AutoGap").getFloatSetting("threshold", 12.0f);
            if (player.getHealth() < thr && autoEatCooldown == 0) {
                for (int i = 0; i < 9; i++) {
                    ItemStack s = player.getInventory().main.get(i);
                    if (s.getItem() == Items.GOLDEN_APPLE || s.getItem() == Items.ENCHANTED_GOLDEN_APPLE) {
                        player.getInventory().selectedSlot = i;
                        player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN, 0));
                        autoEatCooldown = 20; break;
                    }
                }
            }
        }

        // ── AUTO EAT ─────────────────────────────────────────────
        if (autoEatCooldown > 0) autoEatCooldown--;
        if (ModuleManager.isEnabled("AutoEat") && autoEatCooldown == 0) {
            int threshold = ModuleManager.get("AutoEat").getIntSetting("threshold", 16);
            if (player.getHungerManager().getFoodLevel() < threshold) {
                for (int i = 0; i < 9; i++) {
                    ItemStack s = player.getInventory().main.get(i);
                    if (!s.isEmpty() && s.getItem().isFood()) {
                        player.getInventory().selectedSlot = i;
                        player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN, 0));
                        autoEatCooldown = 20; break;
                    }
                }
            }
        }

        // ── REGEN ────────────────────────────────────────────────
        if (ModuleManager.isEnabled("Regen")) {
            float rate = ModuleManager.get("Regen").getFloatSetting("rate", 0.3f);
            if (player.getHealth() < player.getMaxHealth())
                player.setHealth(Math.min(player.getHealth() + rate, player.getMaxHealth()));
        }

        // ── AUTO TOTEM ───────────────────────────────────────────
        if (ModuleManager.isEnabled("AutoTotem")) {
            int threshold = ModuleManager.get("AutoTotem").getIntSetting("threshold", 8);
            if (player.getHealth() <= threshold) {
                ItemStack offhand = player.getInventory().offHand.get(0);
                if (offhand.getItem() != Items.TOTEM_OF_UNDYING) {
                    for (int i = 0; i < player.getInventory().main.size(); i++) {
                        ItemStack s = player.getInventory().main.get(i);
                        if (s.getItem() == Items.TOTEM_OF_UNDYING) {
                            player.getInventory().offHand.set(0, s.copy());
                            player.getInventory().main.set(i, offhand.copy()); break;
                        }
                    }
                }
            }
        }

        // ── AUTO LOG ─────────────────────────────────────────────
        if (ModuleManager.isEnabled("AutoLog")) {
            float thr = ModuleManager.get("AutoLog").getFloatSetting("threshold", 6.0f);
            if (player.getHealth() <= thr) {
                MinecraftClient.getInstance().getNetworkHandler().sendCommand("disconnect");
                player.networkHandler.getConnection().disconnect(
                    net.minecraft.text.Text.literal("§c[FlexClient] AutoLog - düşük sağlık!"));
            }
        }

        // ── FULLBRIGHT ───────────────────────────────────────────
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.options != null) {
                if (ModuleManager.isEnabled("Fullbright")) {
                    mc.options.getGamma().setValue(
                        (double) ModuleManager.get("Fullbright").getFloatSetting("gamma", 16.0f));
                } else if ((double) mc.options.getGamma().getValue() > 1.1) {
                    mc.options.getGamma().setValue(1.0);
                }
            }
        } catch (Exception ignored) {}

        // ── NUKER ────────────────────────────────────────────────
        if (ModuleManager.isEnabled("Nuker")) {
            int r = ModuleManager.get("Nuker").getIntSetting("range", 3);
            BlockPos center = player.getBlockPos();
            outer:
            for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
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

        // ── SCAFFOLD ─────────────────────────────────────────────
        if (ModuleManager.isEnabled("Scaffold") && !player.isOnGround()) {
            BlockPos below = player.getBlockPos().down();
            if (player.getWorld().getBlockState(below).isAir()) {
                for (int i = 0; i < 9; i++) {
                    ItemStack s = player.getInventory().main.get(i);
                    if (!s.isEmpty() && s.getItem() instanceof BlockItem) {
                        player.getInventory().selectedSlot = i;
                        player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(
                            Hand.MAIN_HAND,
                            new BlockHitResult(Vec3d.ofCenter(below.up()), Direction.UP, below, false), 0));
                        break;
                    }
                }
            }
        }

        // ── AUTO TOOL ────────────────────────────────────────────
        if (ModuleManager.isEnabled("AutoTool")) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult bhr) {
                BlockPos bp = bhr.getBlockPos();
                net.minecraft.block.BlockState bs = player.getWorld().getBlockState(bp);
                int bestSlot = -1; float bestSpeed = -1;
                for (int i = 0; i < 9; i++) {
                    float speed = player.getInventory().main.get(i)
                        .getMiningSpeedMultiplier(bs);
                    if (speed > bestSpeed) { bestSpeed = speed; bestSlot = i; }
                }
                if (bestSlot >= 0) player.getInventory().selectedSlot = bestSlot;
            }
        }

        // ── ANTI AFK ─────────────────────────────────────────────
        if (ModuleManager.isEnabled("AntiAFK")) {
            Module aa = ModuleManager.get("AntiAFK");
            int interval = aa.getIntSetting("interval", 60);
            antiAfkTick++;
            if (antiAfkTick >= interval) {
                antiAfkTick = 0;
                if (aa.getSetting("rotate")) {
                    player.setYaw(player.getYaw() + (rng.nextFloat() * 10 - 5));
                }
                if (aa.getSetting("swing")) {
                    player.swingHand(Hand.MAIN_HAND);
                }
                // Kısa ileri-geri hareket simüle et
                player.addVelocity(rng.nextFloat() * 0.02 - 0.01, 0, rng.nextFloat() * 0.02 - 0.01);
            }
        }
    }

    // ── Yardımcı ─────────────────────────────────────────────────
    private static float wrapAngle(float a) {
        while (a >  180) a -= 360;
        while (a < -180) a += 360;
        return a;
    }
    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
