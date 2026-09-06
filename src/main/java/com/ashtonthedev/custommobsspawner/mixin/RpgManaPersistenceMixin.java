package com.ashtonthedev.custommobsspawner.mixin;

import com.ashtonthedev.custommobsspawner.compat.RpgManaOverchargeCompat;
import com.cleannrooster.rpgmana.Rpgmana;
import com.cleannrooster.rpgmana.api.ManaInstance;
import com.cleannrooster.rpgmana.api.ManaInterface;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(PlayerEntity.class)
public abstract class RpgManaPersistenceMixin {
    private static final String CUSTOM_MOBS_SPAWNER_RPG_MANA_KEY = "cmobs_rpgmana";
    private static final String CUSTOM_MOBS_SPAWNER_RPG_MANA_ATTRIBUTES_V1_KEY = "cmobs_rpgmana_attributes_v1";
    private static final double LEGACY_BASE_MANA = 0.0D;
    private static final double TARGET_BASE_MANA = 80.0D;
    private static final double LEGACY_BASE_MANA_REGEN = 4.0D;
    private static final double TARGET_BASE_MANA_REGEN = 1.0D;
    private static final double ATTRIBUTE_EPSILON = 0.0001D;

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void customMobsSpawner$writeRpgMana(NbtCompound nbt, CallbackInfo ci) {
        if ((Object) this instanceof PlayerEntity player && player instanceof ManaInterface manaInterface) {
            double mana = Math.min(manaInterface.getMana(), RpgManaOverchargeCompat.getOverchargeMaxMana(player, manaInterface));
            nbt.putDouble(CUSTOM_MOBS_SPAWNER_RPG_MANA_KEY, mana);
            nbt.putBoolean(CUSTOM_MOBS_SPAWNER_RPG_MANA_ATTRIBUTES_V1_KEY, true);
        }
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void customMobsSpawner$readRpgMana(NbtCompound nbt, CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (!nbt.getBoolean(CUSTOM_MOBS_SPAWNER_RPG_MANA_ATTRIBUTES_V1_KEY)) {
            customMobsSpawner$migrateLegacyRpgManaAttributes(player);
        }

        if (!nbt.contains(CUSTOM_MOBS_SPAWNER_RPG_MANA_KEY, NbtElement.DOUBLE_TYPE)
                || !(player instanceof ManaInterface manaInterface)) {
            return;
        }

        double savedMana = nbt.getDouble(CUSTOM_MOBS_SPAWNER_RPG_MANA_KEY);
        if (!Double.isFinite(savedMana)) {
            return;
        }

        double targetMana = Math.max(-999999.0D, Math.min(
                savedMana,
                RpgManaOverchargeCompat.getOverchargeMaxMana(player, manaInterface)
        ));
        customMobsSpawner$setManaWithoutPowerPulse(manaInterface, targetMana);
    }

    private static void customMobsSpawner$migrateLegacyRpgManaAttributes(PlayerEntity player) {
        customMobsSpawner$migrateLegacyBaseValue(player, Rpgmana.MANA, LEGACY_BASE_MANA, TARGET_BASE_MANA);
        customMobsSpawner$migrateLegacyBaseValue(player, Rpgmana.MANAREGEN, LEGACY_BASE_MANA_REGEN, TARGET_BASE_MANA_REGEN);
    }

    private static void customMobsSpawner$migrateLegacyBaseValue(
            PlayerEntity player,
            EntityAttribute attribute,
            double legacyValue,
            double targetValue
    ) {
        EntityAttributeInstance instance = player.getAttributeInstance(attribute);
        if (instance != null && Math.abs(instance.getBaseValue() - legacyValue) <= ATTRIBUTE_EPSILON) {
            instance.setBaseValue(targetValue);
        }
    }

    private static void customMobsSpawner$setManaWithoutPowerPulse(ManaInterface manaInterface, double targetMana) {
        double delta = targetMana - manaInterface.getMana();
        if (Math.abs(delta) <= ATTRIBUTE_EPSILON) {
            return;
        }

        List<ManaInstance> manaInstances = manaInterface.getManaInstances();
        int previousSize = manaInstances == null ? -1 : manaInstances.size();
        manaInterface.spendMana(delta);

        if (manaInstances != null && previousSize >= 0 && manaInstances.size() > previousSize) {
            manaInstances.subList(previousSize, manaInstances.size()).clear();
        }
    }
}
