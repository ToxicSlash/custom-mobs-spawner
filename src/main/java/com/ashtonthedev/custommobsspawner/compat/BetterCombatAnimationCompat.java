package com.ashtonthedev.custommobsspawner.compat;

import com.ashtonthedev.custommobsspawner.CustomMobsSpawner;
import com.ashtonthedev.custommobsspawner.data.CustomMobsSpawnerLog;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public final class BetterCombatAnimationCompat {
    public static final Identifier LOCAL_ATTACK_ANIMATION_PACKET = CustomMobsSpawner.id("better_combat_local_attack_animation");

    private static boolean warned;
    private static boolean initialized;
    private static Class<?> animatedHandClass;
    private static Object mainHand;
    private static Object offHand;
    private static Object twoHanded;
    private static Constructor<?> attackAnimationConstructor;
    private static Method writeMethod;
    private static Identifier packetId;

    private BetterCombatAnimationCompat() {
    }

    public static boolean playAttackAnimation(
            ServerPlayerEntity player,
            String hand,
            String animation,
            float length,
            float upswing
    ) {
        if (!FabricLoader.getInstance().isModLoaded("bettercombat") || !initialize()) {
            return false;
        }

        try {
            Object animatedHand = animatedHand(hand);
            Object packet = attackAnimationConstructor.newInstance(
                    player.getId(),
                    animatedHand,
                    animation,
                    length,
                    upswing
            );
            boolean sent = sendLocal(player, hand, animation, length, upswing);
            for (ServerPlayerEntity trackingPlayer : PlayerLookup.tracking(player)) {
                sent |= send(trackingPlayer, packet);
            }
            return sent;
        } catch (ReflectiveOperationException | LinkageError exception) {
            warn(exception);
            return false;
        }
    }

    private static boolean initialize() {
        if (initialized) {
            return attackAnimationConstructor != null;
        }
        initialized = true;
        try {
            animatedHandClass = Class.forName("net.bettercombat.logic.AnimatedHand");
            mainHand = enumValue(animatedHandClass, "MAIN_HAND");
            offHand = enumValue(animatedHandClass, "OFF_HAND");
            twoHanded = enumValue(animatedHandClass, "TWO_HANDED");

            Class<?> packetClass = Class.forName("net.bettercombat.network.Packets$AttackAnimation");
            packetId = (Identifier) packetClass.getField("ID").get(null);
            attackAnimationConstructor = packetClass.getConstructor(
                    int.class,
                    animatedHandClass,
                    String.class,
                    float.class,
                    float.class
            );
            writeMethod = packetClass.getMethod("write");
            return true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            warn(exception);
            return false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumValue(Class<?> enumClass, String name) {
        return Enum.valueOf(enumClass.asSubclass(Enum.class), name);
    }

    private static Object animatedHand(String value) {
        return switch (value.toLowerCase()) {
            case "offhand", "off_hand" -> offHand;
            case "two_handed", "twohanded", "both" -> twoHanded;
            default -> mainHand;
        };
    }

    private static boolean send(ServerPlayerEntity player, Object packet) throws ReflectiveOperationException {
        if (ServerPlayNetworking.canSend(player, packetId)) {
            PacketByteBuf buf = (PacketByteBuf) writeMethod.invoke(packet);
            ServerPlayNetworking.send(player, packetId, buf);
            return true;
        }
        return false;
    }

    private static boolean sendLocal(ServerPlayerEntity player, String hand, String animation, float length, float upswing) {
        if (!ServerPlayNetworking.canSend(player, LOCAL_ATTACK_ANIMATION_PACKET)) {
            return false;
        }
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(hand);
        buf.writeString(animation);
        buf.writeFloat(length);
        buf.writeFloat(upswing);
        ServerPlayNetworking.send(player, LOCAL_ATTACK_ANIMATION_PACKET, buf);
        return true;
    }

    private static void warn(Throwable exception) {
        if (!warned) {
            warned = true;
            CustomMobsSpawnerLog.warn("Failed to play Better Combat attack animation: " + exception.getMessage());
        }
    }
}
