package com.ashtonthedev.custommobsspawner.compat;

import com.ashtonthedev.custommobsspawner.combat.ModAttributes;
import com.cleannrooster.rpgmana.api.ManaInterface;
import net.minecraft.entity.player.PlayerEntity;

public final class RpgManaOverchargeCompat {
    private static final double MANA_EPSILON = 0.0001D;

    private RpgManaOverchargeCompat() {
    }

    public static double getOverchargeCapacity(PlayerEntity player) {
        return Math.max(0.0D, player.getAttributeValue(ModAttributes.MANA_OVERCHARGE));
    }

    public static double getOverchargeMaxMana(PlayerEntity player, ManaInterface manaInterface) {
        return manaInterface.getMaxMana() + getOverchargeCapacity(player);
    }

    public static boolean isOvercharged(PlayerEntity player, ManaInterface manaInterface) {
        return manaInterface.getMana() > manaInterface.getMaxMana() + MANA_EPSILON;
    }
}
