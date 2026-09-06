package com.ashtonthedev.custommobsspawner.client;

import com.ashtonthedev.custommobsspawner.compat.BetterCombatAnimationCompat;
import com.ashtonthedev.custommobsspawner.data.CustomMobsSpawnerLog;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

public final class BetterCombatClientCompat {
    private BetterCombatClientCompat() {
    }

    public static void register() {
        registerLocalAttackAnimationReceiver();
        try {
            Class<?> eventsClass = Class.forName("net.bettercombat.api.client.BetterCombatClientEvents");
            Class<?> listenerClass = Class.forName("net.bettercombat.api.client.BetterCombatClientEvents$PlayerAttackHit");
            Object publisher = eventsClass.getField("ATTACK_HIT").get(null);
            Object listener = Proxy.newProxyInstance(
                    listenerClass.getClassLoader(),
                    new Class<?>[]{listenerClass},
                    BetterCombatClientCompat::handleAttackHit
            );
            publisher.getClass().getMethod("register", Object.class).invoke(publisher, listener);
        } catch (ReflectiveOperationException | LinkageError exception) {
            CustomMobsSpawnerLog.warn("Failed to register Better Combat failed-attack hook: " + exception.getMessage());
        }
    }

    private static void registerLocalAttackAnimationReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(BetterCombatAnimationCompat.LOCAL_ATTACK_ANIMATION_PACKET, (client, handler, buf, responseSender) -> {
            String hand = buf.readString();
            String animation = buf.readString();
            float length = buf.readFloat();
            float upswing = buf.readFloat();
            client.execute(() -> playLocalAttackAnimation(client, hand, animation, length, upswing));
        });
    }

    private static void playLocalAttackAnimation(MinecraftClient client, String hand, String animation, float length, float upswing) {
        if (client.player == null) {
            return;
        }
        try {
            Class<?> animatedHandClass = Class.forName("net.bettercombat.logic.AnimatedHand");
            Object animatedHand = enumValue(animatedHandClass, switch (hand.toLowerCase()) {
                case "offhand", "off_hand" -> "OFF_HAND";
                case "two_handed", "twohanded", "both" -> "TWO_HANDED";
                default -> "MAIN_HAND";
            });
            Class<?> animatableClass = Class.forName("net.bettercombat.client.animation.PlayerAttackAnimatable");
            Method playMethod = animatableClass.getMethod("playAttackAnimation", String.class, animatedHandClass, float.class, float.class);
            playMethod.invoke(client.player, animation, animatedHand, length, upswing);
        } catch (ReflectiveOperationException | LinkageError exception) {
            CustomMobsSpawnerLog.warn("Failed to play local Better Combat attack animation: " + exception.getMessage());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumValue(Class<?> enumClass, String name) {
        return Enum.valueOf(enumClass.asSubclass(Enum.class), name);
    }

    private static Object handleAttackHit(Object proxy, Method method, Object[] args) {
        if (method.getDeclaringClass() == Object.class) {
            return handleObjectMethod(proxy, method, args);
        }

        if ("onPlayerAttackStart".equals(method.getName())
                && args != null
                && args.length >= 3
                && args[0] instanceof ClientPlayerEntity player
                && args[2] instanceof List<?> targets
                && targets.isEmpty()) {
            FailedAttackClientNotifier.notifyFailedAttack(player);
        }
        return null;
    }

    private static Object handleObjectMethod(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "toString" -> "CmobsBetterCombatAttackHitListener";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == (args == null || args.length == 0 ? null : args[0]);
            default -> null;
        };
    }
}
