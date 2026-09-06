package com.ashtonthedev.custommobsspawner.mixin;

import com.ashtonthedev.custommobsspawner.skill.CustomSkillRegistry;
import net.spell_engine.entity.SpellProjectile;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpellProjectile.class)
public abstract class SpellProjectileMixin {
    @Inject(method = "onEntityHit", at = @At("HEAD"))
    private void customMobsSpawner$beginSpellProjectileImpact(EntityHitResult entityHitResult, CallbackInfo ci) {
        CustomSkillRegistry.beginSpellProjectileImpact((SpellProjectile) (Object) this);
    }

    @Inject(method = "onEntityHit", at = @At("RETURN"))
    private void customMobsSpawner$endSpellProjectileImpact(EntityHitResult entityHitResult, CallbackInfo ci) {
        CustomSkillRegistry.endSpellProjectileImpact();
    }
}
