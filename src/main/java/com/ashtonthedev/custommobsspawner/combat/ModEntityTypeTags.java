package com.ashtonthedev.custommobsspawner.combat;

import com.ashtonthedev.custommobsspawner.CustomMobsSpawner;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;

public final class ModEntityTypeTags {
    public static final TagKey<EntityType<?>> NO_STUN = TagKey.of(RegistryKeys.ENTITY_TYPE, CustomMobsSpawner.id("no_stun"));
    public static final TagKey<EntityType<?>> NO_POSTURE = TagKey.of(RegistryKeys.ENTITY_TYPE, CustomMobsSpawner.id("no_posture"));

    private ModEntityTypeTags() {
    }
}
