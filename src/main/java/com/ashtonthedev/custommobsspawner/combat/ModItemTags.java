package com.ashtonthedev.custommobsspawner.combat;

import com.ashtonthedev.custommobsspawner.CustomMobsSpawner;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;

public final class ModItemTags {
    public static final TagKey<Item> PARRY_SWORDS = TagKey.of(RegistryKeys.ITEM, CustomMobsSpawner.id("parry_swords"));
    public static final TagKey<Item> SWORDS = TagKey.of(RegistryKeys.ITEM, CustomMobsSpawner.id("swords"));
    public static final TagKey<Item> KNIVES = TagKey.of(RegistryKeys.ITEM, CustomMobsSpawner.id("knives"));
    public static final TagKey<Item> RAPIERS = TagKey.of(RegistryKeys.ITEM, CustomMobsSpawner.id("rapiers"));
    public static final TagKey<Item> MANA_CAPACITORS = TagKey.of(RegistryKeys.ITEM, CustomMobsSpawner.id("mana_capacitors"));

    private ModItemTags() {
    }
}
