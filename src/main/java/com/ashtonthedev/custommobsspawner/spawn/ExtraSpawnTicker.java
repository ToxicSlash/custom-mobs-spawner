package com.ashtonthedev.custommobsspawner.spawn;

import com.ashtonthedev.custommobsspawner.data.CustomMobRegistry;
import com.ashtonthedev.custommobsspawner.util.SpawnSafety;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class ExtraSpawnTicker {
    private static final String DEBUG_TAG = "custom_spawn_debug";

    private ExtraSpawnTicker() {
    }

    public static void tick(ServerWorld world) {
        if (world.getPlayers().isEmpty()) {
            return;
        }

        long time = world.getTime();
        Random random = world.random;
        CapCache capCache = null;
        for (SpawnRule rule : CustomSpawnRules.extraSpawnRules()) {
            if (time % rule.intervalTicks() != 0 || random.nextFloat() > rule.chance()) {
                continue;
            }

            if (rule.obeyDoMobSpawning() && !world.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING)) {
                debug(world, rule, "skipped: doMobSpawning is false");
                continue;
            }

            Optional<EntityType<?>> entityType = getEntityType(rule);
            if (entityType.isEmpty() && rule.targetKind() != SpawnTargetKind.FUNCTION) {
                debug(world, rule, "failed: unknown entity type for " + rule.displayTarget());
                continue;
            }

            if (world.getDifficulty() == Difficulty.PEACEFUL && parseSpawnGroup(rule.spawnGroup()) == SpawnGroup.MONSTER) {
                debug(world, rule, "skipped: peaceful difficulty blocks monster extra spawns");
                continue;
            }

            if (hasCapChecks(rule)) {
                if (capCache == null) {
                    capCache = CapCache.create(world);
                }
                CapResult capResult = checkCaps(capCache, rule);
                if (!capResult.allowed()) {
                    debug(world, rule, capResult.message());
                    continue;
                }
            }

            for (ServerPlayerEntity player : world.getPlayers()) {
                for (int attempt = 0; attempt < rule.attemptsPerPlayer(); attempt++) {
                    SpawnAttemptResult result = trySpawn(world, player, entityType.orElse(null), rule, random);
                    if (isDebugging(player)) {
                        player.sendMessage(Text.literal("[CustomSpawn] " + rule.displayTarget() + " near " + player.getName().getString() + " -> " + result.message()), false);
                    }
                }
            }
        }
    }

    private static boolean hasCapChecks(SpawnRule rule) {
        return rule.mobCap() >= 0 || rule.groupMobCap() >= 0;
    }

    @SuppressWarnings("unchecked")
    private static SpawnAttemptResult trySpawn(ServerWorld world, ServerPlayerEntity player, EntityType<?> type, SpawnRule rule, Random random) {
        BlockPos pos = pickPosition(world, player.getBlockPos(), rule, random);
        if (pos == null) {
            return SpawnAttemptResult.fail("failed: could not find position outside min_distance");
        }
        if (!SpawnSafety.isLoadedAround(world, pos, 1, 2)) {
            return SpawnAttemptResult.fail("failed: chunk not fully loaded at " + format(pos));
        }

        if (!CustomSpawnRules.matchesRule(world, pos, rule)) {
            return SpawnAttemptResult.fail("failed rule checks at " + format(pos));
        }

        if (rule.targetKind() == SpawnTargetKind.FUNCTION) {
            return runFunction(world, player, rule, pos);
        }

        Entity entity = rule.targetKind() == SpawnTargetKind.CUSTOM_MOB
                ? CustomMobRegistry.create(rule.target(), world, pos, random.nextFloat() * 360.0F)
                : type.create(world);
        if (!(entity instanceof MobEntity mob)) {
            return SpawnAttemptResult.fail("failed: could not create mob entity");
        }

        if (!MobEntity.canMobSpawn((EntityType<? extends MobEntity>) mob.getType(), world, SpawnReason.NATURAL, pos, random)) {
            return SpawnAttemptResult.fail("failed vanilla placement checks at " + format(pos));
        }

        if (rule.targetKind() == SpawnTargetKind.ENTITY) {
            mob.refreshPositionAndAngles(pos, random.nextFloat() * 360.0F, 0.0F);
        }
        if (!SpawnSafety.isLoaded(world, mob)) {
            return SpawnAttemptResult.fail("failed: entity bounds cross unloaded chunk at " + format(pos));
        }
        if (!world.isSpaceEmpty(mob) || !world.doesNotIntersectEntities(mob)) {
            return SpawnAttemptResult.fail("failed collision checks at " + format(pos));
        }

        mob.addCommandTag("custom_spawned");
        mob.addCommandTag(rule.ruleTag());
        mob.addCommandTag(rule.groupTag());
        if (rule.targetKind() == SpawnTargetKind.ENTITY) {
            mob.initialize(world, world.getLocalDifficulty(pos), SpawnReason.NATURAL, null, null);
        }
        world.spawnEntityAndPassengers(mob);
        if (rule.targetKind() == SpawnTargetKind.CUSTOM_MOB) {
            CustomMobRegistry.applyPostSpawn(mob);
        }
        return SpawnAttemptResult.success("spawned at " + format(pos));
    }

    private static Optional<EntityType<?>> getEntityType(SpawnRule rule) {
        return switch (rule.targetKind()) {
            case ENTITY -> Registries.ENTITY_TYPE.getOrEmpty(rule.target());
            case CUSTOM_MOB -> CustomMobRegistry.getEntityType(rule.target());
            case FUNCTION, GROUP -> Optional.empty();
        };
    }

    private static SpawnAttemptResult runFunction(ServerWorld world, ServerPlayerEntity player, SpawnRule rule, BlockPos pos) {
        if (!SpawnSafety.isLoadedAround(world, pos, 1, 2)) {
            return SpawnAttemptResult.fail("function skipped: chunk not fully loaded at " + format(pos));
        }
        ServerCommandSource source = player.getCommandSource()
                .withWorld(world)
                .withPosition(Vec3d.ofBottomCenter(pos))
                .withRotation(Vec2f.ZERO)
                .withSilent();
        int result = world.getServer().getCommandManager().executeWithPrefix(source, "function " + rule.target());
        return result > 0
                ? SpawnAttemptResult.success("ran function at " + format(pos))
                : SpawnAttemptResult.fail("function returned 0 at " + format(pos));
    }

    private static BlockPos pickPosition(ServerWorld world, BlockPos origin, SpawnRule rule, Random random) {
        int min = Math.min(rule.minDistance(), rule.maxDistance());
        int max = Math.max(rule.minDistance(), rule.maxDistance());
        int minSquared = min * min;
        int maxSquared = max * max;

        for (int attempt = 0; attempt < 16; attempt++) {
            int xOffset = random.nextBetween(-max, max);
            int zOffset = random.nextBetween(-max, max);
            int distanceSquared = xOffset * xOffset + zOffset * zOffset;
            if (distanceSquared < minSquared || distanceSquared > maxSquared) {
                continue;
            }

            int x = origin.getX() + xOffset;
            int z = origin.getZ() + zOffset;
            if (!SpawnSafety.isColumnLoaded(world, x, z)) {
                continue;
            }
            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (SpawnSafety.isLoadedAround(world, pos, 1, 2)) {
                return pos;
            }
        }

        return null;
    }

    private static CapResult checkCaps(CapCache cache, SpawnRule rule) {
        int ruleCount = cache.ruleCounts().getOrDefault(rule.ruleTag(), 0);
        int groupCount = cache.customGroupCounts().getOrDefault(rule.groupTag(), 0)
                + cache.vanillaGroupCounts().getOrDefault(parseSpawnGroup(rule.spawnGroup()), 0);

        if (rule.mobCap() >= 0 && ruleCount >= rule.mobCap()) {
            return CapResult.blocked("blocked by mob_cap " + ruleCount + "/" + rule.mobCap());
        }

        if (rule.groupMobCap() >= 0 && groupCount >= rule.groupMobCap()) {
            return CapResult.blocked("blocked by " + rule.spawnGroup() + " group_mob_cap " + groupCount + "/" + rule.groupMobCap());
        }

        return CapResult.allowed(ruleCount, groupCount);
    }

    private static SpawnGroup parseSpawnGroup(String value) {
        return switch (value.toLowerCase()) {
            case "creature" -> SpawnGroup.CREATURE;
            case "ambient" -> SpawnGroup.AMBIENT;
            case "water_creature" -> SpawnGroup.WATER_CREATURE;
            case "water_ambient" -> SpawnGroup.WATER_AMBIENT;
            case "underground_water_creature" -> SpawnGroup.UNDERGROUND_WATER_CREATURE;
            case "axolotls" -> SpawnGroup.AXOLOTLS;
            case "misc" -> SpawnGroup.MISC;
            default -> SpawnGroup.MONSTER;
        };
    }

    private static boolean isDebugging(ServerPlayerEntity player) {
        return player.getCommandTags().contains(DEBUG_TAG);
    }

    private static void debug(ServerWorld world, SpawnRule rule, String message) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (isDebugging(player)) {
                player.sendMessage(Text.literal("[CustomSpawn] " + rule.displayTarget() + " -> " + message), false);
            }
        }
    }

    private static String format(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private record SpawnAttemptResult(boolean success, String message) {
        private static SpawnAttemptResult success(String message) {
            return new SpawnAttemptResult(true, message);
        }

        private static SpawnAttemptResult fail(String message) {
            return new SpawnAttemptResult(false, message);
        }
    }

    private record CapResult(boolean allowed, String message) {
        private static CapResult allowed(int ruleCount, int groupCount) {
            return new CapResult(true, "caps ok rule=" + ruleCount + " group=" + groupCount);
        }

        private static CapResult blocked(String message) {
            return new CapResult(false, message);
        }
    }

    private record CapCache(
            Map<String, Integer> ruleCounts,
            Map<String, Integer> customGroupCounts,
            Map<SpawnGroup, Integer> vanillaGroupCounts
    ) {
        private static CapCache create(ServerWorld world) {
            Map<String, Integer> ruleCounts = new HashMap<>();
            Map<String, Integer> customGroupCounts = new HashMap<>();
            Map<SpawnGroup, Integer> vanillaGroupCounts = new HashMap<>();

            for (Entity entity : world.iterateEntities()) {
                if (!(entity instanceof MobEntity mob) || !entity.isAlive()) {
                    continue;
                }

                boolean customSpawned = mob.getCommandTags().contains("custom_spawned");
                for (String tag : mob.getCommandTags()) {
                    if (tag.startsWith("custom_spawn_rule_")) {
                        ruleCounts.merge(tag, 1, Integer::sum);
                    } else if (tag.startsWith("custom_spawn_group_")) {
                        customGroupCounts.merge(tag, 1, Integer::sum);
                    }
                }

                if (!customSpawned) {
                    vanillaGroupCounts.merge(mob.getType().getSpawnGroup(), 1, Integer::sum);
                }
            }

            return new CapCache(ruleCounts, customGroupCounts, vanillaGroupCounts);
        }
    }
}
