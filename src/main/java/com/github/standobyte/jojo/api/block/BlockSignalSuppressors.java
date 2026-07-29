package com.github.standobyte.jojo.api.block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Owner-keyed signal suppression callbacks for addon-defined block
 * protection. Callbacks must be server-safe and side-effect free.
 */
public final class BlockSignalSuppressors {
	private static final Map<ResourceLocation, BlockSignalSuppressor>
			SUPPRESSORS = new LinkedHashMap<>();

	private BlockSignalSuppressors() {}

	public static synchronized void register(
			ResourceLocation owner,
			BlockSignalSuppressor suppressor) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(suppressor, "suppressor");
		if (SUPPRESSORS.putIfAbsent(owner, suppressor) != null) {
			throw new IllegalStateException(
					"Duplicate block signal suppressor: " + owner);
		}
	}

	@ApiStatus.Internal
	public static boolean shouldSuppress(
			Level level,
			BlockPos position,
			BlockState state,
			BlockSignalQuery.Kind kind) {
		Objects.requireNonNull(position, "position");
		Objects.requireNonNull(kind, "kind");
		BlockSignalQuery query =
				new BlockSignalQuery(level, position, state, kind);
		List<Map.Entry<ResourceLocation, BlockSignalSuppressor>>
				snapshot;
		synchronized (BlockSignalSuppressors.class) {
			snapshot = new ArrayList<>(SUPPRESSORS.entrySet());
		}
		for (Map.Entry<ResourceLocation, BlockSignalSuppressor>
				entry : snapshot) {
			try {
				if (entry.getValue().suppress(query)) {
					return true;
				}
			}
			catch (RuntimeException e) {
				JojoMod.getLogger().error(
						"Block signal suppressor {} failed.",
						entry.getKey(),
						e);
			}
		}
		return false;
	}

	static synchronized List<ResourceLocation> registeredOwners() {
		return List.copyOf(SUPPRESSORS.keySet());
	}

	static synchronized void resetForTests() {
		SUPPRESSORS.clear();
	}
}
