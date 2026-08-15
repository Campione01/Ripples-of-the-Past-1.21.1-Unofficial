package com.github.standobyte.jojo.api.stand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import com.github.standobyte.jojo.api.stand.StandManualMovementObservers.CloseDrainResult;
import com.github.standobyte.jojo.api.stand.StandManualMovementObservers.DrainResult;
import com.github.standobyte.jojo.api.stand.StandManualMovementObservers.EventEnvelope;
import com.github.standobyte.jojo.api.stand.StandManualMovementObservers.LogicalSide;

import net.minecraft.resources.ResourceLocation;

public final class StandManualMovementObserversSmokeTest {
	private static final long WAIT_SECONDS = 5L;

	private StandManualMovementObserversSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		testDeferredCallbacksAndFailureAccounting();
		testBoundedOverflowAndGenerationIsolation();
		testConcurrentFifoAndSerializedDrains();
		testReservedProducerCloseRace();
		testBoundedCloseDoesNotInvokeObserver();
		testCallbackSelfClose();
		testNoObserverFastPath();
		check(!StandManualMovementObservers.hasObservers(),
				"all smoke registrations must be closed");
	}

	private static void testDeferredCallbacksAndFailureAccounting() {
		AtomicInteger observed = new AtomicInteger();
		AtomicReference<EventEnvelope> lastEnvelope = new AtomicReference<>();
		StandManualMovementObservers.Registration failing =
				StandManualMovementObservers.register(
						id("failing"),
						envelope -> {
							throw new IllegalStateException("fixture failure");
						});
		StandManualMovementObservers.Registration collecting =
				StandManualMovementObservers.register(
						id("collecting"),
						envelope -> {
							lastEnvelope.set(envelope);
							observed.incrementAndGet();
						});
		try {
			check(StandManualMovementObservers.hasObservers(),
					"registered observers must activate the fast path");
			publishBinding(1L, true, LogicalSide.CLIENT);
			check(observed.get() == 0,
					"publish must not invoke callbacks on the producer thread");

			DrainResult failingDrain = failing.drain();
			DrainResult collectingDrain = collecting.drain();
			check(failingDrain.drainedCount() == 1
					&& failingDrain.acceptedCount() == 1L
					&& failingDrain.deliveredCount() == 0L
					&& failingDrain.callbackFailureCount() == 1L,
					"failed callbacks must not self-certify delivery");
			check(collectingDrain.drainedCount() == 1
					&& collectingDrain.deliveredCount() == 1L
					&& collectingDrain.callbackFailureCount() == 0L
					&& observed.get() == 1,
					"drain must isolate observer failures");
			EventEnvelope envelope = lastEnvelope.get();
			check(envelope != null
					&& envelope.logicalSide() == LogicalSide.CLIENT
					&& "test:dimension".equals(envelope.dimension())
					&& envelope.gameTime() == 1L
					&& envelope.sequence() > 0L
					&& envelope.acceptedOrdinal() == 1L,
					"event envelope metadata and ordinal must be complete");

			CloseDrainResult failingClose = failing.closeAndFreeze();
			CloseDrainResult collectingClose = collecting.closeAndFreeze();
			check(failingClose.closed()
					&& failingClose.remainingCount() == 0
					&& failingClose.acceptedCount() == 1L
					&& failingClose.deliveredCount() == 0L
					&& failingClose.callbackFailureCount() == 1L
					&& !failingClose.lossless()
					&& failingClose.failed(),
					"callback failure must make close evidence fail closed");
			check(collectingClose.closed()
					&& collectingClose.remainingCount() == 0
					&& collectingClose.callbackFailureCount() == 0L
					&& collectingClose.lossless()
					&& !collectingClose.failed(),
					"successful delivery must produce lossless close evidence");
		}
		finally {
			failing.close();
			collecting.close();
		}
	}

	private static void testBoundedOverflowAndGenerationIsolation() {
		ResourceLocation owner = id("bounded");
		AtomicInteger oldGenerationObserved = new AtomicInteger();
		StandManualMovementObservers.Registration bounded =
				StandManualMovementObservers.register(
						owner, 1,
						event -> oldGenerationObserved.incrementAndGet());
		publishBinding(2L, true, LogicalSide.CLIENT);
		publishBinding(3L, false, LogicalSide.CLIENT);
		check(bounded.overflowCount() == 1L
				&& bounded.droppedCount() == 1L
				&& bounded.pendingCount() == 1,
				"bounded MPSC capacity must reject without blocking");
		DrainResult boundedDrain = bounded.drain();
		check(boundedDrain.deliveredCount() == 1L
				&& oldGenerationObserved.get() == 1,
				"caller must drain accepted events before freezing");
		CloseDrainResult boundedClose = bounded.closeAndFreeze();
		check(boundedClose.acceptedCount() == 1L
				&& boundedClose.deliveredCount() == 1L
				&& boundedClose.overflowCount() == 1L
				&& boundedClose.droppedCount() == 1L
				&& boundedClose.callbackFailureCount() == 0L
				&& boundedClose.closeBarrierSequence()
						>= boundedClose.lastAcceptedSequence()
				&& !boundedClose.lossless()
				&& boundedClose.failed(),
				"overflow must be frozen as a failed close contract");

		AtomicInteger newGenerationObserved = new AtomicInteger();
		StandManualMovementObservers.Registration next =
				StandManualMovementObservers.register(
						owner,
						event -> newGenerationObserved.incrementAndGet());
		try {
			publishBinding(4L, true, LogicalSide.CLIENT);
			next.drain();
			check(oldGenerationObserved.get() == 1
					&& newGenerationObserved.get() == 1
					&& next.registrationId() != bounded.registrationId(),
					"closed registrations must not receive the next scene");
		}
		finally {
			next.close();
		}
	}

	private static void testConcurrentFifoAndSerializedDrains() {
		int producerCount = 4;
		int eventsPerProducer = 64;
		int expectedCount = producerCount * eventsPerProducer;
		List<Long> ordinals = Collections.synchronizedList(
				new ArrayList<>());
		List<Long> sequences = Collections.synchronizedList(
				new ArrayList<>());
		StandManualMovementObservers.Registration registration =
				StandManualMovementObservers.register(
						id("concurrent_fifo"), expectedCount,
						envelope -> {
							ordinals.add(envelope.acceptedOrdinal());
							sequences.add(envelope.sequence());
						});
		AtomicReference<Throwable> threadFailure = new AtomicReference<>();
		CountDownLatch producerStart = new CountDownLatch(1);
		List<Thread> producers = new ArrayList<>();
		for (int producer = 0; producer < producerCount; producer++) {
			int producerId = producer;
			Thread thread = thread(
					"stand-observer-producer-" + producer,
					threadFailure, () -> {
						await(producerStart, "producer start");
						for (int event = 0;
								event < eventsPerProducer; event++) {
							publishBinding(
									producerId * 1_000L + event,
									(event & 1) == 0,
									(producerId & 1) == 0
											? LogicalSide.CLIENT
											: LogicalSide.SERVER);
						}
					});
			producers.add(thread);
			thread.start();
		}
		producerStart.countDown();
		producers.forEach(StandManualMovementObserversSmokeTest::join);
		rethrowThreadFailure(threadFailure);
		check(registration.pendingCount() == expectedCount
				&& registration.overflowCount() == 0L,
				"concurrent producers must reserve every bounded slot");

		CountDownLatch drainStart = new CountDownLatch(1);
		AtomicInteger drained = new AtomicInteger();
		Thread firstDrainer = thread(
				"stand-observer-drainer-1", threadFailure, () -> {
					await(drainStart, "drain start");
					drained.addAndGet(
							registration.drain().drainedCount());
				});
		Thread secondDrainer = thread(
				"stand-observer-drainer-2", threadFailure, () -> {
					await(drainStart, "drain start");
					drained.addAndGet(
							registration.drain().drainedCount());
				});
		firstDrainer.start();
		secondDrainer.start();
		drainStart.countDown();
		join(firstDrainer);
		join(secondDrainer);
		rethrowThreadFailure(threadFailure);
		check(drained.get() == expectedCount
				&& ordinals.size() == expectedCount,
				"two drainers must deliver every event exactly once");
		for (int index = 0; index < expectedCount; index++) {
			check(ordinals.get(index) == index + 1L,
					"registration callbacks must follow accepted ordinal FIFO");
		}
		long maxSequence = sequences.stream()
				.mapToLong(Long::longValue).max().orElseThrow();
		CloseDrainResult close = registration.closeAndFreeze();
		check(close.acceptedCount() == expectedCount
				&& close.deliveredCount() == expectedCount
				&& close.callbackFailureCount() == 0L
				&& close.lastAcceptedSequence() == maxSequence
				&& close.lossless()
				&& !close.failed(),
				"concurrent FIFO delivery must close losslessly");
	}

	private static void testReservedProducerCloseRace() {
		AtomicInteger observed = new AtomicInteger();
		StandManualMovementObservers.Registration registration =
				StandManualMovementObservers.register(
						id("reserved_close"), 4,
						envelope -> observed.incrementAndGet());
		StandManualMovementObservers.Registration.ReservedOfferForTest
				reserved = registration.reserveForTest(
						new EventEnvelope(
								10_000_000L, 0L, LogicalSide.CLIENT,
								"test:dimension", 5L,
								new StandManualMovementObservers
										.ControllerBinding(
												UUID.randomUUID(),
												UUID.randomUUID(), true)));
		CountDownLatch release = new CountDownLatch(1);
		AtomicReference<Throwable> threadFailure = new AtomicReference<>();
		Thread producer = thread(
				"stand-observer-reserved-producer", threadFailure,
				() -> {
					await(release, "reserved producer release");
					registration.commitReservedForTest(reserved);
				});
		producer.start();

		AtomicReference<CloseDrainResult> closeResult =
				new AtomicReference<>();
		Thread closer = thread(
				"stand-observer-race-closer", threadFailure,
				() -> closeResult.set(registration.closeAndFreeze()));
		closer.start();
		waitUntil(registration::isClosing, "registration closing");
		join(closer);
		rethrowThreadFailure(threadFailure);
		CloseDrainResult frozen = closeResult.get();
		check(frozen != null
				&& frozen.closed()
				&& frozen.acceptedCount() == 0L
				&& frozen.deliveredCount() == 0L
				&& frozen.droppedCount() == 1L
				&& frozen.remainingCount() == 0
				&& frozen.callbackFailureCount() == 0L
				&& frozen.closeBarrierSequence() > 0L
				&& !frozen.lossless()
				&& frozen.failed(),
				"close must cancel and freeze a reserved producer");

		release.countDown();
		join(producer);
		rethrowThreadFailure(threadFailure);
		check(observed.get() == 0
				&& registration.pendingCount() == 0
				&& registration.droppedCount() == frozen.droppedCount()
				&& registration.closeAndFreeze().equals(frozen),
				"a late reserved producer must not mutate frozen evidence");
	}

	private static void testBoundedCloseDoesNotInvokeObserver() {
		AtomicBoolean callbackEntered = new AtomicBoolean();
		CountDownLatch releaseCallback = new CountDownLatch(1);
		StandManualMovementObservers.Registration registration =
				StandManualMovementObservers.register(
						id("bounded_close"), envelope -> {
							callbackEntered.set(true);
							awaitUnbounded(releaseCallback);
						});
		publishBinding(6L, true, LogicalSide.CLIENT);
		AtomicReference<CloseDrainResult> closeResult =
				new AtomicReference<>();
		AtomicReference<Throwable> threadFailure = new AtomicReference<>();
		Thread closer = thread(
				"stand-observer-bounded-closer", threadFailure,
				() -> closeResult.set(registration.closeAndFreeze()));
		closer.start();
		boolean returnedBoundedly;
		try {
			returnedBoundedly = joinWithin(closer, 2L);
		}
		finally {
			releaseCallback.countDown();
			if (closer.isAlive()) {
				join(closer);
			}
		}
		rethrowThreadFailure(threadFailure);
		CloseDrainResult frozen = closeResult.get();
		check(returnedBoundedly
				&& !callbackEntered.get()
				&& frozen != null
				&& frozen.closed()
				&& frozen.acceptedCount() == 1L
				&& frozen.deliveredCount() == 0L
				&& frozen.droppedCount() == 1L
				&& frozen.remainingCount() == 0
				&& !frozen.lossless()
				&& frozen.failed(),
				"closeAndFreeze must never invoke a pending observer");
	}

	private static void testCallbackSelfClose() {
		AtomicReference<StandManualMovementObservers.Registration>
				registrationRef = new AtomicReference<>();
		AtomicBoolean reentrantCloseRejected = new AtomicBoolean();
		StandManualMovementObservers.Registration registration =
				StandManualMovementObservers.register(
						id("self_close"), envelope -> {
							try {
								registrationRef.get().closeAndFreeze();
								throw new AssertionError(
										"reentrant closeAndFreeze returned");
							}
							catch (IllegalStateException expected) {
								reentrantCloseRejected.set(true);
							}
						});
		registrationRef.set(registration);
		publishBinding(6L, true, LogicalSide.CLIENT);
		DrainResult drain = registration.drain();
		CloseDrainResult close = registration.closeAndFreeze();
		check(reentrantCloseRejected.get()
				&& drain.drainedCount() == 1
				&& registration.isClosed()
				&& close.deliveredCount() == 1L
				&& close.callbackFailureCount() == 0L
				&& close.lossless(),
				"callback self-close must defer without deadlock");
	}

	private static void testNoObserverFastPath() {
		check(!StandManualMovementObservers
					.shouldResolveControllerAction(false, false, false)
				&& !StandManualMovementObservers
						.shouldResolveControllerAction(false, true, false)
				&& StandManualMovementObservers
						.shouldResolveControllerAction(false, true, true)
				&& StandManualMovementObservers
						.shouldResolveControllerAction(true, false, false),
				"action lookup must require observation or continuous input");
		AtomicLong sequenceBefore = new AtomicLong();
		StandManualMovementObservers.Registration before =
				StandManualMovementObservers.register(
						id("fast_path_before"),
						envelope -> sequenceBefore.set(envelope.sequence()));
		publishBinding(7L, true, LogicalSide.CLIENT);
		before.drain();
		before.close();
		check(!StandManualMovementObservers.hasObservers(),
				"fast-path probe requires an empty registry");

		publishBinding(8L, true, LogicalSide.CLIENT);

		AtomicLong sequenceAfter = new AtomicLong();
		StandManualMovementObservers.Registration after =
				StandManualMovementObservers.register(
						id("fast_path_after"),
						envelope -> sequenceAfter.set(envelope.sequence()));
		publishBinding(9L, true, LogicalSide.CLIENT);
		after.drain();
		after.close();
		check(sequenceAfter.get() == sequenceBefore.get() + 1L,
				"publish with no observers must not allocate an envelope");
	}

	private static Thread thread(
			String name,
			AtomicReference<Throwable> failure,
			Runnable task) {
		return new Thread(() -> {
			try {
				task.run();
			}
			catch (Throwable error) {
				failure.compareAndSet(null, error);
			}
		}, name);
	}

	private static void publishBinding(
			long gameTime, boolean bound, LogicalSide side) {
		StandManualMovementObservers.publish(
				side, "test:dimension", gameTime,
				new StandManualMovementObservers.ControllerBinding(
						UUID.randomUUID(), UUID.randomUUID(), bound));
	}

	private static void await(
			CountDownLatch latch, String description) {
		try {
			check(latch.await(WAIT_SECONDS, TimeUnit.SECONDS),
					"timed out waiting for " + description);
		}
		catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new AssertionError(
					"interrupted waiting for " + description,
					interrupted);
		}
	}

	private static void awaitUnbounded(CountDownLatch latch) {
		try {
			latch.await();
		}
		catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new AssertionError(
					"blocking observer was interrupted", interrupted);
		}
	}

	private static boolean joinWithin(Thread thread, long seconds) {
		try {
			thread.join(TimeUnit.SECONDS.toMillis(seconds));
		}
		catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new AssertionError(
					"interrupted joining " + thread.getName(),
					interrupted);
		}
		return !thread.isAlive();
	}

	private static void join(Thread thread) {
		try {
			thread.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));
		}
		catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new AssertionError(
					"interrupted joining " + thread.getName(),
					interrupted);
		}
		check(!thread.isAlive(),
				"timed out joining " + thread.getName());
	}

	private static void waitUntil(
			BooleanSupplier condition, String description) {
		long deadline = System.nanoTime()
				+ TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
		while (!condition.getAsBoolean()
				&& System.nanoTime() < deadline) {
			Thread.onSpinWait();
		}
		check(condition.getAsBoolean(),
				"timed out waiting for " + description);
	}

	private static void rethrowThreadFailure(
			AtomicReference<Throwable> failure) {
		Throwable error = failure.get();
		if (error != null) {
			throw new AssertionError("worker thread failed", error);
		}
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(
				"stand_manual_movement_observer_test", path);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
