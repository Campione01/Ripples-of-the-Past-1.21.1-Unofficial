package com.github.standobyte.jojo.api.network;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.LongFunction;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.entityaction.type.SpecialEntityActionType;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Bounded, read-only receipts for production ability synchronization. Mutation
 * is owned by side-specific internal recorders in this package.
 */
public final class AbilityNetworkDiagnostics {
	static final String RECORDING_PROPERTY =
			"rotp.addonApiSmoke.clientRenderVisibleOnly";
	private static final int MAX_EVENTS = 256;
	private static final EventWindow EVENT_WINDOW =
			new EventWindow(MAX_EVENTS);
	private static long epoch = 1L;
	@Nullable private static Object serverIdentity;
	@Nullable private static Object clientLevelIdentity;
	@Nullable private static Object clientConnectionIdentity;
	private static boolean serverIdentityInitialized;
	private static boolean clientIdentityInitialized;

	private AbilityNetworkDiagnostics() {}

	static boolean isRecordingEnabled() {
		return Boolean.getBoolean(RECORDING_PROPERTY);
	}

	public enum Stage {
		SERVER_INPUT_APPLIED,
		SERVER_INPUT_REJECTED,
		SERVER_RELEASE_APPLIED,
		SERVER_RELEASE_REJECTED,
		CLIENT_REPLAY_APPLIED,
		CLIENT_REPLAY_REJECTED,
		CLIENT_RELEASE_APPLIED,
		CLIENT_RELEASE_REJECTED,
		CLIENT_ACTION_SYNC_QUEUED,
		CLIENT_ACTION_SYNC_APPLIED,
		CLIENT_ACTION_SYNC_REJECTED,
		CLIENT_ACTION_DEPENDENT_SYNC_APPLIED,
		CLIENT_ACTION_DEPENDENT_SYNC_REJECTED
	}

	public static synchronized long latestSequence() {
		return EVENT_WINDOW.latestSequence();
	}

	public static synchronized long currentEpoch() {
		return epoch;
	}

	public static synchronized Snapshot snapshotAfter(long sequenceExclusive) {
		return EVENT_WINDOW.snapshotAfter(sequenceExclusive, epoch);
	}

	public static List<Event> eventsAfter(long sequenceExclusive) {
		return snapshotAfter(sequenceExclusive).events();
	}

	public static ActionStateSnapshot actionState(@Nullable LivingEntity entity) {
		if (entity == null) {
			return ActionStateSnapshot.missing();
		}
		LivingComponentAction component =
				LivingComponentAction.getExistingComponent(entity);
		EntityActionInstance action = component != null
				? component.getAction() : null;
		return new ActionStateSnapshot(
				true,
				entity.level().isClientSide() ? "client" : "server",
				entity.getId(),
				entity.getUUID().toString(),
				abilityTypeId(action),
				abilityName(action),
				action != null ? action.id : -1,
				component != null ? component.actionGeneration() : 0L,
				action != null && action.getPhase() != null
						? action.getPhase().name()
						: "none",
				action == null || action.isOver());
	}

	public static ServerConnectionSnapshot serverConnection(
			@Nullable ServerPlayer player,
			@Nullable CustomPacketPayload.Type<?> payloadType) {
		if (player == null) {
			return ServerConnectionSnapshot.missing();
		}
		boolean connectionPresent = player.connection != null;
		boolean acceptingMessages = connectionPresent
				&& player.connection.isAcceptingMessages();
		boolean connected = isConnectionReady(
				connectionPresent,
				acceptingMessages,
				player.hasDisconnected());
		boolean channelPresent = payloadType != null && connectionPresent
				&& player.connection.hasChannel(payloadType);
		boolean payloadNegotiated = isPayloadNegotiated(
				connected,
				channelPresent);
		return new ServerConnectionSnapshot(
				true,
				player.getId(),
				player.getUUID().toString(),
				connectionPresent,
				connected,
				payloadType != null ? payloadType.id().toString() : "none",
				payloadNegotiated);
	}

	static boolean isConnectionReady(
			boolean connectionPresent,
			boolean acceptingMessages,
			boolean disconnected) {
		return connectionPresent && acceptingMessages && !disconnected;
	}

	static boolean isPayloadNegotiated(
			boolean connected,
			boolean channelPresent) {
		return connected && channelPresent;
	}

	static synchronized void recordServerAbility(
			Stage stage,
			@Nullable LivingEntity entity,
			@Nullable Ability ability,
			short key,
			String inputEvent,
			int unreadExtraBytes,
			boolean mainThread,
			@Nullable Object currentServerIdentity,
			String detail) {
		DiagnosticsWriteAccess.requireCaller(
				AbilityNetworkDiagnostics.class,
				ServerAbilityNetworkDiagnostics.class.getName());
		updateServerEpoch(currentServerIdentity);
		record(new EventData(
				stage,
				"server",
				entity != null ? entity.getId() : -1,
				entity != null ? entity.getUUID().toString() : "none",
				abilityTypeId(ability),
				abilityName(ability),
				key,
				inputEvent,
				-1,
				-1L,
				"none",
				unreadExtraBytes,
				mainThread,
				detail));
	}

	static synchronized void recordClientAbility(
			Stage stage,
			@Nullable LivingEntity entity,
			@Nullable Ability ability,
			short key,
			String inputEvent,
			int unreadExtraBytes,
			boolean mainThread,
			@Nullable Object currentLevelIdentity,
			@Nullable Object currentConnectionIdentity,
			String detail) {
		DiagnosticsWriteAccess.requireCaller(
				AbilityNetworkDiagnostics.class,
				ClientAbilityNetworkDiagnostics.class.getName());
		updateClientEpoch(
				currentLevelIdentity, currentConnectionIdentity);
		record(new EventData(
				stage,
				"client",
				entity != null ? entity.getId() : -1,
				entity != null ? entity.getUUID().toString() : "none",
				abilityTypeId(ability),
				abilityName(ability),
				key,
				inputEvent,
				-1,
				-1L,
				"none",
				unreadExtraBytes,
				mainThread,
				detail));
	}

	static synchronized void recordClientAction(
			Stage stage,
			@Nullable LivingEntity entity,
			int entityId,
			@Nullable EntityActionInstance action,
			long actionGeneration,
			boolean mainThread,
			@Nullable Object currentLevelIdentity,
			@Nullable Object currentConnectionIdentity,
			String detail) {
		DiagnosticsWriteAccess.requireCaller(
				AbilityNetworkDiagnostics.class,
				ClientAbilityNetworkDiagnostics.class.getName());
		updateClientEpoch(
				currentLevelIdentity, currentConnectionIdentity);
		record(new EventData(
				stage,
				"client",
				entity != null ? entity.getId() : entityId,
				entity != null ? entity.getUUID().toString() : "none",
				abilityTypeId(action),
				abilityName(action),
				(short) -1,
				"none",
				action != null ? action.id : -1,
				actionGeneration,
				action != null && action.getPhase() != null
						? action.getPhase().name()
						: "none",
				0,
				mainThread,
				detail));
	}

	private static void updateServerEpoch(
			@Nullable Object currentServerIdentity) {
		if (!serverIdentityInitialized) {
			serverIdentity = currentServerIdentity;
			serverIdentityInitialized = true;
			return;
		}
		if (serverIdentity != currentServerIdentity) {
			startNewEpoch();
			serverIdentity = currentServerIdentity;
			clientIdentityInitialized = false;
		}
	}

	private static void updateClientEpoch(
			@Nullable Object currentLevelIdentity,
			@Nullable Object currentConnectionIdentity) {
		if (!clientIdentityInitialized) {
			clientLevelIdentity = currentLevelIdentity;
			clientConnectionIdentity = currentConnectionIdentity;
			clientIdentityInitialized = true;
			return;
		}
		if (clientLevelIdentity != currentLevelIdentity
				|| clientConnectionIdentity != currentConnectionIdentity) {
			startNewEpoch();
			clientLevelIdentity = currentLevelIdentity;
			clientConnectionIdentity = currentConnectionIdentity;
			serverIdentityInitialized = false;
		}
	}

	private static void startNewEpoch() {
		if (epoch == Long.MAX_VALUE) {
			throw new IllegalStateException(
					"Ability diagnostics epoch exhausted");
		}
		++epoch;
		EVENT_WINDOW.clearRetained();
	}

	private static void record(EventData data) {
		EVENT_WINDOW.record(sequence -> new Event(
				epoch,
				sequence,
				data.stage(),
				data.side(),
				data.entityId(),
				data.entityUuid(),
				data.abilityTypeId(),
				data.movesetAbilityName(),
				data.key(),
				data.inputEvent(),
				data.actionId(),
				data.actionGeneration(),
				data.actionPhase(),
				data.unreadExtraBytes(),
				data.mainThread(),
				Thread.currentThread().getName(),
				data.detail()));
	}

	private static String abilityTypeId(@Nullable Ability ability) {
		return ability != null && ability.abilityType != null
				&& ability.abilityType.registryKey != null
						? ability.abilityType.registryKey.toString()
						: "none";
	}

	private static String abilityName(@Nullable Ability ability) {
		return ability != null ? ability.name() : "none";
	}

	private static String abilityTypeId(
			@Nullable EntityActionInstance action) {
		if (action != null && action.ability instanceof Ability ability) {
			return abilityTypeId(ability);
		}
		return action != null
				&& action.ability instanceof SpecialEntityActionType special
						? special.id.toString()
						: "none";
	}

	private static String abilityName(
			@Nullable EntityActionInstance action) {
		if (action != null && action.ability instanceof Ability ability) {
			return abilityName(ability);
		}
		return action != null
				&& action.ability instanceof SpecialEntityActionType special
						? special.id.getPath()
						: "none";
	}

	private record EventData(
			Stage stage,
			String side,
			int entityId,
			String entityUuid,
			String abilityTypeId,
			String movesetAbilityName,
			short key,
			String inputEvent,
			int actionId,
			long actionGeneration,
			String actionPhase,
			int unreadExtraBytes,
			boolean mainThread,
			String detail) {}

	static final class EventWindow {
		private final int maximumEvents;
		private final Deque<Event> events;
		private long sequence;

		EventWindow(int maximumEvents) {
			if (maximumEvents <= 0) {
				throw new IllegalArgumentException(
						"Diagnostic event window must be positive");
			}
			this.maximumEvents = maximumEvents;
			this.events = new ArrayDeque<>(maximumEvents);
		}

		synchronized long latestSequence() {
			return sequence;
		}

		synchronized void record(LongFunction<Event> eventFactory) {
			while (events.size() >= maximumEvents) {
				events.removeFirst();
			}
			events.addLast(eventFactory.apply(++sequence));
		}

		synchronized Snapshot snapshotAfter(
				long sequenceExclusive, long epoch) {
			List<Event> retained = new ArrayList<>();
			for (Event event : events) {
				if (event.sequence() > sequenceExclusive) {
					retained.add(event);
				}
			}
			long firstRetainedSequence = events.isEmpty()
					? sequence + 1L : events.getFirst().sequence();
			boolean truncated = sequenceExclusive
					< firstRetainedSequence - 1L;
			return new Snapshot(
					epoch,
					firstRetainedSequence,
					sequence,
					truncated,
					List.copyOf(retained));
		}

		synchronized void clearRetained() {
			events.clear();
		}
	}

	public record Snapshot(
			long epoch,
			long firstRetainedSequence,
			long latestSequence,
			boolean truncated,
			List<Event> events) {}

	public record Event(
			long epoch,
			long sequence,
			Stage stage,
			String side,
			int entityId,
			String entityUuid,
			String abilityTypeId,
			String movesetAbilityName,
			short key,
			String inputEvent,
			int actionId,
			long actionGeneration,
			String actionPhase,
			int unreadExtraBytes,
			boolean mainThread,
			String threadName,
			String detail) {}

	public record ActionStateSnapshot(
			boolean entityPresent,
			String side,
			int entityId,
			String entityUuid,
			String abilityTypeId,
			String movesetAbilityName,
			int actionId,
			long actionGeneration,
			String actionPhase,
			boolean absentOrOver) {
		private static ActionStateSnapshot missing() {
			return new ActionStateSnapshot(
					false, "unknown", -1, "none", "none", "none",
					-1, 0L, "none", true);
		}
	}

	public record ServerConnectionSnapshot(
			boolean playerPresent,
			int entityId,
			String playerUuid,
			boolean connectionPresent,
			boolean connected,
			String payloadTypeId,
			boolean payloadNegotiated) {
		private static ServerConnectionSnapshot missing() {
			return new ServerConnectionSnapshot(
					false, -1, "none", false, false, "none", false);
		}
	}
}
