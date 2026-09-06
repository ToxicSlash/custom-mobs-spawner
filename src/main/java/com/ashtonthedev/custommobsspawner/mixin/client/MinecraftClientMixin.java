package com.ashtonthedev.custommobsspawner.mixin.client;

import com.ashtonthedev.custommobsspawner.client.FailedAttackClientNotifier;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Inject(method = "doAttack", at = @At("RETURN"))
    private void customMobsSpawner$notifyFailedAttack(CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        FailedAttackClientNotifier.notifyIfCrosshairMiss(client.player);
    }
}
