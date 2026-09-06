package com.ashtonthedev.custommobsspawner.data;

import com.ashtonthedev.custommobsspawner.CustomMobsSpawner;
import com.ashtonthedev.custommobsspawner.spawn.CustomSpawnRules;
import com.ashtonthedev.custommobsspawner.spawn.SpawnRule;
import com.ashtonthedev.custommobsspawner.skill.CustomSkillDefinition;
import com.ashtonthedev.custommobsspawner.skill.CustomSkillRegistry;
import com.ashtonthedev.custommobsspawner.skill.SkillTrigger;
import com.ashtonthedev.custommobsspawner.spawn.SpawnTargetKind;
import com.ashtonthedev.custommobsspawner.util.SpawnSafety;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class CustomMobData implements SimpleSynchronousResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static final String SPAWN_STAND_CHECKED_TAG = "cmobs_spawn_stand_checked";
    private static final double DEFAULT_SPAWN_STAND_PLAYER_DISTANCE = 80.0D;
    private static final List<ReplacementRule> REPLACEMENT_RULES = new CopyOnWriteArrayList<>();
    private static final Map<Identifier, MobGroupDefinition> MOB_GROUPS = new ConcurrentHashMap<>();
    private static final List<SpawnStandDefinition> SPAWN_STANDS = new CopyOnWriteArrayList<>();
    private static final Set<String> SPAWN_STAND_TAGS = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Long> SPAWN_STAND_COOLDOWNS = new ConcurrentHashMap<>();

    @Override
    public Identifier getFabricId() {
        return CustomMobsSpawner.id("custom_mob_data");
    }

    @Override
    public void reload(ResourceManager manager) {
        reloadAll(manager);
    }

    public static void reloadAll(ResourceManager manager) {
        List<CustomMobDefinition> customMobs = new ArrayList<>();
        List<CustomSkillDefinition> skills = new ArrayList<>();
        List<ReplacementRule> replacements = new ArrayList<>();
        List<SpawnRule> spawns = new ArrayList<>();
        List<SpawnStandDefinition> spawnStands = new ArrayList<>();
        Map<Identifier, MobGroupDefinition> mobGroups = new HashMap<>();
        Map<Identifier, JsonElement> spellConditions = new HashMap<>();

        loadSkills(manager, skills);
        loadSpellConditions(manager, spellConditions);
        loadCustomMobs(manager, customMobs);
        loadMobGroups(manager, mobGroups);
        loadReplacementRules(manager, replacements);
        loadSpawnRules(manager, spawns);
        loadSpawnStands(manager, spawnStands);

        CustomSkillRegistry.replaceAll(skills);
        CustomSkillRegistry.replaceSpellConditions(spellConditions);
        CustomMobRegistry.replaceAll(customMobs);
        MOB_GROUPS.clear();
        MOB_GROUPS.putAll(mobGroups);
        REPLACEMENT_RULES.clear();
        REPLACEMENT_RULES.addAll(replacements);
        SPAWN_STANDS.clear();
        SPAWN_STANDS.addAll(spawnStands);
        SPAWN_STAND_TAGS.clear();
        for (SpawnStandDefinition spawnStand : spawnStands) {
            SPAWN_STAND_TAGS.addAll(spawnStand.requiredTags());
            SPAWN_STAND_TAGS.addAll(spawnStand.anyTags());
        }
        SPAWN_STAND_COOLDOWNS.clear();
        CustomSpawnRules.replaceAll(spawns);
    }

    public static void tryReplace(Entity entity, ServerWorld world) {
        if (trySpawnStand(entity, world)) {
            return;
        }

        if (!(entity instanceof MobEntity mob) || entity.getCommandTags().contains(CustomMobsSpawner.CHECKED_TAG)) {
            return;
        }

        for (ReplacementRule rule : REPLACEMENT_RULES) {
            if (!rule.source().matches(entity.getType()) || !matchesReplacementRule(world, rule)) {
                continue;
            }

            mob.addCommandTag(CustomMobsSpawner.CHECKED_TAG);
            if (world.random.nextFloat() <= rule.chance() && spawnReplacement(mob, world, pickReplacement(rule, mob, world))) {
                mob.discard();
            }
            return;
        }

        mob.addCommandTag(CustomMobsSpawner.CHECKED_TAG);
    }

    public static void tickSpawnStands(ServerWorld world) {
        if (spawnStandsDisabled(world) || SPAWN_STANDS.isEmpty() || world.getPlayers().isEmpty() || world.getTime() % 20 != 0) {
            return;
        }

        for (Entity entity : world.iterateEntities()) {
            if (entity instanceof ArmorStandEntity && hasSpawnStandTag(entity)) {
                trySpawnStand(entity, world);
            }
        }
    }

    private static boolean matchesReplacementRule(ServerWorld world, ReplacementRule rule) {
        if (rule.dimensions().isEmpty()) {
            return true;
        }

        Identifier currentDimension = world.getRegistryKey().getValue();
        for (String dimension : rule.dimensions()) {
            Identifier dimensionId = Identifier.tryParse(dimension);
            if (dimensionId != null && currentDimension.equals(dimensionId)) {
                return true;
            }
        }
        return false;
    }

    private static ReplacementEntry pickReplacement(ReplacementRule rule, MobEntity source, ServerWorld world) {
        List<ReplacementEntry> candidates = rule.replacements().stream()
                .filter(entry -> matchesReplacementEntry(source, entry))
                .toList();
        int totalWeight = candidates.stream()
                .mapToInt(entry -> Math.max(0, entry.weight()))
                .sum();
        if (totalWeight <= 0) {
            return null;
        }

        int roll = world.random.nextInt(totalWeight);
        for (ReplacementEntry entry : candidates) {
            roll -= Math.max(0, entry.weight());
            if (roll < 0) {
                return entry;
            }
        }
        return null;
    }

    private static boolean matchesReplacementEntry(MobEntity source, ReplacementEntry entry) {
        int y = source.getBlockY();
        return (entry.kind() != SpawnTargetKind.GROUP || !isSpawnerSpawned(source))
                && (entry.minY() == null || y >= entry.minY())
                && (entry.maxY() == null || y <= entry.maxY());
    }

    private static boolean spawnReplacement(MobEntity source, ServerWorld world, ReplacementEntry entry) {
        if (entry == null) {
            return false;
        }
        if (!SpawnSafety.isLoadedAround(world, source.getBlockPos(), 1, 2)) {
            return false;
        }

        if (entry.kind() == SpawnTargetKind.FUNCTION) {
            return runFunction(source, world, entry.id(), source.getPos(), false);
        }

        if (entry.kind() == SpawnTargetKind.GROUP) {
            return spawnGroup(source, world, entry.id(), Vec3d.ZERO);
        }

        Entity replacement = entry.kind() == SpawnTargetKind.CUSTOM_MOB
                ? CustomMobRegistry.create(entry.id(), world, source.getBlockPos(), source.getYaw())
                : Registries.ENTITY_TYPE.getOrEmpty(entry.id()).map(type -> type.create(world)).orElse(null);
        if (!(replacement instanceof MobEntity replacementMob)) {
            return false;
        }

        if (entry.kind() == SpawnTargetKind.ENTITY) {
            replacementMob.refreshPositionAndAngles(source.getX(), source.getY(), source.getZ(), source.getYaw(), source.getPitch());
            replacementMob.initialize(world, world.getLocalDifficulty(replacementMob.getBlockPos()), SpawnReason.CONVERSION, null, null);
        }
        if (!SpawnSafety.isLoaded(world, replacementMob)) {
            return false;
        }
        replacementMob.setVelocity(source.getVelocity());
        if (source.hasCustomName()) {
            replacementMob.setCustomName(source.getCustomName());
            replacementMob.setCustomNameVisible(source.isCustomNameVisible());
        }
        replacementMob.addCommandTag(CustomMobsSpawner.CHECKED_TAG);

        if (source.isPersistent()) {
            replacementMob.setPersistent();
        }

        if (isSpawnerSpawned(source)) {
            replacementMob.addCommandTag(CustomMobsSpawner.SPAWNER_SPAWNED_TAG);
            copySpawnerEquipment(source, replacementMob);
        }

        if (entry.kind() == SpawnTargetKind.CUSTOM_MOB) {
            world.spawnEntityAndPassengers(replacementMob);
            CustomMobRegistry.applyPostSpawn(replacementMob);
            return true;
        }
        return world.spawnEntity(replacementMob);
    }

    private static boolean trySpawnStand(Entity entity, ServerWorld world) {
        if (spawnStandsDisabled(world) || world.getPlayers().isEmpty() || !(entity instanceof ArmorStandEntity) || !hasSpawnStandTag(entity)) {
            return false;
        }

        for (SpawnStandDefinition spawnStand : SPAWN_STANDS) {
            if (!spawnStand.repeatable() && entity.getCommandTags().contains(SPAWN_STAND_CHECKED_TAG)) {
                return false;
            }
            if (!matchesSpawnStand(entity, world, spawnStand)) {
                continue;
            }

            if (!spawnStand.repeatable()) {
                entity.addCommandTag(SPAWN_STAND_CHECKED_TAG);
            } else if (!spawnStandCooldownReady(entity, world, spawnStand)) {
                return true;
            }
            boolean spawned = world.random.nextFloat() <= spawnStand.chance()
                    && spawnStandTarget(entity, world, spawnStand);
            if (spawned && spawnStand.discardStand()) {
                entity.discard();
            } else if (spawned && spawnStand.repeatable()) {
                SPAWN_STAND_COOLDOWNS.put(entity.getUuid(), world.getTime() + spawnStand.cooldownTicks());
            }
            return true;
        }
        return false;
    }

    private static boolean hasSpawnStandTag(Entity entity) {
        if (SPAWN_STAND_TAGS.isEmpty()) {
            return false;
        }
        for (String tag : entity.getCommandTags()) {
            if (SPAWN_STAND_TAGS.contains(tag)) {
                return true;
            }
        }
        return false;
    }

    private static boolean spawnStandsDisabled(ServerWorld world) {
        return world.getGameRules().getBoolean(CustomMobsSpawner.DISABLE_SPAWN_STANDS);
    }

    private static boolean spawnStandCooldownReady(Entity entity, ServerWorld world, SpawnStandDefinition spawnStand) {
        if (spawnStand.cooldownTicks() <= 0) {
            return true;
        }
        return world.getTime() >= SPAWN_STAND_COOLDOWNS.getOrDefault(entity.getUuid(), Long.MIN_VALUE);
    }

    private static boolean matchesSpawnStand(Entity entity, ServerWorld world, SpawnStandDefinition spawnStand) {
        for (String tag : spawnStand.requiredTags()) {
            if (!entity.getCommandTags().contains(tag)) {
                return false;
            }
        }
        if (!spawnStand.anyTags().isEmpty() && spawnStand.anyTags().stream().noneMatch(entity.getCommandTags()::contains)) {
            return false;
        }
        return (spawnStand.requiredBlock() == null || matchesBlockPredicate(world, entity.getBlockPos(), spawnStand.requiredBlock()))
                && matchesSpawnStandRuntimeChecks(entity, world, spawnStand);
    }

    private static boolean spawnStandTarget(Entity source, ServerWorld world, SpawnStandDefinition spawnStand) {
        Vec3d baseOffset = spawnStand.offset();
        boolean spawned;
        if (spawnStand.kind() == SpawnTargetKind.FUNCTION) {
            spawned = runFunction(source, world, spawnStand.id(), source.getPos().add(baseOffset), true);
        } else if (spawnStand.kind() == SpawnTargetKind.GROUP) {
            spawned = spawnGroup(source, world, spawnStand.id(), baseOffset);
        } else {
            spawned = spawnSingleStandTarget(source, world, spawnStand, baseOffset);
        }

        if (spawned) {
            consumeRequiredItems(source, world, spawnStand);
        }
        return spawned;
    }

    private static boolean runFunction(Entity source, ServerWorld world, Identifier id, Vec3d position, boolean elevated) {
        if (!SpawnSafety.isLoadedAround(world, BlockPos.ofFloored(position), 1, 2)) {
            return false;
        }
        try {
            ServerCommandSource commandSource = source.getCommandSource()
                    .withWorld(world)
                    .withPosition(position)
                    .withRotation(Vec2f.ZERO)
                    .withSilent();
            if (elevated) {
                commandSource = commandSource.withLevel(2);
            }
            return world.getServer().getCommandManager().executeWithPrefix(commandSource, "function " + id) > 0;
        } catch (RuntimeException exception) {
            CustomMobsSpawnerLog.warn("Failed to run spawn function " + id + ": " + exception.getMessage());
            return false;
        }
    }

    private static boolean spawnSingleStandTarget(Entity source, ServerWorld world, SpawnStandDefinition spawnStand, Vec3d baseOffset) {
        Entity replacement = spawnStand.kind() == SpawnTargetKind.CUSTOM_MOB
                ? CustomMobRegistry.create(spawnStand.id(), world, source.getBlockPos(), source.getYaw())
                : Registries.ENTITY_TYPE.getOrEmpty(spawnStand.id()).map(type -> type.create(world)).orElse(null);
        if (replacement == null) {
            return false;
        }

        Vec3d pos = source.getPos().add(baseOffset);
        if (!SpawnSafety.isLoadedAround(world, BlockPos.ofFloored(pos), 1, 2)) {
            return false;
        }
        replacement.refreshPositionAndAngles(pos.x, pos.y, pos.z, source.getYaw(), source.getPitch());
        replacement.setVelocity(source.getVelocity());
        replacement.addCommandTag(CustomMobsSpawner.CHECKED_TAG);
        if (replacement instanceof MobEntity mob && spawnStand.kind() == SpawnTargetKind.ENTITY) {
            mob.initialize(world, world.getLocalDifficulty(mob.getBlockPos()), SpawnReason.STRUCTURE, null, null);
        }

        world.spawnEntityAndPassengers(replacement);
        CustomMobRegistry.applyPostSpawn(replacement);
        return true;
    }

    private static boolean matchesSpawnStandRuntimeChecks(Entity entity, ServerWorld world, SpawnStandDefinition spawnStand) {
        if (spawnStand.maxPlayerDistance() > 0.0D
                && world.getClosestPlayer(entity.getX(), entity.getY(), entity.getZ(), spawnStand.maxPlayerDistance(), false) == null) {
            return false;
        }
        for (SpawnStandItemRequirement item : spawnStand.requiredItems()) {
            if (!hasRequiredItem(entity, world, item)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasRequiredItem(Entity entity, ServerWorld world, SpawnStandItemRequirement requirement) {
        return matchingItemEntities(entity, world, requirement).stream()
                .mapToInt(item -> item.getStack().getCount())
                .sum() >= requirement.count();
    }

    private static void consumeRequiredItems(Entity entity, ServerWorld world, SpawnStandDefinition spawnStand) {
        for (SpawnStandItemRequirement requirement : spawnStand.requiredItems()) {
            if (!requirement.consume()) {
                continue;
            }

            int remaining = requirement.count();
            for (ItemEntity itemEntity : matchingItemEntities(entity, world, requirement)) {
                ItemStack stack = itemEntity.getStack();
                int taken = Math.min(remaining, stack.getCount());
                stack.decrement(taken);
                remaining -= taken;
                if (stack.isEmpty()) {
                    itemEntity.discard();
                } else {
                    itemEntity.setStack(stack);
                }
                if (remaining <= 0) {
                    break;
                }
            }
        }
    }

    private static List<ItemEntity> matchingItemEntities(Entity entity, ServerWorld world, SpawnStandItemRequirement requirement) {
        Box box = entity.getBoundingBox().expand(requirement.distance());
        return world.getEntitiesByType(EntityType.ITEM, box, itemEntity ->
                !itemEntity.isRemoved() && itemEntity.getStack().isOf(requirement.item())
                        && itemEntity.squaredDistanceTo(entity) <= requirement.distance() * requirement.distance());
    }

    private static boolean spawnGroup(Entity source, ServerWorld world, Identifier groupId, Vec3d baseOffset) {
        MobGroupDefinition group = MOB_GROUPS.get(groupId);
        if (group == null) {
            CustomMobsSpawnerLog.warn("Unknown mob group " + groupId);
            return false;
        }

        List<GroupMember> selected = new ArrayList<>(group.members().size() + group.pools().size());
        Random random = world.random;
        for (GroupMember member : group.members()) {
            if (random.nextFloat() <= member.chance()) {
                selected.add(member);
            }
        }
        for (GroupPool pool : group.pools()) {
            if (random.nextFloat() > pool.chance()) {
                continue;
            }

            GroupMember member = pickGroupMember(pool.entries(), random);
            if (member != null && random.nextFloat() <= member.chance()) {
                selected.add(member);
            }
        }

        boolean spawnedAny = false;
        for (GroupMember member : selected) {
            spawnedAny |= spawnGroupMember(source, world, member, baseOffset);
        }
        return spawnedAny;
    }

    private static GroupMember pickGroupMember(List<GroupMember> entries, Random random) {
        int totalWeight = entries.stream()
                .mapToInt(entry -> Math.max(0, entry.weight()))
                .sum();
        if (totalWeight <= 0) {
            return null;
        }

        int roll = random.nextInt(totalWeight);
        for (GroupMember entry : entries) {
            roll -= Math.max(0, entry.weight());
            if (roll < 0) {
                return entry;
            }
        }
        return null;
    }

    private static boolean spawnGroupMember(Entity source, ServerWorld world, GroupMember member, Vec3d baseOffset) {
        Entity entity = member.kind() == SpawnTargetKind.CUSTOM_MOB
                ? CustomMobRegistry.create(member.id(), world, source.getBlockPos(), source.getYaw() + member.yaw())
                : Registries.ENTITY_TYPE.getOrEmpty(member.id()).map(type -> type.create(world)).orElse(null);
        if (entity == null) {
            CustomMobsSpawnerLog.warn("Failed to create mob group member " + member.kind().name().toLowerCase() + ":" + member.id());
            return false;
        }

        Vec3d pos = source.getPos().add(baseOffset).add(member.x(), member.y(), member.z());
        if (!SpawnSafety.isLoadedAround(world, BlockPos.ofFloored(pos), 1, 2)) {
            return false;
        }
        entity.refreshPositionAndAngles(pos.x, pos.y, pos.z, source.getYaw() + member.yaw(), source.getPitch());
        entity.addCommandTag(CustomMobsSpawner.CHECKED_TAG);
        if (entity instanceof MobEntity mob) {
            if (member.kind() == SpawnTargetKind.ENTITY) {
                mob.initialize(world, world.getLocalDifficulty(mob.getBlockPos()), source instanceof MobEntity ? SpawnReason.CONVERSION : SpawnReason.STRUCTURE, null, null);
            }
            mob.setVelocity(source.getVelocity());
            if (source instanceof MobEntity sourceMob && sourceMob.isPersistent()) {
                mob.setPersistent();
            }
        }
        world.spawnEntityAndPassengers(entity);
        CustomMobRegistry.applyPostSpawn(entity);
        return true;
    }

    private static boolean isSpawnerSpawned(MobEntity mob) {
        return mob.getCommandTags().contains(CustomMobsSpawner.SPAWNER_SPAWNED_TAG);
    }

    private static void copySpawnerEquipment(MobEntity source, MobEntity replacement) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = source.getEquippedStack(slot);
            if (!stack.isEmpty()) {
                replacement.equipStack(slot, stack.copy());
            }
        }
    }

    private static void loadReplacementRules(ResourceManager manager, List<ReplacementRule> out) {
        for (Map.Entry<Identifier, Resource> entry : manager.findResources("cmobs/replacements", id -> id.getPath().endsWith(".json")).entrySet()) {
            try (Reader reader = new InputStreamReader(entry.getValue().getInputStream(), StandardCharsets.UTF_8)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                String source = getString(json, "source");
                float chance = json.has("chance") ? json.get("chance").getAsFloat() : 1.0F;
                out.add(new ReplacementRule(
                        EntitySelector.parse(source),
                        clampChance(chance),
                        readReplacementEntries(json),
                        getStringList(json, "dimensions")
                ));
            } catch (Exception exception) {
                CustomMobsSpawnerLog.warn("Skipping replacement rule " + entry.getKey() + ": " + exception.getMessage());
            }
        }
    }

    private static void loadCustomMobs(ResourceManager manager, List<CustomMobDefinition> out) {
        for (Map.Entry<Identifier, Resource> entry : manager.findResources("cmobs/custom_mobs", id -> id.getPath().endsWith(".json")).entrySet()) {
            try (Reader reader = new InputStreamReader(entry.getValue().getInputStream(), StandardCharsets.UTF_8)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                Identifier entity = getIdentifier(json, "entity");
                out.add(new CustomMobDefinition(ruleId(entry.getKey(), "cmobs/custom_mobs/"), entity, json));
            } catch (Exception exception) {
                CustomMobsSpawnerLog.warn("Skipping custom mob " + entry.getKey() + ": " + exception.getMessage());
            }
        }
    }

    private static void loadMobGroups(ResourceManager manager, Map<Identifier, MobGroupDefinition> out) {
        for (Map.Entry<Identifier, Resource> entry : manager.findResources("cmobs/groups", id -> id.getPath().endsWith(".json")).entrySet()) {
            try (Reader reader = new InputStreamReader(entry.getValue().getInputStream(), StandardCharsets.UTF_8)) {
                Identifier id = ruleId(entry.getKey(), "cmobs/groups/");
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                out.put(id, readMobGroup(id, json));
            } catch (Exception exception) {
                CustomMobsSpawnerLog.warn("Skipping mob group " + entry.getKey() + ": " + exception.getMessage());
            }
        }
    }

    private static MobGroupDefinition readMobGroup(Identifier id, JsonObject json) {
        List<GroupMember> members = new ArrayList<>();
        if (json.has("members")) {
            members.addAll(readGroupMembers(json.getAsJsonArray("members"), null));
        }
        if (json.has("mobs")) {
            members.addAll(readGroupMembers(json.getAsJsonArray("mobs"), null));
        }

        List<GroupPool> pools = new ArrayList<>();
        if (json.has("pools")) {
            for (JsonElement element : json.getAsJsonArray("pools")) {
                if (!element.isJsonObject()) {
                    throw new IllegalArgumentException("Mob group pool must be an object");
                }
                JsonObject pool = element.getAsJsonObject();
                if (!pool.has("entries")) {
                    throw new IllegalArgumentException("Mob group pool is missing entries");
                }
                List<GroupMember> entries = readGroupMembers(pool.getAsJsonArray("entries"), pool);
                if (entries.isEmpty()) {
                    throw new IllegalArgumentException("Mob group pool must contain at least one entry");
                }
                float chance = pool.has("chance") ? clampChance(pool.get("chance").getAsFloat()) : 1.0F;
                pools.add(new GroupPool(chance, List.copyOf(entries)));
            }
        }

        if (members.isEmpty() && pools.isEmpty()) {
            throw new IllegalArgumentException("Mob group " + id + " must define members, mobs, or pools");
        }
        return new MobGroupDefinition(id, List.copyOf(members), List.copyOf(pools));
    }

    private static List<GroupMember> readGroupMembers(JsonArray array, JsonObject defaults) {
        List<GroupMember> members = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("Mob group member must be an object");
            }
            members.add(readGroupMember(element.getAsJsonObject(), defaults));
        }
        return members;
    }

    private static GroupMember readGroupMember(JsonObject json, JsonObject defaults) {
        SpawnTarget target = readGroupMemberTarget(json);
        Vec3d defaultOffset = readOffset(defaults, Vec3d.ZERO);
        double defaultX = getDouble(defaults, "x", defaultOffset.x);
        double defaultY = getDouble(defaults, "y", defaultOffset.y);
        double defaultZ = getDouble(defaults, "z", defaultOffset.z);
        Vec3d offset = readOffset(json, new Vec3d(defaultX, defaultY, defaultZ));
        double x = getDouble(json, "x", offset.x);
        double y = getDouble(json, "y", offset.y);
        double z = getDouble(json, "z", offset.z);
        float yaw = (float) getDouble(json, "yaw", getDouble(defaults, "yaw", 0.0D));
        int weight = json.has("weight") ? json.get("weight").getAsInt() : 1;
        float chance = json.has("chance") ? clampChance(json.get("chance").getAsFloat()) : 1.0F;
        return new GroupMember(target.kind(), target.id(), Math.max(0, weight), chance, x, y, z, yaw);
    }

    private static SpawnTarget readGroupMemberTarget(JsonObject json) {
        int targetCount = 0;
        targetCount += json.has("entity") ? 1 : 0;
        targetCount += json.has("custom_mob") ? 1 : 0;
        if (targetCount != 1) {
            throw new IllegalArgumentException("Expected exactly one group member target: entity or custom_mob");
        }
        if (json.has("custom_mob")) {
            return new SpawnTarget(SpawnTargetKind.CUSTOM_MOB, getIdentifier(json, "custom_mob"));
        }
        return new SpawnTarget(SpawnTargetKind.ENTITY, getIdentifier(json, "entity"));
    }

    private static void loadSkills(ResourceManager manager, List<CustomSkillDefinition> out) {
        loadSkillFolder(manager, out, "cmobs/skills", false);
        loadSkillFolder(manager, out, "cmobs/player_skills", true);
    }

    private static void loadSpellConditions(ResourceManager manager, Map<Identifier, JsonElement> out) {
        for (Map.Entry<Identifier, Resource> entry : manager.findResources("spells", id -> id.getPath().endsWith(".json")).entrySet()) {
            try (Reader reader = new InputStreamReader(entry.getValue().getInputStream(), StandardCharsets.UTF_8)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (json.has("cmobs_conditions")) {
                    out.put(ruleId(entry.getKey(), "spells/"), json.get("cmobs_conditions"));
                }
            } catch (Exception exception) {
                CustomMobsSpawnerLog.warn("Skipping spell conditions " + entry.getKey() + ": " + exception.getMessage());
            }
        }
    }

    private static void loadSkillFolder(ResourceManager manager, List<CustomSkillDefinition> out, String folder, boolean playerSkill) {
        for (Map.Entry<Identifier, Resource> entry : manager.findResources(folder, id -> id.getPath().endsWith(".json")).entrySet()) {
            try (Reader reader = new InputStreamReader(entry.getValue().getInputStream(), StandardCharsets.UTF_8)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                SkillTrigger trigger = SkillTrigger.parse(getString(json, "trigger"));
                Identifier predicate = json.has("predicate") ? parseIdentifier(json.get("predicate").getAsString(), "predicate") : null;
                float chance = json.has("chance") ? json.get("chance").getAsFloat() : 1.0F;
                int intervalTicks = json.has("interval_ticks") ? json.get("interval_ticks").getAsInt() : 20;
                int cooldownTicks = readCooldownTicks(json, "cooldown_ticks", "cooldown_seconds");
                int globalCooldownTicks = readCooldownTicks(json, "global_cooldown_ticks", "global_cooldown_seconds");
                out.add(new CustomSkillDefinition(
                        ruleId(entry.getKey(), folder + "/"),
                        trigger,
                        predicate,
                        clampChance(chance),
                        Math.max(1, intervalTicks),
                        Math.max(0, cooldownTicks),
                        Math.max(0, globalCooldownTicks),
                        playerSkill,
                        json
                ));
            } catch (Exception exception) {
                CustomMobsSpawnerLog.warn("Skipping skill " + entry.getKey() + ": " + exception.getMessage());
            }
        }
    }

    private static void loadSpawnRules(ResourceManager manager, List<SpawnRule> out) {
        for (Map.Entry<Identifier, Resource> entry : manager.findResources("cmobs/spawn_rules", id -> id.getPath().endsWith(".json")).entrySet()) {
            try (Reader reader = new InputStreamReader(entry.getValue().getInputStream(), StandardCharsets.UTF_8)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                SpawnTarget target = readTarget(json);
                if (target.kind() == SpawnTargetKind.GROUP) {
                    throw new IllegalArgumentException("Mob groups are only supported by replacement rules");
                }
                List<String> dimensions = getStringList(json, "dimensions");
                List<String> biomes = getStringList(json, "biomes");
                int minLight = json.has("min_light") ? json.get("min_light").getAsInt() : 0;
                int maxLight = json.has("max_light") ? json.get("max_light").getAsInt() : 15;
                List<String> blocks = getStringList(json, "blocks");
                boolean extraSpawn = json.has("extra_spawn") && json.get("extra_spawn").getAsBoolean();
                int intervalTicks = json.has("interval_ticks") ? json.get("interval_ticks").getAsInt() : 200;
                int attemptsPerPlayer = json.has("attempts_per_player") ? json.get("attempts_per_player").getAsInt() : 1;
                float chance = json.has("chance") ? json.get("chance").getAsFloat() : 1.0F;
                int minDistance = json.has("min_distance") ? json.get("min_distance").getAsInt() : 24;
                int maxDistance = json.has("max_distance") ? json.get("max_distance").getAsInt() : 48;
                String spawnGroup = json.has("spawn_group") ? json.get("spawn_group").getAsString() : defaultSpawnGroup(target);
                int groupMobCap = json.has("group_mob_cap") ? json.get("group_mob_cap").getAsInt() : -1;
                int mobCap = json.has("mob_cap") ? json.get("mob_cap").getAsInt() : -1;
                boolean obeyDoMobSpawning = !json.has("obey_do_mob_spawning") || json.get("obey_do_mob_spawning").getAsBoolean();
                out.add(new SpawnRule(
                        ruleId(entry.getKey(), "cmobs/spawn_rules/"),
                        target.kind(),
                        target.id(),
                        dimensions,
                        biomes,
                        Math.max(0, minLight),
                        Math.min(15, maxLight),
                        blocks,
                        extraSpawn,
                        Math.max(1, intervalTicks),
                        Math.max(1, attemptsPerPlayer),
                        clampChance(chance),
                        Math.max(0, minDistance),
                        Math.max(1, maxDistance),
                        spawnGroup.toLowerCase(),
                        groupMobCap,
                        mobCap,
                        obeyDoMobSpawning
                ));
            } catch (Exception exception) {
                CustomMobsSpawnerLog.warn("Skipping spawn rule " + entry.getKey() + ": " + exception.getMessage());
            }
        }
    }

    private static void loadSpawnStands(ResourceManager manager, List<SpawnStandDefinition> out) {
        for (Map.Entry<Identifier, Resource> entry : manager.findResources("cmobs/spawn_stands", id -> id.getPath().endsWith(".json")).entrySet()) {
            try (Reader reader = new InputStreamReader(entry.getValue().getInputStream(), StandardCharsets.UTF_8)) {
                Identifier id = ruleId(entry.getKey(), "cmobs/spawn_stands/");
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                out.add(readSpawnStand(id, json));
            } catch (Exception exception) {
                CustomMobsSpawnerLog.warn("Skipping spawn stand " + entry.getKey() + ": " + exception.getMessage());
            }
        }
    }

    private static SpawnStandDefinition readSpawnStand(Identifier id, JsonObject json) {
        SpawnTarget target = readTarget(json);
        List<String> requiredTags = getStringList(json, "tags");
        requiredTags.addAll(getStringList(json, "required_tags"));
        if (json.has("tag")) {
            requiredTags.add(json.get("tag").getAsString());
        }
        if (requiredTags.isEmpty()) {
            throw new IllegalArgumentException("Spawn stand " + id + " must define tag, tags, or required_tags");
        }

        float chance = json.has("chance") ? clampChance(json.get("chance").getAsFloat()) : 1.0F;
        boolean discardStand = !json.has("discard_stand") || json.get("discard_stand").getAsBoolean();
        boolean repeatable = json.has("repeatable") && json.get("repeatable").getAsBoolean();
        int cooldownTicks = json.has("cooldown_ticks") ? Math.max(1, json.get("cooldown_ticks").getAsInt()) : 20;
        ParsedBlockPredicate requiredBlock = null;
        if (json.has("required_block")) {
            String requiredBlockText = json.get("required_block").getAsString();
            requiredBlock = parseBlockPredicate(requiredBlockText);
            if (requiredBlock == null) {
                throw new IllegalArgumentException("Invalid required_block '" + requiredBlockText + "'");
            }
        }
        double maxPlayerDistance = getDouble(json, "max_player_distance", getDouble(json, "player_range", DEFAULT_SPAWN_STAND_PLAYER_DISTANCE));
        return new SpawnStandDefinition(
                id,
                List.copyOf(requiredTags),
                List.copyOf(getStringList(json, "any_tags")),
                target.kind(),
                target.id(),
                readOffset(json, Vec3d.ZERO),
                chance,
                discardStand,
                requiredBlock,
                maxPlayerDistance,
                List.copyOf(readSpawnStandItemRequirements(json)),
                repeatable,
                cooldownTicks
        );
    }

    private static List<SpawnStandItemRequirement> readSpawnStandItemRequirements(JsonObject json) {
        JsonArray array = firstSpawnStandItemRequirementArray(json);
        if (array == null) {
            return List.of();
        }

        List<SpawnStandItemRequirement> items = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("Spawn stand item requirement must be an object");
            }
            JsonObject itemJson = element.getAsJsonObject();
            Identifier itemId = parseIdentifier(getString(itemJson, "item"), "item");
            Item item = Registries.ITEM.getOrEmpty(itemId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown spawn stand item " + itemId));
            int count = itemJson.has("count") ? Math.max(1, itemJson.get("count").getAsInt()) : 1;
            double distance = getDouble(itemJson, "distance", getDouble(json, "item_distance", 0.5D));
            boolean consume = !itemJson.has("consume") || itemJson.get("consume").getAsBoolean();
            items.add(new SpawnStandItemRequirement(item, count, Math.max(0.0D, distance), consume));
        }
        return items;
    }

    private static JsonArray firstSpawnStandItemRequirementArray(JsonObject json) {
        for (String key : List.of("required_items", "items")) {
            if (json.has(key)) {
                return json.getAsJsonArray(key);
            }
        }
        return null;
    }

    private static boolean matchesBlockPredicate(ServerWorld world, BlockPos pos, ParsedBlockPredicate parsed) {
        if (!SpawnSafety.isLoaded(world, pos)) {
            return false;
        }
        BlockState state = world.getBlockState(pos);
        if (!state.isOf(parsed.block())) {
            return false;
        }

        for (Map.Entry<String, String> entry : parsed.properties().entrySet()) {
            Property<?> property = state.getBlock().getStateManager().getProperty(entry.getKey());
            if (property == null || !matchesPropertyValue(state, property, entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static ParsedBlockPredicate parseBlockPredicate(String predicate) {
        int stateStart = predicate.indexOf('[');
        String blockId = stateStart >= 0 ? predicate.substring(0, stateStart) : predicate;
        if (stateStart >= 0 && !predicate.endsWith("]")) {
            return null;
        }
        Identifier id = Identifier.tryParse(blockId);
        if (id == null) {
            return null;
        }

        Block block = Registries.BLOCK.getOrEmpty(id).orElse(null);
        if (block == null) {
            return null;
        }

        Map<String, String> properties = new HashMap<>();
        if (stateStart >= 0 && predicate.endsWith("]")) {
            String propertyText = predicate.substring(stateStart + 1, predicate.length() - 1);
            if (!propertyText.isBlank()) {
                for (String property : propertyText.split(",")) {
                    String[] parts = property.split("=", 2);
                    if (parts.length != 2) {
                        return null;
                    }
                    properties.put(parts[0].trim(), parts[1].trim());
                }
            }
        }
        return new ParsedBlockPredicate(block, properties);
    }

    private static <T extends Comparable<T>> boolean matchesPropertyValue(BlockState state, Property<T> property, String value) {
        return property.parse(value)
                .map(parsed -> state.get(property).equals(parsed))
                .orElse(false);
    }

    private static String getString(JsonObject json, String key) {
        if (!json.has(key)) {
            throw new IllegalArgumentException("Missing required property '" + key + "'");
        }
        return json.get(key).getAsString();
    }

    private static Identifier getIdentifier(JsonObject json, String key) {
        return parseIdentifier(getString(json, key), key);
    }

    private static Identifier parseIdentifier(String value, String property) {
        Identifier id = Identifier.tryParse(value);
        if (id == null) {
            throw new IllegalArgumentException("Invalid identifier in '" + property + "': " + value);
        }
        return id;
    }

    private static List<String> getStringList(JsonObject json, String key) {
        List<String> values = new ArrayList<>();
        if (!json.has(key)) {
            return values;
        }

        JsonElement element = json.get(key);
        if (element.isJsonPrimitive()) {
            values.add(element.getAsString());
            return values;
        }

        JsonArray array = element.getAsJsonArray();
        for (JsonElement value : array) {
            values.add(value.getAsString());
        }
        return values;
    }

    private static double getDouble(JsonObject json, String key, double fallback) {
        return json != null && json.has(key) ? json.get(key).getAsDouble() : fallback;
    }

    private static Vec3d readOffset(JsonObject json, Vec3d fallback) {
        if (json == null || !json.has("offset")) {
            return fallback;
        }

        JsonElement offset = json.get("offset");
        if (offset.isJsonArray()) {
            JsonArray array = offset.getAsJsonArray();
            if (array.size() != 3) {
                throw new IllegalArgumentException("Mob group offset arrays must contain x, y, and z");
            }
            return new Vec3d(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble());
        }
        if (offset.isJsonObject()) {
            JsonObject object = offset.getAsJsonObject();
            return new Vec3d(
                    getDouble(object, "x", fallback.x),
                    getDouble(object, "y", fallback.y),
                    getDouble(object, "z", fallback.z)
            );
        }
        throw new IllegalArgumentException("Mob group offset must be an object or [x, y, z] array");
    }

    private static int readCooldownTicks(JsonObject json, String ticksKey, String secondsKey) {
        if (json.has(ticksKey)) {
            return json.get(ticksKey).getAsInt();
        }
        if (json.has(secondsKey)) {
            return Math.round(json.get(secondsKey).getAsFloat() * 20.0F);
        }
        return 0;
    }

    private static Identifier ruleId(Identifier resourceId, String prefix) {
        String path = resourceId.getPath();
        if (path.startsWith(prefix)) {
            path = path.substring(prefix.length());
        }
        if (path.endsWith(".json")) {
            path = path.substring(0, path.length() - ".json".length());
        }
        return new Identifier(resourceId.getNamespace(), path);
    }

    private static SpawnTarget readTarget(JsonObject json) {
        int targetCount = 0;
        targetCount += json.has("entity") ? 1 : 0;
        targetCount += json.has("custom_mob") ? 1 : 0;
        targetCount += json.has("function") ? 1 : 0;
        targetCount += json.has("group") ? 1 : 0;
        if (targetCount != 1) {
            throw new IllegalArgumentException("Expected exactly one target: entity, custom_mob, function, or group");
        }
        if (json.has("custom_mob")) {
            return new SpawnTarget(SpawnTargetKind.CUSTOM_MOB, getIdentifier(json, "custom_mob"));
        }
        if (json.has("function")) {
            return new SpawnTarget(SpawnTargetKind.FUNCTION, getIdentifier(json, "function"));
        }
        if (json.has("group")) {
            return new SpawnTarget(SpawnTargetKind.GROUP, getIdentifier(json, "group"));
        }
        return new SpawnTarget(SpawnTargetKind.ENTITY, getIdentifier(json, "entity"));
    }

    private static List<ReplacementEntry> readReplacementEntries(JsonObject json) {
        List<ReplacementEntry> entries = new ArrayList<>();
        if (json.has("replacements")) {
            for (JsonElement element : json.getAsJsonArray("replacements")) {
                JsonObject entry = element.getAsJsonObject();
                SpawnTarget target = readTarget(entry);
                int weight = entry.has("weight") ? entry.get("weight").getAsInt() : 1;
                entries.add(new ReplacementEntry(
                        target.kind(),
                        target.id(),
                        Math.max(0, weight),
                        entry.has("min_y") ? entry.get("min_y").getAsInt() : null,
                        entry.has("max_y") ? entry.get("max_y").getAsInt() : null
                ));
            }
            return entries;
        }

        SpawnTarget legacyTarget = readLegacyReplacementTarget(json);
        entries.add(new ReplacementEntry(legacyTarget.kind(), legacyTarget.id(), 1));
        return entries;
    }

    private static SpawnTarget readLegacyReplacementTarget(JsonObject json) {
        if (json.has("target")) {
            return new SpawnTarget(SpawnTargetKind.ENTITY, getIdentifier(json, "target"));
        }
        return readTarget(json);
    }

    private static String defaultSpawnGroup(SpawnTarget target) {
        Optional<EntityType<?>> type = switch (target.kind()) {
            case ENTITY -> Registries.ENTITY_TYPE.getOrEmpty(target.id());
            case CUSTOM_MOB -> CustomMobRegistry.getEntityType(target.id());
            case FUNCTION, GROUP -> Optional.empty();
        };
        return type
                .map(entityType -> entityType.getSpawnGroup().getName())
                .orElse("monster");
    }

    private record MobGroupDefinition(Identifier id, List<GroupMember> members, List<GroupPool> pools) {
    }

    private record GroupPool(float chance, List<GroupMember> entries) {
    }

    private record GroupMember(SpawnTargetKind kind, Identifier id, int weight, float chance, double x, double y, double z, float yaw) {
    }

    private record SpawnTarget(SpawnTargetKind kind, Identifier id) {
    }

    private record SpawnStandDefinition(Identifier definitionId, List<String> requiredTags, List<String> anyTags,
                                        SpawnTargetKind kind, Identifier id, Vec3d offset, float chance,
                                        boolean discardStand, ParsedBlockPredicate requiredBlock, double maxPlayerDistance,
                                        List<SpawnStandItemRequirement> requiredItems, boolean repeatable,
                                        int cooldownTicks) {
    }

    private record SpawnStandItemRequirement(Item item, int count, double distance, boolean consume) {
    }

    private record ParsedBlockPredicate(Block block, Map<String, String> properties) {
    }

    private static float clampChance(float chance) {
        if (chance < 0.0F) {
            return 0.0F;
        }
        return Math.min(chance, 1.0F);
    }
}
