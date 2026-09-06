package com.ashtonthedev.custommobsspawner.compat;

import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;

public final class SpellDamagePenetrationCompat {
    private static final double ARMOR_PENETRATION = 0.5D;
    private static final ThreadLocal<Integer> SPELL_DAMAGE_DEPTH = ThreadLocal.withInitial(() -> 0);

    private SpellDamagePenetrationCompat() {
    }

    public static void beginSpellDamage() {
        SPELL_DAMAGE_DEPTH.set(SPELL_DAMAGE_DEPTH.get() + 1);
    }

    public static void endSpellDamage() {
        int depth = SPELL_DAMAGE_DEPTH.get();
        if (depth <= 1) {
            SPELL_DAMAGE_DEPTH.remove();
            return;
        }
        SPELL_DAMAGE_DEPTH.set(depth - 1);
    }

    public static boolean isSpellDamageActive() {
        return SPELL_DAMAGE_DEPTH.get() > 0;
    }

    public static int penetrateArmor(int armor) {
        if (!isSpellDamageActive() || armor <= 0) {
            return armor;
        }
        return Math.max(0, (int) Math.floor(armor * retainedDefenseMultiplier()));
    }

    public static double penetrateArmorToughness(EntityAttribute attribute, double value) {
        if (!isSpellDamageActive()
                || attribute != EntityAttributes.GENERIC_ARMOR_TOUGHNESS
                || value <= 0.0D) {
            return value;
        }
        return Math.max(0.0D, value * retainedDefenseMultiplier());
    }

    public static int penetrateProtection(int protection) {
        if (!isSpellDamageActive() || protection <= 0) {
            return protection;
        }
        return Math.max(0, (int) Math.floor(protection * retainedDefenseMultiplier()));
    }

    private static double retainedDefenseMultiplier() {
        return 1.0D - ARMOR_PENETRATION;
    }
}
