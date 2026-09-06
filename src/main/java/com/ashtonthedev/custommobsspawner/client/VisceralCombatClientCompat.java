package com.ashtonthedev.custommobsspawner.client;

import com.ashtonthedev.custommobsspawner.config.CustomMobsSpawnerConfig;
import com.google.common.collect.Multimap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;

public final class VisceralCombatClientCompat {
    private static final double VANILLA_ATTACK_SPEED = 4.0D;

    private VisceralCombatClientCompat() {
    }

    public static void applyAttackStartLunge(ClientPlayerEntity player) {
        if (!FabricLoader.getInstance().isModLoaded("visceral_combat")) {
            return;
        }

        CustomMobsSpawnerConfig.ConfigData config = CustomMobsSpawnerConfig.get();
        if (!config.visceralCombatLungeScalingEnabled) {
            return;
        }

        double attackSpeed = Math.max(0.1D, effectiveAttackSpeed(player));
        double extraLunge = Math.max(0.0D, (VANILLA_ATTACK_SPEED / attackSpeed - 1.0D) * config.visceralCombatExtraLungeScale);
        if (extraLunge < config.visceralCombatMinimumExtraLunge) {
            return;
        }

        Vec3d forward = player.getRotationVector();
        forward = new Vec3d(forward.x, 0.0D, forward.z);
        if (forward.lengthSquared() < 0.001D) {
            return;
        }

        Vec3d lunge = forward.normalize().multiply(extraLunge);
        player.addVelocity(lunge.x, 0.0D, lunge.z);
        player.velocityModified = true;
    }

    private static double effectiveAttackSpeed(ClientPlayerEntity player) {
        double attributeSpeed = player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_SPEED);
        double heldItemSpeed = heldItemAttackSpeed(player.getMainHandStack());
        return heldItemSpeed > 0.0D ? Math.min(attributeSpeed, heldItemSpeed) : attributeSpeed;
    }

    private static double heldItemAttackSpeed(ItemStack stack) {
        Multimap<EntityAttribute, EntityAttributeModifier> modifiers = stack.getAttributeModifiers(EquipmentSlot.MAINHAND);
        if (!modifiers.containsKey(EntityAttributes.GENERIC_ATTACK_SPEED)) {
            return 0.0D;
        }

        double speed = VANILLA_ATTACK_SPEED;
        for (EntityAttributeModifier modifier : modifiers.get(EntityAttributes.GENERIC_ATTACK_SPEED)) {
            if (modifier.getOperation() == EntityAttributeModifier.Operation.ADDITION) {
                speed += modifier.getValue();
            }
        }
        for (EntityAttributeModifier modifier : modifiers.get(EntityAttributes.GENERIC_ATTACK_SPEED)) {
            if (modifier.getOperation() == EntityAttributeModifier.Operation.MULTIPLY_BASE) {
                speed += VANILLA_ATTACK_SPEED * modifier.getValue();
            }
        }
        for (EntityAttributeModifier modifier : modifiers.get(EntityAttributes.GENERIC_ATTACK_SPEED)) {
            if (modifier.getOperation() == EntityAttributeModifier.Operation.MULTIPLY_TOTAL) {
                speed *= 1.0D + modifier.getValue();
            }
        }
        return speed;
    }
}
