package com.github.standobyte.jojo.powersystem.entityaction;

import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.client.entityanim.pose.AnimFramePose;
import com.github.standobyte.jojo.client.entityanim.pose.AnimatedEntity;
import com.github.standobyte.jojo.customobjects.LivingReactToNewAction;
import com.github.standobyte.jojo.entityattachment.SynchronizablePlayerData;
import com.github.standobyte.jojo.entityattachment.TickingEntityData;
import com.github.standobyte.jojo.init.ModDataAttachmentTypes;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance.InputLifecycleSnapshot;
import com.github.standobyte.jojo.powersystem.entityaction.netcode.SyncType;
import com.github.standobyte.jojo.powersystem.entityaction.netcode.TrEntityActionInstancePacket;
import com.github.standobyte.jojo.powersystem.entityaction.syncdata.SyncActionInstanceData;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.subsystems.target.ActionTargetAim;

import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.network.PacketDistributor;

public class LivingComponentAction implements SynchronizablePlayerData, TickingEntityData, INBTSerializable<CompoundTag> {
	private static final GenerationSequence SERVER_ACTION_GENERATIONS =
			new GenerationSequence();

	private final LivingEntity entity;
	private final LivingReactToNewAction setActionCallback;
	private final AtomicInteger actionIdCounter = new AtomicInteger();
	private long actionGenerationCounter;
	private long actionGeneration;
	private long lifecycleRemovalAttempts;
	@Nullable private EntityActionInstance action;
	
	public ActionComboStringTracker comboString = new ActionComboStringTracker();
	
	@Nullable public AnimFramePose clPrevPunchPose;
	
	// TODO move aim to a separate component
	@Deprecated
	public final ActionTargetAim entityAim;
	
	public LivingComponentAction(LivingEntity entity) {
		this.entity = entity;
		this.setActionCallback = (entity instanceof LivingReactToNewAction standEntity) ? standEntity : null;
		this.entityAim = new ActionTargetAim();
		addSynchronization(entity);
		addTicking(entity);
	}
	
	public EntityActionInstance getAction() {
		return action;
	}

	@ApiStatus.Internal
	public long actionGeneration() {
		return actionGeneration;
	}

	@ApiStatus.Internal
	public TransactionSnapshot captureTransactionSnapshot() {
		return new TransactionSnapshot(
				action,
				action != null ? action.captureInputLifecycle() : null,
				actionIdCounter.get(),
				actionGeneration,
				lifecycleRemovalAttempts);
	}

	/** Restores the complete action lifecycle after an input transaction failed. */
	@ApiStatus.Internal
	public void rollbackFailedInputAction(
			TransactionSnapshot snapshot) {
		if (snapshot == null) {
			return;
		}
		boolean replacementInstalled = action != snapshot.action();
		boolean removalAttempted = lifecycleRemovalAttempts
				!= snapshot.lifecycleRemovalAttempts();
		if (!replacementInstalled && !removalAttempted) {
			return;
		}
		EntityActionInstance failedAction = replacementInstalled ? action : null;
		if (replacementInstalled) {
			removeFailedActionCallback(failedAction, snapshot.action());
		}
		actionIdCounter.set(snapshot.actionIdCounter());
		restoreActionReference(snapshot.action());
		if (snapshot.action() != null && snapshot.lifecycle() != null) {
			snapshot.action().restoreInputLifecycle(
					snapshot.lifecycle(), failedAction);
		}
		rebuildComboState(snapshot.action());

		if (entity.level().isClientSide()) {
			actionGeneration = snapshot.actionGeneration();
			if (snapshot.action() != null) {
				snapshot.action().setNetworkGeneration(actionGeneration);
			}
		}
		else if (replacementInstalled) {
			advanceActionGeneration(snapshot.action());
			PacketDistributor.sendToPlayersTrackingEntityAndSelf(
					entity,
					new TrEntityActionInstancePacket(
							entity.getId(), entity.getUUID(), actionGeneration,
							snapshot.action()));
		}
	}

	/** Legacy transaction entry point retained for internal addon source compatibility. */
	@ApiStatus.Internal
	public void rollbackFailedInputAction(
			@Nullable EntityActionInstance actionBeforeInput) {
		rollbackFailedInputAction(new TransactionSnapshot(
				actionBeforeInput,
				actionBeforeInput != null
						? actionBeforeInput.captureInputLifecycle() : null,
				actionIdCounter.get(),
				actionGeneration,
				lifecycleRemovalAttempts));
	}

	/** Clears an action whose dependent network update failed partway through. */
	@ApiStatus.Internal
	public void clearFailedNetworkAction() {
		EntityActionInstance failedAction = action;
		removeFailedActionCallback(failedAction, null);
		restoreActionReference(null);
		rebuildComboState(null);
	}

	@ApiStatus.Internal
	public int getActionIdCounterForTransaction() {
		return actionIdCounter.get();
	}

	private void removeFailedActionCallback(
			@Nullable EntityActionInstance failedAction,
			@Nullable EntityActionInstance replacement) {
		if (failedAction != null && failedAction != replacement) {
			try {
				++lifecycleRemovalAttempts;
				failedAction._beforeActionRemoved(replacement);
			}
			catch (RuntimeException error) {
				JojoMod.getLogger().error(
						"Failed to run action rollback callback for {}.",
						entity, error);
			}
		}
	}

	private void rebuildComboState(
			@Nullable EntityActionInstance replacement) {
		try {
			comboString.clear();
			if (replacement != null) {
				comboString.onNewAction(replacement);
			}
		}
		catch (RuntimeException error) {
			JojoMod.getLogger().error(
					"Failed to clear combo state while rolling back input for {}.",
						entity, error);
		}
	}

	private void restoreActionReference(
			@Nullable EntityActionInstance replacement) {
		if (replacement != null) {
			replacement.performer = entity;
		}
		this.action = replacement;
	}
	
	
	public HeldInput setAction(EntityActionInstance action, LivingEntity powerUser, SyncType sync) {
		if (action != null) {
			action.powerUser.setEntity(powerUser);
		}
		return setAction(action, sync);
	}
	
	@ApiStatus.Internal
	public HeldInput setAction(@Nullable EntityActionInstance action, SyncType sync) {
		return setActionInternal(action, sync, 0L, false);
	}

	@ApiStatus.Internal
	public HeldInput setActionFromNetwork(
			@Nullable EntityActionInstance action,
			long generation) {
		if (!entity.level().isClientSide()) {
			throw new IllegalStateException(
					"Network action state may only be installed on the client");
		}
		if (generation <= 0L) {
			throw new IllegalArgumentException(
					"Entity action generation must be positive");
		}
		return setActionInternal(
				action, SyncType.NO_SYNC, generation, true);
	}

	private HeldInput setActionInternal(
			@Nullable EntityActionInstance action,
			SyncType sync,
			long networkGeneration,
			boolean authoritativeClientState) {
		// A way for the performer entity to the changed action, i.e. for a Stand entity to reset offset to idle
		
		if (setActionCallback != null && setActionCallback.onActionSet(action)) {
			return null;
		}
		
		// Resolve entity references
		
		if (action != null && action.standRotationTarget != null) {
			action.standRotationTarget = action.standRotationTarget.resolveEntityId(entity.level());
		}
		
		// Action callbacks that may be overriden by specific abilities
		
		if (this.action != null) {
			++lifecycleRemovalAttempts;
			this.action._beforeActionRemoved(action);
		}
		EntityActionInstance prevAction = this.action;
		assignAction(action, networkGeneration, authoritativeClientState);
		if (action != null) {
			action._onActionStarted(prevAction);
		}
		
		if (entity.level().isClientSide()) {
			clPrevPunchPose = null;
			if (action != null && action.savePrevPoseForAnimTransition(prevAction) && entity instanceof AnimatedEntity entity) {
				AnimFramePose clientSavedPose = entity.jojo_ripples$getModelPose(AnimatedEntity.PoseType.UNMODIFIED);
				if (clientSavedPose != null) {
					clPrevPunchPose = clientSavedPose.deepCopy();
				}
			}
		}
		
		// Sync to players
		
		if (!entity.level().isClientSide()) {
			switch (sync) {
				case TRACKING -> PacketDistributor.sendToPlayersTrackingEntity(entity, new TrEntityActionInstancePacket(
						entity.getId(), entity.getUUID(), actionGeneration, action));
				case TRACKING_AND_SELF -> PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new TrEntityActionInstancePacket(
						entity.getId(), entity.getUUID(), actionGeneration, action));
				default -> {}
			}
		}
		
		if (action != null) {
			comboString.onNewAction(action);
		}
		else {
			comboString.clear();
		}
		
		return action;
	}
	
	private void assignAction(
			@Nullable EntityActionInstance action,
			long networkGeneration,
			boolean authoritativeClientState) {
		if (action != null) {
			action.performer = entity;
			if (!entity.level().isClientSide()) {
				action.id = actionIdCounter.incrementAndGet() & 127;
			}
		}
		this.action = action;
		if (!entity.level().isClientSide()) {
			advanceActionGeneration(action);
		}
		else if (authoritativeClientState) {
			actionGeneration = networkGeneration;
			if (action != null) {
				action.setNetworkGeneration(networkGeneration);
			}
		}
		else {
			actionGeneration = 0L;
			if (action != null) {
				action.setNetworkGeneration(0L);
			}
		}
	}

	private long advanceActionGeneration(
			@Nullable EntityActionInstance currentAction) {
		actionGeneration = SERVER_ACTION_GENERATIONS.nextAfter(
				Math.max(actionGenerationCounter, actionGeneration));
		actionGenerationCounter = actionGeneration;
		if (currentAction != null) {
			currentAction.setNetworkGeneration(actionGeneration);
		}
		return actionGeneration;
	}

	private void ensureServerGeneration() {
		if (!entity.level().isClientSide() && actionGeneration <= 0L) {
			advanceActionGeneration(action);
		}
	}
	
	
	@Override
	public void tick() {
		if (action != null) {
			if (entity instanceof StandEntity standEntity && standEntity.summonLockTicks > 0) {
				return;
			}
			if (entity instanceof StandEntity standEntity
					&& ModStatusEffects.isStunned(standEntity)
					&& !action.ability.ignoresPerformerStun()) {
				return;
			}
			tickAction();
			if (action != null && !entity.level().isClientSide()) {
				SyncActionInstanceData.tickSyncDirtyData(
						entity, actionGeneration,
						action.synchedData.getDataSyncher());
			}
		}
	}
	
	protected void tickAction() {
		EntityActionInstance tickingAction = action;
		tickingAction._tickAction();
		// Action callbacks may stop or replace the current action reentrantly.
		if (action == tickingAction && tickingAction.isOver()) {
			setAction(null, null, SyncType.NO_SYNC);
		}
	}
	
	
	@Override
	public void syncToPlayer(ServerPlayer player) {
		ensureServerGeneration();
		PacketDistributor.sendToPlayer(player, new TrEntityActionInstancePacket(
				entity.getId(), entity.getUUID(), actionGeneration, action));
	}

	@Override
	public void syncToTracking(ServerPlayer player) {
		ensureServerGeneration();
		PacketDistributor.sendToPlayer(player, new TrEntityActionInstancePacket(
				entity.getId(), entity.getUUID(), actionGeneration, action));
		SyncActionInstanceData.onStartedTracking(
				player, entity, actionGeneration, action);
	}
	
	@Override
	public void onPlayerClone(Player newPlayer, boolean wasDeath) {
		if (newPlayer.level().isClientSide()) {
			return;
		}
		LivingComponentAction replacement = getComponent(newPlayer);
		replacement.actionIdCounter.set(actionIdCounter.get());
		replacement.actionGenerationCounter = Math.max(
				actionGenerationCounter, actionGeneration);
		replacement.advanceActionGeneration(null);
	}

	static final class GenerationSequence {
		private long lastIssued;

		GenerationSequence() {}

		GenerationSequence(long lastIssued) {
			if (lastIssued < 0L) {
				throw new IllegalArgumentException(
						"Entity action generation cannot be negative");
			}
			this.lastIssued = lastIssued;
		}

		synchronized long nextAfter(long generationFloor) {
			if (generationFloor < 0L) {
				throw new IllegalArgumentException(
						"Entity action generation floor cannot be negative");
			}
			long floor = Math.max(lastIssued, generationFloor);
			if (floor == Long.MAX_VALUE) {
				throw new IllegalStateException(
						"Entity action generation exhausted");
			}
			return lastIssued = floor + 1L;
		}
	}

	@ApiStatus.Internal
	public record TransactionSnapshot(
			@Nullable EntityActionInstance action,
			@Nullable InputLifecycleSnapshot lifecycle,
			int actionIdCounter,
			long actionGeneration,
			long lifecycleRemovalAttempts) {}


	// TODO (entity action 2) nbt save/load
	@Override
	public CompoundTag serializeNBT(Provider provider) {
		CompoundTag nbt = new CompoundTag();
		
		return nbt;
	}

	@Override
	public void deserializeNBT(Provider provider, CompoundTag nbt) {
		
	}
	
	
	public static LivingComponentAction create(IAttachmentHolder obj) {
		if (obj instanceof LivingEntity entity) {
			return new LivingComponentAction(entity);
		}
		throw new IllegalArgumentException();
	}
	
	public static LivingComponentAction getComponent(LivingEntity entity) {
		return entity.getData(ModDataAttachmentTypes.LIVING_ACTION.get());
	}
	
	@Nullable
	public static LivingComponentAction getExistingComponent(LivingEntity entity) {
		if (entity == null) return null;
		AttachmentType<LivingComponentAction> t = ModDataAttachmentTypes.LIVING_ACTION.get();
		return entity.hasData(t) ? entity.getData(t) : null;
	}
	
	@Nullable
	public static EntityActionInstance getCurEntityAction(LivingEntity entity) {
		LivingComponentAction existingData = getExistingComponent(entity);
		return existingData != null ? existingData.getAction() : null;
	}
	
	@Nullable
	public static ActionTargetAim getAim(LivingEntity entity) {
		LivingComponentAction existingData = getExistingComponent(entity);
		return existingData != null ? existingData.entityAim : null;
	}

}
