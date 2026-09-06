package com.ashtonthedev.custommobsspawner.client;

import com.ashtonthedev.custommobsspawner.combat.MobPostureHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.AbstractTeam;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

public final class MobPostureBarRenderer {
    private static final int BAR_WIDTH = 116;
    private static final int BAR_HEIGHT = 3;
    private static final int MOB_BAR_Y = 34;
    private static final int PLAYER_BAR_Y = -12;
    private static final int MOB_MAX_RENDER_DISTANCE_SQUARED = 16 * 16;
    private static final int PLAYER_MAX_RENDER_DISTANCE_SQUARED = 64 * 64;
    private static final Map<Integer, PostureState> POSTURE_STATES = new HashMap<>();

    private MobPostureBarRenderer() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(MobPostureHandler.MOB_POSTURE_SYNC_PACKET, (client, handler, buf, responseSender) -> {
            int entityId = buf.readVarInt();
            float postureFraction = buf.readUnsignedByte() / 100.0F;
            boolean showPosture = buf.readBoolean();
            boolean staggered = buf.readBoolean();
            float staggerDamageFraction = buf.readUnsignedByte() / 100.0F;
            boolean vulnerable = buf.readBoolean();
            client.execute(() -> {
                if (!showPosture) {
                    POSTURE_STATES.remove(entityId);
                    return;
                }

                long expireTime = client.world == null ? 0L : client.world.getTime() + 80L;
                POSTURE_STATES.put(entityId, new PostureState(
                        clamp(postureFraction),
                        staggered,
                        clamp(staggerDamageFraction),
                        vulnerable,
                        expireTime
                ));
            });
        });
    }

    public static void render(LivingEntity entity, MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return;
        }

        if (!canRenderPostureBar(entity, client.player) || entity.isInvisible() || client.player.squaredDistanceTo(entity) > maxRenderDistanceSquared(entity)) {
            return;
        }

        PostureState state = POSTURE_STATES.get(entity.getId());
        if (state == null) {
            return;
        }
        if (client.world.getTime() > state.expireTime()) {
            POSTURE_STATES.remove(entity.getId());
            return;
        }
        matrices.push();
        matrices.translate(0.0D, entity.getHeight() + 1.2D, 0.0D);
        matrices.multiply(client.getEntityRenderDispatcher().getRotation());
        matrices.scale(-0.025F, -0.025F, 0.025F);

        int left = -BAR_WIDTH / 2;
        int top = entity instanceof PlayerEntity ? PLAYER_BAR_Y : MOB_BAR_Y;
        int filledWidth = state.fraction() <= 0.0F || state.staggered() ? 0 : Math.max(1, Math.round(BAR_WIDTH * state.fraction()));
        int staggerWidth = state.staggered() && state.staggerDamageFraction() > 0.0F
                ? Math.max(1, Math.round(BAR_WIDTH * state.staggerDamageFraction()))
                : 0;
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getTextBackgroundSeeThrough());
        drawRect(buffer, matrix, left - 1, top - 1, BAR_WIDTH + 2, 1, 0.0F, 10, 14, 28, 220);
        drawRect(buffer, matrix, left - 1, top + BAR_HEIGHT, BAR_WIDTH + 2, 1, 0.0F, 10, 14, 28, 220);
        drawRect(buffer, matrix, left - 1, top, 1, BAR_HEIGHT, 0.0F, 10, 14, 28, 220);
        drawRect(buffer, matrix, left + BAR_WIDTH, top, 1, BAR_HEIGHT, 0.0F, 10, 14, 28, 220);
        if (filledWidth > 0) {
            if (state.vulnerable()) {
                drawRect(buffer, matrix, left, top, filledWidth, BAR_HEIGHT, 0.0F, 255, 150, 35, 245);
            } else {
                drawRect(buffer, matrix, left, top, filledWidth, BAR_HEIGHT, 0.0F, 40, 130, 255, 235);
            }
        }
        if (staggerWidth > 0) {
            drawRect(buffer, matrix, left + BAR_WIDTH - staggerWidth, top, staggerWidth, BAR_HEIGHT, 0.0F, 230, 35, 35, 235);
        }
        matrices.pop();
    }

    private static boolean canRenderPostureBar(LivingEntity entity, PlayerEntity viewer) {
        if (entity instanceof PlayerEntity player) {
            return player == viewer || isNameTagVisibleFor(player, viewer);
        }
        return entity instanceof HostileEntity;
    }

    private static int maxRenderDistanceSquared(LivingEntity entity) {
        return entity instanceof PlayerEntity ? PLAYER_MAX_RENDER_DISTANCE_SQUARED : MOB_MAX_RENDER_DISTANCE_SQUARED;
    }

    private static boolean isNameTagVisibleFor(PlayerEntity target, PlayerEntity viewer) {
        AbstractTeam team = target.getScoreboardTeam();
        if (team == null) {
            return true;
        }

        AbstractTeam viewerTeam = viewer.getScoreboardTeam();
        return switch (team.getNameTagVisibilityRule()) {
            case ALWAYS -> true;
            case NEVER -> false;
            case HIDE_FOR_OTHER_TEAMS -> viewerTeam != null && team.isEqual(viewerTeam);
            case HIDE_FOR_OWN_TEAM -> viewerTeam == null || !team.isEqual(viewerTeam);
        };
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static void drawRect(VertexConsumer buffer, Matrix4f matrix, int x, int y, int width, int height, float z, int red, int green, int blue, int alpha) {
        int right = x + width;
        int bottom = y + height;
        buffer.vertex(matrix, x, bottom, z).color(red, green, blue, alpha).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).next();
        buffer.vertex(matrix, right, bottom, z).color(red, green, blue, alpha).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).next();
        buffer.vertex(matrix, right, y, z).color(red, green, blue, alpha).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).next();
        buffer.vertex(matrix, x, y, z).color(red, green, blue, alpha).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).next();
    }

    private record PostureState(float fraction, boolean staggered, float staggerDamageFraction, boolean vulnerable, long expireTime) {
    }
}
