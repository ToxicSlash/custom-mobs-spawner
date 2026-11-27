package net.smitherz.init;

import java.util.ArrayList;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import net.smitherz.item.*;
import net.smitherz.item.attribute.GemEntityAttributeModifier;

public class ItemInit {

    // Item Group
    public static final RegistryKey<ItemGroup> SMITHERZ_ITEM_GROUP = RegistryKey.of(RegistryKeys.ITEM_GROUP, new Identifier("smitherz", "item_group"));
    // Lists
    public static final ArrayList<String> ITEM_IDS = new ArrayList<String>();

    public static final Item SMITHER_HAMMER_1 = register("smither_hammer_1", new Hammer(new Item.Settings().rarity(Rarity.UNCOMMON), 5f));
    public static final Item SMITHER_HAMMER_2 = register("smither_hammer_2", new Hammer(new Item.Settings().rarity(Rarity.UNCOMMON), 10f));
    public static final Item SMITHER_HAMMER_3 = register("smither_hammer_3", new Hammer(new Item.Settings().rarity(Rarity.UNCOMMON), 20f));
    public static final Item SMITHER_HAMMER_4 = register("smither_hammer_4", new Hammer(new Item.Settings().rarity(Rarity.UNCOMMON), 50f));

    public static final Item EXTRACTION_HAMMER_1 = register("extraction_hammer_1", new Hammer(new Item.Settings().rarity(Rarity.UNCOMMON), 5f));
    public static final Item EXTRACTION_HAMMER_2 = register("extraction_hammer_2", new Hammer(new Item.Settings().rarity(Rarity.UNCOMMON), 10f));
    public static final Item EXTRACTION_HAMMER_3 = register("extraction_hammer_3", new Hammer(new Item.Settings().rarity(Rarity.UNCOMMON), 20f));
    public static final Item EXTRACTION_HAMMER_4 = register("extraction_hammer_4", new Hammer(new Item.Settings().rarity(Rarity.UNCOMMON), 50f));

    public static Item STRENGTH_I;
    public static Item STRENGTH_II;
    public static Item STRENGTH_III;
    public static Item STRENGTH_IV;
    public static Item STRENGTH_V;

    public static Item DEFENSE_I;
    public static Item DEFENSE_II;
    public static Item DEFENSE_III;
    public static Item DEFENSE_IV;
    public static Item DEFENSE_V;

    public static Item HEALTH_I;
    public static Item HEALTH_II;
    public static Item HEALTH_III;
    public static Item HEALTH_IV;
    public static Item HEALTH_V;

    public static Item SPEED_I;
    public static Item SPEED_II;
    public static Item SPEED_III;
    public static Item SPEED_IV;
    public static Item SPEED_V;

    public static Item ATTACK_SPEED_I;
    public static Item ATTACK_SPEED_II;
    public static Item ATTACK_SPEED_III;
    public static Item ATTACK_SPEED_IV;
    public static Item ATTACK_SPEED_V;

    public static final ArrayList<ArrayList<Item>> ITEM_CATEGORIES = new ArrayList<ArrayList<Item>>();
    private static final String[] CUSTOM_GEM_TYPES = {
            "ruby",
            "sapphire",
            "topaz",
            "citrine",
            "jade",
            "tanzanite",
            "amethyst",
            "diamond",
            "emerald"
    };

    public static Item register(String id, Item item) {
        ITEM_IDS.add(id);
        return register(new Identifier("smitherz", id), item);
    }

    private static Item register(Identifier id, Item item) {
        ItemGroupEvents.modifyEntriesEvent(SMITHERZ_ITEM_GROUP).register(entries -> entries.add(item));
        return Registry.register(Registries.ITEM, id, item);
    }

    public static void init() {
        if (ConfigInit.CONFIG.defaultGems) {
            STRENGTH_I = register("strength_1_gem", new Gem(new Item.Settings(),
                    ImmutableMultimap.of(EntityAttributes.GENERIC_ATTACK_DAMAGE, new GemEntityAttributeModifier(EntityAttributes.GENERIC_ATTACK_DAMAGE.getTranslationKey(), 2, Operation.ADDITION)),
                    0.45f, 0.0f, 0.5f, TagInit.SWORDS));
            STRENGTH_II = register("strength_2_gem", new Gem(new Item.Settings(),
                    ImmutableMultimap.of(EntityAttributes.GENERIC_ATTACK_DAMAGE, new GemEntityAttributeModifier(EntityAttributes.GENERIC_ATTACK_DAMAGE.getTranslationKey(), 4, Operation.ADDITION)),
                    0.33f, 0.0f, 0.3f, TagInit.SWORDS));
            STRENGTH_III = register("strength_3_gem", new Gem(new Item.Settings(),
                    ImmutableMultimap.of(EntityAttributes.GENERIC_ATTACK_DAMAGE, new GemEntityAttributeModifier(EntityAttributes.GENERIC_ATTACK_DAMAGE.getTranslationKey(), 6, Operation.ADDITION)),
                    0.25f, 0.0f, 0.2f, TagInit.SWORDS));
            STRENGTH_IV = register("strength_4_gem", new Gem(new Item.Settings(),
                    ImmutableMultimap.of(EntityAttributes.GENERIC_ATTACK_DAMAGE, new GemEntityAttributeModifier(EntityAttributes.GENERIC_ATTACK_DAMAGE.getTranslationKey(), 8, Operation.ADDITION)),
                    0.09f, 0.01f, 0.05f, TagInit.SWORDS));
            STRENGTH_V = register("strength_5_gem", new Gem(new Item.Settings(),
                    ImmutableMultimap.of(EntityAttributes.GENERIC_ATTACK_DAMAGE, new GemEntityAttributeModifier(EntityAttributes.GENERIC_ATTACK_DAMAGE.getTranslationKey(), 12, Operation.ADDITION)),
                    0.01f, 0.05f, 0.01f, TagInit.SWORDS));

            DEFENSE_I = register("defense_1_gem",
                    new Gem(new Item.Settings(),
                            ImmutableMultimap.of(EntityAttributes.GENERIC_ARMOR, new GemEntityAttributeModifier(EntityAttributes.GENERIC_ARMOR.getTranslationKey(), 2, Operation.ADDITION)), 0.45f,
                            0.0f, 0.5f, null));
            DEFENSE_II = register("defense_2_gem",
                    new Gem(new Item.Settings(),
                            ImmutableMultimap.of(EntityAttributes.GENERIC_ARMOR, new GemEntityAttributeModifier(EntityAttributes.GENERIC_ARMOR.getTranslationKey(), 4, Operation.ADDITION)), 0.33f,
                            0.0f, 0.3f, null));
            DEFENSE_III = register("defense_3_gem",
                    new Gem(new Item.Settings(),
                            ImmutableMultimap.of(EntityAttributes.GENERIC_ARMOR, new GemEntityAttributeModifier(EntityAttributes.GENERIC_ARMOR.getTranslationKey(), 6, Operation.ADDITION)), 0.25f,
                            0.0f, 0.2f, null));
            DEFENSE_IV = register("defense_4_gem",
                    new Gem(new Item.Settings(),
                            ImmutableMultimap.of(EntityAttributes.GENERIC_ARMOR, new GemEntityAttributeModifier(EntityAttributes.GENERIC_ARMOR.getTranslationKey(), 8, Operation.ADDITION)), 0.09f,
                            0.01f, 0.05f, null));
            DEFENSE_V = register("defense_5_gem",
                    new Gem(new Item.Settings(),
                            ImmutableMultimap.of(EntityAttributes.GENERIC_ARMOR, new GemEntityAttributeModifier(EntityAttributes.GENERIC_ARMOR.getTranslationKey(), 10, Operation.ADDITION)), 0.01f,
                            0.05f, 0.01f, null));

            HEALTH_I = register("health_1_gem",
                    new Gem(new Item.Settings(),
                            ImmutableMultimap.of(EntityAttributes.GENERIC_MAX_HEALTH, new GemEntityAttributeModifier(EntityAttributes.GENERIC_MAX_HEALTH.getTranslationKey(), 2, Operation.ADDITION)),
                            0.45f, 0.0f, 0.5f, null));
            HEALTH_II = register("health_2_gem",
                    new Gem(new Item.Settings(),
                            ImmutableMultimap.of(EntityAttributes.GENERIC_MAX_HEALTH, new GemEntityAttributeModifier(EntityAttributes.GENERIC_MAX_HEALTH.getTranslationKey(), 4, Operation.ADDITION)),
                            0.33f, 0.0f, 0.3f, null));
            HEALTH_III = register("health_3_gem",
                    new Gem(new Item.Settings(),
                            ImmutableMultimap.of(EntityAttributes.GENERIC_MAX_HEALTH, new GemEntityAttributeModifier(EntityAttributes.GENERIC_MAX_HEALTH.getTranslationKey(), 6, Operation.ADDITION)),
                            0.25f, 0.0f, 0.2f, null));
            HEALTH_IV = register("health_4_gem",
                    new Gem(new Item.Settings(),
                            ImmutableMultimap.of(EntityAttributes.GENERIC_MAX_HEALTH, new GemEntityAttributeModifier(EntityAttributes.GENERIC_MAX_HEALTH.getTranslationKey(), 8, Operation.ADDITION)),
                            0.09f, 0.01f, 0.05f, null));
            HEALTH_V = register("health_5_gem",
                    new Gem(new Item.Settings(),
                            ImmutableMultimap.of(EntityAttributes.GENERIC_MAX_HEALTH, new GemEntityAttributeModifier(EntityAttributes.GENERIC_MAX_HEALTH.getTranslationKey(), 12, Operation.ADDITION)),
                            0.01f, 0.05f, 0.01f, null));

            SPEED_I = register("speed_1_gem", new Gem(new Item.Settings(), ImmutableMultimap.of(EntityAttributes.GENERIC_MOVEMENT_SPEED,
                    new GemEntityAttributeModifier(EntityAttributes.GENERIC_MOVEMENT_SPEED.getTranslationKey(), 0.005D, Operation.ADDITION)), 0.45f, 0.0f, 0.5f, TagInit.ARMOR));
            SPEED_II = register("speed_2_gem", new Gem(new Item.Settings(), ImmutableMultimap.of(EntityAttributes.GENERIC_MOVEMENT_SPEED,
                    new GemEntityAttributeModifier(EntityAttributes.GENERIC_MOVEMENT_SPEED.getTranslationKey(), 0.01D, Operation.ADDITION)), 0.33f, 0.0f, 0.3f, TagInit.ARMOR));
            SPEED_III = register("speed_3_gem", new Gem(new Item.Settings(), ImmutableMultimap.of(EntityAttributes.GENERIC_MOVEMENT_SPEED,
                    new GemEntityAttributeModifier(EntityAttributes.GENERIC_MOVEMENT_SPEED.getTranslationKey(), 0.015D, Operation.ADDITION)), 0.25f, 0.0f, 0.2f, TagInit.ARMOR));
            SPEED_IV = register("speed_4_gem", new Gem(new Item.Settings(), ImmutableMultimap.of(EntityAttributes.GENERIC_MOVEMENT_SPEED,
                    new GemEntityAttributeModifier(EntityAttributes.GENERIC_MOVEMENT_SPEED.getTranslationKey(), 0.02D, Operation.ADDITION)), 0.09f, 0.01f, 0.05f, TagInit.ARMOR));
            SPEED_V = register("speed_5_gem", new Gem(new Item.Settings(), ImmutableMultimap.of(EntityAttributes.GENERIC_MOVEMENT_SPEED,
                    new GemEntityAttributeModifier(EntityAttributes.GENERIC_MOVEMENT_SPEED.getTranslationKey(), 0.03D, Operation.ADDITION)), 0.01f, 0.05f, 0.01f, TagInit.ARMOR));

            ATTACK_SPEED_I = register("attack_speed_1_gem", new Gem(new Item.Settings(),
                    ImmutableMultimap.of(EntityAttributes.GENERIC_ATTACK_SPEED, new GemEntityAttributeModifier(EntityAttributes.GENERIC_ATTACK_SPEED.getTranslationKey(), 0.05D, Operation.ADDITION)),
                    0.45f, 0.0f, 0.5f, TagInit.TOOLS));
            ATTACK_SPEED_II = register("attack_speed_2_gem", new Gem(new Item.Settings(),
                    ImmutableMultimap.of(EntityAttributes.GENERIC_ATTACK_SPEED, new GemEntityAttributeModifier(EntityAttributes.GENERIC_ATTACK_SPEED.getTranslationKey(), 0.1D, Operation.ADDITION)),
                    0.33f, 0.0f, 0.3f, TagInit.TOOLS));
            ATTACK_SPEED_III = register("attack_speed_3_gem", new Gem(new Item.Settings(),
                    ImmutableMultimap.of(EntityAttributes.GENERIC_ATTACK_SPEED, new GemEntityAttributeModifier(EntityAttributes.GENERIC_ATTACK_SPEED.getTranslationKey(), 0.15D, Operation.ADDITION)),
                    0.25f, 0.0f, 0.2f, TagInit.TOOLS));
            ATTACK_SPEED_IV = register("attack_speed_4_gem", new Gem(new Item.Settings(),
                    ImmutableMultimap.of(EntityAttributes.GENERIC_ATTACK_SPEED, new GemEntityAttributeModifier(EntityAttributes.GENERIC_ATTACK_SPEED.getTranslationKey(), 0.2D, Operation.ADDITION)),
                    0.09f, 0.01f, 0.05f, TagInit.TOOLS));
            ATTACK_SPEED_V = register("attack_speed_5_gem", new Gem(new Item.Settings(),
                    ImmutableMultimap.of(EntityAttributes.GENERIC_ATTACK_SPEED, new GemEntityAttributeModifier(EntityAttributes.GENERIC_ATTACK_SPEED.getTranslationKey(), 0.3D, Operation.ADDITION)),
                    0.01f, 0.05f, 0.01f, TagInit.TOOLS));
        }
        registerCustomGems();
        Registry.register(Registries.ITEM_GROUP, SMITHERZ_ITEM_GROUP,
                FabricItemGroup.builder().icon(() -> new ItemStack(SMITHER_HAMMER_4)).displayName(Text.translatable("item.smitherz.item_group")).build());
    }

    private static void registerCustomGems() {
        for (String gemType : CUSTOM_GEM_TYPES) {
            for (int tier = 1; tier <= 5; tier++) {
                register("t" + tier + "_" + gemType, new Gem(
                        new Item.Settings().rarity(rarity(tier)),
                        attributes(gemType, tier),
                        linkChance(tier),
                        linkBreakChance(tier),
                        unlinkChance(tier),
                        null
                ));
            }
        }
    }

    private static Multimap<EntityAttribute, EntityAttributeModifier> attributes(String gemType, int tier) {
        if (tier != 1) {
            return ImmutableMultimap.of();
        }

        return switch (gemType) {
            case "ruby" -> attributes(
                    attribute(EntityAttributes.GENERIC_ATTACK_DAMAGE, 0.08D, Operation.MULTIPLY_BASE)
            );
            case "sapphire" -> attributes(
                    attribute("extraspellattributes:reabsorption", 6.0D, Operation.ADDITION)
            );
            case "diamond" -> attributes(
                    attribute(EntityAttributes.GENERIC_ARMOR, 6.0D, Operation.ADDITION)
            );
            case "emerald" -> attributes(
                    attribute("zenith_attributes:healing_received", 0.12D, Operation.ADDITION)
            );
            case "topaz" -> attributes(
                    attribute("spell_power:fire", 0.12D, Operation.MULTIPLY_BASE),
                    attribute("spell_power:arcane", 0.12D, Operation.MULTIPLY_BASE)
            );
            case "citrine" -> attributes(
                    attribute("spell_power:healing", 0.12D, Operation.MULTIPLY_BASE),
                    attribute("spell_power:lightning", 0.12D, Operation.MULTIPLY_BASE)
            );
            case "tanzanite" -> attributes(
                    attribute("spell_power:frost", 0.12D, Operation.MULTIPLY_BASE),
                    attribute("spell_power:soul", 0.12D, Operation.MULTIPLY_BASE)
            );
            case "jade" -> attributes(
                    attribute("ranged_weapon:damage", 0.08D, Operation.MULTIPLY_BASE)
            );
            case "amethyst" -> attributes(
                    attribute(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.08D, Operation.MULTIPLY_BASE)
            );
            default -> ImmutableMultimap.of();
        };
    }

    private static Multimap<EntityAttribute, EntityAttributeModifier> attributes(AttributeSpec... specs) {
        ImmutableMultimap.Builder<EntityAttribute, EntityAttributeModifier> builder = ImmutableMultimap.builder();
        for (AttributeSpec spec : specs) {
            if (spec.attribute() != null) {
                builder.put(spec.attribute(), new GemEntityAttributeModifier(spec.attribute().getTranslationKey(), spec.amount(), spec.operation()));
            }
        }
        return builder.build();
    }

    private static AttributeSpec attribute(EntityAttribute attribute, double amount, Operation operation) {
        return new AttributeSpec(attribute, amount, operation);
    }

    private static AttributeSpec attribute(String id, double amount, Operation operation) {
        return new AttributeSpec(Registries.ATTRIBUTE.get(new Identifier(id)), amount, operation);
    }

    private static float linkChance(int tier) {
        return switch (tier) {
            case 1 -> 0.45f;
            case 2 -> 0.33f;
            case 3 -> 0.25f;
            case 4 -> 0.09f;
            default -> 0.01f;
        };
    }

    private static float linkBreakChance(int tier) {
        return switch (tier) {
            case 4 -> 0.01f;
            case 5 -> 0.05f;
            default -> 0.0f;
        };
    }

    private static float unlinkChance(int tier) {
        return switch (tier) {
            case 1 -> 0.5f;
            case 2 -> 0.3f;
            case 3 -> 0.2f;
            case 4 -> 0.05f;
            default -> 0.01f;
        };
    }

    private static Rarity rarity(int tier) {
        return switch (tier) {
            case 5 -> Rarity.EPIC;
            case 4 -> Rarity.EPIC;
            case 3 -> Rarity.RARE;
            case 2 -> Rarity.UNCOMMON;
            default -> Rarity.UNCOMMON;
        };
    }

    private record AttributeSpec(EntityAttribute attribute, double amount, Operation operation) {
    }

}
