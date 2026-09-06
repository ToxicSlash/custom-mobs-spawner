package com.ashtonthedev.custommobsspawner.client;

import com.ashtonthedev.custommobsspawner.combat.PlayerParryHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

public final class PostureHudOverlay {
    private static final Identifier ICONS = new Identifier("textures/gui/icons.png");
    private static final int BAR_WIDTH = 182;
    private static final int BAR_HEIGHT = 5;
    private static float postureFraction = 1.0F;
    private static boolean showPosture;

    private PostureHudOverlay() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(PlayerParryHandler.POSTURE_SYNC_PACKET, (client, handler, buf, responseSender) -> {
            float syncedPostureFraction = buf.readUnsignedByte() / 100.0F;
            boolean syncedShowPosture = buf.readBoolean();
            client.execute(() -> {
                postureFraction = Math.max(0.0F, Math.min(1.0F, syncedPostureFraction));
                showPosture = syncedShowPosture;
            });
        });
        HudRenderCallback.EVENT.register(PostureHudOverlay::render);
    }

    private static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || !showPosture) {
            return;
        }

        int x = context.getScaledWindowWidth() / 2 - 91;
        int y = context.getScaledWindowHeight() - 29;
        int filledWidth = Math.round(BAR_WIDTH * postureFraction);
        context.drawTexture(ICONS, x, y, 0, 84, BAR_WIDTH, BAR_HEIGHT);
        if (filledWidth > 0) {
            context.drawTexture(ICONS, x, y, 0, 89, filledWidth, BAR_HEIGHT);
        }
    }
}
