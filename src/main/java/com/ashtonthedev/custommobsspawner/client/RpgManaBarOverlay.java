package com.ashtonthedev.custommobsspawner.client;

import com.ashtonthedev.custommobsspawner.compat.RpgManaOverchargeCompat;
import com.cleannrooster.rpgmana.Rpgmana;
import com.cleannrooster.rpgmana.api.ManaInterface;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.spell_engine.internals.casting.SpellCasterClient;

public final class RpgManaBarOverlay {
    private static final Identifier MANA_BAR = new Identifier("rpgmana", "textures/gui/bar_mana.png");
    private static final Identifier NEGATIVE_MANA_BAR = new Identifier("rpgmana", "textures/gui/bar_mana_neg.png");
    private static final int RECENT_MANA_ACTIVITY_TICKS = 200;
    private static final int FOOD_BAR_X_OFFSET = 101;
    private static final int FOOD_BAR_Y_OFFSET = -7;
    private static final float FOOD_BAR_MANA_SCALE_X = 81.0F / 182.0F;
    private static final int SOURCE_BAR_WIDTH = 182;
    private static final int BAR_HEIGHT = 5;

    private RpgManaBarOverlay() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register(RpgManaBarOverlay::render);
    }

    private static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || !(client.player instanceof ManaInterface manaInterface)) {
            return;
        }
        if (Rpgmana.clientConfig != null && Rpgmana.clientConfig.alt) {
            return;
        }

        double maxMana = manaInterface.getMaxMana();
        if (maxMana <= 0.0D || !shouldShow(client, manaInterface, maxMana)) {
            return;
        }

        double currentMana = manaInterface.getMana();
        double ratio = currentMana / maxMana;
        int sourceWidth = Math.min(SOURCE_BAR_WIDTH, (int) Math.floor(Math.abs(ratio) * SOURCE_BAR_WIDTH));
        if (sourceWidth <= 0) {
            return;
        }

        int originalX = context.getScaledWindowWidth() / 2 - SOURCE_BAR_WIDTH / 2;
        int originalY = context.getScaledWindowHeight() - 29;
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(originalX + FOOD_BAR_X_OFFSET, originalY + FOOD_BAR_Y_OFFSET, 150.0D);
        matrices.scale(FOOD_BAR_MANA_SCALE_X, 1.0F, 1.0F);
        context.drawTexture(
                currentMana >= 0.0D ? MANA_BAR : NEGATIVE_MANA_BAR,
                0,
                0,
                0.0F,
                0.0F,
                sourceWidth,
                BAR_HEIGHT,
                SOURCE_BAR_WIDTH,
                BAR_HEIGHT
        );
        matrices.pop();
    }

    private static boolean shouldShow(MinecraftClient client, ManaInterface manaInterface, double maxMana) {
        if (manaInterface.getTimeFull() < RECENT_MANA_ACTIVITY_TICKS) {
            return true;
        }
        if (manaInterface.getMana() < maxMana - 0.0001D) {
            return true;
        }
        if (RpgManaOverchargeCompat.isOvercharged(client.player, manaInterface)) {
            return true;
        }
        return client.player instanceof SpellCasterClient caster && caster.isCastingSpell();
    }
}
