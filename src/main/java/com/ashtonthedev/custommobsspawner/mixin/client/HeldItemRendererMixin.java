package com.ashtonthedev.custommobsspawner.mixin.client;

import com.ashtonthedev.custommobsspawner.combat.PlayerParryHandler;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void customMobsSpawner$claymoreBlockingPose(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (!player.isUsingItem() || player.getActiveHand() != hand || !PlayerParryHandler.isParrySword(item)) {
            return;
        }

        int side = hand == Hand.MAIN_HAND ? 1 : -1;
        matrices.translate(side * -0.18D, 0.18D, -0.32D);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 38.0F));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-48.0F));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * 18.0F));
    }
}
