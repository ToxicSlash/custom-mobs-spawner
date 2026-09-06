package com.ashtonthedev.custommobsspawner.mixin;

import com.ashtonthedev.custommobsspawner.combat.ComboHandler;
import com.ashtonthedev.custommobsspawner.skill.CustomSkillRegistry;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PersistentProjectileEntity.class)
public abstract class PersistentProjectileEntityMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void customMobsSpawner$skillPersistentProjectileTrailTick(CallbackInfo ci) {
        CustomSkillRegistry.tickSkillProjectile((ProjectileEntity) (Object) this);
    }

    @Inject(method = "onBlockHit", at = @At("HEAD"))
    private void customMobsSpawner$skillPersistentProjectileBlockImpact(BlockHitResult hitResult, CallbackInfo ci) {
        ComboHandler.recordProjectileMiss((ProjectileEntity) (Object) this);
        CustomSkillRegistry.onSkillProjectileHit((ProjectileEntity) (Object) this, hitResult);
    }

    @Inject(method = "onEntityHit", at = @At("HEAD"))
    private void customMobsSpawner$skillPersistentProjectileEntityImpact(EntityHitResult hitResult, CallbackInfo ci) {
        CustomSkillRegistry.onSkillProjectileHit((ProjectileEntity) (Object) this, hitResult);
    }
}
