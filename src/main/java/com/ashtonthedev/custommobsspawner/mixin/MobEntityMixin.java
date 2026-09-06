package com.ashtonthedev.custommobsspawner.mixin;

import com.ashtonthedev.custommobsspawner.CustomMobsSpawner;
import com.ashtonthedev.custommobsspawner.effect.ModStatusEffects;
import com.ashtonthedev.custommobsspawner.skill.CustomSkillRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEntity.class)
public abstract class MobEntityMixin {
    private static final float TURNING_SLOW_BASE_DEGREES_PER_TICK = 6.0F;
    private static final float TURNING_SLOW_MIN_DEGREES_PER_TICK = 3.0F;

    @Inject(method = "initialize", at = @At("HEAD"))
    private void customMobsSpawner$recordSpawnerSpawn(
            ServerWorldAccess world,
            LocalDifficulty difficulty,
            SpawnReason spawnReason,
            EntityData entityData,
            NbtCompound entityNbt,
            CallbackInfoReturnable<EntityData> cir
    ) {
        if (spawnReason == SpawnReason.SPAWNER) {
            ((MobEntity) (Object) this).addCommandTag(CustomMobsSpawner.SPAWNER_SPAWNED_TAG);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void customMobsSpawner$slowTurning(CallbackInfo ci) {
        MobEntity mob = (MobEntity) (Object) this;
        StatusEffectInstance turningSlow = mob.getStatusEffect(ModStatusEffects.TURNING_SLOW);
        if (turningSlow == null) {
            return;
        }

        float maxDegreesPerTick = Math.max(
                TURNING_SLOW_MIN_DEGREES_PER_TICK,
                TURNING_SLOW_BASE_DEGREES_PER_TICK / (turningSlow.getAmplifier() + 1.0F)
        );
        mob.setYaw(clampedAngle(mob.prevYaw, mob.getYaw(), maxDegreesPerTick));
        mob.bodyYaw = clampedAngle(mob.prevBodyYaw, mob.bodyYaw, maxDegreesPerTick);
        mob.headYaw = clampedAngle(mob.prevHeadYaw, mob.headYaw, maxDegreesPerTick);
    }

    @Inject(method = "tryAttack", at = @At("RETURN"))
    private void customMobsSpawner$whenAttacks(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            CustomSkillRegistry.runAttack((MobEntity) (Object) this, target);
        }
    }

    private static float clampedAngle(float previous, float current, float maxDegrees) {
        float delta = MathHelper.wrapDegrees(current - previous);
        return previous + MathHelper.clamp(delta, -maxDegrees, maxDegrees);
    }
}
