package com.github.standobyte.jojo.powersystem.entityaction.netcode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.github.standobyte.jojo.api.network.ClientAbilityNetworkDiagnostics;
import com.github.standobyte.jojo.api.network.AbilityNetworkDiagnostics.Stage;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.network.ClientNetworkFailureLogLimiter;
import com.github.standobyte.jojo.network.ClientNetworkFailureLogLimiter.Decision;
import com.github.standobyte.jojo.network.NetworkPayloadValidation;
import com.github.standobyte.jojo.powersystem.entityaction.ActionOBB;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction.TransactionSnapshot;
import com.github.standobyte.jojo.powersystem.entityaction.syncdata.SyncActionInstanceData;
import com.github.standobyte.jojo.powersystem.entityaction.syncdata.TrActionSynchedDataPacket;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;

public final class ClientEntityActionSyncQueue {
	private static final int TTL_TICKS = 40;
	private static final int MAX_PENDING_ENTITIES = 256;
	private static final int MAX_PENDING_PER_ENTITY = 16;
	private static final int MAX_ORPHAN_GENERATION_LEDGERS =
			MAX_PENDING_ENTITIES * 4;
	private static final Component CAPACITY_DISCONNECT_REASON = Component.literal(
			"Ripples of the Past entity action synchronization capacity was exhausted; reconnect required.");

	private static final Map<EntityIdentity, PendingAction> PENDING_ACTIONS =
			new LinkedHashMap<>();
	private static final Map<EntityIdentity, List<Pending<TrActionSynchedDataPacket>>>
			PENDING_SYNCHED_DATA = new LinkedHashMap<>();
	private static final Map<EntityIdentity, List<Pending<TrEntityActionPhaseTimePacket>>>
			PENDING_PHASE_TIMES = new LinkedHashMap<>();
	private static final Map<EntityIdentity, List<Pending<TrEntityActionWithOBBSyncPacket>>>
			PENDING_OBB_SYNCS = new LinkedHashMap<>();
	private static final GenerationLedgerRegistry GENERATIONS =
			new GenerationLedgerRegistry(MAX_ORPHAN_GENERATION_LEDGERS);
	private static final ConnectionEpoch CONNECTION_EPOCH = new ConnectionEpoch();

	private ClientEntityActionSyncQueue() {}

	public enum ApplyResult {
		APPLIED,
		REJECTED,
		RETRY
	}

	@Nullable
	public static Component tick(Minecraft mc) {
		ensureCurrentEpoch(mc);
		if (!CONNECTION_EPOCH.acceptsPackets(mc.getConnection())) {
			return consumeCapacityDisconnectReason(mc);
		}
		if (mc.level == null) {
			clearPending();
			return null;
		}
		applyPendingActions();
		applyPendingList(PENDING_SYNCHED_DATA,
				ClientEntityActionSyncQueue::applySynchedData);
		applyPendingList(PENDING_PHASE_TIMES,
				ClientEntityActionSyncQueue::applyPhaseTime);
		applyPendingList(PENDING_OBB_SYNCS,
				ClientEntityActionSyncQueue::applyObbSync);
		expirePending();
		if (pruneGenerationLedgers(mc) == CapacityResult.CAPACITY_EXHAUSTED) {
			failCapacity(mc);
		}
		return consumeCapacityDisconnectReason(mc);
	}

	public static void applyOrQueueAction(
			ICommonPacketListener originListener,
			int performerId,
			UUID performerUuid,
			long generation,
			@Nullable byte[] actionData) {
		Minecraft mc = Minecraft.getInstance();
		if (!beginPacket(mc, originListener, "action")) {
			return;
		}
		NetworkPayloadValidation.requireGeneration(
				generation, "entity action");
		EntityIdentity identity = new EntityIdentity(
				performerId, performerUuid);
		LivingEntity entityLifetime = findLiving(identity);
		GenerationLedger ledger = ledgerFor(identity);
		if (ledger == null) {
			return;
		}
		ActionReservation reservation = ledger.reserveAction(
				generation, actionData, entityLifetime);
		switch (reservation) {
			case SUPERSEDED, DUPLICATE, REJECTED -> {
				return;
			}
			case CONFLICT -> {
				rejectPendingActionConflict(identity, generation);
				return;
			}
			case FRESH -> {
				removePendingActionBefore(identity, generation);
				clearDependentPendingBefore(identity, generation);
			}
			case LIFETIME_REPLAY -> {
				removePendingActionBefore(identity, generation);
				removePendingAction(identity, generation);
				clearDependentPendingBefore(identity, generation);
				clearActionDependentPending(identity, generation);
			}
			case PENDING -> clearDependentPendingBefore(
					identity, generation);
		}

		byte[] reservedActionData = ledger.pendingActionData(generation);
		ApplyResult result = applyAction(
				identity, entityLifetime, generation, reservedActionData);
		if (result == ApplyResult.RETRY) {
			queueAction(identity, generation, reservedActionData);
			ClientAbilityNetworkDiagnostics.recordAction(
					Stage.CLIENT_ACTION_SYNC_QUEUED,
					null,
					identity.entityId(),
					null,
					generation,
					"entity_not_loaded:generation=" + generation);
			return;
		}
		completeActionResult(
				identity, generation, result, entityLifetime);
	}

	/** @deprecated Network handlers must pass their payload context listener. */
	@Deprecated(forRemoval = false)
	public static void applyOrQueueAction(
			int performerId,
			UUID performerUuid,
			long generation,
			@Nullable byte[] actionData) {
		applyOrQueueAction(
				null, performerId, performerUuid, generation, actionData);
	}

	public static void applyOrQueueSynchedData(
			ICommonPacketListener originListener,
			TrActionSynchedDataPacket packet) {
		if (!beginPacket(
				Minecraft.getInstance(), originListener, "synched_data")) {
			return;
		}
		handleDependent(
				PENDING_SYNCHED_DATA,
				new EntityIdentity(
						packet.entityId(), packet.performerUuid()),
				packet.actionGeneration(),
				packet,
				ClientEntityActionSyncQueue::applySynchedData);
	}

	/** @deprecated Network handlers must pass their payload context listener. */
	@Deprecated(forRemoval = false)
	public static void applyOrQueueSynchedData(
			TrActionSynchedDataPacket packet) {
		applyOrQueueSynchedData(null, packet);
	}

	public static void applyOrQueuePhaseTime(
			ICommonPacketListener originListener,
			TrEntityActionPhaseTimePacket packet) {
		if (!beginPacket(
				Minecraft.getInstance(), originListener, "phase_time")) {
			return;
		}
		handleDependent(
				PENDING_PHASE_TIMES,
				new EntityIdentity(
						packet.performerId(), packet.performerUuid()),
				packet.actionGeneration(),
				packet,
				ClientEntityActionSyncQueue::applyPhaseTime);
	}

	/** @deprecated Network handlers must pass their payload context listener. */
	@Deprecated(forRemoval = false)
	public static void applyOrQueuePhaseTime(
			TrEntityActionPhaseTimePacket packet) {
		applyOrQueuePhaseTime(null, packet);
	}

	public static void applyOrQueueObbSync(
			ICommonPacketListener originListener,
			TrEntityActionWithOBBSyncPacket packet) {
		if (!beginPacket(
				Minecraft.getInstance(), originListener, "obb_sync")) {
			return;
		}
		handleDependent(
				PENDING_OBB_SYNCS,
				new EntityIdentity(
						packet.performerId(), packet.performerUuid()),
				packet.actionGeneration(),
				packet,
				ClientEntityActionSyncQueue::applyObbSync);
	}

	/** @deprecated Network handlers must pass their payload context listener. */
	@Deprecated(forRemoval = false)
	public static void applyOrQueueObbSync(
			TrEntityActionWithOBBSyncPacket packet) {
		applyOrQueueObbSync(null, packet);
	}

	private static <T> void handleDependent(
			Map<EntityIdentity, List<Pending<T>>> map,
			EntityIdentity identity,
			long generation,
			T payload,
			PayloadApplier<T> applier) {
		NetworkPayloadValidation.requireGeneration(
				generation, "entity action dependency");
		GenerationLedger ledger = ledgerFor(identity);
		if (ledger == null) {
			return;
		}
		switch (ledger.classifyDependency(generation)) {
			case DROP -> {
				return;
			}
			case WAIT -> {
				queue(map, identity, generation, payload);
				return;
			}
			case READY -> {}
		}

		ApplyResult result = applier.apply(payload);
		if (result == ApplyResult.RETRY) {
			queue(map, identity, generation, payload);
		}
		else if (result == ApplyResult.REJECTED) {
			ledger.markRejected(generation);
			clearActionDependentPending(identity, generation);
		}
	}

	private static void queueAction(
			EntityIdentity identity,
			long generation,
			@Nullable byte[] actionData) {
		PendingAction pending = new PendingAction(generation, actionData);
		PendingAction existing = PENDING_ACTIONS.putIfAbsent(
				identity, pending);
		if (existing != null
				&& (existing.generation != generation
						|| !Arrays.equals(existing.actionData, actionData))) {
			throw new IllegalStateException(
					"Pending entity action bytes changed after reservation");
		}
		trimPendingActions();
	}

	private static <T> void queue(
			Map<EntityIdentity, List<Pending<T>>> map,
			EntityIdentity identity,
			long generation,
			T payload) {
		List<Pending<T>> pending = map.computeIfAbsent(
				identity, key -> new ArrayList<>());
		pending.add(new Pending<>(generation, payload));
		while (pending.size() > MAX_PENDING_PER_ENTITY) {
			pending.remove(0);
		}
		trimMap(map);
	}

	private static void applyPendingActions() {
		List<CompletedGeneration> completed = new ArrayList<>();
		Iterator<Map.Entry<EntityIdentity, PendingAction>> iterator =
				PENDING_ACTIONS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<EntityIdentity, PendingAction> entry = iterator.next();
			PendingAction pending = entry.getValue();
			GenerationLedger ledger = GENERATIONS.get(entry.getKey());
			LivingEntity entityLifetime = findLiving(entry.getKey());
			if (ledger == null
					|| ledger.reserveAction(
							pending.generation, pending.actionData,
							entityLifetime)
							!= ActionReservation.PENDING) {
				iterator.remove();
				continue;
			}
			ApplyResult result = applyAction(
					entry.getKey(), entityLifetime,
					pending.generation, pending.actionData);
			if (result == ApplyResult.RETRY) {
				continue;
			}
			iterator.remove();
			completed.add(new CompletedGeneration(
					entry.getKey(), pending.generation, result,
					entityLifetime));
		}
		for (CompletedGeneration completion : completed) {
			completeActionResult(
					completion.identity(), completion.generation(),
					completion.result(), completion.entityLifetime());
		}
	}

	private static void completeActionResult(
			EntityIdentity identity,
			long generation,
			ApplyResult result,
			LivingEntity entityLifetime) {
		GenerationLedger ledger = GENERATIONS.get(identity);
		if (ledger == null) {
			return;
		}
		if (result == ApplyResult.APPLIED) {
			removePendingAction(identity, generation);
			EntityActionInstance action =
					LivingComponentAction.getCurEntityAction(entityLifetime);
			ledger.markApplied(
					generation, entityLifetime, action != null);
			clearDependentPendingBefore(identity, generation);
			if (action != null) {
				applyPendingFor(identity, generation);
			}
			else {
				clearActionDependentPending(identity, generation);
			}
		}
		else if (result == ApplyResult.REJECTED) {
			removePendingAction(identity, generation);
			ledger.markRejected(generation);
			clearActionDependentPending(identity, generation);
		}
	}

	private static <T> void applyPendingList(
			Map<EntityIdentity, List<Pending<T>>> map,
			PayloadApplier<T> applier) {
		List<FailedGeneration> failed = new ArrayList<>();
		Iterator<Map.Entry<EntityIdentity, List<Pending<T>>>> mapIterator =
				map.entrySet().iterator();
		while (mapIterator.hasNext()) {
			Map.Entry<EntityIdentity, List<Pending<T>>> entry = mapIterator.next();
			GenerationLedger ledger = GENERATIONS.get(entry.getKey());
			Iterator<Pending<T>> listIterator = entry.getValue().iterator();
			while (listIterator.hasNext()) {
				Pending<T> pending = listIterator.next();
				DependencyDisposition disposition = ledger != null
						? ledger.classifyDependency(pending.generation)
						: DependencyDisposition.WAIT;
				if (disposition == DependencyDisposition.WAIT) {
					continue;
				}
				if (disposition == DependencyDisposition.DROP) {
					listIterator.remove();
					continue;
				}
				ApplyResult result = applier.apply(pending.payload);
				if (result == ApplyResult.RETRY) {
					continue;
				}
				listIterator.remove();
				if (result == ApplyResult.REJECTED) {
					failed.add(new FailedGeneration(
							entry.getKey(), pending.generation));
					break;
				}
			}
			if (entry.getValue().isEmpty()) {
				mapIterator.remove();
			}
		}
		for (FailedGeneration failure : failed) {
			GenerationLedger ledger = GENERATIONS.get(failure.identity());
			if (ledger != null) {
				ledger.markRejected(failure.generation());
			}
			clearActionDependentPending(
					failure.identity(), failure.generation());
		}
	}

	private static void applyPendingFor(
			EntityIdentity identity, long generation) {
		if (applyPendingFor(
				PENDING_SYNCHED_DATA, identity, generation,
				ClientEntityActionSyncQueue::applySynchedData)
				== ApplyResult.REJECTED) {
			rejectGeneration(identity, generation);
			return;
		}
		if (applyPendingFor(
				PENDING_PHASE_TIMES, identity, generation,
				ClientEntityActionSyncQueue::applyPhaseTime)
				== ApplyResult.REJECTED) {
			rejectGeneration(identity, generation);
			return;
		}
		if (applyPendingFor(
				PENDING_OBB_SYNCS, identity, generation,
				ClientEntityActionSyncQueue::applyObbSync)
				== ApplyResult.REJECTED) {
			rejectGeneration(identity, generation);
		}
	}

	static <T> ApplyResult applyPendingFor(
			Map<EntityIdentity, List<Pending<T>>> map,
			EntityIdentity identity,
			long generation,
			PayloadApplier<T> applier) {
		List<Pending<T>> pendingList = map.get(identity);
		if (pendingList == null) {
			return ApplyResult.APPLIED;
		}
		ApplyResult aggregate = ApplyResult.APPLIED;
		Iterator<Pending<T>> iterator = pendingList.iterator();
		while (iterator.hasNext()) {
			Pending<T> pending = iterator.next();
			if (pending.generation != generation) {
				continue;
			}
			ApplyResult result = applier.apply(pending.payload);
			if (result == ApplyResult.RETRY) {
				aggregate = ApplyResult.RETRY;
				continue;
			}
			iterator.remove();
			if (result == ApplyResult.REJECTED) {
				aggregate = ApplyResult.REJECTED;
				break;
			}
		}
		if (pendingList.isEmpty()) {
			map.remove(identity);
		}
		return aggregate;
	}

	private static ApplyResult applyAction(
			EntityIdentity identity,
			@Nullable LivingEntity living,
			long generation,
			@Nullable byte[] actionData) {
		if (living == null) {
			return ApplyResult.RETRY;
		}
		LivingComponentAction component =
				LivingComponentAction.getComponent(living);
		TransactionSnapshot snapshot = component.captureTransactionSnapshot();
		FriendlyByteBuf input = null;
		try {
			if (actionData == null || actionData.length == 0) {
				throw new IllegalArgumentException(
						"Missing entity action payload data");
			}
			NetworkPayloadValidation.requireByteLength(
					actionData.length,
					NetworkPayloadValidation.MAX_ENTITY_ACTION_BYTES,
					"entity action");
			boolean encodedActionPresent = actionData[0] != 0;
			input = new FriendlyByteBuf(Unpooled.wrappedBuffer(actionData));
			EntityActionInstance action = EntityActionInstance.decode(
					living.level(), input);
			if (encodedActionPresent && action == null) {
				throw new IllegalArgumentException(
						"Entity action type could not be resolved");
			}
			if (input.isReadable()) {
				throw new IllegalArgumentException(
						"Entity action payload has trailing bytes: "
								+ input.readableBytes());
			}
			component.setActionFromNetwork(action, generation);
			if (component.getAction() != action
					|| component.actionGeneration() != generation) {
				throw new IllegalStateException(
						"Entity action callback rejected the authoritative state");
			}
			ClientAbilityNetworkDiagnostics.recordAction(
					Stage.CLIENT_ACTION_SYNC_APPLIED,
					living,
					living.getId(),
					action,
					generation,
					(action != null ? "action_applied" : "action_cleared")
							+ ":generation=" + generation);
			return ApplyResult.APPLIED;
		}
		catch (RuntimeException error) {
			try {
				component.rollbackFailedInputAction(snapshot);
			}
			catch (RuntimeException rollbackError) {
				error.addSuppressed(rollbackError);
			}
			recordActionRejection(
					living, identity.entityId(), generation, error);
			return ApplyResult.REJECTED;
		}
		finally {
			if (input != null) {
				input.release();
			}
		}
	}

	private static void recordActionRejection(
			@Nullable LivingEntity living,
			int entityId,
			long generation,
			RuntimeException error) {
		ClientAbilityNetworkDiagnostics.recordAction(
				Stage.CLIENT_ACTION_SYNC_REJECTED,
				living,
				entityId,
				null,
				generation,
				error.getClass().getName() + ":generation=" + generation);
		Decision decision = ClientNetworkFailureLogLimiter.acquire(
				"entity_action_sync",
				Integer.toString(entityId),
				error.getClass());
		if (!decision.logStackTrace()) {
			return;
		}
		if (decision.suppressedCount() > 0L) {
			JojoMod.getLogger().warn(
					"Skipping client entity action sync for performer {}; {} similar failures were suppressed.",
					entityId, decision.suppressedCount(), error);
		}
		else {
			JojoMod.getLogger().warn(
					"Skipping client entity action sync for performer {}.",
					entityId, error);
		}
	}

	private static void rejectPendingActionConflict(
			EntityIdentity identity, long generation) {
		removePendingAction(identity, generation);
		clearActionDependentPending(identity, generation);
		recordActionRejection(
				findLiving(identity),
				identity.entityId(),
				generation,
				new IllegalStateException(
						"Conflicting bytes for pending entity action generation"));
	}

	private static ApplyResult applySynchedData(
			TrActionSynchedDataPacket packet) {
		LivingEntity living = findLiving(new EntityIdentity(
				packet.entityId(), packet.performerUuid()));
		EntityActionInstance action = matchingAction(
				living, packet.actionGeneration());
		if (living == null || action == null) {
			return ApplyResult.RETRY;
		}
		try {
			SyncActionInstanceData.setDataClientSide(
					living, packet.packedItems());
			recordDependentApplied(
					living, action, packet.actionGeneration(),
					"synched_data");
			return ApplyResult.APPLIED;
		}
		catch (RuntimeException error) {
			containDependentFailure(
					living, action, packet.actionGeneration(),
					"synched_data", error);
			return ApplyResult.REJECTED;
		}
	}

	private static ApplyResult applyPhaseTime(
			TrEntityActionPhaseTimePacket packet) {
		LivingEntity living = findLiving(new EntityIdentity(
				packet.performerId(), packet.performerUuid()));
		EntityActionInstance action = matchingAction(
				living, packet.actionGeneration());
		if (living == null || action == null) {
			return ApplyResult.RETRY;
		}
		if (action.id != packet.actionId()) {
			RuntimeException error = new IllegalStateException(
					"Phase sync action ID does not match its generation");
			containDependentFailure(
					living, action, packet.actionGeneration(),
					"phase_time", error);
			return ApplyResult.REJECTED;
		}
		try {
			action.phasesLength = packet.phasesLength();
			action.setPhase(packet.phase(), packet.curPhaseTick());
			recordDependentApplied(
					living, action, packet.actionGeneration(),
					"phase_time");
			return ApplyResult.APPLIED;
		}
		catch (RuntimeException error) {
			containDependentFailure(
					living, action, packet.actionGeneration(),
					"phase_time", error);
			return ApplyResult.REJECTED;
		}
	}

	private static ApplyResult applyObbSync(
			TrEntityActionWithOBBSyncPacket packet) {
		LivingEntity living = findLiving(new EntityIdentity(
				packet.performerId(), packet.performerUuid()));
		EntityActionInstance action = matchingAction(
				living, packet.actionGeneration());
		if (living == null || action == null) {
			return ApplyResult.RETRY;
		}
		if (action.id != packet.actionId()
				|| !(action instanceof ActionOBB obbAction)
				|| obbAction.extendableOBB() == null) {
			RuntimeException error = new IllegalStateException(
					"OBB sync does not match the current action generation");
			containDependentFailure(
					living, action, packet.actionGeneration(),
					"obb", error);
			return ApplyResult.REJECTED;
		}
		try {
			obbAction.extendableOBB().setIsMovingForward(false);
			obbAction.extendableOBB().setIsRetracting(true);
			recordDependentApplied(
					living, action, packet.actionGeneration(), "obb");
			return ApplyResult.APPLIED;
		}
		catch (RuntimeException error) {
			containDependentFailure(
					living, action, packet.actionGeneration(),
					"obb", error);
			return ApplyResult.REJECTED;
		}
	}

	@Nullable
	private static EntityActionInstance matchingAction(
			@Nullable LivingEntity living,
			long generation) {
		if (living == null) {
			return null;
		}
		LivingComponentAction component =
				LivingComponentAction.getComponent(living);
		EntityActionInstance action = component.getAction();
		return action != null
				&& component.actionGeneration() == generation
				&& action.networkGeneration() == generation
						? action : null;
	}

	private static void containDependentFailure(
			LivingEntity living,
			EntityActionInstance action,
			long generation,
			String syncType,
			RuntimeException error) {
		try {
			LivingComponentAction.getComponent(living)
					.clearFailedNetworkAction();
		}
		catch (RuntimeException cleanupError) {
			error.addSuppressed(cleanupError);
		}
		ClientAbilityNetworkDiagnostics.recordAction(
				Stage.CLIENT_ACTION_DEPENDENT_SYNC_REJECTED,
				living,
				living.getId(),
				action,
				generation,
				syncType + ':' + error.getClass().getName()
						+ ":generation=" + generation);
		Decision decision = ClientNetworkFailureLogLimiter.acquire(
				"entity_action_dependent_sync_" + syncType,
				Integer.toString(living.getId()),
				error.getClass());
		if (!decision.logStackTrace()) {
			return;
		}
		if (decision.suppressedCount() > 0L) {
			JojoMod.getLogger().warn(
					"Rejected client entity action {} update for performer {}; {} similar failures were suppressed.",
					syncType, living.getId(), decision.suppressedCount(), error);
		}
		else {
			JojoMod.getLogger().warn(
					"Rejected client entity action {} update for performer {}.",
					syncType, living.getId(), error);
		}
	}

	private static void recordDependentApplied(
			LivingEntity living,
			EntityActionInstance action,
			long generation,
			String syncType) {
		if (!Minecraft.getInstance().isSameThread()) {
			throw new IllegalStateException(
					"Entity action dependent sync applied off the client thread");
		}
		DependentSyncDiagnostic diagnostic =
				dependentSyncAppliedDiagnostic(syncType, generation);
		ClientAbilityNetworkDiagnostics.recordAction(
				diagnostic.stage(),
				living,
				living.getId(),
				action,
				generation,
				diagnostic.detail());
	}

	static DependentSyncDiagnostic dependentSyncAppliedDiagnostic(
			String syncType, long generation) {
		String prefix = switch (syncType) {
			case "synched_data", "phase_time", "obb" -> syncType;
			default -> throw new IllegalArgumentException(
					"Unknown entity action dependent sync type " + syncType);
		};
		return new DependentSyncDiagnostic(
				Stage.CLIENT_ACTION_DEPENDENT_SYNC_APPLIED,
				prefix + ":generation=" + generation);
	}

	private static void rejectGeneration(
			EntityIdentity identity, long generation) {
		GenerationLedger ledger = GENERATIONS.get(identity);
		if (ledger != null) {
			ledger.markRejected(generation);
		}
		clearActionDependentPending(identity, generation);
	}

	@Nullable
	private static LivingEntity findLiving(EntityIdentity identity) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return null;
		}
		Entity entity = mc.level.getEntity(identity.entityId());
		return entity instanceof LivingEntity living
				&& identity.performerUuid().equals(living.getUUID())
						? living : null;
	}

	private static void expirePending() {
		Iterator<Map.Entry<EntityIdentity, PendingAction>> actionIterator =
				PENDING_ACTIONS.entrySet().iterator();
		List<FailedGeneration> expiredActions = new ArrayList<>();
		while (actionIterator.hasNext()) {
			Map.Entry<EntityIdentity, PendingAction> entry = actionIterator.next();
			if (entry.getValue().tickExpired()) {
				expiredActions.add(new FailedGeneration(
						entry.getKey(), entry.getValue().generation));
				actionIterator.remove();
			}
		}
		for (FailedGeneration expired : expiredActions) {
			rejectGeneration(expired.identity(), expired.generation());
		}
		expireListMap(PENDING_SYNCHED_DATA);
		expireListMap(PENDING_PHASE_TIMES);
		expireListMap(PENDING_OBB_SYNCS);
	}

	private static <T> void expireListMap(
			Map<EntityIdentity, List<Pending<T>>> map) {
		Iterator<Map.Entry<EntityIdentity, List<Pending<T>>>> mapIterator =
				map.entrySet().iterator();
		while (mapIterator.hasNext()) {
			List<Pending<T>> pendingList = mapIterator.next().getValue();
			pendingList.removeIf(Pending::tickExpired);
			if (pendingList.isEmpty()) {
				mapIterator.remove();
			}
		}
	}

	private static void clearDependentPendingBefore(
			EntityIdentity identity, long generation) {
		removePendingBefore(PENDING_SYNCHED_DATA, identity, generation);
		removePendingBefore(PENDING_PHASE_TIMES, identity, generation);
		removePendingBefore(PENDING_OBB_SYNCS, identity, generation);
	}

	private static void removePendingAction(
			EntityIdentity identity, long generation) {
		PendingAction pending = PENDING_ACTIONS.get(identity);
		if (pending != null && pending.generation == generation) {
			PENDING_ACTIONS.remove(identity);
		}
	}

	private static void removePendingActionBefore(
			EntityIdentity identity, long generation) {
		PendingAction pending = PENDING_ACTIONS.get(identity);
		if (pending != null && pending.generation < generation) {
			PENDING_ACTIONS.remove(identity);
		}
	}

	private static <T> void removePendingBefore(
			Map<EntityIdentity, List<Pending<T>>> map,
			EntityIdentity identity,
			long generation) {
		List<Pending<T>> pending = map.get(identity);
		if (pending == null) {
			return;
		}
		pending.removeIf(entry -> entry.generation < generation);
		if (pending.isEmpty()) {
			map.remove(identity);
		}
	}

	private static void clearActionDependentPending(
			EntityIdentity identity, long generation) {
		removePendingGeneration(PENDING_SYNCHED_DATA, identity, generation);
		removePendingGeneration(PENDING_PHASE_TIMES, identity, generation);
		removePendingGeneration(PENDING_OBB_SYNCS, identity, generation);
	}

	static <T> void removePendingGeneration(
			Map<EntityIdentity, List<Pending<T>>> map,
			EntityIdentity identity,
			long generation) {
		List<Pending<T>> pending = map.get(identity);
		if (pending == null) {
			return;
		}
		pending.removeIf(entry -> entry.generation == generation);
		if (pending.isEmpty()) {
			map.remove(identity);
		}
	}

	private static void ensureCurrentEpoch(Minecraft mc) {
		ensureCurrentEpoch(mc.level, mc.getConnection());
	}

	private static void ensureCurrentEpoch(
			@Nullable Object currentLevel,
			@Nullable Object currentConnection) {
		if (CONNECTION_EPOCH.update(currentLevel, currentConnection)) {
			clearPending();
		}
	}

	private static boolean beginPacket(
			Minecraft mc,
			@Nullable ICommonPacketListener originListener,
			String syncType) {
		ICommonPacketListener currentListener = mc.getConnection();
		Object currentLevel = mc.level;
		if (!admitPacketOrigin(
				originListener, currentListener,
				() -> ensureCurrentEpoch(currentLevel, currentListener))) {
			recordOriginDrop(syncType, originListener, currentListener);
			return false;
		}
		return CONNECTION_EPOCH.acceptsPackets(currentListener);
	}

	static boolean admitPacketOrigin(
			@Nullable Object originListener,
			@Nullable Object currentListener,
			Runnable admittedAction) {
		Objects.requireNonNull(admittedAction, "admittedAction");
		if (originListener == null || originListener != currentListener) {
			return false;
		}
		admittedAction.run();
		return true;
	}

	private static void recordOriginDrop(
			String syncType,
			@Nullable Object originListener,
			@Nullable Object currentListener) {
		Decision decision = ClientNetworkFailureLogLimiter.acquire(
				"entity_action_origin", syncType, IllegalStateException.class);
		if (!decision.logStackTrace()) {
			return;
		}
		Logger logger = JojoMod.getLogger();
		logger.warn(
				"Dropped stale client entity action {} payload.", syncType);
		logger.warn(
				"Packet listener origin {} differs from current listener {}.",
				originListener, currentListener);
		if (decision.suppressedCount() > 0L) {
			logger.warn(
					"{} similar drops were suppressed.",
					Long.valueOf(decision.suppressedCount()));
		}
	}

	private static void clearPending() {
		PENDING_ACTIONS.clear();
		PENDING_SYNCHED_DATA.clear();
		PENDING_PHASE_TIMES.clear();
		PENDING_OBB_SYNCS.clear();
		GENERATIONS.clear();
	}

	@Nullable
	private static GenerationLedger ledgerFor(EntityIdentity identity) {
		LedgerAccess access = GENERATIONS.getOrCreate(
				identity, findLiving(identity) != null);
		if (access.result() == CapacityResult.CAPACITY_EXHAUSTED) {
			failCapacity(Minecraft.getInstance());
			return null;
		}
		return access.ledger();
	}

	private static CapacityResult pruneGenerationLedgers(Minecraft mc) {
		return GENERATIONS.sweep(
				identity -> {
					if (mc.level == null) {
						return false;
					}
					Entity entity = mc.level.getEntity(identity.entityId());
					return entity instanceof LivingEntity living
							&& identity.performerUuid().equals(living.getUUID());
				},
				ClientEntityActionSyncQueue::hasPending).result();
	}

	private static void failCapacity(Minecraft mc) {
		CONNECTION_EPOCH.failCapacity(
				mc.level, mc.getConnection(),
				ClientEntityActionSyncQueue::clearPending);
	}

	@Nullable
	private static Component consumeCapacityDisconnectReason(Minecraft mc) {
		return CONNECTION_EPOCH.consumeDisconnectRequest(mc.getConnection())
				? CAPACITY_DISCONNECT_REASON : null;
	}

	private static boolean hasPending(EntityIdentity identity) {
		return PENDING_ACTIONS.containsKey(identity)
				|| PENDING_SYNCHED_DATA.containsKey(identity)
				|| PENDING_PHASE_TIMES.containsKey(identity)
				|| PENDING_OBB_SYNCS.containsKey(identity);
	}

	private static void trimPendingActions() {
		while (PENDING_ACTIONS.size() > MAX_PENDING_ENTITIES) {
			Iterator<Map.Entry<EntityIdentity, PendingAction>> iterator =
					PENDING_ACTIONS.entrySet().iterator();
			if (!iterator.hasNext()) {
				return;
			}
			Map.Entry<EntityIdentity, PendingAction> evicted = iterator.next();
			iterator.remove();
			GenerationLedger ledger = GENERATIONS.get(evicted.getKey());
			if (ledger != null) {
				ledger.markRejected(evicted.getValue().generation);
			}
			clearActionDependentPending(
					evicted.getKey(), evicted.getValue().generation);
		}
	}

	private static void trimMap(Map<EntityIdentity, ?> map) {
		while (map.size() > MAX_PENDING_ENTITIES) {
			Iterator<EntityIdentity> iterator = map.keySet().iterator();
			if (!iterator.hasNext()) {
				return;
			}
			iterator.next();
			iterator.remove();
		}
	}

	@FunctionalInterface
	interface PayloadApplier<T> {
		ApplyResult apply(T payload);
	}

	static class Pending<T> {
		protected final long generation;
		private final T payload;
		private int ticksLeft = TTL_TICKS;

		Pending(long generation, T payload) {
			this.generation = generation;
			this.payload = payload;
		}

		boolean tickExpired() {
			return --ticksLeft <= 0;
		}
	}

	private static final class PendingAction extends Pending<byte[]> {
		@Nullable private final byte[] actionData;

		private PendingAction(
				long generation,
				@Nullable byte[] actionData) {
			super(generation,
					actionData != null ? actionData.clone() : null);
			this.actionData = actionData != null ? actionData.clone() : null;
		}
	}

	enum ActionReservation {
		FRESH,
		LIFETIME_REPLAY,
		PENDING,
		DUPLICATE,
		SUPERSEDED,
		REJECTED,
		CONFLICT
	}

	enum DependencyDisposition {
		READY,
		WAIT,
		DROP
	}

	private enum GenerationState {
		NONE,
		PENDING,
		APPLIED,
		REJECTED
	}

	static final class GenerationLedger {
		private long highestActionGeneration;
		private GenerationState state = GenerationState.NONE;
		private boolean currentHasAction;
		@Nullable private byte[] pendingActionData;
		@Nullable private Object appliedEntityLifetime;

		ActionReservation reserveAction(
				long generation,
				@Nullable byte[] actionData) {
			return reserveAction(generation, actionData, null);
		}

		ActionReservation reserveAction(
				long generation,
				@Nullable byte[] actionData,
				@Nullable Object entityLifetime) {
			if (generation <= 0L) {
				throw new IllegalArgumentException(
						"Action generation must be positive");
			}
			if (generation < highestActionGeneration) {
				return ActionReservation.SUPERSEDED;
			}
			if (generation > highestActionGeneration) {
				highestActionGeneration = generation;
				state = GenerationState.PENDING;
				currentHasAction = false;
				pendingActionData = cloneBytes(actionData);
				appliedEntityLifetime = null;
				return ActionReservation.FRESH;
			}
			return switch (state) {
				case PENDING -> {
					if (Arrays.equals(pendingActionData, actionData)) {
						yield ActionReservation.PENDING;
					}
					state = GenerationState.REJECTED;
					currentHasAction = false;
					pendingActionData = null;
					appliedEntityLifetime = null;
					yield ActionReservation.CONFLICT;
				}
				case APPLIED -> {
					if (entityLifetime != null
							&& appliedEntityLifetime != null
							&& entityLifetime != appliedEntityLifetime) {
						state = GenerationState.PENDING;
						currentHasAction = false;
						pendingActionData = cloneBytes(actionData);
						appliedEntityLifetime = null;
						yield ActionReservation.LIFETIME_REPLAY;
					}
					yield ActionReservation.DUPLICATE;
				}
				case REJECTED -> ActionReservation.REJECTED;
				case NONE -> {
					state = GenerationState.PENDING;
					pendingActionData = cloneBytes(actionData);
					appliedEntityLifetime = null;
					yield ActionReservation.FRESH;
				}
			};
		}

		DependencyDisposition classifyDependency(long generation) {
			if (generation < highestActionGeneration) {
				return DependencyDisposition.DROP;
			}
			if (generation > highestActionGeneration) {
				return DependencyDisposition.WAIT;
			}
			return switch (state) {
				case APPLIED -> currentHasAction
						? DependencyDisposition.READY
						: DependencyDisposition.DROP;
				case NONE, PENDING -> DependencyDisposition.WAIT;
				case REJECTED -> DependencyDisposition.DROP;
			};
		}

		void markApplied(long generation, boolean hasAction) {
			markApplied(generation, null, hasAction);
		}

		void markApplied(
				long generation,
				@Nullable Object entityLifetime,
				boolean hasAction) {
			if (generation == highestActionGeneration
					&& state == GenerationState.PENDING) {
				state = GenerationState.APPLIED;
				currentHasAction = hasAction;
				pendingActionData = null;
				appliedEntityLifetime = entityLifetime;
			}
		}

		void markRejected(long generation) {
			if (generation == highestActionGeneration) {
				state = GenerationState.REJECTED;
				currentHasAction = false;
				pendingActionData = null;
				appliedEntityLifetime = null;
			}
		}

		long highestActionGeneration() {
			return highestActionGeneration;
		}

		boolean rejected() {
			return state == GenerationState.REJECTED;
		}

		@Nullable
		byte[] pendingActionData(long generation) {
			return generation == highestActionGeneration
					&& state == GenerationState.PENDING
							? cloneBytes(pendingActionData) : null;
		}

		@Nullable
		private static byte[] cloneBytes(@Nullable byte[] bytes) {
			return bytes != null ? bytes.clone() : null;
		}
	}

	static final class GenerationLedgerRegistry {
		private final int maximumOrphans;
		private final Map<EntityIdentity, LedgerEntry> ledgers =
				new LinkedHashMap<>();

		GenerationLedgerRegistry() {
			this(MAX_ORPHAN_GENERATION_LEDGERS);
		}

		GenerationLedgerRegistry(int maximumOrphans) {
			if (maximumOrphans <= 0) {
				throw new IllegalArgumentException(
						"Maximum orphan generation ledgers must be positive");
			}
			this.maximumOrphans = maximumOrphans;
		}

		LedgerAccess getOrCreate(
				EntityIdentity identity, boolean entityPresent) {
			LedgerEntry existing = ledgers.get(identity);
			int projectedOrphans = orphanCount();
			if (existing != null) {
				if (existing.entityPresent() && !entityPresent) {
					++projectedOrphans;
				}
				else if (!existing.entityPresent() && entityPresent) {
					--projectedOrphans;
				}
				if (projectedOrphans > maximumOrphans) {
					return LedgerAccess.capacityExhausted();
				}
				existing.touch(entityPresent);
				return LedgerAccess.accepted(existing.ledger());
			}
			if (!entityPresent) {
				++projectedOrphans;
			}
			if (projectedOrphans > maximumOrphans) {
				return LedgerAccess.capacityExhausted();
			}
			LedgerEntry created = new LedgerEntry(
					new GenerationLedger(), entityPresent);
			ledgers.put(identity, created);
			return LedgerAccess.accepted(created.ledger());
		}

		@Nullable
		GenerationLedger get(EntityIdentity identity) {
			LedgerEntry entry = ledgers.get(identity);
			return entry != null ? entry.ledger() : null;
		}

		int size() {
			return ledgers.size();
		}

		void clear() {
			ledgers.clear();
		}

		SweepResult sweep(
				Predicate<EntityIdentity> entityPresent,
				Predicate<EntityIdentity> pending) {
			List<LedgerSweep> plan = new ArrayList<>(ledgers.size());
			int expiredEntries = 0;
			int keptEntries = 0;
			int liveToOrphanCandidates = 0;
			int projectedOrphans = 0;
			for (Map.Entry<EntityIdentity, LedgerEntry> entry
					: ledgers.entrySet()) {
				boolean live = entityPresent.test(entry.getKey());
				boolean hasPending = pending.test(entry.getKey());
				int nextOrphanTicks = live || hasPending
						? TTL_TICKS : entry.getValue().orphanTicksLeft() - 1;
				boolean expire = !live && !hasPending && nextOrphanTicks <= 0;
				plan.add(new LedgerSweep(
						entry.getKey(), entry.getValue(), live,
						nextOrphanTicks, expire));
				if (expire) {
					++expiredEntries;
				}
				else {
					++keptEntries;
					if (!live) {
						++projectedOrphans;
						if (entry.getValue().entityPresent()) {
							++liveToOrphanCandidates;
						}
					}
				}
			}

			SweepResult result = new SweepResult(
					projectedOrphans > maximumOrphans
							? CapacityResult.CAPACITY_EXHAUSTED
							: CapacityResult.ACCEPTED,
					expiredEntries, keptEntries,
					liveToOrphanCandidates, projectedOrphans);
			if (result.result() == CapacityResult.CAPACITY_EXHAUSTED) {
				return result;
			}

			for (LedgerSweep sweep : plan) {
				if (sweep.expire()) {
					ledgers.remove(sweep.identity(), sweep.entry());
				}
				else {
					sweep.entry().applySweep(
							sweep.entityPresent(), sweep.orphanTicksLeft());
				}
			}
			return result;
		}

		private int orphanCount() {
			int count = 0;
			for (LedgerEntry entry : ledgers.values()) {
				if (!entry.entityPresent()) {
					++count;
				}
			}
			return count;
		}

		@Nullable
		LedgerEntryState state(EntityIdentity identity) {
			LedgerEntry entry = ledgers.get(identity);
			return entry != null ? new LedgerEntryState(
					entry.ledger(), entry.entityPresent(),
					entry.orphanTicksLeft()) : null;
		}
	}

	private static final class LedgerEntry {
		private final GenerationLedger ledger;
		private boolean entityPresent;
		private int orphanTicksLeft = TTL_TICKS;

		private LedgerEntry(
				GenerationLedger ledger, boolean entityPresent) {
			this.ledger = ledger;
			this.entityPresent = entityPresent;
		}

		private GenerationLedger ledger() {
			return ledger;
		}

		private boolean entityPresent() {
			return entityPresent;
		}

		private void touch(boolean entityPresent) {
			this.entityPresent = entityPresent;
			resetOrphanRetention();
		}

		private void resetOrphanRetention() {
			orphanTicksLeft = TTL_TICKS;
		}

		private int orphanTicksLeft() {
			return orphanTicksLeft;
		}

		private void applySweep(
				boolean entityPresent, int orphanTicksLeft) {
			this.entityPresent = entityPresent;
			this.orphanTicksLeft = orphanTicksLeft;
		}
	}

	enum CapacityResult {
		ACCEPTED,
		CAPACITY_EXHAUSTED
	}

	static record LedgerAccess(
			CapacityResult result, @Nullable GenerationLedger ledger) {
		private static LedgerAccess accepted(GenerationLedger ledger) {
			return new LedgerAccess(CapacityResult.ACCEPTED, ledger);
		}

		private static LedgerAccess capacityExhausted() {
			return new LedgerAccess(CapacityResult.CAPACITY_EXHAUSTED, null);
		}
	}

	static record SweepResult(
			CapacityResult result,
			int expiredEntries,
			int keptEntries,
			int liveToOrphanCandidates,
			int projectedOrphans) {}

	private record LedgerSweep(
			EntityIdentity identity,
			LedgerEntry entry,
			boolean entityPresent,
			int orphanTicksLeft,
			boolean expire) {}

	static record LedgerEntryState(
			GenerationLedger ledger,
			boolean entityPresent,
			int orphanTicksLeft) {}

	static final class ConnectionEpoch {
		@Nullable private Object level;
		@Nullable private Object connection;
		private boolean initialized;
		private long epoch;
		private boolean capacityFailed;
		private boolean disconnectRequested;

		synchronized boolean update(
				@Nullable Object currentLevel,
				@Nullable Object currentConnection) {
			boolean changed = initialized
					&& (level != currentLevel || connection != currentConnection);
			boolean connectionChanged = initialized
					&& connection != currentConnection;
			if (changed) {
				++epoch;
			}
			if (connectionChanged) {
				capacityFailed = false;
				disconnectRequested = false;
			}
			level = currentLevel;
			connection = currentConnection;
			initialized = true;
			return changed;
		}

		synchronized boolean failCapacity(
				@Nullable Object currentLevel,
				@Nullable Object currentConnection,
				Runnable clearQueue) {
			Objects.requireNonNull(clearQueue, "clearQueue");
			if (capacityFailed && connection == currentConnection) {
				return false;
			}
			clearQueue.run();
			level = currentLevel;
			connection = currentConnection;
			initialized = true;
			++epoch;
			capacityFailed = true;
			disconnectRequested = true;
			return true;
		}

		synchronized boolean acceptsPackets(
				@Nullable Object currentConnection) {
			return !capacityFailed || connection != currentConnection;
		}

		synchronized boolean consumeDisconnectRequest(
				@Nullable Object currentConnection) {
			if (!capacityFailed || connection != currentConnection
					|| !disconnectRequested) {
				return false;
			}
			disconnectRequested = false;
			return true;
		}

		synchronized long epoch() {
			return epoch;
		}

		synchronized ConnectionEpochState state() {
			return new ConnectionEpochState(
					level, connection, initialized, epoch,
					capacityFailed, disconnectRequested);
		}
	}

	static record ConnectionEpochState(
			@Nullable Object level,
			@Nullable Object connection,
			boolean initialized,
			long epoch,
			boolean capacityFailed,
			boolean disconnectRequested) {}

	static record EntityIdentity(int entityId, UUID performerUuid) {
		EntityIdentity {
			Objects.requireNonNull(performerUuid, "performerUuid");
		}
	}

	static record DependentSyncDiagnostic(Stage stage, String detail) {}

	private record CompletedGeneration(
			EntityIdentity identity,
			long generation,
			ApplyResult result,
			LivingEntity entityLifetime) {}

	private record FailedGeneration(
			EntityIdentity identity, long generation) {}
}
