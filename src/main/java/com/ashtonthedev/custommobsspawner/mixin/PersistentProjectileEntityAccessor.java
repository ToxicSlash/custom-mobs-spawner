package com.ashtonthedev.custommobsspawner.mixin;

import net.minecraft.entity.projectile.PersistentProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PersistentProjectileEntity.class)
public interface PersistentProjectileEntityAccessor {
    @Accessor("inGround")
    boolean customMobsSpawner$isInGround();

    @Accessor("life")
    void customMobsSpawner$setLife(int life);
}
