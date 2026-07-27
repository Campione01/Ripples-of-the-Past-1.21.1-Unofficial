package com.github.standobyte.jojo.api.stand;

import java.util.Objects;
import java.util.Optional;

import com.github.standobyte.jojo.powersystem.standpower.StandInstance;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;

import net.minecraft.resources.ResourceLocation;

/**
 * Server-authoritative Stand ownership changes for addons.
 * <p>
 * Item consumption, inventory delivery, drops, projectiles, and animation
 * staging remain the caller's responsibility.
 */
public final class StandPowerTransitions {
	private StandPowerTransitions() {}

	public static Result insert(StandPower power, StandInstance replacement) {
		Objects.requireNonNull(power, "power");
		return insert(new StandPowerAccess(power), replacement);
	}

	public static Result extract(StandPower power, ResourceLocation expectedCurrent) {
		Objects.requireNonNull(power, "power");
		return extract(new StandPowerAccess(power), expectedCurrent);
	}

	public static Result replace(StandPower power, ResourceLocation expectedCurrent, StandInstance replacement) {
		Objects.requireNonNull(power, "power");
		return replace(new StandPowerAccess(power), expectedCurrent, replacement);
	}

	static Result insert(PowerAccess power, StandInstance replacement) {
		Objects.requireNonNull(power, "power");
		if (!power.isServerSide()) {
			return Result.failed(Status.NOT_SERVER_SIDE);
		}
		if (replacement == null || !replacement.standExists()) {
			return Result.failed(Status.INVALID_REPLACEMENT);
		}
		if (power.getStandInstance().isPresent()) {
			return Result.failed(Status.STAND_ALREADY_PRESENT);
		}

		StandInstance committed = replacement.copy();
		power.setStandInstance(Optional.of(committed));
		return Result.applied(Optional.empty(), Optional.of(committed));
	}

	static Result extract(PowerAccess power, ResourceLocation expectedCurrent) {
		Objects.requireNonNull(power, "power");
		if (!power.isServerSide()) {
			return Result.failed(Status.NOT_SERVER_SIDE);
		}
		Objects.requireNonNull(expectedCurrent, "expectedCurrent");

		Optional<StandInstance> current = power.getStandInstance();
		if (current.isEmpty()) {
			return Result.failed(Status.NO_STAND);
		}
		StandInstance previous = current.get();
		if (!previous.getStandId().equals(expectedCurrent)) {
			return Result.failed(Status.SOURCE_MISMATCH);
		}

		StandInstance previousSnapshot = previous.copy();
		power.setStandInstance(Optional.empty());
		return Result.applied(Optional.of(previousSnapshot), Optional.empty());
	}

	static Result replace(PowerAccess power, ResourceLocation expectedCurrent, StandInstance replacement) {
		Objects.requireNonNull(power, "power");
		if (!power.isServerSide()) {
			return Result.failed(Status.NOT_SERVER_SIDE);
		}
		Objects.requireNonNull(expectedCurrent, "expectedCurrent");
		if (replacement == null || !replacement.standExists()) {
			return Result.failed(Status.INVALID_REPLACEMENT);
		}

		Optional<StandInstance> current = power.getStandInstance();
		if (current.isEmpty()) {
			return Result.failed(Status.NO_STAND);
		}
		StandInstance previous = current.get();
		if (!previous.getStandId().equals(expectedCurrent)) {
			return Result.failed(Status.SOURCE_MISMATCH);
		}

		StandInstance previousSnapshot = previous.copy();
		StandInstance committed = replacement.copy();
		power.setStandInstance(Optional.of(committed));
		return Result.applied(Optional.of(previousSnapshot), Optional.of(committed));
	}

	public record Result(
			Status status,
			Optional<StandInstance> previous,
			Optional<StandInstance> current) {

		public Result {
			Objects.requireNonNull(status, "status");
			previous = copy(previous, "previous");
			current = copy(current, "current");
		}

		private static Result applied(Optional<StandInstance> previous, Optional<StandInstance> current) {
			return new Result(Status.APPLIED, previous, current);
		}

		private static Result failed(Status status) {
			return new Result(status, Optional.empty(), Optional.empty());
		}

		private static Optional<StandInstance> copy(Optional<StandInstance> stand, String name) {
			return Objects.requireNonNull(stand, name).map(StandInstance::copy);
		}

		public boolean applied() {
			return status == Status.APPLIED;
		}

		@Override
		public Optional<StandInstance> previous() {
			return copy(previous, "previous");
		}

		@Override
		public Optional<StandInstance> current() {
			return copy(current, "current");
		}
	}

	public enum Status {
		APPLIED,
		NOT_SERVER_SIDE,
		INVALID_REPLACEMENT,
		NO_STAND,
		STAND_ALREADY_PRESENT,
		SOURCE_MISMATCH
	}

	interface PowerAccess {
		boolean isServerSide();
		Optional<StandInstance> getStandInstance();
		void setStandInstance(Optional<StandInstance> standInstance);
	}

	private record StandPowerAccess(StandPower power) implements PowerAccess {
		@Override
		public boolean isServerSide() {
			var user = power.getUser();
			return user != null && !user.level().isClientSide();
		}

		@Override
		public Optional<StandInstance> getStandInstance() {
			return power.getStandInstance();
		}

		@Override
		public void setStandInstance(Optional<StandInstance> standInstance) {
			power.setStandInstance(standInstance);
		}
	}
}
