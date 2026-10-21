package rotp.core.powersystem.standpower.effect;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import rotp.core.entityattachment.custom_effect.EntityCustomEffect;
import rotp.core.entityattachment.custom_effect.EntityCustomEffectType;
import rotp.core.entityattachment.custom_effect.TrEntityCustomEffectsPacket;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public abstract class StandEffectInstance extends EntityCustomEffect {
	protected StandPower userPower;

	private Entity target;
	private LivingEntity targetLiving;
	private UUID targetUUID;
	private int targetNetworkId = -1;
	
	public boolean removeOnStandChanged = true;
	public boolean needsTarget = false;
	
	public boolean isFromStandAction = false;
	public EntityActionInstance standAction;
	public boolean isFromUserAction = false;
	public EntityActionInstance userAction;


	public StandEffectInstance(@Nonnull EntityCustomEffectType<?> effectType) {
		super(effectType);
	}
	
	protected void initStandPower(StandPower userPower) {
		this.userPower = userPower;
		// on its action once, as 1.16's task kept its modifiers in a set: a client initializes an effect from its add
		// packet twice (withEntity, then addEffect)
		if (isFromStandAction) {
			StandEntity standEntity = userPower.getSummonedStandEntity();
			if (standEntity != null) standAction = LivingComponentAction.getCurEntityAction(standEntity);
			if (standAction != null && !standAction.getPunchModifiers().contains(this)) standAction.getPunchModifiers().add(this);
		}
		if (isFromUserAction) {
			LivingEntity user = userPower.getUser();
			if (user != null) userAction = LivingComponentAction.getCurEntityAction(user);
			if (userAction != null && !userAction.getPunchModifiers().contains(this)) userAction.getPunchModifiers().add(this);
		}
	}

	@Override
	public StandEffectInstance withEntity(Entity entity) {
		super.withEntity(entity);
		initStandPower(StandPower.get((LivingEntity) entity));
		return this;
	}

	public StandEffectInstance withStand(StandPower stand) {
		super.withEntity(stand.getUser());
		initStandPower(stand);
		return this;
	}

	public StandEffectInstance withTarget(Entity target) {
		this.target = target;
		this.targetLiving = target instanceof LivingEntity ? (LivingEntity) target : null;
		this.targetUUID = target != null ? target.getUUID() : null;
		this.targetNetworkId = target.getId();
		return this;
	}

	public StandEffectInstance withTargetEntityId(int entityId) {
		this.targetNetworkId = entityId;
		if (target != null && target.getId() != entityId) {
			this.target = null;
			this.targetLiving = null;
		}
		return this;
	}

	public StandPower getUserPower() {
		return userPower;
	}

	public boolean makesAttackNonLethal(LivingEntity target) {
		return false;
	}

	/**
	 * A modifier of the action it attached to as it started (isFromStandAction: the Stand's current action, isFromUserAction:
	 * the user's; EntityActionInstance#getPunchModifiers), as 1.16's StandEntityActionModifier added to the running task
	 * (StandEntityTask.addModifierAction): called once on every tick of that action, on both sides, right after the action's
	 * own tick and, on its PERFORM's first tick, after its perform start, while this effect is not stopped, as 1.16
	 * StandEntityTask.tick ran each modifier's phase hooks (standTickWindup, standPerform, standTickPerform,
	 * standTickRecovery) right after its action's. Also in the tick where the action ended itself (its tick or perform start
	 * stopped it): the action then reads the phase it ended in until this returns, as 1.16's stopped task kept its phase
	 * for the rest of that tick. Nothing by default.
	 */
	@ApiStatus.OverrideOnly
	public void onModifiedActionTick(EntityActionInstance action) {}

	/**
	 * A modifier of the action (see {@link #onModifiedActionTick}) told of the action's phase change, as 1.16's
	 * StandEntityTask.phaseTransition called each modifier's phaseTransition right after the action's own: on both sides,
	 * right after the action's own EntityActionInstance#onSetPhase, with the action already in its new phase, whenever the
	 * action enters a phase with a length (one without is passed over, as 1.16 moveToPhase did; prevPhase is then the last
	 * phase with a length it was in); and with nextPhase null when the action runs out its last phase (1.16 moveToPhase(null)),
	 * still in it, but not when it is stopped or replaced (1.16 stopTask told no modifier). While this effect is not stopped.
	 * Nothing by default.
	 */
	@ApiStatus.OverrideOnly
	public void onModifiedActionPhaseTransition(EntityActionInstance action, @Nullable ActionPhase prevPhase,
			@Nullable ActionPhase nextPhase) {}

	public LivingEntity getStandUser() {
		return (LivingEntity) getEntity();
	}

	public Entity getTarget() {
		return target;
	}

	public LivingEntity getTargetLiving() {
		return targetLiving;
	}

	public UUID getTargetUUID() {
		return targetUUID;
	}

	@Override
	public void onStart() {
		if (targetLiving != null) {
			StandEffectsTarget targetEffects = StandEffectsTarget.getList(targetLiving);
			if (targetEffects != null) {
				targetEffects.addEffectTargetedBy(this);
			}
		}
		super.onStart();
	}

	@Override
	public void onTick() {
		if (!toBeRemoved) {
			updateTarget(level);
			if (targetUUID == null && needsTarget) {
				if (!level.isClientSide()) remove();
				return;
			}
		}
		
		super.onTick();
	}

	public void updateTarget(Level level) {
		if (target == null) {
			if (!level.isClientSide()) {
				if (targetUUID != null) {
					Entity entity = ((ServerLevel) level).getEntity(targetUUID);
					setTargetEntity(entity);
				}
			}
			else if (targetNetworkId > -1) {
				Entity entity = level.getEntity(targetNetworkId);
				setTargetEntity(entity);
			}
		}

		if (!level.isClientSide() && targetUUID != null && 
				target != null && shouldClearTarget(target, targetLiving)) {
			clearTarget();
		}

		if (target != null && !target.isAlive()) {
			setTargetEntity(null);
		}
	}

	protected final void clearTarget() {
		targetUUID = null;
		setTargetEntity(null);
	}

	public void setTargetEntity(Entity target) {
		if (this.target != target) {
			if (this.targetLiving != null) {
				StandEffectsTarget oldTargetEffects = StandEffectsTarget.getList(this.targetLiving);
				if (oldTargetEffects != null) {
					oldTargetEffects.removeEffectTargetedBy(this);
				}
			}
			this.target = target;
			if (target != null) {
				this.targetUUID = target.getUUID();
			}
			if (target instanceof LivingEntity newTargetLiving) {
				StandEffectsTarget targetEffects = StandEffectsTarget.getList(newTargetLiving);
				if (targetEffects != null) {
					targetEffects.addEffectTargetedBy(this);
				}
				this.targetLiving = newTargetLiving;
			}
			else {
				this.targetLiving = null;
			}

			if (!level.isClientSide()) {
				PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, TrEntityCustomEffectsPacket.updateTarget(this));
			}
		}
	}
	
	public int getTargetEntityId() {
		return Optional.ofNullable(this.getTarget()).map(Entity::getId).orElse(-1);
	}

	@Override
	public void onStop() {
		if (targetLiving != null) {
			StandEffectsTarget targetEffects = StandEffectsTarget.getList(targetLiving);
			if (targetEffects != null) {
				targetEffects.removeEffectTargetedBy(this);
			}
		}
		super.onStop();
	}

	protected abstract void start();
	protected abstract void tick();
	protected abstract void stop();
	
	protected boolean shouldClearTarget(Entity target, @Nullable LivingEntity targetLiving) {
		return targetLiving != null && targetLiving.isDeadOrDying();
	}

	@Override
	public void syncWithUserOnly(ServerPlayer user) {
		super.syncWithUserOnly(user);
		updateTarget(user.level());
	}

	@Override
	public void syncWithTrackingOrUser(ServerPlayer user) {
		super.syncWithTrackingOrUser(user);
	}

	@Override
	public void writeAdditionalPacketData(FriendlyByteBuf buf, boolean sendingToUser) {
		buf.writeInt(getTargetEntityId());
	}

	@Override
	public void readAdditionalPacketData(FriendlyByteBuf buf, boolean clientIsUser) {
		int targetEntityId = buf.readInt();
		if (targetEntityId != -1) {
			this.withTargetEntityId(targetEntityId);
			this.updateTarget(entity.level());
		}
	}

	@Override
	protected void writeAdditionalSaveData(CompoundTag nbt) {
		if (targetUUID != null) {
			nbt.putUUID("Target", targetUUID);
		}
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag nbt) {
		if (nbt.hasUUID("Target")) {
			this.targetUUID = nbt.getUUID("Target");
		}
	}
}
