package com.ashtonthedev.custommobsspawner.item;

import com.ashtonthedev.custommobsspawner.compat.AmethystRpgManaCompat;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;

import java.util.List;

public class CustomManaPotionItem extends Item {
    private static final int USE_TIME_TICKS = 12;
    private static final int COOLDOWN_TICKS = 40;
    private final int restoreAmount;
    private final String tooltipKey;
    private final boolean allowOvercharge;

    public CustomManaPotionItem(Settings settings, int restoreAmount, String tooltipKey, boolean allowOvercharge) {
        super(settings);
        this.restoreAmount = restoreAmount;
        this.tooltipKey = tooltipKey;
        this.allowOvercharge = allowOvercharge;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (!AmethystRpgManaCompat.hasRpgMana(user)) {
            return TypedActionResult.fail(user.getStackInHand(hand));
        }

        if (AmethystRpgManaCompat.canRestoreMana(user, allowOvercharge)) {
            return ItemUsage.consumeHeldItem(world, user, hand);
        }
        return TypedActionResult.fail(user.getStackInHand(hand));
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (!(user instanceof PlayerEntity player) || !AmethystRpgManaCompat.hasRpgMana(player)) {
            return stack;
        }

        if (player instanceof ServerPlayerEntity serverPlayer) {
            Criteria.CONSUME_ITEM.trigger(serverPlayer, stack);
        }

        int restoredMana = AmethystRpgManaCompat.restoreMana(player, restoreAmount, allowOvercharge);
        if (restoredMana > 0) {
            int xp = (3 + world.random.nextInt(5) + world.random.nextInt(5)) * 2;
            float fractionRestored = Math.min(1.0F, restoredMana / 100.0F);
            player.addExperience(Math.max((int) (xp * fractionRestored), 1));
        }

        player.incrementStat(Stats.USED.getOrCreateStat(this));
        ItemStack result = stack;
        if (!player.getAbilities().creativeMode) {
            stack.decrement(1);
            if (stack.isEmpty()) {
                result = new ItemStack(Items.GLASS_BOTTLE);
            } else {
                player.getInventory().insertStack(new ItemStack(Items.GLASS_BOTTLE));
            }
        }

        world.emitGameEvent(user, GameEvent.DRINK, player.getPos());
        player.getItemCooldownManager().set(this, COOLDOWN_TICKS);
        return result;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.DRINK;
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return USE_TIME_TICKS;
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable(tooltipKey, restoreAmount));
    }
}
