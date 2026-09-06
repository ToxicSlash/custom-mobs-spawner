package com.ashtonthedev.custommobsspawner.client;

import com.ashtonthedev.custommobsspawner.combat.PlayerParryHandler;
import com.ashtonthedev.custommobsspawner.mixin.client.MinecraftClientAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

public final class AttackCooldownSyncHandler {
    private static final int RESYNC_TICKS = 4;
    private static int pendingTicks;
    private static int pendingCooldownTicks;
    private static float pendingProgress;

    private AttackCooldownSyncHandler() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(PlayerParryHandler.ATTACK_COOLDOWN_SYNC_PACKET, (client, handler, buf, responseSender) -> {
            float progress = buf.readFloat();
            int cooldownTicks = buf.readableBytes() > 0 ? buf.readVarInt() : -1;
            client.execute(() -> {
                pendingProgress = progress;
                pendingCooldownTicks = cooldownTicks;
                pendingTicks = RESYNC_TICKS;
                apply(client);
            });
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (pendingTicks <= 0) {
                return;
            }

            apply(client);
            if (pendingCooldownTicks > 0) {
                pendingCooldownTicks--;
            }
            pendingTicks--;
        });
    }

    private static void apply(MinecraftClient client) {
        if (client.player != null) {
            if (pendingCooldownTicks >= 0) {
                PlayerParryHandler.applySyncedAttackCooldownTicks(client.player, pendingCooldownTicks);
            } else {
                PlayerParryHandler.applySyncedAttackCooldownProgress(client.player, pendingProgress);
            }
        }
        ((MinecraftClientAccessor) client).customMobsSpawner$setAttackCooldown(0);
    }
}
