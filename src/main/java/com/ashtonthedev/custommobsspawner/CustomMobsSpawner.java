package com.ashtonthedev.custommobsspawner;

import com.ashtonthedev.custommobsspawner.data.CustomMobData;
import com.ashtonthedev.custommobsspawner.command.CustomMobsCommand;
import com.ashtonthedev.custommobsspawner.command.CustomSummonCommand;
import com.ashtonthedev.custommobsspawner.combat.ModAttributes;
import com.ashtonthedev.custommobsspawner.combat.ComboHandler;
import com.ashtonthedev.custommobsspawner.combat.MobPostureHandler;
import com.ashtonthedev.custommobsspawner.combat.PlayerParryHandler;
import com.ashtonthedev.custommobsspawner.compat.BotaniaManaPoolRpgManaCompat;
import com.ashtonthedev.custommobsspawner.config.CustomMobsSpawnerConfig;
import com.ashtonthedev.custommobsspawner.config.DefaultConfigInstaller;
import com.ashtonthedev.custommobsspawner.effect.ModStatusEffects;
import com.ashtonthedev.custommobsspawner.entity.ModEntities;
import com.ashtonthedev.custommobsspawner.item.ModItems;
import com.ashtonthedev.custommobsspawner.spawn.ExtraSpawnTicker;
import com.ashtonthedev.custommobsspawner.skill.CustomSkillRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameRules;

public class CustomMobsSpawner implements ModInitializer {
    public static final String MOD_ID = "cmobs";
    public static final String CHECKED_TAG = "checked";
    public static final String SPAWNER_SPAWNED_TAG = "cmobs_spawner_spawned";
    public static final GameRules.Key<GameRules.BooleanRule> DISABLE_SPAWN_STANDS = GameRuleRegistry.register(
            "cmobsDisableSpawnStands",
            GameRules.Category.SPAWNING,
            GameRuleFactory.createBooleanRule(false)
    );

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        CustomMobsSpawnerConfig.load();
        DefaultConfigInstaller.install();
        ModAttributes.register();
        ModStatusEffects.register();
        ModItems.register();
        ModEntities.register();
        CustomSkillRegistry.registerCustomSpellHandlers();
        FabricDefaultAttributeRegistry.register(ModEntities.CUSTOM_ZOMBIE, ModEntities.createCustomZombieAttributes());

        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new CustomMobData());
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            CustomSummonCommand.register(dispatcher);
            CustomMobsCommand.register(dispatcher);
        });
        ServerPlayNetworking.registerGlobalReceiver(CustomSkillRegistry.FAILED_ATTACK_PACKET, (server, player, handler, buf, responseSender) ->
                server.execute(() -> CustomSkillRegistry.queueFailedPlayerAttack(player))
        );
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> CustomMobData.tryReplace(entity, world));
        ServerTickEvents.START_SERVER_TICK.register(ModStatusEffects::tickServer);
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            ExtraSpawnTicker.tick(world);
            CustomMobData.tickSpawnStands(world);
            CustomSkillRegistry.tickTimed(world);
        });
        if (FabricLoader.getInstance().isModLoaded("botania") && FabricLoader.getInstance().isModLoaded("rpgmana")) {
            ServerTickEvents.END_WORLD_TICK.register(BotaniaManaPoolRpgManaCompat::tickWorld);
        }
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ComboHandler.tick(server);
            CustomSkillRegistry.tickCooldowns(server);
            PlayerParryHandler.tickCooldowns(server);
            MobPostureHandler.tick(server);
        });
    }
}
