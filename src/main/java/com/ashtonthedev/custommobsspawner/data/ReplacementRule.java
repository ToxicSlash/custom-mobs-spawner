package com.ashtonthedev.custommobsspawner.data;

import net.minecraft.util.Identifier;

import java.util.List;

public record ReplacementRule(EntitySelector source, float chance, List<ReplacementEntry> replacements, List<String> dimensions) {
    public ReplacementRule(EntitySelector source, float chance, List<ReplacementEntry> replacements) {
        this(source, chance, replacements, List.of());
    }
}
