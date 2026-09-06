package com.ashtonthedev.custommobsspawner.compat;

import com.cleannrooster.rpgmana.api.ManaInterface;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import vazkii.botania.api.mana.ManaPool;

public final class BotaniaManaPoolRpgManaCompat {
    private static final int TICKS_PER_EXCHANGE = 10;
    private static final int POOL_MANA_PER_EXCHANGE = 25;
    private static final double PLAYER_MANA_PER_EXCHANGE = 2.0D;
    private static final double POOL_MANA_PER_PLAYER_MANA = POOL_MANA_PER_EXCHANGE / PLAYER_MANA_PER_EXCHANGE;
    private static final double INSIDE_POOL_MAX_Y_OFFSET = 0.5D;
    private static final double MANA_EPSILON = 0.0001D;

    private BotaniaManaPoolRpgManaCompat() {
    }

    public static void tickWorld(ServerWorld world) {
        if (world.getTime() % TICKS_PER_EXCHANGE != 0L) {
            return;
        }

        for (ServerPlayerEntity player : world.getPlayers()) {
            exchangePoolMana(player);
        }
    }

    private static void exchangePoolMana(ServerPlayerEntity player) {
        if (!player.isAlive() || player.isSpectator() || !(player instanceof ManaInterface manaInterface)) {
            return;
        }

        double missingMana = RpgManaOverchargeCompat.getOverchargeMaxMana(player, manaInterface) - manaInterface.getMana();
        if (missingMana <= MANA_EPSILON) {
            return;
        }

        ManaPool manaPool = findContainingManaPool(player);
        if (manaPool == null || manaPool.getCurrentMana() <= 0) {
            return;
        }

        double playerManaToRestore = Math.min(PLAYER_MANA_PER_EXCHANGE, missingMana);
        int poolManaToConsume = Math.min(
                POOL_MANA_PER_EXCHANGE,
                (int) Math.ceil(playerManaToRestore * POOL_MANA_PER_PLAYER_MANA)
        );
        poolManaToConsume = Math.min(poolManaToConsume, manaPool.getCurrentMana());
        if (poolManaToConsume <= 0) {
            return;
        }

        playerManaToRestore = Math.min(playerManaToRestore, poolManaToConsume / POOL_MANA_PER_PLAYER_MANA);
        if (playerManaToRestore <= MANA_EPSILON) {
            return;
        }

        manaPool.receiveMana(-poolManaToConsume);
        manaInterface.spendMana(playerManaToRestore);
    }

    private static ManaPool findContainingManaPool(ServerPlayerEntity player) {
        BlockPos feetPos = player.getBlockPos();
        if (player.getY() >= feetPos.getY() + INSIDE_POOL_MAX_Y_OFFSET) {
            return null;
        }
        return manaPoolAt(player.getServerWorld(), feetPos);
    }

    private static ManaPool manaPoolAt(ServerWorld world, BlockPos pos) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        return blockEntity instanceof ManaPool manaPool ? manaPool : null;
    }
}
