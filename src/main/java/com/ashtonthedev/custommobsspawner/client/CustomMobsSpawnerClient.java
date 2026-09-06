package com.ashtonthedev.custommobsspawner.client;

import com.ashtonthedev.custommobsspawner.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.render.entity.ZombieEntityRenderer;

public class CustomMobsSpawnerClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.CUSTOM_ZOMBIE, ZombieEntityRenderer::new);
        if (FabricLoader.getInstance().isModLoaded("bettercombat")) {
            BetterCombatClientCompat.register();
        }
        PostureHudOverlay.register();
        ComboHudOverlay.register();
        MobPostureBarRenderer.register();
        AttackCooldownSyncHandler.register();
    }
}
