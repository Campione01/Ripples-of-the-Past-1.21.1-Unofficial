package com.github.standobyte.jojo.api.stand;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Read-only observations from the production Stand manual-movement route.
 * Production threads only make bounded non-blocking queue offers. Observer
 * callbacks run exclusively when the registration owner drains its queue.
 */
public final class StandManualMovementObservers {
	public static final int DEFAULT_QUEUE_CAPACITY = 4_096;
	public static final int MAX_QUEUE_CAPACITY = 65_536;

	private static final Object REGISTRY_LOCK = new Object();
	private static final Map<ResourceLocation, Registration> REGISTRATIONS =
			new LinkedHashMap<>();
	private static final AtomicLong NEXT_REGISTRATION_ID = new AtomicLong();
	private static final AtomicLong NEXT_EVENT_SEQUENCE = new AtomicLong();
	private static volatile Registration[] activeRegistrations =
			new Registration[0];

	private StandManualMovementObservers() {}

	public static Registration register(
			ResourceLocation owner, Observer observer) {
		return register(owner, DEFAULT_QUEUE_CAPACITY, observer);
	}

	public static Registration register(
			ResourceLocation owner, int queueCapacity, Observer observer) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(observer, "observer");
		if (queueCapacity < 1 || queueCapacity > MAX_QUEUE_CAPACITY) {
			throw new IllegalArgumentException(
					"Stand manual-movement observer queue capacity must be "
							+ "between 1 and " + MAX_QUEUE_CAPACITY + ".");
		}
		Registration registration = new Registration(
				NEXT_REGISTRATION_ID.incrementAndGet(), owner,
				queueCapacity, observer);
		synchronized (REGISTRY_LOCK) {
			if (REGISTRATIONS.putIfAbsent(owner, registration) != null) {
				throw new IllegalStateException(
						"Duplicate Stand manual-movement observer: " + owner);
			}
			refreshActiveRegistrations();
		}
		return registration;
	}

	public static boolean hasObservers() {
		return activeRegistrations.length != 0;
	}

	public static boolean isRegistered(ResourceLocation owner) {
		synchronized (REGISTRY_LOCK) {
			return REGISTRATIONS.containsKey(owner);
		}
	}

	public static void publish(
			LogicalSide logicalSide, Level level, Observation observation) {
		Objects.requireNonNull(level, "level");
		publish(logicalSide, level.dimension().location().toString(),
				level.getGameTime(), observation);
	}

	public static void publish(
			LogicalSide logicalSide,
			String dimension,
			long gameTime,
			Observation observation) {
		Registration[] registrations = activeRegistrations;
		if (registrations.length == 0) {
			return;
		}
		Objects.requireNonNull(logicalSide, "logicalSide");
		Objects.requireNonNull(dimension, "dimension");
		Objects.requireNonNull(observation, "observation");
		EventEnvelope envelope = new EventEnvelope(
				NEXT_EVENT_SEQUENCE.incrementAndGet(), 0L, logicalSide,
				dimension, gameTime, observation);
		for (Registration registration : registrations) {
			registration.offer(envelope);
		}
	}

	public static String stableActionId(
			@Nullable EntityActionInstance action) {
		if (action == null || action.ability == null) {
			return "none";
		}
		AbilityId abilityId = action.ability.getAbilityId();
		if (abilityId != null) {
			String powerClass = abilityId.powerClass() != null
					? abilityId.powerClass().toString() : "none";
			String powerType = abilityId.powerTypeId() != null
					? abilityId.powerTypeId().toString() : "none";
			return powerClass + ":" + powerType + ":"
					+ abilityId.nameInMoveset();
		}
		return action.ability.getClass().getName();
	}

	/**
	 * Keeps the controller's no-observer path aligned with the production
	 * baseline while still capturing idle and blocked decisions when observed.
	 */
	public static boolean shouldResolveControllerAction(
			boolean observing, boolean hasInput, boolean previousInput) {
		return observing || hasInput && previousInput;
	}

	private static void refreshActiveRegistrations() {
		activeRegistrations = REGISTRATIONS.values()
				.toArray(Registration[]::new);
	}

	@FunctionalInterface
	public interface Observer {
		void onObservation(EventEnvelope envelope);
	}

	public sealed interface Observation permits ControllerBinding,
			ControllerDecision, PacketAttempt, PacketReceipt,
			ServerPacketResult, StandMoveResult {}

	/**
	 * {@code sequence} is process-local diagnostic order. It cannot be used as
	 * an exact client/server correlation key across separate JVMs.
	 * {@code acceptedOrdinal} is assigned by one registration's successful
	 * capacity reservation and is the FIFO order used by that registration's
	 * callbacks, including events published by different logical sides in the
	 * same JVM. A gap can only accompany an explicitly counted cancellation
	 * in a failed close contract.
	 */
	public record EventEnvelope(
			long sequence,
			long acceptedOrdinal,
			LogicalSide logicalSide,
			String dimension,
			long gameTime,
			Observation observation) {
		private EventEnvelope acceptedBy(long ordinal) {
			return new EventEnvelope(sequence, ordinal, logicalSide,
					dimension, gameTime, observation);
		}
	}

	public record ControllerBinding(
			@Nullable UUID ownerUuid,
			UUID controllerUuid,
			boolean bound) implements Observation {}

	public record ControllerDecision(
			@Nullable UUID ownerUuid,
			UUID controllerUuid,
			float rawLeftImpulse,
			float rawForwardImpulse,
			boolean rawJumping,
			boolean rawSneaking,
			float filteredLeftImpulse,
			float filteredForwardImpulse,
			boolean filteredJumping,
			boolean filteredSneaking,
			boolean manuallyControlled,
			boolean canMoveManually,
			String actionId,
			boolean directionalInputFiltered,
			boolean previousInput,
			boolean hasInput,
			double movementSpeed,
			float manualMovementSpeed,
			float actionWalkSpeed,
			double motionX,
			double motionY,
			double motionZ) implements Observation {}

	public record PacketAttempt(
			@Nullable UUID ownerUuid,
			UUID controllerUuid,
			double x,
			double y,
			double z,
			float xRot,
			float yRot,
			boolean deltaResetRequested) implements Observation {}

	public record PacketReceipt(
			UUID ownerUuid,
			double x,
			double y,
			double z,
			float xRot,
			float yRot,
			boolean deltaResetRequested) implements Observation {}

	public record ServerPacketResult(
			ServerDecision decision,
			UUID ownerUuid,
			@Nullable UUID standUuid,
			boolean manuallyControlled,
			boolean canMoveManually,
			String actionId,
			boolean requestRejected,
			boolean deltaResetRequested,
			boolean deltaResetApplied,
			double preX,
			double preY,
			double preZ,
			double requestedX,
			double requestedY,
			double requestedZ,
			double postX,
			double postY,
			double postZ,
			double requestedDeltaSqr,
			double actualDeltaSqr,
			boolean moveInvoked,
			boolean correctionSent) implements Observation {}

	public record StandMoveResult(
			MovePath movePath,
			@Nullable UUID ownerUuid,
			UUID standUuid,
			double preX,
			double preY,
			double preZ,
			double requestedX,
			double requestedY,
			double requestedZ,
			double postSuperX,
			double postSuperY,
			double postSuperZ,
			double postX,
			double postY,
			double postZ,
			double requestedDeltaSqr,
			double actualDeltaSqr,
			boolean horizontalCollision,
			boolean verticalCollision,
			double ownerDistanceAfterSuperBeforeClamp,
			boolean rangeCorrectionApplied,
			double rangeCorrectionDeltaSqr,
			double finalOwnerDistance,
			double maxMovementRange) implements Observation {}

	public enum LogicalSide {
		CLIENT,
		SERVER
	}

	public enum MovePath {
		CLIENT_SELF,
		CLIENT_OTHER,
		SERVER_PLAYER,
		SERVER_OTHER
	}

	public enum ServerDecision {
		INVALID,
		NO_POWER,
		NO_STAND,
		MANUAL_LOCKED,
		TOO_QUICK,
		APPLIED
	}

	public record DrainResult(
			long registrationId,
			int drainedCount,
			int remainingCount,
			long acceptedCount,
			long deliveredCount,
			long overflowCount,
			long droppedCount,
			long lastAcceptedSequence,
			long callbackFailureCount) {}

	public record CloseDrainResult(
			long registrationId,
			long closeBarrierSequence,
			int drainedCount,
			int remainingCount,
			long acceptedCount,
			long deliveredCount,
			long overflowCount,
			long droppedCount,
			long lastAcceptedSequence,
			boolean closed,
			long callbackFailureCount,
			boolean lossless,
			boolean failed) {
		public CloseDrainResult {
			boolean contractFailed = !closed
					|| overflowCount != 0L
					|| droppedCount != 0L
					|| callbackFailureCount != 0L
					|| remainingCount != 0
					|| acceptedCount != deliveredCount;
			if (failed != contractFailed
					|| lossless == contractFailed) {
				throw new IllegalArgumentException(
						"Inconsistent Stand manual-movement close "
								+ "delivery contract.");
			}
		}
	}

	public static final class Registration implements AutoCloseable {
		private static final long CLOSE_LOCK_TIMEOUT_SECONDS = 5L;
		private static final long PRODUCER_GRACE_NANOS =
				TimeUnit.MILLISECONDS.toNanos(10L);

		private final long registrationId;
		private final ResourceLocation owner;
		private final int queueCapacity;
		private final Observer observer;
		private final AtomicReferenceArray<QueueSlot> queue;
		private final AtomicReference<QueueState> queueState =
				new AtomicReference<>(QueueState.active());
		private final ReentrantLock drainMutex = new ReentrantLock();
		private final ReentrantLock closeMutex = new ReentrantLock();
		private final AtomicLong acceptedCount = new AtomicLong();
		private final AtomicLong deliveredCount = new AtomicLong();
		private final AtomicLong callbackFailureCount = new AtomicLong();
		private final AtomicLong lastAcceptedSequence = new AtomicLong(-1L);
		private final AtomicBoolean deferredCloseRequested =
				new AtomicBoolean();
		private long headOrdinal = 1L;
		@Nullable
		private volatile Thread callbackThread;
		@Nullable
		private volatile CloseDrainResult closeResult;

		private Registration(
				long registrationId,
				ResourceLocation owner,
				int queueCapacity,
				Observer observer) {
			this.registrationId = registrationId;
			this.owner = owner;
			this.queueCapacity = queueCapacity;
			this.observer = observer;
			this.queue = new AtomicReferenceArray<>(queueCapacity);
		}

		public long registrationId() {
			return registrationId;
		}

		public ResourceLocation owner() {
			return owner;
		}

		public int queueCapacity() {
			return queueCapacity;
		}

		public boolean isClosed() {
			return queueState.get().lifecycle == Lifecycle.CLOSED;
		}

		public boolean isClosing() {
			return queueState.get().lifecycle == Lifecycle.CLOSING;
		}

		public long overflowCount() {
			return queueState.get().overflowCount;
		}

		public long droppedCount() {
			return queueState.get().droppedCount;
		}

		public long callbackFailureCount() {
			return callbackFailureCount.get();
		}

		public int pendingCount() {
			return queueState.get().occupied;
		}

		public DrainResult drain() {
			if (callbackThread == Thread.currentThread()) {
				throw new IllegalStateException(
						"An observer callback cannot recursively drain its "
								+ "registration.");
			}
			boolean closeAfterDrain;
			DrainResult result;
			drainMutex.lock();
			try {
				int drained = drainQueued(queueState.get().nextOrdinal);
				result = drainResult(drained);
				closeAfterDrain = deferredCloseRequested.getAndSet(false);
			}
			finally {
				drainMutex.unlock();
			}
			if (closeAfterDrain) {
				closeAndFreeze();
			}
			return result;
		}

		/**
		 * Stops publication and freezes the queue without invoking the observer.
		 * Callers must drain successfully before closing for a lossless result;
		 * every event still pending at the close barrier is counted as dropped.
		 */
		public CloseDrainResult closeAndFreeze() {
			if (callbackThread == Thread.currentThread()) {
				deferredCloseRequested.set(true);
				throw new IllegalStateException(
						"closeAndFreeze cannot return a final result from its "
								+ "own observer callback; close was deferred.");
			}
			CloseDrainResult previous = closeResult;
			if (previous != null) {
				return previous;
			}
			lockBoundedly(closeMutex, "close");
			try {
				previous = closeResult;
				if (previous != null) {
					return previous;
				}
				transitionToClosingAndUnregister();
				lockBoundedly(drainMutex, "drain");
				int drained;
				try {
					long limit = queueState.get().nextOrdinal;
					awaitProducerGrace();
					settleOutstandingReservations(limit);
					awaitNoInFlightProducers();
					freezeQueued(limit);
					drained = 0;
					transitionToClosed();
				}
				finally {
					drainMutex.unlock();
				}
				QueueState frozen = queueState.get();
				long accepted = acceptedCount.get();
				long delivered = deliveredCount.get();
				long callbackFailures = callbackFailureCount.get();
				boolean lossless = frozen.lifecycle == Lifecycle.CLOSED
						&& frozen.occupied == 0
						&& frozen.overflowCount == 0L
						&& frozen.droppedCount == 0L
						&& callbackFailures == 0L
						&& accepted == delivered;
				CloseDrainResult result = new CloseDrainResult(
						registrationId, frozen.lastOfferedSequence,
						drained, frozen.occupied, accepted, delivered,
						frozen.overflowCount, frozen.droppedCount,
						lastAcceptedSequence.get(), true,
						callbackFailures, lossless, !lossless);
				closeResult = result;
				deferredCloseRequested.set(false);
				return result;
			}
			finally {
				closeMutex.unlock();
			}
		}

		/**
		 * @deprecated Use {@link #closeAndFreeze()}; this alias has identical
		 * bounded freeze semantics and never invokes the observer.
		 */
		@Deprecated(forRemoval = false)
		public CloseDrainResult closeAndDrain() {
			return closeAndFreeze();
		}

		@Override
		public void close() {
			if (callbackThread == Thread.currentThread()) {
				deferredCloseRequested.set(true);
				return;
			}
			closeAndFreeze();
		}

		private void offer(EventEnvelope envelope) {
			QueueSlot slot = reserve(envelope);
			if (slot == null) {
				return;
			}
			commitReserved(slot);
		}

		private void commitReserved(QueueSlot slot) {
			int index = queueIndex(slot.ordinal);
			if (queue.compareAndSet(index, null, slot)) {
				settleAccepted(slot);
			}
			else {
				QueueSlot existing = queue.get(index);
				if (existing != null && existing.ordinal == slot.ordinal) {
					if (existing.cancelled) {
						settleCancelled(existing);
					}
					else {
						settleAccepted(existing);
					}
				}
				else {
					settleUnqueuedCancellation(slot);
					JojoMod.getLogger().error(
							"Stand manual-movement observer {} could not "
									+ "publish reserved ordinal {}.",
							owner, slot.ordinal);
				}
			}
		}

		@Nullable
		private QueueSlot reserve(EventEnvelope envelope) {
			while (true) {
				QueueState current = queueState.get();
				if (current.lifecycle != Lifecycle.ACTIVE) {
					return null;
				}
				long lastOfferedSequence = Math.max(
						current.lastOfferedSequence, envelope.sequence());
				if (current.occupied >= queueCapacity) {
					QueueState overflow = new QueueState(
							Lifecycle.ACTIVE, current.inFlight,
							current.occupied, current.nextOrdinal,
							current.overflowCount + 1L,
							current.droppedCount + 1L,
							lastOfferedSequence);
					if (queueState.compareAndSet(current, overflow)) {
						return null;
					}
					continue;
				}
				long ordinal = current.nextOrdinal;
				QueueState reserved = new QueueState(
						Lifecycle.ACTIVE, current.inFlight + 1,
						current.occupied + 1, ordinal + 1L,
						current.overflowCount, current.droppedCount,
						lastOfferedSequence);
				if (queueState.compareAndSet(current, reserved)) {
					return QueueSlot.accepted(
							ordinal, envelope.acceptedBy(ordinal));
				}
			}
		}

		private int drainQueued(long limitExclusive) {
			int drained = 0;
			while (headOrdinal < limitExclusive) {
				int index = queueIndex(headOrdinal);
				QueueSlot slot = queue.get(index);
				if (slot == null || slot.ordinal != headOrdinal) {
					break;
				}
				if (slot.cancelled) {
					settleCancelled(slot);
					headOrdinal++;
					continue;
				}
				settleAccepted(slot);
				if (!queue.compareAndSet(index, slot, null)) {
					throw new IllegalStateException(
							"Stand manual-movement observer queue changed "
									+ "under its serialized drain.");
				}
				headOrdinal++;
				releaseOccupiedAfterDrain();
				callbackThread = Thread.currentThread();
				try {
					observer.onObservation(slot.envelope);
					deliveredCount.incrementAndGet();
				}
				catch (Throwable error) {
					callbackFailureCount.incrementAndGet();
					JojoMod.getLogger().error(
							"Stand manual-movement observer {} failed while "
									+ "draining event {}.",
							owner, slot.envelope.sequence(), error);
				}
				finally {
					callbackThread = null;
				}
				drained++;
			}
			return drained;
		}

		private void freezeQueued(long limitExclusive) {
			while (headOrdinal < limitExclusive) {
				int index = queueIndex(headOrdinal);
				QueueSlot slot = queue.get(index);
				if (slot == null || slot.ordinal != headOrdinal) {
					throw new IllegalStateException(
							"Stand manual-movement reservation slot "
									+ "mismatch while freezing ordinal "
									+ headOrdinal + ".");
				}
				if (!slot.cancelled) {
					settleAccepted(slot);
					if (!queue.compareAndSet(index, slot, null)) {
						throw new IllegalStateException(
								"Stand manual-movement observer queue changed "
										+ "under its serialized freeze.");
					}
					releaseOccupiedAfterFreeze();
				}
				headOrdinal++;
			}
		}

		private void settleAccepted(QueueSlot slot) {
			if (!slot.settled.compareAndSet(false, true)) {
				return;
			}
			acceptedCount.incrementAndGet();
			lastAcceptedSequence.accumulateAndGet(
					slot.envelope.sequence(), Math::max);
			releaseInFlight(false);
		}

		private void settleCancelled(QueueSlot slot) {
			if (!slot.settled.compareAndSet(false, true)) {
				return;
			}
			releaseInFlight(true);
		}

		private void settleUnqueuedCancellation(QueueSlot slot) {
			if (!slot.settled.compareAndSet(false, true)) {
				return;
			}
			releaseInFlight(true);
		}

		private void releaseInFlight(boolean dropped) {
			while (true) {
				QueueState current = queueState.get();
				if (current.inFlight <= 0) {
					throw new IllegalStateException(
							"Stand manual-movement producer reservation "
									+ "underflow.");
				}
				QueueState released = new QueueState(
						current.lifecycle, current.inFlight - 1,
						current.occupied - (dropped ? 1 : 0),
						current.nextOrdinal, current.overflowCount,
						current.droppedCount + (dropped ? 1L : 0L),
						current.lastOfferedSequence);
				if (queueState.compareAndSet(current, released)) {
					return;
				}
			}
		}

		private void releaseOccupiedAfterDrain() {
			while (true) {
				QueueState current = queueState.get();
				if (current.occupied <= 0) {
					throw new IllegalStateException(
							"Stand manual-movement queue occupancy "
									+ "underflow.");
				}
				QueueState released = new QueueState(
						current.lifecycle, current.inFlight,
						current.occupied - 1, current.nextOrdinal,
						current.overflowCount, current.droppedCount,
						current.lastOfferedSequence);
				if (queueState.compareAndSet(current, released)) {
					return;
				}
			}
		}

		private void releaseOccupiedAfterFreeze() {
			while (true) {
				QueueState current = queueState.get();
				if (current.occupied <= 0) {
					throw new IllegalStateException(
							"Stand manual-movement queue occupancy "
									+ "underflow while freezing.");
				}
				QueueState released = new QueueState(
						current.lifecycle, current.inFlight,
						current.occupied - 1, current.nextOrdinal,
						current.overflowCount,
						current.droppedCount + 1L,
						current.lastOfferedSequence);
				if (queueState.compareAndSet(current, released)) {
					return;
				}
			}
		}

		private void transitionToClosingAndUnregister() {
			synchronized (REGISTRY_LOCK) {
				while (true) {
					QueueState current = queueState.get();
					if (current.lifecycle != Lifecycle.ACTIVE) {
						break;
					}
					QueueState closing = new QueueState(
							Lifecycle.CLOSING, current.inFlight,
							current.occupied, current.nextOrdinal,
							current.overflowCount, current.droppedCount,
							current.lastOfferedSequence);
					if (queueState.compareAndSet(current, closing)) {
						break;
					}
				}
				if (REGISTRATIONS.remove(owner, this)) {
					refreshActiveRegistrations();
				}
			}
		}

		private void transitionToClosed() {
			while (true) {
				QueueState current = queueState.get();
				if (current.lifecycle == Lifecycle.CLOSED) {
					return;
				}
				if (current.lifecycle != Lifecycle.CLOSING
						|| current.inFlight != 0) {
					throw new IllegalStateException(
							"Stand manual-movement observer closed before "
									+ "producer reservations settled.");
				}
				QueueState closed = new QueueState(
						Lifecycle.CLOSED, 0, current.occupied,
						current.nextOrdinal, current.overflowCount,
						current.droppedCount,
						current.lastOfferedSequence);
				if (queueState.compareAndSet(current, closed)) {
					return;
				}
			}
		}

		private void awaitProducerGrace() {
			long deadline = System.nanoTime() + PRODUCER_GRACE_NANOS;
			while (queueState.get().inFlight != 0
					&& System.nanoTime() < deadline) {
				LockSupport.parkNanos(50_000L);
			}
		}

		private void settleOutstandingReservations(long limitExclusive) {
			for (long ordinal = headOrdinal;
					ordinal < limitExclusive; ordinal++) {
				int index = queueIndex(ordinal);
				QueueSlot slot = queue.get(index);
				if (slot == null) {
					QueueSlot cancelled = QueueSlot.cancelled(ordinal);
					if (queue.compareAndSet(index, null, cancelled)) {
						slot = cancelled;
					}
					else {
						slot = queue.get(index);
					}
				}
				if (slot == null || slot.ordinal != ordinal) {
					throw new IllegalStateException(
							"Stand manual-movement reservation slot "
									+ "mismatch at ordinal " + ordinal + ".");
				}
				if (slot.cancelled) {
					settleCancelled(slot);
				}
				else {
					settleAccepted(slot);
				}
			}
		}

		private void awaitNoInFlightProducers() {
			long deadline = System.nanoTime()
					+ TimeUnit.SECONDS.toNanos(
							CLOSE_LOCK_TIMEOUT_SECONDS);
			while (queueState.get().inFlight != 0
					&& System.nanoTime() < deadline) {
				LockSupport.parkNanos(50_000L);
			}
			if (queueState.get().inFlight != 0) {
				throw new IllegalStateException(
						"Timed out settling Stand manual-movement "
								+ "producer reservations.");
			}
		}

		private DrainResult drainResult(int drained) {
			QueueState snapshot = queueState.get();
			return new DrainResult(
					registrationId, drained, snapshot.occupied,
					acceptedCount.get(), deliveredCount.get(),
					snapshot.overflowCount, snapshot.droppedCount,
					lastAcceptedSequence.get(),
					callbackFailureCount.get());
		}

		private int queueIndex(long ordinal) {
			return (int) ((ordinal - 1L) % queueCapacity);
		}

		private static void lockBoundedly(
				ReentrantLock lock, String phase) {
			boolean acquired;
			try {
				acquired = lock.tryLock(
						CLOSE_LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			}
			catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(
						"Interrupted waiting for Stand manual-movement "
								+ phase + " lock.", interrupted);
			}
			if (!acquired) {
				throw new IllegalStateException(
						"Timed out waiting for Stand manual-movement "
								+ phase + " lock.");
			}
		}

		ReservedOfferForTest reserveForTest(EventEnvelope envelope) {
			Objects.requireNonNull(envelope, "envelope");
			QueueSlot slot = reserve(envelope);
			if (slot == null) {
				throw new IllegalStateException(
						"Could not reserve a test observer offer.");
			}
			return new ReservedOfferForTest(slot);
		}

		void commitReservedForTest(ReservedOfferForTest reserved) {
			Objects.requireNonNull(reserved, "reserved");
			commitReserved(reserved.slot);
		}

		private enum Lifecycle {
			ACTIVE,
			CLOSING,
			CLOSED
		}

		private record QueueState(
				Lifecycle lifecycle,
				int inFlight,
				int occupied,
				long nextOrdinal,
				long overflowCount,
				long droppedCount,
				long lastOfferedSequence) {
			private static QueueState active() {
				return new QueueState(
						Lifecycle.ACTIVE, 0, 0, 1L,
						0L, 0L, -1L);
			}
		}

		private static final class QueueSlot {
			private final long ordinal;
			@Nullable
			private final EventEnvelope envelope;
			private final boolean cancelled;
			private final AtomicBoolean settled = new AtomicBoolean();

			private QueueSlot(
					long ordinal,
					@Nullable EventEnvelope envelope,
					boolean cancelled) {
				this.ordinal = ordinal;
				this.envelope = envelope;
				this.cancelled = cancelled;
			}

			private static QueueSlot accepted(
					long ordinal, EventEnvelope envelope) {
				return new QueueSlot(ordinal, envelope, false);
			}

			private static QueueSlot cancelled(long ordinal) {
				return new QueueSlot(ordinal, null, true);
			}
		}

		static final class ReservedOfferForTest {
			private final QueueSlot slot;

			private ReservedOfferForTest(QueueSlot slot) {
				this.slot = slot;
			}
		}
	}
}
