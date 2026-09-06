package com.ashtonthedev.custommobsspawner.mixin;

import com.ashtonthedev.custommobsspawner.compat.RpgManaSpellEngineCompat;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.internals.SpellRegistry;
import net.spell_engine.internals.casting.SpellCast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = SpellHelper.class, priority = 1500)
public abstract class RpgManaSpellEngineThresholdMixin {
    @Inject(method = "performSpell", at = @At("HEAD"), cancellable = true)
    private static void customMobsSpawner$requireHalfManaBeforeRpgManaSpellEngineCast(
            World world,
            PlayerEntity player,
            Identifier spellId,
            List<Entity> targets,
            SpellCast.Action action,
            float progress,
            CallbackInfo ci
    ) {
        Spell spell = SpellRegistry.getSpell(spellId);
        if (!RpgManaSpellEngineCompat.canPerformSpell(player, spellId, spell, targets, action, progress)) {
            RpgManaSpellEngineCompat.sendInsufficientManaMessage(player);
            ci.cancel();
        }
    }
}
