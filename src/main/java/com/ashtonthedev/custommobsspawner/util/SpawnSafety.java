package com.ashtonthedev.custommobsspawner.util;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldAccess;

public final class SpawnSafety {
    private SpawnSafety() {
    }

    public static boolean isLoaded(WorldAccess world, BlockPos pos) {
        return !world.isOutOfHeightLimit(pos) && isColumnLoaded(world, pos.getX(), pos.getZ());
    }

    public static boolean isLoaded(WorldAccess world, Vec3d pos) {
        return isLoaded(world, BlockPos.ofFloored(pos));
    }

    public static boolean isLoaded(WorldAccess world, Box box) {
        int minChunkX = ChunkSectionPos.getSectionCoord(MathHelper.floor(box.minX));
        int maxChunkX = ChunkSectionPos.getSectionCoord(MathHelper.floor(box.maxX));
        int minChunkZ = ChunkSectionPos.getSectionCoord(MathHelper.floor(box.minZ));
        int maxChunkZ = ChunkSectionPos.getSectionCoord(MathHelper.floor(box.maxZ));

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    return false;
                }
            }
        }

        int minY = MathHelper.floor(box.minY);
        int maxY = MathHelper.floor(box.maxY);
        return minY < world.getTopY() && maxY >= world.getBottomY();
    }

    public static boolean isLoaded(WorldAccess world, Entity entity) {
        return isLoaded(world, entity.getBoundingBox());
    }

    public static boolean isLoadedAround(WorldAccess world, BlockPos pos, int horizontalRadius, int verticalRadius) {
        if (world.isOutOfHeightLimit(pos)) {
            return false;
        }

        int minChunkX = ChunkSectionPos.getSectionCoord(pos.getX() - horizontalRadius);
        int maxChunkX = ChunkSectionPos.getSectionCoord(pos.getX() + horizontalRadius);
        int minChunkZ = ChunkSectionPos.getSectionCoord(pos.getZ() - horizontalRadius);
        int maxChunkZ = ChunkSectionPos.getSectionCoord(pos.getZ() + horizontalRadius);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    return false;
                }
            }
        }

        int minY = pos.getY() - verticalRadius;
        int maxY = pos.getY() + verticalRadius;
        return minY < world.getTopY() && maxY >= world.getBottomY();
    }

    public static boolean isColumnLoaded(WorldAccess world, int blockX, int blockZ) {
        return world.isChunkLoaded(ChunkSectionPos.getSectionCoord(blockX), ChunkSectionPos.getSectionCoord(blockZ));
    }
}
