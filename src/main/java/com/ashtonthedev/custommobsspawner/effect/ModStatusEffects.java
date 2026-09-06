package com.ashtonthedev.custommobsspawner.effect;

import com.ashtonthedev.custommobsspawner.CustomMobsSpawner;
import com.ashtonthedev.custommobsspawner.combat.ComboHandler;
import com.ashtonthedev.custommobsspawner.combat.ModAttributes;
import com.ashtonthedev.custommobsspawner.compat.ManaCapacitorInfluxHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.SpellInfo;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.internals.SpellRegistry;
import net.spell_engine.particle.ParticleHelper;
import net.spell_engine.utils.SoundHelper;
import net.spell_power.api.SpellPower;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ModStatusEffects {
    private static final Identifier ZENITH_CRIT_CHANCE = new Identifier("zenith_attributes", "crit_chance");
    private static final Identifier ZENITH_DODGE_CHANCE = new Identifier("zenith_attributes", "dodge_chance");
    private static final Identifier ZENITH_LIFE_STEAL = new Identifier("zenith_attributes", "life_steal");
    private static final Identifier ZENITH_DRAW_SPEED = new Identifier("zenith_attributes", "draw_speed");
    private static final Identifier ZENITH_ARROW_VELOCITY = new Identifier("zenith_attributes", "arrow_velocity");
    private static final Identifier ZENITH_ARMOR_SHRED = new Identifier("zenith_attributes", "armor_shred");
    private static final Identifier SPELL_POWER_CRIT_CHANCE = new Identifier("spell_power", "critical_chance");
    private static final Identifier SPELL_POWER_HASTE = new Identifier("spell_power", "haste");
    private static final Identifier FIRE_SPELL_POWER = new Identifier("spell_power", "fire");
    private static final Identifier ARCANE_SPELL_POWER = new Identifier("spell_power", "arcane");
    private static final Identifier FIRE_FUSE_POWER = new Identifier("more_rpg_classes", "fire_fuse_modifier");
    private static final Identifier ARCANE_FUSE_POWER = new Identifier("more_rpg_classes", "arcane_fuse_modifier");
    private static final Identifier MORE_RPG_CLASSES_RAGE_MODIFIER = new Identifier("more_rpg_classes", "rage_modifier");
    private static final Identifier MORE_RPG_CLASSES_DAMAGE_REFLECT_MODIFIER = new Identifier("more_rpg_classes", "damage_reflect_modifier");
    private static final Identifier MORE_RPG_CLASSES_DAMAGE_TAKEN = new Identifier("more_rpg_classes", "damage_taken");
    private static final Identifier COMBUSTION_SPELL = new Identifier("simplyskills", "fire_explosion");
    private static final RegistryKey<DamageType> COMBUSTION_TICK_DAMAGE_TYPE = RegistryKey.of(
            RegistryKeys.DAMAGE_TYPE,
            CustomMobsSpawner.id("combustion_tick")
    );
    private static final double COMBUSTION_FALLBACK_RADIUS = 6.0D;
    private static final double COMBUSTION_ATTACK_DAMAGE_SCALE = 0.1D;
    private static final double COMBUSTION_FIRE_SPELL_POWER_SCALE = 0.5D;
    private static final float COMBUSTION_TICK_DAMAGE_BASE = 2.0F;
    private static final float COMBUSTION_TICK_DAMAGE_PER_AMPLIFIER = 2.0F;
    private static final int COMBUSTION_TICK_DAMAGE_INTERVAL_TICKS = 10;
    private static final int COMBUSTION_EXPLOSION_FLAME_PARTICLES = 30;
    private static final int COMBUSTION_EXPLOSION_LAVA_PARTICLES = 8;
    private static final int COMBUSTION_EXPLOSION_SMOKE_PARTICLES = 12;
    private static final float COMBUSTION_CHAIN_CHANCE = 0.10F;
    private static final int COMBUSTION_CHAIN_DURATION_TICKS = 20;
    private static final int COMBUSTION_SMOKE_PARTICLES = 2;
    private static final Map<UUID, ItemStack> LAST_MAINHAND_STACKS = new ConcurrentHashMap<>();

    public static final TagKey<StatusEffect> WEAPON_COMBO_STATUS_EFFECTS = TagKey.of(
            RegistryKeys.STATUS_EFFECT,
            CustomMobsSpawner.id("weapon_combo_status_effects")
    );
    public static final StatusEffect VULNERABLE = new VulnerableStatusEffect();
    public static final StatusEffect TURNING_SLOW = new TurningSlowStatusEffect();
    public static final StatusEffect FLOW = new FlowStatusEffect()
            .addAttributeModifier(
                    EntityAttributes.GENERIC_ATTACK_SPEED,
                    "3e26ec1a-0405-4f08-9115-544bcb8b89be",
                    0.02D,
                    EntityAttributeModifier.Operation.MULTIPLY_TOTAL
            )
            .addAttributeModifier(
                    ModAttributes.POSTURE_DAMAGE,
                    "d7ba78e9-1816-4c88-a108-44b12cb9c230",
                    2.0D,
                    EntityAttributeModifier.Operation.ADDITION
            )
            .addAttributeModifier(
                    ModAttributes.POSTURE_DAMAGE_MULTIPLIER,
                    "a2e680ea-bad8-4384-afb3-626ec8c655c4",
                    0.02D,
                    EntityAttributeModifier.Operation.MULTIPLY_TOTAL
            );
    public static final StatusEffect MOMENTUM = new MomentumStatusEffect()
            .addAttributeModifier(
                    ModAttributes.ATTACK_RANGE,
                    "54cf4e18-73a0-49d7-b50e-b49935d3b985",
                    0.2D,
                    EntityAttributeModifier.Operation.ADDITION
            )
            .addAttributeModifier(
                    ModAttributes.POSTURE_DAMAGE,
                    "1b173752-f733-4fdd-8c90-7c264a44f723",
                    5.0D,
                    EntityAttributeModifier.Operation.ADDITION
            )
            .addAttributeModifier(
                    ModAttributes.POSTURE_DAMAGE_MULTIPLIER,
                    "de521a47-44b7-491a-a6dc-0a19f4f1f60a",
                    0.05D,
                    EntityAttributeModifier.Operation.MULTIPLY_TOTAL
            );
    public static final StatusEffect PRECISION = new PrecisionStatusEffect()
            .addAttributeModifier(
                    EntityAttributes.GENERIC_ATTACK_SPEED,
                    "a815d9e8-40f0-48d2-a021-377961a21467",
                    0.02D,
                    EntityAttributeModifier.Operation.MULTIPLY_TOTAL
            )
            .addAttributeModifier(
                    EntityAttributes.GENERIC_MOVEMENT_SPEED,
                    "79884b66-b44a-44ba-9bff-227e8531f5f5",
                    0.03D,
                    EntityAttributeModifier.Operation.MULTIPLY_TOTAL
            );
    public static final StatusEffect BLOODLUST = new BloodlustStatusEffect()
            .addAttributeModifier(
                    EntityAttributes.GENERIC_ATTACK_SPEED,
                    "dcdaff2d-9792-4bc4-a98f-f4f0788452d8",
                    0.03D,
                    EntityAttributeModifier.Operation.MULTIPLY_TOTAL
            )
            .addAttributeModifier(
                    ModAttributes.POSTURE_DAMAGE_RESISTANCE,
                    "3af61df8-ff2a-4a77-a207-d6c8fe70f60c",
                    0.05D,
                    EntityAttributeModifier.Operation.MULTIPLY_TOTAL
            );
    public static final StatusEffect KINDLE = new KindleStatusEffect();
    public static final StatusEffect MANA_WARD = new ManaWardStatusEffect();
    public static final StatusEffect MANA_INFLUX = new ManaInfluxStatusEffect();
    public static final StatusEffect COMBUSTION = new CombustionStatusEffect();
    public static final StatusEffect STATIC = new StaticStatusEffect();
    public static final StatusEffect VERDANT_GROWTH = new VerdantGrowthStatusEffect();
    public static final StatusEffect RESOLVE = new ResolveStatusEffect()
            .addAttributeModifier(
                    EntityAttributes.GENERIC_ARMOR,
                    "f6059f13-2615-49a1-8935-134d5abfb2a9",
                    4.0D,
                    EntityAttributeModifier.Operation.ADDITION
            )
            .addAttributeModifier(
                    ModAttributes.POSTURE_DAMAGE_RESISTANCE,
                    "60160641-6a9d-4d29-bdc2-0dc5495fef3c",
                    0.05D,
                    EntityAttributeModifier.Operation.ADDITION
            );
    public static final StatusEffect SOULCHILL = new SoulchillStatusEffect()
            .addAttributeModifier(
                    EntityAttributes.GENERIC_MOVEMENT_SPEED,
                    "1f373a1f-2429-481a-9d73-0552ec6414c4",
                    -0.04D,
                    EntityAttributeModifier.Operation.MULTIPLY_TOTAL
            );
    public static final StatusEffect SHADOWFLOW = new ShadowflowStatusEffect();
    public static final StatusEffect FOCUS = new FocusStatusEffect();
    public static final StatusEffect SHADOWPHASE = new ShadowphaseStatusEffect()
            .addAttributeModifier(
                    EntityAttributes.GENERIC_MOVEMENT_SPEED,
                    "d28380a1-d099-4297-b3fa-b44dba3ac48f",
                    0.15D,
                    EntityAttributeModifier.Operation.MULTIPLY_TOTAL
            );
    public static final StatusEffect ELUSION = new ElusionStatusEffect();

    private ModStatusEffects() {
    }

    public static void register() {
        addOptionalAttributeModifier(
                PRECISION,
                ZENITH_CRIT_CHANCE,
                "1521c532-54f2-45e6-a237-66b8ec9da837",
                0.025D,
                EntityAttributeModifier.Operation.ADDITION
        );
        addOptionalAttributeModifier(
                STATIC,
                ZENITH_CRIT_CHANCE,
                "3cbdbd8a-ef47-4577-bdd7-32a682f435b9",
                0.04D,
                EntityAttributeModifier.Operation.ADDITION
        );
        addOptionalAttributeModifier(
                STATIC,
                SPELL_POWER_CRIT_CHANCE,
                "e3342949-0948-4f0e-89dc-7bad1d3cb342",
                0.04D,
                EntityAttributeModifier.Operation.ADDITION
        );
        addOptionalAttributeModifier(
                VERDANT_GROWTH,
                MORE_RPG_CLASSES_DAMAGE_REFLECT_MODIFIER,
                "fb9b6d48-fbd7-4c92-a246-fdece57b3b94",
                0.06D,
                EntityAttributeModifier.Operation.MULTIPLY_BASE
        );
        addOptionalAttributeModifier(
                VERDANT_GROWTH,
                ZENITH_LIFE_STEAL,
                "55d62a30-d38e-4d70-9b1c-1fb923dcdd78",
                0.03D,
                EntityAttributeModifier.Operation.MULTIPLY_BASE
        );
        addOptionalAttributeModifier(
                SOULCHILL,
                MORE_RPG_CLASSES_DAMAGE_TAKEN,
                "9d75f68a-f0fa-4dda-8f99-ff59d705a97c",
                0.02D,
                EntityAttributeModifier.Operation.MULTIPLY_BASE
        );
        addOptionalAttributeModifier(
                SHADOWFLOW,
                ZENITH_CRIT_CHANCE,
                "9e3499f7-78f1-4f6b-9400-9524609010b9",
                0.025D,
                EntityAttributeModifier.Operation.ADDITION
        );
        addOptionalAttributeModifier(
                SHADOWFLOW,
                ZENITH_DODGE_CHANCE,
                "f3212d67-4826-4ecf-98ad-f17157475a05",
                0.02D,
                EntityAttributeModifier.Operation.ADDITION
        );
        addOptionalAttributeModifier(
                SHADOWPHASE,
                ZENITH_DODGE_CHANCE,
                "9e1c92f8-4584-4ec9-a4b4-01a0c43a2d9f",
                0.15D,
                EntityAttributeModifier.Operation.ADDITION
        );
        ELUSION.addAttributeModifier(
                ModAttributes.ZENITH_CRIT_CHANCE,
                "a379ad0d-c6cf-4c10-8821-70984e48c116",
                0.4D,
                EntityAttributeModifier.Operation.ADDITION
        );
        ELUSION.addAttributeModifier(
                ModAttributes.ZENITH_CRIT_DAMAGE,
                "5d2a8903-9025-4e0f-a2f4-73b06bcb67e3",
                1.25D,
                EntityAttributeModifier.Operation.ADDITION
        );
        addOptionalAttributeModifier(
                ELUSION,
                ZENITH_DODGE_CHANCE,
                "0812359a-c899-4363-bef1-d4df801e8e17",
                0.2D,
                EntityAttributeModifier.Operation.ADDITION
        );
        addOptionalAttributeModifier(
                FOCUS,
                ZENITH_DRAW_SPEED,
                "497cb316-62c4-4b83-bbf1-51be6277e136",
                0.04D,
                EntityAttributeModifier.Operation.MULTIPLY_BASE
        );
        addOptionalAttributeModifier(
                FOCUS,
                ZENITH_ARROW_VELOCITY,
                "55689b35-c732-4655-9bb0-bf8772d79b3a",
                0.04D,
                EntityAttributeModifier.Operation.MULTIPLY_BASE
        );
        addOptionalAttributeModifier(
                FOCUS,
                ZENITH_ARMOR_SHRED,
                "0f1b2d45-4eb4-45ac-9fd8-c42385df2b49",
                0.15D,
                EntityAttributeModifier.Operation.ADDITION
        );
        addOptionalAttributeModifier(
                KINDLE,
                FIRE_SPELL_POWER,
                "7a817a27-a50c-4ef5-8c66-cf2c45a2b0d9",
                0.04D,
                EntityAttributeModifier.Operation.MULTIPLY_TOTAL
        );
        addOptionalAttributeModifier(
                KINDLE,
                ARCANE_SPELL_POWER,
                "fd4be08e-de65-4c91-a4b0-15f1c167c32e",
                0.04D,
                EntityAttributeModifier.Operation.MULTIPLY_TOTAL
        );
        addOptionalAttributeModifier(
                BLOODLUST,
                SPELL_POWER_HASTE,
                "a1139e55-0e33-4dfd-8e4b-1c3eeb8ffba8",
                0.05D,
                EntityAttributeModifier.Operation.MULTIPLY_TOTAL
        );
        addOptionalAttributeModifier(
                BLOODLUST,
                MORE_RPG_CLASSES_RAGE_MODIFIER,
                "14409571-2862-4734-b621-1633495f8ef5",
                0.04D,
                EntityAttributeModifier.Operation.MULTIPLY_TOTAL
        );
        addOptionalAttributeModifierFromField(
                MANA_WARD,
                "com.extraspellattributes.ReabsorptionInit",
                "WARDING",
                "50fc0fed-16cb-41c2-9b7b-6f8366b8f752",
                4.0D,
                EntityAttributeModifier.Operation.ADDITION
        );
        addOptionalAttributeModifierFromField(
                MANA_WARD,
                "com.cleannrooster.rpgmana.Rpgmana",
                "MANAREGEN",
                "726a5f5b-fbe0-4366-aae6-8d532437643e",
                0.5D,
                EntityAttributeModifier.Operation.ADDITION
        );
        addOptionalAttributeModifierFromField(
                MANA_WARD,
                "com.cleannrooster.rpgmana.Rpgmana",
                "MANACOST",
                "ed99d004-60ad-4e59-a52b-b831022f20cd",
                -0.05D,
                EntityAttributeModifier.Operation.MULTIPLY_TOTAL
        );
        addOptionalAttributeModifierFromField(
                BLOODLUST,
                "com.cleannrooster.rpgmana.Rpgmana",
                "MANACOST",
                "ed99d004-60ad-4e59-a52b-b831022f20cd",
                -0.05D,
                EntityAttributeModifier.Operation.MULTIPLY_TOTAL
        );
        addOptionalAttributeModifierFromField(
                MANA_INFLUX,
                "com.cleannrooster.rpgmana.Rpgmana",
                "MANAREGEN",
                "a449588e-cbde-4f70-a9a2-f5cb2bfc7508",
                10.0D,
                EntityAttributeModifier.Operation.ADDITION
        );

        Registry.register(Registries.STATUS_EFFECT, CustomMobsSpawner.id("vulnerable"), VULNERABLE);
        Registry.register(Registries.STATUS_EFFECT, CustomMobsSpawner.id("turning_slow"), TURNING_SLOW);
        Registry.register(Registries.STATUS_EFFECT, CustomMobsSpawner.id("flow"), FLOW);
        Registry.register(Registries.STATUS_EFFECT, CustomMobsSpawner.id("momentum"), MOMENTUM);
        Registry.register(Registries.STATUS_EFFECT, CustomMobsSpawner.id("precision"), PRECISION);
        Registry.register(Registries.STATUS_EFFECT, CustomMobsSpawner.id("bloodlust"), BLOODLUST);
        Registry.register(Registries.STATUS_EFFECT, CustomMobsSpawner.id("kindle"), KINDLE);
        Registry.register(Registries.STATUS_EFFECT, CustomMobsSpawner.id("mana_ward"), MANA_WARD);
        Registry.register(Registries.STATUS_EFFECT, CustomMobsSpawner.id("mana_influx"), MANA_INFLUX);
        Registry.register(Registries.STATUS_EFFECT, CustomMobsSpawner.id("combustion"), COMBUSTION);
        Registry.register(Registries.STATUS_EFFECT, CustomMobsSpawner.id("static"), STATIC);
        Registry.register(Registries.STATUS_EFFECT, CustomMobsSpawner.id("verdant_growth"), VERDANT_GROWTH);
        Registry.register(Registries.STATUS_EFFECT, CustomMobsSpawner.id("resolve"), RESOLVE);
        Registry.register(Registries.STATUS_EFFECT, CustomMobsSpawner.id("soulchill"), SOULCHILL);
        Registry.register(Registries.STATUS_EFFECT, CustomMobsSpawner.id("shadowflow"), SHADOWFLOW);
        Registry.register(Registries.STATUS_EFFECT, CustomMobsSpawner.id("focus"), FOCUS);
        Registry.register(Registries.STATUS_EFFECT, CustomMobsSpawner.id("shadowphase"), SHADOWPHASE);
        Registry.register(Registries.STATUS_EFFECT, CustomMobsSpawner.id("elusion"), ELUSION);
    }

    public static void tickServer(MinecraftServer server) {
        LAST_MAINHAND_STACKS.keySet().removeIf(uuid -> server.getPlayerManager().getPlayer(uuid) == null);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            tickWeaponComboStatusEffects(player);
            tickPrecision(player);
            ManaCapacitorInfluxHandler.tick(player);
        }
    }

    private static void tickWeaponComboStatusEffects(ServerPlayerEntity player) {
        ItemStack current = player.getMainHandStack();
        ItemStack previous = LAST_MAINHAND_STACKS.put(player.getUuid(), current.copy());
        if (previous != null && !mainhandStacksMatch(previous, current)) {
            clearWeaponComboStatusEffects(player);
            ComboHandler.reset(player);
        }
    }

    private static boolean mainhandStacksMatch(ItemStack previous, ItemStack current) {
        if (previous.isEmpty() && current.isEmpty()) {
            return true;
        }
        return previous.isOf(current.getItem())
                && previous.getCount() == current.getCount();
    }

    public static void clearWeaponComboStatusEffects(LivingEntity entity) {
        if (entity.getWorld().isClient()) {
            return;
        }

        List<StatusEffect> effectsToClear = new ArrayList<>();
        for (StatusEffectInstance instance : entity.getStatusEffects()) {
            StatusEffect effect = instance.getEffectType();
            if (Registries.STATUS_EFFECT.getEntry(effect).isIn(WEAPON_COMBO_STATUS_EFFECTS)) {
                effectsToClear.add(effect);
            }
        }
        for (StatusEffect effect : effectsToClear) {
            entity.removeStatusEffect(effect);
        }
    }

    private static void tickPrecision(LivingEntity entity) {
        StatusEffectInstance precision = entity.getStatusEffect(PRECISION);
        if (precision == null || precision.getDuration() > 1 || precision.getAmplifier() <= 0) {
            return;
        }

        entity.removeStatusEffect(PRECISION);
        entity.addStatusEffect(new StatusEffectInstance(
                PRECISION,
                20,
                precision.getAmplifier() - 1,
                precision.isAmbient(),
                precision.shouldShowParticles(),
                precision.shouldShowIcon()
        ));
    }

    private static void addOptionalAttributeModifier(
            StatusEffect effect,
            Identifier id,
            String uuid,
            double amount,
            EntityAttributeModifier.Operation operation
    ) {
        Registries.ATTRIBUTE.getOrEmpty(id).ifPresent(attribute ->
                effect.addAttributeModifier(attribute, uuid, amount, operation)
        );
    }

    private static void addOptionalAttributeModifierFromField(
            StatusEffect effect,
            String className,
            String fieldName,
            String uuid,
            double amount,
            EntityAttributeModifier.Operation operation
    ) {
        try {
            Object value = Class.forName(className).getField(fieldName).get(null);
            if (value instanceof EntityAttribute attribute) {
                effect.addAttributeModifier(attribute, uuid, amount, operation);
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void triggerCombustion(LivingEntity origin, int amplifier) {
        if (!(origin.getWorld() instanceof ServerWorld world)) {
            return;
        }

        Spell registrySpell = SpellRegistry.getSpell(COMBUSTION_SPELL);
        if (registrySpell == null) {
            return;
        }

        Spell spell = registrySpell;
        SpellInfo spellInfo = new SpellInfo(spell, COMBUSTION_SPELL);
        Vec3d position = origin.getPos();
        SpellHelper.ImpactContext impactContext = new SpellHelper.ImpactContext(
                1.0F,
                0.0F,
                position,
                combustionSpellPower(origin, spell, amplifier),
                SpellHelper.impactTargetingMode(spell)
        );

        if (spell.release != null && spell.release.particles != null) {
            ParticleHelper.sendBatches(origin, spell.release.particles);
        }
        if (spell.release != null && spell.release.sound != null) {
            SoundHelper.playSound(world, origin, spell.release.sound);
        }
        playCombustionExplosionFeedback(world, origin);

        double radius = spell.range > 0.0F ? spell.range : COMBUSTION_FALLBACK_RADIUS;
        Box box = origin.getBoundingBox().expand(radius);
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            boolean damaged = SpellHelper.performImpacts(world, origin, target, origin, spellInfo, impactContext, false);
            if (damaged && target != origin && target instanceof MobEntity && world.random.nextFloat() < COMBUSTION_CHAIN_CHANCE) {
                target.addStatusEffect(new StatusEffectInstance(COMBUSTION, COMBUSTION_CHAIN_DURATION_TICKS, 0));
            }
        }
    }

    private static SpellPower.Result combustionSpellPower(LivingEntity origin, Spell spell, int amplifier) {
        SpellPower.Result existingPower = SpellPower.getSpellPower(spell.school, origin);
        double damage = combustionImpactDamage(origin, existingPower);
        double coefficient = firstDamageCoefficient(spell);
        double baseValue = coefficient <= 0.0D ? damage : damage / coefficient;
        return new SpellPower.Result(existingPower.school(), Math.max(0.0D, baseValue), existingPower.criticalChance(), existingPower.criticalDamage());
    }

    private static double combustionImpactDamage(LivingEntity origin, SpellPower.Result firePower) {
        return origin.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE) * COMBUSTION_ATTACK_DAMAGE_SCALE
                + firePower.baseValue() * COMBUSTION_FIRE_SPELL_POWER_SCALE;
    }

    private static double firstDamageCoefficient(Spell spell) {
        if (spell.impact == null) {
            return 0.0D;
        }
        for (Spell.Impact impact : spell.impact) {
            if (impact == null || impact.action == null || impact.action.damage == null) {
                continue;
            }
            if (impact.action.damage.spell_power_coefficient > 0.0F) {
                return impact.action.damage.spell_power_coefficient;
            }
        }
        return 0.0D;
    }

    private static void applyCombustionTickDamage(LivingEntity entity, int amplifier) {
        if (!(entity.getWorld() instanceof ServerWorld world) || !entity.isAlive()) {
            return;
        }

        StatusEffectInstance instance = entity.getStatusEffect(COMBUSTION);
        if (instance == null || instance.getDuration() % COMBUSTION_TICK_DAMAGE_INTERVAL_TICKS != 0) {
            return;
        }

        float damage = COMBUSTION_TICK_DAMAGE_BASE + COMBUSTION_TICK_DAMAGE_PER_AMPLIFIER * Math.max(0, amplifier);
        if (damage <= 0.0F) {
            return;
        }

        entity.timeUntilRegen = 0;
        entity.damage(world.getDamageSources().create(COMBUSTION_TICK_DAMAGE_TYPE), damage);
    }

    private static void playCombustionExplosionFeedback(ServerWorld world, LivingEntity origin) {
        double x = origin.getX();
        double y = origin.getY() + origin.getHeight() * 0.5D;
        double z = origin.getZ();
        world.playSound(
                null,
                x,
                y,
                z,
                SoundEvents.ITEM_FIRECHARGE_USE,
                SoundCategory.PLAYERS,
                1.0F,
                0.75F
        );
        world.spawnParticles(ParticleTypes.FLAME, x, y, z, COMBUSTION_EXPLOSION_FLAME_PARTICLES, 0.28D, 0.22D, 0.28D, 0.5D);
        world.spawnParticles(ParticleTypes.LAVA, x, y, z, COMBUSTION_EXPLOSION_LAVA_PARTICLES, 0.18D, 0.12D, 0.18D, 0.08D);
        world.spawnParticles(ParticleTypes.SMOKE, x, y, z, COMBUSTION_EXPLOSION_SMOKE_PARTICLES, 0.32D, 0.18D, 0.32D, 0.04D);
    }

    private static void spawnCombustionSmoke(LivingEntity entity) {
        if (!(entity.getWorld() instanceof ServerWorld world)) {
            return;
        }

        double chestY = entity.getY() + entity.getHeight() * 0.58D;
        double spread = Math.max(0.15D, entity.getWidth() * 0.35D);
        world.spawnParticles(
                ParticleTypes.SMOKE,
                entity.getX(),
                chestY,
                entity.getZ(),
                COMBUSTION_SMOKE_PARTICLES,
                spread,
                entity.getHeight() * 0.08D,
                spread,
                0.01D
        );
    }

    private static final class VulnerableStatusEffect extends StatusEffect {
        private VulnerableStatusEffect() {
            super(StatusEffectCategory.HARMFUL, 0x6B1010);
        }
    }

    private static final class TurningSlowStatusEffect extends StatusEffect {
        private TurningSlowStatusEffect() {
            super(StatusEffectCategory.HARMFUL, 0x28486B);
        }
    }

    private static final class FlowStatusEffect extends StatusEffect {
        private FlowStatusEffect() {
            super(StatusEffectCategory.BENEFICIAL, 0x54D6C3);
        }
    }

    private static final class MomentumStatusEffect extends StatusEffect {
        private MomentumStatusEffect() {
            super(StatusEffectCategory.BENEFICIAL, 0xD0A040);
        }
    }

    private static final class PrecisionStatusEffect extends StatusEffect {
        private PrecisionStatusEffect() {
            super(StatusEffectCategory.BENEFICIAL, 0xF0E4A0);
        }
    }

    private static final class BloodlustStatusEffect extends StatusEffect {
        private BloodlustStatusEffect() {
            super(StatusEffectCategory.BENEFICIAL, 0xFF1010);
        }
    }

    private static final class KindleStatusEffect extends StatusEffect {
        private KindleStatusEffect() {
            super(StatusEffectCategory.BENEFICIAL, 0xFF7A24);
        }
    }

    private static final class ManaWardStatusEffect extends StatusEffect {
        private ManaWardStatusEffect() {
            super(StatusEffectCategory.BENEFICIAL, 0x2F8CFF);
        }
    }

    private static final class ManaInfluxStatusEffect extends StatusEffect {
        private ManaInfluxStatusEffect() {
            super(StatusEffectCategory.BENEFICIAL, 0x4DEBFF);
        }
    }

    private static final class CombustionStatusEffect extends StatusEffect {
        private CombustionStatusEffect() {
            super(StatusEffectCategory.HARMFUL, 0xFF4A18);
        }

        @Override
        public boolean canApplyUpdateEffect(int duration, int amplifier) {
            return true;
        }

        @Override
        public void applyUpdateEffect(LivingEntity entity, int amplifier) {
            spawnCombustionSmoke(entity);
            applyCombustionTickDamage(entity, amplifier);
            StatusEffectInstance instance = entity.getStatusEffect(COMBUSTION);
            if (instance != null && instance.getDuration() <= 1) {
                triggerCombustion(entity, amplifier);
            }
        }
    }

    private static final class StaticStatusEffect extends StatusEffect {
        private StaticStatusEffect() {
            super(StatusEffectCategory.BENEFICIAL, 0xF5D76E);
        }
    }

    private static final class VerdantGrowthStatusEffect extends StatusEffect {
        private VerdantGrowthStatusEffect() {
            super(StatusEffectCategory.BENEFICIAL, 0x4FC36B);
        }
    }

    private static final class ResolveStatusEffect extends StatusEffect {
        private ResolveStatusEffect() {
            super(StatusEffectCategory.BENEFICIAL, 0x8DA6B8);
        }
    }

    private static final class SoulchillStatusEffect extends StatusEffect {
        private SoulchillStatusEffect() {
            super(StatusEffectCategory.HARMFUL, 0x67A7FF);
        }
    }

    private static final class ShadowflowStatusEffect extends StatusEffect {
        private ShadowflowStatusEffect() {
            super(StatusEffectCategory.BENEFICIAL, 0x6744B8);
        }
    }

    private static final class FocusStatusEffect extends StatusEffect {
        private FocusStatusEffect() {
            super(StatusEffectCategory.BENEFICIAL, 0xE7B84B);
        }
    }

    private static final class ShadowphaseStatusEffect extends StatusEffect {
        private ShadowphaseStatusEffect() {
            super(StatusEffectCategory.BENEFICIAL, 0x2B1F4F);
        }
    }

    private static final class ElusionStatusEffect extends StatusEffect {
        private ElusionStatusEffect() {
            super(StatusEffectCategory.BENEFICIAL, 0x87F2D7);
        }
    }
}
