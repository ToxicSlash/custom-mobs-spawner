package com.ashtonthedev.custommobsspawner.mixin;

import com.ashtonthedev.custommobsspawner.combat.ComboHandler;
import com.ashtonthedev.custommobsspawner.effect.ModStatusEffects;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
    @Shadow
    public ServerPlayerEntity player;

    @Inject(method = "onUpdateSelectedSlot", at = @At("HEAD"))
    private void customMobsSpawner$clearWeaponComboStatusEffects(UpdateSelectedSlotC2SPacket packet, CallbackInfo ci) {
        if (packet.getSelectedSlot() != this.player.getInventory().selectedSlot) {
            ModStatusEffects.clearWeaponComboStatusEffects(this.player);
            ComboHandler.reset(this.player);
        }
    }
}
