package net.smitherz.init;

import io.github.apace100.autotag.common.TagIdentifiers;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

public class TagInit {
    // smitherz
    public static final TagKey<Item> GEMS = TagKey.of(RegistryKeys.ITEM, new Identifier("smitherz", "gems"));
    public static final TagKey<Item> BONUS_ITEMS = TagKey.of(RegistryKeys.ITEM, new Identifier("smitherz", "bonus_items"));
    public static final TagKey<Item> EXTRACTION_ITEMS = TagKey.of(RegistryKeys.ITEM, new Identifier("smitherz", "extraction_items"));
    public static final TagKey<Item> SOCKETABLE = TagKey.of(RegistryKeys.ITEM, new Identifier("smitherz", "socketable"));
    public static final TagKey<Item> DEFAULT_1_SOCKET = TagKey.of(RegistryKeys.ITEM, new Identifier("smitherz", "default_1_socket"));
    public static final TagKey<Item> DEFAULT_2_SOCKET = TagKey.of(RegistryKeys.ITEM, new Identifier("smitherz", "default_2_socket"));
    public static final TagKey<Item> DEFAULT_3_SOCKET = TagKey.of(RegistryKeys.ITEM, new Identifier("smitherz", "default_3_socket"));
    public static final TagKey<Item> DEFAULT_4_SOCKET = TagKey.of(RegistryKeys.ITEM, new Identifier("smitherz", "default_4_socket"));
    public static final TagKey<Item> DEFAULT_5_SOCKET = TagKey.of(RegistryKeys.ITEM, new Identifier("smitherz", "default_5_socket"));
    public static final TagKey<Item> DEFAULT_6_SOCKET = TagKey.of(RegistryKeys.ITEM, new Identifier("smitherz", "default_6_socket"));
    // autotag
    public static final TagKey<Item> SWORDS = TagKey.of(RegistryKeys.ITEM, TagIdentifiers.Items.SWORDS);
    public static final TagKey<Item> ARMOR = TagKey.of(RegistryKeys.ITEM, TagIdentifiers.Items.ARMOR);
    public static final TagKey<Item> TOOLS = TagKey.of(RegistryKeys.ITEM, TagIdentifiers.Items.TOOLS);

    public static void init() {
    }

}
