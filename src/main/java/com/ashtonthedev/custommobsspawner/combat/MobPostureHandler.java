package com.ashtonthedev.custommobsspawner.combat;

import com.ashtonthedev.custommobsspawner.CustomMobsSpawner;
import com.ashtonthedev.custommobsspawner.config.CustomMobsSpawnerConfig;
import com.ashtonthedev.custommobsspawner.effect.ModStatusEffects;
import com.ashtonthedev.custommobsspawner.skill.CustomSkillRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MobPostureHandler {
    public static final Identifier MOB_POSTURE_SYNC_PACKET = CustomMobsSpawner.id("mob_posture_sync");
    private static final int SYNC_REFRESH_TICKS = 20;
    private static final ConcurrentHashMap<UUID, Double> POSTURE_DAMAGE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Integer> REGEN_DELAYS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Integer> STAGGER_WINDOWS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Integer> STAGGER_COOLDOWNS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Integer> LAST_POSTURE_SYNC = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Integer> LAST_POSTURE_SYNC_ENTITY_IDS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> LAST_POSTURE_SYNC_TIMES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> POSTURE_DISPLAY_UNTIL = new ConcurrentHashMap<>();

    private MobPostureHandler() {
    }

    public static void applyPlayerDamage(LivingEntity entity, DamageSource source, float amount) {
        PlayerEntity player = playerDamageSource(source);
        if (entity.getWorld().isClient() || !hasPosture(entity) || player == null) {
            return;
        }

        PlayerParryHandler.markCombat(player);
        double flatPostureDamage = Math.max(0.0D, amount + player.getAttributeValue(ModAttributes.POSTURE_DAMAGE));
        double postureDamageMultiplier = Math.max(0.0D, player.getAttributeValue(ModAttributes.POSTURE_DAMAGE_MULTIPLIER));
        applyPostureDamage(entity, flatPostureDamage * postureDamageMultiplier);
    }

    public static float modifyIncomingDamage(LivingEntity entity, DamageSource source, float amount) {
        if (entity.getWorld().isClient() || !hasPosture(entity) || !isStaggered(entity)) {
            return amount;
        }
        if (!isLivingSourcedDamage(source)) {
            return amount;
        }

        playStaggeredHitSound(entity);
        double staggerDamageMultiplier = staggerDamageMultiplier(source);
        return (float) (amount * staggerDamageMultiplier);
    }

    public static boolean applyParry(LivingEntity entity) {
        if (!entity.getWorld().isClient() && hasPosture(entity)) {
            return applyPostureDamage(entity, CustomMobsSpawnerConfig.get().mobParryPostureDamage);
        }
        return false;
    }

    public static boolean applySkillPostureDamage(LivingEntity entity, double amount) {
        return applySkillPostureDamage(entity, amount, false);
    }

    public static boolean applySkillPostureDamage(LivingEntity entity, double amount, boolean staggerOnBreak) {
        if (!entity.getWorld().isClient() && hasPosture(entity)) {
            return applyPostureDamage(entity, Math.max(0.0D, amount), staggerOnBreak);
        }
        return false;
    }

    public static void restoreSkillPosture(LivingEntity entity, double amount) {
        if (entity.getWorld().isClient() || !hasPosture(entity) || amount <= 0.0D) {
            return;
        }

        UUID uuid = entity.getUuid();
        double postureDamage = POSTURE_DAMAGE.getOrDefault(uuid, 0.0D);
        if (postureDamage <= 0.0D) {
            return;
        }

        double remainingDamage = Math.max(0.0D, postureDamage - amount);
        if (remainingDamage <= 0.0D) {
            POSTURE_DAMAGE.remove(uuid);
            REGEN_DELAYS.remove(uuid);
        } else {
            POSTURE_DAMAGE.put(uuid, remainingDamage);
        }
        POSTURE_DISPLAY_UNTIL.put(uuid, entity.getWorld().getTime() + CustomMobsSpawnerConfig.get().mobPostureDisplayTicks);
    }

    private static boolean applyPostureDamage(LivingEntity entity, double amount) {
        return applyPostureDamage(entity, amount, false);
    }

    private static boolean applyPostureDamage(LivingEntity entity, double amount, boolean staggerOnBreak) {
        if (amount <= 0.0D) {
            return false;
        }

        UUID uuid = entity.getUuid();
        CustomMobsSpawnerConfig.ConfigData config = CustomMobsSpawnerConfig.get();
        double postureHealth = getPostureHealth(entity);
        double postureDamage = POSTURE_DAMAGE.merge(uuid, vulnerablePostureDamage(entity, amount), Double::sum);
        POSTURE_DISPLAY_UNTIL.put(uuid, entity.getWorld().getTime() + config.mobPostureDisplayTicks);
        REGEN_DELAYS.put(uuid, config.mobPostureRegenDelayTicks);
        if (postureDamage >= postureHealth && !isStaggered(entity) && (staggerOnBreak || !STAGGER_COOLDOWNS.containsKey(uuid))) {
            POSTURE_DAMAGE.remove(uuid);
            REGEN_DELAYS.remove(uuid);
            STAGGER_WINDOWS.put(uuid, config.mobStaggerTicks);
            STAGGER_COOLDOWNS.put(uuid, config.mobStaggerCooldownTicks);
            syncPosture(entity, 1.0D, true);
            CustomSkillRegistry.cancelFor(entity);
            CustomSkillRegistry.applyGlobalCooldown(entity, config.mobStaggerTicks);
            if (!entity.getType().isIn(ModEntityTypeTags.NO_STUN)) {
                PlayerParryHandler.stunEntity(entity, config.mobStaggerTicks);
            }
            spawnStaggerParticles(entity);
            return true;
        }
        return false;
    }

    public static void tick(MinecraftServer server) {
        tickMap(REGEN_DELAYS);
        tickMap(STAGGER_WINDOWS);
        tickMap(STAGGER_COOLDOWNS);
        tickDisplayedPosture(server);
        for (UUID uuid : POSTURE_DAMAGE.keySet()) {
            LivingEntity entity = getLivingEntity(server, uuid);
            if (entity == null || !entity.isAlive()) {
                syncPosture(server, uuid, false);
                POSTURE_DAMAGE.remove(uuid);
                REGEN_DELAYS.remove(uuid);
                STAGGER_WINDOWS.remove(uuid);
                STAGGER_COOLDOWNS.remove(uuid);
                LAST_POSTURE_SYNC.remove(uuid);
                LAST_POSTURE_SYNC_ENTITY_IDS.remove(uuid);
                LAST_POSTURE_SYNC_TIMES.remove(uuid);
                POSTURE_DISPLAY_UNTIL.remove(uuid);
                continue;
            }
            if (!hasPosture(entity)) {
                syncPosture(server, uuid, false);
                POSTURE_DAMAGE.remove(uuid);
                REGEN_DELAYS.remove(uuid);
                STAGGER_WINDOWS.remove(uuid);
                STAGGER_COOLDOWNS.remove(uuid);
                LAST_POSTURE_SYNC.remove(uuid);
                LAST_POSTURE_SYNC_ENTITY_IDS.remove(uuid);
                LAST_POSTURE_SYNC_TIMES.remove(uuid);
                POSTURE_DISPLAY_UNTIL.remove(uuid);
                continue;
            }
            double postureHealth = getPostureHealth(entity);
            double postureDamage = POSTURE_DAMAGE.getOrDefault(uuid, 0.0D);
            double postureFraction = postureFraction(postureHealth, postureDamage);
            spawnPostureSweat(entity, postureFraction);
            syncPosture(entity, postureFraction, true);
            if (REGEN_DELAYS.containsKey(uuid)) {
                continue;
            }

            double regenerated = getPostureRegen(entity) / 20.0D;
            double remainingDamage = Math.max(0.0D, postureDamage - regenerated);
            if (remainingDamage <= 0.0D) {
                POSTURE_DAMAGE.remove(uuid);
            } else {
                POSTURE_DAMAGE.put(uuid, remainingDamage);
            }
        }
    }

    private static void tickDisplayedPosture(MinecraftServer server) {
        long time = server.getOverworld().getTime();
        for (UUID uuid : POSTURE_DISPLAY_UNTIL.keySet()) {
            LivingEntity entity = getLivingEntity(server, uuid);
            long displayUntil = POSTURE_DISPLAY_UNTIL.getOrDefault(uuid, Long.MIN_VALUE);
            boolean hasPostureDamage = POSTURE_DAMAGE.containsKey(uuid);
            if (entity == null || !entity.isAlive() || !hasPosture(entity) || (!hasPostureDamage && time > displayUntil)) {
                syncPosture(server, uuid, false);
                POSTURE_DISPLAY_UNTIL.remove(uuid);
                LAST_POSTURE_SYNC.remove(uuid);
                LAST_POSTURE_SYNC_ENTITY_IDS.remove(uuid);
                LAST_POSTURE_SYNC_TIMES.remove(uuid);
                continue;
            }
            if (isStaggered(entity)) {
                double postureFraction = hasPostureDamage
                        ? postureFraction(getPostureHealth(entity), POSTURE_DAMAGE.getOrDefault(uuid, 0.0D))
                        : 1.0D;
                syncPosture(entity, postureFraction, true);
            } else if (!hasPostureDamage) {
                syncPosture(entity, 1.0D, true);
            }
        }
    }

    private static LivingEntity getLivingEntity(MinecraftServer server, UUID uuid) {
        for (ServerWorld world : server.getWorlds()) {
            if (world.getEntity(uuid) instanceof LivingEntity livingEntity) {
                return livingEntity;
            }
        }
        return null;
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

    private static boolean isStaggered(LivingEntity entity) {
        return STAGGER_WINDOWS.containsKey(entity.getUuid());
    }

    private static boolean hasPosture(LivingEntity entity) {
        return entity instanceof HostileEntity && !entity.getType().isIn(ModEntityTypeTags.NO_POSTURE);
    }

    private static boolean isLivingSourcedDamage(DamageSource source) {
        if (source.getAttacker() instanceof LivingEntity) {
            return true;
        }
        if (source.getSource() instanceof LivingEntity) {
            return true;
        }
        return source.getSource() instanceof ProjectileEntity projectile && projectile.getOwner() instanceof LivingEntity;
    }

    private static double staggerDamageMultiplier(DamageSource source) {
        double baseMultiplier = CustomMobsSpawnerConfig.get().baseStaggerDamageMultiplier;
        PlayerEntity player = playerDamageSource(source);
        if (player == null) {
            return baseMultiplier;
        }
        EntityAttributeInstance instance = player.getAttributeInstance(ModAttributes.STAGGER_DAMAGE_MULTIPLIER);
        if (instance == null) {
            return baseMultiplier;
        }
        double configuredValue = baseMultiplier * Math.max(0.0D, instance.getValue()) / ModAttributes.DEFAULT_STAGGER_DAMAGE_MULTIPLIER;
        return Math.max(baseMultiplier, configuredValue);
    }

    private static PlayerEntity playerDamageSource(DamageSource source) {
        if (source.getAttacker() instanceof PlayerEntity player) {
            return player;
        }
        if (source.getSource() instanceof PlayerEntity player) {
            return player;
        }
        Entity damageSource = source.getSource();
        if (damageSource instanceof ProjectileEntity projectile && projectile.getOwner() instanceof PlayerEntity player) {
            return player;
        }
        return null;
    }

    private static void playStaggeredHitSound(LivingEntity entity) {
        entity.getWorld().playSound(null, entity.getX(), entity.getY(), entity.getZ(), net.minecraft.sound.SoundEvents.ITEM_SHIELD_BREAK, net.minecraft.sound.SoundCategory.HOSTILE, 0.05F, 1.15F);
    }

    private static void spawnStaggerParticles(LivingEntity entity) {
        if (entity.getWorld() instanceof ServerWorld world) {
            world.spawnParticles(ParticleTypes.CRIT, entity.getX(), entity.getY() + entity.getHeight() * 0.6D, entity.getZ(), 24, 0.35D, 0.35D, 0.35D, 0.25D);
            world.spawnParticles(ParticleTypes.END_ROD, entity.getX(), entity.getY() + entity.getHeight() * 0.6D, entity.getZ(), 12, 0.25D, 0.35D, 0.25D, 0.25D);
        }
    }

    private static void spawnPostureSweat(LivingEntity entity, double postureFraction) {
        if (postureFraction >= 0.5D || entity.getWorld().getTime() % 8L != 0L || !(entity.getWorld() instanceof ServerWorld world)) {
            return;
        }

        int count = postureFraction < 0.1D ? 7 : postureFraction < 0.25D ? 4 : 1;
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

    private static double postureFraction(double postureHealth, double postureDamage) {
        return postureHealth <= 0.0D ? 1.0D : Math.max(0.0D, Math.min(1.0D, 1.0D - postureDamage / postureHealth));
    }

    private static double getPostureHealth(LivingEntity entity) {
        CustomMobsSpawnerConfig.ConfigData config = CustomMobsSpawnerConfig.get();
        EntityAttributeInstance instance = entity.getAttributeInstance(ModAttributes.POSTURE_HEALTH);
        double attributeBonus = instance == null ? 0.0D : instance.getValue() - ModAttributes.DEFAULT_MOB_POSTURE_HEALTH;
        double basePostureHealth = config.mobBasePostureHealth + attributeBonus;
        return Math.max(1.0D, basePostureHealth + maxHealthPostureBonus(entity, config));
    }

    private static double getPostureRegen(LivingEntity entity) {
        EntityAttributeInstance instance = entity.getAttributeInstance(ModAttributes.POSTURE_REGEN);
        CustomMobsSpawnerConfig.ConfigData config = CustomMobsSpawnerConfig.get();
        double attributeBonus = instance == null ? 0.0D : instance.getValue() - ModAttributes.DEFAULT_POSTURE_REGEN;
        return Math.max(0.0D, config.mobBasePostureRegen + attributeBonus);
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

    private static double maxHealthPostureBonus(LivingEntity entity, CustomMobsSpawnerConfig.ConfigData config) {
        EntityAttributeInstance instance = entity.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (instance == null) {
            return 0.0D;
        }
        return Math.min(config.mobMaxHealthPostureBonusCap, Math.max(0.0D, instance.getValue() * config.mobMaxHealthPostureScale));
    }

    private static void syncPosture(LivingEntity entity, double postureFraction, boolean showPosture) {
        int posturePercent = Math.max(0, Math.min(100, (int) Math.round(postureFraction * 100.0D)));
        boolean staggered = showPosture && isStaggered(entity);
        boolean vulnerable = showPosture && entity.hasStatusEffect(ModStatusEffects.VULNERABLE);
        int redPosturePercent = staggered ? 100 - posturePercent : 0;
        int syncValue = showPosture ? posturePercent + redPosturePercent * 101 + (staggered ? 10201 : 0) + (vulnerable ? 20402 : 0) : -1;
        UUID uuid = entity.getUuid();
        long time = entity.getWorld().getTime();
        if (LAST_POSTURE_SYNC.getOrDefault(uuid, Integer.MIN_VALUE) == syncValue
                && time - LAST_POSTURE_SYNC_TIMES.getOrDefault(uuid, Long.MIN_VALUE) < SYNC_REFRESH_TICKS) {
            return;
        }
        if (showPosture) {
            LAST_POSTURE_SYNC.put(uuid, syncValue);
            LAST_POSTURE_SYNC_ENTITY_IDS.put(uuid, entity.getId());
            LAST_POSTURE_SYNC_TIMES.put(uuid, time);
        } else {
            LAST_POSTURE_SYNC.remove(uuid);
            LAST_POSTURE_SYNC_ENTITY_IDS.remove(uuid);
            LAST_POSTURE_SYNC_TIMES.remove(uuid);
        }

        for (ServerPlayerEntity player : PlayerLookup.tracking(entity)) {
            sendPostureSync(player, entity.getId(), posturePercent / 100.0F, showPosture, staggered, redPosturePercent / 100.0F, vulnerable);
        }
    }

    private static void syncPosture(MinecraftServer server, UUID uuid, boolean showPosture) {
        Integer entityId = LAST_POSTURE_SYNC_ENTITY_IDS.remove(uuid);
        if (entityId == null) {
            return;
        }
        LAST_POSTURE_SYNC.remove(uuid);
        LAST_POSTURE_SYNC_TIMES.remove(uuid);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            sendPostureSync(player, entityId, 1.0F, showPosture, false, 0.0F, false);
        }
    }

    private static void sendPostureSync(ServerPlayerEntity player, int entityId, float postureFraction, boolean showPosture, boolean staggered, float staggerDamageFraction, boolean vulnerable) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(entityId);
        buf.writeByte(Math.max(0, Math.min(100, Math.round(postureFraction * 100.0F))));
        buf.writeBoolean(showPosture);
        buf.writeBoolean(staggered);
        buf.writeByte(Math.max(0, Math.min(100, Math.round(staggerDamageFraction * 100.0F))));
        buf.writeBoolean(vulnerable);
        ServerPlayNetworking.send(player, MOB_POSTURE_SYNC_PACKET, buf);
    }
}
