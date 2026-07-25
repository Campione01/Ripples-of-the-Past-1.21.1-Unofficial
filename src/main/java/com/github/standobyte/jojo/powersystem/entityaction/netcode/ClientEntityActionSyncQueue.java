package com.github.standobyte.jojo.powersystem.entityaction.netcode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.powersystem.entityaction.ActionOBB;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.entityaction.syncdata.SyncActionInstanceData;
import com.github.standobyte.jojo.powersystem.entityaction.syncdata.TrActionSynchedDataPacket;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class ClientEntityActionSyncQueue {
	private static final int TTL_TICKS = 40;
	private static final int MAX_PENDING_ENTITIES = 256;
	private static final int MAX_PENDING_PER_ENTITY = 16;

	private static final Map<Integer, PendingAction> PENDING_ACTIONS = new HashMap<>();
	private static final Map<Integer, List<Pending<List<SynchedEntityData.DataValue<?>>>>> PENDING_SYNCHED_DATA = new HashMap<>();
	private static final Map<Integer, List<Pending<TrEntityActionPhaseTimePacket>>> PENDING_PHASE_TIMES = new HashMap<>();
	private static final Map<Integer, List<Pending<TrEntityActionWithOBBSyncPacket>>> PENDING_OBB_SYNCS = new HashMap<>();

	private ClientEntityActionSyncQueue() {}

	public static void tick(Minecraft mc) {
		if (mc.level == null) {
			clear();
			return;
		}
		applyPendingActions();
		applyPendingSynchedData();
		applyPendingPhaseTimes();
		applyPendingObbSyncs();
		expirePending();
	}

	public static void applyOrQueueAction(int performerId, @Nullable FriendlyByteBuf actionData) {
		LivingEntity living = findLiving(performerId);
		if (living != null) {
			applyAction(living, actionData);
			applyPendingFor(performerId);
		}
		else {
			queueAction(performerId, actionData);
		}
	}

	public static void applyOrQueueSynchedData(TrActionSynchedDataPacket packet) {
		if (!applySynchedData(packet.entityId(), packet.packedItems())) {
			queue(PENDING_SYNCHED_DATA, packet.entityId(), new ArrayList<>(packet.packedItems()));
		}
	}

	public static void applyOrQueuePhaseTime(TrEntityActionPhaseTimePacket packet) {
		if (!applyPhaseTime(packet)) {
			queue(PENDING_PHASE_TIMES, packet.performerId(), packet);
		}
	}

	public static void applyOrQueueObbSync(TrEntityActionWithOBBSyncPacket packet) {
		if (!applyObbSync(packet)) {
			queue(PENDING_OBB_SYNCS, packet.performerId(), packet);
		}
	}

	private static void queueAction(int performerId, @Nullable FriendlyByteBuf actionData) {
		clearActionDependentPending(performerId);
		PENDING_ACTIONS.put(performerId, new PendingAction(actionData));
		trimMap(PENDING_ACTIONS);
	}

	private static <T> void queue(Map<Integer, List<Pending<T>>> map, int entityId, T payload) {
		List<Pending<T>> pending = map.computeIfAbsent(entityId, key -> new ArrayList<>());
		pending.add(new Pending<>(payload));
		while (pending.size() > MAX_PENDING_PER_ENTITY) {
			pending.remove(0);
		}
		trimMap(map);
	}

	private static void applyPendingActions() {
		Iterator<Map.Entry<Integer, PendingAction>> iterator = PENDING_ACTIONS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<Integer, PendingAction> entry = iterator.next();
			LivingEntity living = findLiving(entry.getKey());
			if (living != null) {
				applyAction(living, entry.getValue().actionData);
				iterator.remove();
				applyPendingFor(entry.getKey());
			}
		}
	}

	private static void applyPendingSynchedData() {
		applyPendingList(PENDING_SYNCHED_DATA, (entityId, payload) -> applySynchedData(entityId, payload));
	}

	private static void applyPendingPhaseTimes() {
		applyPendingList(PENDING_PHASE_TIMES, (entityId, payload) -> applyPhaseTime(payload));
	}

	private static void applyPendingObbSyncs() {
		applyPendingList(PENDING_OBB_SYNCS, (entityId, payload) -> applyObbSync(payload));
	}

	private static <T> void applyPendingList(Map<Integer, List<Pending<T>>> map, PendingApplier<T> applier) {
		Iterator<Map.Entry<Integer, List<Pending<T>>>> mapIterator = map.entrySet().iterator();
		while (mapIterator.hasNext()) {
			Map.Entry<Integer, List<Pending<T>>> entry = mapIterator.next();
			Iterator<Pending<T>> listIterator = entry.getValue().iterator();
			while (listIterator.hasNext()) {
				Pending<T> pending = listIterator.next();
				if (applier.apply(entry.getKey(), pending.payload)) {
					listIterator.remove();
				}
			}
			if (entry.getValue().isEmpty()) {
				mapIterator.remove();
			}
		}
	}

	private static void applyPendingFor(int entityId) {
		applyPendingList(PENDING_SYNCHED_DATA, entityId, payload -> applySynchedData(entityId, payload));
		applyPendingList(PENDING_PHASE_TIMES, entityId, ClientEntityActionSyncQueue::applyPhaseTime);
		applyPendingList(PENDING_OBB_SYNCS, entityId, ClientEntityActionSyncQueue::applyObbSync);
	}

	private static <T> void applyPendingList(Map<Integer, List<Pending<T>>> map, int entityId, PayloadApplier<T> applier) {
		List<Pending<T>> pendingList = map.get(entityId);
		if (pendingList == null) {
			return;
		}
		Iterator<Pending<T>> iterator = pendingList.iterator();
		while (iterator.hasNext()) {
			if (applier.apply(iterator.next().payload)) {
				iterator.remove();
			}
		}
		if (pendingList.isEmpty()) {
			map.remove(entityId);
		}
	}

	private static void applyAction(LivingEntity living, @Nullable FriendlyByteBuf actionData) {
		try {
			EntityActionInstance action = null;
			if (actionData != null) {
				actionData.readerIndex(0);
				action = EntityActionInstance.decode(living.level(), actionData);
			}
			LivingComponentAction.getComponent(living).setAction(action, SyncType.NO_SYNC);
			if (action == null) {
				clearActionDependentPending(living.getId());
			}
		}
		catch (RuntimeException e) {
			JojoMod.getLogger().warn("Skipping client entity action sync for performer {}.", living.getId(), e);
		}
	}

	private static boolean applySynchedData(int entityId, List<SynchedEntityData.DataValue<?>> packedItems) {
		LivingEntity living = findLiving(entityId);
		if (living == null || LivingComponentAction.getCurEntityAction(living) == null) {
			return false;
		}
		SyncActionInstanceData.setDataClientSide(living, packedItems);
		return true;
	}

	private static boolean applyPhaseTime(TrEntityActionPhaseTimePacket packet) {
		LivingEntity living = findLiving(packet.performerId());
		if (living == null) {
			return false;
		}
		EntityActionInstance action = LivingComponentAction.getCurEntityAction(living);
		if (action != null && action.id == packet.actionId()) {
			action.phasesLength = packet.phasesLength();
			action.setPhase(packet.phase(), packet.curPhaseTick());
			return true;
		}
		return false;
	}

	private static boolean applyObbSync(TrEntityActionWithOBBSyncPacket packet) {
		LivingEntity living = findLiving(packet.performerId());
		if (living == null) {
			return false;
		}
		EntityActionInstance action = LivingComponentAction.getCurEntityAction(living);
		if (action != null && action.id == packet.actionId() && action instanceof ActionOBB obbAction && obbAction.extendableOBB() != null) {
			obbAction.extendableOBB().setIsMovingForward(false);
			obbAction.extendableOBB().setIsRetracting(true);
			return true;
		}
		return false;
	}

	@Nullable
	private static LivingEntity findLiving(int entityId) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return null;
		}
		Entity entity = mc.level.getEntity(entityId);
		return entity instanceof LivingEntity living ? living : null;
	}

	private static void expirePending() {
		expireActionMap();
		expireListMap(PENDING_SYNCHED_DATA);
		expireListMap(PENDING_PHASE_TIMES);
		expireListMap(PENDING_OBB_SYNCS);
	}

	private static void expireActionMap() {
		Iterator<Map.Entry<Integer, PendingAction>> iterator = PENDING_ACTIONS.entrySet().iterator();
		while (iterator.hasNext()) {
			if (iterator.next().getValue().tickExpired()) {
				iterator.remove();
			}
		}
	}

	private static <T> void expireListMap(Map<Integer, List<Pending<T>>> map) {
		Iterator<Map.Entry<Integer, List<Pending<T>>>> mapIterator = map.entrySet().iterator();
		while (mapIterator.hasNext()) {
			List<Pending<T>> pendingList = mapIterator.next().getValue();
			pendingList.removeIf(Pending::tickExpired);
			if (pendingList.isEmpty()) {
				mapIterator.remove();
			}
		}
	}

	private static void clearActionDependentPending(int entityId) {
		PENDING_SYNCHED_DATA.remove(entityId);
		PENDING_PHASE_TIMES.remove(entityId);
		PENDING_OBB_SYNCS.remove(entityId);
	}

	private static void clear() {
		PENDING_ACTIONS.clear();
		PENDING_SYNCHED_DATA.clear();
		PENDING_PHASE_TIMES.clear();
		PENDING_OBB_SYNCS.clear();
	}

	private static void trimMap(Map<Integer, ?> map) {
		while (map.size() > MAX_PENDING_ENTITIES) {
			Iterator<Integer> iterator = map.keySet().iterator();
			if (!iterator.hasNext()) {
				return;
			}
			iterator.next();
			iterator.remove();
		}
	}

	@FunctionalInterface
	private interface PendingApplier<T> {
		boolean apply(int entityId, T payload);
	}

	@FunctionalInterface
	private interface PayloadApplier<T> {
		boolean apply(T payload);
	}

	private static class Pending<T> {
		private final T payload;
		private int ticksLeft = TTL_TICKS;

		private Pending(T payload) {
			this.payload = payload;
		}

		boolean tickExpired() {
			return --ticksLeft <= 0;
		}
	}

	private static final class PendingAction extends Pending<FriendlyByteBuf> {
		@Nullable private final FriendlyByteBuf actionData;

		private PendingAction(@Nullable FriendlyByteBuf actionData) {
			super(actionData);
			this.actionData = actionData;
		}
	}
}
