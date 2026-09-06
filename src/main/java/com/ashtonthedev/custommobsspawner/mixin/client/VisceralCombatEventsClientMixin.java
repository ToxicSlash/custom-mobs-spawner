package com.ashtonthedev.custommobsspawner.mixin.client;

import com.ashtonthedev.custommobsspawner.client.VisceralCombatClientCompat;
import net.bettercombat.api.AttackHand;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.cleannrooster.visceral_combat.client.CombatEventsClient", remap = false)
public abstract class VisceralCombatEventsClientMixin {
    @Inject(method = "lambda$register$0(Lnet/minecraft/class_746;Lnet/bettercombat/api/AttackHand;)V", at = @At("TAIL"), require = 0)
    private static void customMobsSpawner$scaleVisceralLunge(ClientPlayerEntity player, AttackHand attackHand, CallbackInfo ci) {
        VisceralCombatClientCompat.applyAttackStartLunge(player);
    }
}
