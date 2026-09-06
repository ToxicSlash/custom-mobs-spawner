package com.ashtonthedev.custommobsspawner.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSkeletonEntity.class)
public abstract class AbstractSkeletonEntityMixin {
    @Unique
    private static final String CUSTOM_MOBS_SPAWNER_LOST_ARCHER_TAG = "lost_archer";
    @Unique
    private static final int CUSTOM_MOBS_SPAWNER_LOST_ARCHER_SHOT_INTERVAL = 60;
    @Unique
    private long customMobsSpawner$lastLostArcherShotTime = -CUSTOM_MOBS_SPAWNER_LOST_ARCHER_SHOT_INTERVAL;

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void customMobsSpawner$rateLimitLostArcherShots(LivingEntity target, float pullProgress, CallbackInfo ci) {
        AbstractSkeletonEntity skeleton = (AbstractSkeletonEntity) (Object) this;
        if (!skeleton.getCommandTags().contains(CUSTOM_MOBS_SPAWNER_LOST_ARCHER_TAG)) {
            return;
        }

        long time = skeleton.getWorld().getTime();
        if (time >= customMobsSpawner$lastLostArcherShotTime
                && time - customMobsSpawner$lastLostArcherShotTime < CUSTOM_MOBS_SPAWNER_LOST_ARCHER_SHOT_INTERVAL) {
            ci.cancel();
            return;
        }
        customMobsSpawner$lastLostArcherShotTime = time;
    }
}
