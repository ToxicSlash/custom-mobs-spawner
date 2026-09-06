package com.ashtonthedev.custommobsspawner.mixin;

import com.ashtonthedev.custommobsspawner.compat.SpellDamagePenetrationCompat;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnchantmentHelper.class)
public abstract class SpellDamagePenetrationEnchantmentHelperMixin {
    @Inject(method = "getProtectionAmount", at = @At("RETURN"), cancellable = true)
    private static void customMobsSpawner$penetrateSpellProtection(
            Iterable<ItemStack> equipment,
            DamageSource source,
            CallbackInfoReturnable<Integer> cir
    ) {
        if (SpellDamagePenetrationCompat.isSpellDamageActive()) {
            cir.setReturnValue(SpellDamagePenetrationCompat.penetrateProtection(cir.getReturnValue()));
        }
    }
}
