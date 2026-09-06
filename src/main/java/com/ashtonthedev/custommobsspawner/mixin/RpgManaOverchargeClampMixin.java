package com.ashtonthedev.custommobsspawner.mixin;

import com.ashtonthedev.custommobsspawner.compat.RpgManaOverchargeCompat;
import com.cleannrooster.rpgmana.api.ManaInterface;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = PlayerEntity.class, priority = 500)
public abstract class RpgManaOverchargeClampMixin {
    @Unique
    private double customMobsSpawner$overchargedManaBeforeTick = Double.NaN;

    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void customMobsSpawner$recordOverchargeBeforeRpgManaClamp(CallbackInfo ci) {
        customMobsSpawner$overchargedManaBeforeTick = Double.NaN;
        if (!((Object) this instanceof PlayerEntity player) || !((Object) this instanceof ManaInterface manaInterface)) {
            return;
        }

        double baseMaxMana = manaInterface.getMaxMana();
        double currentMana = manaInterface.getMana();
        if (currentMana > baseMaxMana + 0.0001D) {
            customMobsSpawner$overchargedManaBeforeTick = Math.min(
                    currentMana,
                    RpgManaOverchargeCompat.getOverchargeMaxMana(player, manaInterface)
            );
        }
    }

    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void customMobsSpawner$restoreOverchargedManaAfterClamp(CallbackInfo ci) {
        if (Double.isNaN(customMobsSpawner$overchargedManaBeforeTick)
                || !((Object) this instanceof PlayerEntity player)
                || !((Object) this instanceof ManaInterface manaInterface)) {
            return;
        }

        double baseMaxMana = manaInterface.getMaxMana();
        double currentMana = manaInterface.getMana();
        double targetMana = Math.min(
                customMobsSpawner$overchargedManaBeforeTick + currentMana - baseMaxMana,
                RpgManaOverchargeCompat.getOverchargeMaxMana(player, manaInterface)
        );
        double manaToRestore = targetMana - currentMana;
        if (manaToRestore > 0.0001D) {
            List<?> manaInstances = manaInterface.getManaInstances();
            int instanceCount = manaInstances.size();
            manaInterface.spendMana(manaToRestore);
            while (manaInstances.size() > instanceCount) {
                manaInstances.remove(manaInstances.size() - 1);
            }
        }
    }
}
