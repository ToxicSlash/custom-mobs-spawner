package com.ashtonthedev.custommobsspawner.combat;

import com.ashtonthedev.custommobsspawner.CustomMobsSpawner;
import net.minecraft.entity.attribute.ClampedEntityAttribute;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModAttributes {
    public static final double DEFAULT_PARRY_WINDOW_TICKS = 0.0D;
    public static final double DEFAULT_POSTURE_HEALTH = 100.0D;
    public static final double DEFAULT_POSTURE_REGEN = 40.0D;
    public static final double DEFAULT_POSTURE_DAMAGE = 0.0D;
    public static final double DEFAULT_POSTURE_DAMAGE_MULTIPLIER = 1.0D;
    public static final double DEFAULT_POSTURE_DAMAGE_RESISTANCE = 0.0D;
    public static final double DEFAULT_PVP_TAKEN_POSTURE_DAMAGE_MULTIPLIER = 1.0D;
    public static final double DEFAULT_STAGGER_DAMAGE_MULTIPLIER = 1.5D;
    public static final double DEFAULT_ATTACK_RANGE = 0.0D;
    public static final double DEFAULT_MANA_OVERCHARGE = 80.0D;
    public static final double DEFAULT_ZENITH_CRIT_CHANCE = 0.0D;
    public static final double DEFAULT_ZENITH_CRIT_DAMAGE = 0.0D;
    public static final double SHIELD_PARRY_WINDOW_TICKS = 5.0D;
    public static final double TWO_HANDED_PARRY_WINDOW_TICKS = 2.0D;
    public static final double SHIELD_POSTURE_HEALTH_BONUS = 50.0D;
    public static final double TWO_HANDED_POSTURE_HEALTH_BONUS = 25.0D;
    public static final double KNIFE_POSTURE_DAMAGE_BONUS = 10.0D;
    public static final double KNIFE_STAGGER_DAMAGE_MULTIPLIER_BONUS = 1.0D;
    public static final double RAPIER_POSTURE_DAMAGE_BONUS = 10.0D;
    public static final double RAPIER_STAGGER_DAMAGE_MULTIPLIER_BONUS = 1.0D;
    public static final double DEFAULT_MOB_POSTURE_HEALTH = 100.0D;

    public static final EntityAttribute PARRY_WINDOW_TICKS = new ClampedEntityAttribute(
            "attribute.name.cmobs.parry_window_ticks",
            DEFAULT_PARRY_WINDOW_TICKS,
            0.0D,
            60.0D
    ).setTracked(true);

    public static final EntityAttribute POSTURE_HEALTH = new ClampedEntityAttribute(
            "attribute.name.cmobs.posture_health",
            DEFAULT_POSTURE_HEALTH,
            0.0D,
            1024.0D
    ).setTracked(true);

    public static final EntityAttribute POSTURE_REGEN = new ClampedEntityAttribute(
            "attribute.name.cmobs.posture_regen",
            DEFAULT_POSTURE_REGEN,
            0.0D,
            1024.0D
    ).setTracked(true);

    public static final EntityAttribute POSTURE_DAMAGE = new ClampedEntityAttribute(
            "attribute.name.cmobs.posture_damage",
            DEFAULT_POSTURE_DAMAGE,
            0.0D,
            1024.0D
    ).setTracked(true);

    public static final EntityAttribute POSTURE_DAMAGE_MULTIPLIER = new ClampedEntityAttribute(
            "attribute.name.cmobs.posture_damage_multiplier",
            DEFAULT_POSTURE_DAMAGE_MULTIPLIER,
            0.0D,
            10.0D
    ).setTracked(true);

    public static final EntityAttribute POSTURE_DAMAGE_RESISTANCE = new ClampedEntityAttribute(
            "attribute.name.cmobs.posture_damage_resistance",
            DEFAULT_POSTURE_DAMAGE_RESISTANCE,
            0.0D,
            1.0D
    ).setTracked(true);

    public static final EntityAttribute PVP_TAKEN_POSTURE_DAMAGE_MULTIPLIER = new ClampedEntityAttribute(
            "attribute.name.cmobs.pvp_taken_posture_damage_multiplier",
            DEFAULT_PVP_TAKEN_POSTURE_DAMAGE_MULTIPLIER,
            0.0D,
            10.0D
    ).setTracked(true);

    public static final EntityAttribute STAGGER_DAMAGE_MULTIPLIER = new ClampedEntityAttribute(
            "attribute.name.cmobs.stagger_damage_multiplier",
            DEFAULT_STAGGER_DAMAGE_MULTIPLIER,
            0.0D,
            10.0D
    ).setTracked(true);

    public static final EntityAttribute ATTACK_RANGE = new ClampedEntityAttribute(
            "attribute.name.cmobs.attack_range",
            DEFAULT_ATTACK_RANGE,
            -64.0D,
            64.0D
    ).setTracked(true);

    public static final EntityAttribute MANA_OVERCHARGE = new ClampedEntityAttribute(
            "attribute.name.cmobs.mana_overcharge",
            DEFAULT_MANA_OVERCHARGE,
            0.0D,
            1024.0D
    ).setTracked(true);

    public static final EntityAttribute ZENITH_CRIT_CHANCE = new ClampedEntityAttribute(
            "attribute.name.cmobs.zenith_crit_chance",
            DEFAULT_ZENITH_CRIT_CHANCE,
            0.0D,
            100.0D
    ).setTracked(true);

    public static final EntityAttribute ZENITH_CRIT_DAMAGE = new ClampedEntityAttribute(
            "attribute.name.cmobs.zenith_crit_damage",
            DEFAULT_ZENITH_CRIT_DAMAGE,
            0.0D,
            1024.0D
    ).setTracked(true);

    private ModAttributes() {
    }

    public static void register() {
        Registry.register(Registries.ATTRIBUTE, CustomMobsSpawner.id("parry_window_ticks"), PARRY_WINDOW_TICKS);
        Registry.register(Registries.ATTRIBUTE, CustomMobsSpawner.id("posture_health"), POSTURE_HEALTH);
        Registry.register(Registries.ATTRIBUTE, CustomMobsSpawner.id("posture_regen"), POSTURE_REGEN);
        Registry.register(Registries.ATTRIBUTE, CustomMobsSpawner.id("posture_damage"), POSTURE_DAMAGE);
        Registry.register(Registries.ATTRIBUTE, CustomMobsSpawner.id("posture_damage_multiplier"), POSTURE_DAMAGE_MULTIPLIER);
        Registry.register(Registries.ATTRIBUTE, CustomMobsSpawner.id("posture_damage_resistance"), POSTURE_DAMAGE_RESISTANCE);
        Registry.register(Registries.ATTRIBUTE, CustomMobsSpawner.id("pvp_taken_posture_damage_multiplier"), PVP_TAKEN_POSTURE_DAMAGE_MULTIPLIER);
        Registry.register(Registries.ATTRIBUTE, CustomMobsSpawner.id("stagger_damage_multiplier"), STAGGER_DAMAGE_MULTIPLIER);
        Registry.register(Registries.ATTRIBUTE, CustomMobsSpawner.id("attack_range"), ATTACK_RANGE);
        Registry.register(Registries.ATTRIBUTE, CustomMobsSpawner.id("mana_overcharge"), MANA_OVERCHARGE);
        Registry.register(Registries.ATTRIBUTE, CustomMobsSpawner.id("zenith_crit_chance"), ZENITH_CRIT_CHANCE);
        Registry.register(Registries.ATTRIBUTE, CustomMobsSpawner.id("zenith_crit_damage"), ZENITH_CRIT_DAMAGE);
    }
}
