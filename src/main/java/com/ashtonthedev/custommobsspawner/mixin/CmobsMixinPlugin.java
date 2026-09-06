package com.ashtonthedev.custommobsspawner.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class CmobsMixinPlugin implements IMixinConfigPlugin {
    private static final String BETTER_TRIMS_DEV_BRIDGE =
            "com.ashtonthedev.custommobsspawner.mixin.BetterTrimsDevBridgeMixin";
    private static final String BETTER_COMBAT_TARGET_FINDER =
            "com.ashtonthedev.custommobsspawner.mixin.client.BetterCombatTargetFinderMixin";
    private static final String RPG_MANA_IN_GAME_HUD =
            "com.ashtonthedev.custommobsspawner.mixin.client.RpgManaInGameHudMixin";
    private static final String RPG_MANA_DEFAULTS =
            "com.ashtonthedev.custommobsspawner.mixin.RpgManaDefaultsMixin";
    private static final String RPG_MANA_PERSISTENCE =
            "com.ashtonthedev.custommobsspawner.mixin.RpgManaPersistenceMixin";
    private static final String RPG_MANA_COOLDOWN_REGEN =
            "com.ashtonthedev.custommobsspawner.mixin.RpgManaCooldownRegenMixin";
    private static final String RPG_MANA_DEATH_RESET =
            "com.ashtonthedev.custommobsspawner.mixin.RpgManaDeathResetMixin";
    private static final String RPG_MANA_OVERCHARGE_CLAMP =
            "com.ashtonthedev.custommobsspawner.mixin.RpgManaOverchargeClampMixin";
    private static final String RPG_MANA_SPELL_ENGINE_THRESHOLD =
            "com.ashtonthedev.custommobsspawner.mixin.RpgManaSpellEngineThresholdMixin";
    private static final String RPG_MANA_SPELL_ENGINE_ATTEMPT_THRESHOLD =
            "com.ashtonthedev.custommobsspawner.mixin.RpgManaSpellEngineAttemptThresholdMixin";
    private static final String SPELL_ENGINE_DAMAGE_PENETRATION =
            "com.ashtonthedev.custommobsspawner.mixin.SpellEngineDamagePenetrationMixin";
    private static final String AMETHYST_CORE_DAMAGE_PENETRATION =
            "com.ashtonthedev.custommobsspawner.mixin.AmethystCoreDamagePenetrationMixin";
    private static final String AMETHYST_CORE_MISSILE_DAMAGE_PENETRATION =
            "com.ashtonthedev.custommobsspawner.mixin.AmethystCoreMissileDamagePenetrationMixin";
    private static final String AMETHYST_CORE_SCEPTER_RPG_MANA =
            "com.ashtonthedev.custommobsspawner.mixin.AmethystCoreAugmentScepterRpgManaMixin";
    private static final String AMETHYST_IMBUEMENT_MANA_POTION_RPG_MANA =
            "com.ashtonthedev.custommobsspawner.mixin.AmethystImbuementManaPotionRpgManaMixin";
    private static final String AMETHYST_MANA_REGENERATION_OVERCHARGE =
            "com.ashtonthedev.custommobsspawner.mixin.AmethystManaRegenerationOverchargeMixin";
    private static final String AMETHYST_IMBUEMENT_SPELL_SCROLL_RPG_MANA =
            "com.ashtonthedev.custommobsspawner.mixin.AmethystImbuementSpellScrollRpgManaMixin";
    private static final String AMETHYST_IMBUEMENT_STATUS_RPG_MANA =
            "com.ashtonthedev.custommobsspawner.mixin.AmethystImbuementStatusRpgManaMixin";
    private static final String ARMOR_DAMAGE_SCALE_ZENITH_ARMOR =
            "com.ashtonthedev.custommobsspawner.mixin.ArmorDamageScaleZenithArmorMixin";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (BETTER_TRIMS_DEV_BRIDGE.equals(mixinClassName)) {
            FabricLoader loader = FabricLoader.getInstance();
            return loader.isDevelopmentEnvironment() && loader.isModLoaded("bettertrims");
        }
        if (BETTER_COMBAT_TARGET_FINDER.equals(mixinClassName)) {
            return FabricLoader.getInstance().isModLoaded("bettercombat");
        }
        if (RPG_MANA_IN_GAME_HUD.equals(mixinClassName)) {
            return FabricLoader.getInstance().isModLoaded("rpgmana");
        }
        if (RPG_MANA_DEFAULTS.equals(mixinClassName)) {
            return FabricLoader.getInstance().isModLoaded("rpgmana");
        }
        if (RPG_MANA_PERSISTENCE.equals(mixinClassName)) {
            return FabricLoader.getInstance().isModLoaded("rpgmana");
        }
        if (RPG_MANA_COOLDOWN_REGEN.equals(mixinClassName)) {
            return FabricLoader.getInstance().isModLoaded("rpgmana");
        }
        if (RPG_MANA_DEATH_RESET.equals(mixinClassName)) {
            return FabricLoader.getInstance().isModLoaded("rpgmana");
        }
        if (RPG_MANA_OVERCHARGE_CLAMP.equals(mixinClassName)) {
            return FabricLoader.getInstance().isModLoaded("rpgmana");
        }
        if (RPG_MANA_SPELL_ENGINE_THRESHOLD.equals(mixinClassName)
                || RPG_MANA_SPELL_ENGINE_ATTEMPT_THRESHOLD.equals(mixinClassName)) {
            FabricLoader loader = FabricLoader.getInstance();
            return loader.isModLoaded("rpgmana") && loader.isModLoaded("spell_engine");
        }
        if (SPELL_ENGINE_DAMAGE_PENETRATION.equals(mixinClassName)) {
            return FabricLoader.getInstance().isModLoaded("spell_engine");
        }
        if (AMETHYST_CORE_DAMAGE_PENETRATION.equals(mixinClassName)
                || AMETHYST_CORE_MISSILE_DAMAGE_PENETRATION.equals(mixinClassName)) {
            return FabricLoader.getInstance().isModLoaded("amethyst_core");
        }
        if (AMETHYST_CORE_SCEPTER_RPG_MANA.equals(mixinClassName)) {
            FabricLoader loader = FabricLoader.getInstance();
            return loader.isModLoaded("rpgmana") && loader.isModLoaded("amethyst_core");
        }
        if (AMETHYST_IMBUEMENT_MANA_POTION_RPG_MANA.equals(mixinClassName)
                || AMETHYST_MANA_REGENERATION_OVERCHARGE.equals(mixinClassName)
                || AMETHYST_IMBUEMENT_SPELL_SCROLL_RPG_MANA.equals(mixinClassName)
                || AMETHYST_IMBUEMENT_STATUS_RPG_MANA.equals(mixinClassName)) {
            FabricLoader loader = FabricLoader.getInstance();
            return loader.isModLoaded("rpgmana") && loader.isModLoaded("amethyst_imbuement");
        }
        if (ARMOR_DAMAGE_SCALE_ZENITH_ARMOR.equals(mixinClassName)) {
            FabricLoader loader = FabricLoader.getInstance();
            return loader.isModLoaded("armordamagescale") && loader.isModLoaded("zenith_attributes");
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
