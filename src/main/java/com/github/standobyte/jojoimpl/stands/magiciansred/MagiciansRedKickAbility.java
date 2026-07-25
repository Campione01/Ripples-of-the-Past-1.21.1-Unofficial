package com.github.standobyte.jojoimpl.stands.magiciansred;

import com.github.standobyte.jojo.customobjects.DamageSourceModified;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance.StandPart;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojoimpl.stands._entitybase.StandEntityHeavyPunchAbility;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class MagiciansRedKickAbility extends StandEntityHeavyPunchAbility {
	private static final float KICK_KNOCKBACK_MULTIPLIER = 1.2F;

	public MagiciansRedKickAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId);
		this.createActionObj = MagiciansRedKick::new;
		partsRequired(StandPart.LEGS);
	}

	@Override
	public boolean noAdheringToUserOffset(StandPower standPower, StandEntity standEntity) {
		return standMovesByItself(standEntity);
	}

	@Override
	public boolean noAdheringToUserOffsetClientFallback(StandEntity standEntity) {
		return standMovesByItself(standEntity);
	}

	@Override
	public boolean lockStandManualMovement(StandPower standPower, StandEntity standEntity) {
		return standMovesByItself(standEntity);
	}

	private static boolean standMovesByItself(StandEntity standEntity) {
		EntityActionInstance action = standEntity.getCurStandAction();
		if (action == null) {
			return false;
		}
		ActionPhase phase = action.getPhase();
		return phase == ActionPhase.WINDUP && action.getPhaseTicksLeft() <= 2
				|| phase == ActionPhase.PERFORM
				|| phase == ActionPhase.RECOVERY;
	}

	@Override
	public Component getName(Power<?> context) {
		if (context instanceof StandPower standPower) {
			StandEntity stand = standPower.getSummonedStandEntity();
			if (stand != null && MRRedBindEntity.getLandedRedBind(stand).isPresent()) {
				return Component.translatable("jojo_ripples.ability.kick_bind");
			}
		}
		return super.getName(context);
	}

	public static class MagiciansRedKick extends StandEntityHeavyPunchAbility.StandEntityHeavyPunch {
		private static final double SLIDE_DISTANCE = 3;
		private LivingEntity redBindTarget;

		public MagiciansRedKick(EntityActionType ability) {
			super(ability);
		}

		@Override
		protected Holder<SoundEvent> getHeavyPunchImpactSound(ActionTarget target) {
			return ModSoundEvents.MAGICIANS_RED_KICK_HEAVY;
		}

		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			redBindTarget = null;
			boolean redBindFollowup = false;
			if (performer instanceof StandEntity stand) {
				MRRedBindEntity.getLandedRedBind(stand).ifPresent(redBind -> {
					redBind.setKickAttack();
					redBindTarget = redBind.getEntityAttachedTo();
				});
				redBindFollowup = redBindTarget != null;
				if (redBindFollowup && !stand.level().isClientSide()) {
					LivingEntity user = getPowerUser();
					if (user != null) {
						JojoModUtil.sayVoiceLine(user, ModSoundEvents.AVDOL_HELL_2_U);
					}
				}
			}
			super.onActionSet(prevAction);
		}

		@Override
		public void actionTick() {
			super.actionTick();
			if (performer instanceof StandEntity stand && getPhase() == ActionPhase.WINDUP) {
				float ticksLeft = getPhaseTicksLeft();
				if (ticksLeft <= 2 && ticksLeft > 1) {
					Vec3 targetPos = redBindTarget != null ? redBindTarget.getEyePosition() : stand.getEyePosition().add(stand.getLookAngle().scale(SLIDE_DISTANCE));
					Vec3 slideVec = targetPos.subtract(stand.getEyePosition());
					double distance = slideVec.length();
					if (distance > 0.0001) {
						slideVec = slideVec.normalize().scale(Math.min(Math.max(distance - stand.getBbWidth(), 0), SLIDE_DISTANCE));
						stand.setDeltaMovement(slideVec);
					}
				}
				else if (ticksLeft <= 1) {
					stand.setDeltaMovement(Vec3.ZERO);
					if (!stand.level().isClientSide()) {
						MRRedBindEntity.getLandedRedBind(stand).ifPresent(redBind -> {
							if (redBind.isInKickAttack()) {
								redBind.discard();
							}
						});
					}
				}
			}
		}

		@Override
		protected ActionTarget getPunchTarget(StandEntity stand) {
			if (redBindTarget != null && redBindTarget.isAlive()) {
				return new ActionTarget(redBindTarget);
			}
			return super.getPunchTarget(stand);
		}

		@Override
		protected void addKnockback(DamageSource dmgSource) {
			if (!(performer instanceof StandEntity stand)) {
				super.addKnockback(dmgSource);
				return;
			}
			float knockbackStrength = getAdditionalHeavyPunchKnockback(stand) * KICK_KNOCKBACK_MULTIPLIER;
			((DamageSourceModified) dmgSource).jojo_ripples$modifyKnockback(knockbackStrength, 1);
		}
	}
}
