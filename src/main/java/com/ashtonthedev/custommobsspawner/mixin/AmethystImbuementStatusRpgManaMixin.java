package com.ashtonthedev.custommobsspawner.mixin;

import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.effect.StatusEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "me.fzzyhmstrs.amethyst_imbuement.registry.RegisterStatus", remap = false)
public abstract class AmethystImbuementStatusRpgManaMixin {
    @Unique
    private static boolean customMobsSpawner$rpgManaRegenModifierAdded;

    @Inject(method = "registerAll", at = @At("TAIL"), require = 0)
    private void customMobsSpawner$addRpgManaRegenModifier(CallbackInfo ci) {
        if (customMobsSpawner$rpgManaRegenModifierAdded) {
            return;
        }

        try {
            StatusEffect manaRegeneration = customMobsSpawner$getAmethystManaRegenerationStatus();
            EntityAttribute rpgManaRegen = customMobsSpawner$getRpgManaRegenAttribute();
            manaRegeneration.addAttributeModifier(
                    rpgManaRegen,
                    "ef07280d-bcd6-47ce-bac3-bc191e2af0ff",
                    1.0D,
                    EntityAttributeModifier.Operation.ADDITION
            );
        } catch (ReflectiveOperationException | ClassCastException exception) {
            throw new IllegalStateException(
                    "Failed to bridge Amethyst Imbuement mana regeneration to RPGMana",
                    exception
            );
        }
        customMobsSpawner$rpgManaRegenModifierAdded = true;
    }

    @Unique
    private static StatusEffect customMobsSpawner$getAmethystManaRegenerationStatus()
            throws ReflectiveOperationException {
        Class<?> registerStatusClass = Class.forName("me.fzzyhmstrs.amethyst_imbuement.registry.RegisterStatus");
        Object registerStatus = registerStatusClass.getField("INSTANCE").get(null);
        return (StatusEffect) registerStatusClass.getMethod("getMANA_REGENERATION").invoke(registerStatus);
    }

    @Unique
    private static EntityAttribute customMobsSpawner$getRpgManaRegenAttribute()
            throws ReflectiveOperationException {
        Class<?> rpgManaClass = Class.forName("com.cleannrooster.rpgmana.Rpgmana");
        return (EntityAttribute) rpgManaClass.getField("MANAREGEN").get(null);
    }
}
