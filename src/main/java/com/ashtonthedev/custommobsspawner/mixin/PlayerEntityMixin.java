package com.ashtonthedev.custommobsspawner.mixin;

import com.ashtonthedev.custommobsspawner.combat.ModAttributes;
import com.ashtonthedev.custommobsspawner.skill.CustomSkillRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    private static final Identifier AMETHYST_CORE_SPELL_DAMAGE = new Identifier("amethyst_core", "spell_damage");
    private static final Identifier SPELL_POWER_ARCANE = new Identifier("spell_power", "arcane");
    private static final Identifier SPELL_POWER_FIRE = new Identifier("spell_power", "fire");
    private static final Identifier SPELL_POWER_FROST = new Identifier("spell_power", "frost");
    private static final Identifier SPELL_POWER_HEALING = new Identifier("spell_power", "healing");
    private static final Identifier SPELL_POWER_LIGHTNING = new Identifier("spell_power", "lightning");
    private static final Identifier SPELL_POWER_SOUL = new Identifier("spell_power", "soul");
    private static final Identifier RPGMANA_MANA = new Identifier("rpgmana", "mana");
    private static final Identifier RPGMANA_MANA_REGEN = new Identifier("rpgmana", "manaregen");
    private static final Identifier RPGMANA_MANA_COST = new Identifier("rpgmana", "manacost");

    @Inject(method = "createPlayerAttributes", at = @At("RETURN"))
    private static void customMobsSpawner$addParryAttributes(CallbackInfoReturnable<DefaultAttributeContainer.Builder> cir) {
        DefaultAttributeContainer.Builder builder = cir.getReturnValue()
                .add(ModAttributes.PARRY_WINDOW_TICKS)
                .add(ModAttributes.POSTURE_HEALTH)
                .add(ModAttributes.POSTURE_REGEN)
                .add(ModAttributes.POSTURE_DAMAGE)
                .add(ModAttributes.POSTURE_DAMAGE_MULTIPLIER)
                .add(ModAttributes.POSTURE_DAMAGE_RESISTANCE)
                .add(ModAttributes.PVP_TAKEN_POSTURE_DAMAGE_MULTIPLIER)
                .add(ModAttributes.STAGGER_DAMAGE_MULTIPLIER)
                .add(ModAttributes.ATTACK_RANGE)
                .add(ModAttributes.MANA_OVERCHARGE)
                .add(ModAttributes.ZENITH_CRIT_CHANCE)
                .add(ModAttributes.ZENITH_CRIT_DAMAGE);
        customMobsSpawner$addOptionalAttribute(builder, AMETHYST_CORE_SPELL_DAMAGE, 6.0D);
        customMobsSpawner$addOptionalAttribute(builder, SPELL_POWER_ARCANE, 6.0D);
        customMobsSpawner$addOptionalAttribute(builder, SPELL_POWER_FIRE, 6.0D);
        customMobsSpawner$addOptionalAttribute(builder, SPELL_POWER_FROST, 6.0D);
        customMobsSpawner$addOptionalAttribute(builder, SPELL_POWER_HEALING, 6.0D);
        customMobsSpawner$addOptionalAttribute(builder, SPELL_POWER_LIGHTNING, 6.0D);
        customMobsSpawner$addOptionalAttribute(builder, SPELL_POWER_SOUL, 6.0D);
        customMobsSpawner$addOptionalAttribute(builder, RPGMANA_MANA, 80.0D);
        customMobsSpawner$addOptionalAttribute(builder, RPGMANA_MANA_REGEN, 1.0D);
        customMobsSpawner$addOptionalAttribute(builder, RPGMANA_MANA_COST, 100.0D);
    }

    @Inject(method = "attack", at = @At("RETURN"))
    private void customMobsSpawner$whenAttacks(Entity target, CallbackInfo ci) {
        if (target instanceof LivingEntity) {
            if ((Object) this instanceof ServerPlayerEntity player) {
                CustomSkillRegistry.markSuccessfulPlayerAttack(player);
            }
            CustomSkillRegistry.runAttack((PlayerEntity) (Object) this, target);
        }
    }

    private static void customMobsSpawner$addOptionalAttribute(
            DefaultAttributeContainer.Builder builder,
            Identifier id,
            double baseValue
    ) {
        Registries.ATTRIBUTE.getOrEmpty(id).ifPresent(attribute -> customMobsSpawner$addAttribute(builder, attribute, baseValue));
    }

    private static void customMobsSpawner$addAttribute(
            DefaultAttributeContainer.Builder builder,
            EntityAttribute attribute,
            double baseValue
    ) {
        builder.add(attribute, baseValue);
    }
}
