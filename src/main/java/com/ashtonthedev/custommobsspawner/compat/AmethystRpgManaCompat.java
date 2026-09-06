package com.ashtonthedev.custommobsspawner.compat;

import com.cleannrooster.rpgmana.api.ManaInterface;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

public final class AmethystRpgManaCompat {
    private static final double AMETHYST_MANA_TO_RPG_MANA = 1.0D;
    private static final double MINIMUM_MANA_COST_FRACTION = 0.5D;
    private static final int MANA_POTION_AMETHYST_RESTORE = 50;
    private static final int LESSER_MANA_POTION_AMETHYST_RESTORE = 25;
    private static final double MANA_EPSILON = 0.0001D;
    private static StatusEffect manaRegenerationStatus;
    private static boolean lookedUpManaRegenerationStatus;

    private AmethystRpgManaCompat() {
    }

    public static boolean canUseRpgMana(LivingEntity user) {
        return user instanceof PlayerEntity && user instanceof ManaInterface;
    }

    public static boolean hasRpgMana(PlayerEntity player) {
        return player instanceof ManaInterface;
    }

    public static boolean checkManaCost(int amethystManaCost, ItemCostContext context) {
        if (amethystManaCost <= 0) {
            return true;
        }

        LivingEntity user = context.user();
        if (!(user instanceof ManaInterface manaInterface)) {
            return true;
        }

        return manaInterface.getMana() + MANA_EPSILON >= toRpgMana(amethystManaCost) * MINIMUM_MANA_COST_FRACTION;
    }

    public static void spendMana(int amethystManaCost, LivingEntity user) {
        if (!(user instanceof ManaInterface manaInterface)) {
            return;
        }

        double rpgManaCost = toRpgMana(amethystManaCost);
        if (rpgManaCost > 0.0D) {
            manaInterface.spendMana(-rpgManaCost);
        }
    }

    public static boolean canRestoreMana(PlayerEntity player) {
        if (!(player instanceof ManaInterface manaInterface)) {
            return false;
        }
        return manaInterface.getMana() + MANA_EPSILON < manaInterface.getMaxMana();
    }

    public static boolean canRestoreMana(PlayerEntity player, boolean allowOvercharge) {
        if (!(player instanceof ManaInterface manaInterface)) {
            return false;
        }
        double maxMana = allowOvercharge
                ? RpgManaOverchargeCompat.getOverchargeMaxMana(player, manaInterface)
                : manaInterface.getMaxMana();
        return manaInterface.getMana() + MANA_EPSILON < maxMana;
    }

    public static int restoreFromAmethystManaPotion(PlayerEntity player) {
        return restoreAmethystMana(player, MANA_POTION_AMETHYST_RESTORE);
    }

    public static int restoreFromLesserManaPotion(PlayerEntity player) {
        return restoreAmethystMana(player, LESSER_MANA_POTION_AMETHYST_RESTORE);
    }

    public static int restoreMana(PlayerEntity player, int amethystManaAmount) {
        return restoreAmethystMana(player, amethystManaAmount, false);
    }

    public static int restoreMana(PlayerEntity player, int amethystManaAmount, boolean allowOvercharge) {
        return restoreAmethystMana(player, amethystManaAmount, allowOvercharge);
    }

    public static boolean hasManaRegenerationStatus(PlayerEntity player) {
        StatusEffect statusEffect = getManaRegenerationStatus();
        return statusEffect != null && player.hasStatusEffect(statusEffect);
    }

    private static int restoreAmethystMana(PlayerEntity player, int amethystManaAmount) {
        return restoreAmethystMana(player, amethystManaAmount, false);
    }

    private static int restoreAmethystMana(PlayerEntity player, int amethystManaAmount, boolean allowOvercharge) {
        if (!(player instanceof ManaInterface manaInterface)) {
            return 0;
        }

        double maxMana = allowOvercharge
                ? RpgManaOverchargeCompat.getOverchargeMaxMana(player, manaInterface)
                : manaInterface.getMaxMana();
        double missingMana = Math.max(0.0D, maxMana - manaInterface.getMana());
        double rpgManaRestore = Math.min(toRpgManaRaw(amethystManaAmount), missingMana);
        if (rpgManaRestore <= MANA_EPSILON) {
            return 0;
        }

        manaInterface.spendMana(rpgManaRestore);
        return (int) Math.round(rpgManaRestore / AMETHYST_MANA_TO_RPG_MANA);
    }

    private static double toRpgMana(int amethystManaCost) {
        if (amethystManaCost <= 0) {
            return 0.0D;
        }
        return Math.max(1.0D, toRpgManaRaw(amethystManaCost));
    }

    private static double toRpgManaRaw(int amethystManaAmount) {
        return Math.max(0.0D, amethystManaAmount * AMETHYST_MANA_TO_RPG_MANA);
    }

    private static StatusEffect getManaRegenerationStatus() {
        if (lookedUpManaRegenerationStatus) {
            return manaRegenerationStatus;
        }
        lookedUpManaRegenerationStatus = true;
        try {
            Class<?> registerStatusClass = Class.forName("me.fzzyhmstrs.amethyst_imbuement.registry.RegisterStatus");
            Object registerStatus = registerStatusClass.getField("INSTANCE").get(null);
            manaRegenerationStatus = (StatusEffect) registerStatusClass
                    .getMethod("getMANA_REGENERATION")
                    .invoke(registerStatus);
        } catch (ReflectiveOperationException | ClassCastException exception) {
            manaRegenerationStatus = null;
        }
        return manaRegenerationStatus;
    }

    public record ItemCostContext(World world, LivingEntity user) {
    }
}
