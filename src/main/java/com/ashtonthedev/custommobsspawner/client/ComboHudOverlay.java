package com.ashtonthedev.custommobsspawner.client;

import com.ashtonthedev.custommobsspawner.combat.ComboHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Util;

public final class ComboHudOverlay {
    private static final int WHITE = 0xFFFFFF;
    private static final int YELLOW = 0xFFFF55;
    private static final int GOLD = 0xFFAA00;
    private static final float TEXT_SCALE = 0.5F;
    private static final long COMBAT_VISIBILITY_MS = 10_000L;
    private static final int HEALTH_BAR_LEFT_OFFSET = -91;
    private static final int HEALTH_BAR_TOP_OFFSET = -39;
    private static final int HEALTH_BAR_GAP = 2;
    private static int combo;
    private static long lastCombatTimeMs = Long.MIN_VALUE;

    private ComboHudOverlay() {
    }

    private static void reset() {
        combo = 0;
        lastCombatTimeMs = Long.MIN_VALUE;
        ComboHandler.clearClientCombos();
    }

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        ClientPlayNetworking.registerGlobalReceiver(ComboHandler.COMBO_SYNC_PACKET, (client, handler, buf, responseSender) -> {
            int syncedCombo = buf.readVarInt();
            boolean markCombat = buf.readableBytes() > 0 && buf.readBoolean();
            client.execute(() -> {
                if (client.getNetworkHandler() != handler || client.player == null) return;
                combo = Math.max(0, syncedCombo);
                if (client.player != null) {
                    ComboHandler.setClientCombo(client.player.getUuid(), combo);
                }
                if (combo > 0 || markCombat) {
                    lastCombatTimeMs = Util.getMeasuringTimeMs();
                }
            });
        });
        HudRenderCallback.EVENT.register(ComboHudOverlay::render);
    }

    private static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || !client.player.isAlive() || client.player.isSpectator() || client.options.hudHidden) {
            return;
        }
        if (lastCombatTimeMs == Long.MIN_VALUE || Util.getMeasuringTimeMs() - lastCombatTimeMs > COMBAT_VISIBILITY_MS) {
            return;
        }

        String text = "Combo (" + combo + "x)";
        TextRenderer textRenderer = client.textRenderer;
        int textWidth = Math.round(textRenderer.getWidth(text) * TEXT_SCALE);
        int healthBarLeft = context.getScaledWindowWidth() / 2 + HEALTH_BAR_LEFT_OFFSET;
        int x = Math.max(2, healthBarLeft - HEALTH_BAR_GAP - textWidth);
        int y = context.getScaledWindowHeight() + HEALTH_BAR_TOP_OFFSET;
        context.getMatrices().push();
        context.getMatrices().scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
        context.drawTextWithShadow(textRenderer, text, Math.round(x / TEXT_SCALE), Math.round(y / TEXT_SCALE), colorFor(combo));
        context.getMatrices().pop();
    }

    private static int colorFor(int combo) {
        if (combo >= 10) {
            return GOLD;
        }
        if (combo > 5) {
            return YELLOW;
        }
        return WHITE;
    }
}
