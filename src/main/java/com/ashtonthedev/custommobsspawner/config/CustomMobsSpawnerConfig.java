package com.ashtonthedev.custommobsspawner.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ashtonthedev.custommobsspawner.combat.ModAttributes;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CustomMobsSpawnerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("cmobs.json");
    private static ConfigData data = new ConfigData();
    private static long lastLoadedModifiedTime = Long.MIN_VALUE;

    private CustomMobsSpawnerConfig() {
    }

    public static void load() {
        ensureExists();
        readConfig();
    }

    public static ConfigData get() {
        reloadIfChanged();
        return data;
    }

    private static void reloadIfChanged() {
        try {
            long modifiedTime = Files.exists(CONFIG_PATH) ? Files.getLastModifiedTime(CONFIG_PATH).toMillis() : Long.MIN_VALUE;
            if (modifiedTime != lastLoadedModifiedTime) {
                load();
            }
        } catch (IOException ignored) {
        }
    }

    private static void ensureExists() {
        if (Files.exists(CONFIG_PATH)) {
            return;
        }

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            writeConfig(new ConfigData());
        } catch (IOException ignored) {
        }
    }

    private static void readConfig() {
        try {
            lastLoadedModifiedTime = Files.exists(CONFIG_PATH) ? Files.getLastModifiedTime(CONFIG_PATH).toMillis() : Long.MIN_VALUE;
            ConfigData loaded = GSON.fromJson(stripJsonComments(Files.readString(CONFIG_PATH)), ConfigData.class);
            data = loaded == null ? new ConfigData() : loaded.sanitized();
            writeConfig(data);
        } catch (IOException | RuntimeException ignored) {
            data = new ConfigData();
        }
    }

    private static void writeConfig(ConfigData config) throws IOException {
        Files.createDirectories(CONFIG_PATH.getParent());
        Files.writeString(CONFIG_PATH, toCommentedJson(config));
        lastLoadedModifiedTime = Files.getLastModifiedTime(CONFIG_PATH).toMillis();
    }

    private static String toCommentedJson(ConfigData config) {
        StringBuilder out = new StringBuilder();
        out.append("{\n");
        append(out, "visceralCombatLungeScalingEnabled", config.visceralCombatLungeScalingEnabled, "Enables attack speed based lunge distance scaling for Visceral Combat.", true);
        append(out, "visceralCombatExtraLungeScale", config.visceralCombatExtraLungeScale, "Scales how much extra lunge slower attacks receive.", true);
        append(out, "visceralCombatMinimumExtraLunge", config.visceralCombatMinimumExtraLunge, "Sets the smallest extra lunge amount that will be applied.", true);
        append(out, "showSelfOverheadPostureDebugBar", config.showSelfOverheadPostureDebugBar, "Shows your own overhead posture debug bar.", true);
        append(out, "playerBasePostureHealth", config.playerBasePostureHealth, "Sets the base posture health for players.", true);
        append(out, "playerBasePostureRegen", config.playerBasePostureRegen, "Sets how quickly players regenerate posture.", true);
        append(out, "mobBasePostureHealth", config.mobBasePostureHealth, "Sets the base posture health for mobs.", true);
        append(out, "mobBasePostureRegen", config.mobBasePostureRegen, "Sets how quickly mobs regenerate posture.", true);
        append(out, "maxHealthPostureScale", config.maxHealthPostureScale, "Adds player posture health based on max health.", true);
        append(out, "maxHealthPostureBonusCap", config.maxHealthPostureBonusCap, "Caps the player posture bonus from max health.", true);
        append(out, "mobMaxHealthPostureScale", config.mobMaxHealthPostureScale, "Adds mob posture health based on max health.", true);
        append(out, "mobMaxHealthPostureBonusCap", config.mobMaxHealthPostureBonusCap, "Caps the mob posture bonus from max health.", true);
        append(out, "vulnerablePostureDamageMultiplierPerLevel", config.vulnerablePostureDamageMultiplierPerLevel, "Increases posture damage taken per Vulnerable level.", true);
        append(out, "baseStaggerDamageMultiplier", config.baseStaggerDamageMultiplier, "Multiplies damage dealt to staggered mobs.", true);
        append(out, "shieldParryWindowTicks", config.shieldParryWindowTicks, "Sets the parry window when blocking with a shield.", true);
        append(out, "twoHandedParryWindowTicks", config.twoHandedParryWindowTicks, "Sets the parry window when blocking with a two-handed weapon.", true);
        append(out, "shieldPostureHealthBonus", config.shieldPostureHealthBonus, "Adds posture health while using a shield.", true);
        append(out, "twoHandedPostureHealthBonus", config.twoHandedPostureHealthBonus, "Adds posture health while using a two-handed weapon.", true);
        append(out, "playerParryCounterWindowTicks", config.playerParryCounterWindowTicks, "Sets how long a player parry counter window lasts.", true);
        append(out, "playerStaggerTicks", config.playerStaggerTicks, "Sets how long player stagger lasts.", true);
        append(out, "playerStaggerCooldownTicks", config.playerStaggerCooldownTicks, "Sets how long before a player can be staggered again.", true);
        append(out, "playerPostureRegenDelayTicks", config.playerPostureRegenDelayTicks, "Sets the delay before player posture starts regenerating.", true);
        append(out, "playerParryPostureInvulnerabilityTicks", config.playerParryPostureInvulnerabilityTicks, "Sets how long after parrying players ignore posture damage.", true);
        append(out, "playerParryPostureRestore", config.playerParryPostureRestore, "Restores this much player posture on a successful parry.", true);
        append(out, "playerParryPostureDamage", config.playerParryPostureDamage, "Deals this much posture damage when a player parries.", true);
        append(out, "playerMaxPostureDamagePerHit", config.playerMaxPostureDamagePerHit, "Caps final posture damage per hit when the attacker is another player.", true);
        append(out, "playerPvpPostureDamageMultiplier", config.playerPvpPostureDamageMultiplier, "Sets the default base for cmobs:pvp_taken_posture_damage_multiplier.", true);
        append(out, "playerTakenDamagePostureMultiplier", config.playerTakenDamagePostureMultiplier, "Multiplies player posture loss after post-mitigation damage is scaled by 2x.", true);
        append(out, "playerTakenDamagePostureGraceTicks", config.playerTakenDamagePostureGraceTicks, "Sets the minimum ticks between posture loss from normal incoming damage hits.", true);
        append(out, "playerBlockedDamagePostureMultiplier", config.playerBlockedDamagePostureMultiplier, "Multiplies posture damage players take when blocking normal damage.", true);
        append(out, "playerBlockedSkillPostureMultiplier", config.playerBlockedSkillPostureMultiplier, "Multiplies posture damage players take when blocking skill damage.", true);
        append(out, "playerMaxTakenDamagePostureDamage", config.playerMaxTakenDamagePostureDamage, "Caps posture damage converted from raw incoming damage before PvP capping.", true);
        append(out, "mobPostureRegenDelayTicks", config.mobPostureRegenDelayTicks, "Sets the delay before mob posture starts regenerating.", true);
        append(out, "mobPostureDisplayTicks", config.mobPostureDisplayTicks, "Sets how long mob posture bars stay visible after posture changes.", true);
        append(out, "mobStaggerTicks", config.mobStaggerTicks, "Sets how long mob stagger lasts.", true);
        append(out, "mobStaggerCooldownTicks", config.mobStaggerCooldownTicks, "Sets how long before a mob can be staggered again.", true);
        append(out, "mobParryPostureDamage", config.mobParryPostureDamage, "Deals this much posture damage when a mob parries.", true);
        append(out, "knifePostureDamageBonus", config.knifePostureDamageBonus, "Adds posture damage to knife attacks.", true);
        append(out, "knifeStaggerDamageMultiplierBonus", config.knifeStaggerDamageMultiplierBonus, "Adds staggered damage multiplier to knife attacks.", true);
        append(out, "rapierPostureDamageBonus", config.rapierPostureDamageBonus, "Adds posture damage to rapier attacks.", true);
        append(out, "rapierStaggerDamageMultiplierBonus", config.rapierStaggerDamageMultiplierBonus, "Adds staggered damage multiplier to rapier attacks.", false);
        out.append("}\n");
        return out.toString();
    }

    private static void append(StringBuilder out, String key, boolean value, String comment, boolean comma) {
        appendRaw(out, key, Boolean.toString(value), comment, comma);
    }

    private static void append(StringBuilder out, String key, int value, String comment, boolean comma) {
        appendRaw(out, key, Integer.toString(value), comment, comma);
    }

    private static void append(StringBuilder out, String key, double value, String comment, boolean comma) {
        appendRaw(out, key, Double.toString(value), comment, comma);
    }

    private static void appendRaw(StringBuilder out, String key, String value, String comment, boolean comma) {
        out.append("  // ").append(comment).append('\n');
        out.append("  \"").append(key).append("\": ").append(value);
        if (comma) {
            out.append(',');
        }
        out.append('\n');
    }

    private static String stripJsonComments(String json) {
        StringBuilder out = new StringBuilder(json.length());
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            char next = i + 1 < json.length() ? json.charAt(i + 1) : '\0';

            if (inString) {
                out.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }

            if (c == '"') {
                inString = true;
                out.append(c);
            } else if (c == '/' && next == '/') {
                i += 2;
                while (i < json.length() && json.charAt(i) != '\n' && json.charAt(i) != '\r') {
                    i++;
                }
                if (i < json.length()) {
                    out.append(json.charAt(i));
                }
            } else if (c == '/' && next == '*') {
                i += 2;
                while (i + 1 < json.length() && !(json.charAt(i) == '*' && json.charAt(i + 1) == '/')) {
                    i++;
                }
                i++;
            } else {
                out.append(c);
            }
        }

        return out.toString();
    }

    public static final class ConfigData {
        public boolean visceralCombatLungeScalingEnabled = true;
        public double visceralCombatExtraLungeScale = 0.2D;
        public double visceralCombatMinimumExtraLunge = 0.01D;
        public boolean showSelfOverheadPostureDebugBar = true;
        public double playerBasePostureHealth = 50.0D;
        public double playerBasePostureRegen = 40.0D;
        public double mobBasePostureHealth = 25.0D;
        public double mobBasePostureRegen = 100.0D;
        public double maxHealthPostureScale = 0.75D;
        public double maxHealthPostureBonusCap = 1000.0D;
        public double mobMaxHealthPostureScale = 3.0D;
        public double mobMaxHealthPostureBonusCap = 10000.0D;
        public double vulnerablePostureDamageMultiplierPerLevel = 0.5D;
        public double baseStaggerDamageMultiplier = 2.0D;
        public double shieldParryWindowTicks = ModAttributes.SHIELD_PARRY_WINDOW_TICKS;
        public double twoHandedParryWindowTicks = ModAttributes.TWO_HANDED_PARRY_WINDOW_TICKS;
        public double shieldPostureHealthBonus = ModAttributes.SHIELD_POSTURE_HEALTH_BONUS;
        public double twoHandedPostureHealthBonus = ModAttributes.TWO_HANDED_POSTURE_HEALTH_BONUS;
        public int playerParryCounterWindowTicks = 10;
        public int playerStaggerTicks = 40;
        public int playerStaggerCooldownTicks = 80;
        public int playerPostureRegenDelayTicks = 60;
        public int playerParryPostureInvulnerabilityTicks = 5;
        public double playerParryPostureRestore = 25.0D;
        public double playerParryPostureDamage = 40.0D;
        public double playerMaxPostureDamagePerHit = 45.0D;
        public double playerPvpPostureDamageMultiplier = 1.0D;
        public double playerTakenDamagePostureMultiplier = 0.6D;
        public int playerTakenDamagePostureGraceTicks = 2;
        public double playerBlockedDamagePostureMultiplier = 1.25D;
        public double playerBlockedSkillPostureMultiplier = 1.15D;
        public double playerMaxTakenDamagePostureDamage = 80.0D;
        public int mobPostureRegenDelayTicks = 80;
        public int mobPostureDisplayTicks = 400;
        public int mobStaggerTicks = 40;
        public int mobStaggerCooldownTicks = 80;
        public double mobParryPostureDamage = 70.0D;
        public double knifePostureDamageBonus = ModAttributes.KNIFE_POSTURE_DAMAGE_BONUS;
        public double knifeStaggerDamageMultiplierBonus = ModAttributes.KNIFE_STAGGER_DAMAGE_MULTIPLIER_BONUS;
        public double rapierPostureDamageBonus = ModAttributes.RAPIER_POSTURE_DAMAGE_BONUS;
        public double rapierStaggerDamageMultiplierBonus = ModAttributes.RAPIER_STAGGER_DAMAGE_MULTIPLIER_BONUS;

        private ConfigData sanitized() {
            ConfigData sanitized = new ConfigData();
            sanitized.visceralCombatLungeScalingEnabled = visceralCombatLungeScalingEnabled;
            sanitized.visceralCombatExtraLungeScale = clamp(visceralCombatExtraLungeScale, 0.0D, 2.0D);
            sanitized.visceralCombatMinimumExtraLunge = clamp(visceralCombatMinimumExtraLunge, 0.0D, 0.5D);
            sanitized.showSelfOverheadPostureDebugBar = showSelfOverheadPostureDebugBar;
            sanitized.playerBasePostureHealth = clamp(playerBasePostureHealth, 1.0D, 1024.0D);
            sanitized.playerBasePostureRegen = clamp(playerBasePostureRegen, 0.0D, 1024.0D);
            sanitized.mobBasePostureHealth = clamp(mobBasePostureHealth, 1.0D, 1024.0D);
            sanitized.mobBasePostureRegen = clamp(mobBasePostureRegen, 0.0D, 1024.0D);
            sanitized.maxHealthPostureScale = clamp(maxHealthPostureScale, 0.0D, 10.0D);
            sanitized.maxHealthPostureBonusCap = clamp(maxHealthPostureBonusCap, 0.0D, 100000.0D);
            sanitized.mobMaxHealthPostureScale = clamp(mobMaxHealthPostureScale, 0.0D, 10.0D);
            sanitized.mobMaxHealthPostureBonusCap = clamp(mobMaxHealthPostureBonusCap, 0.0D, 100000.0D);
            sanitized.vulnerablePostureDamageMultiplierPerLevel = clamp(vulnerablePostureDamageMultiplierPerLevel, 0.0D, 10.0D);
            sanitized.baseStaggerDamageMultiplier = clamp(baseStaggerDamageMultiplier, 0.0D, 10.0D);
            sanitized.shieldParryWindowTicks = clamp(shieldParryWindowTicks, 0.0D, 60.0D);
            sanitized.twoHandedParryWindowTicks = clamp(twoHandedParryWindowTicks, 0.0D, 60.0D);
            sanitized.shieldPostureHealthBonus = clamp(shieldPostureHealthBonus, 0.0D, 1024.0D);
            sanitized.twoHandedPostureHealthBonus = clamp(twoHandedPostureHealthBonus, 0.0D, 1024.0D);
            sanitized.playerParryCounterWindowTicks = clamp(playerParryCounterWindowTicks, 0, 200);
            sanitized.playerStaggerTicks = clamp(playerStaggerTicks, 0, 1200);
            sanitized.playerStaggerCooldownTicks = clamp(playerStaggerCooldownTicks, 0, 1200);
            sanitized.playerPostureRegenDelayTicks = clamp(playerPostureRegenDelayTicks, 0, 1200);
            sanitized.playerParryPostureInvulnerabilityTicks = clamp(playerParryPostureInvulnerabilityTicks, 0, 200);
            sanitized.playerParryPostureRestore = clamp(playerParryPostureRestore, 0.0D, 1024.0D);
            sanitized.playerParryPostureDamage = clamp(playerParryPostureDamage, 0.0D, 1024.0D);
            sanitized.playerMaxPostureDamagePerHit = clamp(playerMaxPostureDamagePerHit, 0.0D, 1024.0D);
            sanitized.playerPvpPostureDamageMultiplier = clamp(playerPvpPostureDamageMultiplier, 0.0D, 10.0D);
            sanitized.playerTakenDamagePostureMultiplier = clamp(playerTakenDamagePostureMultiplier, 0.0D, 10.0D);
            sanitized.playerTakenDamagePostureGraceTicks = clamp(playerTakenDamagePostureGraceTicks, 0, 40);
            sanitized.playerBlockedDamagePostureMultiplier = clamp(playerBlockedDamagePostureMultiplier, 0.0D, 10.0D);
            sanitized.playerBlockedSkillPostureMultiplier = clamp(playerBlockedSkillPostureMultiplier, 0.0D, 10.0D);
            sanitized.playerMaxTakenDamagePostureDamage = clamp(playerMaxTakenDamagePostureDamage, 0.0D, 1024.0D);
            sanitized.mobPostureRegenDelayTicks = clamp(mobPostureRegenDelayTicks, 0, 1200);
            sanitized.mobPostureDisplayTicks = clamp(mobPostureDisplayTicks, 0, 6000);
            sanitized.mobStaggerTicks = clamp(mobStaggerTicks, 0, 1200);
            sanitized.mobStaggerCooldownTicks = clamp(mobStaggerCooldownTicks, 0, 1200);
            sanitized.mobParryPostureDamage = clamp(mobParryPostureDamage, 0.0D, 1024.0D);
            sanitized.knifePostureDamageBonus = clamp(knifePostureDamageBonus, 0.0D, 1024.0D);
            sanitized.knifeStaggerDamageMultiplierBonus = clamp(knifeStaggerDamageMultiplierBonus, 0.0D, 10.0D);
            sanitized.rapierPostureDamageBonus = clamp(rapierPostureDamageBonus, 0.0D, 1024.0D);
            sanitized.rapierStaggerDamageMultiplierBonus = clamp(rapierStaggerDamageMultiplierBonus, 0.0D, 10.0D);
            return sanitized;
        }

        private static double clamp(double value, double min, double max) {
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return min;
            }
            return Math.max(min, Math.min(max, value));
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}
