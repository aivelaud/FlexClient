package com.flex.client.mixin;

import com.flex.client.module.Module;
import com.flex.client.module.ModuleManager;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(ClientPlayerEntity.class)
public abstract class CrystalAuraMixin {

    private int crystalAttackDelay = 0;
    private int crystalPlaceDelay  = 0;

    @Inject(at = @At("HEAD"), method = "tick()V")
    private void onCrystalTick(CallbackInfo ci) {
        if (!ModuleManager.isEnabled("CrystalAura")) return;
        ClientPlayerEntity player = (ClientPlayerEntity)(Object)this;
        if (player == null || player.getWorld() == null || player.networkHandler == null) return;

        Module m      = ModuleManager.get("CrystalAura");
        int    range  = m.getIntSetting("range", 5);
        int    delay  = m.getIntSetting("delay", 3);
        float  minDmg = m.getFloatSetting("minDamage", 6.0f);
        float  safe   = m.getFloatSetting("selfSafe", 8.0f);
        boolean place = m.getSetting("autoPlace");
        boolean atk   = m.getSetting("autoAttack");
        boolean hitP  = m.getSetting("hitPlayers");

        // ── 1. Kristalleri patlat ────────────────────────────────
        if (atk) {
            if (crystalAttackDelay > 0) { crystalAttackDelay--; }
            else {
                EndCrystalEntity best = null;
                float bestScore = Float.MIN_VALUE;

                for (Entity e : player.getWorld().getOtherEntities(player,
                        player.getBoundingBox().expand(range + 2))) {
                    if (!(e instanceof EndCrystalEntity ec)) continue;
                    Vec3d pos   = ec.getPos();
                    float tDmg  = maxTargetDamage(player, pos, range, hitP);
                    float sDmg  = crystalDamage((float) player.getPos().distanceTo(pos));
                    if (tDmg >= minDmg && sDmg <= safe) {
                        float sc = tDmg - sDmg * 0.5f;
                        if (sc > bestScore) { bestScore = sc; best = ec; }
                    }
                }

                if (best != null) {
                    player.networkHandler.sendPacket(
                        PlayerInteractEntityC2SPacket.attack(best, player.isSneaking()));
                    player.swingHand(Hand.MAIN_HAND);
                    crystalAttackDelay = delay;
                }
            }
        }

        // ── 2. Kristal yerleştir ─────────────────────────────────
        if (place) {
            if (crystalPlaceDelay > 0) { crystalPlaceDelay--; }
            else {
                // End Crystal elimizde mi?
                int crystalSlot = -1;
                for (int i = 0; i < 9; i++) {
                    if (player.getInventory().main.get(i).getItem() == Items.END_CRYSTAL) {
                        crystalSlot = i; break;
                    }
                }
                if (crystalSlot < 0) return;
                player.getInventory().selectedSlot = crystalSlot;

                BlockPos bestPos = null;
                float bestScore  = Float.MIN_VALUE;
                BlockPos pPos    = player.getBlockPos();

                for (int x = -range; x <= range; x++)
                for (int z = -range; z <= range; z++)
                for (int y = -1; y <= 2; y++) {
                    BlockPos bp  = pPos.add(x, y, z);
                    BlockPos top = bp.up();
                    var bs = player.getWorld().getBlockState(bp);
                    if (bs.getBlock() != Blocks.OBSIDIAN && bs.getBlock() != Blocks.BEDROCK) continue;
                    if (!player.getWorld().getBlockState(top).isAir()) continue;
                    if (!player.getWorld().getBlockState(top.up()).isAir()) continue;

                    Vec3d crystalPos = Vec3d.ofBottomCenter(top);
                    float tDmg  = maxTargetDamage(player, crystalPos, range + 2, hitP);
                    float sDmg  = crystalDamage((float) player.getPos().distanceTo(crystalPos));
                    if (tDmg >= minDmg && sDmg <= safe) {
                        float sc = tDmg - sDmg * 0.5f;
                        if (sc > bestScore) { bestScore = sc; bestPos = bp; }
                    }
                }

                if (bestPos != null) {
                    player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(
                        Hand.MAIN_HAND,
                        new BlockHitResult(Vec3d.ofCenter(bestPos.up()), Direction.UP, bestPos, false),
                        0));
                    crystalPlaceDelay = delay + 1;
                }
            }
        }
    }

    private float maxTargetDamage(ClientPlayerEntity player, Vec3d explPos, int range, boolean hitP) {
        float max = 0;
        for (Entity e : player.getWorld().getOtherEntities(player,
                player.getBoundingBox().expand(range))) {
            if (!(e instanceof LivingEntity le) || le.isDead()) continue;
            if (!(e instanceof Monster) && !(hitP && e instanceof PlayerEntity)) continue;
            float d = (float) e.getPos().distanceTo(explPos);
            float dmg = crystalDamage(d);
            if (dmg > max) max = dmg;
        }
        return max;
    }

    /** Basit patlama hasar tahmini (6.0 yarıçap) */
    private static float crystalDamage(float dist) {
        float radius = 6.0f;
        if (dist >= radius * 2) return 0f;
        float exposure = 1f - (dist / (radius * 2));
        return exposure * exposure * 97f + 1f;
    }
}
