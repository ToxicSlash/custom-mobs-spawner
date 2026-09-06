package com.ashtonthedev.custommobsspawner.mixin;

import com.google.common.collect.Multimap;
import com.ashtonthedev.custommobsspawner.combat.PlayerParryHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Inject(method = "getMaxUseTime", at = @At("HEAD"), cancellable = true)
    private void customMobsSpawner$parrySwordUseTime(CallbackInfoReturnable<Integer> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        if (PlayerParryHandler.isParryItem(stack)) {
            cir.setReturnValue(72000);
        }
    }

    @Inject(method = "getUseAction", at = @At("HEAD"), cancellable = true)
    private void customMobsSpawner$parrySwordUseAction(CallbackInfoReturnable<UseAction> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        if (PlayerParryHandler.isParryItem(stack)) {
            cir.setReturnValue(UseAction.BLOCK);
        }
    }

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void customMobsSpawner$useParrySword(World world, PlayerEntity user, Hand hand, CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
        ItemStack stack = (ItemStack) (Object) this;
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

    @Inject(method = "getAttributeModifiers", at = @At("RETURN"), cancellable = true)
    private void customMobsSpawner$addParryAttributes(
            EquipmentSlot slot,
            CallbackInfoReturnable<Multimap<EntityAttribute, EntityAttributeModifier>> cir
    ) {
        ItemStack stack = (ItemStack) (Object) this;
        cir.setReturnValue(PlayerParryHandler.addParryAttributeModifiers(stack, slot, cir.getReturnValue()));
    }
}
