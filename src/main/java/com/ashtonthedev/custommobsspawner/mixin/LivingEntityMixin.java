package com.ashtonthedev.custommobsspawner.mixin;

import com.ashtonthedev.custommobsspawner.combat.ComboHandler;
import com.ashtonthedev.custommobsspawner.combat.PlayerParryHandler;
import com.ashtonthedev.custommobsspawner.combat.MobPostureHandler;
import com.ashtonthedev.custommobsspawner.combat.ModAttributes;
import com.ashtonthedev.custommobsspawner.compat.AmethystRpgManaCompat;
import com.ashtonthedev.custommobsspawner.compat.SpellDamagePenetrationCompat;
import com.ashtonthedev.custommobsspawner.data.CustomMobsSpawnerLog;
import com.ashtonthedev.custommobsspawner.skill.CustomSkillRegistry;
import com.ashtonthedev.custommobsspawner.skill.SkillTrigger;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    private static final Identifier MORE_RPG_CLASSES_RAGE_MODIFIER =
            new Identifier("more_rpg_classes", "rage_modifier");
    private static final int MANA_REFUND_ON_MOB_KILL = 8;
    private static final long SPELL_RAGE_COOLDOWN_TICKS = 10L;
    private static final Map<UUID, Long> SPELL_RAGE_COOLDOWNS = new ConcurrentHashMap<>();

    private float customMobsSpawner$healthAndAbsorptionBeforeDamage;

    @ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float customMobsSpawner$applyMobStaggerDamageBonus(float amount, DamageSource source) {
        amount = customMobsSpawner$applySpellRageDamageBonus(amount, source);
        if ((Object) this instanceof LivingEntity livingEntity) {
            amount = MobPostureHandler.modifyIncomingDamage(livingEntity, source, amount);
        }
        Float spellProjectileOverride = CustomSkillRegistry.currentSpellProjectileDamageOverride();
        if (spellProjectileOverride != null) {
            return spellProjectileOverride;
        }
        return amount;
    }

    @ModifyVariable(method = "applyDamage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float customMobsSpawner$applyPostMitigationCritDamage(float amount, DamageSource source) {
        if (amount <= 0.0F || !((Object) this instanceof LivingEntity target) || target.getWorld().isClient()) {
            return amount;
        }

        if (!(source.getAttacker() instanceof LivingEntity attacker) || attacker == target) {
            return amount;
        }

        EntityAttributeInstance chanceInstance = attacker.getAttributeInstance(ModAttributes.ZENITH_CRIT_CHANCE);
        EntityAttributeInstance damageInstance = attacker.getAttributeInstance(ModAttributes.ZENITH_CRIT_DAMAGE);
        double critChance = chanceInstance == null ? 0.0D : Math.max(0.0D, chanceInstance.getValue());
        double critDamage = damageInstance == null ? 0.0D : Math.max(0.0D, damageInstance.getValue());
        if (critChance <= 0.0D || critDamage <= 0.0D) {
            return amount;
        }

        int crits = (int) Math.floor(critChance);
        double fractionalChance = critChance - crits;
        if (fractionalChance > 0.0D && attacker.getRandom().nextDouble() < fractionalChance) {
            crits++;
        }
        if (crits <= 0) {
            return amount;
        }

        double multiplier = 1.0D + critDamage * crits;
        return (float) Math.min(Float.MAX_VALUE, amount * multiplier);
    }

    private float customMobsSpawner$applySpellRageDamageBonus(float amount, DamageSource source) {
        if (amount <= 0.0F || !SpellDamagePenetrationCompat.isSpellDamageActive()) {
            return amount;
        }

        Entity attacker = source.getAttacker();
        if (!(attacker instanceof LivingEntity livingAttacker) || livingAttacker.getWorld().isClient()) {
            return amount;
        }

        long time = livingAttacker.getWorld().getTime();
        UUID attackerUuid = livingAttacker.getUuid();
        Long lastProcTime = SPELL_RAGE_COOLDOWNS.get(attackerUuid);
        if (lastProcTime != null && time - lastProcTime < SPELL_RAGE_COOLDOWN_TICKS) {
            return amount;
        }

        EntityAttribute rageAttribute = Registries.ATTRIBUTE.getOrEmpty(MORE_RPG_CLASSES_RAGE_MODIFIER).orElse(null);
        if (rageAttribute == null) {
            return amount;
        }

        EntityAttributeInstance rage = livingAttacker.getAttributeInstance(rageAttribute);
        if (rage == null || rage.getValue() == 100.0D) {
            return amount;
        }

        float maxHealth = livingAttacker.getMaxHealth();
        float health = livingAttacker.getHealth();
        if (maxHealth <= 0.0F || health >= maxHealth) {
            return amount;
        }

        float missingHealthRatio = (maxHealth - health) / maxHealth;
        float rageRatio = (float) ((rage.getValue() - 100.0D) / 100.0D);
        float bonus = Math.max(0.1F, amount * rageRatio * missingHealthRatio);
        SPELL_RAGE_COOLDOWNS.put(attackerUuid, time);
        return amount + bonus;
    }

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void customMobsSpawner$cancelableWhenHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof PlayerEntity player) {
            customMobsSpawner$healthAndAbsorptionBeforeDamage = player.getHealth() + player.getAbsorptionAmount();
            if (customMobsSpawner$shouldTraceDamage(source)) {
                customMobsSpawner$debugPlayerDamage("incoming", player, source, amount);
            }
            if (customMobsSpawner$isLostCaptainDirectAttack(source)) {
                customMobsSpawner$debugPlayerDamage("cancelled_direct_lost_captain", player, source, amount);
                cir.setReturnValue(false);
                return;
            }
            PlayerParryHandler.recordLivingEntityHit(player, source);
            if (PlayerParryHandler.tryParry(player, source) || PlayerParryHandler.tryAbsorbBlock(player, source, amount)) {
                cir.setReturnValue(false);
                return;
            }
        } else if ((Object) this instanceof LivingEntity livingEntity) {
            MobPostureHandler.applyPlayerDamage(livingEntity, source, amount);
        }
        if (CustomSkillRegistry.runCancelableHurt((LivingEntity) (Object) this, source, amount)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "stopUsingItem", at = @At("HEAD"))
    private void customMobsSpawner$startBlockingCooldown(CallbackInfo ci) {
        if ((Object) this instanceof PlayerEntity player) {
            PlayerParryHandler.finishBlockIfActive(player);
        }
    }

    @Inject(method = "clearActiveItem", at = @At("HEAD"))
    private void customMobsSpawner$startBlockingCooldownOnClear(CallbackInfo ci) {
        if ((Object) this instanceof PlayerEntity player) {
            PlayerParryHandler.finishBlockIfActive(player);
        }
    }

    @Inject(method = "damage", at = @At("RETURN"))
    private void customMobsSpawner$whenHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            if ((Object) this instanceof PlayerEntity player) {
                float actualLoss = Math.max(0.0F, customMobsSpawner$healthAndAbsorptionBeforeDamage - player.getHealth() - player.getAbsorptionAmount());
                if (customMobsSpawner$shouldTraceDamage(source)) {
                    customMobsSpawner$debugPlayerDamage("applied actual_loss=" + actualLoss, player, source, amount);
                }
                if (player instanceof ServerPlayerEntity serverPlayer) {
                    ComboHandler.decrementFromDamageTaken(serverPlayer, source);
                }
                PlayerParryHandler.applyTakenDamagePostureLoss(player, source, actualLoss);
            }
            ComboHandler.recordDamageSuccess((LivingEntity) (Object) this, source);
            customMobsSpawner$runRangedHitSkill(source);
            customMobsSpawner$runRangedWeaponAttackSkill(source);
            CustomSkillRegistry.runHurt((LivingEntity) (Object) this, source, amount);
        }
    }

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void customMobsSpawner$whenKilled(DamageSource damageSource, CallbackInfo ci) {
        customMobsSpawner$refundManaOnMobKill(damageSource);
        CustomSkillRegistry.run((LivingEntity) (Object) this, SkillTrigger.WHEN_KILLED);
    }

    private void customMobsSpawner$refundManaOnMobKill(DamageSource source) {
        if (!((Object) this instanceof MobEntity)) {
            return;
        }

        ServerPlayerEntity player = customMobsSpawner$serverPlayerAttacker(source);
        if (player != null) {
            AmethystRpgManaCompat.restoreMana(player, MANA_REFUND_ON_MOB_KILL);
        }
    }

    private ServerPlayerEntity customMobsSpawner$serverPlayerAttacker(DamageSource source) {
        if (source.getAttacker() instanceof ServerPlayerEntity player) {
            return player;
        }
        if (source.getSource() instanceof ServerPlayerEntity player) {
            return player;
        }
        if (source.getSource() instanceof ProjectileEntity projectile && projectile.getOwner() instanceof ServerPlayerEntity player) {
            return player;
        }
        return null;
    }

    private void customMobsSpawner$runRangedHitSkill(DamageSource source) {
        if (!((Object) this instanceof LivingEntity target)) {
            return;
        }
        if (source.getSource() instanceof ProjectileEntity projectile
                && projectile.getOwner() instanceof ServerPlayerEntity player
                && customMobsSpawner$isUsingRangedWeapon(player)) {
            CustomSkillRegistry.runRangedHit(player, target);
        }
    }

    private void customMobsSpawner$runRangedWeaponAttackSkill(DamageSource source) {
        if (!((Object) this instanceof LivingEntity target)) {
            return;
        }
        if (source.getSource() instanceof ProjectileEntity projectile
                && projectile.getOwner() instanceof ServerPlayerEntity player
                && customMobsSpawner$isUsingRangedWeapon(player)) {
            CustomSkillRegistry.runAttack(player, target);
        }
    }

    private boolean customMobsSpawner$isUsingRangedWeapon(ServerPlayerEntity player) {
        return player.getMainHandStack().getItem() instanceof RangedWeaponItem;
    }

    private boolean customMobsSpawner$isLostCaptainDirectAttack(DamageSource source) {
        if (CustomSkillRegistry.isSpellProjectileImpacting()) {
            return false;
        }
        Entity attacker = source.getAttacker();
        Entity sourceEntity = source.getSource();
        return attacker instanceof LivingEntity livingAttacker
                && customMobsSpawner$isLostCaptain(livingAttacker)
                && (sourceEntity == null || sourceEntity == attacker);
    }

    private boolean customMobsSpawner$isLostCaptain(LivingEntity entity) {
        return entity.getCommandTags().contains("lost_captain")
                || entity.getCommandTags().contains("custom_mob_cmobs_lost_captain")
                || entity.getCommandTags().contains("custom_mob_cmobs_overworld_lost_captain")
                || entity.getCommandTags().contains("custom_mob_cmobs_overworld_lost_lost_captain")
                || "Lost Captain".equals(entity.getName().getString());
    }

    private boolean customMobsSpawner$shouldTraceDamage(DamageSource source) {
        Entity attacker = source.getAttacker();
        if (attacker instanceof LivingEntity livingAttacker && customMobsSpawner$isLostCaptain(livingAttacker)) {
            return true;
        }

        Entity sourceEntity = source.getSource();
        return sourceEntity != null && sourceEntity.getClass().getName().contains("SpellProjectile");
    }

    private void customMobsSpawner$debugPlayerDamage(String stage, PlayerEntity player, DamageSource source, float amount) {
        if (!customMobsSpawner$shouldTraceDamage(source)) {
            return;
        }

        Entity attacker = source.getAttacker();
        Entity sourceEntity = source.getSource();
        String attackerName = attacker == null ? "none" : attacker.getName().getString();
        String sourceName = sourceEntity == null ? "none" : sourceEntity.getName().getString();
        String sourceClass = sourceEntity == null ? "none" : sourceEntity.getClass().getName();
        CustomMobsSpawnerLog.info("Damage trace " + stage
                + " player=" + player.getName().getString()
                + " amount=" + amount
                + " health=" + player.getHealth()
                + " source=" + source.getName()
                + " attacker=" + attackerName
                + " source_entity=" + sourceName
                + " source_class=" + sourceClass);
    }
}
