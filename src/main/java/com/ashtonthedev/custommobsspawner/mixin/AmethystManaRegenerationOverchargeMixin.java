package com.ashtonthedev.custommobsspawner.mixin;

import com.ashtonthedev.custommobsspawner.compat.AmethystRpgManaCompat;
import com.ashtonthedev.custommobsspawner.compat.RpgManaOverchargeCompat;
import com.cleannrooster.rpgmana.api.ManaInterface;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class AmethystManaRegenerationOverchargeMixin {
    private static final double MANA_EPSILON = 0.0001D;

    @Inject(method = "tick", at = @At("TAIL"))
    private void customMobsSpawner$overchargeFromAmethystManaRegeneration(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (player.getWorld().isClient()
                || !AmethystRpgManaCompat.hasManaRegenerationStatus(player)
                || !(player instanceof ManaInterface manaInterface)) {
            return;
        }

        double currentMana = manaInterface.getMana();
        double baseMaxMana = manaInterface.getMaxMana();
        if (currentMana + MANA_EPSILON < baseMaxMana) {
            return;
        }

        double overchargeMaxMana = RpgManaOverchargeCompat.getOverchargeMaxMana(player, manaInterface);
        double missingOverchargeMana = overchargeMaxMana - currentMana;
        if (missingOverchargeMana <= MANA_EPSILON) {
            return;
        }

        double manaToRestore = Math.min(Math.max(manaInterface.getManaRegen(), 0.0D), missingOverchargeMana);
        if (manaToRestore > MANA_EPSILON) {
            manaInterface.spendMana(manaToRestore);
        }
    }
}
