package com.ashtonthedev.custommobsspawner.mixin;

import com.ashtonthedev.custommobsspawner.combat.ComboHandler;
import com.ashtonthedev.custommobsspawner.skill.CustomSkillRegistry;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ProjectileEntity.class)
public abstract class ProjectileEntityMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void customMobsSpawner$skillProjectileTrailTick(CallbackInfo ci) {
        CustomSkillRegistry.tickSkillProjectile((ProjectileEntity) (Object) this);
    }

    @Inject(method = "onCollision", at = @At("HEAD"))
    private void customMobsSpawner$skillProjectileImpact(HitResult hitResult, CallbackInfo ci) {
        CustomSkillRegistry.onSkillProjectileHit((ProjectileEntity) (Object) this, hitResult);
    }

    @Inject(method = "onBlockHit", at = @At("HEAD"))
    private void customMobsSpawner$skillProjectileBlockImpact(BlockHitResult hitResult, CallbackInfo ci) {
        ComboHandler.recordProjectileMiss((ProjectileEntity) (Object) this);
        CustomSkillRegistry.onSkillProjectileHit((ProjectileEntity) (Object) this, hitResult);
    }

    @Inject(method = "onEntityHit", at = @At("HEAD"))
    private void customMobsSpawner$skillProjectileEntityImpact(EntityHitResult hitResult, CallbackInfo ci) {
        CustomSkillRegistry.onSkillProjectileHit((ProjectileEntity) (Object) this, hitResult);
    }
}
