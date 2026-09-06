package com.ashtonthedev.custommobsspawner.mixin;

import com.ashtonthedev.custommobsspawner.compat.RpgManaSpellEngineCompat;
import com.ashtonthedev.custommobsspawner.skill.CustomSkillRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.internals.SpellRegistry;
import net.spell_engine.internals.casting.SpellCast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SpellHelper.class, priority = 1500)
public abstract class RpgManaSpellEngineAttemptThresholdMixin {
    @Inject(
            method = "attemptCasting(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/Identifier;Z)Lnet/spell_engine/internals/casting/SpellCast$Attempt;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void customMobsSpawner$requireHalfManaBeforeSpellEngineAttempt(
            PlayerEntity player,
            ItemStack stack,
            Identifier spellId,
            boolean checkAmmo,
            CallbackInfoReturnable<SpellCast.Attempt> cir
    ) {
        Spell spell = SpellRegistry.getSpell(spellId);
        if (player instanceof ServerPlayerEntity serverPlayer && !CustomSkillRegistry.canAttemptSpellCast(serverPlayer, spellId)) {
            cir.setReturnValue(SpellCast.Attempt.none());
            return;
        }
        if (!RpgManaSpellEngineCompat.canAttemptSpell(player, spellId, spell)) {
            RpgManaSpellEngineCompat.sendInsufficientManaMessage(player);
            cir.setReturnValue(SpellCast.Attempt.none());
        }
    }
}
