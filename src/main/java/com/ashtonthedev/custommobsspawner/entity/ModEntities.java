package com.ashtonthedev.custommobsspawner.entity;

import com.ashtonthedev.custommobsspawner.CustomMobsSpawner;
import com.ashtonthedev.custommobsspawner.combat.ModAttributes;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModEntities {
    public static final EntityType<CustomZombieEntity> CUSTOM_ZOMBIE = Registry.register(
            Registries.ENTITY_TYPE,
            CustomMobsSpawner.id("custom_zombie"),
            FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, CustomZombieEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6F, 1.95F))
                    .trackRangeBlocks(8)
                    .build()
    );

    private ModEntities() {
    }

    public static void register() {
    }

    public static DefaultAttributeContainer.Builder createCustomZombieAttributes() {
        return ZombieEntity.createZombieAttributes()
                .add(ModAttributes.POSTURE_HEALTH, ModAttributes.DEFAULT_MOB_POSTURE_HEALTH)
                .add(ModAttributes.POSTURE_REGEN)
                .add(ModAttributes.POSTURE_DAMAGE_RESISTANCE);
    }
}
