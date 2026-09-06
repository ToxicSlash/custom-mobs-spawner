package com.ashtonthedev.custommobsspawner.data;

import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

public record EntitySelector(boolean tag, Identifier id) {
    public static EntitySelector parse(String value) {
        if (value.startsWith("#")) {
            return new EntitySelector(true, new Identifier(value.substring(1)));
        }
        return new EntitySelector(false, new Identifier(value));
    }

    public boolean matches(EntityType<?> type) {
        if (tag) {
            return type.isIn(TagKey.of(RegistryKeys.ENTITY_TYPE, id));
        }
        return Registries.ENTITY_TYPE.getId(type).equals(id);
    }
}
