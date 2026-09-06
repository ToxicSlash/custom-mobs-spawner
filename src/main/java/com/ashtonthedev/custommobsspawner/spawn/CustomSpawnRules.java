package com.ashtonthedev.custommobsspawner.spawn;

import com.ashtonthedev.custommobsspawner.util.SpawnSafety;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LightType;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.WorldAccess;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CustomSpawnRules {
    private static final Map<Identifier, SpawnRule> RULES = new ConcurrentHashMap<>();

    private CustomSpawnRules() {
    }

    public static void replaceAll(Collection<SpawnRule> rules) {
        RULES.clear();
        for (SpawnRule rule : rules) {
            RULES.put(rule.id(), rule);
        }
    }

    public static List<SpawnRule> extraSpawnRules() {
        return RULES.values().stream()
                .filter(SpawnRule::extraSpawn)
                .toList();
    }

    public static boolean canSpawn(EntityType<? extends MobEntity> type, ServerWorldAccess world, SpawnReason reason, BlockPos pos, Random random) {
        Identifier entityId = Registries.ENTITY_TYPE.getId(type);
        SpawnRule rule = RULES.values().stream()
                .filter(candidate -> candidate.targetKind() == SpawnTargetKind.ENTITY && candidate.target().equals(entityId))
                .findFirst()
                .orElse(null);
        if (rule == null) {
            return false;
        }

        return matchesRule(world, pos, rule) && MobEntity.canMobSpawn(type, world, reason, pos, random);
    }

    public static boolean matchesRule(ServerWorldAccess world, BlockPos pos, SpawnRule rule) {
        if (!matchesDimension(world, rule)) {
            return false;
        }
        if (!SpawnSafety.isLoadedAround(world, pos, 1, 1) || !SpawnSafety.isLoaded(world, pos.down())) {
            return false;
        }

        int blockLight = world.getLightLevel(LightType.BLOCK, pos);
        if (blockLight < rule.minLight() || blockLight > rule.maxLight()) {
            return false;
        }

        if (!matchesBiome(world, pos, rule)) {
            return false;
        }

        if (!matchesBlock(world, pos.down(), rule)) {
            return false;
        }

        return true;
    }

    private static boolean matchesDimension(ServerWorldAccess world, SpawnRule rule) {
        if (rule.dimensions().isEmpty()) {
            return true;
        }

        Identifier currentDimension = world.toServerWorld().getRegistryKey().getValue();
        for (String entry : rule.dimensions()) {
            Identifier dimension = Identifier.tryParse(entry);
            if (dimension != null && currentDimension.equals(dimension)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesBiome(WorldAccess world, BlockPos pos, SpawnRule rule) {
        if (rule.biomes().isEmpty()) {
            return true;
        }

        var biome = world.getBiome(pos);
        for (String entry : rule.biomes()) {
            if (entry.startsWith("#")) {
                Identifier tagId = Identifier.tryParse(entry.substring(1));
                if (tagId != null && biome.isIn(TagKey.of(RegistryKeys.BIOME, tagId))) {
                    return true;
                }
            } else {
                var key = world.getRegistryManager().get(RegistryKeys.BIOME).getKey(biome.value());
                Identifier biomeId = Identifier.tryParse(entry);
                if (biomeId != null && key.isPresent() && key.get().getValue().equals(biomeId)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matchesBlock(WorldAccess world, BlockPos pos, SpawnRule rule) {
        if (rule.blocks().isEmpty()) {
            return true;
        }

        BlockState state = world.getBlockState(pos);
        for (String entry : rule.blocks()) {
            if (entry.startsWith("#")) {
                Identifier tagId = Identifier.tryParse(entry.substring(1));
                if (tagId != null && state.isIn(TagKey.of(RegistryKeys.BLOCK, tagId))) {
                    return true;
                }
            } else {
                Identifier blockId = Identifier.tryParse(entry);
                if (blockId != null && Registries.BLOCK.getId(state.getBlock()).equals(blockId)) {
                    return true;
                }
            }
        }
        return false;
    }
}
