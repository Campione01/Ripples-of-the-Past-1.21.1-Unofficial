package rotp.core.impl.stands.hierophant;

import rotp.core.powersystem.standpower.StandInstance.StandPart;

import rotp.core.entityattachment.syncheddata.SynchedDataBuilder;
import rotp.core.entityattachment.syncheddata.SyncedDataHolderExtended;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.AimingEntity;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class HierophantGrappleAbility extends StandEntityAbility {
	private static final ActionAnimIdentifier GRAPPLE_ANIM = ActionAnimIdentifier.getOrCreate("grapple", false);
	private static final ActionAnimIdentifier GRAPPLE_ENTITY_ANIM = ActionAnimIdentifier.getOrCreate("grapple_entity", false);

	private static final float SHOT_VELOCITY = 4.0F;
	private static final float STAMINA_COST_TICK = 1F;

	public HierophantGrappleAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, type -> {
			boolean bindEntities = abilityId.nameInMoveset().equals("grapple_entity");
			return new GrappleShot(type, bindEntities);
		});
		partsRequired(StandPart.ARMS);
		setButtonHoldPhase(ActionPhase.PERFORM);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		return check.isPositive() ? StandAbilityStamina.check(context, STAMINA_COST_TICK) : check;
	}

	@Override
	public boolean retractsStandAfterAction(StandPower standPower, StandEntity standEntity, EntityActionInstance action) {
		return action instanceof GrappleShot grapple && grapple.isBindEntities();
	}

	@Override
	public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
		return action instanceof GrappleShot grapple && grapple.isBindEntities() ? GRAPPLE_ENTITY_ANIM : GRAPPLE_ANIM;
	}

	public static class GrappleShot extends EntityActionInstance implements SyncedDataHolderExtended {
		private static final EntityDataAccessor<Boolean> CAUGHT_ENTITY = SynchedEntityData.defineId(
				GrappleShot.class, EntityDataSerializers.BOOLEAN);
		private final boolean bindEntities;

		public GrappleShot(EntityActionType ability, boolean bindEntities) {
			super(ability);
			this.bindEntities = bindEntities;
		}

		boolean isBindEntities() {
			return bindEntities;
		}

		void setCaughtEntity(boolean caughtEntity) {
			synchedData.set(CAUGHT_ENTITY, caughtEntity);
		}

		boolean hasCaughtEntity() {
			return synchedData.get(CAUGHT_ENTITY);
		}

		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			if (bindEntities && performer instanceof StandEntity stand) {
				ActionTarget target = captureActionTargetFromAim(stand);
				if (target.getType() == ActionTarget.TargetType.ENTITY && !target.isEmpty(level())) {
					keepStandAimedAtTarget(target);
				}
			}
			aimAs = AimingEntity.STAND;
		}

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			userWalkSpeed = newPhase == ActionPhase.PERFORM ? 0.0F : 1.0F;
		}

		@Override
		public void actionPerformStart() {
			Level level = level();
			if (level.isClientSide()) {
				return;
			}
			LivingEntity user = getPowerUser();
			if (user == null) {
				return;
			}
			LivingEntity performer = getPerformer();
			if (performer == null) {
				return;
			}
			HGGrappleEntity grapple = new HGGrappleEntity(performer, level);
			grapple.setBindEntities(bindEntities);
			grapple.setShootingPosOf(performer);
			grapple.shootFromRotation(performer, performer.getXRot(), performer.getYRot(), 0, SHOT_VELOCITY, 0);
			addProjectileWithStandStats(grapple);
		}

		@Override
		public void actionTick() {
			if (level().isClientSide() || getPhase() != ActionPhase.PERFORM) {
				return;
			}
			LivingEntity user = getPowerUser();
			if (user == null) {
				return;
			}
			StandPower standPower = StandPower.get(user);
			if (!StandAbilityStamina.consume(ability, standPower, STAMINA_COST_TICK, true)) {
				startRecovery();
			}
		}

		@Override
		public void onButtonStopHold() {
			startRecovery();
		}

		@Override
		public void defineSynchedData(SynchedDataBuilder builder) {
			builder.define(CAUGHT_ENTITY, false);
		}

		@Override
		public <T> void onSyncedDataUpdated(T oldValue, T newValue, EntityDataAccessor<T> dataAccessor) {}
	}
}
