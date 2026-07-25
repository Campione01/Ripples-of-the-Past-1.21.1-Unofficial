package com.github.standobyte.jojoimpl.stands._entitybase;

import com.github.standobyte.jojo.client.ClientGlobals;
import com.github.standobyte.jojo.client.sound.ClientsideSoundsHelper;
import com.github.standobyte.jojo.client.sound.sounds.EntityLingeringSoundInstance;
import com.github.standobyte.jojo.init.ModDataAttachmentTypes;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.mechanics.KnockbackCollisionImpact;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.AbilityUsageGroup;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntityAbility;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandOffsetFromUser;
import com.github.standobyte.jojo.subsystems.entity_grab.LivingComponentGrab;
import com.github.standobyte.jojo.subsystems.target.AimingEntity;

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
					grabbedEntity.setDeltaMovement(throwVec);
					grabbedEntity.hurtMarked = true;

					StandEntity stand = (StandEntity) performer;
					float explRadius = Math.min((float) stand.getAttackDamage() * 0.175F, 10);
					KnockbackCollisionImpact kbImpact = KnockbackCollisionImpact.getHandler(grabbedEntity);
					if (kbImpact != null) {
						kbImpact
						.onPunchSetKnockbackImpact(grabbedEntity.getDeltaMovement(), stand)
						.withImpactExplosion(Math.max(explRadius - 0.5F, 0), null, 0);
					}
				}
				StandPower standPower = StandPower.get(getPowerUser());
				if (standPower != null) {
					standPower.consumeStamina(50);
				}
			}
			aimAs = AimingEntity.CAMERA_ENTITY;
		}

		@Override
		public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
			return phase.ordinal() < ActionPhase.PERFORM.ordinal();
		}
	}
}
