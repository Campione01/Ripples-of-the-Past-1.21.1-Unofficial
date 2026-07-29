package com.github.standobyte.jojo.api.stonemask;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.block.StoneMaskBlock;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

/**
 * Registers addon Stone Mask blocks with the core Stone Mask block entity.
 * Registrations must happen during mod construction, before block entity
 * types are frozen.
 */
public final class StoneMaskExtensions {
	private static final Map<ResourceLocation,
			Supplier<? extends StoneMaskBlock>> BLOCKS =
					new LinkedHashMap<>();
	private static boolean frozen;

	private StoneMaskExtensions() {}

	public static synchronized void registerBlock(
			ResourceLocation owner,
			Supplier<? extends StoneMaskBlock> block) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(block, "block");
		if (frozen) {
			throw new IllegalStateException(
					"Stone Mask block registration is already frozen");
		}
		if (BLOCKS.putIfAbsent(owner, block) != null) {
			throw new IllegalStateException(
					"Duplicate Stone Mask block owner: " + owner);
		}
	}

	@ApiStatus.Internal
	public static synchronized Block[] resolveBlocks(
			Block... coreBlocks) {
		frozen = true;
		Set<Block> resolved = new LinkedHashSet<>();
		for (Block coreBlock : coreBlocks) {
			resolved.add(Objects.requireNonNull(
					coreBlock, "coreBlock"));
		}
		for (Map.Entry<ResourceLocation,
				Supplier<? extends StoneMaskBlock>> entry
				: BLOCKS.entrySet()) {
			StoneMaskBlock block = entry.getValue().get();
			if (block == null) {
				throw new IllegalStateException(
						"Stone Mask block provider returned null: "
								+ entry.getKey());
			}
			resolved.add(block);
		}
		return resolved.toArray(Block[]::new);
	}

	static synchronized Set<ResourceLocation> registeredOwners() {
		return Set.copyOf(BLOCKS.keySet());
	}

	static synchronized void resetForTests() {
		BLOCKS.clear();
		frozen = false;
	}
}
