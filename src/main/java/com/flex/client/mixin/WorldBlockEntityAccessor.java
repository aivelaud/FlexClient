package com.flex.client.mixin;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Set;

@Mixin(World.class)
public interface WorldBlockEntityAccessor {
    @Accessor("blockEntities")
    Set<BlockEntity> getBlockEntities();
}
