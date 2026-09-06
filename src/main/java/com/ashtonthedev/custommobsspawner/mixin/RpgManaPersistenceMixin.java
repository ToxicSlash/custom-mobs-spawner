package com.ashtonthedev.custommobsspawner.mixin;

import com.ashtonthedev.custommobsspawner.compat.RpgManaOverchargeCompat;
import com.cleannrooster.rpgmana.api.ManaInterface;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class RpgManaPersistenceMixin {
    private static final String CUSTOM_MOBS_SPAWNER_RPG_MANA_KEY = "cmobs_rpgmana";

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void customMobsSpawner$writeRpgMana(NbtCompound nbt, CallbackInfo ci) {
        if ((Object) this instanceof PlayerEntity player && player instanceof ManaInterface manaInterface) {
            double mana = Math.min(manaInterface.getMana(), RpgManaOverchargeCompat.getOverchargeMaxMana(player, manaInterface));
            nbt.putDouble(CUSTOM_MOBS_SPAWNER_RPG_MANA_KEY, mana);
        }
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void customMobsSpawner$readRpgMana(NbtCompound nbt, CallbackInfo ci) {
        if (!nbt.contains(CUSTOM_MOBS_SPAWNER_RPG_MANA_KEY, NbtElement.DOUBLE_TYPE)
                || !((Object) this instanceof ManaInterface manaInterface)) {
            return;
        }

        double savedMana = nbt.getDouble(CUSTOM_MOBS_SPAWNER_RPG_MANA_KEY);
        if (!Double.isFinite(savedMana)) {
            return;
        }

        PlayerEntity player = (PlayerEntity) (Object) this;
        double targetMana = Math.max(-999999.0D, Math.min(
                savedMana,
                RpgManaOverchargeCompat.getOverchargeMaxMana(player, manaInterface)
        ));
        manaInterface.spendMana(targetMana - manaInterface.getMana());
    }
}
