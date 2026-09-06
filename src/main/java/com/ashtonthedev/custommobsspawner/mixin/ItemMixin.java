package com.ashtonthedev.custommobsspawner.mixin;

import com.ashtonthedev.custommobsspawner.combat.PlayerParryHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public abstract class ItemMixin {
    @Inject(method = "getMaxUseTime", at = @At("HEAD"), cancellable = true)
    private void customMobsSpawner$parrySwordUseTime(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (PlayerParryHandler.isParryItem(stack)) {
            cir.setReturnValue(72000);
        }
    }

    @Inject(method = "getUseAction", at = @At("HEAD"), cancellable = true)
    private void customMobsSpawner$parrySwordUseAction(ItemStack stack, CallbackInfoReturnable<UseAction> cir) {
        if (PlayerParryHandler.isParryItem(stack)) {
            cir.setReturnValue(UseAction.BLOCK);
        }
    }

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void customMobsSpawner$useParrySword(World world, PlayerEntity user, Hand hand, CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
        ItemStack stack = user.getStackInHand(hand);
        if (PlayerParryHandler.isParryItem(stack)) {
            if (!PlayerParryHandler.canStartBlocking(user, stack)) {
                PlayerParryHandler.disarmWeaponBlock(user);
                cir.setReturnValue(TypedActionResult.fail(stack));
                return;
            }
            user.setCurrentHand(hand);
            PlayerParryHandler.armParryBlock(user, stack);
            cir.setReturnValue(TypedActionResult.consume(stack));
        }
    }
}
