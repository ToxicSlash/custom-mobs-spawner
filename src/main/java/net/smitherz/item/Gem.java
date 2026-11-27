package net.smitherz.item;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

public class Gem extends Item {

    private final Multimap<EntityAttribute, EntityAttributeModifier> entityAttributeModifiers;
    private final float linkChance;
    private final float linkBreakChance;
    private final float unlinkChance;
    @Nullable
    private final TagKey<Item> restrictionTag;

    public Gem(Settings settings, Multimap<EntityAttribute, EntityAttributeModifier> entityAttributeModifiers, float linkChance, float linkBreakChance, float unlinkChance,
            @Nullable TagKey<Item> restrictionTag) {
        super(settings);
        this.entityAttributeModifiers = entityAttributeModifiers;
        this.linkChance = 1.0f;
        this.linkBreakChance = linkBreakChance;
        this.restrictionTag = restrictionTag;
        this.unlinkChance = 1.0f;
    }

    public float getLinkChance() {
        return this.linkChance;
    }

    public float getLinkBreakChance() {
        return this.linkBreakChance;
    }

    public float getUnlinkChance() {
        return this.unlinkChance;
    }

    public boolean canLinkToItemStack(ItemStack stack) {
        if (this.restrictionTag != null && !stack.isIn(this.restrictionTag)) {
            return false;
        }
        return true;
    }

    public Multimap<EntityAttribute, EntityAttributeModifier> getGemAttributeModifiers() {
        return this.entityAttributeModifiers;
    }

    @Override
    public Multimap<EntityAttribute, EntityAttributeModifier> getAttributeModifiers(ItemStack stack, EquipmentSlot slot) {
        if (!stack.isOf(this) && slot.equals(LivingEntity.getPreferredEquipmentSlot(stack))) {
            return this.entityAttributeModifiers;
        } else if (slot.equals(EquipmentSlot.MAINHAND)) {
            return ImmutableMultimap.of();
        } else {
            return super.getAttributeModifiers(stack, slot);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        boolean shiftKeyPressed = InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow().getHandle(), 340);
        GemLore lore = getCustomLore();
        if (lore != null) {
            appendLore(tooltip, lore);
            tooltip.add(Text.literal("Hold Shift to reveal Traits").formatted(Formatting.DARK_GRAY));
            if (shiftKeyPressed) {
                appendTraits(tooltip, lore);
                appendAttributeTooltip(tooltip);
            }
            return;
        }

        if (!shiftKeyPressed) {
            tooltip.add(Text.translatable("item.modifiers.shift").formatted(Formatting.GRAY));
            return;
        }

        if (shiftKeyPressed) {
            appendAttributeTooltip(tooltip);
        }
    }

    private void appendLore(List<Text> tooltip, GemLore lore) {
        tooltip.add(Text.literal(lore.name()).formatted(Formatting.GRAY));
        for (String line : lore.description()) {
            tooltip.add(Text.literal(line).formatted(Formatting.GRAY));
        }
    }

    private void appendTraits(List<Text> tooltip, GemLore lore) {
        tooltip.add(Text.literal("- " + lore.traitName()).formatted(Formatting.YELLOW));
        for (String line : lore.traitDescription()) {
            tooltip.add(Text.literal(line).formatted(Formatting.GRAY));
        }
        if (lore.finisherName() == null) {
            tooltip.add(Text.literal("- Finisher Aspect").formatted(Formatting.DARK_GRAY));
            tooltip.add(Text.literal("The gem is too dull and speaks too softly...").formatted(Formatting.DARK_GRAY));
        } else {
            tooltip.add(Text.literal("- " + lore.finisherName() + " (Finisher)").formatted(Formatting.GOLD));
            for (String line : lore.finisherDescription()) {
                tooltip.add(Text.literal(line).formatted(Formatting.GRAY));
            }
        }
    }

    private void appendAttributeTooltip(List<Text> tooltip) {
        List<Text> attributeTooltip = getAttributeTooltip();
        if (!attributeTooltip.isEmpty()) {
            tooltip.add(Text.translatable("item.modifiers.pressed_shift").formatted(Formatting.GRAY));
            tooltip.addAll(attributeTooltip);
        }
    }

    @Nullable
    private GemLore getCustomLore() {
        Identifier id = Registries.ITEM.getId(this);
        if (!id.getNamespace().equals("smitherz")) {
            return null;
        }
        String path = id.getPath();
        if (path.length() <= 3 || path.charAt(0) != 't' || !Character.isDigit(path.charAt(1)) || path.charAt(2) != '_') {
            return null;
        }
        int tier = Character.digit(path.charAt(1), 10);
        String type = path.substring(3);
        return switch (type) {
            case "ruby" -> withTierFinisher(tier, type, new GemLore("Ruby",
                    new String[] {
                            "\"The crystal beats with a faint warmth.",
                            "Holding it makes you feel agitated,",
                            "and your patience begins to wear thin.\""
                    },
                    "Bloodlust",
                    new String[] {
                            "Weapon combos build 'Bloodlust.' Each stack",
                            "increases Posture Damage (+4%) and Rage (+4%)",
                            "until your combo ends."
                    }, null, null));
            case "sapphire" -> withTierFinisher(tier, type, new GemLore("Sapphire",
                    new String[] {
                            "\"A cool, tranquil presence settles over your mind.",
                            "The gem hums softly.\""
                    },
                    "Mana Ward",
                    new String[] {
                            "Combat gradually strengthens a Mana Ward,",
                            "increasing Reabsorption and mana regen,",
                            "while reducing mana cost."
                    }, null, null));
            case "diamond" -> withTierFinisher(tier, type, new GemLore("Diamond",
                    new String[] {
                            "\"Perfectly still and impossibly clear.",
                            "Even in the chaos of battle,",
                            "the crystal refuses to yield.\""
                    },
                    "Resolve",
                    new String[] {
                            "Combat builds Resolve,",
                            "increasing armour and posture damage resistance."
                    }, null, null));
            case "emerald" -> withTierFinisher(tier, type, new GemLore("Emerald",
                    new String[] {
                            "\"Life seems to linger within the stone.",
                            "Tiny veins of green shimmer like new growth after rain.\""
                    },
                    "Verdant Growth",
                    new String[] {
                            "Attacks grant stacks of Verdant Growth.",
                            "Each stack grants thorns and poison chance."
                    }, null, null));
            case "topaz" -> withTierFinisher(tier, type, new GemLore("Topaz",
                    new String[] {
                            "\"Golden embers drift beneath the crystal's surface.",
                            "Every spark feels eager to escape.\""
                    },
                    "Kindle",
                    new String[] {
                            "Weapon combos grant stacks of 'Kindle',",
                            "increasing your arcane and fire damage.",
                            "Melee and ranged attacks deal further",
                            "fire/arcane damage."
                    }, null, null));
            case "citrine" -> withTierFinisher(tier, type, new GemLore("Citrine",
                    new String[] {
                            "\"Tiny flashes dance inside the gem, leaping",
                            "from one facet to another faster than the eye can follow.\""
                    },
                    "Static",
                    new String[] {
                            "Combat builds Static.",
                            "Increases crit chance, attack speed,",
                            "high static zaps nearby hostiles."
                    }, null, null));
            case "tanzanite" -> withTierFinisher(tier, type, new GemLore("Tanzanite",
                    new String[] {
                            "\"The gem's glare makes you feel cold.",
                            "It makes your breathing feel laboured.\""
                    },
                    "Soulchill",
                    new String[] {
                            "Combos build chill, slowing enemies",
                            "and making them fragile."
                    }, null, null));
            case "jade" -> withTierFinisher(tier, type, new GemLore("Jade",
                    new String[] {
                            "\"The polished stone feels impossibly balanced.\""
                    },
                    "Focus",
                    new String[] {
                            "Consecutive ranged attacks build Focus,",
                            "improving projectile speed,",
                            "and armour penetration."
                    }, null, null));
            case "amethyst" -> withTierFinisher(tier, type, new GemLore("Amethyst",
                    new String[] {
                            "\"The edges of the gem blur when you move it.",
                            "Shadows seem to linger just a little longer than they should.\""
                    },
                    "Shadowflow",
                    new String[] {
                            "Combat builds Shadowflow,",
                            "each stack increases crit chance (+1%),",
                            "dodge chance (+0.5%).",
                            "Eventually (10x stacks) entering a phasing state,",
                            "high dodge and movement speed."
                    }, null, null));
            default -> null;
        };
    }

    private static GemLore withTierFinisher(int tier, String type, GemLore lore) {
        if (tier != 2) {
            return lore;
        }
        return switch (type) {
            case "ruby" -> lore.withFinisher("Execution", new String[] {
                    "Finisher unleashes a powerful strike,",
                    "instantly staggering (6s), and reducing",
                    "armour effectiveness. (6s)"
            });
            case "sapphire" -> lore.withFinisher("Arcane Rebound", new String[] {
                    "Finisher overcharges mana, provides high reabsorption (+6),",
                    "also reflects damage. (12%)"
            });
            case "diamond" -> lore.withFinisher("Bulwark", new String[] {
                    "Finisher hardens armour (+6), high resistance. (8%s)"
            });
            case "emerald" -> lore.withFinisher("Bloom", new String[] {
                    "Finisher grants regen (10s), inflicts long poison. (12s)"
            });
            case "topaz" -> lore.withFinisher("Fireburst", new String[] {
                    "Finisher erupts burst of Fire and Arcane energy,",
                    "scorching nearby enemies, has a chance to chain."
            });
            case "citrine" -> lore.withFinisher("Discharge", new String[] {
                    "Finisher releases stored energy, chains lightning.",
                    "Revitalises user."
            });
            case "tanzanite" -> lore.withFinisher("Soul Shatter", new String[] {
                    "Finisher creates plume of chill,",
                    "executing low health chilled enemies."
            });
            case "jade" -> lore.withFinisher("Hunter's Mark", new String[] {
                    "Finisher marks enemies, increases all damage",
                    "and roots them. (4s)"
            });
            case "amethyst" -> lore.withFinisher("Shadow State", new String[] {
                    "Finisher briefly cloaks you.",
                    "Grants great crit chance and dodge chance. (4s)"
            });
            default -> lore;
        };
    }

    private List<Text> getAttributeTooltip() {
        List<Text> tooltip = new ArrayList<Text>();
        for (var entry : this.entityAttributeModifiers.entries()) {
            EntityAttributeModifier modifier = entry.getValue();
            double value = modifier.getValue();
            boolean percentAttribute = isPercentAttribute(entry.getKey());
            double formattedValue = modifier.getOperation() == EntityAttributeModifier.Operation.MULTIPLY_BASE
                    || modifier.getOperation() == EntityAttributeModifier.Operation.MULTIPLY_TOTAL || percentAttribute ? value * 100.0 : value;

            if (value > 0.0D) {
                tooltip.add(formatAttributeTooltip("attribute.modifier.plus.", modifier, formattedValue, entry.getKey(), percentAttribute).formatted(Formatting.BLUE));
            } else if (value < 0.0D) {
                tooltip.add(formatAttributeTooltip("attribute.modifier.take.", modifier, formattedValue * -1.0D, entry.getKey(), percentAttribute).formatted(Formatting.RED));
            }
        }
        return tooltip;
    }

    private static MutableText formatAttributeTooltip(String translationPrefix, EntityAttributeModifier modifier, double value, EntityAttribute attribute, boolean percentAttribute) {
        if (percentAttribute && modifier.getOperation() == EntityAttributeModifier.Operation.ADDITION) {
            String sign = translationPrefix.contains("plus") ? "+" : "-";
            return Text.literal(sign + ItemStack.MODIFIER_FORMAT.format(value) + "% ").append(Text.translatable(attribute.getTranslationKey()));
        }
        return Text.translatable(translationPrefix + modifier.getOperation().getId(), ItemStack.MODIFIER_FORMAT.format(value), Text.translatable(attribute.getTranslationKey()));
    }

    private static boolean isPercentAttribute(EntityAttribute attribute) {
        return attribute.getTranslationKey().equals("attribute.name.zenith_attributes.healing_received");
    }

    private record GemLore(String name, String[] description, String traitName, String[] traitDescription, @Nullable String finisherName, @Nullable String[] finisherDescription) {
        private GemLore withFinisher(String finisherName, String[] finisherDescription) {
            return new GemLore(this.name, this.description, this.traitName, this.traitDescription, finisherName, finisherDescription);
        }
    }

}
