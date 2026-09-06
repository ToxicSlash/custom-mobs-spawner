package com.ashtonthedev.custommobsspawner.mixin;

import com.ashtonthedev.custommobsspawner.combat.SpellComboHandler;
import com.ashtonthedev.custommobsspawner.compat.SpellDamagePenetrationCompat;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.world.World;
import net.spell_engine.api.spell.SpellInfo;
import net.spell_engine.internals.SpellHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SpellHelper.class, priority = 1500)
public abstract class SpellEngineDamagePenetrationMixin {
    @Inject(
            method = "performImpacts(Lnet/minecraft/world/World;Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/Entity;Lnet/spell_engine/api/spell/SpellInfo;Lnet/spell_engine/internals/SpellHelper$ImpactContext;)Z",
            at = @At("HEAD")
    )
    private static void customMobsSpawner$beginSpellEngineDamagePenetration(
            World world,
            LivingEntity caster,
            Entity target,
            Entity source,
            SpellInfo spellInfo,
            SpellHelper.ImpactContext context,
            CallbackInfoReturnable<Boolean> cir
    ) {
        SpellComboHandler.beginSpellImpact();
        SpellDamagePenetrationCompat.beginSpellDamage();
    }

    @Inject(
            method = "performImpacts(Lnet/minecraft/world/World;Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/Entity;Lnet/spell_engine/api/spell/SpellInfo;Lnet/spell_engine/internals/SpellHelper$ImpactContext;)Z",
            at = @At("RETURN")
    )
    private static void customMobsSpawner$endSpellEngineDamagePenetration(
            World world,
            LivingEntity caster,
            Entity target,
            Entity source,
            SpellInfo spellInfo,
            SpellHelper.ImpactContext context,
            CallbackInfoReturnable<Boolean> cir
    ) {
        SpellDamagePenetrationCompat.endSpellDamage();
        SpellComboHandler.recordImpact(world, caster, target, source, spellInfo, false, cir.getReturnValue());
        SpellComboHandler.endSpellImpact();
    }

    @Inject(
            method = "performImpacts(Lnet/minecraft/world/World;Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/Entity;Lnet/spell_engine/api/spell/SpellInfo;Lnet/spell_engine/internals/SpellHelper$ImpactContext;Z)Z",
            at = @At("HEAD")
    )
    private static void customMobsSpawner$beginSpellEngineDamagePenetrationWithArea(
            World world,
            LivingEntity caster,
            Entity target,
            Entity source,
            SpellInfo spellInfo,
            SpellHelper.ImpactContext context,
            boolean areaImpact,
            CallbackInfoReturnable<Boolean> cir
    ) {
        SpellComboHandler.beginSpellImpact();
        SpellDamagePenetrationCompat.beginSpellDamage();
    }

    @Inject(
            method = "performImpacts(Lnet/minecraft/world/World;Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/Entity;Lnet/spell_engine/api/spell/SpellInfo;Lnet/spell_engine/internals/SpellHelper$ImpactContext;Z)Z",
            at = @At("RETURN")
    )
    private static void customMobsSpawner$endSpellEngineDamagePenetrationWithArea(
            World world,
            LivingEntity caster,
            Entity target,
            Entity source,
            SpellInfo spellInfo,
            SpellHelper.ImpactContext context,
            boolean areaImpact,
            CallbackInfoReturnable<Boolean> cir
    ) {
        SpellDamagePenetrationCompat.endSpellDamage();
        SpellComboHandler.recordImpact(world, caster, target, source, spellInfo, areaImpact, cir.getReturnValue());
        SpellComboHandler.endSpellImpact();
    }
}
