package com.github.standobyte.jojo.entityattachment.custom_effect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.entityattachment.custom_effect.sync.SyncStandEffectInstanceData;
import com.github.standobyte.jojo.entityattachment.custom_effect.sync.TrStandEffectSynchedDataPacket;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

public final class ClientCustomEffectSyncQueue {
	private static final int TTL_TICKS = 40;
	private static final int MAX_PENDING_ENTITIES = 256;
	private static final int MAX_PENDING_PER_ENTITY = 32;

	private static final Map<Integer, List<Pending<TrEntityCustomEffectsPacket>>> PENDING_EFFECTS = new HashMap<>();
	private static final Map<Integer, List<Pending<TrStandEffectSynchedDataPacket>>> PENDING_SYNCHED_DATA = new HashMap<>();

	private ClientCustomEffectSyncQueue() {}

	public static void tick(Minecraft mc) {
		if (mc.level == null) {
			clear();
			return;
		}
		applyPendingEffects();
		applyPendingSynchedData();
		expirePending();
	}

	public static void applyOrQueueEffectPacket(TrEntityCustomEffectsPacket packet) {
		if (!applyEffectPacket(packet)) {
			queue(PENDING_EFFECTS, packet.userId(), packet);
		}
		else {
			applyPendingSynchedData(packet.userId());
		}
	}

	public static void applyOrQueueSynchedData(TrStandEffectSynchedDataPacket packet) {
		if (!applySynchedData(packet)) {
			queue(PENDING_SYNCHED_DATA, packet.entityId(), packet);
		}
	}

	private static boolean applyEffectPacket(TrEntityCustomEffectsPacket packet) {
		try {
			return packet.tryApplyClient();
		}
		catch (RuntimeException e) {
			JojoMod.getLogger().warn("Skipping client custom effect sync for entity {} effect {}.", packet.userId(), packet.effectId(), e);
			return true;
		}
	}

	private static boolean applySynchedData(TrStandEffectSynchedDataPacket packet) {
		try {
			Entity entity = ClientProxy.getEntityById(packet.entityId());
			if (entity == null) {
				return false;
			}
			EntityCustomEffectsMap<?> effects = packet.effectsClass().get(entity, false);
			if (effects == null || effects.getById(packet.effectId()) == null) {
				return false;
			}
			SyncStandEffectInstanceData.setDataClientSide(entity, packet.effectId(), packet.effectsClass(), packet.packedItems());
			return true;
		}
		catch (RuntimeException e) {
			JojoMod.getLogger().warn("Skipping client custom effect data sync for entity {} effect {}.", packet.entityId(), packet.effectId(), e);
			return true;
		}
	}

	private static <T> void queue(Map<Integer, List<Pending<T>>> map, int entityId, T payload) {
		List<Pending<T>> pending = map.computeIfAbsent(entityId, key -> new ArrayList<>());
		pending.add(new Pending<>(payload));
		while (pending.size() > MAX_PENDING_PER_ENTITY) {
			pending.remove(0);
		}
		trimMap(map);
	}

	private static void applyPendingEffects() {
		applyPendingList(PENDING_EFFECTS, (entityId, payload) -> applyEffectPacket(payload));
	}

	private static void applyPendingSynchedData() {
		applyPendingList(PENDING_SYNCHED_DATA, (entityId, payload) -> applySynchedData(payload));
	}

	private static void applyPendingSynchedData(int entityId) {
		List<Pending<TrStandEffectSynchedDataPacket>> pendingList = PENDING_SYNCHED_DATA.get(entityId);
		if (pendingList == null) {
			return;
		}
		Iterator<Pending<TrStandEffectSynchedDataPacket>> iterator = pendingList.iterator();
		while (iterator.hasNext()) {
			if (applySynchedData(iterator.next().payload)) {
				iterator.remove();
			}
		}
		if (pendingList.isEmpty()) {
			PENDING_SYNCHED_DATA.remove(entityId);
		}
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

	private static void expirePending() {
		expireListMap(PENDING_EFFECTS);
		expireListMap(PENDING_SYNCHED_DATA);
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

	private static void clear() {
		PENDING_EFFECTS.clear();
		PENDING_SYNCHED_DATA.clear();
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

	private static final class Pending<T> {
		private final T payload;
		private int ticksLeft = TTL_TICKS;

		private Pending(T payload) {
			this.payload = payload;
		}

		private boolean tickExpired() {
			return --ticksLeft <= 0;
		}
	}
}
