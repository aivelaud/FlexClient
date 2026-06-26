package com.flex.client.mixin;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Entity.stepHeight (field_6013 / class_1542) doğrudan ClientPlayerEntity'de
 * declare edilmemiştir — bu yüzden PlayerMixin içinde @Shadow kullanamayız.
 * Bu accessor, alanı Entity seviyesinden okur/yazar.
 */
@Mixin(Entity.class)
public interface StepHeightAccessor {
    @Accessor("stepHeight")
    float getStepHeight();

    @Accessor("stepHeight")
    void setStepHeight(float value);
}
