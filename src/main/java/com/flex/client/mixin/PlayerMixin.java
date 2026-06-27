package com.flex.client.mixin;

import com.flex.client.module.Module;
import com.flex.client.module.ModuleManager;
import com.flex.client.CrashGuard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;
import java.util.stream.Collectors;

@Mixin(ClientPlayerEntity.class)
public abstract class PlayerMixin {

    private int  killAuraCooldown  = 0;
    private int  autoEatCooldown   = 0;
    private int  antiAfkTick       = 0;
    private int  kaTargetIndex     = 0;
    private boolean wasOnGround    = false;
    private boolean longJumpReady  = false;
    private boolean sprintedThisTick = false;
    private final Random rng       = new Random();

    @Inject(at = @At("HEAD"), method = "tick()V")
    private void onTick(CallbackInfo ci) {
        ClientPlayerEntity player = (ClientPlayerEntity)(Object)this;
        if (player.getWorld() == null || player.networkHandler == null) return;
        CrashGuard.tick();
        MinecraftClient mc = MinecraftClient.getInstance();

        // ── FLY ─────────────────────────────────────────────────────
        if (ModuleManager.isEnabled("Fly")) {
            Module fly = ModuleManager.get("Fly");
            String mode = fly.getStringSetting("mode", "Vanilla");
            float spd = fly.getFloatSetting("speed", 0.25f);
            if ("Vanilla".equals(mode) || "Creative".equals(mode)) {
                player.getAbilities().flying = true;
                player.getAbilities().setFlySpeed(spd);
            } else if ("Packet".equals(mode)) {
                // Packet fly: hızlı hareket + fake ground paketleri
                player.getAbilities().flying = false;
                Vec3d vel = player.getVelocity();
                double yawRad = Math.toRadians(player.getYaw());
                boolean moving = mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed()
                              || mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed();
                if (moving) {
                    double vx = -Math.sin(yawRad) * spd;
                    double vz =  Math.cos(yawRad) * spd;
                    double vy = mc.options.jumpKey.isPressed() ? spd : mc.options.sneakKey.isPressed() ? -spd : 0;
                    player.setVelocity(vx, vy, vz);
                } else {
                    player.setVelocity(0, mc.options.jumpKey.isPressed() ? spd : mc.options.sneakKey.isPressed() ? -spd : 0, 0);
                }
                // Fake on-ground packet every 4 ticks to bypass some anti-cheats
                if (antiAfkTick % 4 == 0) {
                    player.networkHandler.sendPacket(
                        new PlayerMoveC2SPacket.OnGroundOnly(true));
                }
            }
        } else if (player.getAbilities().flying && !player.getAbilities().allowFlying) {
            player.getAbilities().flying = false;
            player.getAbilities().setFlySpeed(0.05f);
        }

        // ── SPEED ────────────────────────────────────────────────────
        if (ModuleManager.isEnabled("Speed") && !ModuleManager.isEnabled("Fly")) {
            Module speed = ModuleManager.get("Speed");
            String sMode = speed.getStringSetting("mode", "Strafe");
            float spd = speed.getFloatSetting("speed", 0.35f);
            player.setSprinting(true);

            if ("Strafe".equals(sMode) && player.isOnGround()) {
                // Strafe: doğrudan velocity ayarla
                double yawRad = Math.toRadians(player.getYaw());
                boolean moving = mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed()
                              || mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed();
                if (moving) {
                    player.setVelocity(
                        -Math.sin(yawRad) * spd,
                        player.getVelocity().y,
                         Math.cos(yawRad) * spd
                    );
                }
            } else if ("YPort".equals(sMode)) {
                // YPort: sunucu bypass - yerde gibi göster, hız ekle
                double yawRad = Math.toRadians(player.getYaw());
                player.setVelocity(
                    -Math.sin(yawRad) * spd,
                    player.isOnGround() ? 0.42 : player.getVelocity().y,
                     Math.cos(yawRad) * spd
                );
                if (antiAfkTick % 2 == 0) {
                    player.networkHandler.sendPacket(
                        new PlayerMoveC2SPacket.PositionAndOnGround(
                            player.getX(), player.getY(), player.getZ(), true));
                }
            } else if ("Ground".equals(sMode)) {
                // Ground: abilities hızını ayarla
                player.getAbilities().setWalkSpeed(spd);
            }
        } else if (!ModuleManager.isEnabled("Speed")) {
            player.getAbilities().setWalkSpeed(0.1f);
        }

        // ── SPRINT ───────────────────────────────────────────────────
        if (ModuleManager.isEnabled("Sprint") && !player.isSneaking()) {
            player.setSprinting(true);
        }

        // ── NO FALL ──────────────────────────────────────────────────
        // Packet interception is handled by NoFallMixin
        // Also clear fall distance client-side as backup
        if (ModuleManager.isEnabled("NoFall")) {
            player.fallDistance = 0f;
        }

        // ── SAFE WALK ────────────────────────────────────────────────
        if (ModuleManager.isEnabled("SafeWalk")) player.setSneaking(true);

        // ── NO SLOW ──────────────────────────────────────────────────
        if (ModuleManager.isEnabled("NoSlow") && player.isUsingItem()) {
            player.getAbilities().setWalkSpeed(ModuleManager.isEnabled("Speed")
                ? ModuleManager.get("Speed").getFloatSetting("speed", 0.35f) : 0.1f);
        }

        // ── CLIP (NoClip / Phase) ────────────────────────────────────
        ((EntityNoClipAccessor)(Object)this).setNoClip(ModuleManager.isEnabled("Clip"));

        // ── STEP ──────────────────────────────────────────────────────
        StepHeightAccessor sa = (StepHeightAccessor)(Object)this;
        sa.setStepHeight(ModuleManager.isEnabled("Step")
            ? ModuleManager.get("Step").getFloatSetting("height", 2.5f) : 0.6f);

        // ── VCLIP ─────────────────────────────────────────────────────
        if (ModuleManager.isEnabled("VClip")) {
            Module vc = ModuleManager.get("VClip");
            float dist = vc.getFloatSetting("distance", 5.0f);
            boolean up = vc.getSetting("up");
            double newY = player.getY() + (up ? dist : -dist);
            player.networkHandler.sendPacket(
                new PlayerMoveC2SPacket.PositionAndOnGround(
                    player.getX(), newY, player.getZ(), false));
            player.refreshPositionAndAngles(player.getX(), newY, player.getZ(),
                player.getYaw(), player.getPitch());
            ModuleManager.get("VClip").setSetting("active", false); // one-shot
            ModuleManager.get("VClip").setEnabled(false);
        }

        // ── BUNNY HOP ────────────────────────────────────────────────
        if (ModuleManager.isEnabled("BunnyHop") && player.isOnGround()) {
            player.addVelocity(0, 0.42 + ModuleManager.get("BunnyHop").getFloatSetting("boost", 0.03f), 0);
            player.setSprinting(true);
        }

        // ── LONG JUMP ────────────────────────────────────────────────
        if (ModuleManager.isEnabled("LongJump")) {
            if (player.isOnGround()) longJumpReady = true;
            if (longJumpReady && !player.isOnGround() && wasOnGround) {
                float b = ModuleManager.get("LongJump").getFloatSetting("boost", 0.8f);
                double y = Math.toRadians(player.getYaw());
                player.addVelocity(-Math.sin(y) * b, 0, Math.cos(y) * b);
                longJumpReady = false;
            }
        }
        wasOnGround = player.isOnGround();

        // ── SPIDER ───────────────────────────────────────────────────
        if (ModuleManager.isEnabled("Spider") && !player.isOnGround()) {
            float spd = ModuleManager.get("Spider").getFloatSetting("speed", 0.3f);
            if (player.horizontalCollision) {
                player.setVelocity(player.getVelocity().x, spd, player.getVelocity().z);
            }
        }

        // ── HIGH JUMP ────────────────────────────────────────────────
        if (ModuleManager.isEnabled("HighJump") && player.isOnGround()) {
            float boost = ModuleManager.get("HighJump").getFloatSetting("boost", 0.5f);
            if (mc.options.jumpKey.isPressed()) {
                player.addVelocity(0, boost, 0);
            }
        }

        // ── ANTI VOID ────────────────────────────────────────────────
        if (ModuleManager.isEnabled("AntiVoid")) {
            Module av = ModuleManager.get("AntiVoid");
            float safeY = av.getFloatSetting("safeY", 0f);
            if (player.getY() < safeY + 5 && player.getVelocity().y < -0.5) {
                if (av.getSetting("slowFall")) {
                    player.setVelocity(player.getVelocity().x, -0.1, player.getVelocity().z);
                    player.networkHandler.sendPacket(
                        new PlayerMoveC2SPacket.PositionAndOnGround(
                            player.getX(), player.getY(), player.getZ(), true));
                } else {
                    player.setVelocity(player.getVelocity().x, 0, player.getVelocity().z);
                }
            }
        }

        // ── PARKOUR ──────────────────────────────────────────────────
        if (ModuleManager.isEnabled("Parkour") && player.isOnGround()) {
            BlockPos front = player.getBlockPos().offset(player.getHorizontalFacing()).down();
            if (player.getWorld().getBlockState(front).isAir()) {
                player.addVelocity(0, 0.42, 0);
            }
        }

        // ── VELOCITY ─────────────────────────────────────────────────
        if (ModuleManager.isEnabled("Velocity")) {
            Module vel = ModuleManager.get("Velocity");
            float h = vel.getFloatSetting("horizontal", 0.15f);
            float v = vel.getFloatSetting("vertical", 1.0f);
            Vec3d playerVel = player.getVelocity();
            double hz = Math.sqrt(playerVel.x * playerVel.x + playerVel.z * playerVel.z);
            if (hz > 0.3 && !ModuleManager.isEnabled("Fly") && !ModuleManager.isEnabled("Speed")) {
                player.setVelocity(playerVel.x * h, playerVel.y * v, playerVel.z * h);
            }
        }

        // ── ANTI KNOCKBACK ───────────────────────────────────────────
        if (ModuleManager.isEnabled("AntiKnockback")) {
            Vec3d playerVel = player.getVelocity();
            double hz = Math.sqrt(playerVel.x * playerVel.x + playerVel.z * playerVel.z);
            if (hz > 0.3 && !ModuleManager.isEnabled("Fly") && !ModuleManager.isEnabled("Speed")) {
                float amt = ModuleManager.get("AntiKnockback").getFloatSetting("amount", 0.0f);
                player.setVelocity(playerVel.x * amt, playerVel.y, playerVel.z * amt);
            }
        }

        // ── CRITICALS ────────────────────────────────────────────────
        // Packet mode criticals — handled in KillAura before attack
        if (ModuleManager.isEnabled("Criticals")) {
            String mode = ModuleManager.get("Criticals").getStringSetting("mode", "Packet");
            if ("Jump".equals(mode) && player.isOnGround() && !player.isTouchingWater() && killAuraCooldown == 0) {
                player.addVelocity(0, 0.42, 0);
            }
        }

        // ── AIM ASSIST ───────────────────────────────────────────────
        if (ModuleManager.isEnabled("AimAssist")) {
            Module aa = ModuleManager.get("AimAssist");
            int range = aa.getIntSetting("range", 5);
            float aimSpeed = aa.getFloatSetting("speed", 3.0f);
            float fovAngle = aa.getFloatSetting("fovAngle", 90.0f);

            Entity nearest = null;
            double nearestDist = Double.MAX_VALUE;

            for (Entity e : player.getWorld().getOtherEntities(player, player.getBoundingBox().expand(range))) {
                if (!(e instanceof LivingEntity le) || le.isDead()) continue;
                boolean isP = e instanceof AbstractClientPlayerEntity;
                boolean isM = e instanceof Monster;
                boolean isA = e instanceof AnimalEntity;
                if (isP && !aa.getSetting("hitPlayers")) continue;
                if (isM && !aa.getSetting("hitMobs")) continue;
                if (!isP && !isM && !isA) continue;

                // FOV check
                if (aa.getSetting("fov")) {
                    double dx = e.getX() - player.getX();
                    double dz = e.getZ() - player.getZ();
                    double dy = e.getEyeY() - player.getEyeY();
                    double hz = Math.sqrt(dx*dx + dz*dz);
                    float tYaw = (float)(Math.toDegrees(Math.atan2(dz,dx)) - 90);
                    float tPitch = (float)(-Math.toDegrees(Math.atan2(dy,hz)));
                    float yawDiff = Math.abs(wrapAngle(tYaw - player.getYaw()));
                    float pitchDiff = Math.abs(tPitch - player.getPitch());
                    if (yawDiff > fovAngle / 2 || pitchDiff > fovAngle / 2) continue;
                }

                double d = player.squaredDistanceTo(e);
                if (d < nearestDist) { nearestDist = d; nearest = e; }
            }

            if (nearest != null) {
                double dx = nearest.getX() - player.getX();
                double dy = nearest.getEyeY() - player.getEyeY();
                double dz = nearest.getZ() - player.getZ();
                double hz = Math.sqrt(dx*dx + dz*dz);
                float tYaw   = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90);
                float tPitch = (float)(-Math.toDegrees(Math.atan2(dy, hz)));
                // Smooth aim — Doomsday + Meteor hybrid: linear lerp with randomization
                float yawDelta   = wrapAngle(tYaw - player.getYaw());
                float pitchDelta = tPitch - player.getPitch();
                float noise = 0.1f * (rng.nextFloat() - 0.5f);
                player.setYaw(player.getYaw()   + clamp(yawDelta,   -aimSpeed, aimSpeed)   + noise);
                player.setPitch(player.getPitch() + clamp(pitchDelta, -aimSpeed * 0.6f, aimSpeed * 0.6f));
            }
        }

        // ── KILL AURA ─────────────────────────────────────────────────
        if (killAuraCooldown > 0) killAuraCooldown--;
        if (ModuleManager.isEnabled("KillAura") && killAuraCooldown == 0) {
            Module ka = ModuleManager.get("KillAura");
            int range  = ka.getIntSetting("range", 5);
            int delay  = ka.getIntSetting("delay", 3);
            String kMode = ka.getStringSetting("mode", "Single");

            List<Entity> targets = player.getWorld()
                .getOtherEntities(player, player.getBoundingBox().expand(range))
                .stream()
                .filter(e -> {
                    if (!(e instanceof LivingEntity le) || le.isDead()) return false;
                    boolean isMonster = e instanceof Monster;
                    boolean isAnimal  = e instanceof AnimalEntity;
                    boolean isPlayerE = e instanceof AbstractClientPlayerEntity;
                    return (ka.getSetting("hitMonsters") && isMonster)
                        || (ka.getSetting("hitAnimals")  && isAnimal)
                        || (ka.getSetting("hitPlayers")  && isPlayerE);
                })
                .sorted(Comparator.comparingDouble(e -> player.squaredDistanceTo(e)))
                .collect(Collectors.toList());

            if (!targets.isEmpty()) {
                // Attack cooldown check — 1.20.1'de kritik için gerekli
                float cooldown = player.getAttackCooldownProgress(0.5f);

                List<Entity> toHit;
                if ("Multi".equals(kMode)) {
                    toHit = targets;
                } else if ("Switch".equals(kMode)) {
                    kaTargetIndex = kaTargetIndex % targets.size();
                    toHit = Collections.singletonList(targets.get(kaTargetIndex));
                    kaTargetIndex++;
                } else {
                    toHit = Collections.singletonList(targets.get(0));
                }

                for (Entity target : toHit) {
                    // Rotate to target
                    if (ka.getSetting("rotate")) {
                        double dx = target.getX() - player.getX();
                        double dy = target.getEyeY() - player.getEyeY();
                        double dz = target.getZ() - player.getZ();
                        double hz = Math.sqrt(dx*dx + dz*dz);
                        float yaw   = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90);
                        float pitch = (float)(-Math.toDegrees(Math.atan2(dy, hz)));
                        player.setYaw(yaw);
                        player.setPitch(pitch);
                        player.networkHandler.sendPacket(
                            new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, player.isOnGround()));
                    }

                    // Criticals: Packet mode — send fake jump packets before attack
                    if (ModuleManager.isEnabled("Criticals")) {
                        String cMode = ModuleManager.get("Criticals").getStringSetting("mode", "Packet");
                        if (("Packet".equals(cMode) || "Always".equals(cMode))
                                && !player.isTouchingWater() && !player.isClimbing()) {
                            double px = player.getX(), py = player.getY(), pz = player.getZ();
                            player.networkHandler.sendPacket(
                                new PlayerMoveC2SPacket.PositionAndOnGround(px, py + 0.0625, pz, false));
                            player.networkHandler.sendPacket(
                                new PlayerMoveC2SPacket.PositionAndOnGround(px, py, pz, false));
                            player.networkHandler.sendPacket(
                                new PlayerMoveC2SPacket.PositionAndOnGround(px, py + 1.1E-5, pz, false));
                            player.networkHandler.sendPacket(
                                new PlayerMoveC2SPacket.PositionAndOnGround(px, py, pz, false));
                        }
                    }

                    // Attack — mc.interactionManager works in both SP and MP
                    if (mc.interactionManager != null) {
                        mc.interactionManager.attackEntity(player, target);
                    } else {
                        player.networkHandler.sendPacket(
                            PlayerInteractEntityC2SPacket.attack(target, player.isSneaking()));
                    }
                    if (ka.getSetting("swing")) player.swingHand(Hand.MAIN_HAND);
                }
                killAuraCooldown = delay + rng.nextInt(2);
            }
        }

        // ── TRIGGER BOT ──────────────────────────────────────────────
        if (ModuleManager.isEnabled("TriggerBot") && killAuraCooldown == 0) {
            Entity targeted = mc.targetedEntity;
            if (targeted instanceof LivingEntity le && !le.isDead()) {
                Module tb = ModuleManager.get("TriggerBot");
                boolean isP = targeted instanceof AbstractClientPlayerEntity;
                boolean isM = targeted instanceof Monster;
                boolean isA = targeted instanceof AnimalEntity;
                boolean should = (isP && tb.getSetting("hitPlayers"))
                              || (isM && tb.getSetting("hitMobs"))
                              || (isA && tb.getSetting("hitAnimals"));
                if (should) {
                    // Criticals for TriggerBot too
                    if (ModuleManager.isEnabled("Criticals")) {
                        String cMode = ModuleManager.get("Criticals").getStringSetting("mode","Packet");
                        if (("Packet".equals(cMode) || "Always".equals(cMode))
                                && !player.isTouchingWater()) {
                            double px=player.getX(), py=player.getY(), pz=player.getZ();
                            player.networkHandler.sendPacket(
                                new PlayerMoveC2SPacket.PositionAndOnGround(px,py+0.0625,pz,false));
                            player.networkHandler.sendPacket(
                                new PlayerMoveC2SPacket.PositionAndOnGround(px,py,pz,false));
                        }
                    }
                    if (mc.interactionManager != null) {
                        mc.interactionManager.attackEntity(player, targeted);
                    }
                    player.swingHand(Hand.MAIN_HAND);
                    if (tb.getSetting("autoSprint")) player.setSprinting(true);
                    int baseDelay = tb.getIntSetting("delay", 4);
                    int rand = tb.getIntSetting("randomDelay", 2);
                    killAuraCooldown = baseDelay + (rand > 0 ? rng.nextInt(rand) : 0);
                }
            }
        }

        // ── AUTO WEAPON ──────────────────────────────────────────────
        if (ModuleManager.isEnabled("AutoWeapon") && killAuraCooldown == 0) {
            boolean prefSword = ModuleManager.get("AutoWeapon").getSetting("preferSword");
            for (int i = 0; i < 9; i++) {
                Item it = player.getInventory().main.get(i).getItem();
                if (prefSword && it instanceof SwordItem) { player.getInventory().selectedSlot = i; break; }
                if (!prefSword && it instanceof AxeItem)  { player.getInventory().selectedSlot = i; break; }
            }
        }

        // ── AUTO GAP ─────────────────────────────────────────────────
        if (ModuleManager.isEnabled("AutoGap") && autoEatCooldown == 0) {
            float thr = ModuleManager.get("AutoGap").getFloatSetting("threshold", 12f);
            if (player.getHealth() < thr) {
                for (int i = 0; i < 9; i++) {
                    ItemStack s = player.getInventory().main.get(i);
                    if (s.getItem() == Items.GOLDEN_APPLE || s.getItem() == Items.ENCHANTED_GOLDEN_APPLE) {
                        player.getInventory().selectedSlot = i;
                        player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN, 0));
                        autoEatCooldown = 20;
                        break;
                    }
                }
            }
        }

        // ── AUTO EAT ─────────────────────────────────────────────────
        if (autoEatCooldown > 0) autoEatCooldown--;
        if (ModuleManager.isEnabled("AutoEat") && autoEatCooldown == 0) {
            int thr = ModuleManager.get("AutoEat").getIntSetting("threshold", 16);
            if (player.getHungerManager().getFoodLevel() < thr) {
                for (int i = 0; i < 9; i++) {
                    ItemStack s = player.getInventory().main.get(i);
                    if (!s.isEmpty() && s.getItem().isFood()) {
                        player.getInventory().selectedSlot = i;
                        player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN, 0));
                        autoEatCooldown = 20;
                        break;
                    }
                }
            }
        }

        // ── REGEN ────────────────────────────────────────────────────
        if (ModuleManager.isEnabled("Regen")) {
            float rate = ModuleManager.get("Regen").getFloatSetting("rate", 0.3f);
            if (player.getHealth() < player.getMaxHealth())
                player.setHealth(Math.min(player.getHealth() + rate, player.getMaxHealth()));
        }

        // ── AUTO TOTEM ───────────────────────────────────────────────
        if (ModuleManager.isEnabled("AutoTotem")) {
            int thr = ModuleManager.get("AutoTotem").getIntSetting("threshold", 8);
            if (player.getHealth() <= thr && player.getInventory().offHand.get(0).getItem() != Items.TOTEM_OF_UNDYING) {
                for (int i = 0; i < player.getInventory().main.size(); i++) {
                    ItemStack s = player.getInventory().main.get(i);
                    if (s.getItem() == Items.TOTEM_OF_UNDYING) {
                        ItemStack off = player.getInventory().offHand.get(0).copy();
                        player.getInventory().offHand.set(0, s.copy());
                        player.getInventory().main.set(i, off);
                        break;
                    }
                }
            }
        }

        // ── AUTO LOG ─────────────────────────────────────────────────
        if (ModuleManager.isEnabled("AutoLog")) {
            float thr = ModuleManager.get("AutoLog").getFloatSetting("threshold", 6f);
            if (player.getHealth() <= thr) {
                player.networkHandler.getConnection().disconnect(
                    net.minecraft.text.Text.literal("[FlexClient] AutoLog"));
            }
        }

        // ── CHEST STEALER ────────────────────────────────────────────
        if (ModuleManager.isEnabled("ChestStealer") && mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.GenericContainerScreen gcScreen) {
            GenericContainerScreenHandler handler = gcScreen.getScreenHandler();
            Module cs = ModuleManager.get("ChestStealer");
            int csDelay = cs.getIntSetting("delay", 2);
            if (autoEatCooldown % csDelay == 0) {
                for (int i = 0; i < handler.getRows() * 9; i++) {
                    ItemStack stack = handler.getSlot(i).getStack();
                    if (!stack.isEmpty()) {
                        // Quick-move (shift+click) to take item
                        player.networkHandler.sendPacket(new ClickSlotC2SPacket(
                            handler.syncId, handler.getRevision(), i, 0,
                            SlotActionType.QUICK_MOVE,
                            ItemStack.EMPTY,
                            new java.util.HashMap<>()
                        ));
                        break; // take one per tick
                    }
                }
            }
        }

        // ── FULLBRIGHT ───────────────────────────────────────────────
        // Handled by FullbrightMixin for proper lightmap fix
        // Backup: gamma option
        try {
            if (mc.options != null) {
                if (ModuleManager.isEnabled("Fullbright")) {
                    mc.options.getGamma().setValue(16.0);
                } else if ((double) mc.options.getGamma().getValue() > 1.1) {
                    mc.options.getGamma().setValue(1.0);
                }
            }
        } catch (Exception ignored) {}

        // ── NUKER ────────────────────────────────────────────────────
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

        // ── SCAFFOLD ─────────────────────────────────────────────────
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

        // ── AUTO TOOL ────────────────────────────────────────────────
        if (ModuleManager.isEnabled("AutoTool")) {
            if (mc.crosshairTarget instanceof BlockHitResult bhr) {
                var bs = player.getWorld().getBlockState(bhr.getBlockPos());
                int bestSlot = -1; float bestSpeed = -1;
                for (int i = 0; i < 9; i++) {
                    float sp = player.getInventory().main.get(i).getMiningSpeedMultiplier(bs);
                    if (sp > bestSpeed) { bestSpeed = sp; bestSlot = i; }
                }
                if (bestSlot >= 0) player.getInventory().selectedSlot = bestSlot;
            }
        }

        // ── ANTI AFK ─────────────────────────────────────────────────
        if (ModuleManager.isEnabled("AntiAFK")) {
            Module aa = ModuleManager.get("AntiAFK");
            int interval = aa.getIntSetting("interval", 60);
            if (++antiAfkTick >= interval) {
                antiAfkTick = 0;
                if (aa.getSetting("rotate")) player.setYaw(player.getYaw() + rng.nextFloat() * 10 - 5);
                if (aa.getSetting("swing"))  player.swingHand(Hand.MAIN_HAND);
                if (aa.getSetting("jump"))   player.addVelocity(0, 0.42, 0);
                player.addVelocity(rng.nextFloat() * 0.02f - 0.01f, 0, rng.nextFloat() * 0.02f - 0.01f);
            }
        } else {
            antiAfkTick++;
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
