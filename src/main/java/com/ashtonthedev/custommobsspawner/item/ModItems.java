package com.ashtonthedev.custommobsspawner.item;

import com.ashtonthedev.custommobsspawner.CustomMobsSpawner;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import vazkii.botania.api.BotaniaFabricCapabilities;

public final class ModItems {
    public static final Item LESSER_MANA_POTION = Registry.register(
            Registries.ITEM,
            CustomMobsSpawner.id("lesser_mana_potion"),
            new CustomManaPotionItem(
                    new FabricItemSettings().maxCount(16),
                    25,
                    "item.cmobs.lesser_mana_potion.tooltip",
                    false
            )
    );
    public static final Item GREATER_MANA_POTION = Registry.register(
            Registries.ITEM,
            CustomMobsSpawner.id("greater_mana_potion"),
            new CustomManaPotionItem(
                    new FabricItemSettings().maxCount(16),
                    100,
                    "item.cmobs.greater_mana_potion.tooltip",
                    true
            )
    );
    public static final Item MANA_CAPACITOR = Registry.register(
            Registries.ITEM,
            CustomMobsSpawner.id("mana_capacitor"),
            new ManaCapacitorItem(new FabricItemSettings().maxCount(1))
    );

    private ModItems() {
    }

    public static void register() {
        BotaniaFabricCapabilities.MANA_ITEM.registerForItems(
                (stack, context) -> new ManaCapacitorItem.ManaItemImpl(stack),
                MANA_CAPACITOR
        );

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FOOD_AND_DRINK).register(entries -> {
            entries.add(LESSER_MANA_POTION);
            entries.add(GREATER_MANA_POTION);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(MANA_CAPACITOR));
    }
}
