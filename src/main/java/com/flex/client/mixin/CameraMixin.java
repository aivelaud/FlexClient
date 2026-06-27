package com.flex.client.mixin;

import com.flex.client.module.ModuleManager;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CameraMixin — NoHurtCam + Zoom + FreeLook destek noktasi
 * MC 1.20.1 Fabric — Camera.update inject
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Inject(method = "update", at = @At("RETURN"))
    private void afterCameraUpdate(BlockView area, Entity focusedEntity,
                                   boolean thirdPerson, boolean inverseView,
                                   float tickDelta, CallbackInfo ci) {
        if (focusedEntity == null) return;
        // NoHurtCam etkisi GameRendererMixin icinde hurtTime=0 ile uygulanir.
        // FreeLook icin gelecekte kamera offset buraya eklenir.
    }
}
