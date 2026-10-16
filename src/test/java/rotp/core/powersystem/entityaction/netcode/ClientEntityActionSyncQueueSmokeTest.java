package rotp.core.powersystem.entityaction.netcode;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.api.network.AbilityNetworkDiagnostics.Stage;
import rotp.core.compat.v1_21_4.missingmethods._Vec3;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.netcode.ClientEntityActionSyncQueue.ApplyResult;
import rotp.core.powersystem.entityaction.netcode.ClientEntityActionSyncQueue.ActionReservation;
import rotp.core.powersystem.entityaction.netcode.ClientEntityActionSyncQueue.CapacityResult;
import rotp.core.powersystem.entityaction.netcode.ClientEntityActionSyncQueue.ConnectionEpoch;
import rotp.core.powersystem.entityaction.netcode.ClientEntityActionSyncQueue.ConnectionEpochState;
import rotp.core.powersystem.entityaction.netcode.ClientEntityActionSyncQueue.DependencyDisposition;
import rotp.core.powersystem.entityaction.netcode.ClientEntityActionSyncQueue.DependentSyncDiagnostic;
import rotp.core.powersystem.entityaction.netcode.ClientEntityActionSyncQueue.EntityIdentity;
import rotp.core.powersystem.entityaction.netcode.ClientEntityActionSyncQueue.GenerationLedger;
import rotp.core.powersystem.entityaction.netcode.ClientEntityActionSyncQueue.GenerationLedgerRegistry;
import rotp.core.powersystem.entityaction.netcode.ClientEntityActionSyncQueue.LedgerAccess;
import rotp.core.powersystem.entityaction.netcode.ClientEntityActionSyncQueue.LedgerEntryState;
import rotp.core.powersystem.entityaction.netcode.ClientEntityActionSyncQueue.SweepResult;
import rotp.core.powersystem.entityaction.syncdata.TrActionSynchedDataPacket;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.AimingEntity;
import rotp.core.util.functions_network.NetworkUtil;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.connection.ConnectionType;

public final class ClientEntityActionSyncQueueSmokeTest {
	private ClientEntityActionSyncQueueSmokeTest() {}

	public static void run() {
		testApplyResultContract();
		testConnectionEpochIdentity();
		testStaleOriginAdmissionIsMutationFree();
		testFailedOriginDoesNotPoisonReplacement();
		testGenerationLedgerTombstones();
		testPendingActionBytesAreImmutable();
		testGenerationLedgersSurviveMoreThan256Entities();
		testLifecycleBoundAndEntityIdReuse();
		testSameIdentityReplacementLifetimeReplay();
		testLifetimeReplayDependencyBarrier();
		testDependentSyncAppliedDiagnostics();
		testTransactionalSweepCapacityFailure();
		testExistingLiveTouchCapacityFailure();
		testCapacityFailureClearsAndBlocksConnection();
		testMaximumOrphanTtlPath();
		testQueueContainmentSourceContract();
		testCapacityDisconnectSourceContract();
		testOriginAwareHandlerContract();
		testOriginDropDiagnosticSourceContract();
		testUuidWireSourceContract();
		testTargetPacketCodecRoundTrip();
		testTargetSyncReachesItsAction();
		testTargetSyncForAnotherActionChangesNothing();
		testTargetSyncQueuedBeforeItsAction();
	}

	// 1.16 TrStandTaskTargetPacket carried the task target the server changed; every field must come back as written
	private static void testTargetPacketCodecRoundTrip() {
		ActionTarget block = new ActionTarget(new BlockPos(3, -7, 11), Direction.WEST)
				.withClipPos(Optional.of(new Vec3(3.5D, -6.25D, 11.0D)));
		ActionTarget entity = entityTarget(4242, new Vec3(1.0D, 2.5D, -3.0D));
		checkRoundTrip(new TrEntityActionTargetPacket(
				1234, new UUID(5L, 6L), 77L, 9, block, entity, AimingEntity.STAND, entity),
				"a target sync keeping a block and turning to an entity, its task holding the entity");
		checkRoundTrip(new TrEntityActionTargetPacket(
				-8, new UUID(9L, 10L), 300L, 0, entity, block, AimingEntity.PLAYER, block),
				"a target sync keeping an entity and turning to a block, its task holding the block");
		checkRoundTrip(new TrEntityActionTargetPacket(
				1, new UUID(11L, 12L), 1L, 3, null, null, AimingEntity.CAMERA_ENTITY, null),
				"a target sync that keeps and turns to nothing, its task holding nothing");
	}

	private static void testTargetSyncReachesItsAction() {
		GenerationLedger ledger = new GenerationLedger();
		check(ledger.reserveAction(120L, new byte[] {12})
				== ActionReservation.FRESH,
				"Fixture: the action generation was not reserved");
		ledger.markApplied(120L, true);
		EntityActionInstance action = clientAction(7);
		TrEntityActionTargetPacket packet = targetSync(61, 120L, 7);
		ClientEntityActionSyncQueue.applyOrQueueTarget(packet, ignored -> ledger,
				sent -> ClientEntityActionSyncQueue.applyTargetTo(action, sent));
		checkHolds(action, packet,
				"a target sync for the client's action of its generation");
		check(!ledger.rejected(),
				"a target sync applied to its action must not reject the generation");
	}

	private static void testTargetSyncForAnotherActionChangesNothing() {
		GenerationLedger ledger = new GenerationLedger();
		ledger.reserveAction(140L, new byte[] {14});
		ledger.markApplied(140L, true);
		EntityActionInstance action = clientAction(8);
		action.aimAs = AimingEntity.STAND;
		ClientEntityActionSyncQueue.applyOrQueueTarget(targetSync(62, 140L, 9),
				ignored -> ledger,
				sent -> ClientEntityActionSyncQueue.applyTargetTo(action, sent));
		check(savedTarget(action) == null && action.standRotationTarget == null
				&& action.aimAs == AimingEntity.STAND && ledger.rejected(),
				"a target sync for another action ID of the generation must change nothing and reject the generation: it"
						+ " left " + describe(savedTarget(action)) + ", " + describe(action.standRotationTarget) + ", "
						+ action.aimAs + ", rejected " + ledger.rejected());
	}

	// sent before its action reached the client, a target sync waits and reaches the action once it applied
	private static void testTargetSyncQueuedBeforeItsAction() {
		GenerationLedger ledger = new GenerationLedger();
		ledger.reserveAction(160L, new byte[] {16});
		EntityActionInstance action = clientAction(5);
		TrEntityActionTargetPacket packet = targetSync(63, 160L, 5);
		int[] applied = {0};
		ClientEntityActionSyncQueue.PayloadApplier<TrEntityActionTargetPacket> applier = sent -> {
			applied[0]++;
			return ClientEntityActionSyncQueue.applyTargetTo(action, sent);
		};
		ClientEntityActionSyncQueue.applyOrQueueTarget(packet, ignored -> ledger, applier);
		check(applied[0] == 0 && savedTarget(action) == null && action.standRotationTarget == null,
				"a target sync that came before its action was applied at once");
		ledger.markApplied(160L, true);
		EntityIdentity identity = new EntityIdentity(packet.performerId(), packet.performerUuid());
		ClientEntityActionSyncQueue.applyPendingFor(identity, 160L, applier);
		check(applied[0] == 1, "a target sync that came before its action was applied " + applied[0]
				+ " times once the action applied");
		checkHolds(action, packet, "a target sync that came before its action, once the action applied");
		ClientEntityActionSyncQueue.applyPendingFor(identity, 160L, applier);
		check(applied[0] == 1, "a queued target sync was applied again");
	}

	private static TrEntityActionTargetPacket targetSync(int performerId, long generation, int actionId) {
		return new TrEntityActionTargetPacket(performerId, new UUID(0L, performerId * 100L), generation, actionId,
				entityTarget(900 + performerId, new Vec3(0.5D, 1.0D, 0.5D)),
				new ActionTarget(new BlockPos(performerId, 64, -performerId), Direction.UP),
				AimingEntity.CAMERA_ENTITY, entityTarget(700 + performerId, new Vec3(0.0D, 1.5D, 0.0D)));
	}

	// a client's action of a generation, as the action packet installed it
	private static EntityActionInstance clientAction(int id) {
		EntityActionInstance action = new EntityActionInstance(null);
		action.id = id;
		return action;
	}

	private static void checkHolds(EntityActionInstance action, TrEntityActionTargetPacket packet, String what) {
		check(Arrays.equals(targetBytes(savedTarget(action)), targetBytes(packet.keptTarget()))
				&& Arrays.equals(targetBytes(action.standRotationTarget), targetBytes(packet.rotationTarget()))
				&& action.aimAs == packet.aimAs()
				&& Arrays.equals(targetBytes(taskTarget(action)), targetBytes(packet.taskTarget())),
				what + " must leave the action holding what the server sent: it holds " + describe(savedTarget(action))
						+ ", turns to " + describe(action.standRotationTarget) + ", aims as " + action.aimAs
						+ " and its task holds " + describe(taskTarget(action)));
	}

	private static void checkRoundTrip(TrEntityActionTargetPacket sent, String what) {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(
				Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
		try {
			TrEntityActionTargetPacket.Handler.STREAM_CODEC.encode(buf, sent);
			TrEntityActionTargetPacket read = TrEntityActionTargetPacket.Handler.STREAM_CODEC.decode(buf);
			check(!buf.isReadable()
					&& read.performerId() == sent.performerId()
					&& read.performerUuid().equals(sent.performerUuid())
					&& read.actionGeneration() == sent.actionGeneration()
					&& read.actionId() == sent.actionId()
					&& read.aimAs() == sent.aimAs()
					&& Arrays.equals(targetBytes(read.keptTarget()), targetBytes(sent.keptTarget()))
					&& Arrays.equals(targetBytes(read.rotationTarget()), targetBytes(sent.rotationTarget()))
					&& Arrays.equals(targetBytes(read.taskTarget()), targetBytes(sent.taskTarget())),
					what + " must read back as written: " + describe(sent) + " came back as " + describe(read)
							+ (buf.isReadable() ? " with " + buf.readableBytes() + " bytes left" : ""));
		}
		finally {
			buf.release();
		}
	}

	// an entity target as a client decodes one, the entity not looked up
	private static ActionTarget entityTarget(int entityId, Vec3 clip) {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		try {
			buf.writeEnum(ActionTarget.TargetType.ENTITY);
			NetworkUtil.writeOptional(Optional.of(clip), buf, _Vec3.STREAM_CODEC);
			buf.writeInt(entityId);
			return ActionTarget.STREAM_CODEC_UNRESOLVED_ENTITY_ID.decode(buf);
		}
		finally {
			buf.release();
		}
	}

	@Nullable
	private static byte[] targetBytes(@Nullable ActionTarget target) {
		if (target == null) {
			return null;
		}
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		try {
			ActionTarget.STREAM_CODEC_UNRESOLVED_ENTITY_ID.encode(buf, target);
			byte[] bytes = new byte[buf.readableBytes()];
			buf.readBytes(bytes);
			return bytes;
		}
		finally {
			buf.release();
		}
	}

	private static String describe(TrEntityActionTargetPacket packet) {
		return "[performer " + packet.performerId() + " " + packet.performerUuid() + ", generation "
				+ packet.actionGeneration() + ", action " + packet.actionId() + ", kept " + describe(packet.keptTarget())
				+ ", turned to " + describe(packet.rotationTarget()) + ", " + packet.aimAs() + ", task "
				+ describe(packet.taskTarget()) + "]";
	}

	private static String describe(@Nullable ActionTarget target) {
		return target == null ? "nothing" : target.getType() + Arrays.toString(targetBytes(target));
	}

	// what a client's action holds as its task's target, which turns its offsets: only what the server sent
	@Nullable
	private static ActionTarget taskTarget(EntityActionInstance action) {
		try {
			Field field = EntityActionInstance.class.getDeclaredField("taskTarget");
			field.setAccessible(true);
			return (ActionTarget) field.get(action);
		}
		catch (ReflectiveOperationException error) {
			throw new AssertionError("cannot read the action's task target", error);
		}
	}

	@Nullable
	private static ActionTarget savedTarget(EntityActionInstance action) {
		try {
			Field field = EntityActionInstance.class.getDeclaredField("actionTargetSnapshot");
			field.setAccessible(true);
			return (ActionTarget) field.get(action);
		}
		catch (ReflectiveOperationException error) {
			throw new AssertionError("cannot read the action's saved target", error);
		}
	}

	private static void testGenerationLedgerTombstones() {
		GenerationLedger ledger = new GenerationLedger();
		check(ledger.reserveAction(41L, new byte[] {1})
				== ActionReservation.FRESH,
				"first action generation must reserve fresh state");
		check(ledger.classifyDependency(41L) == DependencyDisposition.WAIT,
				"dependencies must wait until their action generation applies");
		ledger.markApplied(41L, true);
		check(ledger.classifyDependency(41L) == DependencyDisposition.READY,
				"only an applied live action may admit its dependencies");

		check(ledger.reserveAction(42L, new byte[] {2})
				== ActionReservation.FRESH,
				"newer action generation must supersede the old action");
		check(ledger.classifyDependency(41L) == DependencyDisposition.DROP,
				"late data from the previous action must be tombstoned");
		ledger.markRejected(42L);
		check(ledger.rejected()
				&& ledger.classifyDependency(42L) == DependencyDisposition.DROP,
				"a rejected generation must retain its epoch tombstone");

		check(ledger.reserveAction(43L, new byte[] {3})
				== ActionReservation.FRESH,
				"a newer action must advance past a rejected generation");
		ledger.markApplied(43L, false);
		check(ledger.classifyDependency(43L) == DependencyDisposition.DROP,
				"a successfully synchronized clear action admits no dependencies");
		check(ledger.reserveAction(42L, new byte[] {2})
				== ActionReservation.SUPERSEDED
				&& ledger.highestActionGeneration() == 43L,
				"late action packets must never rewind the generation high-water mark");
	}

	private static void testPendingActionBytesAreImmutable() {
		GenerationLedger ledger = new GenerationLedger();
		byte[] original = new byte[] {7, 8, 9};
		check(ledger.reserveAction(70L, original)
				== ActionReservation.FRESH,
				"first pending payload must reserve its generation");
		original[0] = 99;
		check(ledger.pendingActionData(70L)[0] == 7,
				"reservation must clone caller-owned action bytes");

		byte[] returned = ledger.pendingActionData(70L);
		returned[1] = 99;
		check(ledger.pendingActionData(70L)[1] == 8,
				"pending action bytes must not expose mutable storage");
		check(ledger.reserveAction(70L, new byte[] {7, 8, 9})
				== ActionReservation.PENDING,
				"an identical pending duplicate must be idempotent");
		check(ledger.reserveAction(70L, new byte[] {7, 8, 10})
				== ActionReservation.CONFLICT,
				"a conflicting pending duplicate must be rejected");
		check(ledger.rejected()
				&& ledger.pendingActionData(70L) == null
				&& ledger.reserveAction(70L, new byte[] {7, 8, 9})
						== ActionReservation.REJECTED,
				"a conflicting duplicate must leave a non-rewritable tombstone");
	}

	private static void testGenerationLedgersSurviveMoreThan256Entities() {
		GenerationLedgerRegistry registry = new GenerationLedgerRegistry();
		for (int entityId = 1; entityId <= 300; entityId++) {
			EntityIdentity identity = identity(entityId, entityId);
			GenerationLedger ledger = acceptedLedger(
					registry.getOrCreate(identity, true));
			check(ledger.reserveAction(entityId, new byte[] {(byte) entityId})
					== ActionReservation.FRESH,
					"each entity must receive an independent generation ledger");
			ledger.markRejected(entityId);
		}
		check(registry.size() == 300,
				"more than 256 simultaneously live ledgers must not be evicted");
		check(registry.get(identity(1, 1)).highestActionGeneration() == 1L
				&& registry.get(identity(1, 1)).rejected()
				&& registry.get(identity(300, 300))
						.highestActionGeneration() == 300L,
				"all live UUID-bound ledgers must retain their generation floors");
		registry.clear();
		check(registry.size() == 0,
				"connection or level epoch expiry must clear all ledgers together");
	}

	private static void testLifecycleBoundAndEntityIdReuse() {
		GenerationLedgerRegistry registry = new GenerationLedgerRegistry(4);
		EntityIdentity removed = identity(17, 1700);
		EntityIdentity replacement = identity(17, 1701);
		GenerationLedger removedLedger = acceptedLedger(
				registry.getOrCreate(removed, true));
		removedLedger.reserveAction(81L, new byte[] {1});
		removedLedger.markRejected(81L);
		GenerationLedger replacementLedger = acceptedLedger(
				registry.getOrCreate(replacement, true));
		replacementLedger.reserveAction(82L, new byte[] {2});
		replacementLedger.markApplied(82L, true);
		check(registry.size() == 2
				&& registry.get(removed) == removedLedger
				&& registry.get(replacement) == replacementLedger,
				"entity-ID reuse must retain independent UUID-bound ledgers");
		check(removedLedger.classifyDependency(81L)
				== DependencyDisposition.DROP
				&& replacementLedger.classifyDependency(82L)
						== DependencyDisposition.READY,
				"late dependencies must not cross an entity-ID reuse boundary");

		for (int tick = 0; tick < 20; tick++) {
			registry.sweep(replacement::equals, ignored -> false);
		}
		check(acceptedLedger(registry.getOrCreate(removed, false))
				== removedLedger,
				"a late packet must resolve to the removed identity tombstone");
		for (int tick = 0; tick < 39; tick++) {
			registry.sweep(replacement::equals, ignored -> false);
		}
		check(registry.get(removed) != null,
				"late packets must restart removed-identity retention");
		registry.sweep(replacement::equals, ignored -> false);
		check(registry.get(removed) == null
				&& registry.get(replacement) == replacementLedger,
				"expired removed identities must be pruned while live replacements remain");
	}

	private static void testSameIdentityReplacementLifetimeReplay() {
		GenerationLedgerRegistry registry = new GenerationLedgerRegistry(4);
		EntityIdentity identity = identity(19, 1900);
		Object firstEntity = new Object();
		Object replacementEntity = new Object();
		byte[] actionData = new byte[] {9, 1};
		GenerationLedger ledger = acceptedLedger(
				registry.getOrCreate(identity, true));

		check(ledger.reserveAction(91L, actionData, firstEntity)
				== ActionReservation.FRESH,
				"the first entity lifetime must reserve its action generation");
		ledger.markApplied(91L, firstEntity, true);
		check(ledger.reserveAction(91L, actionData, firstEntity)
				== ActionReservation.DUPLICATE,
				"the same entity object must reject a duplicate action packet");
		for (int tick = 0; tick < 39; ++tick) {
			registry.sweep(ignored -> false, ignored -> false);
		}
		check(registry.get(identity) == ledger,
				"the applied ledger must remain retained through TTL minus one ticks");
		check(ledger.reserveAction(91L, actionData, null)
				== ActionReservation.DUPLICATE,
				"an absent entity must not broadly reopen an applied generation");
		check(ledger.reserveAction(90L, actionData, replacementEntity)
				== ActionReservation.SUPERSEDED,
				"a replacement lifetime must not lower the generation floor");

		GenerationLedger sameIdentityLedger = acceptedLedger(
				registry.getOrCreate(identity, true));
		check(sameIdentityLedger == ledger
				&& sameIdentityLedger.reserveAction(
						91L, actionData, replacementEntity)
						== ActionReservation.LIFETIME_REPLAY,
				"the same ID and UUID on a new entity object must replay the current generation");
		sameIdentityLedger.markApplied(91L, replacementEntity, true);
		check(sameIdentityLedger.reserveAction(
				91L, actionData, replacementEntity)
				== ActionReservation.DUPLICATE,
				"the replacement object must regain normal duplicate suppression after apply");
	}

	private static void testLifetimeReplayDependencyBarrier() {
		GenerationLedger ledger = new GenerationLedger();
		EntityIdentity identity = identity(20, 2000);
		Object firstEntity = new Object();
		Object replacementEntity = new Object();
		byte[] actionData = new byte[] {10, 2};
		check(ledger.reserveAction(101L, actionData, firstEntity)
				== ActionReservation.FRESH,
				"the original entity lifetime must reserve a fresh root");
		ledger.markApplied(101L, firstEntity, true);

		Map<EntityIdentity, List<ClientEntityActionSyncQueue.Pending<String>>>
				pending = new LinkedHashMap<>();
		pending.put(identity, new ArrayList<>(List.of(
				new ClientEntityActionSyncQueue.Pending<>(
						101L, "before_replacement_root"),
				new ClientEntityActionSyncQueue.Pending<>(
						102L, "other_generation"))));
		check(ledger.reserveAction(101L, actionData, replacementEntity)
				== ActionReservation.LIFETIME_REPLAY,
				"a replacement lifetime must open a distinct replay barrier");
		ClientEntityActionSyncQueue.removePendingGeneration(
				pending, identity, 101L);
		check(pending.get(identity).size() == 1
				&& pending.get(identity).getFirst().generation == 102L,
				"lifetime replay must clear only exact-generation dependents");

		ledger.markApplied(101L, replacementEntity, true);
		check(ledger.classifyDependency(101L)
				== DependencyDisposition.READY,
				"the replacement root must admit later same-generation dependencies");
		pending.clear();
		pending.put(identity, new ArrayList<>(List.of(
				new ClientEntityActionSyncQueue.Pending<>(
						101L, "after_replacement_root"))));
		List<String> applied = new ArrayList<>();
		check(ClientEntityActionSyncQueue.applyPendingFor(
				pending, identity, 101L, payload -> {
					applied.add(payload);
					return ApplyResult.APPLIED;
				}) == ApplyResult.APPLIED
				&& applied.equals(List.of("after_replacement_root"))
				&& !pending.containsKey(identity),
				"dependencies queued after the replay root must apply normally");
	}

	private static void testDependentSyncAppliedDiagnostics() {
		for (String prefix : List.of(
				"synched_data", "phase_time", "obb", "target")) {
			DependentSyncDiagnostic diagnostic =
					ClientEntityActionSyncQueue
							.dependentSyncAppliedDiagnostic(prefix, 111L);
			check(diagnostic.stage()
					== Stage.CLIENT_ACTION_DEPENDENT_SYNC_APPLIED
					&& diagnostic.detail().startsWith(prefix + ":")
					&& diagnostic.detail().endsWith("generation=111"),
					"dependent sync diagnostics must retain their exact stage and detail prefix");
		}
		expectThrows(IllegalArgumentException.class,
				() -> ClientEntityActionSyncQueue
						.dependentSyncAppliedDiagnostic("unknown", 111L),
				"unknown dependent sync diagnostics must fail closed");

		String queue = read(
				"src/main/java/rotp/core/powersystem/entityaction/netcode/ClientEntityActionSyncQueue.java");
		int helper = queue.indexOf(
				"private static void recordDependentApplied(");
		int mainThread = queue.indexOf(
				"Minecraft.getInstance().isSameThread()", helper);
		int record = queue.indexOf(
				"ClientAbilityNetworkDiagnostics.recordAction(", mainThread);
		check(helper >= 0 && mainThread > helper && record > mainThread
				&& queue.indexOf(
						"Stage.CLIENT_ACTION_DEPENDENT_SYNC_APPLIED",
						record) > record,
				"successful dependent receipts must be main-thread derived and production-recorded");
		int synchedMutation = queue.indexOf(
				"SyncActionInstanceData.setDataClientSide(");
		int synchedReceipt = queue.indexOf(
				"recordDependentApplied(", synchedMutation);
		int phaseMutation = queue.indexOf(
				"action.setPhase(packet.phase(), packet.curPhaseTick())");
		int phaseReceipt = queue.indexOf(
				"recordDependentApplied(", phaseMutation);
		int obbMutation = queue.indexOf(
				"obbAction.extendableOBB().setIsRetracting(true)");
		int obbReceipt = queue.indexOf(
				"recordDependentApplied(", obbMutation);
		int targetMutation = queue.indexOf(
				"result = applyTargetTo(action, packet)");
		int targetReceipt = queue.indexOf(
				"recordDependentApplied(", targetMutation);
		check(synchedMutation >= 0 && synchedReceipt > synchedMutation
				&& phaseMutation >= 0 && phaseReceipt > phaseMutation
				&& obbMutation >= 0 && obbReceipt > obbMutation
				&& targetMutation >= 0 && targetReceipt > targetMutation,
				"each dependent receipt must follow its successful production mutation");
	}

	private static void testTransactionalSweepCapacityFailure() {
		GenerationLedgerRegistry registry = new GenerationLedgerRegistry(2);
		EntityIdentity first = identity(1, 101);
		EntityIdentity second = identity(2, 102);
		EntityIdentity third = identity(3, 103);
		acceptedLedger(registry.getOrCreate(first, true));
		acceptedLedger(registry.getOrCreate(second, true));
		acceptedLedger(registry.getOrCreate(third, true));
		List<LedgerEntryState> before = List.of(
				registry.state(first), registry.state(second), registry.state(third));

		SweepResult result = registry.sweep(
				ignored -> false, ignored -> false);
		check(result.result() == CapacityResult.CAPACITY_EXHAUSTED
				&& result.liveToOrphanCandidates() == 3
				&& result.projectedOrphans() == 3,
				"three simultaneously removed live ledgers must report projected overflow");
		check(before.equals(List.of(
				registry.state(first), registry.state(second), registry.state(third))),
				"a failed sweep must not change any ledger flag, TTL, or identity");
	}

	private static void testExistingLiveTouchCapacityFailure() {
		GenerationLedgerRegistry registry = new GenerationLedgerRegistry(2);
		EntityIdentity firstOrphan = identity(11, 1101);
		EntityIdentity secondOrphan = identity(12, 1201);
		EntityIdentity existingLive = identity(13, 1301);
		acceptedLedger(registry.getOrCreate(firstOrphan, false));
		acceptedLedger(registry.getOrCreate(secondOrphan, false));
		acceptedLedger(registry.getOrCreate(existingLive, true));
		List<LedgerEntryState> before = List.of(
				registry.state(firstOrphan), registry.state(secondOrphan),
				registry.state(existingLive));

		LedgerAccess access = registry.getOrCreate(existingLive, false);
		check(access.result() == CapacityResult.CAPACITY_EXHAUSTED
				&& access.ledger() == null,
				"a late packet must report capacity failure before live-to-orphan touch");
		check(before.equals(List.of(
				registry.state(firstOrphan), registry.state(secondOrphan),
				registry.state(existingLive))),
				"failed live-to-orphan access must leave the registry unchanged");
	}

	private static void testCapacityFailureClearsAndBlocksConnection() {
		ConnectionEpoch epoch = new ConnectionEpoch();
		Object level = new Object();
		Object nextLevel = new Object();
		Object connection = new Object();
		Object nextConnection = new Object();
		check(!epoch.update(level, connection),
				"initial epoch capture must be stable");
		long beforeEpoch = epoch.epoch();
		List<Integer> pending = new ArrayList<>(List.of(1, 2, 3));
		GenerationLedgerRegistry ledgers = new GenerationLedgerRegistry(2);
		acceptedLedger(ledgers.getOrCreate(identity(21, 2101), true));
		int[] clearCalls = {0};

		check(epoch.failCapacity(level, connection, () -> {
			pending.clear();
			ledgers.clear();
			++clearCalls[0];
		}), "first capacity failure must advance the connection epoch");
		check(pending.isEmpty() && ledgers.size() == 0
				&& epoch.epoch() == beforeEpoch + 1,
				"capacity failure must clear queue state while advancing the epoch");
		check(!epoch.acceptsPackets(connection),
				"the failed connection must reject all subsequent packet data");
		check(epoch.consumeDisconnectRequest(connection)
				&& !epoch.consumeDisconnectRequest(connection),
				"the failed connection must request disconnect exactly once");
		check(!epoch.failCapacity(level, connection, () -> ++clearCalls[0])
				&& clearCalls[0] == 1,
				"repeated failure on the same connection must not rearm or reclear");
		check(epoch.update(nextLevel, connection)
				&& !epoch.acceptsPackets(connection),
				"a level change must not unblock the failed connection");
		check(epoch.update(nextLevel, nextConnection)
				&& epoch.acceptsPackets(nextConnection),
				"only connection replacement may admit a new packet epoch");
	}

	private static void testMaximumOrphanTtlPath() {
		GenerationLedgerRegistry registry = new GenerationLedgerRegistry();
		for (int entityId = 1; entityId <= 1024; ++entityId) {
			check(registry.getOrCreate(
					identity(entityId, 5000L + entityId), false)
						.result() == CapacityResult.ACCEPTED,
					"the normal orphan bound must retain all 1024 tombstones");
		}
		check(registry.getOrCreate(identity(1025, 6025), false).result()
				== CapacityResult.CAPACITY_EXHAUSTED
				&& registry.size() == 1024,
				"the 1025th orphan must fail without changing the normal registry");
		for (int tick = 0; tick < 39; ++tick) {
			check(registry.sweep(ignored -> false, ignored -> false).result()
					== CapacityResult.ACCEPTED,
					"normal orphan TTL sweeps must remain within capacity");
		}
		check(registry.size() == 1024,
				"normal tombstones must survive through TTL minus one ticks");
		SweepResult expiry = registry.sweep(
				ignored -> false, ignored -> false);
		check(expiry.result() == CapacityResult.ACCEPTED
				&& expiry.expiredEntries() == 1024
				&& expiry.projectedOrphans() == 0
				&& registry.size() == 0,
				"the normal 1024-ledger path must expire transactionally at TTL");
	}

	private static void testApplyResultContract() {
		check(EnumSet.allOf(ApplyResult.class).equals(EnumSet.of(
				ApplyResult.APPLIED,
				ApplyResult.REJECTED,
				ApplyResult.RETRY)),
				"action queue result must distinguish apply, reject, and retry");
	}

	private static void testConnectionEpochIdentity() {
		ConnectionEpoch epoch = new ConnectionEpoch();
		Object levelA = new Object();
		Object levelB = new Object();
		Object connectionA = new Object();
		Object connectionB = new Object();
		check(!epoch.update(levelA, connectionA),
				"initial epoch capture must not report a transition");
		check(!epoch.update(levelA, connectionA),
				"stable level and connection identities must retain pending state");
		check(epoch.update(levelA, connectionB),
				"connection replacement must invalidate entity-ID keyed state");
		check(epoch.update(levelB, connectionB),
				"level replacement must invalidate entity-ID keyed state");
		check(epoch.update(null, null),
				"disconnect transition must invalidate pending state");
		check(!epoch.update(null, null),
				"repeated disconnected ticks must remain the same epoch");
	}

	private static void testStaleOriginAdmissionIsMutationFree() {
		ConnectionEpoch epoch = new ConnectionEpoch();
		Object level = new Object();
		Object oldListener = new Object();
		Object newListener = new Object();
		epoch.update(level, oldListener);
		ConnectionEpochState before = epoch.state();
		List<Integer> pending = new ArrayList<>(List.of(4, 5, 6));
		int[] mutations = {0};
		Runnable forbiddenMutation = () -> {
			++mutations[0];
			pending.clear();
			epoch.update(null, null);
		};

		check(!ClientEntityActionSyncQueue.admitPacketOrigin(
				oldListener, null, forbiddenMutation),
				"an old queued context must be rejected after disconnect");
		check(!ClientEntityActionSyncQueue.admitPacketOrigin(
				oldListener, newListener, forbiddenMutation),
				"an old queued context must be rejected after listener replacement");
		check(before.equals(epoch.state())
				&& pending.equals(List.of(4, 5, 6))
				&& mutations[0] == 0,
				"stale origins must not mutate queue, epoch, or disconnect state");

		check(ClientEntityActionSyncQueue.admitPacketOrigin(
				oldListener, oldListener, () -> {
					++mutations[0];
					check(!epoch.update(level, oldListener),
							"same-listener admission must retain its epoch");
				}), "the exact current listener must be admitted");
		check(before.equals(epoch.state())
				&& pending.equals(List.of(4, 5, 6))
				&& mutations[0] == 1,
				"same-listener admission must run only its admitted action");
	}

	private static void testFailedOriginDoesNotPoisonReplacement() {
		ConnectionEpoch epoch = new ConnectionEpoch();
		Object level = new Object();
		Object oldListener = new Object();
		Object newListener = new Object();
		List<Integer> pending = new ArrayList<>(List.of(7, 8));
		epoch.update(level, oldListener);
		epoch.failCapacity(level, oldListener, pending::clear);
		ConnectionEpochState failedOld = epoch.state();
		pending.add(9);
		int[] staleMutations = {0};

		check(ClientEntityActionSyncQueue.admitPacketOrigin(
				oldListener, oldListener,
				() -> epoch.update(level, oldListener))
				&& !epoch.acceptsPackets(oldListener),
				"a matching listener must still be rejected after capacity failure");
		check(failedOld.equals(epoch.state()),
				"capacity rejection must not alter the failed epoch");

		check(!ClientEntityActionSyncQueue.admitPacketOrigin(
				oldListener, newListener, () -> {
					++staleMutations[0];
					pending.clear();
					epoch.update(level, newListener);
					epoch.failCapacity(level, newListener, () -> {});
				}), "a failed old context must not enter the replacement epoch");
		check(failedOld.equals(epoch.state())
				&& pending.equals(List.of(9))
				&& staleMutations[0] == 0
				&& !epoch.consumeDisconnectRequest(newListener),
				"an old task must neither pollute nor disconnect the new listener");

		check(ClientEntityActionSyncQueue.admitPacketOrigin(
				newListener, newListener, () -> {
					if (epoch.update(level, newListener)) {
						pending.clear();
					}
				}), "the replacement listener context must establish a fresh epoch");
		check(epoch.acceptsPackets(newListener)
				&& pending.isEmpty()
				&& !epoch.consumeDisconnectRequest(newListener)
				&& !epoch.state().capacityFailed()
				&& !epoch.state().disconnectRequested(),
				"the replacement listener must not inherit old failure or disconnect state");
	}

	private static void testQueueContainmentSourceContract() {
		String source = read(
				"src/main/java/rotp/core/powersystem/entityaction/netcode/ClientEntityActionSyncQueue.java");
		check(source.contains("ledger.markRejected(generation)")
				&& source.contains("CLIENT_ACTION_DEPENDENT_SYNC_REJECTED")
				&& source.contains("clearFailedNetworkAction()")
				&& source.contains("clearActionDependentPending(identity, generation)"),
				"rejected action dependencies must be contained and discarded");
		check(source.contains(
				"CONNECTION_EPOCH.update(currentLevel, currentConnection)")
				&& source.contains("clearPending()"),
				"level or connection epoch changes must clear all pending maps");
		check(!source.contains("Map<Integer, GenerationLedger>")
				&& source.contains("Map<EntityIdentity, LedgerEntry>")
				&& source.contains("MAX_ORPHAN_GENERATION_LEDGERS")
				&& source.contains("GENERATIONS.sweep(")
				&& source.contains("identity.performerUuid().equals(living.getUUID())")
				&& source.contains("PENDING_ACTIONS.putIfAbsent("),
				"generation state must be UUID-bound, lifecycle-pruned, and immutable");
		check(source.contains("appliedEntityLifetime")
				&& source.contains("entityLifetime != appliedEntityLifetime")
				&& source.contains(
						"generation, entityLifetime, action != null"),
				"applied generation state must be bound to the exact client entity lifetime");
		check(source.contains("encodedActionPresent && action == null")
				&& source.contains("input.isReadable()")
				&& source.contains("return ApplyResult.REJECTED"),
				"malformed or unresolved action payloads must reject, not apply");
		check(source.contains("clearDependentPendingBefore(identity, generation)")
				&& source.contains("applyPendingFor(identity, generation)")
				&& source.contains("pending.generation != generation"),
				"dependency replay must be isolated to the successfully applied generation");
		int freshReservation = source.indexOf("case FRESH ->");
		int lifetimeReplay = source.indexOf(
				"case LIFETIME_REPLAY ->", freshReservation);
		int clearReplayGeneration = source.indexOf(
				"clearActionDependentPending(identity, generation)",
				lifetimeReplay);
		int pendingReservation = source.indexOf(
				"case PENDING ->", clearReplayGeneration);
		int retireBeforeApply = source.indexOf(
				"clearDependentPendingBefore(identity, generation)",
				freshReservation);
		int applyFresh = source.indexOf(
				"ApplyResult result = applyAction(", retireBeforeApply);
		int appliedResult = source.indexOf(
				"if (result == ApplyResult.APPLIED)", applyFresh);
		int retireBeforeReplay = source.indexOf(
				"clearDependentPendingBefore(identity, generation)",
				appliedResult);
		int replayGeneration = source.indexOf(
				"applyPendingFor(identity, generation)", retireBeforeReplay);
		check(freshReservation >= 0
				&& lifetimeReplay > freshReservation
				&& clearReplayGeneration > lifetimeReplay
				&& pendingReservation > clearReplayGeneration
				&& retireBeforeApply > freshReservation
				&& applyFresh > retireBeforeApply
				&& appliedResult > applyFresh
				&& retireBeforeReplay > appliedResult
				&& replayGeneration > retireBeforeReplay,
				"fresh and lifetime-replay roots must preserve their distinct dependency barriers");
	}

	private static void testCapacityDisconnectSourceContract() {
		String queue = read(
				"src/main/java/rotp/core/powersystem/entityaction/netcode/ClientEntityActionSyncQueue.java");
		String tickHandler = read(
				"src/main/java/rotp/core/client/ClientTickHandler.java");
		int projection = queue.indexOf("projectedOrphans > maximumOrphans");
		int commit = queue.indexOf("for (LedgerSweep sweep : plan)");
		check(queue.contains("return LedgerAccess.capacityExhausted()")
				&& projection >= 0 && commit > projection
				&& queue.contains("ClientEntityActionSyncQueue::clearPending")
				&& queue.contains("acceptsPackets(currentListener)")
				&& queue.contains("consumeDisconnectRequest(mc.getConnection())"),
				"capacity exhaustion must be explicit, transactional, and connection-fatal");
		check(tickHandler.contains(
				"Component entityActionDisconnect = ClientEntityActionSyncQueue.tick(mc)")
				&& tickHandler.contains("mc.isSameThread()")
				&& tickHandler.contains("listener.getConnection().disconnect(reason)"),
				"the client tick must perform the controlled main-thread disconnect");
	}

	private static void testOriginAwareHandlerContract() {
		String action = read(
				"src/main/java/rotp/core/powersystem/entityaction/netcode/TrEntityActionInstancePacket.java");
		String data = read(
				"src/main/java/rotp/core/powersystem/entityaction/syncdata/TrActionSynchedDataPacket.java");
		String phase = read(
				"src/main/java/rotp/core/powersystem/entityaction/netcode/TrEntityActionPhaseTimePacket.java");
		String obb = read(
				"src/main/java/rotp/core/powersystem/entityaction/netcode/TrEntityActionWithOBBSyncPacket.java");
		String target = read(
				"src/main/java/rotp/core/powersystem/entityaction/netcode/TrEntityActionTargetPacket.java");
		check(normalize(action).contains(
				"ClientEntityActionSyncQueue.applyOrQueueAction( context.listener(), payload.performerId")
				&& normalize(data).contains(
						"ClientEntityActionSyncQueue.applyOrQueueSynchedData( context.listener(), payload)")
				&& normalize(phase).contains(
						"ClientEntityActionSyncQueue.applyOrQueuePhaseTime( context.listener(), payload)")
				&& normalize(obb).contains(
						"ClientEntityActionSyncQueue.applyOrQueueObbSync( context.listener(), payload)")
				&& normalize(target).contains(
						"ClientEntityActionSyncQueue.applyOrQueueTarget( context.listener(), payload)"),
				"all five payload handlers must pass their immutable context listener origin");

		String queue = read(
				"src/main/java/rotp/core/powersystem/entityaction/netcode/ClientEntityActionSyncQueue.java");
		int begin = queue.indexOf("private static boolean beginPacket(");
		int capture = queue.indexOf(
				"ICommonPacketListener currentListener = mc.getConnection()", begin);
		int admission = queue.indexOf("admitPacketOrigin(", capture);
		int ensure = queue.indexOf(
				"ensureCurrentEpoch(currentLevel, currentListener)", admission);
		int rejected = queue.indexOf("recordOriginDrop(", ensure);
		int capacity = queue.indexOf(
				"CONNECTION_EPOCH.acceptsPackets(currentListener)", rejected);
		check(begin >= 0 && capture > begin && admission > capture
				&& ensure > admission && rejected > ensure && capacity > rejected,
				"origin identity must gate epoch mutation before capacity admission");
		check(queue.contains("ClientNetworkFailureLogLimiter.acquire(")
				&& queue.contains(
						"null, performerId, performerUuid, generation, actionData")
				&& queue.contains("applyOrQueueSynchedData(null, packet)")
				&& queue.contains("applyOrQueuePhaseTime(null, packet)")
				&& queue.contains("applyOrQueueObbSync(null, packet)")
				&& queue.contains("applyOrQueueTarget(null, packet)"),
				"stale-origin diagnostics must be bounded and legacy overloads fail closed");

		assertPublicMethod(
				"applyOrQueueAction",
				ICommonPacketListener.class, int.class, UUID.class,
				long.class, byte[].class);
		assertPublicMethod(
				"applyOrQueueSynchedData",
				ICommonPacketListener.class, TrActionSynchedDataPacket.class);
		assertPublicMethod(
				"applyOrQueuePhaseTime",
				ICommonPacketListener.class, TrEntityActionPhaseTimePacket.class);
		assertPublicMethod(
				"applyOrQueueObbSync",
				ICommonPacketListener.class,
				TrEntityActionWithOBBSyncPacket.class);
		assertPublicMethod(
				"applyOrQueueTarget",
				ICommonPacketListener.class,
				TrEntityActionTargetPacket.class);
		assertPublicMethod(
				"applyOrQueueAction",
				int.class, UUID.class, long.class, byte[].class);
		assertPublicMethod(
				"applyOrQueueSynchedData", TrActionSynchedDataPacket.class);
		assertPublicMethod(
				"applyOrQueuePhaseTime", TrEntityActionPhaseTimePacket.class);
		assertPublicMethod(
				"applyOrQueueObbSync", TrEntityActionWithOBBSyncPacket.class);
		assertPublicMethod(
				"applyOrQueueTarget", TrEntityActionTargetPacket.class);
	}

	private static void testOriginDropDiagnosticSourceContract() {
		String queue = normalize(read(
				"src/main/java/rotp/core/powersystem/entityaction/netcode/ClientEntityActionSyncQueue.java"));
		int diagnosticStart = queue.indexOf(
				"private static void recordOriginDrop(");
		int diagnosticEnd = queue.indexOf(
				"private static void clearPending()", diagnosticStart);
		check(diagnosticStart >= 0 && diagnosticEnd > diagnosticStart,
				"the stale-origin diagnostic closure must remain locally bounded");
		String diagnostic = queue.substring(diagnosticStart, diagnosticEnd);
		check(diagnostic.contains(
				"Dropped stale client entity action {} payload.")
				&& diagnostic.contains(
						"Packet listener origin {} differs from current listener {}.")
				&& diagnostic.contains(
						"{} similar drops were suppressed.")
				&& diagnostic.contains("originListener, currentListener")
				&& diagnostic.contains(
						"Long.valueOf(decision.suppressedCount())")
				&& !diagnostic.contains("listenerIdentity(")
				&& !diagnostic.contains("new Object["),
				"stale-origin diagnostics must retain evidence without local formatting allocation");
	}

	private static void testUuidWireSourceContract() {
		String action = read(
				"src/main/java/rotp/core/powersystem/entityaction/netcode/TrEntityActionInstancePacket.java");
		String data = read(
				"src/main/java/rotp/core/powersystem/entityaction/syncdata/TrActionSynchedDataPacket.java");
		String phase = read(
				"src/main/java/rotp/core/powersystem/entityaction/netcode/TrEntityActionPhaseTimePacket.java");
		String obb = read(
				"src/main/java/rotp/core/powersystem/entityaction/netcode/TrEntityActionWithOBBSyncPacket.java");
		String target = read(
				"src/main/java/rotp/core/powersystem/entityaction/netcode/TrEntityActionTargetPacket.java");
		check(action.contains("UUID performerUuid")
				&& action.contains("buf.writeUUID(packet.performerUuid)")
				&& action.contains("UUID performerUuid = buf.readUUID()"),
				"root action payload must round-trip the performer UUID");
		for (String dependent : new String[] {data, phase, obb, target}) {
			check(dependent.contains("UUID performerUuid")
					&& ((dependent.contains("writeUUID")
							&& dependent.contains("readUUID"))
							|| dependent.contains("UUIDUtil.STREAM_CODEC")),
					"every dependent action payload must round-trip the performer UUID");
		}
	}

	private static EntityIdentity identity(int entityId, long uuidTail) {
		return new EntityIdentity(entityId, new UUID(0L, uuidTail));
	}

	private static GenerationLedger acceptedLedger(LedgerAccess access) {
		check(access.result() == CapacityResult.ACCEPTED
				&& access.ledger() != null,
				"expected an accepted generation ledger access");
		return access.ledger();
	}

	private static void assertPublicMethod(
			String name, Class<?>... parameterTypes) {
		try {
			ClientEntityActionSyncQueue.class.getMethod(name, parameterTypes);
		}
		catch (NoSuchMethodException error) {
			throw new AssertionError(
					"missing origin-aware or compatibility queue method " + name,
					error);
		}
	}

	private static String normalize(String source) {
		return source.replaceAll("\\s+", " ");
	}

	private static String read(String path) {
		try {
			return Files.readString(Path.of(path));
		}
		catch (IOException error) {
			throw new AssertionError("failed to read " + path, error);
		}
	}

	private static <T extends Throwable> T expectThrows(
			Class<T> expected, Runnable action, String message) {
		try {
			action.run();
		}
		catch (Throwable actual) {
			if (expected.isInstance(actual)) {
				return expected.cast(actual);
			}
			throw new AssertionError(message, actual);
		}
		throw new AssertionError(message);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
