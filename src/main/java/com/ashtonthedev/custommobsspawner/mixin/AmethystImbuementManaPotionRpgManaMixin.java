package com.ashtonthedev.custommobsspawner.mixin;

import com.ashtonthedev.custommobsspawner.compat.AmethystRpgManaCompat;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "me.fzzyhmstrs.amethyst_imbuement.item.ManaPotionItem", remap = false)
public abstract class AmethystImbuementManaPotionRpgManaMixin {
    private static final int CUSTOM_MOBS_SPAWNER_MANA_POTION_COOLDOWN_TICKS = 40;

    @Inject(method = {"method_7836", "use"}, at = @At("HEAD"), cancellable = true, require = 0)
    private void customMobsSpawner$drinkManaPotionForRpgMana(
            World world,
            PlayerEntity user,
            Hand hand,
            CallbackInfoReturnable<TypedActionResult<ItemStack>> cir
    ) {
        if (!AmethystRpgManaCompat.hasRpgMana(user)) {
            return;
        }

        if (AmethystRpgManaCompat.canRestoreMana(user)) {
            cir.setReturnValue(ItemUsage.consumeHeldItem(world, user, hand));
        } else {
            cir.setReturnValue(TypedActionResult.fail(user.getStackInHand(hand)));
        }
    }

    @Inject(method = {"method_7861", "finishUsing"}, at = @At("HEAD"), cancellable = true, require = 0)
    private void customMobsSpawner$restoreRpgMana(
            ItemStack stack,
            World world,
            LivingEntity user,
            CallbackInfoReturnable<ItemStack> cir
    ) {
        if (!(user instanceof PlayerEntity player) || !AmethystRpgManaCompat.hasRpgMana(player)) {
            return;
        }

        if (player instanceof ServerPlayerEntity serverPlayer) {
            Criteria.CONSUME_ITEM.trigger(serverPlayer, stack);
        }

        int restoredAmethystMana = AmethystRpgManaCompat.restoreFromAmethystManaPotion(player);
        if (restoredAmethystMana > 0) {
            int xp = (3 + world.random.nextInt(5) + world.random.nextInt(5)) * 2;
            float fractionRestored = Math.min(1.0F, restoredAmethystMana / 100.0F);
            player.addExperience(Math.max((int) (xp * fractionRestored), 1));
        }

        Item item = (Item) (Object) this;
        player.incrementStat(Stats.USED.getOrCreateStat(item));
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
        player.getItemCooldownManager().set(item, CUSTOM_MOBS_SPAWNER_MANA_POTION_COOLDOWN_TICKS);
        cir.setReturnValue(result);
    }
}
