package com.ashtonthedev.custommobsspawner.data;

import com.ashtonthedev.custommobsspawner.spawn.SpawnTargetKind;
import net.minecraft.util.Identifier;

public record ReplacementEntry(SpawnTargetKind kind, Identifier id, int weight, Integer minY, Integer maxY) {
    public ReplacementEntry(SpawnTargetKind kind, Identifier id, int weight) {
        this(kind, id, weight, null, null);
    }
}
