package com.ashtonthedev.custommobsspawner.compat;

import com.ashtonthedev.custommobsspawner.combat.ModItemTags;
import com.ashtonthedev.custommobsspawner.effect.ModStatusEffects;
import com.ashtonthedev.custommobsspawner.item.ManaCapacitorItem;
import com.cleannrooster.rpgmana.api.ManaInterface;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.BiConsumer;

public final class ManaCapacitorInfluxHandler {
    private static final double TRIGGER_MANA_THRESHOLD = 20.0D;
    private static final int MANA_INFLUX_DURATION_TICKS = 80;
    private static final int CAPACITOR_RPG_MANA_COST = 40;

    private ManaCapacitorInfluxHandler() {
    }

    public static void tick(ServerPlayerEntity player) {
        if (!(player instanceof ManaInterface manaInterface)
                || player.hasStatusEffect(ModStatusEffects.MANA_INFLUX)
                || manaInterface.getMana() > TRIGGER_MANA_THRESHOLD) {
            return;
        }

        if (consumeEquippedCapacitor(player)) {
            player.addStatusEffect(new StatusEffectInstance(
                    ModStatusEffects.MANA_INFLUX,
                    MANA_INFLUX_DURATION_TICKS,
                    0,
                    false,
                    true,
                    true
            ));
        }
    }

    private static boolean consumeEquippedCapacitor(ServerPlayerEntity player) {
        boolean[] consumed = {false};
        trinketComponent(player).ifPresent(component -> forEachTrinket(component, (slot, stack) -> {
            if (!consumed[0] && isManaCapacitor(stack)) {
                consumed[0] = ManaCapacitorItem.consumeRpgMana(stack, CAPACITOR_RPG_MANA_COST);
            }
        }));
        return consumed[0];
    }

    private static Optional<?> trinketComponent(LivingEntity entity) {
        try {
            Class<?> trinketsApi = Class.forName("dev.emi.trinkets.api.TrinketsApi");
            Method getTrinketComponent = trinketsApi.getMethod("getTrinketComponent", LivingEntity.class);
            Object result = getTrinketComponent.invoke(null, entity);
            return result instanceof Optional<?> optional ? optional : Optional.empty();
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private static void forEachTrinket(Object component, BiConsumer<Object, ItemStack> consumer) {
        try {
            Method forEach = component.getClass().getMethod("forEach", BiConsumer.class);
            forEach.invoke(component, consumer);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
        }
    }

    private static boolean isManaCapacitor(ItemStack stack) {
        return !stack.isEmpty() && stack.isIn(ModItemTags.MANA_CAPACITORS);
    }
}
