package com.ashtonthedev.custommobsspawner.item;

import com.ashtonthedev.custommobsspawner.combat.ModAttributes;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.SlotType;
import dev.emi.trinkets.api.TrinketItem;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.client.item.TooltipData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import vazkii.botania.api.mana.ManaBarTooltip;
import vazkii.botania.api.mana.ManaItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ManaCapacitorItem extends TrinketItem {
    public static final int RPG_MANA_CAPACITY = 300;
    public static final int BOTANIA_MANA_CAPACITY = 3750;
    private static final String TAG_MANA = "mana";
    private static final double MANA_OVERCHARGE_BONUS = 80.0D;

    public ManaCapacitorItem(Settings settings) {
        super(settings);
    }

    @Override
    public boolean canEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
        return isBeltSlot(slot) && super.canEquip(stack, slot, entity);
    }

    @Override
    public Multimap<EntityAttribute, EntityAttributeModifier> getModifiers(
            ItemStack stack,
            SlotReference slot,
            LivingEntity entity,
            UUID uuid
    ) {
        if (!isBeltSlot(slot)) {
            return ImmutableMultimap.of();
        }

        return ImmutableMultimap.of(
                ModAttributes.MANA_OVERCHARGE,
                new EntityAttributeModifier(
                        uuid,
                        "Mana capacitor belt bonus",
                        MANA_OVERCHARGE_BONUS,
                        EntityAttributeModifier.Operation.ADDITION
                )
        );
    }

    @Override
    public void appendTooltip(
            ItemStack stack,
            @Nullable World world,
            List<Text> tooltip,
            TooltipContext context
    ) {
        tooltip.add(Text.translatable(
                "item.cmobs.mana_capacitor.tooltip",
                Math.round(getRpgMana(stack)),
                RPG_MANA_CAPACITY
        ));
    }

    @Override
    public Optional<TooltipData> getTooltipData(ItemStack stack) {
        return Optional.of(ManaBarTooltip.fromManaItem(stack));
    }

    @Override
    public boolean isItemBarVisible(ItemStack stack) {
        return getMana(stack) > 0;
    }

    @Override
    public int getItemBarStep(ItemStack stack) {
        return Math.round(13.0F * getMana(stack) / BOTANIA_MANA_CAPACITY);
    }

    @Override
    public int getItemBarColor(ItemStack stack) {
        return 0x2F8CFF;
    }

    public static int getMana(ItemStack stack) {
        return stack.getOrCreateNbt().getInt(TAG_MANA);
    }

    public static boolean consumeRpgMana(ItemStack stack, int rpgMana) {
        int botaniaMana = Math.max(0, Math.round(rpgMana * BOTANIA_MANA_CAPACITY / (float) RPG_MANA_CAPACITY));
        if (botaniaMana <= 0 || getMana(stack) < botaniaMana) {
            return false;
        }
        setMana(stack, getMana(stack) - botaniaMana);
        return true;
    }

    private static void setMana(ItemStack stack, int mana) {
        if (mana <= 0) {
            stack.removeSubNbt(TAG_MANA);
            return;
        }
        stack.getOrCreateNbt().putInt(TAG_MANA, Math.min(BOTANIA_MANA_CAPACITY, mana));
    }

    private static float getRpgMana(ItemStack stack) {
        return getMana(stack) * (float) RPG_MANA_CAPACITY / BOTANIA_MANA_CAPACITY;
    }

    private static boolean isBeltSlot(SlotReference slot) {
        SlotType slotType = slot.inventory().getSlotType();
        return "legs".equals(slotType.getGroup()) && "belt".equals(slotType.getName());
    }

    public static final class ManaItemImpl implements ManaItem {
        private final ItemStack stack;

        public ManaItemImpl(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public int getMana() {
            return ManaCapacitorItem.getMana(stack);
        }

        @Override
        public int getMaxMana() {
            return BOTANIA_MANA_CAPACITY;
        }

        @Override
        public void addMana(int mana) {
            setMana(stack, Math.max(0, getMana() + mana));
        }

        @Override
        public boolean canReceiveManaFromPool(net.minecraft.block.entity.BlockEntity pool) {
            return getMana() < getMaxMana();
        }

        @Override
        public boolean canReceiveManaFromItem(ItemStack otherStack) {
            return getMana() < getMaxMana();
        }

        @Override
        public boolean canExportManaToPool(net.minecraft.block.entity.BlockEntity pool) {
            return getMana() > 0;
        }

        @Override
        public boolean canExportManaToItem(ItemStack otherStack) {
            return getMana() > 0;
        }

        @Override
        public boolean isNoExport() {
            return false;
        }
    }
}
