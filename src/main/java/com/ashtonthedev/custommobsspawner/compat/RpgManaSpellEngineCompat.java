package com.ashtonthedev.custommobsspawner.compat;

import com.cleannrooster.rpgmana.api.ManaInterface;
import com.cleannrooster.rpgmana.api.SpellcostMixinInterface;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_power.api.SpellPower;
import net.spell_power.api.enchantment.SpellPowerEnchanting;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class RpgManaSpellEngineCompat {
    private static final double MINIMUM_MANA_COST_FRACTION = 0.5D;
    private static final double MANA_EPSILON = 0.0001D;
    private static final long INSUFFICIENT_MANA_MESSAGE_INTERVAL_TICKS = 10L;
    private static final Map<UUID, Long> LAST_INSUFFICIENT_MANA_MESSAGE_TICKS = new ConcurrentHashMap<>();
    private static final Map<String, Field> RPG_MANA_FIELDS = new ConcurrentHashMap<>();
    private static final Map<String, Field> RPG_MANA_CONFIG_FIELDS = new ConcurrentHashMap<>();
    private static final Map<String, Pattern> REGEX_PATTERNS = new ConcurrentHashMap<>();
    private static final Set<String> INVALID_REGEX_PATTERNS = ConcurrentHashMap.newKeySet();

    private RpgManaSpellEngineCompat() {
    }

    public static boolean canPerformSpell(
            PlayerEntity player,
            Identifier spellId,
            Spell spell,
            List<Entity> targets,
            SpellCast.Action action,
            float progress
    ) {
        if (!(player instanceof ManaInterface manaInterface) || !isRpgManaManagedSpell(player, spellId, spell, targets)) {
            return true;
        }

        float manaCost = calculateManaCost(player, spell, action, progress);
        if (manaCost <= 0.0F) {
            return true;
        }

        return manaInterface.getMana() + MANA_EPSILON >= manaCost * MINIMUM_MANA_COST_FRACTION;
    }

    public static boolean canAttemptSpell(PlayerEntity player, Identifier spellId, Spell spell) {
        if (!(player instanceof ManaInterface manaInterface) || !isRpgManaManagedSpell(spellId, spell)) {
            return true;
        }

        float manaCost = calculateManaCost(player, spell, SpellCast.Action.RELEASE, 1.0F);
        if (manaCost <= 0.0F) {
            manaCost = calculateManaCost(player, spell, SpellCast.Action.CHANNEL, 1.0F);
        }
        if (manaCost <= 0.0F) {
            return true;
        }

        return manaInterface.getMana() + MANA_EPSILON >= manaCost * MINIMUM_MANA_COST_FRACTION;
    }

    public static void sendInsufficientManaMessage(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }

        UUID uuid = serverPlayer.getUuid();
        long time = serverPlayer.getWorld().getTime();
        long lastMessageTime = LAST_INSUFFICIENT_MANA_MESSAGE_TICKS.getOrDefault(uuid, Long.MIN_VALUE);
        if (time - lastMessageTime < INSUFFICIENT_MANA_MESSAGE_INTERVAL_TICKS) {
            return;
        }

        LAST_INSUFFICIENT_MANA_MESSAGE_TICKS.put(uuid, time);
        serverPlayer.sendMessage(Text.translatable("message.cmobs.insufficient_mana"), true);
    }

    private static boolean isRpgManaManagedSpell(
            PlayerEntity player,
            Identifier spellId,
            Spell spell,
            List<Entity> targets
    ) {
        if (!isRpgManaManagedSpell(spellId, spell)) {
            return false;
        }
        return (targets != null && !targets.isEmpty())
                || spell.release.target == null
                || spell.release.target.type == Spell.Release.Target.Type.CURSOR;
    }

    private static boolean isRpgManaManagedSpell(Identifier spellId, Spell spell) {
        if (spellId == null || spell == null || spell.release == null || spell.cost == null || spell.cost.item_id == null) {
            return false;
        }
        if (spell.cost.item_id.contains("arrow")) {
            return false;
        }
        return !matches(spellId.toString(), rpgManaConfigString("blacklist_spell_casting_regex"));
    }

    private static float calculateManaCost(
            PlayerEntity player,
            Spell spell,
            SpellCast.Action action,
            float progress
    ) {
        if (!(spell.cost instanceof SpellcostMixinInterface cost)) {
            return 0.0F;
        }

        float channelMultiplier = channelMultiplier(spell, action, progress);
        SpellHelper.ImpactContext context = new SpellHelper.ImpactContext(
                channelMultiplier,
                1.0F,
                null,
                SpellPower.getSpellPower(spell.school, player),
                SpellHelper.impactTargetingMode(spell)
        );

        float damageCoefficient = 0.0F;
        int projectileCount = 1;
        if (spell.impact != null && cost.calculateManaCost()) {
            for (Spell.Impact impact : spell.impact) {
                if (impact.action != null && impact.action.damage != null) {
                    damageCoefficient += impact.action.damage.spell_power_coefficient;
                }
            }
            if (spell.impact.length > 0) {
                damageCoefficient /= spell.impact.length;
            }
            if (spell.release.target != null
                    && spell.release.target.projectile != null
                    && spell.release.target.projectile.launch_properties != null) {
                projectileCount += spell.release.target.projectile.launch_properties.extra_launch_count;
            }
        }

        EntityAttribute manaCostAttribute = rpgManaAttribute("MANACOST");
        double manaCostPercent = manaCostAttribute == null ? 100.0D : player.getAttributeValue(manaCostAttribute);
        float costMultiplier =
                rpgManaConfigFloat("inspiration") * 0.01F * rpgManaEnchantmentLevel("ARCHMAGE", player)
                        - rpgManaConfigFloat("manastabilized") * 0.01F * rpgManaEnchantmentLevel("MANASTABILIZED", player)
                        + (float) (manaCostPercent * 0.01D);
        float baseCost = cost.calculateManaCost()
                ? Math.max(20.0F, 40.0F * damageCoefficient * projectileCount)
                : cost.getManaCost();

        return costMultiplier * context.total() * baseCost;
    }

    private static float channelMultiplier(Spell spell, SpellCast.Action action, float progress) {
        if (action == SpellCast.Action.CHANNEL) {
            return SpellHelper.channelValueMultiplier(spell);
        }
        if (action == SpellCast.Action.RELEASE) {
            if (SpellHelper.isChanneled(spell)) {
                return 0.0F;
            }
            return progress >= 1.0F ? 1.0F : 0.0F;
        }
        return 1.0F;
    }

    private static float rpgManaConfigFloat(String fieldName) {
        Object config = rpgManaConfig();
        if (config == null) {
            return 0.0F;
        }
        try {
            return rpgManaConfigField(config, fieldName).getFloat(config);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return 0.0F;
        }
    }

    private static String rpgManaConfigString(String fieldName) {
        Object config = rpgManaConfig();
        if (config == null) {
            return "";
        }
        try {
            Object value = rpgManaConfigField(config, fieldName).get(config);
            return value instanceof String string ? string : "";
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return "";
        }
    }

    private static Object rpgManaConfig() {
        try {
            return rpgManaField("config").get(null);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static int rpgManaEnchantmentLevel(String fieldName, PlayerEntity player) {
        try {
            Enchantment enchantment = (Enchantment) rpgManaField(fieldName).get(null);
            return SpellPowerEnchanting.getEnchantmentLevel(enchantment, player, player.getMainHandStack());
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return 0;
        }
    }

    private static EntityAttribute rpgManaAttribute(String fieldName) {
        try {
            return (EntityAttribute) rpgManaField(fieldName).get(null);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Field rpgManaField(String fieldName) throws ReflectiveOperationException {
        Field cached = RPG_MANA_FIELDS.get(fieldName);
        if (cached != null) {
            return cached;
        }
        Field resolved = Class.forName("com.cleannrooster.rpgmana.Rpgmana").getField(fieldName);
        Field existing = RPG_MANA_FIELDS.putIfAbsent(fieldName, resolved);
        return existing == null ? resolved : existing;
    }

    private static Field rpgManaConfigField(Object config, String fieldName) throws ReflectiveOperationException {
        String key = config.getClass().getName() + '#' + fieldName;
        Field cached = RPG_MANA_CONFIG_FIELDS.get(key);
        if (cached != null) {
            return cached;
        }
        Field resolved = config.getClass().getField(fieldName);
        Field existing = RPG_MANA_CONFIG_FIELDS.putIfAbsent(key, resolved);
        return existing == null ? resolved : existing;
    }

    private static boolean matches(String subject, String nullableRegex) {
        if (subject == null || nullableRegex == null || nullableRegex.isEmpty()) {
            return false;
        }
        if (INVALID_REGEX_PATTERNS.contains(nullableRegex)) {
            return false;
        }

        try {
            Pattern pattern = REGEX_PATTERNS.get(nullableRegex);
            if (pattern == null) {
                Pattern compiled = Pattern.compile(nullableRegex, Pattern.CASE_INSENSITIVE);
                Pattern existing = REGEX_PATTERNS.putIfAbsent(nullableRegex, compiled);
                pattern = existing == null ? compiled : existing;
            }
            return pattern.matcher(subject).find();
        } catch (PatternSyntaxException exception) {
            INVALID_REGEX_PATTERNS.add(nullableRegex);
            return false;
        }
    }
}
