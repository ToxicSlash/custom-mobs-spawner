package com.ashtonthedev.custommobsspawner.mixin.client;

import com.ashtonthedev.custommobsspawner.combat.ModAttributes;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "net.bettercombat.client.collision.TargetFinder", remap = false)
public abstract class BetterCombatTargetFinderMixin {
    @ModifyVariable(method = "findAttackTargetResult", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private static double customMobsSpawner$addAttackRange(double attackRange, PlayerEntity player) {
        return Math.max(0.0D, attackRange + player.getAttributeValue(ModAttributes.ATTACK_RANGE));
    }
}
