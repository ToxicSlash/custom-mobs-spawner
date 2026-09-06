package com.ashtonthedev.custommobsspawner.skill;

import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;

public record CustomSkillDefinition(
        Identifier id,
        SkillTrigger trigger,
        Identifier predicate,
        float chance,
        int intervalTicks,
        int cooldownTicks,
        int globalCooldownTicks,
        boolean playerSkill,
        JsonObject json
) {
}
