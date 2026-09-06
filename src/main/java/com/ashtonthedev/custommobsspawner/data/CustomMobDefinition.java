package com.ashtonthedev.custommobsspawner.data;

import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;

public record CustomMobDefinition(
        Identifier id,
        Identifier entity,
        JsonObject json
) {
}
