package com.ashtonthedev.custommobsspawner.combat;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.ashtonthedev.custommobsspawner.CustomMobsSpawner;
import com.ashtonthedev.custommobsspawner.config.CustomMobsSpawnerConfig;
import com.ashtonthedev.custommobsspawner.effect.ModStatusEffects;
import com.ashtonthedev.custommobsspawner.mixin.LivingEntityAccessor;
import com.ashtonthedev.custommobsspawner.skill.CustomSkillRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShieldItem;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.network.PacketByteBuf;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerParryHandler {
    public static final Identifier POSTURE_SYNC_PACKET = CustomMobsSpawner.id("posture_sync");
    public static final Identifier ATTACK_COOLDOWN_SYNC_PACKET = CustomMobsSpawner.id("attack_cooldown_sync");
    private static final int ENTITY_POSTURE_SYNC_REFRESH_TICKS = 20;
    private static final double TRACKED_PLAYER_POSTURE_SYNC_DISTANCE_SQUARED = 64.0D * 64.0D;
    private static final float STAGGER_ATTACK_COOLDOWN_PROGRESS = 0.0F;
    private static final float BLOCK_ATTACK_COOLDOWN_PROGRESS = 0.6F;
    private static final float PARRY_ATTACK_COOLDOWN_PROGRESS = 0.95F;
    private static final double TAKEN_DAMAGE_POSTURE_SCALE = 2.0D;
    private static final Identifier MORE_RPG_CLASSES_STUN = new Identifier("more_rpg_classes", "stun");
    private static final UUID PARRY_WINDOW_MODIFIER_ID = UUID.fromString("69e153ff-66c3-4c3a-9261-9cf5f198f6a8");
    private static final UUID POSTURE_HEALTH_MODIFIER_ID = UUID.fromString("129d2419-754b-4ae6-b0cd-f81ac13891f0");
    private static final UUID POSTURE_DAMAGE_MODIFIER_ID = UUID.fromString("8c39fe09-3a6d-41d9-b497-e3f68f070bb3");
    private static final UUID STAGGER_DAMAGE_MULTIPLIER_MODIFIER_ID = UUID.fromString("fedbf2f0-cc18-4433-9946-56451bbb9573");
    private static final UUID PLAYER_BASE_POSTURE_REGEN_MODIFIER_ID = UUID.fromString("69d1d2ce-168c-40ef-8825-9eab0fcd62cb");
    private static final double EPSILON = 0.000001D;
    private static final Set<UUID> SUCCESSFUL_BLOCKS = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> STAGGERED_BLOCKS = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> ARMED_WEAPON_BLOCKS = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> ACTIVE_PARRY_BLOCKS = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<UUID, Integer> PARRY_COUNTER_WINDOWS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Integer> STAGGER_WINDOWS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Integer> STAGGER_COOLDOWNS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Integer> COMBAT_WINDOWS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Integer> POSTURE_INVULNERABILITY_WINDOWS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Integer> POSTURE_REGEN_DELAYS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Double> POSTURE_DAMAGE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Double> POSTURE_HEALTH = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, PostureSyncState> LAST_SELF_POSTURE_SYNC = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, EntityPostureSyncState> LAST_ENTITY_POSTURE_SYNC = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> LAST_ENTITY_POSTURE_SYNC_TIMES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Double> LAST_CONFIGURED_PVP_TAKEN_POSTURE_DAMAGE_BASES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> LAST_TAKEN_POSTURE_DAMAGE_TICKS = new ConcurrentHashMap<>();

    private PlayerParryHandler() {
    }

    public static boolean tryParry(PlayerEntity player, DamageSource source) {
        if (player.getWorld().isClient() || isStaggered(player) || !isBlockingWithParryItem(player) || !isInsideParryWindow(player)) {
            return false;
        }

        LivingEntity attacker = livingDamageSource(source);
        CustomMobsSpawnerConfig.ConfigData config = CustomMobsSpawnerConfig.get();
        markCombat(player, config);
        if (attacker instanceof PlayerEntity playerAttacker) {
            markCombat(playerAttacker, config);
        }
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 80, 0));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 60, 0));
        PARRY_COUNTER_WINDOWS.put(player.getUuid(), config.playerParryCounterWindowTicks);
        POSTURE_INVULNERABILITY_WINDOWS.put(player.getUuid(), config.playerParryPostureInvulnerabilityTicks);
        restorePosture(player, config.playerParryPostureRestore);
        setAndSyncAttackCooldownProgress(player, PARRY_ATTACK_COOLDOWN_PROGRESS);
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.HOSTILE, 0.75F, 1.5F);
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_ANVIL_HIT, SoundCategory.HOSTILE, 0.20F, 1.8F);
        player.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8F, 1.8F);
        player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.18F, 2.0F);

        if (player.getWorld() instanceof ServerWorld world) {
            world.spawnParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1.0D, player.getZ(), 24, 0.45D, 0.45D, 0.45D, 0.08D);
            world.spawnParticles(ParticleTypes.ENCHANTED_HIT, player.getX(), player.getY() + 1.0D, player.getZ(), 18, 0.35D, 0.35D, 0.35D, 0.08D);
        }

        if (attacker instanceof PlayerEntity playerAttacker) {
            applyParriedPlayerPostureDamage(playerAttacker);
            playerAttacker.takeKnockback(0.35D, player.getX() - playerAttacker.getX(), player.getZ() - playerAttacker.getZ());
        } else if (attacker != null) {
            boolean staggered = MobPostureHandler.applyParry(attacker);
            if (!staggered) {
                attacker.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, config.playerStaggerTicks, 2));
            }
            attacker.takeKnockback(0.35D, player.getX() - attacker.getX(), player.getZ() - attacker.getZ());
        }

        return true;
    }

    private static void applyParriedPlayerPostureDamage(PlayerEntity player) {
        if (player.getWorld().isClient() || isStaggered(player)) {
            return;
        }

        UUID uuid = player.getUuid();
        CustomMobsSpawnerConfig.ConfigData config = CustomMobsSpawnerConfig.get();
        markCombat(player, config);
        double postureHealth = getPlayerPostureHealth(player, getEquippedPostureItem(player));
        POSTURE_HEALTH.put(uuid, postureHealth);
        POSTURE_REGEN_DELAYS.put(uuid, config.playerPostureRegenDelayTicks);
        double totalPostureDamage = applyPvpPostureDamage(player, vulnerablePostureDamage(player, config.playerParryPostureDamage), config);
        if (totalPostureDamage >= postureHealth) {
            if (!isOnStaggerCooldown(player)) {
                staggerFromDamage(player, postureHealth);
            } else {
                POSTURE_DAMAGE.put(uuid, postureHealth);
            }
        }
    }

    public static void applySkillPostureDamage(PlayerEntity player, double amount) {
        applySkillPostureDamage(player, amount, false);
    }

    public static void applySkillPostureDamage(PlayerEntity player, double amount, boolean bypassPostureInvulnerability) {
        applySkillPostureDamage(player, amount, bypassPostureInvulnerability, false);
    }

    public static void applySkillPostureDamage(PlayerEntity player, double amount, boolean bypassPostureInvulnerability, boolean blockable) {
        if (player.getWorld().isClient() || amount <= 0.0D || isStaggered(player)) {
            return;
        }
        markCombat(player);
        if (blockable && tryAbsorbSkillPostureDamage(player, amount)) {
            return;
        }
        if (!bypassPostureInvulnerability && isPostureInvulnerable(player)) {
            return;
        }

        UUID uuid = player.getUuid();
        double postureHealth = getPlayerPostureHealth(player, getEquippedPostureItem(player));
        POSTURE_HEALTH.put(uuid, postureHealth);
        POSTURE_REGEN_DELAYS.put(uuid, CustomMobsSpawnerConfig.get().playerPostureRegenDelayTicks);
        double totalPostureDamage = POSTURE_DAMAGE.merge(uuid, vulnerablePostureDamage(player, amount), Double::sum);
        if (totalPostureDamage >= postureHealth) {
            if (!isOnStaggerCooldown(player)) {
                staggerFromDamage(player, postureHealth);
            } else {
                POSTURE_DAMAGE.put(uuid, postureHealth);
            }
        }
    }

    public static void restoreSkillPosture(PlayerEntity player, double amount) {
        if (player.getWorld().isClient() || amount <= 0.0D || isStaggered(player)) {
            return;
        }
        restorePosture(player, amount);
    }

    private static boolean tryAbsorbSkillPostureDamage(PlayerEntity player, double amount) {
        if (!isBlockingWithParryItem(player) || isInsideParryWindow(player) || isPostureInvulnerable(player)) {
            return false;
        }

        SUCCESSFUL_BLOCKS.add(player.getUuid());
        UUID uuid = player.getUuid();
        ItemStack activeItem = player.getActiveItem();
        double postureHealth = getPlayerPostureHealth(player, activeItem);
        POSTURE_HEALTH.put(uuid, postureHealth);
        CustomMobsSpawnerConfig.ConfigData config = CustomMobsSpawnerConfig.get();
        markCombat(player, config);
        POSTURE_REGEN_DELAYS.put(uuid, config.playerPostureRegenDelayTicks);
        double blockedPostureDamage = Math.max(0.0D, amount * config.playerBlockedSkillPostureMultiplier);
        double postureDamage = POSTURE_DAMAGE.merge(uuid, vulnerablePostureDamage(player, blockedPostureDamage), Double::sum);
        playBlockImpactSound(player, activeItem);
        damageBlockedItem(player, (float) amount);
        if (postureDamage >= postureHealth) {
            if (!isOnStaggerCooldown(player)) {
                stagger(player);
                return true;
            } else {
                POSTURE_DAMAGE.put(uuid, postureHealth);
            }
        }
        setAndSyncAttackCooldownProgress(player, BLOCK_ATTACK_COOLDOWN_PROGRESS);
        return true;
    }

    private static void restorePosture(PlayerEntity player, double amount) {
        UUID uuid = player.getUuid();
        double postureDamage = POSTURE_DAMAGE.getOrDefault(uuid, 0.0D);
        if (postureDamage <= 0.0D) {
            return;
        }

        double remainingDamage = Math.max(0.0D, postureDamage - amount);
        if (remainingDamage <= 0.0D) {
            POSTURE_DAMAGE.remove(uuid);
            POSTURE_HEALTH.remove(uuid);
        } else {
            POSTURE_DAMAGE.put(uuid, remainingDamage);
            POSTURE_HEALTH.put(uuid, getPlayerPostureHealth(player, getEquippedPostureItem(player)));
        }
    }

    public static void recordLivingEntityHit(PlayerEntity player, DamageSource source) {
        if (!player.getWorld().isClient() && livingDamageSource(source) != null) {
            CustomMobsSpawnerConfig.ConfigData config = CustomMobsSpawnerConfig.get();
            POSTURE_REGEN_DELAYS.put(player.getUuid(), config.playerPostureRegenDelayTicks);
            markCombat(player, config);
            if (livingDamageSource(source) instanceof PlayerEntity attacker) {
                markCombat(attacker, config);
            }
        }
    }

    public static void markCombat(PlayerEntity player) {
        if (!player.getWorld().isClient()) {
            markCombat(player, CustomMobsSpawnerConfig.get());
        }
    }

    private static void markCombat(PlayerEntity player, CustomMobsSpawnerConfig.ConfigData config) {
        COMBAT_WINDOWS.put(player.getUuid(), Math.max(1, config.playerPostureRegenDelayTicks));
    }

    public static void applyTakenDamagePostureLoss(PlayerEntity player, DamageSource source, float amount) {
        LivingEntity attacker = livingDamageSource(source);
        if (player.getWorld().isClient() || amount <= 0.0F || isPostureInvulnerable(player) || isStaggered(player) || isOnStaggerCooldown(player) || attacker == null) {
            return;
        }

        UUID uuid = player.getUuid();
        double postureHealth = getPlayerPostureHealth(player, getEquippedPostureItem(player));
        POSTURE_HEALTH.put(uuid, postureHealth);
        CustomMobsSpawnerConfig.ConfigData config = CustomMobsSpawnerConfig.get();
        markCombat(player, config);
        if (attacker instanceof PlayerEntity playerAttacker) {
            markCombat(playerAttacker, config);
        }
        if (isWithinTakenPostureGrace(player, config)) {
            return;
        }
        double scaledDamage = amount * TAKEN_DAMAGE_POSTURE_SCALE * config.playerTakenDamagePostureMultiplier;
        double postureDamage = vulnerablePostureDamage(player, Math.min(config.playerMaxTakenDamagePostureDamage, Math.max(0.0D, scaledDamage)));
        double totalPostureDamage = attacker instanceof PlayerEntity
                ? applyPvpPostureDamage(player, postureDamage, config)
                : POSTURE_DAMAGE.merge(uuid, postureDamage, Double::sum);
        if (totalPostureDamage >= postureHealth && !isOnStaggerCooldown(player)) {
            staggerFromDamage(player, postureHealth);
        }
    }

    public static boolean tryAbsorbBlock(PlayerEntity player, DamageSource source, float amount) {
        if (player.getWorld().isClient() || !isBlockingWithParryItem(player) || isInsideParryWindow(player) || isStaggered(player)) {
            return false;
        }
        LivingEntity attacker = livingDamageSource(source);
        if (attacker == null) {
            return false;
        }

        SUCCESSFUL_BLOCKS.add(player.getUuid());
        UUID uuid = player.getUuid();
        ItemStack activeItem = player.getActiveItem();
        double postureHealth = getPlayerPostureHealth(player, activeItem);
        POSTURE_HEALTH.put(uuid, postureHealth);
        CustomMobsSpawnerConfig.ConfigData config = CustomMobsSpawnerConfig.get();
        markCombat(player, config);
        if (attacker instanceof PlayerEntity playerAttacker) {
            markCombat(playerAttacker, config);
        }
        double postureDamage = POSTURE_DAMAGE.getOrDefault(uuid, 0.0D);
        if (!isPostureInvulnerable(player) && !isWithinTakenPostureGrace(player, config)) {
            double blockedPostureDamage = Math.min(config.playerMaxTakenDamagePostureDamage, Math.max(0.0D,
                    amount * config.playerTakenDamagePostureMultiplier * config.playerBlockedDamagePostureMultiplier));
            double vulnerableBlockedPostureDamage = vulnerablePostureDamage(player, blockedPostureDamage);
            postureDamage = attacker instanceof PlayerEntity
                    ? applyPvpPostureDamage(player, vulnerableBlockedPostureDamage, config)
                    : POSTURE_DAMAGE.merge(uuid, vulnerableBlockedPostureDamage, Double::sum);
        }
        playBlockImpactSound(player, activeItem);
        damageBlockedItem(player, amount);
        if (postureDamage >= postureHealth) {
            if (!isOnStaggerCooldown(player)) {
                stagger(player);
                return true;
            }
            POSTURE_DAMAGE.put(uuid, postureHealth);
            setAndSyncAttackCooldownProgress(player, BLOCK_ATTACK_COOLDOWN_PROGRESS);
            return true;
        }

        setAndSyncAttackCooldownProgress(player, BLOCK_ATTACK_COOLDOWN_PROGRESS);
        return true;
    }

    public static boolean isBlockingWithParryItem(PlayerEntity player) {
        if (!player.isUsingItem() || !isParryItem(player.getActiveItem())) {
            return false;
        }
        if (!isParrySword(player.getActiveItem())) {
            return true;
        }
        if (ARMED_WEAPON_BLOCKS.contains(player.getUuid())) {
            return true;
        }
        if (canStartBlocking(player)) {
            armWeaponBlock(player);
            return true;
        }
        return false;
    }

    public static boolean isParryItem(ItemStack stack) {
        return stack.getItem() instanceof ShieldItem || stack.isIn(ModItemTags.PARRY_SWORDS);
    }

    public static boolean isParrySword(ItemStack stack) {
        return stack.isIn(ModItemTags.PARRY_SWORDS);
    }

    public static Multimap<EntityAttribute, EntityAttributeModifier> addParryAttributeModifiers(
            ItemStack stack,
            EquipmentSlot slot,
            Multimap<EntityAttribute, EntityAttributeModifier> original
    ) {
        if (slot != EquipmentSlot.MAINHAND || (!isParryItem(stack) && !isKnife(stack) && !isRapier(stack))) {
            return original;
        }

        ImmutableMultimap.Builder<EntityAttribute, EntityAttributeModifier> builder = ImmutableMultimap.builder();
        builder.putAll(original);
        CustomMobsSpawnerConfig.ConfigData config = CustomMobsSpawnerConfig.get();
        if (isParryItem(stack)) {
            builder.put(
                    ModAttributes.PARRY_WINDOW_TICKS,
                    new EntityAttributeModifier(
                            PARRY_WINDOW_MODIFIER_ID,
                            "Parry window",
                            getParryWindowTicks(stack),
                            EntityAttributeModifier.Operation.ADDITION
                    )
            );
            builder.put(
                    ModAttributes.POSTURE_HEALTH,
                    new EntityAttributeModifier(
                            POSTURE_HEALTH_MODIFIER_ID,
                            "Posture health",
                            getPostureHealth(stack),
                            EntityAttributeModifier.Operation.ADDITION
                    )
            );
        }
        double postureDamageBonus = weaponPostureDamageBonus(stack, config);
        double staggerDamageMultiplierBonus = weaponStaggerDamageMultiplierBonus(stack, config);
        if (postureDamageBonus > 0.0D) {
            builder.put(
                    ModAttributes.POSTURE_DAMAGE,
                    new EntityAttributeModifier(
                            POSTURE_DAMAGE_MODIFIER_ID,
                            "Posture damage",
                            postureDamageBonus,
                            EntityAttributeModifier.Operation.ADDITION
                    )
            );
        }
        if (staggerDamageMultiplierBonus > 0.0D) {
            builder.put(
                    ModAttributes.STAGGER_DAMAGE_MULTIPLIER,
                    new EntityAttributeModifier(
                            STAGGER_DAMAGE_MULTIPLIER_MODIFIER_ID,
                            "Stagger damage multiplier",
                            staggerDamageMultiplierBonus,
                            EntityAttributeModifier.Operation.MULTIPLY_BASE
                    )
            );
        }
        return builder.build();
    }

    private static boolean isKnife(ItemStack stack) {
        return stack.isIn(ModItemTags.KNIVES);
    }

    private static boolean isRapier(ItemStack stack) {
        return stack.isIn(ModItemTags.RAPIERS);
    }

    private static double weaponPostureDamageBonus(ItemStack stack, CustomMobsSpawnerConfig.ConfigData config) {
        double bonus = 0.0D;
        if (isKnife(stack)) {
            bonus += config.knifePostureDamageBonus;
        }
        if (isRapier(stack)) {
            bonus += config.rapierPostureDamageBonus;
        }
        return bonus;
    }

    private static double weaponStaggerDamageMultiplierBonus(ItemStack stack, CustomMobsSpawnerConfig.ConfigData config) {
        double bonus = 0.0D;
        if (isKnife(stack)) {
            bonus += config.knifeStaggerDamageMultiplierBonus;
        }
        if (isRapier(stack)) {
            bonus += config.rapierStaggerDamageMultiplierBonus;
        }
        return bonus;
    }

    public static boolean canStartBlocking(PlayerEntity player) {
        return !isStaggered(player) && player.getAttackCooldownProgress(0.0F) >= 1.0F;
    }

    public static boolean canStartBlocking(PlayerEntity player, ItemStack stack) {
        if (isStaggered(player)) {
            return false;
        }
        return stack.getItem() instanceof ShieldItem || player.getAttackCooldownProgress(0.0F) >= 1.0F;
    }

    public static void armParryBlock(PlayerEntity player, ItemStack stack) {
        ACTIVE_PARRY_BLOCKS.add(player.getUuid());
        if (isParrySword(stack)) {
            ARMED_WEAPON_BLOCKS.add(player.getUuid());
        }
        playBlockStartSound(player);
    }

    public static void armWeaponBlock(PlayerEntity player) {
        ItemStack activeItem = player.getActiveItem();
        if (isParrySword(activeItem)) {
            armParryBlock(player, activeItem);
        }
    }

    public static void disarmWeaponBlock(PlayerEntity player) {
        UUID uuid = player.getUuid();
        ACTIVE_PARRY_BLOCKS.remove(uuid);
        ARMED_WEAPON_BLOCKS.remove(uuid);
    }

    public static void finishBlockIfActive(PlayerEntity player) {
        if (ACTIVE_PARRY_BLOCKS.contains(player.getUuid())) {
            if (player.getWorld().isClient()) {
                return;
            }
            startBlockCooldown(player);
        }
    }

    public static void startBlockCooldown(PlayerEntity player) {
        UUID uuid = player.getUuid();
        ACTIVE_PARRY_BLOCKS.remove(uuid);
        ARMED_WEAPON_BLOCKS.remove(uuid);
        if (STAGGERED_BLOCKS.remove(uuid)) {
            clearStaggeredPostureDamage(uuid);
            setAndSyncAttackCooldownProgress(player, STAGGER_ATTACK_COOLDOWN_PROGRESS);
            SUCCESSFUL_BLOCKS.remove(uuid);
            return;
        }
        if (PARRY_COUNTER_WINDOWS.remove(uuid) != null) {
            setAndSyncAttackCooldownProgress(player, PARRY_ATTACK_COOLDOWN_PROGRESS);
            SUCCESSFUL_BLOCKS.remove(uuid);
            return;
        }
        if (SUCCESSFUL_BLOCKS.remove(uuid)) {
            setAndSyncAttackCooldownProgress(player, BLOCK_ATTACK_COOLDOWN_PROGRESS);
            return;
        }
        setAndSyncAttackCooldownProgress(player, STAGGER_ATTACK_COOLDOWN_PROGRESS);
    }

    public static void playBlockStartSound(PlayerEntity player) {
        if (!player.getWorld().isClient()) {
            player.playSound(SoundEvents.ITEM_ARMOR_EQUIP_IRON, SoundCategory.PLAYERS, 0.22F, 1.35F);
        }
    }

    public static void tickCooldowns(MinecraftServer server) {
        updatePlayerBasePostureRegen(server);
        updatePlayerBasePvpTakenPostureDamageMultiplier(server);
        cleanupTakenPostureGrace(server);
        tickMap(PARRY_COUNTER_WINDOWS);
        tickMap(STAGGER_WINDOWS);
        tickMap(STAGGER_COOLDOWNS);
        tickMap(COMBAT_WINDOWS);
        tickMap(POSTURE_INVULNERABILITY_WINDOWS);
        tickMap(POSTURE_REGEN_DELAYS);
        regeneratePosture(server);
        syncPosture(server);
    }

    private static void tickMap(ConcurrentHashMap<UUID, Integer> values) {
        values.entrySet().removeIf(entry -> {
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                return true;
            }
            values.put(entry.getKey(), remaining);
            return false;
        });
    }

    private static boolean isWithinTakenPostureGrace(PlayerEntity player, CustomMobsSpawnerConfig.ConfigData config) {
        int graceTicks = Math.max(0, config.playerTakenDamagePostureGraceTicks);
        if (graceTicks <= 0) {
            return false;
        }

        UUID uuid = player.getUuid();
        long time = player.getWorld().getTime();
        Long lastTime = LAST_TAKEN_POSTURE_DAMAGE_TICKS.get(uuid);
        if (lastTime != null && time - lastTime < graceTicks) {
            return true;
        }

        LAST_TAKEN_POSTURE_DAMAGE_TICKS.put(uuid, time);
        return false;
    }

    private static void cleanupTakenPostureGrace(MinecraftServer server) {
        LAST_TAKEN_POSTURE_DAMAGE_TICKS.keySet().removeIf(uuid -> server.getPlayerManager().getPlayer(uuid) == null);
    }

    private static void setAttackCooldownProgress(PlayerEntity player, float progress) {
        int ticks = Math.max(0, Math.round(player.getAttackCooldownProgressPerTick() * progress));
        ((LivingEntityAccessor) player).customMobsSpawner$setLastAttackedTicks(ticks);
    }

    private static void setAttackCooldownTicks(PlayerEntity player, int ticks) {
        int lastAttackedTicks = Math.round(player.getAttackCooldownProgressPerTick()) - Math.max(0, ticks);
        ((LivingEntityAccessor) player).customMobsSpawner$setLastAttackedTicks(lastAttackedTicks);
    }

    public static void applySyncedAttackCooldownProgress(PlayerEntity player, float progress) {
        setAttackCooldownProgress(player, Math.max(0.0F, Math.min(1.0F, progress)));
    }

    public static void applySyncedAttackCooldownTicks(PlayerEntity player, int ticks) {
        setAttackCooldownTicks(player, ticks);
    }

    public static void startAttackCooldown(PlayerEntity player) {
        setAndSyncAttackCooldownProgress(player, 0.0F);
    }

    public static void startAttackCooldown(PlayerEntity player, int ticks) {
        if (ticks <= 0) {
            startAttackCooldown(player);
            return;
        }
        setAttackCooldownTicks(player, ticks);
        syncAttackCooldownTicks(player, ticks);
    }

    private static void setAndSyncAttackCooldownProgress(PlayerEntity player, float progress) {
        setAttackCooldownProgress(player, progress);
        syncAttackCooldownProgress(player, progress);
    }

    private static void syncAttackCooldownProgress(PlayerEntity player, float progress) {
        if (!(player instanceof ServerPlayerEntity serverPlayer) || !ServerPlayNetworking.canSend(serverPlayer, ATTACK_COOLDOWN_SYNC_PACKET)) {
            return;
        }

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeFloat(Math.max(0.0F, Math.min(1.0F, progress)));
        ServerPlayNetworking.send(serverPlayer, ATTACK_COOLDOWN_SYNC_PACKET, buf);
    }

    private static void syncAttackCooldownTicks(PlayerEntity player, int ticks) {
        if (!(player instanceof ServerPlayerEntity serverPlayer) || !ServerPlayNetworking.canSend(serverPlayer, ATTACK_COOLDOWN_SYNC_PACKET)) {
            return;
        }

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeFloat(0.0F);
        buf.writeVarInt(Math.max(0, ticks));
        ServerPlayNetworking.send(serverPlayer, ATTACK_COOLDOWN_SYNC_PACKET, buf);
    }

    private static boolean isInsideParryWindow(PlayerEntity player) {
        int parryWindowTicks = Math.max(0, (int) Math.round(getParryWindowTicks(player.getActiveItem())));
        return player.getItemUseTime() <= parryWindowTicks;
    }

    private static double getParryWindowTicks(ItemStack stack) {
        CustomMobsSpawnerConfig.ConfigData config = CustomMobsSpawnerConfig.get();
        return stack.getItem() instanceof ShieldItem
                ? config.shieldParryWindowTicks
                : config.twoHandedParryWindowTicks;
    }

    private static double getPostureHealth(ItemStack stack) {
        CustomMobsSpawnerConfig.ConfigData config = CustomMobsSpawnerConfig.get();
        return stack.getItem() instanceof ShieldItem
                ? config.shieldPostureHealthBonus
                : config.twoHandedPostureHealthBonus;
    }

    private static double getPlayerPostureHealth(PlayerEntity player, ItemStack activeItem) {
        CustomMobsSpawnerConfig.ConfigData config = CustomMobsSpawnerConfig.get();
        double postureHealth = player.getAttributeValue(ModAttributes.POSTURE_HEALTH)
                + config.playerBasePostureHealth
                - ModAttributes.DEFAULT_POSTURE_HEALTH;
        ItemStack mainHandStack = player.getMainHandStack();
        if (isParryItem(mainHandStack)) {
            postureHealth -= getPostureHealth(mainHandStack);
        }
        if (isParryItem(activeItem)) {
            postureHealth += getPostureHealth(activeItem);
        }
        postureHealth += maxHealthPostureBonus(player, config);
        return Math.max(1.0D, postureHealth);
    }

    private static ItemStack getEquippedPostureItem(PlayerEntity player) {
        if (isParryItem(player.getActiveItem())) {
            return player.getActiveItem();
        }
        if (isParryItem(player.getMainHandStack())) {
            return player.getMainHandStack();
        }
        if (isParryItem(player.getOffHandStack())) {
            return player.getOffHandStack();
        }
        return ItemStack.EMPTY;
    }

    private static double vulnerablePostureDamage(LivingEntity entity, double amount) {
        StatusEffectInstance vulnerable = entity.getStatusEffect(ModStatusEffects.VULNERABLE);
        double multiplierPerLevel = CustomMobsSpawnerConfig.get().vulnerablePostureDamageMultiplierPerLevel;
        double vulnerableAmount = vulnerable == null ? amount : amount * (1.0D + multiplierPerLevel * (vulnerable.getAmplifier() + 1));
        return vulnerableAmount * postureDamageTakenMultiplier(entity);
    }

    private static double postureDamageTakenMultiplier(LivingEntity entity) {
        EntityAttributeInstance instance = entity.getAttributeInstance(ModAttributes.POSTURE_DAMAGE_RESISTANCE);
        double resistance = instance == null ? ModAttributes.DEFAULT_POSTURE_DAMAGE_RESISTANCE : Math.max(0.0D, instance.getValue());
        return Math.max(0.0D, 1.0D - resistance);
    }

    private static double applyPvpPostureDamage(PlayerEntity player, double amount, CustomMobsSpawnerConfig.ConfigData config) {
        UUID uuid = player.getUuid();
        double modifiedAmount = Math.max(0.0D, amount * playerPvpTakenPostureDamageMultiplier(player));
        double cappedAmount = Math.min(config.playerMaxPostureDamagePerHit, modifiedAmount);
        if (cappedAmount <= 0.0D) {
            return POSTURE_DAMAGE.getOrDefault(uuid, 0.0D);
        }
        return POSTURE_DAMAGE.merge(uuid, cappedAmount, Double::sum);
    }

    private static double playerPvpTakenPostureDamageMultiplier(PlayerEntity player) {
        EntityAttributeInstance instance = player.getAttributeInstance(ModAttributes.PVP_TAKEN_POSTURE_DAMAGE_MULTIPLIER);
        return instance == null ? CustomMobsSpawnerConfig.get().playerPvpPostureDamageMultiplier : Math.max(0.0D, instance.getValue());
    }

    private static LivingEntity livingDamageSource(DamageSource source) {
        if (source.getAttacker() instanceof LivingEntity living) {
            return living;
        }
        if (source.getSource() instanceof LivingEntity living) {
            return living;
        }
        Entity damageSource = source.getSource();
        if (damageSource instanceof ProjectileEntity projectile && projectile.getOwner() instanceof LivingEntity living) {
            return living;
        }
        return null;
    }

    private static double maxHealthPostureBonus(LivingEntity entity, CustomMobsSpawnerConfig.ConfigData config) {
        EntityAttributeInstance instance = entity.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (instance == null) {
            return 0.0D;
        }
        return Math.min(config.maxHealthPostureBonusCap, Math.max(0.0D, instance.getValue() * config.maxHealthPostureScale));
    }

    private static void damageBlockedItem(PlayerEntity player, float amount) {
        if (amount < 3.0F) {
            return;
        }

        Hand activeHand = player.getActiveHand();
        player.getActiveItem().damage(1 + (int) amount, player, user -> user.sendToolBreakStatus(activeHand));
    }

    private static void playBlockImpactSound(PlayerEntity player, ItemStack activeItem) {
        player.playSound(SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 0.9F, activeItem.getItem() instanceof ShieldItem ? 0.95F : 1.15F);
    }

    private static void stagger(PlayerEntity player) {
        UUID uuid = player.getUuid();
        CustomMobsSpawnerConfig.ConfigData config = CustomMobsSpawnerConfig.get();
        clearStaggeredPostureDamage(uuid);
        STAGGERED_BLOCKS.add(uuid);
        STAGGER_WINDOWS.put(uuid, config.playerStaggerTicks);
        STAGGER_COOLDOWNS.put(uuid, config.playerStaggerCooldownTicks);
        SUCCESSFUL_BLOCKS.remove(uuid);
        CustomSkillRegistry.cancelFor(player);
        player.getItemCooldownManager().set(player.getActiveItem().getItem(), config.playerStaggerTicks);
        stunEntity(player, config.playerStaggerTicks);
        player.stopUsingItem();

        if (player.getWorld() instanceof ServerWorld world) {
            world.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1.0D, player.getZ(), 18, 0.35D, 0.3D, 0.35D, 0.03D);
        }
    }

    private static void staggerFromDamage(PlayerEntity player, double postureHealth) {
        UUID uuid = player.getUuid();
        CustomMobsSpawnerConfig.ConfigData config = CustomMobsSpawnerConfig.get();
        clearStaggeredPostureDamage(uuid);
        STAGGERED_BLOCKS.add(uuid);
        STAGGER_WINDOWS.put(uuid, config.playerStaggerTicks);
        STAGGER_COOLDOWNS.put(uuid, config.playerStaggerCooldownTicks);
        CustomSkillRegistry.cancelFor(player);
        stunEntity(player, config.playerStaggerTicks);
    }

    private static void clearStaggeredPostureDamage(UUID uuid) {
        POSTURE_DAMAGE.remove(uuid);
        POSTURE_HEALTH.remove(uuid);
        POSTURE_REGEN_DELAYS.remove(uuid);
    }

    public static void stunEntity(LivingEntity entity) {
        stunEntity(entity, CustomMobsSpawnerConfig.get().playerStaggerTicks);
    }

    public static void stunEntity(LivingEntity entity, int durationTicks) {
        float volume = entity instanceof PlayerEntity ? 0.9F : 0.1F;
        float anvilVolume = entity instanceof PlayerEntity ? 0.4F : 0.1F;
        entity.getWorld().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ITEM_SHIELD_BREAK, SoundCategory.HOSTILE, volume, 0.7F);
        entity.getWorld().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, anvilVolume, 0.6F);

        if (Registries.STATUS_EFFECT.containsId(MORE_RPG_CLASSES_STUN)) {
            StatusEffect stun = Registries.STATUS_EFFECT.get(MORE_RPG_CLASSES_STUN);
            entity.addStatusEffect(new StatusEffectInstance(stun, durationTicks, 0));
            return;
        }
        entity.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, durationTicks, 1));
        entity.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, durationTicks, 0));
    }

    private static boolean isStaggered(PlayerEntity player) {
        return STAGGER_WINDOWS.containsKey(player.getUuid());
    }

    private static boolean isOnStaggerCooldown(PlayerEntity player) {
        return STAGGER_COOLDOWNS.containsKey(player.getUuid());
    }

    private static boolean isPostureInvulnerable(PlayerEntity player) {
        return POSTURE_INVULNERABILITY_WINDOWS.containsKey(player.getUuid());
    }

    private static void regeneratePosture(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID uuid = player.getUuid();
            double postureDamage = POSTURE_DAMAGE.getOrDefault(uuid, 0.0D);
            if (postureDamage <= 0.0D) {
                POSTURE_DAMAGE.remove(uuid);
                POSTURE_HEALTH.remove(uuid);
                continue;
            }
            if (POSTURE_REGEN_DELAYS.containsKey(uuid)) {
                continue;
            }

            double regenerated = Math.max(0.0D, player.getAttributeValue(ModAttributes.POSTURE_REGEN)) / 20.0D;
            double remainingDamage = Math.max(0.0D, postureDamage - regenerated);
            if (remainingDamage <= 0.0D) {
                POSTURE_DAMAGE.remove(uuid);
                POSTURE_HEALTH.remove(uuid);
            } else {
                POSTURE_DAMAGE.put(uuid, remainingDamage);
            }
        }
    }

    private static void updatePlayerBasePostureRegen(MinecraftServer server) {
        double configuredBaseRegen = CustomMobsSpawnerConfig.get().playerBasePostureRegen;
        double modifierAmount = configuredBaseRegen - ModAttributes.DEFAULT_POSTURE_REGEN;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            EntityAttributeInstance instance = player.getAttributeInstance(ModAttributes.POSTURE_REGEN);
            if (instance == null) {
                continue;
            }

            EntityAttributeModifier currentModifier = instance.getModifier(PLAYER_BASE_POSTURE_REGEN_MODIFIER_ID);
            if (Math.abs(modifierAmount) <= 0.000001D) {
                if (currentModifier != null) {
                    instance.removeModifier(PLAYER_BASE_POSTURE_REGEN_MODIFIER_ID);
                }
                continue;
            }
            if (currentModifier != null && Math.abs(currentModifier.getValue() - modifierAmount) <= 0.000001D) {
                continue;
            }

            if (currentModifier != null) {
                instance.removeModifier(PLAYER_BASE_POSTURE_REGEN_MODIFIER_ID);
            }
            instance.addPersistentModifier(new EntityAttributeModifier(
                    PLAYER_BASE_POSTURE_REGEN_MODIFIER_ID,
                    "Configured player base posture regen",
                    modifierAmount,
                    EntityAttributeModifier.Operation.ADDITION
            ));
        }
    }

    private static void updatePlayerBasePvpTakenPostureDamageMultiplier(MinecraftServer server) {
        double configuredBase = CustomMobsSpawnerConfig.get().playerPvpPostureDamageMultiplier;
        LAST_CONFIGURED_PVP_TAKEN_POSTURE_DAMAGE_BASES.keySet().removeIf(uuid -> server.getPlayerManager().getPlayer(uuid) == null);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            EntityAttributeInstance instance = player.getAttributeInstance(ModAttributes.PVP_TAKEN_POSTURE_DAMAGE_MULTIPLIER);
            if (instance == null) {
                continue;
            }

            UUID uuid = player.getUuid();
            double currentBase = instance.getBaseValue();
            Double lastConfiguredBase = LAST_CONFIGURED_PVP_TAKEN_POSTURE_DAMAGE_BASES.get(uuid);
            if (lastConfiguredBase == null) {
                if (approximately(currentBase, ModAttributes.DEFAULT_PVP_TAKEN_POSTURE_DAMAGE_MULTIPLIER)) {
                    instance.setBaseValue(configuredBase);
                    LAST_CONFIGURED_PVP_TAKEN_POSTURE_DAMAGE_BASES.put(uuid, configuredBase);
                }
                continue;
            }

            if (approximately(currentBase, lastConfiguredBase)) {
                if (!approximately(currentBase, configuredBase)) {
                    instance.setBaseValue(configuredBase);
                }
                LAST_CONFIGURED_PVP_TAKEN_POSTURE_DAMAGE_BASES.put(uuid, configuredBase);
            } else {
                LAST_CONFIGURED_PVP_TAKEN_POSTURE_DAMAGE_BASES.remove(uuid);
            }
        }
    }

    private static boolean approximately(double left, double right) {
        return Math.abs(left - right) <= EPSILON;
    }

    private static void syncPosture(MinecraftServer server) {
        LAST_SELF_POSTURE_SYNC.keySet().removeIf(uuid -> server.getPlayerManager().getPlayer(uuid) == null);
        LAST_ENTITY_POSTURE_SYNC.keySet().removeIf(uuid -> server.getPlayerManager().getPlayer(uuid) == null);
        LAST_ENTITY_POSTURE_SYNC_TIMES.keySet().removeIf(uuid -> server.getPlayerManager().getPlayer(uuid) == null);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID uuid = player.getUuid();
            double postureHealth = POSTURE_HEALTH.getOrDefault(uuid, 0.0D);
            double postureDamage = POSTURE_DAMAGE.getOrDefault(uuid, 0.0D);
            boolean isCombatPostureVisible = player.isAlive()
                    && !player.isSpectator()
                    && (COMBAT_WINDOWS.containsKey(uuid)
                    || postureHealth > 0.0D
                    || POSTURE_REGEN_DELAYS.containsKey(uuid)
                    || isStaggered(player));
            boolean showSelfPosture = isCombatPostureVisible;
            boolean showTrackedPosture = isCombatPostureVisible;
            if (isCombatPostureVisible && postureHealth <= 0.0D) {
                postureHealth = getPlayerPostureHealth(player, getEquippedPostureItem(player));
            }
            float postureFraction = !isCombatPostureVisible || postureHealth <= 0.0D
                    ? 1.0F
                    : (float) Math.max(0.0D, Math.min(1.0D, 1.0D - postureDamage / postureHealth));
            spawnPostureSweat(player, postureFraction);
            int posturePercent = Math.round(postureFraction * 100.0F);
            boolean staggered = showTrackedPosture && isStaggered(player);
            boolean vulnerable = showTrackedPosture && player.hasStatusEffect(ModStatusEffects.VULNERABLE);
            int staggerDamagePercent = staggered ? 100 - posturePercent : 0;
            int selfPosturePercent = staggered ? 0 : posturePercent;
            syncSelfPosture(player, selfPosturePercent, showSelfPosture);
            syncTrackedPlayerPosture(player, posturePercent, showTrackedPosture, staggered, staggerDamagePercent, vulnerable);
        }
    }

    private static void syncSelfPosture(ServerPlayerEntity player, int posturePercent, boolean showPosture) {
        UUID uuid = player.getUuid();
        PostureSyncState syncState = new PostureSyncState(posturePercent, showPosture);
        if (syncState.equals(LAST_SELF_POSTURE_SYNC.get(uuid))) {
            return;
        }

        LAST_SELF_POSTURE_SYNC.put(uuid, syncState);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(Math.max(0, Math.min(100, posturePercent)));
        buf.writeBoolean(showPosture);
        ServerPlayNetworking.send(player, POSTURE_SYNC_PACKET, buf);
    }

    private static void syncTrackedPlayerPosture(
            ServerPlayerEntity player,
            int posturePercent,
            boolean showPosture,
            boolean staggered,
            int staggerDamagePercent,
            boolean vulnerable
    ) {
        UUID uuid = player.getUuid();
        if (!showPosture) {
            if (LAST_ENTITY_POSTURE_SYNC.remove(uuid) != null) {
                LAST_ENTITY_POSTURE_SYNC_TIMES.remove(uuid);
                sendTrackedPlayerPosture(player, 100, false, false, 0, false);
            }
            return;
        }

        EntityPostureSyncState syncState = new EntityPostureSyncState(
                player.getId(),
                posturePercent,
                true,
                staggered,
                staggerDamagePercent,
                vulnerable
        );
        long time = player.getWorld().getTime();
        if (syncState.equals(LAST_ENTITY_POSTURE_SYNC.get(uuid))
                && time - LAST_ENTITY_POSTURE_SYNC_TIMES.getOrDefault(uuid, Long.MIN_VALUE) < ENTITY_POSTURE_SYNC_REFRESH_TICKS) {
            return;
        }

        LAST_ENTITY_POSTURE_SYNC.put(uuid, syncState);
        LAST_ENTITY_POSTURE_SYNC_TIMES.put(uuid, time);
        sendTrackedPlayerPosture(player, posturePercent, true, staggered, staggerDamagePercent, vulnerable);
    }

    private static void sendTrackedPlayerPosture(
            ServerPlayerEntity player,
            int posturePercent,
            boolean showPosture,
            boolean staggered,
            int staggerDamagePercent,
            boolean vulnerable
    ) {
        boolean showSelfDebugBar = showPosture && CustomMobsSpawnerConfig.get().showSelfOverheadPostureDebugBar;
        sendTrackedPlayerPostureToViewer(player, player, posturePercent, showSelfDebugBar, staggered, staggerDamagePercent, vulnerable);
        for (ServerPlayerEntity viewer : PlayerLookup.tracking(player)) {
            if (viewer.squaredDistanceTo(player) > TRACKED_PLAYER_POSTURE_SYNC_DISTANCE_SQUARED || !isNameTagVisibleFor(player, viewer)) {
                continue;
            }

            sendTrackedPlayerPostureToViewer(viewer, player, posturePercent, showPosture, staggered, staggerDamagePercent, vulnerable);
        }
    }

    private static void sendTrackedPlayerPostureToViewer(
            ServerPlayerEntity viewer,
            ServerPlayerEntity player,
            int posturePercent,
            boolean showPosture,
            boolean staggered,
            int staggerDamagePercent,
            boolean vulnerable
    ) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(player.getId());
        buf.writeByte(Math.max(0, Math.min(100, posturePercent)));
        buf.writeBoolean(showPosture);
        buf.writeBoolean(staggered);
        buf.writeByte(Math.max(0, Math.min(100, staggerDamagePercent)));
        buf.writeBoolean(vulnerable);
        ServerPlayNetworking.send(viewer, MobPostureHandler.MOB_POSTURE_SYNC_PACKET, buf);
    }

    private static boolean isNameTagVisibleFor(ServerPlayerEntity target, ServerPlayerEntity viewer) {
        AbstractTeam team = target.getScoreboardTeam();
        if (team == null) {
            return true;
        }

        AbstractTeam viewerTeam = viewer.getScoreboardTeam();
        return switch (team.getNameTagVisibilityRule()) {
            case ALWAYS -> true;
            case NEVER -> false;
            case HIDE_FOR_OTHER_TEAMS -> viewerTeam != null && team.isEqual(viewerTeam);
            case HIDE_FOR_OWN_TEAM -> viewerTeam == null || !team.isEqual(viewerTeam);
        };
    }

    private record PostureSyncState(int posturePercent, boolean showPosture) {
    }

    private record EntityPostureSyncState(
            int entityId,
            int posturePercent,
            boolean showPosture,
            boolean staggered,
            int staggerDamagePercent,
            boolean vulnerable
    ) {
    }

    private static void spawnPostureSweat(LivingEntity entity, float postureFraction) {
        if (postureFraction >= 0.5F || entity.getWorld().getTime() % 8L != 0L || !(entity.getWorld() instanceof ServerWorld world)) {
            return;
        }

        int count = postureFraction < 0.1F ? 7 : postureFraction < 0.25F ? 4 : 1;
        world.spawnParticles(
                ParticleTypes.SPLASH,
                entity.getX(),
                entity.getY() + entity.getHeight() + 0.05D,
                entity.getZ(),
                count,
                0.28D,
                0.08D,
                0.28D,
                0.02D
        );
    }

}
