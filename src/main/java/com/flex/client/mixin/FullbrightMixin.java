package com.flex.client.mixin;

import com.flex.client.module.ModuleManager;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.texture.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FullbrightMixin — LightmapTextureManager'e inject ederek tam parlaklık.
 * Gamma option trick çalışmadığında bu doğrudan lightmap piksellerini maxlar.
 */
@Mixin(LightmapTextureManager.class)
public class FullbrightMixin {

    @Shadow private boolean dirty;
    @Shadow private net.minecraft.client.texture.NativeImageBackedTexture texture;

    @Inject(method = "update", at = @At("RETURN"))
    private void onUpdate(float delta, CallbackInfo ci) {
        if (!ModuleManager.isEnabled("Fullbright")) return;
        LightmapTextureManager self = (LightmapTextureManager)(Object)this;
        NativeImage img = texture.getImage();
        if (img == null) return;
        int width = img.getWidth();
        int height = img.getHeight();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                img.setColor(x, y, 0xFFFFFFFF);
            }
        }
        texture.upload();
        dirty = false;
    }
}
