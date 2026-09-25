package rotp.core.api.stand;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.ApiStatus;

import rotp.core.core.JojoMod;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * Owner-keyed, deny-dominant vetoes for entering Stand manual control.
 *
 * <p>Queried on both logical sides by the manual-control toggle. Leaving
 * manual control is never vetoed. Provider failures leave entry allowed.</p>
 */
public final class ManualControlBlockers {
	private static final Map<ResourceLocation, ManualControlBlocker>
			BLOCKERS = new LinkedHashMap<>();

	private ManualControlBlockers() {}

	public static synchronized void register(
			ResourceLocation owner,
			ManualControlBlocker blocker) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(blocker, "blocker");
		if (BLOCKERS.putIfAbsent(owner, blocker) != null) {
			throw new IllegalStateException(
					"Duplicate manual control blocker: " + owner);
		}
	}

	@ApiStatus.Internal
	public static boolean isBlocked(
			LivingEntity user,
			StandEntity stand) {
		Objects.requireNonNull(user, "user");
		List<Map.Entry<ResourceLocation, ManualControlBlocker>>
				snapshot;
		synchronized (ManualControlBlockers.class) {
			snapshot = new ArrayList<>(BLOCKERS.entrySet());
		}
		for (Map.Entry<ResourceLocation, ManualControlBlocker>
				entry : snapshot) {
			try {
				if (entry.getValue().isBlocked(user, stand)) {
					return true;
				}
			}
			catch (RuntimeException error) {
				JojoMod.getLogger().error(
						"Manual control blocker {} failed.",
						entry.getKey(),
						error);
			}
		}
		return false;
	}
}
