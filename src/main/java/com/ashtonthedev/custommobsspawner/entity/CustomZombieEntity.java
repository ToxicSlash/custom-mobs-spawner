package com.ashtonthedev.custommobsspawner.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.world.World;

public class CustomZombieEntity extends ZombieEntity {
    public CustomZombieEntity(EntityType<? extends ZombieEntity> entityType, World world) {
        super(entityType, world);
    }
}
