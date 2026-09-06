package com.ashtonthedev.custommobsspawner.mixin;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.SpellContainer;
import net.spell_engine.internals.SpellRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Mixin(value = SpellRegistry.class, remap = false)
public class SpellRegistrySwordFinisherMixin {
    private static final Identifier SWORD_FINISHER = new Identifier("cmobs", "sword_finisher");
    private static final Identifier CLAYMORE_FINISHER = new Identifier("cmobs", "claymore_finisher");
    private static final Identifier DAGGER_FINISHER = new Identifier("cmobs", "dagger_finisher");
    private static final TagKey<Item> KNIVES_TAG = TagKey.of(RegistryKeys.ITEM, new Identifier("cmobs", "knives"));
    private static final Set<Identifier> VANILLA_SWORDS = Set.of(
            new Identifier("minecraft", "wooden_sword"),
            new Identifier("minecraft", "stone_sword"),
            new Identifier("minecraft", "iron_sword"),
            new Identifier("minecraft", "golden_sword"),
            new Identifier("minecraft", "diamond_sword"),
            new Identifier("minecraft", "netherite_sword")
    );
    private static final Set<Identifier> CLAYMORES = Set.of(
            new Identifier("paladins", "stone_claymore"),
            new Identifier("paladins", "iron_claymore"),
            new Identifier("paladins", "golden_claymore"),
            new Identifier("paladins", "diamond_claymore"),
            new Identifier("paladins", "netherite_claymore"),
            new Identifier("paladins", "ruby_claymore"),
            new Identifier("paladins", "aeternium_claymore")
    );
    private static final Set<Identifier> KNIFE_FALLBACKS = Set.of(
            new Identifier("mcdw", "sword_beestinger"),
            new Identifier("mcdw", "dagger_fangs_of_frost"),
            new Identifier("rogues", "netherite_dagger")
    );

    @Inject(method = "containerForItem", at = @At("RETURN"), cancellable = true)
    private static void cmobs$appendSwordFinisher(Identifier itemId, CallbackInfoReturnable<SpellContainer> cir) {
        Identifier finisher = finisherForItem(itemId);
        if (finisher == null) {
            return;
        }

        SpellContainer existing = cir.getReturnValue();
        if (existing == null) {
            SpellContainer container = new SpellContainer(
                    SpellContainer.ContentType.ARCHERY,
                    true,
                    null,
                    1,
                    new ArrayList<>(List.of(finisher.toString()))
            );
            cir.setReturnValue(container);
            return;
        }

        SpellContainer merged = existing.copy();
        if (merged.spell_ids == null) {
            merged.spell_ids = new ArrayList<>();
        }
        if (!merged.spell_ids.contains(finisher.toString())) {
            merged.spell_ids.add(finisher.toString());
        }
        if (merged.content == null) {
            merged.content = SpellContainer.ContentType.ARCHERY;
        }
        merged.is_proxy = true;
        merged.max_spell_count = Math.max(merged.max_spell_count, merged.spell_ids.size());
        cir.setReturnValue(merged);
    }

    private static Identifier finisherForItem(Identifier itemId) {
        if (VANILLA_SWORDS.contains(itemId)) {
            return SWORD_FINISHER;
        }
        if (CLAYMORES.contains(itemId)) {
            return CLAYMORE_FINISHER;
        }
        if (KNIFE_FALLBACKS.contains(itemId) || itemInTag(itemId, KNIVES_TAG)) {
            return DAGGER_FINISHER;
        }
        return null;
    }

    private static boolean itemInTag(Identifier itemId, TagKey<Item> tag) {
        Item item = Registries.ITEM.getOrEmpty(itemId).orElse(null);
        return item != null && new ItemStack(item).isIn(tag);
    }
}
