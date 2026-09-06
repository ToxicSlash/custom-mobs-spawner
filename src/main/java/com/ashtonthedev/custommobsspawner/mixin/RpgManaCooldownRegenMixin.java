package com.ashtonthedev.custommobsspawner.mixin;

import com.cleannrooster.rpgmana.api.ManaInterface;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(PlayerEntity.class)
public abstract class RpgManaCooldownRegenMixin {
    private static final double EXTRA_NEGATIVE_MANA_REGEN_MULTIPLIER = 3.0D;

    @Inject(method = "tick", at = @At("TAIL"))
    private void customMobsSpawner$quadrupleNegativeManaRecovery(CallbackInfo ci) {
        if (!((Object) this instanceof ManaInterface manaInterface)) {
            return;
        }

        double currentMana = manaInterface.getMana();
        if (currentMana >= 0.0D) {
            return;
        }

        double extraRegen = Math.min(
                Math.max(manaInterface.getManaRegen(), 0.0D) * EXTRA_NEGATIVE_MANA_REGEN_MULTIPLIER,
                -currentMana
        );
        if (extraRegen <= 0.0D) {
            return;
        }

        List<?> manaInstances = manaInterface.getManaInstances();
        int instanceCount = manaInstances.size();
        manaInterface.spendMana(extraRegen);
        if (manaInstances.size() > instanceCount) {
            manaInstances.remove(manaInstances.size() - 1);
        }
    }
}
