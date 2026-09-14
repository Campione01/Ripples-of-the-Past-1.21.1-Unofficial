package rotp.core.impl.stands._entitybase;

import rotp.core.client.ClientGlobals;
import rotp.core.client.sound.ClientsideSoundsHelper;
import rotp.core.client.sound.sounds.EntityLingeringSoundInstance;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModSoundEvents;
import rotp.core.mechanics.KnockbackCollisionImpact;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.subsystems.entity_grab.LivingComponentGrab;
import rotp.core.subsystems.target.AimingEntity;
import rotp.core.subsystems.timestop.TimeStopState;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class StandEntityGrabThrowAbility extends StandEntityAbility {

	public StandEntityGrabThrowAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, StandEntityGrabThrow::new);
		usageGroup = AbilityUsageGroup.GRAB;
		setDefaultPhaseLength(ActionPhase.BUTTON_CHARGE, 16);
		setButtonHoldPhase(ActionPhase.WINDUP);
		setDefaultPhaseLength(ActionPhase.PERFORM, 6);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 12);
	}

	@Override
	public boolean isAbilityAvailable(Power<?> context) {
		return super.isAbilityAvailable(context) && StandUtil.getStandGrabTarget(context) != null;
	}

	public static class StandEntityGrabThrow extends EntityActionInstance {

		public StandEntityGrabThrow(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onButtonStopHold() {
			switch (getPhase()) {
				case BUTTON_CHARGE -> {
					phasesLength.put(ActionPhase.WINDUP, 0F);
					syncPhaseChanges();
				}
				case WINDUP -> {
					setPhaseStart(ActionPhase.PERFORM);
					syncPhaseChanges();
				}
				default -> {}
			}
		}

		@Override
		public void actionPerformStart() {
			if (performer instanceof StandEntity stand) {
				setStandOffset(0, Math.max(stand.offsetFromUser.getRelativeOffset().z, 0) + 2,
						StandOffsetFromUser.Rotations.HEAD_XY, false);

				Level level = performer.level();
				if (level.isClientSide() && ClientGlobals.canHearStand(stand)) {
					ClientsideSoundsHelper.playNonVanillaClassSound(new EntityLingeringSoundInstance(ClientsideSoundsHelper.withStandSkin(
							ModSoundEvents.STAND_PUNCH_HEAVY_CRY.get(), stand),
							stand.getSoundSource(), 1, 1, stand, level));
				}
			}
			aimAs = AimingEntity.STAND;
		}

		@Override
		public void actionPerformEnd() {
			Level level = level();
			if (!level.isClientSide()) {
				LivingComponentGrab standGrab = performer.getData(ModDataAttachmentTypes.LIVING_GRAB.get());
				LivingEntity grabbedEntity = standGrab != null ? standGrab.getGrabbedEntity() : null;
				if (grabbedEntity != null) {
					standGrab.setGrabTarget(null);
					Vec3 throwVec = performer.getLookAngle().scale(2);
					StandEntity stand = (StandEntity) performer;
					float explRadius = Math.min((float) stand.getAttackDamage() * 0.175F, 10);
					TimeStopState timeStop = level instanceof ServerLevel serverLevel
							&& serverLevel.hasData(ModDataAttachmentTypes.TIME_STOP.get())
							? serverLevel.getData(ModDataAttachmentTypes.TIME_STOP.get()) : null;
					if (timeStop != null && timeStop.shouldFreeze(grabbedEntity)) {
						LivingComponentGrab targetGrab = grabbedEntity.getData(ModDataAttachmentTypes.LIVING_GRAB.get());
						long releaseRevision = targetGrab.getGrabRevision();
						timeStop.queueOnTimeResume(grabbedEntity, () -> {
							if (grabbedEntity.isAlive() && !grabbedEntity.isRemoved() && grabbedEntity.level() == level
									&& targetGrab.getGrabRevision() == releaseRevision && !targetGrab.isGrabbed()) {
								applyThrow(grabbedEntity, stand, throwVec, explRadius);
							}
						});
					}
					else {
						applyThrow(grabbedEntity, stand, throwVec, explRadius);
					}
				}
				StandPower standPower = StandPower.get(getPowerUser());
				if (standPower != null) {
					standPower.consumeStamina(50);
				}
			}
			aimAs = AimingEntity.CAMERA_ENTITY;
		}

		private static void applyThrow(LivingEntity target, StandEntity stand, Vec3 throwVec, float explRadius) {
			target.setDeltaMovement(throwVec);
			target.hurtMarked = true;
			KnockbackCollisionImpact kbImpact = KnockbackCollisionImpact.getHandler(target);
			if (kbImpact != null) {
				kbImpact.onPunchSetKnockbackImpact(throwVec, stand)
						.withImpactExplosion(Math.max(explRadius - 0.5F, 0), null, 0);
			}
		}

		@Override
		public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
			return phase.ordinal() < ActionPhase.PERFORM.ordinal();
		}
	}
}
