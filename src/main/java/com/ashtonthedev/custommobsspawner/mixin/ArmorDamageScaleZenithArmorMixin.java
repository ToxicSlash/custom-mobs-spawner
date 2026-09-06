package com.ashtonthedev.custommobsspawner.mixin;

import com.ashtonthedev.custommobsspawner.data.CustomMobsSpawnerLog;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Mixin(value = LivingEntity.class, priority = 500)
public abstract class ArmorDamageScaleZenithArmorMixin {
    @Unique
    private static final Identifier CUSTOM_MOBS_SPAWNER$ARMOR_SHRED =
            new Identifier("zenith_attributes", "armor_shred");
    @Unique
    private static final Identifier CUSTOM_MOBS_SPAWNER$ARMOR_PIERCE =
            new Identifier("zenith_attributes", "armor_pierce");
    @Unique
    private static final String CUSTOM_MOBS_SPAWNER$FORMULA_ARMOR_ARG = "armor";
    @Unique
    private static final String CUSTOM_MOBS_SPAWNER$FORMULA_TOUGHNESS_ARG = "toughness";
    @Unique
    private static final String CUSTOM_MOBS_SPAWNER$FORMULA_HITPCT_ARG = "hitpct";
    @Unique
    private static final String CUSTOM_MOBS_SPAWNER$FORMULA_DAMAGE_ARG = "damage";
    @Unique
    private static boolean customMobsSpawner$loggedFormulaFailure;

    @Shadow
    protected abstract void damageArmor(DamageSource source, float amount);

    @Shadow
    public abstract int getArmor();

    @Shadow
    public abstract double getAttributeValue(EntityAttribute attribute);

    @Shadow
    public abstract float getMaxHealth();

    @Inject(method = "applyArmorToDamage", at = @At("HEAD"), cancellable = true)
    private void customMobsSpawner$applyZenithPierceToArmorDamageScale(
            DamageSource source,
            float amount,
            CallbackInfoReturnable<Float> cir
    ) {
        if (source.isIn(DamageTypeTags.BYPASSES_ARMOR)) {
            return;
        }
        if (Float.isInfinite(amount) || Float.isNaN(amount)) {
            cir.setReturnValue(0.0F);
            return;
        }
        if (amount <= 0.0F) {
            return;
        }

        damageArmor(source, amount);
        float toughness = (float) getAttributeValue(EntityAttributes.GENERIC_ARMOR_TOUGHNESS);
        float armor = customMobsSpawner$zenithAdjustedArmor(source, getArmor(), toughness);
        float scaledDamage = amount;

        try {
            Object config = customMobsSpawner$armorDamageScaleConfig();
            if (armor > 0.0F) {
                scaledDamage = customMobsSpawner$evaluateFormula(
                        config,
                        "armordamagereduction",
                        CUSTOM_MOBS_SPAWNER$FORMULA_ARMOR_ARG,
                        armor,
                        CUSTOM_MOBS_SPAWNER$FORMULA_DAMAGE_ARG,
                        amount
                );
            }

            if (toughness > 0.0F) {
                float hitPct = Math.max(0.0F, Math.min(1.0F, scaledDamage / getMaxHealth()));
                scaledDamage = customMobsSpawner$evaluateFormula(
                        config,
                        "thoughnessdamagereduction",
                        CUSTOM_MOBS_SPAWNER$FORMULA_TOUGHNESS_ARG,
                        toughness,
                        CUSTOM_MOBS_SPAWNER$FORMULA_HITPCT_ARG,
                        hitPct,
                        CUSTOM_MOBS_SPAWNER$FORMULA_DAMAGE_ARG,
                        scaledDamage
                );
            }
        } catch (Exception exception) {
            customMobsSpawner$logFormulaFailure(exception);
            return;
        }

        cir.setReturnValue(Math.max(0.0F, scaledDamage));
    }

    @Unique
    private static float customMobsSpawner$zenithAdjustedArmor(
            DamageSource source,
            float armor,
            float toughness
    ) {
        LivingEntity attacker = customMobsSpawner$livingAttacker(source);
        if (attacker == null) {
            return armor;
        }

        float toughnessReduction = Math.min(toughness * 0.02F, 0.6F);
        float armorShred = customMobsSpawner$attributeValue(attacker, CUSTOM_MOBS_SPAWNER$ARMOR_SHRED);
        if (armorShred > 0.001F) {
            armor *= 1.0F - armorShred * (1.0F - toughnessReduction);
        }

        float armorPierce = customMobsSpawner$attributeValue(attacker, CUSTOM_MOBS_SPAWNER$ARMOR_PIERCE);
        if (armorPierce > 0.001F) {
            armor -= armorPierce * (1.0F - toughnessReduction);
        }

        return Math.max(0.0F, armor);
    }

    @Unique
    private static float customMobsSpawner$attributeValue(LivingEntity entity, Identifier id) {
        EntityAttribute attribute = Registries.ATTRIBUTE.getOrEmpty(id).orElse(null);
        if (attribute == null) {
            return 0.0F;
        }

        EntityAttributeInstance instance = entity.getAttributeInstance(attribute);
        return instance == null ? 0.0F : (float) instance.getValue();
    }

    @Unique
    private static LivingEntity customMobsSpawner$livingAttacker(DamageSource source) {
        if (source.getAttacker() instanceof LivingEntity attacker) {
            return attacker;
        }
        if (source.getSource() instanceof ProjectileEntity projectile && projectile.getOwner() instanceof LivingEntity owner) {
            return owner;
        }
        if (source.getSource() instanceof LivingEntity sourceEntity) {
            return sourceEntity;
        }
        return null;
    }

    @Unique
    private static float customMobsSpawner$evaluateFormula(
            Object config,
            String fieldName,
            Object... arguments
    ) throws ReflectiveOperationException {
        Field field = config.getClass().getField(fieldName);
        Object expression = field.get(config);
        Method with = expression.getClass().getMethod("with", String.class, Object.class);
        for (int i = 0; i + 1 < arguments.length; i += 2) {
            expression = with.invoke(expression, arguments[i], arguments[i + 1]);
        }
        Object evaluation = expression.getClass().getMethod("evaluate").invoke(expression);
        Object number = evaluation.getClass().getMethod("getNumberValue").invoke(evaluation);
        return ((Number) number).floatValue();
    }

    @Unique
    private static Object customMobsSpawner$armorDamageScaleConfig() throws ReflectiveOperationException {
        Class<?> armorDamage = Class.forName("com.armordamagescale.ArmorDamage");
        Object cupboardConfig = armorDamage.getField("config").get(null);
        return cupboardConfig.getClass().getMethod("getCommonConfig").invoke(cupboardConfig);
    }

    @Unique
    private static void customMobsSpawner$logFormulaFailure(Exception exception) {
        if (customMobsSpawner$loggedFormulaFailure) {
            return;
        }
        customMobsSpawner$loggedFormulaFailure = true;
        CustomMobsSpawnerLog.warn("Failed to apply Zenith armor pierce/shred through ArmorDamageScale: "
                + exception.getClass().getName() + ": " + exception.getMessage());
    }
}
