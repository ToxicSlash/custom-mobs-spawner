package com.ashtonthedev.custommobsspawner.client;

import com.ashtonthedev.custommobsspawner.skill.CustomSkillRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

public final class FailedAttackClientNotifier {
    private static long lastSentTick = Long.MIN_VALUE;

    private FailedAttackClientNotifier() {
    }

    public static void notifyIfCrosshairMiss(ClientPlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!canSend(player, client) || isCrosshairOnLivingTarget(client, player)) {
            return;
        }

        notifyFailedAttack(player);
    }

    public static void notifyFailedAttack(ClientPlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!canSend(player, client)) {
            return;
        }

        long tick = client.world == null ? Long.MIN_VALUE : client.world.getTime();
        if (tick == lastSentTick) {
            return;
        }
        lastSentTick = tick;

        ClientPlayNetworking.send(CustomSkillRegistry.FAILED_ATTACK_PACKET, PacketByteBufs.empty());
    }

    private static boolean canSend(ClientPlayerEntity player, MinecraftClient client) {
        return player != null
                && client.player == player
                && client.getNetworkHandler() != null;
    }

    private static boolean isCrosshairOnLivingTarget(MinecraftClient client, ClientPlayerEntity player) {
        HitResult hitResult = client.crosshairTarget;
        if (!(hitResult instanceof EntityHitResult entityHit)) {
            return false;
        }

        Entity entity = entityHit.getEntity();
        return entity instanceof LivingEntity && entity != player && entity.isAlive();
    }
}
