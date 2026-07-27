package com.github.standobyte.jojo.api.timestop;

import java.util.Objects;

import com.github.standobyte.jojo.subsystems.timestop.TimeStopState;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Server-side lifecycle events for one time-stop instance.
 *
 * <p>These events are fired on the {@link NeoForge#EVENT_BUS}. The mutable
 * {@link PreStart} event runs before stamina, status effects, world freezing,
 * or synchronization are committed. {@link Added} and {@link Removed} run
 * after the corresponding state change has completed.</p>
 */
public abstract class TimeStopLifecycleEvent extends Event {
	public static final int MAX_STARTUP_DELAY_TICKS = 20 * 60;
	public static final int MAX_TOTAL_TICKS =
			Integer.MAX_VALUE - MAX_STARTUP_DELAY_TICKS;

	private final ServerLevel level;
	private final TimeStopState.Instance instance;

	protected TimeStopLifecycleEvent(
			ServerLevel level, TimeStopState.Instance instance) {
		this.level = Objects.requireNonNull(level);
		this.instance = Objects.requireNonNull(instance);
	}

	public final ServerLevel getLevel() {
		return level;
	}

	public TimeStopState.Instance getInstance() {
		return instance;
	}

	public static final class PreStart
			extends TimeStopLifecycleEvent implements ICancellableEvent {
		private TimeStopState.Instance proposedInstance;

		public PreStart(
				ServerLevel level, TimeStopState.Instance proposedInstance) {
			super(level, validate(proposedInstance));
			this.proposedInstance = proposedInstance;
		}

		@Override
		public TimeStopState.Instance getInstance() {
			return proposedInstance;
		}

		/**
		 * Replaces the proposed instance while preserving its state-map and
		 * owner identity. Subscribers may change timing, range, center,
		 * visuals, skin metadata, and stamina cost. Timing must satisfy
		 * {@code 0 < ticksLeft <= totalTicks <= MAX_TOTAL_TICKS}; startup
		 * delay is bounded to {@link #MAX_STARTUP_DELAY_TICKS}, range is
		 * non-negative, and stamina cost must be finite and non-negative.
		 */
		public void setInstance(TimeStopState.Instance proposedInstance) {
			validate(proposedInstance);
			TimeStopState.Instance original = super.getInstance();
			if (proposedInstance.id() != original.id()
					|| proposedInstance.userId() != original.userId()) {
				throw new IllegalArgumentException(
						"A time-stop pre-start event cannot change its instance or user ID.");
			}
			this.proposedInstance = proposedInstance;
		}

		private static TimeStopState.Instance validate(
				TimeStopState.Instance instance) {
			Objects.requireNonNull(instance);
			if (instance.ticksLeft() <= 0
					|| instance.totalTicks() <= 0
					|| instance.ticksLeft() > instance.totalTicks()
					|| instance.totalTicks() > MAX_TOTAL_TICKS
					|| instance.ticksPassed() > 0
					|| instance.ticksPassed()
							< -MAX_STARTUP_DELAY_TICKS
					|| instance.chunkRange() < 0
					|| instance.centerPos() == null
					|| instance.visualRoute() == null
					|| instance.visualRoute().isBlank()
					|| !Float.isFinite(instance.staminaCostTick())
					|| instance.staminaCostTick() < 0.0F) {
				throw new IllegalArgumentException(
						"A proposed time-stop instance has invalid timing, range, visuals, or stamina cost.");
			}
			return instance;
		}
	}

	public static final class Added extends TimeStopLifecycleEvent {
		public Added(ServerLevel level, TimeStopState.Instance instance) {
			super(level, instance);
		}
	}

	public static final class Removed extends TimeStopLifecycleEvent {
		private final RemovalReason reason;

		public Removed(
				ServerLevel level,
				TimeStopState.Instance instance,
				RemovalReason reason) {
			super(level, instance);
			this.reason = Objects.requireNonNull(reason);
		}

		public RemovalReason getReason() {
			return reason;
		}
	}

	public enum RemovalReason {
		EXPIRED,
		MANUAL_RESUME,
		INTERRUPTED,
		EXPLICIT,
		RESET,
		REPLACED
	}
}
