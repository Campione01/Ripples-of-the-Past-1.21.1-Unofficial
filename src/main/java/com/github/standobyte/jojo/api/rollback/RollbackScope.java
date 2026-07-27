package com.github.standobyte.jojo.api.rollback;

import java.util.Objects;

import net.minecraft.core.BlockPos;

/**
 * One immutable, single-dimension spatial declaration.
 */
public record RollbackScope(BlockPos min, BlockPos max) {
	public RollbackScope {
		Objects.requireNonNull(min, "min");
		Objects.requireNonNull(max, "max");
		min = min.immutable();
		max = max.immutable();
		if (min.getX() > max.getX()
				|| min.getY() > max.getY()
				|| min.getZ() > max.getZ()) {
			throw new IllegalArgumentException("rollback scope bounds are reversed");
		}
	}

	public static RollbackScope around(
			BlockPos center, int horizontalRadius, int verticalRadius) {
		Objects.requireNonNull(center, "center");
		if (horizontalRadius < 0 || verticalRadius < 0) {
			throw new IllegalArgumentException("rollback scope radius cannot be negative");
		}
		try {
			return new RollbackScope(
					new BlockPos(
							Math.subtractExact(center.getX(), horizontalRadius),
							Math.subtractExact(center.getY(), verticalRadius),
							Math.subtractExact(center.getZ(), horizontalRadius)),
					new BlockPos(
							Math.addExact(center.getX(), horizontalRadius),
							Math.addExact(center.getY(), verticalRadius),
							Math.addExact(center.getZ(), horizontalRadius)));
		}
		catch (ArithmeticException overflow) {
			throw new IllegalArgumentException("rollback scope overflows block coordinates",
					overflow);
		}
	}

	public boolean contains(BlockPos pos) {
		return pos.getX() >= min.getX() && pos.getX() <= max.getX()
				&& pos.getY() >= min.getY() && pos.getY() <= max.getY()
				&& pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
	}

	public int minChunkX() {
		return min.getX() >> 4;
	}

	public int maxChunkX() {
		return max.getX() >> 4;
	}

	public int minChunkZ() {
		return min.getZ() >> 4;
	}

	public int maxChunkZ() {
		return max.getZ() >> 4;
	}

	public long chunkCount() {
		long xCount = (long) maxChunkX() - minChunkX() + 1L;
		long zCount = (long) maxChunkZ() - minChunkZ() + 1L;
		try {
			return Math.multiplyExact(xCount, zCount);
		}
		catch (ArithmeticException overflow) {
			return Long.MAX_VALUE;
		}
	}
}
