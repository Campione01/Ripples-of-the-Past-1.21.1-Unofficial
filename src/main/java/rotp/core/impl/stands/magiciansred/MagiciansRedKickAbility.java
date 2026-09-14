package rotp.core.impl.stands.magiciansred;

import rotp.core.customobjects.DamageSourceModified;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;

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
