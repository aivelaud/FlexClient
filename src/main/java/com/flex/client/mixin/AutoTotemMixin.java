package com.flex.client.mixin;

import com.flex.client.module.Module;
import com.flex.client.module.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * AutoTotemMixin — 1.20.1 Fabric uyumlu.
 *
 * Offhand'de totem yokken envanterdeki ilk totemi otomatik olarak
 * offhand slotuna taşır. Totem slotu (PlayerScreenHandler):
 *   - Hotbar:         inventory slot 0-8  → screen slot 36-44
 *   - Main inventory: inventory slot 9-35 → screen slot 9-35
 *   - Offhand:                             screen slot 45
 */
@Mixin(ClientPlayerEntity.class)
public abstract class AutoTotemMixin {

    private int autoTotemTick = 0;

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        if (!ModuleManager.isEnabled("AutoTotem")) return;

        ClientPlayerEntity player = (ClientPlayerEntity)(Object)this;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.interactionManager == null) return;

        // Throttle: check every 5 ticks
        if (++autoTotemTick % 5 != 0) return;

        // Offhand zaten totem ise bir şey yapma
        if (player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) return;

        Module m = ModuleManager.get("AutoTotem");
        int threshold = (m != null) ? m.getIntSetting("threshold", 8) : 8;

        // Sadece düşük can veya offhand boşsa değiştir
        boolean shouldSwap = player.getOffHandStack().isEmpty()
                || player.getHealth() <= threshold;
        if (!shouldSwap) return;

        int syncId = player.playerScreenHandler.syncId;

        // Envanteri tara — totem bul
        for (int invSlot = 0; invSlot < player.getInventory().main.size(); invSlot++) {
            if (player.getInventory().main.get(invSlot).getItem() != Items.TOTEM_OF_UNDYING)
                continue;

            // inventory slot → PlayerScreenHandler slot
            int screenSlot = invSlot < 9 ? (36 + invSlot) : invSlot;

            // 1) Totemi cursor'a al
            mc.interactionManager.clickSlot(syncId, screenSlot, 0, SlotActionType.PICKUP, player);
            // 2) Offhand slotuna bırak
            mc.interactionManager.clickSlot(syncId, 45, 0, SlotActionType.PICKUP, player);
            // 3) Eğer cursor'da hâlâ bir şey varsa (eski offhand itemı) aynı slota geri bırak
            if (!player.playerScreenHandler.getCursorStack().isEmpty()) {
                mc.interactionManager.clickSlot(syncId, screenSlot, 0, SlotActionType.PICKUP, player);
            }
            break;
        }
    }
}
