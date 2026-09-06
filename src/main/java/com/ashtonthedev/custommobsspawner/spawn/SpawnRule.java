package com.ashtonthedev.custommobsspawner.spawn;

import net.minecraft.util.Identifier;

import java.util.List;

public record SpawnRule(
        Identifier id,
        SpawnTargetKind targetKind,
        Identifier target,
        List<String> dimensions,
        List<String> biomes,
        int minLight,
        int maxLight,
        List<String> blocks,
        boolean extraSpawn,
        int intervalTicks,
        int attemptsPerPlayer,
        float chance,
        int minDistance,
        int maxDistance,
        String spawnGroup,
        int groupMobCap,
        int mobCap,
        boolean obeyDoMobSpawning
) {
    public String ruleTag() {
        return "custom_spawn_rule_" + sanitize(id.toString());
    }

    public String groupTag() {
        return "custom_spawn_group_" + sanitize(spawnGroup);
    }

    public String displayTarget() {
        return targetKind.name().toLowerCase() + ":" + target;
    }

    private static String sanitize(String value) {
        return value.toLowerCase().replaceAll("[^a-z0-9_]", "_");
    }
}
