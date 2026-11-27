package com.ashtonthedev.custommobsspawner.mixin;

import com.ashtonthedev.custommobsspawner.data.CustomMobRegistry;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = { "com.iafenvoy.iceandfire.entity.EntityCyclops", "com.iafenvoy.iceandfire.entity.EntityTroll" }, remap = false)
public abstract class IceAndFireConfiguredAttributesMixin {
    @Inject(method = "setConfigurableAttributes", at = @At("RETURN"), remap = false)
    private void cmobs$restoreDeclaredBases(CallbackInfo ci) {
        CustomMobRegistry.restoreIafAttributes((Entity)(Object)this);
    }
}
