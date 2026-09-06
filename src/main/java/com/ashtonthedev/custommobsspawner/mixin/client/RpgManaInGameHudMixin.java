package com.ashtonthedev.custommobsspawner.mixin.client;

import com.ashtonthedev.custommobsspawner.compat.RpgManaOverchargeCompat;
import com.cleannrooster.rpgmana.api.ManaInterface;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.spell_engine.internals.casting.SpellCasterClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

@Pseudo
@Mixin(targets = "com.cleannrooster.rpgmana.client.InGameHud", remap = false)
public abstract class RpgManaInGameHudMixin {
    private static final int RECENT_MANA_ACTIVITY_TICKS = 200;
    private static final long RECENT_CASTING_ACTIVITY_MS = 10_000L;
    private static final int FOOD_BAR_X_OFFSET = 101;
    private static final int FOOD_BAR_Y_OFFSET = -7;
    private static final float FOOD_BAR_MANA_SCALE_X = 81.0F / 182.0F;
    private static final int FOOD_BAR_MANA_WIDTH = 81;
    private static final int FOOD_BAR_MANA_HEIGHT = 5;
    private static final float MANA_COUNTER_MAX_SCALE = 0.5F;
    private static final int MANA_COUNTER_Y_OFFSET = -9;
    private static final double MANA_REGEN_APPLICATIONS_PER_SECOND = 20.0D;
    private static final double BOTANIA_POOL_PLAYER_MANA_PER_EXCHANGE = 2.0D;
    private static final double BOTANIA_POOL_MANA_PER_PLAYER_MANA = 5.0D;
    private static final double BOTANIA_POOL_EXCHANGES_PER_SECOND = 2.0D;
    private static final double BOTANIA_INSIDE_POOL_MAX_Y_OFFSET = 0.5D;
    private static final int NORMAL_MANA_COUNTER_COLOR = 0xFFFFFFFF;
    private static final int OVERCHARGED_MANA_COUNTER_COLOR = 0xFF55FFFF;
    private static long customMobsSpawner$lastCastingActivityTimeMs;

    @ModifyConstant(method = "onHudRender", constant = @Constant(intValue = 60), require = 0)
    private int customMobsSpawner$showAfterRecentManaChange(int original) {
        return RECENT_MANA_ACTIVITY_TICKS;
    }

    @Redirect(
            method = "onHudRender",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cleannrooster/rpgmana/api/ManaInterface;getTimeFull()I",
                    ordinal = 0
            ),
            require = 0
    )
    private int customMobsSpawner$showAfterRecentCasting(ManaInterface manaInterface) {
        if (customMobsSpawner$isCastingNowOrRecently()) {
            return 0;
        }
        return manaInterface.getTimeFull();
    }

    @Redirect(
            method = "onHudRender",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/spell_engine/internals/casting/SpellCasterClient;isCastingSpell()Z"
            ),
            require = 0
    )
    private boolean customMobsSpawner$showManaBarWhileCasting(SpellCasterClient client) {
        if (client.isCastingSpell()) {
            customMobsSpawner$lastCastingActivityTimeMs = System.currentTimeMillis();
        }
        return false;
    }

    @Redirect(
            method = "onHudRender",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIFFIIII)V"
            ),
            require = 0
    )
    private void customMobsSpawner$drawManaBarInFoodSlot(
            DrawContext context,
            Identifier texture,
            int x,
            int y,
            float u,
            float v,
            int width,
            int height,
            int textureWidth,
            int textureHeight
    ) {
        if (height != 5 || textureWidth != 182 || textureHeight != 5) {
            context.drawTexture(texture, x, y, u, v, width, height, textureWidth, textureHeight);
            return;
        }

        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x + FOOD_BAR_X_OFFSET, y + FOOD_BAR_Y_OFFSET, 0.0D);
        matrices.scale(FOOD_BAR_MANA_SCALE_X, 1.0F, 1.0F);
        context.drawTexture(texture, 0, 0, u, v, Math.min(width, textureWidth), height, textureWidth, textureHeight);
        matrices.pop();
    }

    @Inject(method = "onHudRender", at = @At("TAIL"), require = 0)
    private void customMobsSpawner$drawManaCounter(DrawContext context, float tickDelta, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.player instanceof ManaInterface manaInterface) || client.textRenderer == null) {
            return;
        }

        if (!customMobsSpawner$shouldShowManaCounter(manaInterface)) {
            return;
        }

        boolean overcharged = RpgManaOverchargeCompat.isOvercharged(client.player, manaInterface);
        String text = customMobsSpawner$formatManaValue(client, manaInterface)
                + " (+" + customMobsSpawner$formatManaRegen(
                customMobsSpawner$getDisplayedManaRegenPerSecond(client, manaInterface)
        ) + "/s)";
        TextRenderer textRenderer = client.textRenderer;
        int textWidth = textRenderer.getWidth(text);
        if (textWidth <= 0) {
            return;
        }

        int barX = context.getScaledWindowWidth() / 2 - 91 + FOOD_BAR_X_OFFSET;
        int barY = context.getScaledWindowHeight() - 22 + FOOD_BAR_Y_OFFSET + MANA_COUNTER_Y_OFFSET;
        float scale = Math.min(MANA_COUNTER_MAX_SCALE, (FOOD_BAR_MANA_WIDTH - 2.0F) / textWidth);
        float scaledWidth = FOOD_BAR_MANA_WIDTH / scale;
        float scaledHeight = FOOD_BAR_MANA_HEIGHT / scale;
        int textX = Math.round((scaledWidth - textWidth) / 2.0F);
        int textY = Math.round((scaledHeight - textRenderer.fontHeight) / 2.0F);

        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(barX, barY, 200.0D);
        matrices.scale(scale, scale, 1.0F);
        context.drawText(
                textRenderer,
                text,
                textX,
                textY,
                overcharged ? OVERCHARGED_MANA_COUNTER_COLOR : NORMAL_MANA_COUNTER_COLOR,
                true
        );
        matrices.pop();
    }

    private static boolean customMobsSpawner$isCastingNowOrRecently() {
        if (MinecraftClient.getInstance().player instanceof SpellCasterClient client && client.isCastingSpell()) {
            customMobsSpawner$lastCastingActivityTimeMs = System.currentTimeMillis();
            return true;
        }
        return System.currentTimeMillis() - customMobsSpawner$lastCastingActivityTimeMs <= RECENT_CASTING_ACTIVITY_MS;
    }

    private static boolean customMobsSpawner$shouldShowManaCounter(ManaInterface manaInterface) {
        return manaInterface.getMaxMana() > 0.0F
                && (RpgManaOverchargeCompat.isOvercharged(MinecraftClient.getInstance().player, manaInterface)
                || manaInterface.getTimeFull() < RECENT_MANA_ACTIVITY_TICKS
                || customMobsSpawner$isCastingNowOrRecently());
    }

    private static String customMobsSpawner$formatManaValue(MinecraftClient client, ManaInterface manaInterface) {
        double baseMaxMana = manaInterface.getMaxMana();
        String value = customMobsSpawner$formatMana(manaInterface.getMana())
                + "/" + customMobsSpawner$formatMana(baseMaxMana);
        if (RpgManaOverchargeCompat.isOvercharged(client.player, manaInterface)) {
            value += "+" + customMobsSpawner$formatMana(RpgManaOverchargeCompat.getOverchargeCapacity(client.player));
        }
        return value;
    }

    private static String customMobsSpawner$formatMana(double value) {
        double rounded = Math.round(value * 10.0D) / 10.0D;
        if (Math.abs(rounded - Math.round(rounded)) < 0.0001D) {
            return Long.toString(Math.round(rounded));
        }
        return String.format(Locale.ROOT, "%.1f", rounded);
    }

    private static String customMobsSpawner$formatManaRegen(double value) {
        double rounded = Math.round(value * 100.0D) / 100.0D;
        if (Math.abs(rounded - Math.round(rounded)) < 0.0001D) {
            return String.format(Locale.ROOT, "%.1f", rounded);
        }
        if (Math.abs(rounded * 10.0D - Math.round(rounded * 10.0D)) < 0.0001D) {
            return String.format(Locale.ROOT, "%.1f", rounded);
        }
        return String.format(Locale.ROOT, "%.2f", rounded);
    }

    private static double customMobsSpawner$getDisplayedManaRegenPerSecond(
            MinecraftClient client,
            ManaInterface manaInterface
    ) {
        return manaInterface.getManaRegen() * MANA_REGEN_APPLICATIONS_PER_SECOND
                + customMobsSpawner$getBotaniaPoolManaRegenPerSecond(client, manaInterface);
    }

    private static double customMobsSpawner$getBotaniaPoolManaRegenPerSecond(
            MinecraftClient client,
            ManaInterface manaInterface
    ) {
        if (client.world == null || client.player == null) {
            return 0.0D;
        }

        if (client.player.getY() >= client.player.getBlockPos().getY() + BOTANIA_INSIDE_POOL_MAX_Y_OFFSET) {
            return 0.0D;
        }

        BlockEntity blockEntity = client.world.getBlockEntity(client.player.getBlockPos());
        if (blockEntity == null) {
            return 0.0D;
        }

        Identifier blockId = Registries.BLOCK.getId(blockEntity.getCachedState().getBlock());
        if (!customMobsSpawner$isBotaniaManaPool(blockId)) {
            return 0.0D;
        }

        int poolMana = customMobsSpawner$getCurrentPoolMana(blockEntity);
        if (poolMana <= 0) {
            return 0.0D;
        }

        double missingMana = RpgManaOverchargeCompat.getOverchargeMaxMana(client.player, manaInterface)
                - manaInterface.getMana();
        if (missingMana <= 0.0001D) {
            return 0.0D;
        }

        double playerManaPerExchange = Math.min(
                BOTANIA_POOL_PLAYER_MANA_PER_EXCHANGE,
                poolMana / BOTANIA_POOL_MANA_PER_PLAYER_MANA
        );
        return Math.min(missingMana, playerManaPerExchange * BOTANIA_POOL_EXCHANGES_PER_SECOND);
    }

    private static boolean customMobsSpawner$isBotaniaManaPool(Identifier blockId) {
        if (!"botania".equals(blockId.getNamespace())) {
            return false;
        }
        return switch (blockId.getPath()) {
            case "mana_pool", "diluted_pool", "fabulous_pool", "creative_pool" -> true;
            default -> false;
        };
    }

    private static int customMobsSpawner$getCurrentPoolMana(BlockEntity blockEntity) {
        try {
            Object value = blockEntity.getClass().getMethod("getCurrentMana").invoke(blockEntity);
            return value instanceof Number number ? number.intValue() : 0;
        } catch (ReflectiveOperationException exception) {
            return 0;
        }
    }
}
