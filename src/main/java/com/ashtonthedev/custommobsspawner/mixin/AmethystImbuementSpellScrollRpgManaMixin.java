package com.ashtonthedev.custommobsspawner.mixin;

import com.ashtonthedev.custommobsspawner.compat.AmethystRpgManaCompat;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "me.fzzyhmstrs.amethyst_imbuement.item.SpellScrollItem", remap = false)
public abstract class AmethystImbuementSpellScrollRpgManaMixin {
    @Inject(method = "checkManaCost", at = @At("HEAD"), cancellable = true, require = 0)
    private void customMobsSpawner$checkRpgManaCost(
            int manaCost,
            ItemStack stack,
            World world,
            LivingEntity user,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!AmethystRpgManaCompat.canUseRpgMana(user)) {
            return;
        }
        cir.setReturnValue(AmethystRpgManaCompat.checkManaCost(
                manaCost,
                new AmethystRpgManaCompat.ItemCostContext(world, user)
        ));
    }

    @Inject(method = "applyManaCost", at = @At("TAIL"), require = 0)
    private void customMobsSpawner$spendRpgMana(
            int manaCost,
            ItemStack stack,
            World world,
            LivingEntity user,
            CallbackInfo ci
    ) {
        if (AmethystRpgManaCompat.canUseRpgMana(user)) {
            AmethystRpgManaCompat.spendMana(manaCost, user);
        }
    }
}
