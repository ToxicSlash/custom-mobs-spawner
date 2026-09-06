package com.ashtonthedev.custommobsspawner.combat;

import com.ashtonthedev.custommobsspawner.CustomMobsSpawner;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ComboHandler {
    public static final Identifier COMBO_SYNC_PACKET = CustomMobsSpawner.id("combo_sync");
    private static final int RESET_TICKS = 100;
    private static final int SUSTAINED_MAGIC_COMBO_INTERVAL_TICKS = 100;
    private static final Map<UUID, Integer> COMBOS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> CLIENT_COMBOS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> RESET_DELAYS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_MELEE_COMBO_GAIN_TICKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_SUSTAINED_MAGIC_COMBO_GAIN_TICKS = new ConcurrentHashMap<>();

    private ComboHandler() {
    }

    public static void recordDamageSuccess(LivingEntity target, DamageSource source) {
        if (target.getWorld().isClient() || SpellComboHandler.isSpellImpactActive()) {
            return;
        }

        ServerPlayerEntity player = playerDamageSource(source);
        if (player == null || player == target) {
            return;
        }

        long time = player.getWorld().getTime();
        UUID uuid = player.getUuid();
        if (isSustainedMagicDamage(source)) {
            Long previousMagicGainTick = LAST_SUSTAINED_MAGIC_COMBO_GAIN_TICKS.get(uuid);
            if (previousMagicGainTick != null && time - previousMagicGainTick < SUSTAINED_MAGIC_COMBO_INTERVAL_TICKS) {
                return;
            }
            LAST_SUSTAINED_MAGIC_COMBO_GAIN_TICKS.put(uuid, time);
            increment(player);
            return;
        }

        Long previousGainTick = LAST_MELEE_COMBO_GAIN_TICKS.get(uuid);
        if (previousGainTick != null && previousGainTick == time) {
            return;
        }
        LAST_MELEE_COMBO_GAIN_TICKS.put(uuid, time);
        increment(player);
    }

    public static void recordProjectileMiss(ProjectileEntity projectile) {
        if (projectile.getWorld().isClient() || !(projectile.getOwner() instanceof ServerPlayerEntity player)) {
            return;
        }

        reset(player);
    }

    public static void increment(ServerPlayerEntity player) {
        increment(player, 1);
    }

    public static void increment(ServerPlayerEntity player, int amount) {
        if (amount <= 0) {
            return;
        }
        UUID uuid = player.getUuid();
        int combo = COMBOS.merge(uuid, amount, Integer::sum);
        RESET_DELAYS.put(uuid, RESET_TICKS);
        sync(player, combo, true);
    }

    public static void reset(ServerPlayerEntity player) {
        reset(player, false);
    }

    public static void clearPlayer(UUID uuid) {
        if (uuid == null) {
            return;
        }
        COMBOS.remove(uuid);
        RESET_DELAYS.remove(uuid);
        LAST_MELEE_COMBO_GAIN_TICKS.remove(uuid);
        LAST_SUSTAINED_MAGIC_COMBO_GAIN_TICKS.remove(uuid);
    }

    public static void decrementFromDamageTaken(ServerPlayerEntity player, DamageSource source) {
        if (source.getAttacker() == player || source.getSource() == player) {
            return;
        }
        decrement(player, 1, true);
    }

    private static void decrement(ServerPlayerEntity player, int amount, boolean markCombat) {
        if (amount <= 0) {
            return;
        }

        UUID uuid = player.getUuid();
        int current = COMBOS.getOrDefault(uuid, 0);
        if (current <= 0) {
            if (markCombat) {
                sync(player, 0, true);
            }
            return;
        }

        int combo = Math.max(0, current - amount);
        if (combo <= 0) {
            clearPlayer(uuid);
        } else {
            COMBOS.put(uuid, combo);
            RESET_DELAYS.putIfAbsent(uuid, RESET_TICKS);
        }
        sync(player, combo, markCombat);
    }

    private static void reset(ServerPlayerEntity player, boolean markCombat) {
        UUID uuid = player.getUuid();
        boolean hadCombo = COMBOS.containsKey(uuid);
        boolean hadDelay = RESET_DELAYS.containsKey(uuid);
        clearPlayer(uuid);
        if (!hadCombo && !hadDelay && !markCombat) {
            return;
        }
        sync(player, 0, markCombat);
    }

    public static int getCombo(ServerPlayerEntity player) {
        return getCombo((PlayerEntity) player);
    }

    public static int getCombo(PlayerEntity player) {
        Map<UUID, Integer> combos = player.getWorld().isClient() ? CLIENT_COMBOS : COMBOS;
        return Math.max(0, combos.getOrDefault(player.getUuid(), 0));
    }

    public static void setClientCombo(UUID uuid, int combo) {
        if (combo <= 0) {
            CLIENT_COMBOS.remove(uuid);
            return;
        }
        CLIENT_COMBOS.put(uuid, combo);
    }

    public static void tick(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID uuid = player.getUuid();
            Integer combo = COMBOS.get(uuid);
            if (combo == null || combo <= 0) {
                LAST_MELEE_COMBO_GAIN_TICKS.remove(uuid);
                LAST_SUSTAINED_MAGIC_COMBO_GAIN_TICKS.remove(uuid);
                continue;
            }

            int delay = RESET_DELAYS.getOrDefault(uuid, RESET_TICKS) - 1;
            if (delay > 0) {
                RESET_DELAYS.put(uuid, delay);
                continue;
            }

            clearPlayer(uuid);
            sync(player, 0, false);
        }
    }

    private static boolean isSustainedMagicDamage(DamageSource source) {
        return source.isOf(DamageTypes.MAGIC) || source.isOf(DamageTypes.INDIRECT_MAGIC);
    }

    private static ServerPlayerEntity playerDamageSource(DamageSource source) {
        if (source.getAttacker() instanceof ServerPlayerEntity player) {
            return player;
        }
        if (source.getSource() instanceof ServerPlayerEntity player) {
            return player;
        }
        Entity damageSource = source.getSource();
        if (damageSource instanceof ProjectileEntity projectile && projectile.getOwner() instanceof ServerPlayerEntity player) {
            return player;
        }
        return null;
    }

    private static void sync(ServerPlayerEntity player, int combo) {
        sync(player, combo, false);
    }

    private static void sync(ServerPlayerEntity player, int combo, boolean markCombat) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(combo);
        buf.writeBoolean(markCombat);
        ServerPlayNetworking.send(player, COMBO_SYNC_PACKET, buf);
    }
}
