package com.ashtonthedev.custommobsspawner.mixin;

import com.ashtonthedev.custommobsspawner.data.CustomMobRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.iafenvoy.iceandfire.entity.EntityDragonBase", remap = false)
public abstract class IceAndFireDragonAttributesMixin implements CustomMobRegistry.IafDragonHealthBaseline {
    @Unique private double cmobs$nativeDragonHealth = Double.NaN;
    @Override
    public double cmobs$getNativeDragonHealth() { return cmobs$nativeDragonHealth; }
    @Inject(method = "updateAttributes", at = @At("RETURN"), remap = false)
    private void cmobs$restoreDeclaredBases(CallbackInfo ci) {
        var instance = ((LivingEntity)(Object)this).getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (instance != null) cmobs$nativeDragonHealth = instance.getBaseValue();
        CustomMobRegistry.restoreIafAttributes((Entity)(Object)this);
    }
}
