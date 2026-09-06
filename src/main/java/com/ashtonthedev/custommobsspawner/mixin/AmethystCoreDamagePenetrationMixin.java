package com.ashtonthedev.custommobsspawner.mixin;

import com.ashtonthedev.custommobsspawner.compat.SpellDamagePenetrationCompat;
import me.fzzyhmstrs.amethyst_core.item_util.SpellCasting;
import me.fzzyhmstrs.amethyst_core.scepter_util.augments.ScepterAugment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "me.fzzyhmstrs.amethyst_core.scepter_util.ScepterHelper", remap = false)
public abstract class AmethystCoreDamagePenetrationMixin {
    @Inject(method = "castSpell", at = @At("HEAD"), require = 0)
    private void customMobsSpawner$beginAmethystSpellDamagePenetration(
            World world,
            LivingEntity user,
            Hand hand,
            ItemStack stack,
            ScepterAugment augment,
            String activeAugment,
            int level,
            SpellCasting spellCasting,
            boolean useItem,
            boolean notify,
            CallbackInfoReturnable<TypedActionResult<ItemStack>> cir
    ) {
        SpellDamagePenetrationCompat.beginSpellDamage();
    }

    @Inject(method = "castSpell", at = @At("RETURN"), require = 0)
    private void customMobsSpawner$endAmethystSpellDamagePenetration(
            World world,
            LivingEntity user,
            Hand hand,
            ItemStack stack,
            ScepterAugment augment,
            String activeAugment,
            int level,
            SpellCasting spellCasting,
            boolean useItem,
            boolean notify,
            CallbackInfoReturnable<TypedActionResult<ItemStack>> cir
    ) {
        SpellDamagePenetrationCompat.endSpellDamage();
    }
}
