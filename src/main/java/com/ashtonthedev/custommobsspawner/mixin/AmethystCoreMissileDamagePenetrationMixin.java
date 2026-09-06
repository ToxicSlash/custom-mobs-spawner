package com.ashtonthedev.custommobsspawner.mixin;

import com.ashtonthedev.custommobsspawner.compat.SpellDamagePenetrationCompat;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "me.fzzyhmstrs.amethyst_core.entity_util.MissileEntity", remap = false)
public abstract class AmethystCoreMissileDamagePenetrationMixin {
    @Inject(method = "method_7454", at = @At("HEAD"), require = 0)
    private void customMobsSpawner$beginAmethystMissileDamagePenetration(
            EntityHitResult entityHitResult,
            CallbackInfo ci
    ) {
        SpellDamagePenetrationCompat.beginSpellDamage();
    }

    @Inject(method = "method_7454", at = @At("RETURN"), require = 0)
    private void customMobsSpawner$endAmethystMissileDamagePenetration(
            EntityHitResult entityHitResult,
            CallbackInfo ci
    ) {
        SpellDamagePenetrationCompat.endSpellDamage();
    }
}
