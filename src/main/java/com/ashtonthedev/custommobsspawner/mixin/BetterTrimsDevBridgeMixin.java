package com.ashtonthedev.custommobsspawner.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LivingEntity.class)
public abstract class BetterTrimsDevBridgeMixin {
    @SuppressWarnings("unused")
    public World method_37908() {
        return ((LivingEntity) (Object) this).getEntityWorld();
    }

    @SuppressWarnings("unused")
    public BlockPos method_24515() {
        return ((LivingEntity) (Object) this).getBlockPos();
    }

    @SuppressWarnings("unused")
    public Vec3d method_33571() {
        return ((LivingEntity) (Object) this).getPos();
    }

    @SuppressWarnings("unused")
    public boolean method_5643(DamageSource source, float amount) {
        return ((LivingEntity) (Object) this).damage(source, amount);
    }

    @SuppressWarnings("unused")
    public double method_26825(EntityAttribute attribute) {
        return ((LivingEntity) (Object) this).getAttributeValue(attribute);
    }

    @SuppressWarnings("unused")
    public boolean method_29504() {
        return ((LivingEntity) (Object) this).isDead();
    }
}
