package com.ashtonthedev.custommobsspawner.mixin;

import com.ashtonthedev.custommobsspawner.data.CustomMobRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityIafHealthReadMixin {
    @Inject(method = "readNbt", at = @At("RETURN"))
    private void cmobs$restoreSavedIafHealth(NbtCompound nbt, CallbackInfo ci) {
        CustomMobRegistry.restoreIafSavedHealth((Entity)(Object)this, nbt);
    }
}
