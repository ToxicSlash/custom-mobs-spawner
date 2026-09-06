package com.ashtonthedev.custommobsspawner.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.entity.LivingEntity;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("lastAttackedTicks")
    void customMobsSpawner$setLastAttackedTicks(int ticks);
}
