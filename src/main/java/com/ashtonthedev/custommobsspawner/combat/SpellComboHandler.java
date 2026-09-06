package com.ashtonthedev.custommobsspawner.combat;

import com.ashtonthedev.custommobsspawner.skill.CustomSkillRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.spell_engine.api.spell.SpellInfo;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SpellComboHandler {
    private static final int AREA_HIT_CACHE_TICKS = 5;
    private static final int SPELL_COMBO_INTERVAL_TICKS = 20;
    private static final int SPELL_COMBO_GAIN = 1;
    private static final int CACHE_CLEANUP_INTERVAL_TICKS = 100;
    private static final Map<AreaHitKey, Long> RECENT_AREA_HITS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_COMBO_GAIN_TICKS = new ConcurrentHashMap<>();
    private static final ThreadLocal<Integer> SPELL_IMPACT_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Integer> COMBO_GAIN_SUPPRESSION_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static long lastCleanupTick = Long.MIN_VALUE;

    private SpellComboHandler() {
    }

    public static void beginSpellImpact() {
        SPELL_IMPACT_DEPTH.set(SPELL_IMPACT_DEPTH.get() + 1);
    }

    public static void endSpellImpact() {
        int depth = SPELL_IMPACT_DEPTH.get() - 1;
        if (depth <= 0) {
            SPELL_IMPACT_DEPTH.remove();
        } else {
            SPELL_IMPACT_DEPTH.set(depth);
        }
    }

    public static boolean isSpellImpactActive() {
        return SPELL_IMPACT_DEPTH.get() > 0;
    }

    public static void beginComboGainSuppression() {
        COMBO_GAIN_SUPPRESSION_DEPTH.set(COMBO_GAIN_SUPPRESSION_DEPTH.get() + 1);
    }

    public static void endComboGainSuppression() {
        int depth = COMBO_GAIN_SUPPRESSION_DEPTH.get() - 1;
        if (depth <= 0) {
            COMBO_GAIN_SUPPRESSION_DEPTH.remove();
        } else {
            COMBO_GAIN_SUPPRESSION_DEPTH.set(depth);
        }
    }

    public static void recordImpact(
            World world,
            LivingEntity caster,
            Entity target,
            Entity source,
            SpellInfo spellInfo,
            boolean areaImpact,
            boolean successful
    ) {
        if (!successful || world.isClient() || !(caster instanceof ServerPlayerEntity player) || !(target instanceof LivingEntity livingTarget)) {
            return;
        }
        if (livingTarget == caster) {
            return;
        }

        long time = world.getTime();
        cleanupIfNeeded(time);
        if (!markSpellHit(player, livingTarget, source, spellInfo, time)) {
            return;
        }

        CustomSkillRegistry.markSuccessfulPlayerAttack(player);
        if (!isComboGainSuppressed() && comboIntervalReady(player, time)) {
            ComboHandler.increment(player, SPELL_COMBO_GAIN);
        }
        CustomSkillRegistry.runSpellHit(player, livingTarget, spellInfo);
    }

    private static boolean isComboGainSuppressed() {
        return COMBO_GAIN_SUPPRESSION_DEPTH.get() > 0;
    }

    private static boolean comboIntervalReady(ServerPlayerEntity player, long time) {
        long previous = LAST_COMBO_GAIN_TICKS.getOrDefault(player.getUuid(), Long.MIN_VALUE);
        if (previous != Long.MIN_VALUE && time - previous < SPELL_COMBO_INTERVAL_TICKS) {
            return false;
        }
        LAST_COMBO_GAIN_TICKS.put(player.getUuid(), time);
        return true;
    }

    private static boolean markSpellHit(ServerPlayerEntity player, LivingEntity target, Entity source, SpellInfo spellInfo, long time) {
        AreaHitKey key = new AreaHitKey(
                player.getUuid(),
                target.getUuid(),
                source == null ? null : source.getUuid(),
                spellInfo == null ? null : spellInfo.id()
        );
        Long previous = RECENT_AREA_HITS.get(key);
        if (previous != null && time - previous <= AREA_HIT_CACHE_TICKS) {
            return false;
        }
        RECENT_AREA_HITS.put(key, time);
        return true;
    }

    private static void cleanupIfNeeded(long time) {
        if (lastCleanupTick != Long.MIN_VALUE && time - lastCleanupTick < CACHE_CLEANUP_INTERVAL_TICKS) {
            return;
        }
        lastCleanupTick = time;
        RECENT_AREA_HITS.entrySet().removeIf(entry -> time - entry.getValue() > CACHE_CLEANUP_INTERVAL_TICKS);
        LAST_COMBO_GAIN_TICKS.entrySet().removeIf(entry -> time - entry.getValue() > CACHE_CLEANUP_INTERVAL_TICKS);
    }

    private record AreaHitKey(UUID casterId, UUID targetId, UUID sourceId, Identifier spellId) {
    }
}
