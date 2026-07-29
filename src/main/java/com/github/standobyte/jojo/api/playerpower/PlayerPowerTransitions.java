package com.github.standobyte.jojo.api.playerpower;

import java.util.Objects;
import java.util.Optional;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.core.JojoRegistries;
import com.github.standobyte.jojo.powersystem.PowerData;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPowerType;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * Non-destructive temporary PlayerPower transitions for addon-owned
 * conversion lifecycles.
 *
 * <p>The snapshot contains only the previous type's data. It intentionally
 * does not invoke the previous type's normal clear callback. Restoring
 * likewise does not invoke either type's normal clear/acquisition callbacks.
 * Narrow passive-state suspension hooks bridge persistent-effect differences
 * between the 1.16.5 and 1.21.1 power implementations. Retained built-in
 * ability cooldowns continue to advance on the temporary source.</p>
 */
public final class PlayerPowerTransitions {
	private static final String TYPE_KEY = "PowerType";
	private static final String DATA_KEY = "PowerData";

	private PlayerPowerTransitions() {}

	public static Optional<Snapshot> forceTemporary(
			PlayerPower power,
			PlayerPowerType<?> temporaryType) {
		Objects.requireNonNull(power, "power");
		Objects.requireNonNull(temporaryType, "temporaryType");
		if (power.getUser().level().isClientSide()) {
			return Optional.empty();
		}

		PlayerPowerType<?> previousType = power.getPowerType();
		PowerData previousData = power.getCurTypeData();
		if (previousType == null || previousData == null
				|| previousType == temporaryType) {
			return Optional.empty();
		}

		Snapshot snapshot = new Snapshot(
				previousType.getId(),
				previousData.serializeNBT(
						power.getUser().registryAccess()));
		return power.beginTemporaryTransition(temporaryType)
				? Optional.of(snapshot)
				: Optional.empty();
	}

	public static boolean restoreAfterDeath(
			PlayerPower target,
			PlayerPower temporarySource,
			Snapshot snapshot) {
		Objects.requireNonNull(target, "target");
		Objects.requireNonNull(temporarySource, "temporarySource");
		Objects.requireNonNull(snapshot, "snapshot");
		if (target.getUser().level().isClientSide()
				|| temporarySource.getUser().level().isClientSide()) {
			return false;
		}
		PlayerPowerType<?> restoredType =
				JojoRegistries.PLAYER_POWER_TYPES_REG.get(
						snapshot.powerTypeId());
		if (restoredType == null) {
			return false;
		}
		PlayerPowerType<?> temporaryType =
				temporarySource.getPowerType();
		CompoundTag retainedDataNbt = snapshot.powerDataNbt();
		try {
			retainedDataNbt = temporarySource
					.serializeRetainedTemporaryData(restoredType)
					.orElse(retainedDataNbt);
		}
		catch (RuntimeException error) {
			JojoMod.getLogger().error(
					"Failed to serialize retained PlayerPower {}; using its transition snapshot",
					restoredType.getId(),
					error);
		}
		return target.restoreTemporaryTypeData(
				restoredType,
				retainedDataNbt,
				temporarySource.getLeapCooldown(),
				temporarySource,
				temporaryType);
	}

	/**
	 * Restores the retained type on the same living entity.
	 */
	public static boolean restore(PlayerPower power) {
		Objects.requireNonNull(power, "power");
		return power.endTemporaryTransition();
	}

	public record Snapshot(
			ResourceLocation powerTypeId,
			CompoundTag powerDataNbt) {
		public Snapshot {
			Objects.requireNonNull(powerTypeId, "powerTypeId");
			Objects.requireNonNull(powerDataNbt, "powerDataNbt");
			powerDataNbt = powerDataNbt.copy();
		}

		@Override
		public CompoundTag powerDataNbt() {
			return powerDataNbt.copy();
		}

		public CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.putString(TYPE_KEY, powerTypeId.toString());
			tag.put(DATA_KEY, powerDataNbt.copy());
			return tag;
		}

		public static Optional<Snapshot> load(CompoundTag tag) {
			if (tag == null
					|| !tag.contains(TYPE_KEY, Tag.TAG_STRING)
					|| !tag.contains(DATA_KEY, Tag.TAG_COMPOUND)) {
				return Optional.empty();
			}
			try {
				ResourceLocation typeId = ResourceLocation.parse(
						tag.getString(TYPE_KEY));
				return Optional.of(new Snapshot(
						typeId, tag.getCompound(DATA_KEY)));
			}
			catch (RuntimeException error) {
				return Optional.empty();
			}
		}
	}
}
