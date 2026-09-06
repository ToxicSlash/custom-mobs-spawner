package com.ashtonthedev.custommobsspawner.mixin;

import com.ashtonthedev.custommobsspawner.compat.SpellDamagePenetrationCompat;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class SpellDamagePenetrationLivingEntityMixin {
    @Inject(method = "getArmor", at = @At("RETURN"), cancellable = true)
    private void customMobsSpawner$penetrateSpellArmor(CallbackInfoReturnable<Integer> cir) {
        if (SpellDamagePenetrationCompat.isSpellDamageActive()) {
            cir.setReturnValue(SpellDamagePenetrationCompat.penetrateArmor(cir.getReturnValue()));
        }
    }

    @Inject(
            method = "getAttributeValue(Lnet/minecraft/entity/attribute/EntityAttribute;)D",
            at = @At("RETURN"),
            cancellable = true
    )
    private void customMobsSpawner$penetrateSpellArmorToughness(
            EntityAttribute attribute,
            CallbackInfoReturnable<Double> cir
    ) {
        if (SpellDamagePenetrationCompat.isSpellDamageActive()) {
            cir.setReturnValue(SpellDamagePenetrationCompat.penetrateArmorToughness(attribute, cir.getReturnValue()));
        }
    }
}
