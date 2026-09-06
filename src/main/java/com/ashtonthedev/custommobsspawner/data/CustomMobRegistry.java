package com.ashtonthedev.custommobsspawner.data;

import com.ashtonthedev.custommobsspawner.util.SpawnSafety;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ashtonthedev.custommobsspawner.skill.CustomSkillRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.BlockPos;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class CustomMobRegistry {
    private static final int MAX_PASSENGER_DEPTH = 4;
    private static final double MIN_SCALE = 0.01D;
    private static final double MAX_SCALE = 64.0D;
    private static final int DEFAULT_SCALE_DELAY_TICKS = 1;
    private static final String POST_SPAWN_APPLIED_TAG = "cmobs_spawn_integrations_applied";
    private static final Map<Identifier, CustomMobDefinition> DEFINITIONS = new ConcurrentHashMap<>();

    private CustomMobRegistry() {
    }

    public static void replaceAll(Collection<CustomMobDefinition> definitions) {
        DEFINITIONS.clear();
        for (CustomMobDefinition definition : definitions) {
            DEFINITIONS.put(definition.id(), definition);
        }
    }

    public static Optional<CustomMobDefinition> get(Identifier id) {
        return Optional.ofNullable(DEFINITIONS.get(id));
    }

    public static Optional<EntityType<?>> getEntityType(Identifier customMobId) {
        return get(customMobId).flatMap(definition -> Registries.ENTITY_TYPE.getOrEmpty(definition.entity()));
    }

    public static Set<Identifier> ids() {
        return DEFINITIONS.keySet();
    }

    public static Entity spawn(Identifier customMobId, ServerWorld world, BlockPos pos, float yaw) {
        Entity entity = create(customMobId, world, pos, yaw);
        if (entity == null) {
            return null;
        }
        if (!SpawnSafety.isLoaded(world, entity)) {
            return null;
        }
        world.spawnEntityAndPassengers(entity);
        applyPostSpawn(entity);
        return entity;
    }

    public static Entity create(Identifier customMobId, ServerWorld world, BlockPos pos, float yaw) {
        return create(customMobId, world, pos, yaw, 0);
    }

    private static Entity create(Identifier customMobId, ServerWorld world, BlockPos pos, float yaw, int passengerDepth) {
        if (!SpawnSafety.isLoadedAround(world, pos, 1, 2)) {
            return null;
        }

        CustomMobDefinition definition = DEFINITIONS.get(customMobId);
        if (definition == null) {
            return null;
        }

        Optional<EntityType<?>> type = Registries.ENTITY_TYPE.getOrEmpty(definition.entity());
        if (type.isEmpty()) {
            return null;
        }

        Entity entity = type.get().create(world);
        if (entity == null) {
            return null;
        }

        entity.refreshPositionAndAngles(pos, yaw, 0.0F);
        if (entity instanceof MobEntity mob) {
            mob.initialize(world, world.getLocalDifficulty(pos), SpawnReason.NATURAL, null, null);
        }

        applyDefinition(entity, definition);
        applyPassengers(entity, definition, world, pos, yaw, passengerDepth);
        return entity;
    }

    private static void applyPassengers(Entity entity, CustomMobDefinition definition, ServerWorld world, BlockPos pos, float yaw, int passengerDepth) {
        JsonObject json = definition.json();
        if (!json.has("passengers")) {
            return;
        }
        if (passengerDepth >= MAX_PASSENGER_DEPTH) {
            CustomMobsSpawnerLog.warn("Skipping custom mob passengers for " + definition.id() + ": passenger nesting is too deep");
            return;
        }

        JsonElement passengers = json.get("passengers");
        if (passengers.isJsonArray()) {
            for (JsonElement passenger : passengers.getAsJsonArray()) {
                addPassenger(entity, passenger, world, pos, yaw, passengerDepth);
            }
            return;
        }
        addPassenger(entity, passengers, world, pos, yaw, passengerDepth);
    }

    private static void addPassenger(Entity entity, JsonElement passengerJson, ServerWorld world, BlockPos pos, float yaw, int passengerDepth) {
        Identifier passengerId = passengerCustomMobId(passengerJson);
        if (passengerId == null) {
            CustomMobsSpawnerLog.warn("Skipping invalid custom mob passenger");
            return;
        }

        Entity passenger = create(passengerId, world, pos, yaw, passengerDepth + 1);
        if (passenger == null) {
            CustomMobsSpawnerLog.warn("Failed to create custom mob passenger " + passengerId);
            return;
        }

        passenger.refreshPositionAndAngles(entity.getX(), entity.getY(), entity.getZ(), yaw, entity.getPitch());
        passenger.startRiding(entity, true);
    }

    private static Identifier passengerCustomMobId(JsonElement passengerJson) {
        if (passengerJson == null || passengerJson.isJsonNull()) {
            return null;
        }
        if (passengerJson.isJsonPrimitive()) {
            return Identifier.tryParse(passengerJson.getAsString());
        }
        if (!passengerJson.isJsonObject()) {
            return null;
        }
        JsonObject json = passengerJson.getAsJsonObject();
        String id = firstString(json, "custom_mob", "id");
        return id == null ? null : Identifier.tryParse(id);
    }

    private static void applyDefinition(Entity entity, CustomMobDefinition definition) {
        JsonObject json = definition.json();

        if (json.has("custom_name")) {
            entity.setCustomName(Text.literal(json.get("custom_name").getAsString()));
            entity.setCustomNameVisible(!json.has("custom_name_visible") || json.get("custom_name_visible").getAsBoolean());
        }

        if (json.has("loot_table")) {
            NbtCompound nbt = new NbtCompound();
            entity.writeNbt(nbt);
            nbt.putString("DeathLootTable", json.get("loot_table").getAsString());
            entity.readNbt(nbt);
        }

        if (entity instanceof MobEntity mob) {
            applyEquipment(mob, json);
            applyAttributes(mob, json);
            if (json.has("health")) {
                mob.setHealth(json.get("health").getAsFloat());
            }
        }

        if (json.has("nbt")) {
            try {
                NbtCompound base = new NbtCompound();
                entity.writeNbt(base);
                base.copyFrom(StringNbtReader.parse(json.get("nbt").getAsString()));
                entity.readNbt(base);
            } catch (Exception exception) {
                CustomMobsSpawnerLog.warn("Failed to apply custom mob NBT for " + definition.id() + ": " + exception.getMessage());
            }
        }

        applyTagsAndSkills(entity, definition, json);
        applyTeam(entity, definition);
    }

    private static void applyTagsAndSkills(Entity entity, CustomMobDefinition definition, JsonObject json) {
        if (json.has("tags")) {
            for (JsonElement tag : json.getAsJsonArray("tags")) {
                entity.addCommandTag(tag.getAsString());
            }
        }

        entity.addCommandTag(customMobTag(definition.id()));

        if (!json.has("skills")) {
            return;
        }
        for (JsonElement skill : json.getAsJsonArray("skills")) {
            if (!skill.isJsonPrimitive()) {
                CustomMobsSpawnerLog.warn("Skipping non-string skill on custom mob " + definition.id());
                continue;
            }
            Identifier skillId = Identifier.tryParse(skill.getAsString());
            if (skillId == null) {
                CustomMobsSpawnerLog.warn("Skipping invalid skill id " + skill.getAsString() + " on custom mob " + definition.id());
                continue;
            }
            CustomSkillRegistry.attachSkill(entity, skillId);
        }
    }

    public static void applyPostSpawn(Entity entity) {
        if (entity == null) {
            return;
        }

        applyPostSpawnSingle(entity);
        for (Entity passenger : entity.getPassengerList()) {
            applyPostSpawn(passenger);
        }
    }

    private static void applyPostSpawnSingle(Entity entity) {
        if (entity.getCommandTags().contains(POST_SPAWN_APPLIED_TAG)) {
            return;
        }

        CustomMobDefinition definition = definitionFor(entity).orElse(null);
        if (definition == null) {
            return;
        }

        entity.addCommandTag(POST_SPAWN_APPLIED_TAG);
        JsonObject json = definition.json();
        applyPehkuiScales(entity, definition.id(), json);
        applyL2Hostility(entity, definition.id(), json);
    }

    private static Optional<CustomMobDefinition> definitionFor(Entity entity) {
        for (CustomMobDefinition definition : DEFINITIONS.values()) {
            if (entity.getCommandTags().contains(customMobTag(definition.id()))) {
                return Optional.of(definition);
            }
        }
        return Optional.empty();
    }

    private static void applyPehkuiScales(Entity entity, Identifier definitionId, JsonObject json) {
        JsonElement element = firstElement(json, "pehkui", "scales", "scale");
        if (element == null || element.isJsonNull()) {
            return;
        }

        Map<Identifier, Double> values = new LinkedHashMap<>();
        int delayTicks = DEFAULT_SCALE_DELAY_TICKS;
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            addAllDefaultScales(values, element.getAsDouble());
        } else if (element.isJsonObject()) {
            JsonObject scale = element.getAsJsonObject();
            delayTicks = getInt(scale, "delay_ticks", getInt(scale, "delay", DEFAULT_SCALE_DELAY_TICKS));
            addScaleAliases(values, scale);
            addCustomScaleTypes(values, firstObject(scale, "types", "values"));
        } else {
            CustomMobsSpawnerLog.warn("Skipping invalid Pehkui scale config on custom mob " + definitionId);
            return;
        }

        for (Map.Entry<Identifier, Double> entry : values.entrySet()) {
            double value = clampScale(entry.getValue());
            if (delayTicks >= 0) {
                runSpawnCommand(entity, "scale delay set " + entry.getKey() + " " + Math.max(0, delayTicks) + " @s");
            }
            runSpawnCommand(entity, "scale set " + entry.getKey() + " " + value + " @s");
        }
    }

    private static void addScaleAliases(Map<Identifier, Double> values, JsonObject scale) {
        putIfNumber(values, "pehkui:hitbox_height", firstElement(scale, "hitbox_height"));
        putIfNumber(values, "pehkui:hitbox_width", firstElement(scale, "hitbox_width"));
        putIfNumber(values, "pehkui:model_height", firstElement(scale, "model_height", "visual_height"));
        putIfNumber(values, "pehkui:model_width", firstElement(scale, "model_width", "visual_width"));

        JsonElement all = firstElement(scale, "all", "base", "scale");
        if (isNumber(all)) {
            addAllDefaultScales(values, all.getAsDouble());
        }

        JsonElement hitbox = firstElement(scale, "hitbox", "hitbox_scale");
        if (isNumber(hitbox)) {
            values.put(new Identifier("pehkui", "hitbox_height"), hitbox.getAsDouble());
            values.put(new Identifier("pehkui", "hitbox_width"), hitbox.getAsDouble());
        }

        JsonElement model = firstElement(scale, "model", "visual", "model_scale", "visual_scale");
        if (isNumber(model)) {
            values.put(new Identifier("pehkui", "model_height"), model.getAsDouble());
            values.put(new Identifier("pehkui", "model_width"), model.getAsDouble());
        }
    }

    private static void addAllDefaultScales(Map<Identifier, Double> values, double value) {
        values.put(new Identifier("pehkui", "hitbox_height"), value);
        values.put(new Identifier("pehkui", "hitbox_width"), value);
        values.put(new Identifier("pehkui", "model_height"), value);
        values.put(new Identifier("pehkui", "model_width"), value);
    }

    private static void putIfNumber(Map<Identifier, Double> values, String id, JsonElement element) {
        if (isNumber(element)) {
            values.put(new Identifier(id), element.getAsDouble());
        }
    }

    private static void addCustomScaleTypes(Map<Identifier, Double> values, JsonObject scaleTypes) {
        if (scaleTypes == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : scaleTypes.entrySet()) {
            if (!isNumber(entry.getValue())) {
                continue;
            }

            Identifier scaleId = parseScaleIdentifier(entry.getKey());
            if (scaleId != null) {
                values.put(scaleId, entry.getValue().getAsDouble());
            }
        }
    }

    private static Identifier parseScaleIdentifier(String value) {
        Identifier id = Identifier.tryParse(value.contains(":") ? value : "pehkui:" + value);
        if (id == null) {
            CustomMobsSpawnerLog.warn("Skipping invalid Pehkui scale id " + value);
        }
        return id;
    }

    private static double clampScale(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 1.0D;
        }
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
    }

    private static void applyL2Hostility(Entity entity, Identifier definitionId, JsonObject json) {
        JsonObject hostility = firstObject(json, "l2hostility", "hostility");
        if (hostility == null) {
            return;
        }

        if (getBoolean(hostility, "clear_traits", getBoolean(hostility, "clear", false))) {
            runSpawnCommand(entity, "hostility mobs @s trait clear");
        }

        applyL2Level(entity, hostility);
        applyL2Traits(entity, definitionId, hostility);
    }

    private static void applyL2Level(Entity entity, JsonObject hostility) {
        JsonElement level = firstElement(hostility, "level");
        if (level != null && !level.isJsonNull()) {
            String operation = normalizeL2LevelOperation(firstString(hostility, "level_operation", "level_mode"));
            int amount;
            if (level.isJsonObject()) {
                JsonObject levelJson = level.getAsJsonObject();
                operation = normalizeL2LevelOperation(firstString(levelJson, "operation", "mode", "op"));
                amount = getInt(levelJson, "amount", getInt(levelJson, "value", getInt(levelJson, "level", 0)));
            } else {
                amount = level.getAsInt();
            }
            runSpawnCommand(entity, "hostility mobs @s level " + operation + " " + Math.max(0, amount));
        }

        if (hostility.has("set_level")) {
            runSpawnCommand(entity, "hostility mobs @s level set " + Math.max(0, hostility.get("set_level").getAsInt()));
        }
        if (hostility.has("add_level")) {
            runSpawnCommand(entity, "hostility mobs @s level add " + Math.max(0, hostility.get("add_level").getAsInt()));
        }
    }

    private static void applyL2Traits(Entity entity, Identifier definitionId, JsonObject hostility) {
        JsonElement traits = firstElement(hostility, "traits");
        if (traits == null || traits.isJsonNull()) {
            return;
        }

        if (traits.isJsonObject()) {
            JsonObject object = traits.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                applyL2Trait(entity, definitionId, entry.getKey(), entry.getValue());
            }
            return;
        }

        if (!traits.isJsonArray()) {
            CustomMobsSpawnerLog.warn("Skipping invalid L2Hostility traits on custom mob " + definitionId);
            return;
        }

        for (JsonElement trait : traits.getAsJsonArray()) {
            applyL2Trait(entity, definitionId, null, trait);
        }
    }

    private static void applyL2Trait(Entity entity, Identifier definitionId, String fallbackId, JsonElement element) {
        String traitId = fallbackId;
        int level = 1;
        String operation = "set";

        if (element.isJsonPrimitive()) {
            if (element.getAsJsonPrimitive().isNumber()) {
                level = element.getAsInt();
            } else {
                traitId = element.getAsString();
            }
        } else if (element.isJsonObject()) {
            JsonObject trait = element.getAsJsonObject();
            traitId = firstString(trait, "id", "trait", "type");
            if (traitId == null) {
                traitId = fallbackId;
            }
            level = getInt(trait, "level", getInt(trait, "amount", getInt(trait, "amplifier", 1)));
            operation = normalizeL2TraitOperation(firstString(trait, "operation", "mode", "op"));
        }

        Identifier id = parseL2Identifier(traitId);
        if (id == null) {
            CustomMobsSpawnerLog.warn("Skipping invalid L2Hostility trait on custom mob " + definitionId + ": " + traitId);
            return;
        }
        if ("remove".equals(operation)) {
            runSpawnCommand(entity, "hostility mobs @s trait remove " + id);
            return;
        }
        runSpawnCommand(entity, "hostility mobs @s trait set " + id + " " + Math.max(0, level));
    }

    private static Identifier parseL2Identifier(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Identifier.tryParse(value.contains(":") ? value : "l2hostility:" + value);
    }

    private static String normalizeL2TraitOperation(String value) {
        return switch (value == null ? "" : value.toLowerCase()) {
            case "remove", "delete" -> "remove";
            default -> "set";
        };
    }

    private static String normalizeL2LevelOperation(String value) {
        return switch (value == null ? "" : value.toLowerCase()) {
            case "add" -> "add";
            case "set" -> "set";
            case "addandrerolltrait", "add_and_reroll_trait" -> "addAndRerollTrait";
            case "setandrerolltrait", "set_and_reroll_trait", "" -> "setAndRerollTrait";
            default -> {
                String cleaned = value.replaceAll("[^A-Za-z]", "");
                yield cleaned.isBlank() ? "setAndRerollTrait" : cleaned;
            }
        };
    }

    private static int runSpawnCommand(Entity entity, String command) {
        if (!(entity.getWorld() instanceof ServerWorld world)) {
            return 0;
        }

        try {
            ServerCommandSource source = entity.getCommandSource()
                    .withWorld(world)
                    .withPosition(entity.getPos())
                    .withRotation(Vec2f.ZERO)
                    .withLevel(2)
                    .withSilent();
            return world.getServer().getCommandManager().executeWithPrefix(source, command);
        } catch (RuntimeException exception) {
            CustomMobsSpawnerLog.warn("Failed to run custom mob spawn command '" + command + "': " + exception.getMessage());
            return 0;
        }
    }

    private static void applyTeam(Entity entity, CustomMobDefinition definition) {
        JsonObject json = definition.json();
        if (!json.has("team") || !(entity.getWorld() instanceof ServerWorld world)) {
            return;
        }

        String teamName = json.get("team").getAsString();
        if (teamName.isBlank()) {
            return;
        }

        try {
            Scoreboard scoreboard = world.getScoreboard();
            Team team = scoreboard.getTeam(teamName);
            if (team == null) {
                team = scoreboard.addTeam(teamName);
                team.setFriendlyFireAllowed(json.has("team_friendly_fire") && json.get("team_friendly_fire").getAsBoolean());
                if (json.has("team_show_friendly_invisibles")) {
                    team.setShowFriendlyInvisibles(json.get("team_show_friendly_invisibles").getAsBoolean());
                }
            }
            scoreboard.addPlayerToTeam(entity.getEntityName(), team);
        } catch (Exception exception) {
            CustomMobsSpawnerLog.warn("Failed to add custom mob " + definition.id() + " to scoreboard team " + teamName + ": " + exception.getMessage());
        }
    }

    private static void applyEquipment(MobEntity mob, JsonObject json) {
        if (json.has("equipment")) {
            JsonObject equipment = json.getAsJsonObject("equipment");
            equip(mob, equipment, "head", EquipmentSlot.HEAD);
            equip(mob, equipment, "chest", EquipmentSlot.CHEST);
            equip(mob, equipment, "legs", EquipmentSlot.LEGS);
            equip(mob, equipment, "feet", EquipmentSlot.FEET);
            equip(mob, equipment, "mainhand", EquipmentSlot.MAINHAND);
            equip(mob, equipment, "offhand", EquipmentSlot.OFFHAND);
        }
    }

    private static void equip(MobEntity mob, JsonObject equipment, String key, EquipmentSlot slot) {
        if (!equipment.has(key)) {
            return;
        }
        mob.equipStack(slot, readStack(equipment.get(key)));
    }

    public static ItemStack readStack(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return ItemStack.EMPTY;
        }

        if (element.isJsonPrimitive()) {
            return readStack(element.getAsString(), 1, null);
        }
        if (!element.isJsonObject()) {
            return ItemStack.EMPTY;
        }

        JsonObject json = element.getAsJsonObject();
        String itemId = firstString(json, "id", "item");
        if (itemId == null || itemId.isBlank()) {
            return ItemStack.EMPTY;
        }

        int count = json.has("count") ? Math.max(0, json.get("count").getAsInt()) : 1;
        String nbt = firstString(json, "nbt");
        return readStack(itemId, count, nbt);
    }

    private static ItemStack readStack(String id, int count, String nbt) {
        Identifier itemId = Identifier.tryParse(id);
        if (itemId == null || count <= 0) {
            return ItemStack.EMPTY;
        }

        var item = Registries.ITEM.getOrEmpty(itemId);
        if (item.isEmpty()) {
            CustomMobsSpawnerLog.warn("Unsupported item " + itemId);
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(item.get(), count);
        if (nbt != null && !nbt.isBlank()) {
            try {
                stack.setNbt(StringNbtReader.parse(nbt));
            } catch (Exception exception) {
                CustomMobsSpawnerLog.warn("Failed to apply item NBT for " + itemId + ": " + exception.getMessage());
            }
        }
        return stack;
    }

    private static String firstString(JsonObject json, String... keys) {
        if (json == null) {
            return null;
        }
        for (String key : keys) {
            if (json.has(key)) {
                return json.get(key).getAsString();
            }
        }
        return null;
    }

    private static JsonElement firstElement(JsonObject json, String... keys) {
        if (json == null) {
            return null;
        }
        for (String key : keys) {
            if (json.has(key)) {
                return json.get(key);
            }
        }
        return null;
    }

    private static JsonObject firstObject(JsonObject json, String... keys) {
        JsonElement element = firstElement(json, keys);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static boolean getBoolean(JsonObject json, String key, boolean fallback) {
        return json != null && json.has(key) ? json.get(key).getAsBoolean() : fallback;
    }

    private static int getInt(JsonObject json, String key, int fallback) {
        return json != null && json.has(key) ? json.get(key).getAsInt() : fallback;
    }

    private static boolean isNumber(JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber();
    }

    private static String customMobTag(Identifier id) {
        return "custom_mob_" + id.toString().toLowerCase().replaceAll("[^a-z0-9_]", "_");
    }

    private static void applyAttributes(MobEntity mob, JsonObject json) {
        if (!json.has("attributes")) {
            return;
        }

        JsonArray attributes = json.getAsJsonArray("attributes");
        for (JsonElement element : attributes) {
            if (!element.isJsonObject()) {
                CustomMobsSpawnerLog.warn("Skipping non-object attribute on custom mob");
                continue;
            }
            JsonObject attributeJson = element.getAsJsonObject();
            if (!attributeJson.has("id")) {
                CustomMobsSpawnerLog.warn("Skipping custom mob attribute without id");
                continue;
            }
            Identifier id = Identifier.tryParse(attributeJson.get("id").getAsString());
            if (id == null) {
                CustomMobsSpawnerLog.warn("Skipping invalid custom mob attribute " + attributeJson.get("id").getAsString());
                continue;
            }
            EntityAttribute attribute = Registries.ATTRIBUTE.getOrEmpty(id).orElse(null);
            if (attribute == null) {
                CustomMobsSpawnerLog.warn("Skipping unsupported custom mob attribute " + id);
                continue;
            }
            EntityAttributeInstance instance = mob.getAttributeInstance(attribute);
            if (instance != null && attributeJson.has("base")) {
                instance.setBaseValue(attributeJson.get("base").getAsDouble());
            }
        }

        EntityAttributeInstance maxHealth = mob.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (maxHealth != null && mob.getHealth() > maxHealth.getValue()) {
            mob.setHealth((float) maxHealth.getValue());
        }
    }
}
