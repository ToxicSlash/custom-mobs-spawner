package com.ashtonthedev.custommobsspawner.mixin.client;

import com.ashtonthedev.custommobsspawner.skill.CustomSkillRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Identifier;
import net.spell_engine.internals.SpellCooldownManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SpellCooldownManager.class, priority = 1500)
public abstract class SpellEngineSkillCooldownMixin {
    @Inject(method = "getCooldownProgress", at = @At("RETURN"), cancellable = true)
    private void customMobsSpawner$grayOutUnavailableSpellCastSkills(
            Identifier spellId,
            float tickDelta,
            CallbackInfoReturnable<Float> cir
    ) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || CustomSkillRegistry.canShowSpellReady(player, spellId)) {
            return;
        }

        cir.setReturnValue(Math.max(cir.getReturnValueF(), 1.0F));
    }
}
