package com.ashtonthedev.custommobsspawner.skill;

import com.ashtonthedev.custommobsspawner.combat.MobPostureHandler;
import com.ashtonthedev.custommobsspawner.combat.ModItemTags;
import com.ashtonthedev.custommobsspawner.combat.PlayerParryHandler;
import com.ashtonthedev.custommobsspawner.combat.ComboHandler;
import com.ashtonthedev.custommobsspawner.combat.SpellComboHandler;
import com.ashtonthedev.custommobsspawner.compat.BetterCombatAnimationCompat;
import com.ashtonthedev.custommobsspawner.data.CustomMobRegistry;
import com.ashtonthedev.custommobsspawner.data.CustomMobsSpawnerLog;
import com.ashtonthedev.custommobsspawner.mixin.PersistentProjectileEntityAccessor;
import com.ashtonthedev.custommobsspawner.util.SpawnSafety;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import net.spell_engine.api.spell.CustomSpellHandler;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.SpellInfo;
import net.spell_engine.entity.SpellProjectile;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.internals.SpellRegistry;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.particle.ParticleHelper;
import net.spell_engine.utils.SoundHelper;
import net.spell_power.api.SpellPower;
import com.google.common.collect.Multimap;

import java.util.Collection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class CustomSkillRegistry {
    public static final Identifier FAILED_ATTACK_PACKET = new Identifier("cmobs", "failed_attack");
    private static final Map<Identifier, CustomSkillDefinition> SKILLS = new ConcurrentHashMap<>();
    private static final Map<SkillTrigger, List<CustomSkillDefinition>> SKILLS_BY_TRIGGER = new ConcurrentHashMap<>();
    private static final Set<Identifier> PLAYER_SKILLS = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Integer> GLOBAL_COOLDOWNS = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<Identifier, Integer>> SKILL_COOLDOWNS = new ConcurrentHashMap<>();
    private static final Map<Identifier, JsonElement> SPELL_CONDITIONS = new ConcurrentHashMap<>();
    private static final Map<PendingSkillKey, Long> LAST_SPELL_PROC_ROLL_TICKS = new ConcurrentHashMap<>();
    private static final Map<UUID, StanceState> ACTIVE_STANCES = new ConcurrentHashMap<>();
    private static final Map<UUID, AuraState> ACTIVE_AURAS = new ConcurrentHashMap<>();
    private static final Map<PendingSkillKey, PendingSkillState> PENDING_SKILLS = new ConcurrentHashMap<>();
    private static final Map<UUID, ScheduledActionState> SCHEDULED_ACTIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, SkillProjectileState> SKILL_PROJECTILES = new ConcurrentHashMap<>();
    private static final Map<UUID, GrenadeProjectileState> GRENADE_PROJECTILES = new ConcurrentHashMap<>();
    private static final Map<UUID, SpellProjectileDamageOverride> SPELL_PROJECTILE_DAMAGE_OVERRIDES = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> SUCCESSFUL_PLAYER_ATTACK_TICKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> PENDING_FAILED_ATTACK_TICKS = new ConcurrentHashMap<>();
    private static final Set<UUID> TIMED_SKILL_ENTITIES = ConcurrentHashMap.newKeySet();
    private static final Set<String> MISSING_SOUND_WARNINGS = ConcurrentHashMap.newKeySet();
    private static final Set<String> MISSING_PARTICLE_WARNINGS = ConcurrentHashMap.newKeySet();
    private static final Set<String> ACTION_WARNINGS = ConcurrentHashMap.newKeySet();
    private static final String SKILL_TAG_PREFIX = "custom_skill_";
    private static final String SKILL_PROJECTILE_TAG = "cmobs_skill_projectile";
    private static final int SPELL_PROJECTILE_OVERRIDE_TTL_TICKS = 20 * 30;
    private static final int PLAYER_HIT_SKILL_MIN_COOLDOWN_TICKS = 2;
    private static final int TIMED_SKILL_ENTITY_CLEANUP_INTERVAL_TICKS = 100;
    private static final ThreadLocal<Boolean> SPELL_PROJECTILE_IMPACTING = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Float> SPELL_PROJECTILE_DAMAGE_OVERRIDE = new ThreadLocal<>();
    private static final ThreadLocal<SkillTargetContext> TARGET_CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Float> ACTIVE_PROC_COEFFICIENT = ThreadLocal.withInitial(() -> 1.0F);
    private static final ThreadLocal<Integer> ACTIVE_COMBO_COUNT = ThreadLocal.withInitial(() -> -1);
    private static final ThreadLocal<Boolean> SPELL_HIT_PROC_CONTEXT = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<SpellInfo> SPELL_HIT_PROC_SPELL = new ThreadLocal<>();
    private static final double VANILLA_ATTACK_SPEED = 4.0D;
    private static final Identifier SWORD_FINISHER_SPELL = new Identifier("cmobs", "sword_finisher");
    private static final Identifier CLAYMORE_FINISHER_SPELL = new Identifier("cmobs", "claymore_finisher");
    private static final Identifier DAGGER_FINISHER_SPELL = new Identifier("cmobs", "dagger_finisher");
    private static final Identifier ZENITH_DRAW_SPEED = new Identifier("zenith_attributes", "draw_speed");
    private static boolean applyingReflectedDamage = false;

    private CustomSkillRegistry() {
    }

    public static void registerCustomSpellHandlers() {
        CustomSpellHandler.register(SWORD_FINISHER_SPELL, dataObject -> {
            CustomSpellHandler.Data data = (CustomSpellHandler.Data) dataObject;
            if (data.action() == SpellCast.Action.RELEASE && data.caster() instanceof ServerPlayerEntity player) {
                runSpellCast(player, SWORD_FINISHER_SPELL, data.targets());
            }
            return true;
        });
        CustomSpellHandler.register(CLAYMORE_FINISHER_SPELL, dataObject -> {
            CustomSpellHandler.Data data = (CustomSpellHandler.Data) dataObject;
            if (data.action() == SpellCast.Action.RELEASE && data.caster() instanceof ServerPlayerEntity player) {
                runSpellCast(player, CLAYMORE_FINISHER_SPELL, data.targets());
            }
            return true;
        });
        CustomSpellHandler.register(DAGGER_FINISHER_SPELL, dataObject -> {
            CustomSpellHandler.Data data = (CustomSpellHandler.Data) dataObject;
            if (data.action() == SpellCast.Action.RELEASE && data.caster() instanceof ServerPlayerEntity player) {
                runSpellCast(player, DAGGER_FINISHER_SPELL, data.targets());
            }
            return true;
        });
    }

    public static void replaceAll(Collection<CustomSkillDefinition> skills) {
        SKILLS.clear();
        SKILLS_BY_TRIGGER.clear();
        PLAYER_SKILLS.clear();
        GLOBAL_COOLDOWNS.clear();
        SKILL_COOLDOWNS.clear();
        LAST_SPELL_PROC_ROLL_TICKS.clear();
        ACTIVE_STANCES.clear();
        ACTIVE_AURAS.clear();
        PENDING_SKILLS.clear();
        SCHEDULED_ACTIONS.clear();
        SKILL_PROJECTILES.clear();
        GRENADE_PROJECTILES.clear();
        SPELL_PROJECTILE_DAMAGE_OVERRIDES.clear();
        SUCCESSFUL_PLAYER_ATTACK_TICKS.clear();
        PENDING_FAILED_ATTACK_TICKS.clear();
        TIMED_SKILL_ENTITIES.clear();
        MISSING_SOUND_WARNINGS.clear();
        MISSING_PARTICLE_WARNINGS.clear();
        ACTION_WARNINGS.clear();
        Map<SkillTrigger, List<CustomSkillDefinition>> byTrigger = new EnumMap<>(SkillTrigger.class);
        for (CustomSkillDefinition skill : skills) {
            SKILLS.put(skill.id(), skill);
            byTrigger.computeIfAbsent(skill.trigger(), ignored -> new ArrayList<>()).add(skill);
            if (skill.playerSkill()) {
                PLAYER_SKILLS.add(skill.id());
            }
        }
        byTrigger.forEach((trigger, definitions) -> SKILLS_BY_TRIGGER.put(trigger, List.copyOf(definitions)));
    }

    public static void replaceSpellConditions(Map<Identifier, JsonElement> spellConditions) {
        SPELL_CONDITIONS.clear();
        SPELL_CONDITIONS.putAll(spellConditions);
    }

    public static void beginSpellProjectileImpact(ProjectileEntity projectile) {
        SPELL_PROJECTILE_IMPACTING.set(true);
        SpellProjectileDamageOverride override = SPELL_PROJECTILE_DAMAGE_OVERRIDES.remove(projectile.getUuid());
        if (override == null || override.expiresAt < projectile.getWorld().getTime()) {
            SPELL_PROJECTILE_DAMAGE_OVERRIDE.remove();
            return;
        }
        SPELL_PROJECTILE_DAMAGE_OVERRIDE.set(override.amount);
    }

    public static void endSpellProjectileImpact() {
        SPELL_PROJECTILE_IMPACTING.set(false);
        SPELL_PROJECTILE_DAMAGE_OVERRIDE.remove();
    }

    public static boolean isSpellProjectileImpacting() {
        return SPELL_PROJECTILE_IMPACTING.get();
    }

    public static Float currentSpellProjectileDamageOverride() {
        return SPELL_PROJECTILE_DAMAGE_OVERRIDE.get();
    }

    public static void attachSkill(Entity entity, Identifier skillId) {
        CustomSkillDefinition skill = SKILLS.get(skillId);
        if (skill == null) {
            return;
        }

        entity.addCommandTag(tagFor(skillId));
        if (skill.trigger() == SkillTrigger.TIMED) {
            TIMED_SKILL_ENTITIES.add(entity.getUuid());
        }
    }

    public static void attachPlayerSkills(ServerPlayerEntity player) {
        for (Identifier skillId : PLAYER_SKILLS) {
            attachSkill(player, skillId);
        }
    }

    private static void attachPlayerSkills(LivingEntity entity) {
        if (entity instanceof ServerPlayerEntity player) {
            attachPlayerSkills(player);
        }
    }

    private static void attachPlayerSkills(ServerWorld world) {
        if (PLAYER_SKILLS.isEmpty()) {
            return;
        }
        for (ServerPlayerEntity player : world.getPlayers()) {
            attachPlayerSkills(player);
        }
    }

    private static List<CustomSkillDefinition> skillsFor(SkillTrigger trigger) {
        return SKILLS_BY_TRIGGER.getOrDefault(trigger, List.of());
    }

    public static void run(LivingEntity entity, SkillTrigger trigger) {
        if (!(entity.getWorld() instanceof ServerWorld world)) {
            return;
        }
        attachPlayerSkills(entity);

        for (CustomSkillDefinition skill : skillsFor(trigger)) {
            if (!entity.getCommandTags().contains(tagFor(skill.id()))) {
                continue;
            }
            if (trigger == SkillTrigger.WHEN_HURT && isCancelableSkill(skill)) {
                continue;
            }
            runSkill(world, entity, skill);
        }
    }

    public static void runAttack(LivingEntity entity, Entity target) {
        if (!(entity.getWorld() instanceof ServerWorld world)) {
            return;
        }
        attachPlayerSkills(entity);

        LivingEntity livingTarget = target instanceof LivingEntity living && isValidSkillTarget(living) && living != entity ? living : null;
        for (CustomSkillDefinition skill : skillsFor(SkillTrigger.WHEN_ATTACKS)) {
            if (!entity.getCommandTags().contains(tagFor(skill.id()))) {
                continue;
            }
            runSkill(world, entity, skill, null, 0.0F, livingTarget);
        }
    }

    public static void runRangedHit(ServerPlayerEntity player, LivingEntity target) {
        if (!(player.getWorld() instanceof ServerWorld world) || !isValidSkillTarget(target) || target == player) {
            return;
        }
        attachPlayerSkills(player);

        for (CustomSkillDefinition skill : skillsFor(SkillTrigger.ON_RANGED_HIT)) {
            if (!player.getCommandTags().contains(tagFor(skill.id()))) {
                continue;
            }
            runSkill(world, player, skill, null, 0.0F, target);
        }
    }

    public static void runSpellHit(ServerPlayerEntity player, LivingEntity target, SpellInfo spellInfo) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        attachPlayerSkills(player);

        boolean previous = SPELL_HIT_PROC_CONTEXT.get();
        SpellInfo previousSpellInfo = SPELL_HIT_PROC_SPELL.get();
        SPELL_HIT_PROC_CONTEXT.set(true);
        SPELL_HIT_PROC_SPELL.set(spellInfo);
        try {
            for (CustomSkillDefinition skill : skillsFor(SkillTrigger.WHEN_ATTACKS)) {
                if (!player.getCommandTags().contains(tagFor(skill.id()))) {
                    continue;
                }
                runSkill(world, player, skill, null, 0.0F, target);
            }
        } finally {
            SPELL_HIT_PROC_CONTEXT.set(previous);
            if (previousSpellInfo == null) {
                SPELL_HIT_PROC_SPELL.remove();
            } else {
                SPELL_HIT_PROC_SPELL.set(previousSpellInfo);
            }
        }
    }

    public static boolean canAttemptSpellCast(ServerPlayerEntity player, Identifier spellId) {
        attachPlayerSkills(player);
        return spellCastSkillReady(player, spellId, true);
    }

    public static boolean canShowSpellReady(PlayerEntity player, Identifier spellId) {
        return spellCastSkillReady(player, spellId, false);
    }

    private static boolean spellCastSkillReady(PlayerEntity player, Identifier spellId, boolean requireAttachedTag) {
        if (!spellConditionsPass(player, spellId)) {
            return false;
        }

        boolean hasSkillForSpell = false;
        for (CustomSkillDefinition skill : skillsFor(SkillTrigger.WHEN_SPELL_CAST)) {
            if (requireAttachedTag && !player.getCommandTags().contains(tagFor(skill.id()))) {
                continue;
            }
            JsonObject json = skill.json();
            if (!json.has("spell") || !spellId.toString().equals(json.get("spell").getAsString())) {
                continue;
            }
            hasSkillForSpell = true;
            if (skillPreconditionsPass(player, json, false) && conditionsPass(player, json)) {
                return true;
            }
        }
        return !hasSkillForSpell;
    }

    private static boolean spellConditionsPass(PlayerEntity player, Identifier spellId) {
        JsonElement conditions = SPELL_CONDITIONS.get(spellId);
        return conditions == null || conditionsPass(player, conditions);
    }

    public static void runSpellCast(ServerPlayerEntity player, Identifier spellId, List<Entity> targets) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        if (!spellConditionsPass(player, spellId)) {
            return;
        }
        attachPlayerSkills(player);

        LivingEntity target = firstLivingTarget(player, targets);
        for (CustomSkillDefinition skill : skillsFor(SkillTrigger.WHEN_SPELL_CAST)) {
            if (!player.getCommandTags().contains(tagFor(skill.id()))) {
                continue;
            }
            JsonObject json = skill.json();
            if (!json.has("spell") || !spellId.toString().equals(json.get("spell").getAsString())) {
                continue;
            }
            runSkill(world, player, skill, null, 0.0F, target);
        }
    }

    public static void markSuccessfulPlayerAttack(ServerPlayerEntity player) {
        SUCCESSFUL_PLAYER_ATTACK_TICKS.put(player.getUuid(), player.getWorld().getTime());
    }

    private static LivingEntity firstLivingTarget(LivingEntity owner, List<Entity> targets) {
        for (Entity target : targets) {
            if (target instanceof LivingEntity living && living != owner && isValidSkillTarget(living)) {
                return living;
            }
        }
        return null;
    }

    public static boolean debugCastSpell(ServerPlayerEntity player, String mode, Identifier spellId) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return false;
        }

        JsonObject action = new JsonObject();
        action.addProperty("spell", spellId.toString());
        action.addProperty("debug", true);
        action.addProperty("play_release_effects", true);

        switch (mode) {
            case "spell" -> {
                action.addProperty("target", "target");
                action.addProperty("target_range", 48.0D);
                spellEngineSpellAction(world, player, action);
                return true;
            }
            case "impact" -> {
                action.addProperty("target", "living");
                action.addProperty("radius", 6.0D);
                action.addProperty("impact_min_power", 0.0F);
                spellEngineImpactAction(world, player, player, action);
                return true;
            }
            case "rain" -> {
                action.addProperty("target", "living");
                action.addProperty("radius", 8.0D);
                action.addProperty("acquire_range", 48.0D);
                action.addProperty("spawn", "origin");
                action.addProperty("target_y_scale", 0.65D);
                action.addProperty("spawn_y_offset", 0.8D);
                action.addProperty("launch_radius", 0.35D);
                action.addProperty("count", 1);
                spellEngineRainAction(world, player, player, action);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public static void queueFailedPlayerAttack(ServerPlayerEntity player) {
        attachPlayerSkills(player);
        PENDING_FAILED_ATTACK_TICKS.put(player.getUuid(), player.getWorld().getTime());
    }

    public static void runHurt(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity.getWorld() instanceof ServerWorld world) || applyingReflectedDamage) {
            return;
        }
        attachPlayerSkills(entity);

        LivingEntity sourceTarget = sourceAttackerTarget(source, entity);
        for (CustomSkillDefinition skill : skillsFor(SkillTrigger.WHEN_HURT)) {
            if (!entity.getCommandTags().contains(tagFor(skill.id())) || isCancelableSkill(skill)) {
                continue;
            }
            runSkill(world, entity, skill, source, amount, sourceTarget);
        }
    }

    public static boolean runCancelableHurt(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity.getWorld() instanceof ServerWorld world)) {
            return false;
        }
        attachPlayerSkills(entity);

        if (consumeParryStance(world, entity, source, amount)) {
            return true;
        }

        LivingEntity sourceTarget = sourceAttackerTarget(source, entity);
        boolean cancelled = false;
        for (CustomSkillDefinition skill : skillsFor(SkillTrigger.WHEN_HURT)) {
            if (!isCancelableSkill(skill) || !entity.getCommandTags().contains(tagFor(skill.id()))) {
                continue;
            }
            if (runSkill(world, entity, skill, source, amount, sourceTarget)) {
                cancelled = true;
            }
        }
        return cancelled;
    }

    public static void tickTimed(ServerWorld world) {
        List<CustomSkillDefinition> timedSkills = skillsFor(SkillTrigger.TIMED);
        if (timedSkills.isEmpty()) {
            return;
        }
        long time = world.getTime();
        attachPlayerSkills(world);
        if (TIMED_SKILL_ENTITIES.isEmpty() || time % 100 == 0) {
            registerLoadedTimedSkillEntities(world, timedSkills);
        }
        if (TIMED_SKILL_ENTITIES.isEmpty()) {
            return;
        }

        for (UUID entityId : TIMED_SKILL_ENTITIES) {
            Entity entity = world.getEntity(entityId);
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
                continue;
            }
            for (CustomSkillDefinition skill : timedSkills) {
                if (!living.getCommandTags().contains(tagFor(skill.id()))) {
                    continue;
                }
                if (time % Math.max(1, skill.intervalTicks()) != 0) {
                    continue;
                }
                if (!isOnCooldown(living, skill)) {
                    runSkill(world, living, skill);
                }
            }
        }
    }

    private static void registerLoadedTimedSkillEntities(ServerWorld world, List<CustomSkillDefinition> timedSkills) {
        for (Entity entity : world.iterateEntities()) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
                continue;
            }
            for (CustomSkillDefinition skill : timedSkills) {
                if (living.getCommandTags().contains(tagFor(skill.id()))) {
                    TIMED_SKILL_ENTITIES.add(living.getUuid());
                    break;
                }
            }
        }
    }

    public static void tickCooldowns(MinecraftServer server) {
        tickFailedAttacks(server);
        cleanupTimedSkillEntities(server);
        tickPendingSkills(server);
        tickScheduledActions(server);
        tickActiveStances(server);
        tickActiveAuras(server);
        tickSkillProjectiles(server);
        tickGrenadeProjectiles(server);

        GLOBAL_COOLDOWNS.entrySet().removeIf(entry -> {
            UUID entityId = entry.getKey();
            int remaining = entry.getValue() - 1;
            if (!entityExists(server, entityId)) {
                TIMED_SKILL_ENTITIES.remove(entityId);
                return true;
            }
            if (remaining <= 0) {
                return true;
            }
            GLOBAL_COOLDOWNS.put(entityId, remaining);
            return false;
        });

        SKILL_COOLDOWNS.entrySet().removeIf(entry -> {
            UUID entityId = entry.getKey();
            Map<Identifier, Integer> cooldowns = entry.getValue();
            if (!entityExists(server, entityId)) {
                TIMED_SKILL_ENTITIES.remove(entityId);
                return true;
            }
            cooldowns.entrySet().removeIf(skillEntry -> {
                int remaining = skillEntry.getValue() - 1;
                if (remaining <= 0) {
                    return true;
                }
                cooldowns.put(skillEntry.getKey(), remaining);
                return false;
            });
            return cooldowns.isEmpty();
        });
        LAST_SPELL_PROC_ROLL_TICKS.keySet().removeIf(key -> !entityExists(server, key.entityId()));
    }

    private static void cleanupTimedSkillEntities(MinecraftServer server) {
        if (TIMED_SKILL_ENTITIES.isEmpty()
                || server.getOverworld().getTime() % TIMED_SKILL_ENTITY_CLEANUP_INTERVAL_TICKS != 0) {
            return;
        }
        TIMED_SKILL_ENTITIES.removeIf(uuid -> {
            LivingEntity entity = findLivingEntity(server, uuid);
            return entity == null || !entity.isAlive();
        });
    }

    private static void tickFailedAttacks(MinecraftServer server) {
        long currentTick = server.getOverworld().getTime();
        PENDING_FAILED_ATTACK_TICKS.entrySet().removeIf(entry -> {
            UUID playerId = entry.getKey();
            long swingTick = entry.getValue();
            if (currentTick <= swingTick) {
                return false;
            }
            LivingEntity entity = findLivingEntity(server, playerId);
            if (!(entity instanceof ServerPlayerEntity player) || !player.isAlive() || !(player.getWorld() instanceof ServerWorld world)) {
                SUCCESSFUL_PLAYER_ATTACK_TICKS.remove(playerId);
                return true;
            }

            long successTick = SUCCESSFUL_PLAYER_ATTACK_TICKS.getOrDefault(playerId, Long.MIN_VALUE);
            if (successTick == swingTick) {
                return true;
            }

            ComboHandler.reset(player);
            for (CustomSkillDefinition skill : skillsFor(SkillTrigger.FAILED_ATTACK)) {
                if (!player.getCommandTags().contains(tagFor(skill.id()))) {
                    continue;
                }
                runSkill(world, player, skill);
            }
            return true;
        });

        long oldestRelevantTick = currentTick - 2L;
        SUCCESSFUL_PLAYER_ATTACK_TICKS.entrySet().removeIf(entry -> entry.getValue() < oldestRelevantTick);
    }

    public static void applyGlobalCooldown(LivingEntity entity, int ticks) {
        if (ticks <= 0) {
            return;
        }
        UUID uuid = entity.getUuid();
        GLOBAL_COOLDOWNS.merge(uuid, ticks, Math::max);
    }

    public static void cancelFor(LivingEntity entity) {
        UUID uuid = entity.getUuid();
        if (entity.getWorld() instanceof ServerWorld world) {
            StanceState stance = ACTIVE_STANCES.remove(uuid);
            if (stance != null) {
                applyActionSet(world, entity, stance.config(), "stance_end", stance.ageTicks());
            }
        } else {
            ACTIVE_STANCES.remove(uuid);
        }
        PENDING_SKILLS.keySet().removeIf(key -> key.entityId().equals(uuid));
        LAST_SPELL_PROC_ROLL_TICKS.keySet().removeIf(key -> key.entityId().equals(uuid));
        SCHEDULED_ACTIONS.entrySet().removeIf(entry -> entry.getValue().entityId().equals(uuid));
        MinecraftServer server = entity.getServer();
        SKILL_PROJECTILES.entrySet().removeIf(entry -> {
            if (!entry.getValue().ownerId().equals(uuid)) {
                return false;
            }
            if (server != null) {
                Entity projectile = findEntityDirect(server, entry.getKey());
                if (projectile != null) {
                    projectile.getCommandTags().remove(SKILL_PROJECTILE_TAG);
                }
            }
            return true;
        });
    }

    public static void onSkillProjectileHit(ProjectileEntity projectile, HitResult hitResult) {
        SkillProjectileState state = SKILL_PROJECTILES.get(projectile.getUuid());
        if (state == null || !(projectile.getWorld() instanceof ServerWorld world) || hitResult.getType() == HitResult.Type.MISS) {
            return;
        }

        LivingEntity owner = findLivingEntity(world.getServer(), state.ownerId());
        if (owner == null || !owner.isAlive()) {
            return;
        }

        Vec3d impactPos = hitResult.getPos();
        if (hitResult instanceof EntityHitResult entityHit) {
            if (!(entityHit.getEntity() instanceof LivingEntity target) || target == owner) {
                return;
            }
            consumeSkillProjectile(projectile);
            applyProjectileHitActions(world, owner, target, state.config(), "hit_enemy", impactPos);
            return;
        }

        consumeSkillProjectile(projectile);
        applyProjectileHitActions(world, owner, owner, state.config(), "hit_ground", impactPos);
    }

    public static void tickSkillProjectile(ProjectileEntity projectile) {
        SkillProjectileState state = SKILL_PROJECTILES.get(projectile.getUuid());
        if (state == null || !(projectile.getWorld() instanceof ServerWorld world) || !projectile.isAlive()) {
            return;
        }
        long worldTime = world.getTime();
        if (state.lastWorldTime() == worldTime) {
            return;
        }

        int ageTicks = state.ageTicks() + 1;
        spawnProjectileTrails(world, projectile, state.config(), ageTicks);
        SkillProjectileState updatedState = state.withTick(ageTicks, worldTime);
        SKILL_PROJECTILES.put(projectile.getUuid(), updatedState);
        applyGroundedProjectileImpact(world, projectile, updatedState);
    }

    private static boolean runSkill(ServerWorld world, LivingEntity entity, CustomSkillDefinition skill) {
        return runSkill(world, entity, skill, null);
    }

    private static boolean runSkill(ServerWorld world, LivingEntity entity, CustomSkillDefinition skill, DamageSource source) {
        return runSkill(world, entity, skill, source, 0.0F);
    }

    private static boolean runSkill(ServerWorld world, LivingEntity entity, CustomSkillDefinition skill, DamageSource source, float sourceDamageAmount) {
        return runSkill(world, entity, skill, source, sourceDamageAmount, null);
    }

    private static boolean runSkill(ServerWorld world, LivingEntity entity, CustomSkillDefinition skill, DamageSource source, float sourceDamageAmount, LivingEntity target) {
        return withTargetContext(entity, target, () -> runSkillInContext(world, entity, skill, source, sourceDamageAmount));
    }

    private static boolean runSkillInContext(ServerWorld world, LivingEntity entity, CustomSkillDefinition skill, DamageSource source, float sourceDamageAmount) {
        if (isOnCooldown(entity, skill)) {
            debugSkill(skill.json(), () -> "Skill " + skill.id() + " blocked by cooldown for " + entity.getName().getString());
            return false;
        }
        JsonObject json = skill.json();
        if (skill.predicate() != null && !testPredicate(world, entity, skill.predicate())) {
            debugSkill(skill.json(), () -> "Skill " + skill.id() + " blocked by predicate for " + entity.getName().getString());
            return false;
        }

        if (!conditionsPass(entity, json)) {
            debugSkill(json, () -> "Skill " + skill.id() + " blocked by conditions for " + entity.getName().getString());
            return false;
        }
        if (!skillPreconditionsPass(entity, json, false)) {
            debugSkill(json, () -> "Skill " + skill.id() + " blocked by trigger preconditions for " + entity.getName().getString());
            return false;
        }
        if (!spellProcIntervalReady(world, entity, skill, json)) {
            debugSkill(json, () -> "Skill " + skill.id() + " blocked by spell proc interval for " + entity.getName().getString());
            return false;
        }
        float chance = effectiveSkillChance(entity, skill);
        if (world.random.nextFloat() > chance) {
            debugSkill(skill.json(), () -> "Skill " + skill.id() + " skipped by chance for " + entity.getName().getString());
            return false;
        }
        float procCoefficient = procCoefficient(entity, json);
        int comboCount = currentComboCount(entity);
        return withProcCoefficient(procCoefficient, () -> withComboCount(comboCount, () -> {
            if (json.has("telegraph")) {
                return scheduleTelegraph(world, entity, skill, source, sourceDamageAmount, json.get("telegraph"));
            }
            if (!isStanceSkill(json) && hasDelayedActionList(json, "actions")) {
                return scheduleDelayedActions(world, entity, skill, source, sourceDamageAmount, json.get("actions"));
            }

            return executeSkillBody(world, entity, skill, source, sourceDamageAmount);
        }));
    }

    private static boolean withProcCoefficient(float procCoefficient, Supplier<Boolean> action) {
        float previous = ACTIVE_PROC_COEFFICIENT.get();
        ACTIVE_PROC_COEFFICIENT.set(Math.max(0.0F, procCoefficient));
        try {
            return action.get();
        } finally {
            ACTIVE_PROC_COEFFICIENT.set(previous);
        }
    }

    private static boolean withComboCount(int comboCount, Supplier<Boolean> action) {
        int previous = ACTIVE_COMBO_COUNT.get();
        ACTIVE_COMBO_COUNT.set(Math.max(0, comboCount));
        try {
            return action.get();
        } finally {
            ACTIVE_COMBO_COUNT.set(previous);
        }
    }

    private static int activeComboCount(LivingEntity entity) {
        int activeCombo = ACTIVE_COMBO_COUNT.get();
        return activeCombo >= 0 ? activeCombo : currentComboCount(entity);
    }

    private static int currentComboCount(LivingEntity entity) {
        return entity instanceof PlayerEntity player ? ComboHandler.getCombo(player) : 0;
    }

    private static boolean withTargetContext(LivingEntity owner, LivingEntity target, Supplier<Boolean> action) {
        if (target == null || !isValidSkillTarget(target) || target == owner) {
            return action.get();
        }

        SkillTargetContext previous = TARGET_CONTEXT.get();
        TARGET_CONTEXT.set(new SkillTargetContext(owner.getUuid(), target.getUuid()));
        try {
            return action.get();
        } finally {
            if (previous == null) {
                TARGET_CONTEXT.remove();
            } else {
                TARGET_CONTEXT.set(previous);
            }
        }
    }

    private static boolean executeSkillBody(ServerWorld world, LivingEntity entity, CustomSkillDefinition skill, DamageSource source, float sourceDamageAmount) {
        return executeSkillBody(world, entity, skill, source, sourceDamageAmount, null);
    }

    private static boolean executeSkillBody(ServerWorld world, LivingEntity entity, CustomSkillDefinition skill, DamageSource source, float sourceDamageAmount, Vec3d lockedDirection) {
        JsonObject json = skill.json();
        if (!skillPreconditionsPass(entity, json, true)) {
            return false;
        }
        Vec3d bodyDirection = bodyDirection(entity, json, lockedDirection);

        if (isStanceSkill(json)) {
            JsonObject stanceJson = json.getAsJsonObject("stance");
            boolean enteredStance = enterStance(world, entity, skill, stanceJson);
            if (enteredStance && !cooldownOnStart(skill)) {
                applyCooldowns(entity, skill);
            }
            return enteredStance;
        }
        if (json.has("target_attacker") && json.get("target_attacker").getAsBoolean()) {
            targetAttacker(entity, source);
        }
        if (json.has("dodge")) {
            dodge(world, entity, source, json.getAsJsonObject("dodge"));
        }
        if (json.has("reflect_damage")) {
            if (!reflectDamage(world, entity, source, sourceDamageAmount, json.getAsJsonObject("reflect_damage"))) {
                return false;
            }
        }
        if (json.has("actions")) {
            if (requiresReflectDamage(json, "actions") && !canReflectDamage(entity, source, sourceDamageAmount)) {
                return false;
            }
            if (hasDelayedActionList(json, "actions")) {
                if (!scheduleStandaloneDelayedActions(world, entity, json.get("actions"), source, sourceDamageAmount, lockedTelegraphDirection(entity, json))) {
                    return false;
                }
            } else {
                applyActionList(world, entity, json, "actions", true, 0, source, sourceDamageAmount, bodyDirection);
            }
        }
        if (json.has("effects")) {
            for (JsonElement element : json.getAsJsonArray("effects")) {
                applyEffect(entity, element.getAsJsonObject());
            }
        }
        if (json.has("particles")) {
            for (JsonElement element : json.getAsJsonArray("particles")) {
                spawnParticles(world, entity, element.getAsJsonObject());
            }
        }
        if (json.has("sounds")) {
            for (JsonElement element : json.getAsJsonArray("sounds")) {
                playSound(world, entity, element.getAsJsonObject());
            }
        }
        if (json.has("functions")) {
            for (JsonElement element : json.getAsJsonArray("functions")) {
                runCommand(world, entity, "function " + element.getAsString());
            }
        }
        if (json.has("commands")) {
            for (JsonElement element : json.getAsJsonArray("commands")) {
                runCommand(world, entity, element.getAsString());
            }
        }
        if (!cooldownOnStart(skill)) {
            applyCooldowns(entity, skill);
        }
        return true;
    }

    private static Vec3d bodyDirection(LivingEntity entity, JsonObject json, Vec3d lockedDirection) {
        if (json.has("body_direction") && "rotation".equalsIgnoreCase(json.get("body_direction").getAsString())) {
            return horizontalForward(entity);
        }
        return lockedDirection;
    }

    private static boolean isStanceSkill(JsonObject json) {
        return json.has("stance");
    }

    private static boolean cooldownOnStart(CustomSkillDefinition skill) {
        JsonObject json = skill.json();
        return json.has("cooldown_on_start") && json.get("cooldown_on_start").getAsBoolean();
    }

    private static boolean conditionsPass(LivingEntity entity, JsonObject json) {
        if (!json.has("conditions")) {
            return true;
        }
        return conditionsPass(entity, json.get("conditions"));
    }

    private static boolean conditionsPass(LivingEntity entity, JsonElement conditions) {
        if (conditions == null || conditions.isJsonNull()) {
            return false;
        }
        if (conditions.isJsonObject()) {
            return conditionPasses(entity, conditions.getAsJsonObject());
        }
        if (!conditions.isJsonArray()) {
            return false;
        }
        for (JsonElement element : conditions.getAsJsonArray()) {
            if (!element.isJsonObject() || !conditionPasses(entity, element.getAsJsonObject())) {
                return false;
            }
        }
        return true;
    }

    private static boolean conditionPasses(LivingEntity entity, JsonObject condition) {
        LivingEntity conditionEntity = conditionEntity(entity, condition);
        if (conditionEntity == null) {
            return false;
        }

        String type = condition.has("type") ? condition.get("type").getAsString() : "";
        return switch (type) {
            case "effect", "status_effect" -> effectConditionPasses(conditionEntity, condition);
            case "combo" -> comboConditionPasses(conditionEntity, condition);
            case "weapon_gem" -> weaponGemConditionPasses(conditionEntity, condition);
            case "health" -> healthConditionPasses(conditionEntity, condition);
            case "nearby_entity", "entity_nearby" -> nearbyEntityConditionPasses(conditionEntity, condition);
            default -> false;
        };
    }

    private static LivingEntity conditionEntity(LivingEntity entity, JsonObject condition) {
        String target = condition.has("target") ? condition.get("target").getAsString() : "self";
        return switch (target) {
            case "self", "owner" -> entity;
            case "target" -> currentTarget(entity);
            default -> null;
        };
    }

    private static boolean spellProcIntervalReady(ServerWorld world, LivingEntity entity, CustomSkillDefinition skill, JsonObject json) {
        if (!SPELL_HIT_PROC_CONTEXT.get() || !json.has("proc_coefficient")) {
            return true;
        }
        JsonElement element = json.get("proc_coefficient");
        if (!element.isJsonObject()) {
            return true;
        }

        JsonObject coefficient = element.getAsJsonObject();
        if (!coefficient.has("spell_min_interval_ticks")) {
            return true;
        }

        int intervalTicks = Math.max(0, coefficient.get("spell_min_interval_ticks").getAsInt());
        if (intervalTicks <= 0) {
            return true;
        }

        PendingSkillKey key = new PendingSkillKey(entity.getUuid(), skill.id());
        long time = world.getTime();
        long previous = LAST_SPELL_PROC_ROLL_TICKS.getOrDefault(key, Long.MIN_VALUE);
        if (previous != Long.MIN_VALUE && time - previous < intervalTicks) {
            return false;
        }

        LAST_SPELL_PROC_ROLL_TICKS.put(key, time);
        return true;
    }

    private static float effectiveSkillChance(LivingEntity entity, CustomSkillDefinition skill) {
        float chance = skill.chance();
        JsonObject json = skill.json();
        if (json.has("combo_minimum") || json.has("combo_increase_proc%")) {
            if (!(entity instanceof ServerPlayerEntity player)) {
                chance = json.has("combo_minimum") ? 0.0F : chance;
            } else {
                int combo = ComboHandler.getCombo(player);
                int comboMinimum = json.has("combo_minimum") ? Math.max(0, json.get("combo_minimum").getAsInt()) : 0;
                if (combo < comboMinimum) {
                    chance = 0.0F;
                } else if (json.has("combo_increase_proc%")) {
                    float comboIncrease = Math.max(0.0F, json.get("combo_increase_proc%").getAsFloat());
                    comboIncrease *= spellComboProcMultiplier(json);
                    chance += combo * comboIncrease;
                }
            }
        }

        chance *= procCoefficient(entity, json);
        return Math.min(1.0F, Math.max(0.0F, chance));
    }

    private static float procCoefficient(LivingEntity entity, JsonObject json) {
        if (!json.has("proc_coefficient")) {
            return 1.0F;
        }

        JsonElement element = json.get("proc_coefficient");
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return Math.max(0.0F, element.getAsFloat());
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean() && !element.getAsBoolean()) {
            return 1.0F;
        }
        if (!element.isJsonObject()) {
            return 1.0F;
        }

        JsonObject coefficient = element.getAsJsonObject();
        String type = coefficient.has("type") ? coefficient.get("type").getAsString() : "attack_speed";
        if (!"attack_speed".equals(type)) {
            return 1.0F;
        }

        float min = coefficient.has("min") ? Math.max(0.0F, coefficient.get("min").getAsFloat()) : 0.75F;
        float max = coefficient.has("max") ? Math.max(min, coefficient.get("max").getAsFloat()) : 2.0F;
        if (SPELL_HIT_PROC_CONTEXT.get()) {
            return spellProcCoefficient(coefficient, min, max);
        }

        double attackSpeed = heldItemProcSpeed(entity.getMainHandStack(), coefficient);
        if (attackSpeed <= 0.0D) {
            return max;
        }

        double baseline = coefficient.has("baseline_attack_speed") ? coefficient.get("baseline_attack_speed").getAsDouble() : 1.6D;
        float multiplier = (float) (baseline / attackSpeed);
        return Math.max(min, Math.min(max, multiplier));
    }

    private static float spellProcCoefficient(JsonObject coefficient, float min, float max) {
        SpellInfo spellInfo = SPELL_HIT_PROC_SPELL.get();
        if (spellInfo == null || spellInfo.spell() == null) {
            return 1.0F;
        }

        float multiplier = spellPowerCoefficient(spellInfo.spell());
        return Math.max(min, Math.min(max, multiplier));
    }

    private static float spellPowerCoefficient(Spell spell) {
        if (spell.impact == null || spell.impact.length == 0) {
            return 1.0F;
        }

        float coefficient = 0.0F;
        for (Spell.Impact impact : spell.impact) {
            if (impact == null || impact.action == null || impact.action.damage == null) {
                continue;
            }
            coefficient = Math.max(coefficient, impact.action.damage.spell_power_coefficient);
        }
        return coefficient > 0.0F ? coefficient : 1.0F;
    }

    private static float spellComboProcMultiplier(JsonObject json) {
        if (!SPELL_HIT_PROC_CONTEXT.get() || !json.has("proc_coefficient")) {
            return 1.0F;
        }

        JsonElement element = json.get("proc_coefficient");
        if (!element.isJsonObject()) {
            return 1.0F;
        }
        JsonObject coefficient = element.getAsJsonObject();
        return coefficient.has("spell_coef") ? Math.max(0.0F, coefficient.get("spell_coef").getAsFloat()) : 1.0F;
    }

    private static double heldItemProcSpeed(ItemStack stack, JsonObject coefficient) {
        if (stack.getItem() instanceof RangedWeaponItem) {
            return heldRangedWeaponDrawSpeed(stack, coefficient);
        }
        return heldItemAttackSpeed(stack);
    }

    private static double heldRangedWeaponDrawSpeed(ItemStack stack, JsonObject coefficient) {
        double drawTicks = coefficient.has("baseline_draw_ticks") ? coefficient.get("baseline_draw_ticks").getAsDouble() : 20.0D;
        if (stack.getItem() instanceof CrossbowItem) {
            drawTicks = coefficient.has("crossbow_draw_ticks") ? coefficient.get("crossbow_draw_ticks").getAsDouble() : 25.0D;
        }
        if (drawTicks <= 0.0D) {
            return VANILLA_ATTACK_SPEED;
        }

        double drawSpeed = heldItemDrawSpeed(stack);
        return (20.0D / drawTicks) * Math.max(0.01D, drawSpeed);
    }

    private static double heldItemDrawSpeed(ItemStack stack) {
        EntityAttribute attribute = Registries.ATTRIBUTE.getOrEmpty(ZENITH_DRAW_SPEED).orElse(null);
        if (attribute == null) {
            return 1.0D;
        }
        return heldItemAttributeValue(stack, attribute, 1.0D);
    }

    private static double heldItemAttackSpeed(ItemStack stack) {
        return heldItemAttributeValue(stack, EntityAttributes.GENERIC_ATTACK_SPEED, VANILLA_ATTACK_SPEED);
    }

    private static double heldItemAttributeValue(ItemStack stack, EntityAttribute attribute, double baseValue) {
        Multimap<EntityAttribute, EntityAttributeModifier> modifiers = stack.getAttributeModifiers(EquipmentSlot.MAINHAND);
        if (!modifiers.containsKey(attribute)) {
            return baseValue;
        }

        double value = baseValue;
        for (EntityAttributeModifier modifier : modifiers.get(attribute)) {
            if (modifier.getOperation() == EntityAttributeModifier.Operation.ADDITION) {
                value += modifier.getValue();
            }
        }
        for (EntityAttributeModifier modifier : modifiers.get(attribute)) {
            if (modifier.getOperation() == EntityAttributeModifier.Operation.MULTIPLY_BASE) {
                value += baseValue * modifier.getValue();
            }
        }
        for (EntityAttributeModifier modifier : modifiers.get(attribute)) {
            if (modifier.getOperation() == EntityAttributeModifier.Operation.MULTIPLY_TOTAL) {
                value *= 1.0D + modifier.getValue();
            }
        }
        return value;
    }

    private static boolean weaponGemConditionPasses(LivingEntity entity, JsonObject condition) {
        if (!(entity instanceof PlayerEntity player)) {
            return false;
        }

        ItemStack stack = switch (condition.has("slot") ? condition.get("slot").getAsString() : "mainhand") {
            case "offhand" -> player.getOffHandStack();
            default -> player.getMainHandStack();
        };
        if (stack.isEmpty() || !stack.hasNbt()) {
            return false;
        }

        NbtCompound stackNbt = stack.getNbt();
        if (stackNbt == null || !stackNbt.contains("Gems", NbtElement.LIST_TYPE)) {
            return false;
        }

        String requiredId = firstString(condition, "id", "gem_id");
        String requiredType = firstString(condition, "gem_type", "type_id", "gem");
        int requiredTier = firstInt(condition, -1, "gem_tier", "tier");
        NbtList gems = stackNbt.getList("Gems", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < gems.size(); i++) {
            NbtCompound gemNbt = gems.getCompound(i);
            String gemId = gemNbt.getString("id");
            if (gemId.isEmpty()) {
                continue;
            }
            if (requiredId != null && !requiredId.equals(gemId)) {
                continue;
            }
            if (requiredType != null && !requiredType.equals(smitherzGemType(gemId))) {
                continue;
            }
            if (requiredTier >= 0 && requiredTier != smitherzGemTier(gemId)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private static String firstString(JsonObject json, String... keys) {
        for (String key : keys) {
            if (json.has(key)) {
                return json.get(key).getAsString();
            }
        }
        return null;
    }

    private static int firstInt(JsonObject json, int fallback, String... keys) {
        for (String key : keys) {
            if (json.has(key)) {
                return json.get(key).getAsInt();
            }
        }
        return fallback;
    }

    private static String smitherzGemType(String itemId) {
        int colon = itemId.indexOf(':');
        String path = colon >= 0 ? itemId.substring(colon + 1) : itemId;
        if (!path.startsWith("t")) {
            return path;
        }

        int underscore = path.indexOf('_');
        return underscore > 0 && underscore + 1 < path.length() ? path.substring(underscore + 1) : path;
    }

    private static int smitherzGemTier(String itemId) {
        int colon = itemId.indexOf(':');
        String path = colon >= 0 ? itemId.substring(colon + 1) : itemId;
        if (!path.startsWith("t")) {
            return -1;
        }

        int underscore = path.indexOf('_');
        if (underscore <= 1) {
            return -1;
        }
        try {
            return Integer.parseInt(path.substring(1, underscore));
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private static boolean effectConditionPasses(LivingEntity entity, JsonObject condition) {
        if (!condition.has("id")) {
            return false;
        }
        Identifier effectId = Identifier.tryParse(condition.get("id").getAsString());
        if (effectId == null || !Registries.STATUS_EFFECT.containsId(effectId)) {
            return false;
        }
        StatusEffect effect = Registries.STATUS_EFFECT.get(effectId);
        StatusEffectInstance instance = entity.getStatusEffect(effect);
        if (instance == null) {
            return false;
        }

        int level = instance.getAmplifier() + 1;
        int minLevel = effectLevelBound(condition, "min_level", "min", Integer.MIN_VALUE);
        int maxLevel = effectLevelBound(condition, "max_level", "max", Integer.MAX_VALUE);
        return level >= minLevel && level <= maxLevel;
    }

    private static int effectLevelBound(JsonObject condition, String directKey, String amountKey, int fallback) {
        if (condition.has(directKey)) {
            return condition.get(directKey).getAsInt();
        }
        if (!condition.has("amount") || !condition.get("amount").isJsonObject()) {
            return fallback;
        }
        JsonObject amount = condition.getAsJsonObject("amount");
        return amount.has(amountKey) ? amount.get(amountKey).getAsInt() : fallback;
    }

    private static boolean comboConditionPasses(LivingEntity entity, JsonObject condition) {
        if (!(entity instanceof PlayerEntity)) {
            return false;
        }

        int combo = activeComboCount(entity);
        int min = condition.has("min") ? condition.get("min").getAsInt()
                : condition.has("min_combo") ? condition.get("min_combo").getAsInt()
                : condition.has("min_count") ? condition.get("min_count").getAsInt() : Integer.MIN_VALUE;
        int max = condition.has("max") ? condition.get("max").getAsInt()
                : condition.has("max_combo") ? condition.get("max_combo").getAsInt()
                : condition.has("max_count") ? condition.get("max_count").getAsInt() : Integer.MAX_VALUE;
        return combo >= min && combo <= max;
    }

    private static boolean healthConditionPasses(LivingEntity entity, JsonObject condition) {
        double health = entity.getHealth();
        double min = condition.has("min") ? condition.get("min").getAsDouble()
                : condition.has("min_health") ? condition.get("min_health").getAsDouble() : Double.NEGATIVE_INFINITY;
        double max = condition.has("max") ? condition.get("max").getAsDouble()
                : condition.has("max_health") ? condition.get("max_health").getAsDouble() : Double.POSITIVE_INFINITY;
        return health >= min && health <= max;
    }

    private static boolean nearbyEntityConditionPasses(LivingEntity entity, JsonObject condition) {
        if (!(entity.getWorld() instanceof ServerWorld world)) {
            return false;
        }

        double minDistance = condition.has("min_distance") ? Math.max(0.0D, condition.get("min_distance").getAsDouble()) : 0.0D;
        double maxDistance = condition.has("max_distance") ? Math.max(0.0D, condition.get("max_distance").getAsDouble())
                : condition.has("radius") ? Math.max(0.0D, condition.get("radius").getAsDouble()) : 16.0D;
        int minCount = condition.has("min_count") ? Math.max(1, condition.get("min_count").getAsInt()) : 1;
        boolean includeSelf = condition.has("include_self") && condition.get("include_self").getAsBoolean();

        double minDistanceSquared = minDistance * minDistance;
        double maxDistanceSquared = maxDistance * maxDistance;
        int count = 0;
        if (includeSelf && nearbyConditionCandidatePasses(entity, entity, condition, minDistanceSquared, maxDistanceSquared)) {
            count++;
            if (count >= minCount) {
                return true;
            }
        }

        for (LivingEntity candidate : nearbyTargets(world, entity, "living", maxDistance)) {
            if (!nearbyConditionCandidatePasses(entity, candidate, condition, minDistanceSquared, maxDistanceSquared)) {
                continue;
            }
            count++;
            if (count >= minCount) {
                return true;
            }
        }
        return false;
    }

    private static boolean nearbyConditionCandidatePasses(LivingEntity origin, LivingEntity candidate, JsonObject condition, double minDistanceSquared, double maxDistanceSquared) {
        if (!candidate.isAlive()) {
            return false;
        }

        double distanceSquared = candidate.squaredDistanceTo(origin);
        return distanceSquared >= minDistanceSquared
                && distanceSquared <= maxDistanceSquared
                && passesRequiredEntityTypes(candidate, condition)
                && passesExcludedEntityTypes(candidate, condition)
                && passesRequiredTags(candidate, condition)
                && passesExcludedTags(candidate, condition);
    }

    private static boolean skillPreconditionsPass(LivingEntity entity, JsonObject json, boolean releasingSkill) {
        if (json.has("requires_living_target") && json.get("requires_living_target").getAsBoolean() && currentTarget(entity) == null) {
            debugSkill(json, () -> "Skill precondition failed: no living target for " + entity.getName().getString());
            return false;
        }

        double targetRange = skillTargetRange(json, releasingSkill);
        double minTargetRange = skillMinTargetRange(json, releasingSkill);
        if (targetRange > 0.0D || minTargetRange > 0.0D) {
            LivingEntity target = currentTarget(entity);
            if (target == null) {
                debugSkill(json, () -> "Skill precondition failed: no range target for " + entity.getName().getString());
                return false;
            }
            double distanceSquared = entity.squaredDistanceTo(target);
            if (targetRange > 0.0D && distanceSquared > targetRange * targetRange) {
                debugSkill(json, () -> "Skill precondition failed: target too far for " + entity.getName().getString()
                        + " distance=" + Math.sqrt(distanceSquared) + " max=" + targetRange);
                return false;
            }
            if (minTargetRange > 0.0D && distanceSquared < minTargetRange * minTargetRange) {
                debugSkill(json, () -> "Skill precondition failed: target too close for " + entity.getName().getString()
                        + " distance=" + Math.sqrt(distanceSquared) + " min=" + minTargetRange);
                return false;
            }
        }

        if (json.has("require_unarmed") && json.get("require_unarmed").getAsBoolean() && !isUnarmed(entity)) {
            return false;
        }
        if (json.has("require_mainhand") && !isHoldingMainhand(entity, json.get("require_mainhand"))) {
            return false;
        }

        return true;
    }

    private static double skillTargetRange(JsonObject json, boolean releasingSkill) {
        if (SPELL_HIT_PROC_CONTEXT.get() && !releasingSkill) {
            return json.has("spell_trigger_range") ? json.get("spell_trigger_range").getAsDouble() : 0.0D;
        }
        if (releasingSkill && json.has("hit_range")) {
            return json.get("hit_range").getAsDouble();
        }
        if (!releasingSkill && json.has("trigger_range")) {
            return json.get("trigger_range").getAsDouble();
        }
        if (json.has("target_range")) {
            return json.get("target_range").getAsDouble();
        }
        if (releasingSkill && json.has("trigger_range")) {
            return json.get("trigger_range").getAsDouble();
        }
        return 0.0D;
    }

    private static double skillMinTargetRange(JsonObject json, boolean releasingSkill) {
        if (SPELL_HIT_PROC_CONTEXT.get() && !releasingSkill) {
            return json.has("spell_min_trigger_range") ? json.get("spell_min_trigger_range").getAsDouble() : 0.0D;
        }
        if (releasingSkill && json.has("min_hit_range")) {
            return json.get("min_hit_range").getAsDouble();
        }
        if (!releasingSkill && json.has("min_trigger_range")) {
            return json.get("min_trigger_range").getAsDouble();
        }
        if (json.has("min_target_range")) {
            return json.get("min_target_range").getAsDouble();
        }
        if (releasingSkill && json.has("min_trigger_range")) {
            return json.get("min_trigger_range").getAsDouble();
        }
        return 0.0D;
    }

    private static LivingEntity currentTarget(LivingEntity entity) {
        LivingEntity contextTarget = currentContextTarget(entity);
        if (contextTarget != null) {
            return contextTarget;
        }

        if (!(entity instanceof MobEntity mob)) {
            return entity instanceof PlayerEntity ? nearestMobTarget(entity, 32.0D) : null;
        }

        LivingEntity target = mob.getTarget();
        if (isValidSkillTarget(target)) {
            return target;
        }
        if (target instanceof PlayerEntity) {
            mob.setTarget(null);
        }

        LivingEntity fallback = nearestPlayerTarget(entity);
        if (fallback != null) {
            mob.setTarget(fallback);
        }
        return fallback;
    }

    private static LivingEntity currentContextTarget(LivingEntity entity) {
        SkillTargetContext context = TARGET_CONTEXT.get();
        if (context == null || !context.ownerId().equals(entity.getUuid()) || context.targetId() == null || entity.getServer() == null) {
            return null;
        }

        LivingEntity target = findLivingEntity(entity.getServer(), context.targetId());
        if (target == entity || !isValidSkillTarget(target)) {
            return null;
        }
        return target;
    }

    private static UUID currentContextTargetId(LivingEntity entity) {
        SkillTargetContext context = TARGET_CONTEXT.get();
        if (context == null || !context.ownerId().equals(entity.getUuid())) {
            return null;
        }
        return context.targetId();
    }

    private static LivingEntity sourceAttackerTarget(DamageSource source, LivingEntity owner) {
        if (source == null || !(source.getAttacker() instanceof LivingEntity attacker) || attacker == owner || !isValidSkillTarget(attacker)) {
            return null;
        }
        return attacker;
    }

    private static boolean isValidSkillTarget(LivingEntity target) {
        if (target == null || !target.isAlive()) {
            return false;
        }
        return !(target instanceof ServerPlayerEntity player) || (!player.isCreative() && !player.isSpectator());
    }

    private static LivingEntity nearestPlayerTarget(LivingEntity entity) {
        return nearestPlayerTarget(entity, 32.0D);
    }

    private static LivingEntity nearestPlayerTarget(LivingEntity entity, double baseRange) {
        if (!(entity.getWorld() instanceof ServerWorld world)) {
            return null;
        }

        double range = baseRange;
        EntityAttributeInstance followRange = entity.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE);
        if (followRange != null) {
            range = Math.max(range, followRange.getValue());
        }
        double rangeSquared = range * range;
        ServerPlayerEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!player.isAlive() || player.isSpectator() || player.isCreative()) {
                continue;
            }
            double distance = entity.squaredDistanceTo(player);
            if (distance > rangeSquared || distance >= nearestDistance) {
                continue;
            }
            nearest = player;
            nearestDistance = distance;
        }
        return nearest;
    }

    private static LivingEntity nearestMobTarget(LivingEntity entity, double range) {
        if (!(entity.getWorld() instanceof ServerWorld world)) {
            return null;
        }

        double rangeSquared = range * range;
        Box box = entity.getBoundingBox().expand(range);
        return world.getEntitiesByClass(MobEntity.class, box, target ->
                        target != entity
                                && target.isAlive()
                                && target.squaredDistanceTo(entity) <= rangeSquared)
                .stream()
                .min(Comparator.comparingDouble(entity::squaredDistanceTo))
                .orElse(null);
    }

    private static boolean isUnarmed(LivingEntity entity) {
        ItemStack mainHand = entity.getMainHandStack();
        ItemStack offHand = entity.getOffHandStack();
        return mainHand.isEmpty() && offHand.isEmpty();
    }

    private static boolean isHoldingMainhand(LivingEntity entity, JsonElement itemIds) {
        ItemStack mainHand = entity.getMainHandStack();
        if (mainHand.isEmpty()) {
            return false;
        }

        if (itemIds.isJsonPrimitive()) {
            return matchesMainhandRequirement(mainHand, itemIds.getAsString());
        }
        for (JsonElement itemId : itemIds.getAsJsonArray()) {
            if (matchesMainhandRequirement(mainHand, itemId.getAsString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesMainhandRequirement(ItemStack stack, String requirement) {
        if (requirement.startsWith("#")) {
            Identifier tagId = Identifier.tryParse(requirement.substring(1));
            if (tagId == null) {
                return false;
            }
            return stack.isIn(TagKey.of(RegistryKeys.ITEM, tagId));
        }

        Identifier itemId = Identifier.tryParse(requirement);
        return itemId != null && Registries.ITEM.getId(stack.getItem()).equals(itemId);
    }

    private static boolean isOnCooldown(LivingEntity entity, CustomSkillDefinition skill) {
        UUID uuid = entity.getUuid();
        if (PENDING_SKILLS.containsKey(new PendingSkillKey(uuid, skill.id()))) {
            return true;
        }
        if (GLOBAL_COOLDOWNS.getOrDefault(uuid, 0) > 0) {
            return true;
        }
        return SKILL_COOLDOWNS.getOrDefault(uuid, Map.of()).getOrDefault(skill.id(), 0) > 0;
    }

    private static void applyCooldowns(LivingEntity entity, CustomSkillDefinition skill) {
        UUID uuid = entity.getUuid();
        if (skill.globalCooldownTicks() > 0) {
            GLOBAL_COOLDOWNS.put(uuid, skill.globalCooldownTicks());
        }
        int cooldownTicks = effectiveCooldownTicks(skill);
        if (cooldownTicks > 0) {
            SKILL_COOLDOWNS.computeIfAbsent(uuid, ignored -> new ConcurrentHashMap<>()).put(skill.id(), cooldownTicks);
        }
    }

    private static int effectiveCooldownTicks(CustomSkillDefinition skill) {
        int cooldownTicks = skill.cooldownTicks();
        if (skill.playerSkill()
                && (skill.trigger() == SkillTrigger.WHEN_ATTACKS || skill.trigger() == SkillTrigger.ON_RANGED_HIT)) {
            cooldownTicks = Math.max(cooldownTicks, PLAYER_HIT_SKILL_MIN_COOLDOWN_TICKS);
        }
        return cooldownTicks;
    }

    private static boolean entityExists(MinecraftServer server, UUID entityId) {
        return findLivingEntity(server, entityId) != null;
    }

    private static LivingEntity findLivingEntity(MinecraftServer server, UUID entityId) {
        if (entityId == null) {
            return null;
        }
        for (ServerWorld world : server.getWorlds()) {
            if (world.getEntity(entityId) instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }

    private static boolean scheduleTelegraph(ServerWorld world, LivingEntity entity, CustomSkillDefinition skill, DamageSource source, float sourceDamageAmount, JsonElement telegraphJson) {
        PendingSkillKey key = new PendingSkillKey(entity.getUuid(), skill.id());
        if (PENDING_SKILLS.containsKey(key)) {
            return false;
        }

        List<TelegraphStep> steps = readTelegraphSteps(telegraphJson);
        if (steps.isEmpty()) {
            return false;
        }

        Vec3d lockedDirection = lockedTelegraphDirection(entity, skill.json());
        PENDING_SKILLS.put(key, new PendingSkillState(0, steps, source, sourceDamageAmount, lockedDirection, currentContextTargetId(entity), true, ACTIVE_PROC_COEFFICIENT.get(), activeComboCount(entity)));
        if (cooldownOnStart(skill)) {
            applyCooldowns(entity, skill);
        }
        runDueTelegraphSteps(world, entity, steps, 0, source, sourceDamageAmount, lockedDirection);
        return true;
    }

    private static boolean scheduleDelayedActions(ServerWorld world, LivingEntity entity, CustomSkillDefinition skill, DamageSource source, float sourceDamageAmount, JsonElement actionsJson) {
        PendingSkillKey key = new PendingSkillKey(entity.getUuid(), skill.id());
        if (PENDING_SKILLS.containsKey(key)) {
            return false;
        }

        List<TelegraphStep> steps = readDelayedActionSteps(actionsJson);
        if (steps.isEmpty()) {
            return false;
        }

        Vec3d lockedDirection = lockedTelegraphDirection(entity, skill.json());
        if (cooldownOnStart(skill)) {
            applyCooldowns(entity, skill);
        }
        runDueTelegraphSteps(world, entity, steps, 0, source, sourceDamageAmount, lockedDirection);

        int totalDelayTicks = steps.get(steps.size() - 1).delayTicks();
        if (totalDelayTicks <= 0) {
            if (!cooldownOnStart(skill)) {
                applyCooldowns(entity, skill);
            }
            return true;
        }

        PENDING_SKILLS.put(key, new PendingSkillState(0, steps, source, sourceDamageAmount, lockedDirection, currentContextTargetId(entity), false, ACTIVE_PROC_COEFFICIENT.get(), activeComboCount(entity)));
        return true;
    }

    private static Vec3d lockedTelegraphDirection(LivingEntity entity, JsonObject json) {
        if (!json.has("lock_telegraph_direction") || !json.get("lock_telegraph_direction").getAsBoolean()) {
            return null;
        }

        LivingEntity target = currentTarget(entity);
        Vec3d direction = target == null ? horizontalForward(entity) : target.getPos().subtract(entity.getPos());
        direction = new Vec3d(direction.x, 0.0D, direction.z);
        if (direction.lengthSquared() < 0.001D) {
            return horizontalForward(entity);
        }
        return direction.normalize();
    }

    private static List<TelegraphStep> readTelegraphSteps(JsonElement telegraphJson) {
        List<TelegraphStep> steps = new ArrayList<>();
        if (telegraphJson.isJsonObject()) {
            JsonObject step = telegraphJson.getAsJsonObject();
            int delayTicks = step.has("delay_ticks") ? Math.max(0, step.get("delay_ticks").getAsInt()) : 10;
            steps.add(new TelegraphStep(delayTicks, step));
            return steps;
        }
        if (!telegraphJson.isJsonArray()) {
            return steps;
        }

        int cumulativeDelay = 0;
        for (JsonElement element : telegraphJson.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject step = element.getAsJsonObject();
            cumulativeDelay += step.has("delay_ticks") ? Math.max(0, step.get("delay_ticks").getAsInt()) : 0;
            steps.add(new TelegraphStep(cumulativeDelay, step));
        }
        return steps;
    }

    private static List<TelegraphStep> readDelayedActionSteps(JsonElement actionsJson) {
        List<TelegraphStep> steps = new ArrayList<>();
        if (!actionsJson.isJsonArray()) {
            return steps;
        }

        int cumulativeDelay = 0;
        for (JsonElement element : actionsJson.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject action = element.getAsJsonObject();
            if (isDelayOnlyAction(action)) {
                cumulativeDelay += actionDelayTicks(action);
                continue;
            }
            if (action.has("delay_ticks")) {
                cumulativeDelay += Math.max(0, action.get("delay_ticks").getAsInt());
            }

            JsonObject step = new JsonObject();
            JsonArray actions = new JsonArray();
            actions.add(action);
            step.add("actions", actions);
            steps.add(new TelegraphStep(cumulativeDelay, step));
        }
        return steps;
    }

    private static boolean hasDelayedActionList(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            return false;
        }
        for (JsonElement element : json.getAsJsonArray(key)) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject action = element.getAsJsonObject();
            if (action.has("delay_ticks") || isDelayOnlyAction(action)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDelayOnlyAction(JsonObject action) {
        if (!action.has("type")) {
            return action.has("delay_ticks") || action.has("ticks");
        }
        return "delay".equals(action.get("type").getAsString());
    }

    private static int actionDelayTicks(JsonObject action) {
        if (action.has("delay_ticks")) {
            return Math.max(0, action.get("delay_ticks").getAsInt());
        }
        if (action.has("ticks")) {
            return Math.max(0, action.get("ticks").getAsInt());
        }
        return 0;
    }

    private static void tickPendingSkills(MinecraftServer server) {
        PENDING_SKILLS.entrySet().removeIf(entry -> {
            PendingSkillKey key = entry.getKey();
            LivingEntity entity = findLivingEntity(server, key.entityId());
            CustomSkillDefinition skill = SKILLS.get(key.skillId());
            if (entity == null || !entity.isAlive() || skill == null || !(entity.getWorld() instanceof ServerWorld world)) {
                return true;
            }

            PendingSkillState state = entry.getValue();
            int ageTicks = state.ageTicks() + 1;
            LivingEntity target = findLivingEntity(server, state.targetId());
            withProcCoefficient(state.procCoefficient(), () -> withComboCount(state.comboCount(), () -> withTargetContext(entity, target, () -> {
                    runDueTelegraphSteps(world, entity, state.steps(), ageTicks, state.source(), state.sourceDamageAmount(), state.lockedDirection());
                    return true;
                })
            ));
            if (ageTicks < state.totalDelayTicks()) {
                PENDING_SKILLS.put(key, state.withAgeTicks(ageTicks));
                return false;
            }

            if (state.runBodyOnComplete()) {
                withProcCoefficient(state.procCoefficient(), () -> withComboCount(state.comboCount(), () -> withTargetContext(entity, target, () -> executeSkillBody(world, entity, skill, state.source(), state.sourceDamageAmount(), state.lockedDirection()))));
            } else if (!cooldownOnStart(skill)) {
                applyCooldowns(entity, skill);
            }
            return true;
        });
    }

    private static void runDueTelegraphSteps(ServerWorld world, LivingEntity entity, List<TelegraphStep> steps, int ageTicks, DamageSource source, float sourceDamageAmount) {
        runDueTelegraphSteps(world, entity, steps, ageTicks, source, sourceDamageAmount, null);
    }

    private static void runDueTelegraphSteps(ServerWorld world, LivingEntity entity, List<TelegraphStep> steps, int ageTicks, DamageSource source, float sourceDamageAmount, Vec3d lockedDirection) {
        for (TelegraphStep step : steps) {
            if (step.delayTicks() == ageTicks) {
                applyActionList(world, entity, step.json(), "actions", true, ageTicks, source, sourceDamageAmount, lockedDirection);
            }
        }
    }

    private static boolean isCancelableSkill(CustomSkillDefinition skill) {
        return skill.json().has("cancel_damage") && skill.json().get("cancel_damage").getAsBoolean();
    }

    private static void targetAttacker(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof MobEntity mob) || source == null || !(source.getAttacker() instanceof LivingEntity attacker)) {
            return;
        }
        mob.setTarget(attacker);
    }

    private static boolean reflectDamage(ServerWorld world, LivingEntity entity, DamageSource source, float sourceDamageAmount, JsonObject json) {
        if (!canReflectDamage(entity, source, sourceDamageAmount)) {
            return false;
        }
        LivingEntity attacker = (LivingEntity) source.getAttacker();

        float multiplier = json.has("multiplier") ? json.get("multiplier").getAsFloat() : 1.0F;
        float reflectedDamage = sourceDamageAmount * multiplier;
        if (reflectedDamage <= 0.0F) {
            return false;
        }

        boolean knockback = !json.has("knockback") || json.get("knockback").getAsBoolean();
        DamageSource reflectSource = damageSourceForOwner(world, entity);

        applyingReflectedDamage = true;
        try {
            damage(attacker, reflectSource, reflectedDamage, knockback);
        } finally {
            applyingReflectedDamage = false;
        }
        return true;
    }

    private static boolean canReflectDamage(LivingEntity entity, DamageSource source, float sourceDamageAmount) {
        return source != null
                && sourceDamageAmount > 0.0F
                && source.getAttacker() instanceof LivingEntity attacker
                && attacker != entity;
    }

    private static boolean enterStance(ServerWorld world, LivingEntity entity, CustomSkillDefinition skill, JsonObject json) {
        if (!(entity instanceof MobEntity mob) || ACTIVE_STANCES.containsKey(entity.getUuid())) {
            return false;
        }

        LivingEntity target = currentTarget(entity);
        if (target == null || !target.isAlive()) {
            return false;
        }
        mob.setTarget(target);

        double targetRange = json.has("target_range") ? json.get("target_range").getAsDouble() : 3.0D;
        if (entity.squaredDistanceTo(target) > targetRange * targetRange) {
            return false;
        }

        int durationTicks = json.has("duration_ticks") ? json.get("duration_ticks").getAsInt() : 30;
        int tickIntervalTicks = json.has("tick_interval_ticks") ? Math.max(1, json.get("tick_interval_ticks").getAsInt()) : 20;
        ACTIVE_STANCES.put(entity.getUuid(), new StanceState(skill.id(), durationTicks, tickIntervalTicks, tickIntervalTicks, 0, json));
        applyActionSet(world, entity, json, "enter");
        return true;
    }

    private static boolean consumeParryStance(ServerWorld world, LivingEntity entity, DamageSource source, float sourceDamageAmount) {
        StanceState state = ACTIVE_STANCES.get(entity.getUuid());
        if (state == null || !state.config().has("parry") || !state.config().get("parry").getAsBoolean()) {
            return false;
        }
        ACTIVE_STANCES.remove(entity.getUuid());

        JsonObject json = state.config();
        if (source.getAttacker() instanceof LivingEntity attacker) {
            float punishDamage = json.has("punish_damage") ? json.get("punish_damage").getAsFloat() : 0.0F;
            if (json.has("punish_reflect_damage")) {
                JsonObject reflectJson = json.getAsJsonObject("punish_reflect_damage");
                float multiplier = reflectJson.has("multiplier") ? reflectJson.get("multiplier").getAsFloat() : 1.0F;
                punishDamage += sourceDamageAmount * multiplier;
            }
            if (punishDamage > 0.0F) {
                DamageSource punishSource = damageSourceForOwner(world, entity);
                boolean knockback = punishKnockback(json);
                damage(attacker, punishSource, punishDamage, knockback);
            }
            applyActionSet(world, attacker, json, "punish", 0, source, sourceDamageAmount);
        }
        applyActionSet(world, entity, json, "parry");
        return true;
    }

    private static boolean punishKnockback(JsonObject json) {
        if (json.has("punish_knockback")) {
            return json.get("punish_knockback").getAsBoolean();
        }
        if (json.has("punish_reflect_damage")) {
            JsonObject reflectJson = json.getAsJsonObject("punish_reflect_damage");
            if (reflectJson.has("knockback")) {
                return reflectJson.get("knockback").getAsBoolean();
            }
        }
        return true;
    }

    private static void tickActiveStances(MinecraftServer server) {
        ACTIVE_STANCES.entrySet().removeIf(entry -> {
            LivingEntity entity = findLivingEntity(server, entry.getKey());
            if (entity == null || !entity.isAlive() || !(entity.getWorld() instanceof ServerWorld world)) {
                return true;
            }

            StanceState state = entry.getValue();
            int ageTicks = state.ageTicks() + 1;
            int nextTickDelay = state.tickDelayTicks() - 1;
            if (nextTickDelay <= 0) {
                applyStanceTick(world, entity, state.config(), ageTicks);
                nextTickDelay = state.tickIntervalTicks();
            }

            int remainingTicks = state.remainingTicks() - 1;
            if (remainingTicks > 0) {
                ACTIVE_STANCES.put(entry.getKey(), state.withTicks(remainingTicks, nextTickDelay, ageTicks));
                return false;
            }

            String expirePrefix = state.config().has("expire_prefix")
                    ? state.config().get("expire_prefix").getAsString()
                    : hasActionSet(state.config(), "stance_end") ? "stance_end" : "fail";
            applyActionSet(world, entity, state.config(), expirePrefix);
            return true;
        });
    }

    private static boolean hasActionSet(JsonObject json, String prefix) {
        return json.has(prefix + "_effects")
                || json.has(prefix + "_particles")
                || json.has(prefix + "_sounds")
                || json.has(prefix + "_functions")
                || json.has(prefix + "_commands")
                || json.has(actionListKey(prefix));
    }

    private static void applyStanceTick(ServerWorld world, LivingEntity entity, JsonObject json, int ageTicks) {
        applyActionSet(world, entity, json, "tick", ageTicks);

        if (!json.has("tick_radius")) {
            return;
        }

        double radius = json.get("tick_radius").getAsDouble();
        if (radius <= 0.0D) {
            return;
        }

        List<LivingEntity> targets = nearbyStanceTargets(world, entity, json, radius);
        for (LivingEntity target : targets) {
            if (json.has("tick_nearby_damage")) {
                float damage = json.get("tick_nearby_damage").getAsFloat();
                if (damage > 0.0F) {
                    DamageSource damageSource = damageSourceForOwner(world, entity);
                    boolean knockback = !json.has("tick_nearby_knockback") || json.get("tick_nearby_knockback").getAsBoolean();
                    damage(target, damageSource, damage, knockback);
                }
            }
            if (json.has("tick_nearby_fire_ticks")) {
                target.setFireTicks(Math.max(target.getFireTicks(), json.get("tick_nearby_fire_ticks").getAsInt()));
            }
            applyActionSet(world, target, json, "tick_nearby", ageTicks);
            applyActionList(world, target, json, "stance_tick", false, ageTicks);
        }
    }

    private static List<LivingEntity> nearbyStanceTargets(ServerWorld world, LivingEntity entity, JsonObject json, double radius) {
        String targetMode = json.has("tick_target") ? json.get("tick_target").getAsString() : "players";
        return nearbyTargets(world, entity, targetMode, radius);
    }

    private static List<LivingEntity> nearbyTargets(ServerWorld world, LivingEntity entity, JsonObject json, double radius) {
        return nearbyTargets(world, entity, json, radius, null);
    }

    private static List<LivingEntity> nearbyTargets(ServerWorld world, LivingEntity entity, JsonObject json, double radius, Vec3d lockedDirection) {
        String targetMode = actionTargetMode(json, "players");
        Vec3d center = areaCenter(entity, json, lockedDirection);
        List<LivingEntity> targets = nearbyTargets(world, entity, targetMode, radius, center);
        if (!hasTargetFilters(json)) {
            return targets;
        }

        return filterTargets(targets, json);
    }

    private static List<LivingEntity> nearbyTargets(ServerWorld world, LivingEntity entity, String targetMode, double radius) {
        return nearbyTargets(world, entity, targetMode, radius, entity.getPos());
    }

    private static List<LivingEntity> nearbyTargets(ServerWorld world, LivingEntity entity, String targetMode, double radius, Vec3d center) {
        double radiusSquared = radius * radius;
        if ("target".equals(targetMode)) {
            LivingEntity target = currentTarget(entity);
            if (target == null || target.squaredDistanceTo(center) > radiusSquared) {
                return List.of();
            }
            return List.of(target);
        }

        Box box = Box.of(center, radius * 2.0D, radius * 2.0D, radius * 2.0D);
        return world.getEntitiesByClass(LivingEntity.class, box, target -> {
            if (target == entity || !target.isAlive() || target.squaredDistanceTo(center) > radiusSquared) {
                return false;
            }
            if ("living".equals(targetMode)) {
                return true;
            }
            if ("mobs".equals(targetMode)) {
                return target instanceof MobEntity;
            }
            return target instanceof PlayerEntity;
        });
    }

    private static Vec3d areaCenter(LivingEntity entity, JsonObject action) {
        return areaCenter(entity, action, null);
    }

    private static Vec3d areaCenter(LivingEntity entity, JsonObject action, Vec3d lockedDirection) {
        double forwardOffset = offsetValue(action, "radius_forward_offset", "forward_offset");
        double sideOffset = offsetValue(action, "radius_side_offset", "side_offset");
        double verticalOffset = offsetValue(action, "radius_vertical_offset", "vertical_offset");
        Vec3d forward = lockedDirection == null ? horizontalForward(entity) : lockedDirection.normalize();
        Vec3d side = horizontalSide(forward);
        return entity.getPos()
                .add(forward.multiply(forwardOffset))
                .add(side.multiply(sideOffset))
                .add(0.0D, verticalOffset, 0.0D);
    }

    private static double offsetValue(JsonObject json, String radiusKey, String fallbackKey) {
        if (json.has(radiusKey)) {
            return json.get(radiusKey).getAsDouble();
        }
        return json.has(fallbackKey) ? json.get(fallbackKey).getAsDouble() : 0.0D;
    }

    private static boolean hasTargetFilters(JsonObject action) {
        return action.has("tags")
                || action.has("exclude_tags")
                || action.has("entity_type")
                || action.has("entity_types")
                || action.has("exclude_entity_type")
                || action.has("exclude_entity_types");
    }

    private static List<LivingEntity> filterTargets(List<LivingEntity> targets, JsonObject action) {
        List<LivingEntity> filtered = new ArrayList<>();
        for (LivingEntity target : targets) {
            if (passesRequiredTags(target, action)
                    && passesExcludedTags(target, action)
                    && passesRequiredEntityTypes(target, action)
                    && passesExcludedEntityTypes(target, action)) {
                filtered.add(target);
            }
        }
        return filtered;
    }

    private static boolean passesRequiredTags(LivingEntity target, JsonObject action) {
        if (!action.has("tags")) {
            return true;
        }
        for (JsonElement element : action.getAsJsonArray("tags")) {
            if (!target.getCommandTags().contains(element.getAsString())) {
                return false;
            }
        }
        return true;
    }

    private static boolean passesExcludedTags(LivingEntity target, JsonObject action) {
        if (!action.has("exclude_tags")) {
            return true;
        }
        for (JsonElement element : action.getAsJsonArray("exclude_tags")) {
            if (target.getCommandTags().contains(element.getAsString())) {
                return false;
            }
        }
        return true;
    }

    private static boolean passesRequiredEntityTypes(LivingEntity target, JsonObject action) {
        if (!action.has("entity_type") && !action.has("entity_types")) {
            return true;
        }
        Identifier current = Registries.ENTITY_TYPE.getId(target.getType());
        return containsIdentifier(action, "entity_type", "entity_types", current);
    }

    private static boolean passesExcludedEntityTypes(LivingEntity target, JsonObject action) {
        Identifier current = Registries.ENTITY_TYPE.getId(target.getType());
        return !containsIdentifier(action, "exclude_entity_type", "exclude_entity_types", current);
    }

    private static boolean containsIdentifier(JsonObject json, String singleKey, String arrayKey, Identifier current) {
        if (json.has(singleKey) && identifierMatches(current, json.get(singleKey).getAsString())) {
            return true;
        }
        if (!json.has(arrayKey)) {
            return false;
        }
        JsonElement element = json.get(arrayKey);
        if (element.isJsonPrimitive()) {
            return identifierMatches(current, element.getAsString());
        }
        for (JsonElement entry : element.getAsJsonArray()) {
            if (identifierMatches(current, entry.getAsString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean identifierMatches(Identifier current, String value) {
        Identifier id = Identifier.tryParse(value);
        return id != null && current.equals(id);
    }

    private static DamageSource damageSourceForOwner(ServerWorld world, LivingEntity owner) {
        if (owner instanceof PlayerEntity player) {
            return world.getDamageSources().playerAttack(player);
        }
        if (owner instanceof MobEntity mob) {
            return world.getDamageSources().mobAttack(mob);
        }
        return world.getDamageSources().generic();
    }

    private static DamageSource actionDamageSource(ServerWorld world, LivingEntity owner, JsonObject action) {
        String damageTypeValue = firstString(action, "damage_type", "source");
        if (damageTypeValue == null || damageTypeValue.isBlank()) {
            return damageSourceForOwner(world, owner);
        }

        Identifier id = Identifier.tryParse(damageTypeValue);
        if (id == null) {
            CustomMobsSpawnerLog.warn("Invalid skill damage type " + damageTypeValue);
            return damageSourceForOwner(world, owner);
        }

        RegistryKey<DamageType> key = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, id);
        return world.getDamageSources().create(key, owner);
    }

    private static DamageSource damageSourceForEntity(ServerWorld world, Entity attacker) {
        if (attacker instanceof PlayerEntity player) {
            return world.getDamageSources().playerAttack(player);
        }
        if (attacker instanceof MobEntity mob) {
            return world.getDamageSources().mobAttack(mob);
        }
        return world.getDamageSources().generic();
    }

    private static void damage(LivingEntity target, DamageSource source, float amount, boolean knockback) {
        damage(target, source, amount, knockback, false);
    }

    private static void damage(LivingEntity target, DamageSource source, float amount, boolean knockback, boolean bypassInvulnerability) {
        if (bypassInvulnerability) {
            target.timeUntilRegen = 0;
        }
        if (knockback) {
            target.damage(source, amount);
            return;
        }

        Vec3d velocity = target.getVelocity();
        target.damage(source, amount);
        target.setVelocity(velocity);
    }

    private static void applyActionSet(ServerWorld world, LivingEntity entity, JsonObject json, String prefix) {
        applyActionSet(world, entity, json, prefix, 0, null, 0.0F);
    }

    private static void applyActionSet(ServerWorld world, LivingEntity entity, JsonObject json, String prefix, int ageTicks) {
        applyActionSet(world, entity, json, prefix, ageTicks, null, 0.0F);
    }

    private static void applyActionSet(ServerWorld world, LivingEntity entity, JsonObject json, String prefix, int ageTicks, DamageSource source, float sourceDamageAmount) {
        applyActionList(world, entity, json, actionListKey(prefix), true, ageTicks, source, sourceDamageAmount);

        String effectsKey = prefix + "_effects";
        if (json.has(effectsKey)) {
            for (JsonElement element : json.getAsJsonArray(effectsKey)) {
                applyEffect(entity, element.getAsJsonObject());
            }
        }

        String particlesKey = prefix + "_particles";
        if (json.has(particlesKey)) {
            for (JsonElement element : json.getAsJsonArray(particlesKey)) {
                spawnParticles(world, entity, element.getAsJsonObject());
            }
        }

        String soundsKey = prefix + "_sounds";
        if (json.has(soundsKey)) {
            for (JsonElement element : json.getAsJsonArray(soundsKey)) {
                playSound(world, entity, element.getAsJsonObject());
            }
        }

        String functionsKey = prefix + "_functions";
        if (json.has(functionsKey)) {
            for (JsonElement element : json.getAsJsonArray(functionsKey)) {
                runCommand(world, entity, "function " + element.getAsString());
            }
        }

        String commandsKey = prefix + "_commands";
        if (json.has(commandsKey)) {
            for (JsonElement element : json.getAsJsonArray(commandsKey)) {
                runCommand(world, entity, element.getAsString());
            }
        }
    }

    private static String actionListKey(String prefix) {
        return switch (prefix) {
            case "enter" -> "enter_stance";
            case "tick" -> "stance_tick";
            case "parry" -> "on_parry";
            default -> prefix;
        };
    }

    private static void applyActionList(ServerWorld world, LivingEntity entity, JsonObject json, String key, boolean selfPass, int ageTicks) {
        applyActionList(world, entity, json, key, selfPass, ageTicks, null, 0.0F);
    }

    private static void applyActionList(ServerWorld world, LivingEntity entity, JsonObject json, String key, boolean selfPass, int ageTicks, DamageSource source) {
        applyActionList(world, entity, json, key, selfPass, ageTicks, source, 0.0F);
    }

    private static void applyActionList(ServerWorld world, LivingEntity entity, JsonObject json, String key, boolean selfPass, int ageTicks, DamageSource source, float sourceDamageAmount) {
        applyActionList(world, entity, json, key, selfPass, ageTicks, source, sourceDamageAmount, null);
    }

    private static void applyActionList(ServerWorld world, LivingEntity entity, JsonObject json, String key, boolean selfPass, int ageTicks, DamageSource source, float sourceDamageAmount, Vec3d lockedDirection) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            return;
        }

        for (JsonElement element : json.getAsJsonArray(key)) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject action = element.getAsJsonObject();
            if (!actionIntervalReady(action, ageTicks)) {
                continue;
            }
            if (selfPass && isOwnerResolvedAction(action)) {
                applyAction(world, entity, action, source, sourceDamageAmount, lockedDirection);
                continue;
            }
            if (selfPass && isPostureDamageAction(action)) {
                if (!conditionsPass(entity, action)) {
                    continue;
                }
                applyAreaPostureDamageAction(world, entity, action, lockedDirection);
                continue;
            }
            if (selfPass && isAreaAction(action)) {
                applyAreaAction(world, entity, action, lockedDirection);
                continue;
            }
            if (selfPass && isDamageAction(action)) {
                if (!conditionsPass(entity, action)) {
                    continue;
                }
                applyAreaDamageAction(world, entity, action, lockedDirection);
                continue;
            }
            if (!selfPass && isDamageAction(action)) {
                continue;
            }
            String target = actionTargetMode(action, "self");
            if (selfPass && !("self".equals(target) || "owner".equals(target))) {
                continue;
            }
            if (!selfPass && !isNearbyActionTarget(entity, target)) {
                continue;
            }
            applyAction(world, entity, action, source, sourceDamageAmount, lockedDirection);
        }
    }

    private static boolean actionIntervalReady(JsonObject action, int ageTicks) {
        int intervalTicks = actionIntervalTicks(action);
        return ageTicks <= 0 || ageTicks % intervalTicks == 0;
    }

    private static int actionIntervalTicks(JsonObject action) {
        if (action.has("aura_interval_ticks")) {
            return Math.max(1, action.get("aura_interval_ticks").getAsInt());
        }
        if (action.has("interval_ticks")) {
            return Math.max(1, action.get("interval_ticks").getAsInt());
        }
        return 1;
    }

    private static boolean hasActionInterval(JsonObject action) {
        return action.has("aura_interval_ticks") || action.has("interval_ticks");
    }

    private static boolean isDamageAction(JsonObject action) {
        return action.has("type") && "damage".equals(action.get("type").getAsString());
    }

    private static boolean isPostureDamageAction(JsonObject action) {
        return action.has("type") && "posture_damage".equals(action.get("type").getAsString());
    }

    private static boolean isOwnerResolvedAction(JsonObject action) {
        if (!action.has("type")) {
            return false;
        }
        String type = action.get("type").getAsString();
        return switch (type) {
            case "effect",
                    "heal",
                    "equip", "equipment",
                    "summon_custom_mob", "custom_mob",
                    "discard", "despawn", "remove",
                    "posture_heal", "posture_restore", "restore_posture",
                    "teleport",
                    "projectile",
                    "grenade", "grenade_projectile",
                    "spell_engine_spell", "spell",
                    "spell_engine_impact", "spell_impact",
                    "spell_engine_rain", "spell_rain",
                    "rally_target",
                    "clear_target", "drop_target", "forget_target" -> true;
            default -> false;
        };
    }

    private static boolean requiresReflectDamage(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            return false;
        }
        for (JsonElement element : json.getAsJsonArray(key)) {
            JsonObject action = element.getAsJsonObject();
            if (action.has("type") && "reflect_damage".equals(action.get("type").getAsString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAreaAction(JsonObject action) {
        return action.has("radius")
                && (action.has("target") || action.has("selector"))
                && !"self".equals(actionTargetMode(action, "self"));
    }

    private static boolean isNearbyActionTarget(LivingEntity entity, String target) {
        return switch (target) {
            case "players" -> entity instanceof PlayerEntity;
            case "mobs" -> entity instanceof MobEntity;
            case "living", "target" -> true;
            default -> false;
        };
    }

    private static String actionTargetMode(JsonObject action, String fallback) {
        if (action.has("selector") && action.get("selector").isJsonPrimitive()) {
            return action.get("selector").getAsString();
        }
        if (action.has("target")) {
            return action.get("target").getAsString();
        }
        return fallback;
    }

    private static void applyAction(ServerWorld world, LivingEntity entity, JsonObject action) {
        applyAction(world, entity, action, null);
    }

    private static void applyAction(ServerWorld world, LivingEntity entity, JsonObject action, DamageSource source) {
        applyAction(world, entity, action, source, 0.0F);
    }

    private static void applyAction(ServerWorld world, LivingEntity entity, JsonObject action, DamageSource source, float sourceDamageAmount) {
        applyAction(world, entity, action, source, sourceDamageAmount, null);
    }

    private static void applyAction(ServerWorld world, LivingEntity entity, JsonObject action, DamageSource source, float sourceDamageAmount, Vec3d lockedDirection) {
        if (!conditionsPass(entity, action)) {
            return;
        }

        String type = action.has("type") ? action.get("type").getAsString() : "";
        switch (type) {
            case "effect" -> applyEffectAction(world, entity, action);
            case "effect_clear" -> clearEffect(entity, action);
            case "heal" -> applyHealAction(world, entity, action);
            case "clear_combo" -> clearCombo(entity);
            case "attack_cooldown" -> startAttackCooldown(entity, action);
            case "particle" -> spawnParticles(world, entity, action, lockedDirection);
            case "sound" -> playSound(world, entity, action);
            case "random" -> applyRandomAction(world, entity, action, source, sourceDamageAmount, lockedDirection);
            case "equip", "equipment" -> equipAction(entity, action);
            case "summon_custom_mob", "custom_mob" -> summonCustomMobAction(world, entity, action, lockedDirection);
            case "damage" -> {
            }
            case "discard", "despawn", "remove" -> entity.discard();
            case "posture_damage" -> applyPostureDamageAction(entity, action);
            case "posture_heal", "posture_restore", "restore_posture" -> applyPostureHealAction(entity, action);
            case "reflect_damage" -> applyReflectedActionDamage(world, entity, source, sourceDamageAmount, action);
            case "dodge" -> dodge(world, entity, source, action);
            case "lunge" -> lunge(entity, action, lockedDirection);
            case "swing", "weapon_swing" -> swingHand(entity, action);
            case "teleport" -> teleportAction(world, entity, action);
            case "projectile" -> projectileAction(world, entity, action);
            case "grenade", "grenade_projectile" -> grenadeProjectileAction(world, entity, action);
            case "spell_engine_spell", "spell" -> spellEngineSpellAction(world, entity, action);
            case "spell_engine_impact", "spell_impact" -> spellEngineImpactAction(world, entity, entity, action);
            case "spell_engine_rain", "spell_rain" -> spellEngineRainAction(world, entity, entity, action);
            case "static_chain" -> staticChainAction(world, entity, action);
            case "rally_target" -> rallyTargetAction(world, entity, action);
            case "clear_target", "drop_target", "forget_target" -> clearTargetAction(entity, action);
            case "aura" -> createAura(world, entity, action);
            case "stun" -> applyStun(entity, action);
            case "knockback" -> {
            }
            case "explosion" -> createExplosion(world, entity, action);
            case "schedule" -> scheduleActions(entity, action);
            case "function" -> runFunctionAction(world, entity, action);
            case "command" -> runCommandAction(world, entity, action);
            default -> CustomMobsSpawnerLog.warn("Unsupported skill action type " + type);
        }
    }

    private static void applyRandomAction(ServerWorld world, LivingEntity entity, JsonObject action, DamageSource source, float sourceDamageAmount, Vec3d lockedDirection) {
        if (!action.has("actions") || !action.get("actions").isJsonArray()) {
            return;
        }

        JsonArray choices = action.getAsJsonArray("actions");
        double totalWeight = 0.0D;
        for (JsonElement element : choices) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject choice = element.getAsJsonObject();
            if (!conditionsPass(entity, choice)) {
                continue;
            }
            totalWeight += randomChoiceWeight(choice);
        }
        if (totalWeight <= 0.0D) {
            return;
        }

        double roll = world.random.nextDouble() * totalWeight;
        for (JsonElement element : choices) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject choice = element.getAsJsonObject();
            if (!conditionsPass(entity, choice)) {
                continue;
            }
            roll -= randomChoiceWeight(choice);
            if (roll > 0.0D) {
                continue;
            }
            applyRandomChoice(world, entity, choice, source, sourceDamageAmount, lockedDirection);
            return;
        }
    }

    private static double randomChoiceWeight(JsonObject choice) {
        if (!choice.has("weight")) {
            return 1.0D;
        }
        return Math.max(0.0D, choice.get("weight").getAsDouble());
    }

    private static void applyRandomChoice(ServerWorld world, LivingEntity entity, JsonObject choice, DamageSource source, float sourceDamageAmount, Vec3d lockedDirection) {
        JsonObject wrapper = new JsonObject();
        if (choice.has("action") && choice.get("action").isJsonObject()) {
            JsonArray actions = new JsonArray();
            actions.add(choice.get("action").getAsJsonObject());
            wrapper.add("actions", actions);
        } else if (choice.has("actions") && choice.get("actions").isJsonArray()) {
            wrapper.add("actions", choice.get("actions").getAsJsonArray());
        } else {
            JsonArray actions = new JsonArray();
            actions.add(choice);
            wrapper.add("actions", actions);
        }
        if (hasDelayedActionList(wrapper, "actions")) {
            scheduleStandaloneDelayedActions(world, entity, wrapper.get("actions"), source, sourceDamageAmount, lockedDirection);
            return;
        }
        applyActionList(world, entity, wrapper, "actions", true, 0, source, sourceDamageAmount, lockedDirection);
    }

    private static void clearCombo(LivingEntity entity) {
        if (entity instanceof ServerPlayerEntity player) {
            ComboHandler.reset(player);
        }
    }

    private static void startAttackCooldown(LivingEntity entity, JsonObject action) {
        if (entity instanceof PlayerEntity player) {
            int ticks = action.has("ticks") ? action.get("ticks").getAsInt() : 0;
            PlayerParryHandler.startAttackCooldown(player, ticks);
        }
    }

    private static void swingHand(LivingEntity entity, JsonObject action) {
        String handValue = action.has("hand") ? action.get("hand").getAsString() : "mainhand";
        boolean betterCombat = action.has("better_combat") && action.get("better_combat").getAsBoolean();
        if (betterCombat && entity instanceof ServerPlayerEntity player) {
            String animation = action.has("animation")
                    ? action.get("animation").getAsString()
                    : "bettercombat:one_handed_slash_horizontal_right";
            float length = action.has("length") ? action.get("length").getAsFloat() : 20.0F;
            float upswing = action.has("upswing") ? action.get("upswing").getAsFloat() : 0.5F;
            if (BetterCombatAnimationCompat.playAttackAnimation(player, handValue, animation, length, upswing)) {
                return;
            }
        }
        Hand hand = switch (handValue.toLowerCase()) {
            case "offhand", "off_hand" -> Hand.OFF_HAND;
            default -> Hand.MAIN_HAND;
        };
        entity.swingHand(hand, true);
    }

    private static void equipAction(LivingEntity entity, JsonObject action) {
        EquipmentSlot slot = equipmentSlot(action.has("slot") ? action.get("slot").getAsString() : "mainhand");
        if (slot == null) {
            CustomMobsSpawnerLog.warn("Unsupported equipment slot for skill action");
            return;
        }

        JsonElement stackJson = action.has("item") ? action.get("item") : action;
        entity.equipStack(slot, CustomMobRegistry.readStack(stackJson));
    }

    private static EquipmentSlot equipmentSlot(String slot) {
        return switch (slot.toLowerCase()) {
            case "head", "helmet" -> EquipmentSlot.HEAD;
            case "chest", "chestplate" -> EquipmentSlot.CHEST;
            case "legs", "leggings" -> EquipmentSlot.LEGS;
            case "feet", "boots" -> EquipmentSlot.FEET;
            case "offhand", "off_hand" -> EquipmentSlot.OFFHAND;
            case "mainhand", "main_hand", "hand" -> EquipmentSlot.MAINHAND;
            default -> null;
        };
    }

    private static void summonCustomMobAction(ServerWorld world, LivingEntity owner, JsonObject action, Vec3d lockedDirection) {
        String customMobId = firstString(action, "custom_mob", "id");
        if (customMobId == null || customMobId.isBlank()) {
            return;
        }

        Identifier id = Identifier.tryParse(customMobId);
        if (id == null) {
            CustomMobsSpawnerLog.warn("Invalid custom mob id in skill action " + customMobId);
            return;
        }

        int count = action.has("count") ? Math.max(1, action.get("count").getAsInt()) : 1;
        for (int index = 0; index < count; index++) {
            LivingEntity origin = actionOriginEntity(owner, action);
            Vec3d position = summonPosition(world, owner, origin, action, lockedDirection);
            if (!SpawnSafety.isLoadedAround(world, BlockPos.ofFloored(position), 1, 2)) {
                debugSkill(action, () -> "Skipped custom mob summon in unloaded chunk at " + BlockPos.ofFloored(position));
                continue;
            }
            Entity spawned = CustomMobRegistry.create(id, world, BlockPos.ofFloored(position), owner.getYaw());
            if (spawned == null) {
                CustomMobsSpawnerLog.warn("Failed to summon custom mob from skill action " + id);
                continue;
            }

            spawned.refreshPositionAndAngles(position.x, position.y, position.z, owner.getYaw(), owner.getPitch());
            if (spawned instanceof MobEntity summoned && owner instanceof MobEntity ownerMob && actionBoolean(action, "target_owner_target")) {
                LivingEntity target = ownerMob.getTarget();
                if (isValidSkillTarget(target)) {
                    summoned.setTarget(target);
                }
            }
            if (!SpawnSafety.isLoaded(world, spawned)) {
                debugSkill(action, () -> "Skipped custom mob summon whose bounds cross an unloaded chunk at " + BlockPos.ofFloored(position));
                continue;
            }
            world.spawnEntityAndPassengers(spawned);
            CustomMobRegistry.applyPostSpawn(spawned);
        }
    }

    private static Vec3d summonPosition(ServerWorld world, LivingEntity owner, LivingEntity origin, JsonObject action, Vec3d lockedDirection) {
        if (action.has("selector") && action.get("selector").isJsonObject()) {
            List<Entity> matches = selectObjectTeleportEntities(world, owner, action.getAsJsonObject("selector"), action.has("radius") ? action.get("radius").getAsDouble() : 0.0D);
            if (!matches.isEmpty()) {
                Entity selected = matches.get(0);
                if (selected instanceof LivingEntity selectedLiving) {
                    return relativePositionFromOrigin(selectedLiving, action, action.has("y_offset") ? action.get("y_offset").getAsDouble() : 0.0D, lockedDirection);
                }
                return selected.getPos().add(0.0D, action.has("y_offset") ? action.get("y_offset").getAsDouble() : 0.0D, 0.0D);
            }
        }
        return hasAbsolutePosition(action)
                ? actionPosition(origin, action, origin.getPos())
                : relativePositionFromOrigin(origin, action, action.has("y_offset") ? action.get("y_offset").getAsDouble() : 0.0D, lockedDirection);
    }

    private static void scheduleActions(LivingEntity entity, JsonObject action) {
        JsonElement stepsJson = action.has("steps") ? action.get("steps") : action;
        List<TelegraphStep> steps = readTelegraphSteps(stepsJson);
        if (steps.isEmpty()) {
            return;
        }
        SCHEDULED_ACTIONS.put(UUID.randomUUID(), new ScheduledActionState(entity.getUuid(), 0, steps, null, 0.0F, null, currentContextTargetId(entity), ACTIVE_PROC_COEFFICIENT.get(), activeComboCount(entity)));
    }

    private static boolean scheduleStandaloneDelayedActions(ServerWorld world, LivingEntity entity, JsonElement actionsJson, DamageSource source, float sourceDamageAmount, Vec3d lockedDirection) {
        List<TelegraphStep> steps = readDelayedActionSteps(actionsJson);
        if (steps.isEmpty()) {
            return false;
        }

        runDueTelegraphSteps(world, entity, steps, 0, source, sourceDamageAmount, lockedDirection);
        int totalDelayTicks = steps.get(steps.size() - 1).delayTicks();
        if (totalDelayTicks > 0) {
            SCHEDULED_ACTIONS.put(UUID.randomUUID(), new ScheduledActionState(entity.getUuid(), 0, steps, source, sourceDamageAmount, lockedDirection, currentContextTargetId(entity), ACTIVE_PROC_COEFFICIENT.get(), activeComboCount(entity)));
        }
        return true;
    }

    private static void tickScheduledActions(MinecraftServer server) {
        SCHEDULED_ACTIONS.entrySet().removeIf(entry -> {
            ScheduledActionState state = entry.getValue();
            LivingEntity entity = findLivingEntity(server, state.entityId());
            if (entity == null || !entity.isAlive() || !(entity.getWorld() instanceof ServerWorld world)) {
                return true;
            }

            int ageTicks = state.ageTicks() + 1;
            LivingEntity target = findLivingEntity(server, state.targetId());
            withProcCoefficient(state.procCoefficient(), () -> withComboCount(state.comboCount(), () -> withTargetContext(entity, target, () -> {
                    runDueTelegraphSteps(world, entity, state.steps(), ageTicks, state.source(), state.sourceDamageAmount(), state.lockedDirection());
                    return true;
                })
            ));
            if (ageTicks < state.totalDelayTicks()) {
                SCHEDULED_ACTIONS.put(entry.getKey(), state.withAgeTicks(ageTicks));
                return false;
            }
            return true;
        });
    }

    private static void tickSkillProjectiles(MinecraftServer server) {
        if (SKILL_PROJECTILES.isEmpty()) {
            return;
        }

        SKILL_PROJECTILES.entrySet().removeIf(entry -> {
            UUID projectileId = entry.getKey();
            Entity entity = findEntityDirect(server, projectileId);
            if (entity instanceof ProjectileEntity projectile && projectile.isAlive()) {
                tickSkillProjectile(projectile);
                return false;
            }
            return true;
        });
    }

    private static void tickGrenadeProjectiles(MinecraftServer server) {
        if (GRENADE_PROJECTILES.isEmpty()) {
            return;
        }

        GRENADE_PROJECTILES.entrySet().removeIf(entry -> {
            GrenadeProjectileState state = entry.getValue();
            Entity entity = findEntityDirect(server, entry.getKey());
            LivingEntity owner = findLivingEntity(server, state.ownerId());
            if (!(entity instanceof ItemEntity grenade) || !grenade.isAlive() || owner == null || !owner.isAlive() || !(grenade.getWorld() instanceof ServerWorld world)) {
                if (entity instanceof ItemEntity grenade) {
                    grenade.discard();
                }
                return true;
            }

            long worldTime = world.getTime();
            if (state.lastWorldTime() == worldTime) {
                return false;
            }

            int ageTicks = state.ageTicks() + 1;
            spawnGrenadeTrails(world, grenade, state.config(), ageTicks);
            if (ageTicks >= state.fuseTicks()) {
                detonateGrenade(world, owner, grenade, state.config());
                grenade.discard();
                return true;
            }

            GRENADE_PROJECTILES.put(entry.getKey(), state.withTick(ageTicks, worldTime));
            return false;
        });
    }

    private static void applyGroundedProjectileImpact(ServerWorld world, ProjectileEntity projectile, SkillProjectileState state) {
        if (state.ageTicks() < 4 || !isGroundedProjectile(projectile)) {
            return;
        }
        consumeSkillProjectile(projectile);

        LivingEntity owner = findLivingEntity(world.getServer(), state.ownerId());
        if (owner == null || !owner.isAlive()) {
            return;
        }
        applyProjectileHitActions(world, owner, owner, state.config(), "hit_ground", projectile.getPos());
    }

    private static void consumeSkillProjectile(ProjectileEntity projectile) {
        SKILL_PROJECTILES.remove(projectile.getUuid());
        projectile.getCommandTags().remove(SKILL_PROJECTILE_TAG);
    }

    private static boolean isGroundedProjectile(ProjectileEntity projectile) {
        if (projectile.isOnGround()) {
            return true;
        }
        return projectile instanceof PersistentProjectileEntity persistentProjectile
                && ((PersistentProjectileEntityAccessor) persistentProjectile).customMobsSpawner$isInGround();
    }

    private static void tickActiveAuras(MinecraftServer server) {
        ACTIVE_AURAS.entrySet().removeIf(entry -> {
            AuraState state = entry.getValue();
            Entity entity = findEntity(server, entry.getKey());
            if (!(entity instanceof LivingEntity anchor) || !anchor.isAlive() || !(anchor.getWorld() instanceof ServerWorld world)) {
                return true;
            }

            int ageTicks = state.ageTicks() + 1;
            int nextTickDelay = state.tickDelayTicks() - 1;
            if (nextTickDelay <= 0) {
                applyAuraTick(world, anchor, state.config(), ageTicks);
                nextTickDelay = state.tickIntervalTicks();
            }

            int remainingTicks = state.remainingTicks() - 1;
            if (remainingTicks > 0) {
                ACTIVE_AURAS.put(entry.getKey(), state.withTicks(remainingTicks, nextTickDelay, ageTicks));
                return false;
            }

            applyActionSet(world, anchor, state.config(), "aura_end", ageTicks);
            anchor.discard();
            return true;
        });
    }

    private static void applyReflectedActionDamage(ServerWorld world, LivingEntity target, DamageSource source, float sourceDamageAmount, JsonObject action) {
        if (sourceDamageAmount <= 0.0F) {
            return;
        }

        float multiplier = action.has("multiplier") ? action.get("multiplier").getAsFloat() : 1.0F;
        float amount = sourceDamageAmount * multiplier;
        if (amount <= 0.0F) {
            return;
        }

        boolean knockback = !action.has("knockback") || action.get("knockback").getAsBoolean();
        Entity attacker = source == null ? null : source.getAttacker();
        DamageSource damageSource = damageSourceForEntity(world, attacker);
        damage(target, damageSource, amount, knockback);
    }

    private static void applyAreaDamageAction(ServerWorld world, LivingEntity owner, JsonObject action) {
        applyAreaDamageAction(world, owner, action, null);
    }

    private static void applyAreaDamageAction(ServerWorld world, LivingEntity owner, JsonObject action, Vec3d lockedDirection) {
        float amount = actionDamageAmount(owner, action);
        double radius = action.has("radius") ? action.get("radius").getAsDouble() : 0.0D;
        if (amount <= 0.0F || radius <= 0.0D) {
            return;
        }

        boolean knockback = !action.has("knockback") || action.get("knockback").getAsBoolean();
        int fireTicks = action.has("fire_ticks") ? action.get("fire_ticks").getAsInt() : action.has("fire_sticks") ? action.get("fire_sticks").getAsInt() : 0;
        DamageSource damageSource = actionDamageSource(world, owner, action);

        for (LivingEntity target : nearbyTargets(world, owner, action, radius, lockedDirection)) {
            boolean bypassInvulnerability = action.has("bypass_invulnerability") && action.get("bypass_invulnerability").getAsBoolean();
            damage(target, damageSource, amount, knockback, bypassInvulnerability);
            if (fireTicks > 0) {
                target.setFireTicks(Math.max(target.getFireTicks(), fireTicks));
            }
        }
    }

    private static float actionDamageAmount(LivingEntity owner, JsonObject action) {
        float amount = action.has("amount") ? action.get("amount").getAsFloat() : action.has("damage") ? action.get("damage").getAsFloat() : 0.0F;
        if (action.has("use_attack_damage") && action.get("use_attack_damage").getAsBoolean()) {
            amount = (float) owner.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        }
        float scale = action.has("damage_scale") ? action.get("damage_scale").getAsFloat() : 1.0F;
        if (action.has("combo_scale")) {
            scale += activeComboCount(owner) * action.get("combo_scale").getAsFloat();
        }
        return amount * scale;
    }

    private static void applyHealAction(ServerWorld world, LivingEntity entity, JsonObject action) {
        String targetMode = actionTargetMode(action, "self");
        if ("self".equals(targetMode) || "owner".equals(targetMode)) {
            heal(entity, action);
            return;
        }

        if ("target".equals(targetMode)) {
            LivingEntity target = currentTarget(entity);
            if (target != null) {
                heal(target, action);
            }
            return;
        }

        double radius = action.has("radius") ? Math.max(0.0D, action.get("radius").getAsDouble()) : 8.0D;
        for (LivingEntity target : nearbyTargets(world, entity, action, radius)) {
            heal(target, action);
        }
    }

    private static void heal(LivingEntity target, JsonObject action) {
        float amount = healAmount(target, action);
        if (amount <= 0.0F || target.getHealth() >= target.getMaxHealth()) {
            return;
        }
        target.heal(amount);
    }

    private static float healAmount(LivingEntity target, JsonObject action) {
        float amount = action.has("amount") ? action.get("amount").getAsFloat()
                : action.has("heal") ? action.get("heal").getAsFloat() : 0.0F;
        if (action.has("current_health_percent")) {
            amount += target.getHealth() * action.get("current_health_percent").getAsFloat();
        }
        if (action.has("base_health_percent")) {
            amount += baseMaxHealth(target) * action.get("base_health_percent").getAsFloat();
        }
        if (action.has("max_health_percent")) {
            amount += target.getMaxHealth() * action.get("max_health_percent").getAsFloat();
        }
        float scale = action.has("heal_scale") ? action.get("heal_scale").getAsFloat() : 1.0F;
        return amount * scale;
    }

    private static float baseMaxHealth(LivingEntity target) {
        EntityAttributeInstance maxHealth = target.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        return maxHealth == null ? target.getMaxHealth() : (float) maxHealth.getBaseValue();
    }

    private static void applyPostureDamageAction(LivingEntity target, JsonObject action) {
        double amount = action.has("amount") ? action.get("amount").getAsDouble() : action.has("damage") ? action.get("damage").getAsDouble() : 0.0D;
        if (amount <= 0.0D) {
            return;
        }
        if (target instanceof PlayerEntity player) {
            boolean bypassInvulnerability = action.has("bypass_posture_invulnerability") && action.get("bypass_posture_invulnerability").getAsBoolean();
            boolean blockable = action.has("blockable") && action.get("blockable").getAsBoolean();
            PlayerParryHandler.applySkillPostureDamage(player, amount, bypassInvulnerability, blockable);
        } else {
            boolean staggerOnBreak = action.has("stagger_on_break") && action.get("stagger_on_break").getAsBoolean();
            MobPostureHandler.applySkillPostureDamage(target, amount, staggerOnBreak);
        }
    }

    private static void applyPostureHealAction(LivingEntity target, JsonObject action) {
        double amount = action.has("amount") ? action.get("amount").getAsDouble()
                : action.has("posture") ? action.get("posture").getAsDouble()
                : action.has("heal") ? action.get("heal").getAsDouble() : 0.0D;
        if (amount <= 0.0D) {
            return;
        }
        if (target instanceof PlayerEntity player) {
            PlayerParryHandler.restoreSkillPosture(player, amount);
        } else {
            MobPostureHandler.restoreSkillPosture(target, amount);
        }
    }

    private static void applyAreaPostureDamageAction(ServerWorld world, LivingEntity owner, JsonObject action) {
        applyAreaPostureDamageAction(world, owner, action, null);
    }

    private static void applyAreaPostureDamageAction(ServerWorld world, LivingEntity owner, JsonObject action, Vec3d lockedDirection) {
        double amount = action.has("amount") ? action.get("amount").getAsDouble() : action.has("damage") ? action.get("damage").getAsDouble() : 0.0D;
        double radius = action.has("radius") ? action.get("radius").getAsDouble() : 0.0D;
        if (amount <= 0.0D || radius <= 0.0D) {
            return;
        }

        for (LivingEntity target : nearbyTargets(world, owner, action, radius, lockedDirection)) {
            applyPostureDamageAction(target, action);
        }
    }

    private static void createExplosion(ServerWorld world, LivingEntity entity, JsonObject action) {
        float power = action.has("power") ? action.get("power").getAsFloat() : 2.0F;
        boolean createFire = action.has("create_fire") && action.get("create_fire").getAsBoolean();
        Vec3d position = actionPosition(entity, action, entity.getPos());
        int radius = Math.max(1, (int) Math.ceil(power) + 2);
        if (!SpawnSafety.isLoadedAround(world, BlockPos.ofFloored(position), radius, radius)) {
            debugSkill(action, () -> "Skipped explosion in unloaded chunk at " + BlockPos.ofFloored(position));
            return;
        }
        world.createExplosion(entity, position.x, position.y, position.z, power, createFire, explosionSourceType(action));
    }

    private static World.ExplosionSourceType explosionSourceType(JsonObject action) {
        if (action.has("source_type")) {
            return explosionSourceType(action.get("source_type").getAsString());
        }
        if (action.has("break_blocks") && !action.get("break_blocks").getAsBoolean()) {
            return World.ExplosionSourceType.NONE;
        }
        return World.ExplosionSourceType.MOB;
    }

    private static World.ExplosionSourceType explosionSourceType(String value) {
        return switch (value.toLowerCase()) {
            case "none" -> World.ExplosionSourceType.NONE;
            case "block" -> World.ExplosionSourceType.BLOCK;
            case "tnt" -> World.ExplosionSourceType.TNT;
            default -> World.ExplosionSourceType.MOB;
        };
    }

    private static void applyAreaAction(ServerWorld world, LivingEntity owner, JsonObject action) {
        applyAreaAction(world, owner, action, null);
    }

    private static void applyAreaAction(ServerWorld world, LivingEntity owner, JsonObject action, Vec3d lockedDirection) {
        if (isDamageAction(action)) {
            if (!conditionsPass(owner, action)) {
                return;
            }
            applyAreaDamageAction(world, owner, action, lockedDirection);
            return;
        }

        double radius = action.get("radius").getAsDouble();
        if (radius <= 0.0D) {
            return;
        }

        if (isKnockbackAction(action) && !conditionsPass(owner, action)) {
            return;
        }

        List<LivingEntity> targets = nearbyTargets(world, owner, action, radius, lockedDirection);
        if (isOwnerOriginSoundAction(action)) {
            if (!targets.isEmpty() && conditionsPass(owner, action)) {
                playSound(world, owner, action);
            }
            return;
        }

        for (LivingEntity target : targets) {
            if (isKnockbackAction(action)) {
                applyKnockback(owner, target, action);
                continue;
            }
            applyAction(world, target, action);
        }
    }

    private static void rallyTargetAction(ServerWorld world, LivingEntity owner, JsonObject action) {
        LivingEntity rallyTarget = currentTarget(owner);
        if (rallyTarget == null) {
            return;
        }
        if (!actionBoolean(action, "allow_non_player_target") && !(rallyTarget instanceof PlayerEntity)) {
            return;
        }

        double radius = action.has("radius") ? action.get("radius").getAsDouble() : 24.0D;
        if (radius <= 0.0D) {
            return;
        }

        int affected = 0;
        int maxTargets = action.has("max_targets") ? Math.max(0, action.get("max_targets").getAsInt()) : Integer.MAX_VALUE;
        for (LivingEntity ally : nearbyTargets(world, owner, action, radius)) {
            if (!(ally instanceof MobEntity mob) || mob == owner) {
                continue;
            }
            mob.setTarget(rallyTarget);
            if (actionBoolean(action, "stop_navigation")) {
                mob.getNavigation().stop();
            }
            affected++;
            if (affected >= maxTargets) {
                break;
            }
        }
        int affectedCount = affected;
        debugSkill(action, () -> "Rallied " + affectedCount + " mobs onto " + rallyTarget.getName().getString());
    }

    private static void clearTargetAction(LivingEntity entity, JsonObject action) {
        String targetMode = actionTargetMode(action, "self");
        if ("target".equals(targetMode)) {
            LivingEntity target = currentTarget(entity);
            if (target != null) {
                clearMobTarget(target);
            }
            return;
        }

        if ("self".equals(targetMode) || "owner".equals(targetMode)) {
            clearMobTarget(entity);
            return;
        }

        if (!(entity.getWorld() instanceof ServerWorld world)) {
            return;
        }
        double radius = action.has("radius") ? Math.max(0.0D, action.get("radius").getAsDouble()) : 8.0D;
        for (LivingEntity target : nearbyTargets(world, entity, action, radius)) {
            clearMobTarget(target);
        }
    }

    private static void clearMobTarget(LivingEntity target) {
        if (!(target instanceof MobEntity mob)) {
            return;
        }
        mob.getNavigation().stop();
        mob.setTarget(null);
        mob.setAttacking(false);
    }

    private static boolean isOwnerOriginSoundAction(JsonObject action) {
        if (!action.has("type") || !"sound".equals(action.get("type").getAsString())) {
            return false;
        }
        String origin = action.has("origin") ? action.get("origin").getAsString() : "";
        return "owner".equals(origin) || "caster".equals(origin);
    }

    private static boolean isKnockbackAction(JsonObject action) {
        return action.has("type") && "knockback".equals(action.get("type").getAsString());
    }

    private static void teleportAction(ServerWorld world, LivingEntity owner, JsonObject action) {
        List<Entity> targets = selectTeleportEntities(world, owner, action);
        for (Entity target : targets) {
            Vec3d destination = teleportDestination(owner, target, action);
            if (destination == null) {
                continue;
            }
            if (!SpawnSafety.isLoadedAround(world, BlockPos.ofFloored(destination), 1, 2)) {
                debugSkill(action, () -> "Skipped teleport into unloaded chunk at " + BlockPos.ofFloored(destination));
                continue;
            }
            if (target instanceof MobEntity mob) {
                mob.getNavigation().stop();
            }
            target.requestTeleport(destination.x, destination.y, destination.z);
            target.setVelocity(Vec3d.ZERO);
            target.velocityModified = true;
        }
    }

    private static Vec3d teleportDestination(LivingEntity owner, Entity selected, JsonObject action) {
        if (action.has("destination")) {
            JsonElement destination = action.get("destination");
            if (destination.isJsonPrimitive()) {
                return namedDestination(owner, selected, destination.getAsString());
            }
            return coordinateDestination(owner, selected, destination.getAsJsonObject());
        }
        if (action.has("target")) {
            JsonElement target = action.get("target");
            if (target.isJsonPrimitive()) {
                return namedDestination(owner, selected, target.getAsString());
            }
            return coordinateDestination(owner, selected, target.getAsJsonObject());
        }
        return null;
    }

    private static Vec3d namedDestination(LivingEntity owner, Entity selected, String target) {
        return switch (target) {
            case "self" -> owner.getPos();
            case "target" -> currentTarget(owner) == null ? null : currentTarget(owner).getPos();
            case "selected" -> selected.getPos();
            default -> null;
        };
    }

    private static Vec3d coordinateDestination(LivingEntity owner, Entity selected, JsonObject json) {
        String relativeTo = json.has("relative_to") ? json.get("relative_to").getAsString() : "selected";
        Vec3d base = switch (relativeTo) {
            case "self" -> owner.getPos();
            case "target" -> currentTarget(owner) == null ? null : currentTarget(owner).getPos();
            case "selector" -> destinationSelectorPosition(owner, selected, json);
            default -> selected.getPos();
        };
        if (base == null) {
            return null;
        }

        double x = json.has("x") ? json.get("x").getAsDouble() : 0.0D;
        double y = json.has("y") ? json.get("y").getAsDouble() : 0.0D;
        double z = json.has("z") ? json.get("z").getAsDouble() : 0.0D;
        boolean relative = !json.has("relative") || json.get("relative").getAsBoolean();
        Vec3d position = relative ? base.add(x, y, z) : new Vec3d(x, y, z);

        double forwardOffset = json.has("forward_offset") ? json.get("forward_offset").getAsDouble() : 0.0D;
        double sideOffset = json.has("side_offset") ? json.get("side_offset").getAsDouble() : 0.0D;
        double verticalOffset = json.has("vertical_offset") ? json.get("vertical_offset").getAsDouble() : 0.0D;
        if (forwardOffset != 0.0D || sideOffset != 0.0D || verticalOffset != 0.0D) {
            LivingEntity offsetOrigin = teleportOffsetOrigin(owner, selected, json, relativeTo);
            Vec3d forward = horizontalForward(offsetOrigin);
            Vec3d side = horizontalSide(forward);
            position = position.add(forward.multiply(forwardOffset)).add(side.multiply(sideOffset)).add(0.0D, verticalOffset, 0.0D);
        }
        return position;
    }

    private static LivingEntity teleportOffsetOrigin(LivingEntity owner, Entity selected, JsonObject json, String relativeTo) {
        String offsetRelativeTo = json.has("offset_relative_to") ? json.get("offset_relative_to").getAsString() : "self";
        if ("relative_to".equals(offsetRelativeTo)) {
            offsetRelativeTo = relativeTo;
        }
        return switch (offsetRelativeTo) {
            case "target" -> currentTarget(owner) == null ? owner : currentTarget(owner);
            case "selected", "selector" -> selected instanceof LivingEntity living ? living : owner;
            default -> owner;
        };
    }

    private static Vec3d destinationSelectorPosition(LivingEntity owner, Entity selected, JsonObject destination) {
        if (!destination.has("selector") || !destination.get("selector").isJsonObject() || !(owner.getWorld() instanceof ServerWorld world)) {
            return null;
        }
        List<Entity> matches = selectObjectTeleportEntities(world, owner, destination.getAsJsonObject("selector"), 0.0D);
        return matches.isEmpty() ? null : matches.get(0).getPos();
    }

    private static List<Entity> selectTeleportEntities(ServerWorld world, LivingEntity owner, JsonObject action) {
        JsonElement selector = action.has("selector") ? action.get("selector") : null;
        if (selector == null || selector.isJsonPrimitive()) {
            String selectorMode = selector == null ? "self" : selector.getAsString();
            double radius = action.has("radius") ? action.get("radius").getAsDouble() : 0.0D;
            return selectSimpleTeleportEntities(world, owner, selectorMode, radius);
        }
        return selectObjectTeleportEntities(world, owner, selector.getAsJsonObject(), action.has("radius") ? action.get("radius").getAsDouble() : 0.0D);
    }

    private static List<Entity> selectSimpleTeleportEntities(ServerWorld world, LivingEntity owner, String selector, double radius) {
        return switch (selector) {
            case "self" -> List.of(owner);
            case "target" -> {
                LivingEntity target = currentTarget(owner);
                if (target == null || (radius > 0.0D && owner.squaredDistanceTo(target) > radius * radius)) {
                    yield List.of();
                }
                yield List.of(target);
            }
            case "players", "living" -> new ArrayList<>(nearbyTargets(world, owner, selector, radius > 0.0D ? radius : 8.0D));
            default -> List.of();
        };
    }

    private static List<Entity> selectObjectTeleportEntities(ServerWorld world, LivingEntity owner, JsonObject selector, double actionRadius) {
        if (selector.has("type") && selector.get("type").isJsonPrimitive() && "target".equals(selector.get("type").getAsString())) {
            LivingEntity target = currentTarget(owner);
            if (target == null || !passesSelectorDistance(owner, target, selector, actionRadius)) {
                return List.of();
            }
            return List.of(target);
        }

        double maxDistance = selectorMaxDistance(selector, actionRadius);
        Box box = owner.getBoundingBox().expand(maxDistance);
        List<Entity> matches = new ArrayList<>();
        for (Entity candidate : world.getOtherEntities(owner, box, candidate -> passesTeleportSelector(owner, candidate, selector, actionRadius))) {
            matches.add(candidate);
        }

        String sort = selector.has("sort") ? selector.get("sort").getAsString() : "nearest";
        switch (sort) {
            case "random" -> Collections.shuffle(matches);
            case "furthest" -> matches.sort(Comparator.comparingDouble((Entity candidate) -> owner.squaredDistanceTo(candidate)).reversed());
            case "arbitrary" -> {
            }
            default -> matches.sort(Comparator.comparingDouble((Entity candidate) -> owner.squaredDistanceTo(candidate)));
        }

        int limit = selector.has("limit") ? Math.max(0, selector.get("limit").getAsInt()) : matches.size();
        if (limit < matches.size()) {
            return new ArrayList<>(matches.subList(0, limit));
        }
        return matches;
    }

    private static boolean passesTeleportSelector(LivingEntity owner, Entity candidate, JsonObject selector, double actionRadius) {
        if (!candidate.isAlive() || !passesSelectorDistance(owner, candidate, selector, actionRadius)) {
            return false;
        }
        if (!selector.has("type")) {
            return true;
        }

        String type = selector.get("type").getAsString();
        if ("players".equals(type)) {
            return candidate instanceof PlayerEntity;
        }
        if ("living".equals(type)) {
            return candidate instanceof LivingEntity;
        }
        Identifier id = Identifier.tryParse(type);
        if (id == null) {
            return false;
        }
        return Registries.ENTITY_TYPE.getId(candidate.getType()).equals(id);
    }

    private static boolean passesSelectorDistance(LivingEntity owner, Entity candidate, JsonObject selector, double actionRadius) {
        double distanceSquared = owner.squaredDistanceTo(candidate);
        double min = selectorMinDistance(selector);
        double max = selectorMaxDistance(selector, actionRadius);
        return distanceSquared >= min * min && distanceSquared <= max * max;
    }

    private static double selectorMinDistance(JsonObject selector) {
        if (!selector.has("distance")) {
            return 0.0D;
        }
        JsonElement distance = selector.get("distance");
        if (distance.isJsonObject() && distance.getAsJsonObject().has("min")) {
            return Math.max(0.0D, distance.getAsJsonObject().get("min").getAsDouble());
        }
        return 0.0D;
    }

    private static double selectorMaxDistance(JsonObject selector, double actionRadius) {
        if (selector.has("distance")) {
            JsonElement distance = selector.get("distance");
            if (distance.isJsonObject() && distance.getAsJsonObject().has("max")) {
                return Math.max(0.0D, distance.getAsJsonObject().get("max").getAsDouble());
            }
            if (distance.isJsonPrimitive()) {
                return Math.max(0.0D, distance.getAsDouble());
            }
        }
        return actionRadius > 0.0D ? actionRadius : 16.0D;
    }

    private static void projectileAction(ServerWorld world, LivingEntity owner, JsonObject action) {
        String projectileType = action.has("projectile_type") ? action.get("projectile_type").getAsString() : "minecraft:snowball";
        Identifier id = Identifier.tryParse(projectileType);
        if (id == null) {
            CustomMobsSpawnerLog.warn("Invalid projectile type " + projectileType);
            return;
        }
        EntityType<?> entityType = Registries.ENTITY_TYPE.getOrEmpty(id).orElse(null);
        if (entityType == null) {
            CustomMobsSpawnerLog.warn("Unsupported projectile type " + id);
            return;
        }

        int amount = action.has("amount") ? Math.max(1, action.get("amount").getAsInt()) : 1;
        float horizontalVelocity = action.has("horizontal_velocity")
                ? action.get("horizontal_velocity").getAsFloat()
                : action.has("velocity") ? action.get("velocity").getAsFloat() : 1.0F;
        float spread = action.has("spread") ? action.get("spread").getAsFloat() : 0.0F;
        double yOffset = action.has("y_offset") ? action.get("y_offset").getAsDouble() : owner.getHeight() * 0.65D;
        double forwardOffset = action.has("forward_offset") ? action.get("forward_offset").getAsDouble() : 0.4D;
        Vec3d origin = owner.getPos().add(0.0D, yOffset, 0.0D).add(horizontalForward(owner).multiply(forwardOffset));
        if (!SpawnSafety.isLoadedAround(world, BlockPos.ofFloored(origin), 1, 2)) {
            debugSkill(action, () -> "Skipped projectile spawn in unloaded chunk at " + BlockPos.ofFloored(origin));
            return;
        }
        boolean calculateTrajectory = action.has("calc_traj") && action.get("calc_traj").getAsBoolean();
        Vec3d direction = calculateTrajectory ? projectileTrajectoryVelocity(owner, origin, action) : projectileDirection(owner, origin, action);
        if (direction.lengthSquared() < 0.001D) {
            return;
        }

        for (int index = 0; index < amount; index++) {
            Entity entity = entityType.create(world);
            if (!(entity instanceof ProjectileEntity projectile)) {
                CustomMobsSpawnerLog.warn("Entity type " + id + " is not a projectile");
                return;
            }
            projectile.setOwner(owner);
            projectile.refreshPositionAndAngles(origin.x, origin.y, origin.z, owner.getYaw(), owner.getPitch());
            if (actionBoolean(action, "nogravity") || actionBoolean(action, "no_gravity")) {
                projectile.setNoGravity(true);
            }
            if (calculateTrajectory) {
                projectile.setVelocity(projectileSpreadVelocity(world, direction, spread));
            } else {
                projectile.setVelocity(direction.x, direction.y, direction.z, horizontalVelocity, spread);
            }
            if (action.has("hit_enemy") || action.has("hit_ground") || hasProjectileTrails(action)) {
                int maxAgeTicks = action.has("max_age_ticks")
                        ? Math.max(1, action.get("max_age_ticks").getAsInt())
                        : action.has("max_lifetime_ticks")
                        ? Math.max(1, action.get("max_lifetime_ticks").getAsInt())
                        : 200;
                prepareProjectileVanillaDespawn(projectile, maxAgeTicks);
                projectile.addCommandTag(SKILL_PROJECTILE_TAG);
                SKILL_PROJECTILES.put(projectile.getUuid(), new SkillProjectileState(owner.getUuid(), action, 0, -1L, maxAgeTicks));
            }
            world.spawnEntity(projectile);
        }
    }

    private static void grenadeProjectileAction(ServerWorld world, LivingEntity owner, JsonObject action) {
        String itemValue = action.has("item") ? action.get("item").getAsString() : "minecraft:tnt";
        Identifier itemId = Identifier.tryParse(itemValue);
        if (itemId == null) {
            CustomMobsSpawnerLog.warn("Invalid grenade item " + itemValue);
            return;
        }
        Item item = Registries.ITEM.getOrEmpty(itemId).orElse(null);
        if (item == null) {
            CustomMobsSpawnerLog.warn("Unsupported grenade item " + itemId);
            return;
        }

        int amount = action.has("amount") ? Math.max(1, action.get("amount").getAsInt()) : 1;
        double yOffset = action.has("y_offset") ? action.get("y_offset").getAsDouble() : owner.getHeight() * 0.65D;
        double forwardOffset = action.has("forward_offset") ? action.get("forward_offset").getAsDouble() : 0.45D;
        Vec3d origin = owner.getPos().add(0.0D, yOffset, 0.0D).add(horizontalForward(owner).multiply(forwardOffset));
        if (!SpawnSafety.isLoadedAround(world, BlockPos.ofFloored(origin), 1, 2)) {
            debugSkill(action, () -> "Skipped grenade spawn in unloaded chunk at " + BlockPos.ofFloored(origin));
            return;
        }
        Vec3d velocity = grenadeVelocity(owner, origin, action);
        if (velocity.lengthSquared() < 0.001D) {
            return;
        }

        int fuseTicks = grenadeFuseTicks(action);
        int maxAgeTicks = action.has("max_age_ticks")
                ? Math.max(1, action.get("max_age_ticks").getAsInt())
                : action.has("max_lifetime_ticks")
                ? Math.max(1, action.get("max_lifetime_ticks").getAsInt())
                : 200;
        maxAgeTicks = Math.min(maxAgeTicks, 200);
        fuseTicks = Math.min(fuseTicks, maxAgeTicks);
        float spread = action.has("spread") ? action.get("spread").getAsFloat() : 0.0F;

        for (int index = 0; index < amount; index++) {
            ItemEntity grenade = new ItemEntity(world, origin.x, origin.y, origin.z, new ItemStack(item));
            grenade.setOwner(owner.getUuid());
            grenade.setPickupDelay(32767);
            prepareGrenadeVanillaDespawn(grenade, maxAgeTicks);
            grenade.setVelocity(projectileSpreadVelocity(world, velocity, spread));
            grenade.setNoGravity(actionBoolean(action, "nogravity") || actionBoolean(action, "no_gravity"));
            grenade.addCommandTag("cmobs_grenade_projectile");
            if (world.spawnEntity(grenade)) {
                GRENADE_PROJECTILES.put(grenade.getUuid(), new GrenadeProjectileState(owner.getUuid(), action, 0, -1L, fuseTicks, maxAgeTicks));
            }
        }
    }

    private static void prepareGrenadeVanillaDespawn(ItemEntity grenade, int maxAgeTicks) {
        int vanillaItemDespawnTicks = 6000;
        int age = Math.max(0, vanillaItemDespawnTicks - maxAgeTicks);
        NbtCompound nbt = new NbtCompound();
        grenade.writeNbt(nbt);
        nbt.putShort("Age", (short) age);
        nbt.putShort("PickupDelay", Short.MAX_VALUE);
        grenade.readNbt(nbt);
    }

    private static void spawnGrenadeTrails(ServerWorld world, ItemEntity grenade, JsonObject config, int ageTicks) {
        spawnProjectileTrails(world, grenade, config, ageTicks);
    }

    private static void detonateGrenade(ServerWorld world, LivingEntity owner, ItemEntity grenade, JsonObject config) {
        ArmorStandEntity anchor = EntityType.ARMOR_STAND.create(world);
        if (anchor == null) {
            return;
        }

        Vec3d position = grenade.getPos();
        if (!SpawnSafety.isLoadedAround(world, BlockPos.ofFloored(position), 1, 2)) {
            return;
        }
        anchor.refreshPositionAndAngles(position.x, position.y, position.z, owner.getYaw(), 0.0F);
        anchor.setInvisible(true);
        anchor.setInvulnerable(true);
        anchor.setNoGravity(true);
        anchor.setSilent(true);
        anchor.addCommandTag("cmobs_grenade_anchor");
        NbtCompound anchorNbt = new NbtCompound();
        anchor.writeNbt(anchorNbt);
        anchorNbt.putBoolean("Marker", true);
        anchorNbt.putBoolean("Invisible", true);
        anchorNbt.putBoolean("NoGravity", true);
        anchorNbt.putBoolean("Invulnerable", true);
        anchorNbt.putBoolean("Silent", true);
        anchor.readNbt(anchorNbt);
        world.spawnEntity(anchor);

        applyActionSet(world, anchor, config, "detonate", 0);
        if (config.has("detonate_actions")) {
            applyGrenadeDetonateActions(world, owner, anchor, config);
        }
        anchor.discard();
    }

    private static void applyGrenadeDetonateActions(ServerWorld world, LivingEntity owner, LivingEntity anchor, JsonObject config) {
        for (JsonElement element : config.getAsJsonArray("detonate_actions")) {
            JsonObject action = element.getAsJsonObject();
            if (!actionIntervalReady(action, 0)) {
                continue;
            }
            String type = action.has("type") ? action.get("type").getAsString() : "";
            if ("spell_engine_impact".equals(type) || "spell_impact".equals(type)) {
                spellEngineImpactAction(world, owner, anchor, action);
                continue;
            }
            if ("spell_engine_rain".equals(type) || "spell_rain".equals(type)) {
                spellEngineRainAction(world, owner, anchor, action);
                continue;
            }
            applyAction(world, anchor, action);
        }
    }

    private static Vec3d grenadeVelocity(LivingEntity owner, Vec3d origin, JsonObject action) {
        if (actionBoolean(action, "calc_traj")) {
            return projectileTrajectoryVelocity(owner, origin, action);
        }

        Vec3d direction = projectileDirection(owner, origin, action);
        if (direction.lengthSquared() < 0.001D) {
            return Vec3d.ZERO;
        }

        double horizontalVelocity = action.has("horizontal_velocity")
                ? action.get("horizontal_velocity").getAsDouble()
                : action.has("velocity") ? action.get("velocity").getAsDouble() : 0.6D;
        double verticalVelocity = action.has("vertical_velocity")
                ? action.get("vertical_velocity").getAsDouble()
                : action.has("arc") ? action.get("arc").getAsDouble() : 0.45D;
        Vec3d horizontal = new Vec3d(direction.x, 0.0D, direction.z);
        if (horizontal.lengthSquared() < 0.001D) {
            horizontal = horizontalForward(owner);
        } else {
            horizontal = horizontal.normalize();
        }
        return horizontal.multiply(horizontalVelocity).add(0.0D, verticalVelocity, 0.0D);
    }

    private static int grenadeFuseTicks(JsonObject action) {
        if (action.has("fuse_ticks")) {
            return Math.max(1, action.get("fuse_ticks").getAsInt());
        }
        if (action.has("detonate_ticks")) {
            return Math.max(1, action.get("detonate_ticks").getAsInt());
        }
        if (action.has("timer_ticks")) {
            return Math.max(1, action.get("timer_ticks").getAsInt());
        }
        return 60;
    }

    private static void spellEngineSpellAction(ServerWorld world, LivingEntity caster, JsonObject action) {
        if (!action.has("spell")) {
            CustomMobsSpawnerLog.warn("Spell Engine skill action missing spell id");
            return;
        }

        Identifier spellId = Identifier.tryParse(action.get("spell").getAsString());
        if (spellId == null) {
            CustomMobsSpawnerLog.warn("Invalid Spell Engine spell " + action.get("spell").getAsString());
            return;
        }
        Spell registrySpell = SpellRegistry.getSpell(spellId);
        if (registrySpell == null) {
            CustomMobsSpawnerLog.warn("Unknown Spell Engine spell " + spellId);
            return;
        }
        Spell spell = adjustedSpell(registrySpell, action);
        if (spell.release == null || spell.release.target == null || spell.release.target.projectile == null || spell.release.target.projectile.projectile == null) {
            CustomMobsSpawnerLog.warn("Spell Engine spell " + spellId + " is not a projectile spell");
            return;
        }

        String targetMode = action.has("target") ? action.get("target").getAsString() : "target";
        boolean forwardOnly = "forward".equals(targetMode);
        LivingEntity target = forwardOnly ? null : resolveSpellTarget(caster, action);
        if (!forwardOnly && target == null) {
            debugSkill(action, () -> "Spell Engine spell " + spellId + " skipped: no target for " + caster.getName().getString()
                    + " players_in_world=" + world.getPlayers().size());
            return;
        }

        lookAtSpellTarget(caster, target, action);

        SpellInfo spellInfo = new SpellInfo(spell, spellId);
        SpellHelper.ImpactContext impactContext = new SpellHelper.ImpactContext(
                action.has("channel") ? action.get("channel").getAsFloat() : 1.0F,
                target == null ? 0.0F : (float) caster.distanceTo(target),
                target == null ? null : projectileTargetPos(target, action),
                spellPower(caster, spell, action),
                SpellHelper.impactTargetingMode(spell)
        );

        int count = action.has("count") ? Math.max(1, action.get("count").getAsInt()) : 1;
        boolean useSpellEngineAim = actionBoolean(action, "use_spell_engine_aim");
        for (int index = 0; index < count; index++) {
            if (target != null && !useSpellEngineAim) {
                shootTargetedSpellProjectile(world, caster, target, spellInfo, impactContext, action);
            } else {
                SpellHelper.shootProjectile(world, caster, target, spellInfo, impactContext, index);
                debugSkill(action, () -> "Fired Spell Engine spell " + spellId + " with SpellHelper from " + caster.getName().getString());
            }
        }

        if (!action.has("play_release_effects") || actionBoolean(action, "play_release_effects")) {
            if (spell.release.particles != null) {
                ParticleHelper.sendBatches(caster, spell.release.particles);
            }
            if (spell.release.sound != null) {
                SoundHelper.playSound(world, caster, spell.release.sound);
            }
        }
    }

    private static void spellEngineImpactAction(ServerWorld world, LivingEntity caster, LivingEntity origin, JsonObject action) {
        if (!action.has("spell")) {
            CustomMobsSpawnerLog.warn("Spell Engine impact action missing spell id");
            return;
        }

        Identifier spellId = Identifier.tryParse(action.get("spell").getAsString());
        if (spellId == null) {
            CustomMobsSpawnerLog.warn("Invalid Spell Engine spell " + action.get("spell").getAsString());
            return;
        }
        Spell registrySpell = SpellRegistry.getSpell(spellId);
        if (registrySpell == null) {
            CustomMobsSpawnerLog.warn("Unknown Spell Engine spell " + spellId);
            return;
        }

        Spell spell = adjustedSpell(registrySpell, action);
        SpellInfo spellInfo = new SpellInfo(spell, spellId);
        Vec3d position = origin.getPos();
        SpellHelper.ImpactContext impactContext = new SpellHelper.ImpactContext(
                action.has("channel") ? action.get("channel").getAsFloat() : 1.0F,
                0.0F,
                position,
                spellPower(caster, spell, action),
                SpellHelper.impactTargetingMode(spell)
        );

        if (!action.has("play_release_effects") || actionBoolean(action, "play_release_effects")) {
            if (spell.release != null && spell.release.particles != null) {
                ParticleHelper.sendBatches(origin, spell.release.particles);
            }
            if (spell.release != null && spell.release.sound != null) {
                SoundHelper.playSound(world, origin, spell.release.sound);
            }
        }

        double radius = action.has("radius") ? action.get("radius").getAsDouble() : spell.range;
        if (radius <= 0.0D) {
            return;
        }
        JsonObject selector = action.deepCopy();
        if (!selector.has("target") && !selector.has("selector")) {
            selector.addProperty("target", "living");
        }
        for (LivingEntity target : nearbyTargets(world, origin, selector, radius)) {
            SpellHelper.performImpacts(world, caster, target, origin, spellInfo, impactContext, false);
            applySpellEngineImpactOverrideDamage(world, caster, target, action);
        }
        debugSkill(action, () -> "Performed Spell Engine impact " + spellId + " at " + position);
    }

    private static void spellEngineRainAction(ServerWorld world, LivingEntity caster, LivingEntity origin, JsonObject action) {
        if (!action.has("spell")) {
            CustomMobsSpawnerLog.warn("Spell Engine rain action missing spell id");
            return;
        }

        Identifier spellId = Identifier.tryParse(action.get("spell").getAsString());
        if (spellId == null) {
            CustomMobsSpawnerLog.warn("Invalid Spell Engine rain spell " + action.get("spell").getAsString());
            return;
        }
        Spell registrySpell = SpellRegistry.getSpell(spellId);
        if (registrySpell == null) {
            CustomMobsSpawnerLog.warn("Unknown Spell Engine spell " + spellId);
            return;
        }

        Spell spell = adjustedSpell(registrySpell, action);
        if (spell.release == null || spell.release.target == null || spell.release.target.projectile == null || spell.release.target.projectile.projectile == null) {
            CustomMobsSpawnerLog.warn("Spell Engine rain spell " + spellId + " is not a projectile spell");
            return;
        }
        if (spell.release.target.projectile.projectile.perks == null) {
            CustomMobsSpawnerLog.warn("Spell Engine rain spell " + spellId + " projectile has no perks data");
            return;
        }

        double radius = action.has("radius") ? action.get("radius").getAsDouble() : action.has("target_range") ? action.get("target_range").getAsDouble() : spell.range;
        if (radius <= 0.0D) {
            return;
        }
        JsonObject selector = action.deepCopy();
        if (!selector.has("target") && !selector.has("selector")) {
            selector.addProperty("target", "living");
        }

        SpellInfo spellInfo = new SpellInfo(spell, spellId);
        double launchHeight = action.has("launch_height") ? action.get("launch_height").getAsDouble() : 8.0D;
        double launchRadius = action.has("launch_radius") ? action.get("launch_radius").getAsDouble() : 1.5D;
        float velocity = action.has("velocity")
                ? action.get("velocity").getAsFloat()
                : spell.release.target.projectile.launch_properties.velocity;
        int count = action.has("count") ? Math.max(1, action.get("count").getAsInt()) : 1;
        String spawnMode = action.has("spawn") ? action.get("spawn").getAsString() : "target";
        if ("origin".equals(spawnMode) || "grenade".equals(spawnMode)) {
            double acquireRange = action.has("acquire_range") ? action.get("acquire_range").getAsDouble() : radius;
            LivingEntity target = nearestSpellRainTarget(world, origin, selector, acquireRange);
            if (target == null) {
                debugSkill(action, () -> "Spell Engine rain " + spellId + " found no target within " + acquireRange);
                return;
            }
            for (int index = 0; index < count; index++) {
                spawnOriginSpellRainProjectile(world, caster, origin, target, spellInfo, action, launchRadius, velocity);
            }
            playSpellReleaseEffects(world, origin, spell, action);
            debugSkill(action, () -> "Launched Spell Engine rain " + spellId + " from origin at " + target.getName().getString());
            return;
        }

        int affected = 0;
        for (LivingEntity target : nearbyTargets(world, origin, selector, radius)) {
            for (int index = 0; index < count; index++) {
                spawnSpellRainProjectile(world, caster, target, spellInfo, action, launchHeight, launchRadius, velocity, index);
            }
            affected++;
        }

        playSpellReleaseEffects(world, origin, spell, action);
        int affectedCount = affected;
        debugSkill(action, () -> "Rained Spell Engine spell " + spellId + " at " + affectedCount + " targets");
    }

    private static LivingEntity nearestSpellRainTarget(ServerWorld world, LivingEntity origin, JsonObject selector, double radius) {
        return nearbyTargets(world, origin, selector, radius).stream()
                .min(Comparator.comparingDouble(target -> target.squaredDistanceTo(origin)))
                .orElse(null);
    }

    private static void staticChainAction(ServerWorld world, LivingEntity caster, JsonObject action) {
        String spellValue = action.has("spell") ? action.get("spell").getAsString() : "cmobs:static_beam";
        Identifier spellId = Identifier.tryParse(spellValue);
        if (spellId == null) {
            CustomMobsSpawnerLog.warn("Invalid static chain spell " + spellValue);
            return;
        }
        Spell spell = SpellRegistry.getSpell(spellId);
        if (spell == null) {
            CustomMobsSpawnerLog.warn("Unknown static chain spell " + spellId);
            return;
        }

        double range = action.has("range") ? Math.max(0.0D, action.get("range").getAsDouble()) : 8.0D;
        float chainChance = action.has("chain_chance") ? Math.max(0.0F, Math.min(1.0F, action.get("chain_chance").getAsFloat())) : 0.35F;
        int maxChains = action.has("max_chains") ? Math.max(0, action.get("max_chains").getAsInt()) : 1;
        ParticleEffect beamParticle = particle(action.has("beam_particle") ? action.get("beam_particle").getAsString() : "spell_engine:electric_arc_a");
        ParticleEffect impactParticle = particle(action.has("impact_particle") ? action.get("impact_particle").getAsString() : "spell_engine:white_spark_mini");
        double yScale = action.has("target_y_scale") ? action.get("target_y_scale").getAsDouble() : 0.6D;

        SpellInfo spellInfo = new SpellInfo(spell, spellId);
        LivingEntity source = caster;
        List<LivingEntity> hitTargets = new ArrayList<>();
        int chainIndex = 0;
        while (chainIndex <= maxChains) {
            LivingEntity target = nearestStaticChainTarget(world, caster, source, hitTargets, range);
            if (target == null) {
                return;
            }

            Vec3d sourcePos = source.getPos().add(0.0D, source.getHeight() * yScale, 0.0D);
            Vec3d targetPos = target.getPos().add(0.0D, target.getHeight() * yScale, 0.0D);
            spawnStaticBeamParticles(world, sourcePos, targetPos, beamParticle, action);
            spawnStaticImpactParticles(world, targetPos, impactParticle, action);

            SpellHelper.ImpactContext impactContext = new SpellHelper.ImpactContext(
                    action.has("channel") ? action.get("channel").getAsFloat() : 1.0F,
                    (float) source.distanceTo(target),
                    targetPos,
                    spellPower(caster, spell, action),
                    SpellHelper.impactTargetingMode(spell)
            );
            performStaticChainImpacts(world, caster, target, source, spellInfo, impactContext, action);
            hitTargets.add(target);

            if (chainIndex >= maxChains || world.random.nextFloat() >= chainChance) {
                return;
            }
            source = target;
            chainIndex++;
        }
    }

    private static void performStaticChainImpacts(
            ServerWorld world,
            LivingEntity caster,
            LivingEntity target,
            LivingEntity source,
            SpellInfo spellInfo,
            SpellHelper.ImpactContext impactContext,
            JsonObject action
    ) {
        if (staticChainContributesCombo(action)) {
            SpellHelper.performImpacts(world, caster, target, source, spellInfo, impactContext, false);
            return;
        }

        SpellComboHandler.beginComboGainSuppression();
        try {
            SpellHelper.performImpacts(world, caster, target, source, spellInfo, impactContext, false);
        } finally {
            SpellComboHandler.endComboGainSuppression();
        }
    }

    private static boolean staticChainContributesCombo(JsonObject action) {
        return !action.has("contribute_combo") || action.get("contribute_combo").getAsBoolean();
    }

    private static LivingEntity nearestStaticChainTarget(ServerWorld world, LivingEntity caster, LivingEntity source, List<LivingEntity> excluded, double range) {
        double rangeSquared = range * range;
        Box box = source.getBoundingBox().expand(range);
        return world.getEntitiesByClass(LivingEntity.class, box, target -> {
                    if (target == caster || target == source || excluded.contains(target) || !target.isAlive() || target.squaredDistanceTo(source) > rangeSquared) {
                        return false;
                    }
                    if (caster instanceof PlayerEntity) {
                        return target instanceof MobEntity;
                    }
                    if (caster instanceof MobEntity) {
                        return target instanceof PlayerEntity && isValidSkillTarget(target);
                    }
                    return target instanceof MobEntity;
                }).stream()
                .min(Comparator.comparingDouble(target -> target.squaredDistanceTo(source)))
                .orElse(null);
    }

    private static void spawnStaticBeamParticles(ServerWorld world, Vec3d start, Vec3d end, ParticleEffect particle, JsonObject action) {
        Vec3d delta = end.subtract(start);
        double distance = delta.length();
        if (distance <= 0.01D) {
            return;
        }

        int points = action.has("beam_points") ? Math.max(1, action.get("beam_points").getAsInt()) : Math.max(4, (int) Math.ceil(distance * 4.0D));
        double spread = action.has("beam_spread") ? Math.max(0.0D, action.get("beam_spread").getAsDouble()) : 0.025D;
        double pointSpread = action.has("beam_point_spread") ? Math.max(0.0D, action.get("beam_point_spread").getAsDouble()) : 0.0D;
        double speed = action.has("beam_speed") ? Math.max(0.0D, action.get("beam_speed").getAsDouble()) : 0.0D;
        for (int index = 0; index <= points; index++) {
            double progress = (double) index / (double) points;
            Vec3d position = start.add(delta.multiply(progress));
            if (pointSpread > 0.0D && index > 0 && index < points) {
                position = position.add(
                        (world.random.nextDouble() - 0.5D) * pointSpread,
                        (world.random.nextDouble() - 0.5D) * pointSpread,
                        (world.random.nextDouble() - 0.5D) * pointSpread
                );
            }
            world.spawnParticles(particle, position.x, position.y, position.z, 1, spread, spread, spread, speed);
        }
    }

    private static void spawnStaticImpactParticles(ServerWorld world, Vec3d position, ParticleEffect particle, JsonObject action) {
        int count = action.has("impact_particles") ? Math.max(0, action.get("impact_particles").getAsInt()) : 12;
        double spread = action.has("impact_spread") ? Math.max(0.0D, action.get("impact_spread").getAsDouble()) : 0.25D;
        double speed = action.has("impact_speed") ? Math.max(0.0D, action.get("impact_speed").getAsDouble()) : 0.08D;
        if (count > 0) {
            world.spawnParticles(particle, position.x, position.y, position.z, count, spread, spread, spread, speed);
        }
    }

    private static void playSpellReleaseEffects(ServerWorld world, LivingEntity origin, Spell spell, JsonObject action) {
        if (action.has("play_release_effects") && !actionBoolean(action, "play_release_effects")) {
            return;
        }
        if (spell.release.particles != null) {
            ParticleHelper.sendBatches(origin, spell.release.particles);
        }
        if (spell.release.sound != null) {
            SoundHelper.playSound(world, origin, spell.release.sound);
        }
    }

    private static void spawnOriginSpellRainProjectile(ServerWorld world, LivingEntity caster, LivingEntity originEntity, LivingEntity target, SpellInfo spellInfo, JsonObject action, double launchRadius, float velocity) {
        Spell spell = spellInfo.spell();
        double angle = world.random.nextDouble() * Math.PI * 2.0D;
        double distance = launchRadius <= 0.0D ? 0.0D : world.random.nextDouble() * launchRadius;
        Vec3d offset = new Vec3d(Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance);
        double yOffset = action.has("spawn_y_offset") ? action.get("spawn_y_offset").getAsDouble() : 0.8D;
        Vec3d origin = originEntity.getPos().add(offset).add(0.0D, yOffset, 0.0D);
        if (!SpawnSafety.isLoadedAround(world, BlockPos.ofFloored(origin), 1, 2)) {
            debugSkill(action, () -> "Skipped origin spell rain projectile in unloaded chunk at " + BlockPos.ofFloored(origin));
            return;
        }
        Vec3d aim = projectileTargetPos(target, action);
        Spell.ProjectileData.Perks perks = spell.release.target.projectile.projectile.perks.copy();
        SpellHelper.ImpactContext impactContext = new SpellHelper.ImpactContext(
                action.has("channel") ? action.get("channel").getAsFloat() : 1.0F,
                action.has("range") ? action.get("range").getAsFloat() : spell.range,
                aim,
                spellPower(caster, spell, action),
                SpellHelper.impactTargetingMode(spell)
        );
        SpellProjectile projectile = new SpellProjectile(
                world,
                caster,
                origin.x,
                origin.y,
                origin.z,
                SpellProjectile.Behaviour.FLY,
                spellInfo.id(),
                target,
                impactContext,
                perks
        );
        projectile.setFollowedTarget(target);
        projectile.setPitch(-90.0F);
        projectile.prevPitch = projectile.getPitch();
        projectile.setVelocity(0.0D, Math.max(0.01F, velocity), 0.0D);
        projectile.range = action.has("range") ? action.get("range").getAsFloat() : Math.max(16.0F, spell.range);
        registerSpellProjectileDamageOverride(world, projectile, action);
        boolean spawned = world.spawnEntity(projectile);
        if (!spawned) {
            SPELL_PROJECTILE_DAMAGE_OVERRIDES.remove(projectile.getUuid());
        }
    }

    private static void spawnSpellRainProjectile(ServerWorld world, LivingEntity caster, LivingEntity target, SpellInfo spellInfo, JsonObject action, double launchHeight, double launchRadius, float velocity, int sequenceIndex) {
        Spell spell = spellInfo.spell();
        double angle = world.random.nextDouble() * Math.PI * 2.0D;
        double distance = launchRadius <= 0.0D ? 0.0D : world.random.nextDouble() * launchRadius;
        Vec3d offset = new Vec3d(Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance);
        Vec3d origin = projectileTargetPos(target, action).add(offset).add(0.0D, launchHeight, 0.0D);
        if (!SpawnSafety.isLoadedAround(world, BlockPos.ofFloored(origin), 1, 2)) {
            debugSkill(action, () -> "Skipped spell rain projectile in unloaded chunk at " + BlockPos.ofFloored(origin));
            return;
        }
        Spell.ProjectileData.Perks perks = spell.release.target.projectile.projectile.perks.copy();
        SpellHelper.ImpactContext impactContext = new SpellHelper.ImpactContext(
                action.has("channel") ? action.get("channel").getAsFloat() : 1.0F,
                (float) launchHeight,
                projectileTargetPos(target, action),
                spellPower(caster, spell, action),
                SpellHelper.impactTargetingMode(spell)
        );
        SpellProjectile projectile = new SpellProjectile(
                world,
                caster,
                origin.x,
                origin.y,
                origin.z,
                SpellProjectile.Behaviour.FALL,
                spellInfo.id(),
                target,
                impactContext,
                perks
        );
        projectile.setPitch(90.0F);
        projectile.prevPitch = projectile.getPitch();
        projectile.setVelocity(0.0D, -Math.max(0.01F, velocity), 0.0D);
        projectile.range = action.has("range") ? action.get("range").getAsFloat() : (float) (launchHeight + 4.0D);
        registerSpellProjectileDamageOverride(world, projectile, action);
        boolean spawned = world.spawnEntity(projectile);
        if (!spawned) {
            SPELL_PROJECTILE_DAMAGE_OVERRIDES.remove(projectile.getUuid());
        }
    }

    private static void applySpellEngineImpactOverrideDamage(ServerWorld world, LivingEntity caster, LivingEntity target, JsonObject action) {
        if (!action.has("damage_override")) {
            return;
        }

        float amount = Math.max(0.0F, action.get("damage_override").getAsFloat());
        if (amount <= 0.0F) {
            return;
        }

        DamageSource damageSource = damageSourceForOwner(world, caster);
        boolean knockback = !action.has("knockback") || action.get("knockback").getAsBoolean();
        boolean bypassInvulnerability = actionBoolean(action, "bypass_invulnerability");
        damage(target, damageSource, amount, knockback, bypassInvulnerability);
    }

    private static Spell adjustedSpell(Spell spell, JsonObject action) {
        boolean hasImpactAdjustment = action.has("damage_override")
                || action.has("impact_damage_multiplier")
                || action.has("impact_min_power");
        if (!hasImpactAdjustment || spell.impact == null || spell.impact.length == 0) {
            return spell;
        }

        Spell adjusted = new Spell();
        adjusted.school = spell.school;
        adjusted.range = spell.range;
        adjusted.group = spell.group;
        adjusted.learn = spell.learn;
        adjusted.mode = spell.mode;
        adjusted.cast = spell.cast;
        adjusted.item_use = spell.item_use;
        adjusted.arrow_perks = spell.arrow_perks;
        adjusted.release = spell.release;
        adjusted.area_impact = spell.area_impact;
        adjusted.cost = spell.cost;
        adjusted.impact = new Spell.Impact[spell.impact.length];

        for (int index = 0; index < spell.impact.length; index++) {
            adjusted.impact[index] = adjustedImpact(spell.impact[index], action);
        }
        return adjusted;
    }

    private static Spell.Impact adjustedImpact(Spell.Impact impact, JsonObject action) {
        if (impact == null) {
            return null;
        }

        Spell.Impact adjusted = new Spell.Impact();
        adjusted.school = impact.school;
        adjusted.particles = impact.particles;
        adjusted.sound = impact.sound;
        adjusted.action = adjustedImpactAction(impact.action, action);
        return adjusted;
    }

    private static Spell.Impact.Action adjustedImpactAction(Spell.Impact.Action impactAction, JsonObject action) {
        if (impactAction == null) {
            return null;
        }

        Spell.Impact.Action adjusted = new Spell.Impact.Action();
        adjusted.type = impactAction.type;
        adjusted.apply_to_caster = impactAction.apply_to_caster;
        adjusted.min_power = action.has("impact_min_power") ? action.get("impact_min_power").getAsFloat() : impactAction.min_power;
        adjusted.damage = adjustedImpactDamage(impactAction.damage, action);
        adjusted.heal = impactAction.heal;
        adjusted.status_effect = impactAction.status_effect;
        adjusted.fire = impactAction.fire;
        adjusted.spawn = impactAction.spawn;
        adjusted.spawns = impactAction.spawns;
        adjusted.teleport = impactAction.teleport;
        return adjusted;
    }

    private static Spell.Impact.Action.Damage adjustedImpactDamage(Spell.Impact.Action.Damage damage, JsonObject action) {
        if (damage == null) {
            return null;
        }

        Spell.Impact.Action.Damage adjusted = new Spell.Impact.Action.Damage();
        adjusted.bypass_iframes = damage.bypass_iframes;
        adjusted.knockback = damage.knockback;
        if (action.has("damage_override")) {
            adjusted.spell_power_coefficient = action.get("damage_override").getAsFloat();
        } else {
            float multiplier = action.has("impact_damage_multiplier") ? action.get("impact_damage_multiplier").getAsFloat() : 1.0F;
            adjusted.spell_power_coefficient = damage.spell_power_coefficient * Math.max(0.0F, multiplier);
        }
        return adjusted;
    }

    private static SpellPower.Result spellPower(LivingEntity caster, Spell spell, JsonObject action) {
        SpellPower.Result power = SpellPower.getSpellPower(spell.school, caster);
        if (action.has("damage_override")) {
            double coefficient = firstDamageCoefficient(spell);
            double baseValue = coefficient <= 0.0D ? action.get("damage_override").getAsDouble() : action.get("damage_override").getAsDouble() / coefficient;
            return new SpellPower.Result(power.school(), Math.max(0.0D, baseValue), 0.0D, power.criticalDamage());
        }
        if (action.has("attack_damage_scale") || action.has("spell_power_scale") || action.has("flat_damage")) {
            double attackDamageScale = action.has("attack_damage_scale") ? Math.max(0.0D, action.get("attack_damage_scale").getAsDouble()) : 0.0D;
            double spellPowerScale = action.has("spell_power_scale") ? Math.max(0.0D, action.get("spell_power_scale").getAsDouble()) : 1.0D;
            double flatDamage = action.has("flat_damage") ? Math.max(0.0D, action.get("flat_damage").getAsDouble()) : 0.0D;
            double damage = flatDamage + caster.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE) * attackDamageScale + power.baseValue() * spellPowerScale;
            double coefficient = firstDamageCoefficient(spell);
            double baseValue = coefficient <= 0.0D ? damage : damage / coefficient;
            return new SpellPower.Result(power.school(), Math.max(0.0D, baseValue), power.criticalChance(), power.criticalDamage());
        }
        double multiplier = action.has("power_multiplier")
                ? Math.max(0.0D, action.get("power_multiplier").getAsDouble())
                : action.has("damage_multiplier") ? Math.max(0.0D, action.get("damage_multiplier").getAsDouble()) : 1.0D;
        if (multiplier == 1.0D) {
            return power;
        }
        return new SpellPower.Result(
                power.school(),
                power.baseValue() * multiplier,
                power.criticalChance(),
                power.criticalDamage()
        );
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

    private static LivingEntity resolveSpellTarget(LivingEntity caster, JsonObject action) {
        LivingEntity target = currentTarget(caster);
        if (target != null) {
            return target;
        }

        double targetRange = action.has("target_range") ? action.get("target_range").getAsDouble() : 48.0D;
        target = nearestPlayerTarget(caster, targetRange);
        if (target != null && caster instanceof MobEntity mob) {
            mob.setTarget(target);
        }
        return target;
    }

    private static void shootTargetedSpellProjectile(ServerWorld world, LivingEntity caster, Entity target, SpellInfo spellInfo, SpellHelper.ImpactContext impactContext, JsonObject action) {
        Spell spell = spellInfo.spell();
        Vec3d origin = SpellHelper.launchPoint(caster);
        if (!SpawnSafety.isLoadedAround(world, BlockPos.ofFloored(origin), 1, 2)) {
            debugSkill(action, () -> "Skipped targeted Spell Engine projectile in unloaded chunk at " + BlockPos.ofFloored(origin));
            return;
        }
        Spell.ProjectileData projectileData = spell.release.target.projectile.projectile;
        if (projectileData.perks == null) {
            CustomMobsSpawnerLog.warn("Spell Engine spell " + spellInfo.id() + " projectile has no perks data");
            return;
        }
        Spell.ProjectileData.Perks perks = projectileData.perks.copy();
        SpellProjectile projectile = new SpellProjectile(
                world,
                caster,
                origin.x,
                origin.y,
                origin.z,
                SpellProjectile.Behaviour.FLY,
                spellInfo.id(),
                target,
                impactContext,
                perks
        );

        Vec3d targetPos = target instanceof LivingEntity livingTarget ? projectileTargetPos(livingTarget, action) : target.getPos();
        Vec3d direction = targetPos.subtract(origin);
        if (direction.lengthSquared() < 0.001D) {
            direction = caster.getRotationVec(1.0F);
        }

        float velocity = action.has("velocity")
                ? action.get("velocity").getAsFloat()
                : spell.release.target.projectile.launch_properties.velocity;
        float divergence = action.has("divergence")
                ? action.get("divergence").getAsFloat()
                : projectileData.divergence;
        projectile.setVelocity(direction.x, direction.y, direction.z, velocity, divergence);
        projectile.range = action.has("range") ? action.get("range").getAsFloat() : spell.range;
        projectile.setYaw(caster.getYaw());
        projectile.setPitch(caster.getPitch());
        registerSpellProjectileDamageOverride(world, projectile, action);
        boolean spawned = world.spawnEntity(projectile);
        if (!spawned) {
            SPELL_PROJECTILE_DAMAGE_OVERRIDES.remove(projectile.getUuid());
        }
        debugSkill(action, () -> "Fired targeted Spell Engine spell " + spellInfo.id() + " from " + caster.getName().getString()
                + " at " + target.getName().getString() + " spawned=" + spawned + " velocity=" + velocity);
    }

    private static void registerSpellProjectileDamageOverride(ServerWorld world, SpellProjectile projectile, JsonObject action) {
        if (!action.has("damage_override")) {
            return;
        }

        long now = world.getTime();
        SPELL_PROJECTILE_DAMAGE_OVERRIDES.entrySet().removeIf(entry -> entry.getValue().expiresAt < now);
        float amount = Math.max(0.0F, action.get("damage_override").getAsFloat());
        SPELL_PROJECTILE_DAMAGE_OVERRIDES.put(
                projectile.getUuid(),
                new SpellProjectileDamageOverride(amount, now + SPELL_PROJECTILE_OVERRIDE_TTL_TICKS)
        );
    }

    private static void debugSkill(JsonObject action, Supplier<String> message) {
        if (action.has("debug") && action.get("debug").getAsBoolean()) {
            CustomMobsSpawnerLog.info(message.get());
        }
    }

    private static void lookAtSpellTarget(LivingEntity caster, LivingEntity target, JsonObject action) {
        if (target == null) {
            return;
        }
        if (caster instanceof MobEntity mob) {
            mob.lookAtEntity(target, 30.0F, 30.0F);
            mob.getLookControl().lookAt(target, 30.0F, 30.0F);
        }

        Rotation rotation = lookRotation(caster.getEyePos(), projectileTargetPos(target, action));
        if (rotation == null) {
            return;
        }
        applyLookRotation(caster, rotation);
    }

    private static void faceTarget(LivingEntity entity, LivingEntity target, JsonObject action) {
        Rotation rotation = lookRotation(entity.getEyePos(), projectileTargetPos(target, action));
        if (rotation == null) {
            return;
        }
        applyLookRotation(entity, rotation);
        if (entity instanceof ServerPlayerEntity player) {
            player.networkHandler.requestTeleport(entity.getX(), entity.getY(), entity.getZ(), rotation.yaw(), rotation.pitch());
        }
    }

    private static Rotation lookRotation(Vec3d from, Vec3d to) {
        Vec3d direction = to.subtract(from);
        double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        if (horizontal < 0.001D) {
            return null;
        }
        return new Rotation(
                (float) (Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0D),
                (float) (-Math.toDegrees(Math.atan2(direction.y, horizontal)))
        );
    }

    private static void applyLookRotation(LivingEntity entity, Rotation rotation) {
        entity.setYaw(rotation.yaw());
        entity.setPitch(rotation.pitch());
        entity.setHeadYaw(rotation.yaw());
        entity.setBodyYaw(rotation.yaw());
    }

    private static void prepareProjectileVanillaDespawn(ProjectileEntity projectile, int maxAgeTicks) {
        if (!(projectile instanceof PersistentProjectileEntity persistentProjectile)) {
            return;
        }

        int vanillaArrowDespawnTicks = 1200;
        int life = Math.max(0, vanillaArrowDespawnTicks - maxAgeTicks);
        ((PersistentProjectileEntityAccessor) persistentProjectile).customMobsSpawner$setLife(life);
    }

    private static boolean hasProjectileTrails(JsonObject action) {
        return action.has("trail") || action.has("trails");
    }

    private static boolean actionBoolean(JsonObject action, String key) {
        return action.has(key) && action.get(key).isJsonPrimitive() && action.get(key).getAsBoolean();
    }

    private static Vec3d projectileSpreadVelocity(ServerWorld world, Vec3d velocity, float spread) {
        if (spread <= 0.0F || velocity.lengthSquared() < 0.001D) {
            return velocity;
        }

        double speed = velocity.length();
        Vec3d direction = velocity.normalize()
                .add(
                        world.random.nextTriangular(0.0D, 0.0172275D * spread),
                        world.random.nextTriangular(0.0D, 0.0172275D * spread),
                        world.random.nextTriangular(0.0D, 0.0172275D * spread)
                )
                .normalize();
        return direction.multiply(speed);
    }

    private static void spawnProjectileTrails(ServerWorld world, Entity projectile, JsonObject projectileConfig, int ageTicks) {
        double defaultPlayerRange = projectileConfig.has("trail_player_range")
                ? projectileConfig.get("trail_player_range").getAsDouble()
                : 32.0D;
        if (projectileConfig.has("trail") && projectileConfig.get("trail").isJsonObject()) {
            spawnProjectileTrail(world, projectile, projectileConfig.getAsJsonObject("trail"), ageTicks, defaultPlayerRange);
        }
        if (projectileConfig.has("trails") && projectileConfig.get("trails").isJsonArray()) {
            for (JsonElement element : projectileConfig.getAsJsonArray("trails")) {
                if (element.isJsonObject()) {
                    spawnProjectileTrail(world, projectile, element.getAsJsonObject(), ageTicks, defaultPlayerRange);
                }
            }
        }
    }

    private static void spawnProjectileTrail(ServerWorld world, Entity projectile, JsonObject json, int ageTicks, double defaultPlayerRange) {
        int startDelayTicks = json.has("start_delay_ticks") ? Math.max(0, json.get("start_delay_ticks").getAsInt()) : 0;
        if (ageTicks < startDelayTicks) {
            return;
        }
        int intervalTicks = json.has("interval_ticks") ? Math.max(1, json.get("interval_ticks").getAsInt()) : 1;
        if ((ageTicks - startDelayTicks) % intervalTicks != 0) {
            return;
        }

        double playerRange = json.has("player_range") ? json.get("player_range").getAsDouble() : defaultPlayerRange;
        if (!hasNearbyPlayer(world, projectile.getPos(), playerRange)) {
            return;
        }

        ParticleEffect particle = particle(json.has("id") ? json.get("id").getAsString() : "minecraft:crit");
        int count = json.has("count") ? Math.max(0, json.get("count").getAsInt()) : 1;
        if (count <= 0) {
            return;
        }
        int points = json.has("points") ? Math.max(1, json.get("points").getAsInt()) : 1;
        double yOffset = json.has("y_offset") ? json.get("y_offset").getAsDouble() : 0.0D;
        double spread = json.has("spread") ? Math.max(0.0D, json.get("spread").getAsDouble()) : 0.0D;
        double speed = json.has("speed") ? Math.max(0.0D, json.get("speed").getAsDouble()) : 0.0D;
        int countPerPoint = Math.max(1, count / points);

        Vec3d previous = new Vec3d(projectile.prevX, projectile.prevY + yOffset, projectile.prevZ);
        Vec3d current = projectile.getPos().add(0.0D, yOffset, 0.0D);
        for (int index = 0; index < points; index++) {
            double progress = points == 1 ? 1.0D : (double) index / (double) (points - 1);
            Vec3d position = previous.lerp(current, progress);
            world.spawnParticles(particle, position.x, position.y, position.z, countPerPoint, spread, spread, spread, speed);
        }
    }

    private static boolean hasNearbyPlayer(ServerWorld world, Vec3d position, double range) {
        if (range <= 0.0D) {
            return true;
        }
        double rangeSquared = range * range;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!player.isSpectator() && player.squaredDistanceTo(position) <= rangeSquared) {
                return true;
            }
        }
        return false;
    }

    private static Vec3d projectileDirection(LivingEntity owner, Vec3d origin, JsonObject action) {
        LivingEntity target = currentTarget(owner);
        String targetMode = action.has("target") && action.get("target").isJsonPrimitive() ? action.get("target").getAsString() : "target";
        Vec3d direction = target != null && !"forward".equals(targetMode)
                ? projectileTargetPos(target, action).subtract(origin)
                : horizontalForward(owner);
        boolean arc = action.has("arc") && action.get("arc").getAsBoolean();
        if (arc && target != null) {
            double liftScale = action.has("arc_lift_scale") ? action.get("arc_lift_scale").getAsDouble() : 0.12D;
            double lift = Math.sqrt(direction.x * direction.x + direction.z * direction.z) * liftScale;
            direction = direction.add(0.0D, lift, 0.0D);
        }
        return direction.normalize();
    }

    private static Vec3d projectileTrajectoryVelocity(LivingEntity owner, Vec3d origin, JsonObject action) {
        LivingEntity target = currentTarget(owner);
        String targetMode = action.has("target") && action.get("target").isJsonPrimitive() ? action.get("target").getAsString() : "target";
        if (target == null || "forward".equals(targetMode)) {
            double fallbackVelocity = action.has("velocity") ? action.get("velocity").getAsDouble() : 1.0D;
            return projectileDirection(owner, origin, action).multiply(fallbackVelocity);
        }

        Vec3d targetPos = projectileTargetPos(target, action);
        Vec3d delta = targetPos.subtract(origin);
        double horizontalDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        double gravity = actionBoolean(action, "nogravity") || actionBoolean(action, "no_gravity")
                ? 0.0D
                : action.has("gravity") ? action.get("gravity").getAsDouble() : 0.05D;
        double drag = action.has("drag") ? action.get("drag").getAsDouble() : 0.99D;
        double verticalVelocity = action.has("vertical_velocity") ? action.get("vertical_velocity").getAsDouble() : 0.6D;
        double fallbackHorizontalVelocity = action.has("horizontal_velocity")
                ? action.get("horizontal_velocity").getAsDouble()
                : action.has("velocity") ? action.get("velocity").getAsDouble() : 1.0D;
        double flightTicks = projectileFlightTicks(delta.y, horizontalDistance, verticalVelocity, gravity, drag, fallbackHorizontalVelocity);
        double horizontalTravelFactor = projectileHorizontalTravelFactor(flightTicks, drag);

        double velocityX = delta.x / horizontalTravelFactor;
        double velocityZ = delta.z / horizontalTravelFactor;
        return new Vec3d(velocityX, verticalVelocity, velocityZ);
    }

    private static Vec3d projectileTargetPos(LivingEntity target, JsonObject action) {
        double targetYScale = action.has("target_y_scale") ? action.get("target_y_scale").getAsDouble() : 0.5D;
        return target.getPos().add(0.0D, target.getHeight() * targetYScale, 0.0D);
    }

    private static double projectileFlightTicks(double deltaY, double horizontalDistance, double verticalVelocity, double gravity, double drag, double fallbackHorizontalVelocity) {
        if (gravity <= 0.0D) {
            if (Math.abs(verticalVelocity) > 0.001D) {
                return Math.max(1.0D, Math.abs(deltaY / verticalVelocity));
            }
            return Math.max(1.0D, horizontalDistance / Math.max(0.001D, fallbackHorizontalVelocity));
        }

        double y = 0.0D;
        double velocityY = verticalVelocity;
        double previousY = y;
        double safeDrag = Math.max(0.0D, Math.min(1.0D, drag));
        for (int tick = 1; tick <= 200; tick++) {
            previousY = y;
            y += velocityY;
            velocityY = velocityY * safeDrag - gravity;
            if (velocityY <= 0.0D && y <= deltaY) {
                double segment = previousY - y;
                if (Math.abs(segment) < 0.001D) {
                    return tick;
                }
                double partial = Math.max(0.0D, Math.min(1.0D, (previousY - deltaY) / segment));
                return Math.max(1.0D, tick - 1 + partial);
            }
        }
        return Math.max(1.0D, horizontalDistance / Math.max(0.001D, fallbackHorizontalVelocity));
    }

    private static double projectileHorizontalTravelFactor(double flightTicks, double drag) {
        double safeDrag = Math.max(0.0D, Math.min(1.0D, drag));
        if (safeDrag >= 0.999D) {
            return Math.max(1.0D, flightTicks);
        }
        return Math.max(1.0D, (1.0D - Math.pow(safeDrag, flightTicks)) / (1.0D - safeDrag));
    }

    private static void applyProjectileHitActions(ServerWorld world, LivingEntity owner, LivingEntity context, JsonObject projectileConfig, String key, Vec3d impactPos) {
        if (!projectileConfig.has(key) || !projectileConfig.get(key).isJsonArray()) {
            return;
        }

        boolean projectileBypassInvulnerability = actionBoolean(projectileConfig, "bypass_invulnerability");
        for (JsonElement element : projectileConfig.getAsJsonArray(key)) {
            JsonObject action = element.getAsJsonObject().deepCopy();
            if (isImpactPositionAction(action)) {
                action.addProperty("x", impactPos.x);
                action.addProperty("y", impactPos.y);
                action.addProperty("z", impactPos.z);
            }
            if (isDamageAction(action) && context != owner) {
                float amount = actionDamageAmount(owner, action);
                if (amount > 0.0F) {
                    DamageSource damageSource = damageSourceForOwner(world, owner);
                    boolean knockback = !action.has("knockback") || action.get("knockback").getAsBoolean();
                    boolean bypassInvulnerability = action.has("bypass_invulnerability")
                            ? actionBoolean(action, "bypass_invulnerability")
                            : projectileBypassInvulnerability;
                    damage(context, damageSource, amount, knockback, bypassInvulnerability);
                }
                continue;
            }
            applyAction(world, context, action);
        }
    }

    private static boolean isImpactPositionAction(JsonObject action) {
        if (!action.has("type")) {
            return false;
        }
        String type = action.get("type").getAsString();
        return "particle".equals(type) || "sound".equals(type) || "explosion".equals(type) || "aura".equals(type);
    }

    private static Entity findEntity(MinecraftServer server, UUID entityId) {
        return findEntityDirect(server, entityId);
    }

    private static Entity findEntityDirect(MinecraftServer server, UUID entityId) {
        for (ServerWorld world : server.getWorlds()) {
            Entity entity = world.getEntity(entityId);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private static void createAura(ServerWorld world, LivingEntity owner, JsonObject action) {
        int durationTicks = action.has("duration_ticks") ? Math.max(1, action.get("duration_ticks").getAsInt()) : 100;
        int tickIntervalTicks = auraSchedulerTickIntervalTicks(action);
        ArmorStandEntity anchor = EntityType.ARMOR_STAND.create(world);
        if (anchor == null) {
            return;
        }

        Vec3d position = actionPosition(owner, action, owner.getPos());
        if (!SpawnSafety.isLoadedAround(world, BlockPos.ofFloored(position), 1, 2)) {
            debugSkill(action, () -> "Skipped aura anchor in unloaded chunk at " + BlockPos.ofFloored(position));
            return;
        }
        anchor.refreshPositionAndAngles(position.x, position.y, position.z, owner.getYaw(), 0.0F);
        anchor.setInvisible(true);
        anchor.setInvulnerable(true);
        anchor.setNoGravity(true);
        anchor.setSilent(true);
        anchor.addCommandTag("cmobs_projectile_aura");
        NbtCompound anchorNbt = new NbtCompound();
        anchor.writeNbt(anchorNbt);
        anchorNbt.putBoolean("Marker", true);
        anchorNbt.putBoolean("Invisible", true);
        anchorNbt.putBoolean("NoGravity", true);
        anchorNbt.putBoolean("Invulnerable", true);
        anchorNbt.putBoolean("Silent", true);
        anchor.readNbt(anchorNbt);
        world.spawnEntity(anchor);

        applyActionSet(world, anchor, action, "aura_start", 0);
        ACTIVE_AURAS.put(anchor.getUuid(), new AuraState(durationTicks, tickIntervalTicks, tickIntervalTicks, 0, action));
    }

    private static int auraSchedulerTickIntervalTicks(JsonObject aura) {
        int intervalTicks = auraBaseTickIntervalTicks(aura);
        intervalTicks = minActionListIntervalTicks(aura, "aura_tick", intervalTicks);
        intervalTicks = minActionListIntervalTicks(aura, "aura_nearby", intervalTicks);
        return intervalTicks;
    }

    private static int auraBaseTickIntervalTicks(JsonObject aura) {
        return aura.has("tick_interval_ticks") ? Math.max(1, aura.get("tick_interval_ticks").getAsInt()) : 20;
    }

    private static int minActionListIntervalTicks(JsonObject json, String key, int currentMin) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            return currentMin;
        }

        int intervalTicks = currentMin;
        for (JsonElement element : json.getAsJsonArray(key)) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject action = element.getAsJsonObject();
            if (hasActionInterval(action)) {
                intervalTicks = Math.min(intervalTicks, actionIntervalTicks(action));
            }
        }
        return intervalTicks;
    }

    private static void applyAuraTick(ServerWorld world, LivingEntity anchor, JsonObject json, int ageTicks) {
        applyActionSet(world, anchor, json, "aura_tick", ageTicks);

        double radius = json.has("tick_radius")
                ? json.get("tick_radius").getAsDouble()
                : json.has("radius") ? json.get("radius").getAsDouble() : 0.0D;
        if (radius <= 0.0D || !auraTargetScanReady(json, ageTicks)) {
            return;
        }

        String targetMode = json.has("tick_target") ? json.get("tick_target").getAsString() : actionTargetMode(json, "players");
        List<LivingEntity> targets = nearbyTargets(world, anchor, targetMode, radius);
        if (hasTargetFilters(json)) {
            targets = filterTargets(targets, json);
        }
        for (LivingEntity target : targets) {
            applyActionSet(world, target, json, "aura_nearby", ageTicks);
            applyAuraTargetActions(world, anchor, target, json, ageTicks);
        }
    }

    private static boolean auraTargetScanReady(JsonObject json, int ageTicks) {
        if (legacyAuraNearbyActionSetReady(json, ageTicks)) {
            return true;
        }
        if (actionListHasReadyAction(json, "aura_nearby", ageTicks)) {
            return true;
        }
        return auraTickTargetActionReady(json, ageTicks);
    }

    private static boolean legacyAuraNearbyActionSetReady(JsonObject json, int ageTicks) {
        if (!hasLegacyActionSet(json, "aura_nearby")) {
            return false;
        }
        int intervalTicks = auraBaseTickIntervalTicks(json);
        return ageTicks <= 0 || ageTicks % intervalTicks == 0;
    }

    private static boolean hasLegacyActionSet(JsonObject json, String prefix) {
        return json.has(prefix + "_effects")
                || json.has(prefix + "_particles")
                || json.has(prefix + "_sounds")
                || json.has(prefix + "_functions")
                || json.has(prefix + "_commands");
    }

    private static boolean actionListHasReadyAction(JsonObject json, String key, int ageTicks) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            return false;
        }
        for (JsonElement element : json.getAsJsonArray(key)) {
            if (element.isJsonObject() && actionIntervalReady(element.getAsJsonObject(), ageTicks)) {
                return true;
            }
        }
        return false;
    }

    private static boolean auraTickTargetActionReady(JsonObject json, int ageTicks) {
        if (!json.has("aura_tick") || !json.get("aura_tick").isJsonArray()) {
            return false;
        }
        for (JsonElement element : json.getAsJsonArray("aura_tick")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject action = element.getAsJsonObject();
            if (!actionIntervalReady(action, ageTicks)) {
                continue;
            }
            String targetMode = actionTargetMode(action, "self");
            if (!"self".equals(targetMode) && !"owner".equals(targetMode)) {
                return true;
            }
        }
        return false;
    }

    private static void applyAuraTargetActions(ServerWorld world, LivingEntity anchor, LivingEntity target, JsonObject json, int ageTicks) {
        if (!json.has("aura_tick") || !json.get("aura_tick").isJsonArray()) {
            return;
        }
        for (JsonElement element : json.getAsJsonArray("aura_tick")) {
            JsonObject action = element.getAsJsonObject();
            if (!actionIntervalReady(action, ageTicks)) {
                continue;
            }
            String targetMode = actionTargetMode(action, "self");
            if (!isNearbyActionTarget(target, targetMode)) {
                continue;
            }
            if (isKnockbackAction(action)) {
                applyKnockback(anchor, target, action);
                continue;
            }
            if (isDamageAction(action)) {
                continue;
            }
            applyAction(world, target, action);
        }
    }

    private static void dodge(ServerWorld world, LivingEntity entity, DamageSource source, JsonObject json) {
        Entity attacker = source == null ? null : source.getAttacker();
        if (attacker == null && actionBoolean(json, "use_target")) {
            attacker = currentTarget(entity);
        }
        if (attacker == null && entity instanceof MobEntity mob) {
            attacker = mob.getTarget();
        }
        Vec3d away = attacker == null
                ? entity.getRotationVector()
                : entity.getPos().subtract(attacker.getPos());
        Vec3d toward = attacker == null
                ? entity.getRotationVector()
                : attacker.getPos().subtract(entity.getPos());
        Vec3d sideways = new Vec3d(-away.z, 0.0D, away.x);
        if (sideways.lengthSquared() < 0.001D) {
            sideways = new Vec3d(1.0D, 0.0D, 0.0D);
        }
        if (toward.lengthSquared() < 0.001D) {
            toward = entity.getRotationVector();
        }

        if (world.random.nextBoolean()) {
            sideways = sideways.multiply(-1.0D);
        }

        double distance = json.has("distance") ? json.get("distance").getAsDouble() : 1.5D;
        double forwardDistance = json.has("forward_distance") ? json.get("forward_distance").getAsDouble() : 0.0D;
        double speed = json.has("pathfind_speed") ? json.get("pathfind_speed").getAsDouble() : 1.2D;
        Vec3d offset = sideways.normalize().multiply(distance).add(toward.normalize().multiply(forwardDistance));

        if (entity instanceof MobEntity mob) {
            mob.getNavigation().stop();
        }

        LivingEntity lookTarget = attacker instanceof LivingEntity livingAttacker ? livingAttacker : currentTarget(entity);
        boolean faceTarget = actionBoolean(json, "face_target") && lookTarget != null;

        Box destinationBox = entity.getBoundingBox().offset(offset);
        if (SpawnSafety.isLoaded(world, destinationBox) && world.isSpaceEmpty(entity, destinationBox)) {
            Rotation rotation = faceTarget ? lookRotation(entity.getEyePos().add(offset), projectileTargetPos(lookTarget, json)) : null;
            moveDodgeEntity(entity, offset, rotation);
        } else if (json.has("fallback_velocity") && json.get("fallback_velocity").getAsBoolean()) {
            double fallbackStrength = json.has("fallback_strength") ? json.get("fallback_strength").getAsDouble() : 0.35D;
            Vec3d fallbackVelocity = offset.normalize().multiply(fallbackStrength);
            entity.setVelocity(entity.getVelocity().add(fallbackVelocity.x, 0.0D, fallbackVelocity.z));
            entity.velocityModified = true;
            if (faceTarget) {
                faceTarget(entity, lookTarget, json);
            }
        }

        if (entity instanceof MobEntity mob && attacker instanceof LivingEntity livingAttacker) {
            mob.setTarget(livingAttacker);
            mob.getNavigation().startMovingTo(livingAttacker, speed);
        }
    }

    private static void moveDodgeEntity(LivingEntity entity, Vec3d offset) {
        moveDodgeEntity(entity, offset, null);
    }

    private static void moveDodgeEntity(LivingEntity entity, Vec3d offset, Rotation rotation) {
        double x = entity.getX() + offset.x;
        double y = entity.getY() + offset.y;
        double z = entity.getZ() + offset.z;
        float yaw = rotation == null ? entity.getYaw() : rotation.yaw();
        float pitch = rotation == null ? entity.getPitch() : rotation.pitch();
        if (!(entity.getWorld() instanceof ServerWorld world) || !SpawnSafety.isLoadedAround(world, BlockPos.ofFloored(x, y, z), 1, 2)) {
            return;
        }
        if (rotation != null) {
            applyLookRotation(entity, rotation);
        }
        if (entity instanceof ServerPlayerEntity player) {
            if (rotation == null) {
                player.requestTeleport(x, y, z);
            } else {
                player.networkHandler.requestTeleport(x, y, z, yaw, pitch);
            }
        } else {
            entity.refreshPositionAndAngles(x, y, z, yaw, pitch);
        }
        entity.velocityModified = true;
    }

    private static void lunge(LivingEntity entity, JsonObject json) {
        lunge(entity, json, null);
    }

    private static void lunge(LivingEntity entity, JsonObject json, Vec3d lockedDirection) {
        Vec3d direction = null;
        if (json.has("fixed_direction") && json.get("fixed_direction").getAsBoolean() && lockedDirection != null) {
            direction = lockedDirection;
        } else if (json.has("direction") && "rotation".equalsIgnoreCase(json.get("direction").getAsString())) {
            direction = horizontalForward(entity);
        } else {
            LivingEntity target = currentTarget(entity);
            if (target == null) {
                return;
            }
            direction = target.getPos().subtract(entity.getPos());
        }

        if (direction.lengthSquared() < 0.001D) {
            return;
        }

        double strength = json.has("strength") ? json.get("strength").getAsDouble() : 0.35D;
        double yStrength = json.has("y_strength") ? json.get("y_strength").getAsDouble() : 0.05D;
        Vec3d velocity = direction.normalize().multiply(strength).add(0.0D, yStrength, 0.0D);
        if (entity instanceof MobEntity mob) {
            mob.getNavigation().stop();
        }
        entity.setVelocity(entity.getVelocity().add(velocity));
        entity.velocityModified = true;
    }

    private static void applyKnockback(LivingEntity owner, LivingEntity target, JsonObject json) {
        Vec3d direction = target.getPos().subtract(owner.getPos());
        if (direction.lengthSquared() < 0.001D) {
            direction = owner.getRotationVector();
        }

        double strength = json.has("strength") ? json.get("strength").getAsDouble() : 0.7D;
        strength *= forcedKnockbackMultiplier(target, json);
        double yStrength = json.has("y_strength") ? json.get("y_strength").getAsDouble() : 0.2D;
        Vec3d velocity = direction.normalize().multiply(strength).add(0.0D, yStrength, 0.0D);
        target.setVelocity(target.getVelocity().add(velocity));
        target.velocityModified = true;
    }

    private static double forcedKnockbackMultiplier(LivingEntity target, JsonObject json) {
        boolean hasForceScale = json.has("force_scale") && json.get("force_scale").isJsonPrimitive();
        if (!actionBoolean(json, "force") && !hasForceScale) {
            return 1.0D;
        }

        double resistance = Math.max(0.0D, Math.min(1.0D,
                target.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE)));
        if (resistance <= 0.0D) {
            return 1.0D;
        }

        double forceScale = hasForceScale
                ? Math.max(0.0D, Math.min(1.0D, json.get("force_scale").getAsDouble()))
                : 0.0D;
        double normalScale = Math.max(0.05D, 1.0D - resistance);
        double forcedScale = 1.0D - resistance * forceScale;
        return forcedScale / normalScale;
    }

    private static void applyStun(LivingEntity entity, JsonObject json) {
        int duration = json.has("duration") ? json.get("duration").getAsInt() : 30;
        int slownessAmplifier = json.has("slowness_amplifier") ? json.get("slowness_amplifier").getAsInt() : 2;
        int miningFatigueAmplifier = json.has("mining_fatigue_amplifier") ? json.get("mining_fatigue_amplifier").getAsInt() : 0;

        JsonObject slowness = new JsonObject();
        slowness.addProperty("id", "minecraft:slowness");
        slowness.addProperty("duration", duration);
        slowness.addProperty("amplifier", slownessAmplifier);
        applyEffect(entity, slowness);

        JsonObject miningFatigue = new JsonObject();
        miningFatigue.addProperty("id", "minecraft:mining_fatigue");
        miningFatigue.addProperty("duration", duration);
        miningFatigue.addProperty("amplifier", miningFatigueAmplifier);
        applyEffect(entity, miningFatigue);
    }

    private static boolean testPredicate(ServerWorld world, LivingEntity entity, Identifier predicate) {
        return runCommand(world, entity, "execute if predicate " + predicate) > 0;
    }

    private static void applyEffect(LivingEntity entity, JsonObject json) {
        StatusEffect effect = statusEffect(json);
        if (effect == null) {
            return;
        }
        int duration = json.has("duration") ? json.get("duration").getAsInt() : 100;
        duration += comboDurationTicks(entity, json);
        float effectProcCoefficient = effectDurationProcCoefficient(json);
        duration = scaleTicks(duration, effectProcCoefficient);
        int amplifier = json.has("amplifier") ? json.get("amplifier").getAsInt() : 0;
        StatusEffectInstance existing = entity.getStatusEffect(effect);
        boolean stack = json.has("stack") && json.get("stack").getAsBoolean();
        int increment = 0;
        if (stack) {
            int currentAmplifier = existing == null ? -1 : existing.getAmplifier();
            increment = json.has("amplifier_increment")
                    ? json.get("amplifier_increment").getAsInt()
                    : json.has("increment") ? json.get("increment").getAsInt() : 1;
            int maxAmplifier = effectMaxAmplifier(json, amplifier);
            amplifier = Math.min(maxAmplifier, currentAmplifier + increment);
        }
        if (stack && amplifier < 0) {
            entity.removeStatusEffect(effect);
            return;
        }
        if (json.has("stack_duration") && json.get("stack_duration").getAsBoolean() && existing != null) {
            int durationIncrement = json.has("duration_increment") ? scaleTicks(json.get("duration_increment").getAsInt(), effectProcCoefficient) : duration;
            int maxDuration = json.has("max_duration") ? scaleTicks(json.get("max_duration").getAsInt(), effectProcCoefficient) : duration;
            duration = Math.min(maxDuration, existing.getDuration() + durationIncrement);
        }
        if (existing != null && stack && increment < 0 && amplifier < existing.getAmplifier()) {
            entity.removeStatusEffect(effect);
        }
        entity.addStatusEffect(new StatusEffectInstance(effect, duration, amplifier));
    }

    private static int effectMaxAmplifier(JsonObject json, int fallback) {
        if (json.has("max_amplifier")) {
            return json.get("max_amplifier").getAsInt();
        }
        if (json.has("max_level")) {
            return Math.max(0, json.get("max_level").getAsInt() - 1);
        }
        return fallback;
    }

    private static int comboDurationTicks(LivingEntity entity, JsonObject json) {
        int perCombo = firstInt(json, 0,
                "combo_duration_ticks",
                "combo_duration_per_combo",
                "combo_duration_per_count");
        if (perCombo == 0) {
            return 0;
        }
        int bonus = activeComboCount(entity) * perCombo;
        if (json.has("max_combo_duration_ticks")) {
            int cap = Math.max(0, json.get("max_combo_duration_ticks").getAsInt());
            bonus = Math.min(bonus, cap);
        }
        return Math.max(0, bonus);
    }

    private static void applyEffectAction(ServerWorld world, LivingEntity entity, JsonObject action) {
        String targetMode = actionTargetMode(action, "self");
        if ("self".equals(targetMode) || "owner".equals(targetMode)) {
            applyEffect(entity, action);
            return;
        }

        if ("target".equals(targetMode)) {
            LivingEntity target = currentTarget(entity);
            if (target != null) {
                applyEffect(target, action);
            }
            return;
        }

        double radius = action.has("radius") ? Math.max(0.0D, action.get("radius").getAsDouble()) : 8.0D;
        for (LivingEntity target : nearbyTargets(world, entity, action, radius)) {
            applyEffect(target, action);
        }
    }

    private static float effectDurationProcCoefficient(JsonObject json) {
        if (json.has("proc_coefficient_duration") && json.get("proc_coefficient_duration").getAsBoolean()) {
            return ACTIVE_PROC_COEFFICIENT.get();
        }
        return 1.0F;
    }

    private static int scaleTicks(int ticks, float multiplier) {
        if (ticks <= 0 || multiplier == 1.0F) {
            return ticks;
        }
        return Math.max(1, Math.round(ticks * Math.max(0.0F, multiplier)));
    }

    private static void clearEffect(LivingEntity entity, JsonObject json) {
        if (!json.has("id")) {
            entity.clearStatusEffects();
            return;
        }

        StatusEffect effect = statusEffect(json);
        if (effect == null) {
            return;
        }
        entity.removeStatusEffect(effect);
    }

    private static StatusEffect statusEffect(JsonObject json) {
        if (!json.has("id")) {
            CustomMobsSpawnerLog.warn("Missing skill effect id");
            return null;
        }

        Identifier id = Identifier.tryParse(json.get("id").getAsString());
        if (id == null) {
            CustomMobsSpawnerLog.warn("Invalid skill effect id " + json.get("id").getAsString());
            return null;
        }

        StatusEffect effect = Registries.STATUS_EFFECT.getOrEmpty(id).orElse(null);
        if (effect == null) {
            CustomMobsSpawnerLog.warn("Unsupported skill effect " + id);
        }
        return effect;
    }

    private static void spawnParticles(ServerWorld world, LivingEntity entity, JsonObject json) {
        spawnParticles(world, entity, json, null);
    }

    private static void spawnParticles(ServerWorld world, LivingEntity entity, JsonObject json, Vec3d lockedDirection) {
        ParticleEffect particle = particle(json.has("id") ? json.get("id").getAsString() : "minecraft:crit");
        int count = json.has("count") ? Math.max(0, json.get("count").getAsInt()) : 8;
        if (count <= 0) {
            return;
        }
        double yOffset = json.has("y_offset") ? json.get("y_offset").getAsDouble() : defaultParticleYOffset(entity, json);
        double spread = json.has("spread") ? Math.max(0.0D, json.get("spread").getAsDouble()) : 0.35D;
        double spreadX = json.has("spread_x") ? Math.max(0.0D, json.get("spread_x").getAsDouble()) : spread;
        double spreadY = json.has("spread_y") ? Math.max(0.0D, json.get("spread_y").getAsDouble()) : spread;
        double spreadZ = json.has("spread_z") ? Math.max(0.0D, json.get("spread_z").getAsDouble()) : spread;
        double speed = json.has("speed") ? Math.max(0.0D, json.get("speed").getAsDouble()) : 0.05D;
        if (json.has("arc") && json.get("arc").isJsonObject()) {
            if (!actionPlayerRangePasses(world, relativeParticlePosition(entity, json, yOffset, lockedDirection), json)) {
                return;
            }
            spawnParticleArc(world, entity, particle, json.getAsJsonObject("arc"), count, yOffset, spread, speed, lockedDirection);
            return;
        }

        Vec3d position = relativeParticlePosition(entity, json, yOffset, lockedDirection);
        if (!actionPlayerRangePasses(world, position, json)) {
            return;
        }
        world.spawnParticles(particle, position.x, position.y, position.z, count, spreadX, spreadY, spreadZ, speed);
    }

    private static void spawnParticleArc(ServerWorld world, LivingEntity entity, ParticleEffect particle, JsonObject json, int defaultCount, double defaultYOffset, double defaultSpread, double defaultSpeed) {
        spawnParticleArc(world, entity, particle, json, defaultCount, defaultYOffset, defaultSpread, defaultSpeed, null);
    }

    private static void spawnParticleArc(ServerWorld world, LivingEntity entity, ParticleEffect particle, JsonObject json, int defaultCount, double defaultYOffset, double defaultSpread, double defaultSpeed, Vec3d lockedDirection) {
        int points = json.has("points") ? Math.max(1, json.get("points").getAsInt()) : 7;
        int countPerPoint = json.has("count_per_point") ? Math.max(1, json.get("count_per_point").getAsInt()) : Math.max(1, defaultCount / points);
        double radius = json.has("radius") ? json.get("radius").getAsDouble() : 2.0D;
        double startDegrees = json.has("start_degrees") ? json.get("start_degrees").getAsDouble() : -60.0D;
        double endDegrees = json.has("end_degrees") ? json.get("end_degrees").getAsDouble() : 60.0D;
        double yOffset = json.has("y_offset") ? json.get("y_offset").getAsDouble() : defaultYOffset;
        double yEndOffset = json.has("end_y_offset") ? json.get("end_y_offset").getAsDouble() : yOffset;
        double forwardOffset = json.has("forward_offset") ? json.get("forward_offset").getAsDouble() : 0.0D;
        double sideOffset = json.has("side_offset") ? json.get("side_offset").getAsDouble() : 0.0D;
        double spread = json.has("spread") ? Math.max(0.0D, json.get("spread").getAsDouble()) : defaultSpread;
        double speed = json.has("speed") ? Math.max(0.0D, json.get("speed").getAsDouble()) : defaultSpeed;

        Vec3d forward = lockedDirection == null ? horizontalForward(entity) : lockedDirection.normalize();
        Vec3d side = horizontalSide(forward);
        Vec3d origin = entity.getPos()
                .add(forward.multiply(forwardOffset))
                .add(side.multiply(sideOffset));
        for (int index = 0; index < points; index++) {
            double progress = points == 1 ? 0.5D : (double) index / (double) (points - 1);
            double angle = Math.toRadians(startDegrees + (endDegrees - startDegrees) * progress);
            double pointYOffset = yOffset + (yEndOffset - yOffset) * progress;
            Vec3d direction = forward.multiply(Math.cos(angle)).add(side.multiply(Math.sin(angle)));
            Vec3d position = origin.add(direction.multiply(radius)).add(0.0D, pointYOffset, 0.0D);
            world.spawnParticles(particle, position.x, position.y, position.z, countPerPoint, spread, spread, spread, speed);
        }
    }

    private static Vec3d relativeParticlePosition(LivingEntity entity, JsonObject json, double yOffset) {
        return relativeParticlePosition(entity, json, yOffset, null);
    }

    private static Vec3d relativeParticlePosition(LivingEntity entity, JsonObject json, double yOffset, Vec3d lockedDirection) {
        if (hasAbsolutePosition(json)) {
            return actionPosition(entity, json, entity.getPos());
        }
        LivingEntity origin = actionOriginEntity(entity, json);
        return relativePositionFromOrigin(origin, json, yOffset, lockedDirection);
    }

    private static Vec3d relativePositionFromOrigin(LivingEntity origin, JsonObject json, double yOffset) {
        return relativePositionFromOrigin(origin, json, yOffset, null);
    }

    private static Vec3d relativePositionFromOrigin(LivingEntity origin, JsonObject json, double yOffset, Vec3d lockedDirection) {
        double forwardOffset = json.has("forward_offset") ? json.get("forward_offset").getAsDouble() : 0.0D;
        double sideOffset = json.has("side_offset") ? json.get("side_offset").getAsDouble() : 0.0D;
        double verticalOffset = json.has("vertical_offset") ? json.get("vertical_offset").getAsDouble() : 0.0D;
        Vec3d forward = lockedDirection == null ? horizontalForward(origin) : lockedDirection.normalize();
        Vec3d side = horizontalSide(forward);
        return particleBasePosition(origin, json)
                .add(forward.multiply(forwardOffset))
                .add(side.multiply(sideOffset))
                .add(0.0D, yOffset + verticalOffset, 0.0D);
    }

    private static LivingEntity actionOriginEntity(LivingEntity entity, JsonObject json) {
        if (!json.has("origin")) {
            return entity;
        }
        String origin = json.get("origin").getAsString();
        if ("target".equalsIgnoreCase(origin)) {
            LivingEntity target = currentTarget(entity);
            return target == null ? entity : target;
        }
        return entity;
    }

    private static double defaultParticleYOffset(LivingEntity entity, JsonObject json) {
        return usesEyeAnchor(json) ? 0.0D : entity.getHeight() * 0.6D;
    }

    private static Vec3d particleBasePosition(LivingEntity entity, JsonObject json) {
        return usesEyeAnchor(json) ? entity.getEyePos() : entity.getPos();
    }

    private static boolean usesEyeAnchor(JsonObject json) {
        if (!json.has("anchor")) {
            return false;
        }
        String anchor = json.get("anchor").getAsString();
        return "eye".equalsIgnoreCase(anchor) || "eyes".equalsIgnoreCase(anchor);
    }

    private static boolean hasAbsolutePosition(JsonObject json) {
        return json.has("x") && json.has("y") && json.has("z");
    }

    private static Vec3d actionPosition(LivingEntity entity, JsonObject json, Vec3d fallback) {
        if (!hasAbsolutePosition(json)) {
            return fallback;
        }
        double x = json.get("x").getAsDouble();
        double y = json.get("y").getAsDouble();
        double z = json.get("z").getAsDouble();
        double verticalOffset = json.has("vertical_offset") ? json.get("vertical_offset").getAsDouble() : 0.0D;
        return new Vec3d(x, y + verticalOffset, z);
    }

    private static Vec3d horizontalForward(LivingEntity entity) {
        Vec3d forward = entity.getRotationVector();
        forward = new Vec3d(forward.x, 0.0D, forward.z);
        if (forward.lengthSquared() < 0.001D) {
            double yaw = Math.toRadians(entity.getYaw());
            forward = new Vec3d(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        }
        return forward.normalize();
    }

    private static Vec3d horizontalSide(Vec3d forward) {
        return new Vec3d(-forward.z, 0.0D, forward.x).normalize();
    }

    private static void playSound(ServerWorld world, LivingEntity entity, JsonObject json) {
        if (!json.has("id")) {
            warnOnce(MISSING_SOUND_WARNINGS, "missing-id", "Skill sound action missing id");
            return;
        }

        Identifier id = Identifier.tryParse(json.get("id").getAsString());
        if (id == null) {
            warnOnce(MISSING_SOUND_WARNINGS, "invalid:" + json.get("id").getAsString(), "Invalid skill sound " + json.get("id").getAsString());
            return;
        }
        SoundEvent sound = Registries.SOUND_EVENT.getOrEmpty(id).orElse(null);
        if (sound == null) {
            warnOnce(MISSING_SOUND_WARNINGS, "unsupported:" + id, "Unsupported skill sound " + id);
            return;
        }
        SoundCategory category = json.has("category") ? soundCategory(json.get("category").getAsString()) : SoundCategory.HOSTILE;
        float volume = json.has("volume") ? Math.max(0.0F, json.get("volume").getAsFloat()) : 1.0F;
        if (volume <= 0.0F) {
            return;
        }
        float pitch = json.has("pitch") ? Math.max(0.01F, json.get("pitch").getAsFloat()) : 1.0F;
        LivingEntity origin = actionOriginEntity(entity, json);
        Vec3d position = hasAbsolutePosition(json)
                ? actionPosition(origin, json, origin.getPos())
                : relativePositionFromOrigin(origin, json, 0.0D);
        if (!actionPlayerRangePasses(world, position, json)) {
            return;
        }
        world.playSound(null, position.x, position.y, position.z, sound, category, volume, pitch);
    }

    private static boolean actionPlayerRangePasses(ServerWorld world, Vec3d position, JsonObject json) {
        if (!json.has("player_range")) {
            return true;
        }
        return hasNearbyPlayer(world, position, Math.max(0.0D, json.get("player_range").getAsDouble()));
    }

    private static SoundCategory soundCategory(String value) {
        return switch (value.toLowerCase()) {
            case "master" -> SoundCategory.MASTER;
            case "music" -> SoundCategory.MUSIC;
            case "record", "records" -> SoundCategory.RECORDS;
            case "weather" -> SoundCategory.WEATHER;
            case "block", "blocks" -> SoundCategory.BLOCKS;
            case "neutral" -> SoundCategory.NEUTRAL;
            case "player", "players" -> SoundCategory.PLAYERS;
            case "ambient" -> SoundCategory.AMBIENT;
            case "voice" -> SoundCategory.VOICE;
            default -> SoundCategory.HOSTILE;
        };
    }

    private static ParticleEffect particle(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) {
            warnOnce(MISSING_PARTICLE_WARNINGS, "invalid:" + id, "Invalid skill particle " + id + ", using minecraft:crit");
            return ParticleTypes.CRIT;
        }
        ParticleType<?> type = Registries.PARTICLE_TYPE.getOrEmpty(identifier).orElse(null);
        if (type instanceof ParticleEffect effect) {
            return effect;
        }
        warnOnce(MISSING_PARTICLE_WARNINGS, "unsupported:" + id, "Unsupported or parameterized skill particle " + id + ", using minecraft:crit");
        return ParticleTypes.CRIT;
    }

    private static void runFunctionAction(ServerWorld world, LivingEntity entity, JsonObject action) {
        String id = firstString(action, "id", "function");
        if (id == null || id.isBlank()) {
            warnOnce(ACTION_WARNINGS, "function-action-missing-id", "Skill function action missing id");
            return;
        }
        runCommand(world, entity, "function " + id);
    }

    private static void runCommandAction(ServerWorld world, LivingEntity entity, JsonObject action) {
        String command = firstString(action, "command");
        if (command == null || command.isBlank()) {
            warnOnce(ACTION_WARNINGS, "command-action-missing-command", "Skill command action missing command");
            return;
        }
        runCommand(world, entity, command);
    }

    private static int runCommand(ServerWorld world, LivingEntity entity, String command) {
        try {
            ServerCommandSource source = entity.getCommandSource()
                    .withWorld(world)
                    .withPosition(entity.getPos())
                    .withRotation(Vec2f.ZERO)
                    .withSilent();
            return world.getServer().getCommandManager().executeWithPrefix(source, command);
        } catch (RuntimeException exception) {
            warnOnce(ACTION_WARNINGS, "command:" + command, "Failed to run skill command '" + command + "': " + exception.getMessage());
            return 0;
        }
    }

    private static void warnOnce(Set<String> keys, String key, String message) {
        if (keys.add(key)) {
            CustomMobsSpawnerLog.warn(message);
        }
    }

    private static String tagFor(Identifier skillId) {
        return SKILL_TAG_PREFIX + skillId.toString().toLowerCase().replaceAll("[^a-z0-9_]", "_");
    }

    private record StanceState(Identifier skillId, int remainingTicks, int tickIntervalTicks, int tickDelayTicks, int ageTicks, JsonObject config) {
        private StanceState withTicks(int remainingTicks, int tickDelayTicks, int ageTicks) {
            return new StanceState(skillId, remainingTicks, tickIntervalTicks, tickDelayTicks, ageTicks, config);
        }
    }

    private record AuraState(int remainingTicks, int tickIntervalTicks, int tickDelayTicks, int ageTicks, JsonObject config) {
        private AuraState withTicks(int remainingTicks, int tickDelayTicks, int ageTicks) {
            return new AuraState(remainingTicks, tickIntervalTicks, tickDelayTicks, ageTicks, config);
        }
    }

    private record PendingSkillKey(UUID entityId, Identifier skillId) {
    }

    private record Rotation(float yaw, float pitch) {
    }

    private record PendingSkillState(int ageTicks, List<TelegraphStep> steps, DamageSource source, float sourceDamageAmount, Vec3d lockedDirection, UUID targetId, boolean runBodyOnComplete, float procCoefficient, int comboCount) {
        private int totalDelayTicks() {
            return steps.isEmpty() ? 0 : steps.get(steps.size() - 1).delayTicks();
        }

        private PendingSkillState withAgeTicks(int ageTicks) {
            return new PendingSkillState(ageTicks, steps, source, sourceDamageAmount, lockedDirection, targetId, runBodyOnComplete, procCoefficient, comboCount);
        }
    }

    private record TelegraphStep(int delayTicks, JsonObject json) {
    }

    private record ScheduledActionState(UUID entityId, int ageTicks, List<TelegraphStep> steps, DamageSource source, float sourceDamageAmount, Vec3d lockedDirection, UUID targetId, float procCoefficient, int comboCount) {
        private int totalDelayTicks() {
            return steps.isEmpty() ? 0 : steps.get(steps.size() - 1).delayTicks();
        }

        private ScheduledActionState withAgeTicks(int ageTicks) {
            return new ScheduledActionState(entityId, ageTicks, steps, source, sourceDamageAmount, lockedDirection, targetId, procCoefficient, comboCount);
        }
    }

    private record SkillTargetContext(UUID ownerId, UUID targetId) {
    }

    private record SpellProjectileDamageOverride(float amount, long expiresAt) {
    }

    private record GrenadeProjectileState(UUID ownerId, JsonObject config, int ageTicks, long lastWorldTime, int fuseTicks, int maxAgeTicks) {
        private GrenadeProjectileState withTick(int ageTicks, long lastWorldTime) {
            return new GrenadeProjectileState(ownerId, config, ageTicks, lastWorldTime, fuseTicks, maxAgeTicks);
        }
    }

    private record SkillProjectileState(UUID ownerId, JsonObject config, int ageTicks, long lastWorldTime, int maxAgeTicks) {
        private SkillProjectileState withTick(int ageTicks, long lastWorldTime) {
            return new SkillProjectileState(ownerId, config, ageTicks, lastWorldTime, maxAgeTicks);
        }
    }
}
