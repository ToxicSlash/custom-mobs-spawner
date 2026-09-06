package com.ashtonthedev.custommobsspawner.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "com.cleannrooster.rpgmana.Rpgmana", remap = false)
public abstract class RpgManaDefaultsMixin {
    @ModifyArg(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/attribute/ClampedEntityAttribute;<init>(Ljava/lang/String;DDD)V",
                    ordinal = 0
            ),
            index = 1,
            require = 0
    )
    private static double customMobsSpawner$setBaseMana(double original) {
        return 80.0D;
    }

    @ModifyArg(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/attribute/ClampedEntityAttribute;<init>(Ljava/lang/String;DDD)V",
                    ordinal = 1
            ),
            index = 1,
            require = 0
    )
    private static double customMobsSpawner$setBaseManaRegen(double original) {
        return 1.0D;
    }
}
