package com.ashtonthedev.custommobsspawner.mixin;

import com.cleannrooster.rpgmana.api.ManaInterface;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ServerPlayerEntity.class)
public abstract class RpgManaDeathResetMixin {
    @Inject(method = "copyFrom", at = @At("TAIL"))
    private void customMobsSpawner$restoreRpgManaAfterDeath(
            ServerPlayerEntity oldPlayer,
            boolean alive,
            CallbackInfo ci
    ) {
        if (alive || !((Object) this instanceof ManaInterface manaInterface)) {
            return;
        }

        double restoreAmount = manaInterface.getMaxMana() - manaInterface.getMana();
        if (restoreAmount <= 0.0D || !Double.isFinite(restoreAmount)) {
            return;
        }

        List<?> manaInstances = manaInterface.getManaInstances();
        int instanceCount = manaInstances.size();
        manaInterface.spendMana(restoreAmount);
        while (manaInstances.size() > instanceCount) {
            manaInstances.remove(manaInstances.size() - 1);
        }
    }
}
