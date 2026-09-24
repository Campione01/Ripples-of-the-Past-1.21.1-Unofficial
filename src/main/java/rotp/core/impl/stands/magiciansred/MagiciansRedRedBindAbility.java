package rotp.core.impl.stands.magiciansred;

import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.standpower.StandInstance.StandPart;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class MagiciansRedRedBindAbility extends StandEntityAbility {

	private static final ActionAnimIdentifier RED_BIND_ANIM = ActionAnimIdentifier.getOrCreate("red_bind", false);
	private static final float STAMINA_COST_TICK = 1F;

	public MagiciansRedRedBindAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, RedBindShot::new);
		partsRequired(StandPart.ARMS);
		noFinisherBarDecay = true;
		setButtonHoldPhase(ActionPhase.PERFORM);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		return check.isPositive() ? StandAbilityStamina.check(context, STAMINA_COST_TICK) : check;
	}

	@Override
	public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
		return RED_BIND_ANIM;
	}

	public static class RedBindShot extends EntityActionInstance {

		public RedBindShot(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			setStandOffset(0.0, 0.5, StandOffsetFromUser.Rotations.BODY, false);
		}

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			userWalkSpeed = newPhase == ActionPhase.PERFORM ? 0.3F : 1;
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
			if (!(getPerformer() instanceof StandEntity stand)) {
				return;
			}
			MRRedBindEntity bind = new MRRedBindEntity(stand, level);
			addProjectileWithStandStats(bind);
			if (!stand.willHeavyPunchBeFinisher()) {
				Ability.sayShoutOf(ability, user, ModSoundEvents.AVDOL_RED_BIND);
			}
			StandUtil.playStandEntitySound(stand, ModSoundEvents.MAGICIANS_RED_RED_BIND, 1.0F, 1.0F);
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
			if (performer instanceof StandEntity stand
					&& stand.willHeavyPunchBeFinisher()
					&& MRRedBindEntity.getLandedRedBind(stand).isPresent()) {
				return;
			}
			startRecovery();
		}

		@Override
		public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
			if (performer instanceof StandEntity stand
					&& stand.willHeavyPunchBeFinisher()
					&& MRRedBindEntity.getLandedRedBind(stand).isPresent()
					&& cancellingAbility.getAbilityId() != null
					&& "kick".equals(cancellingAbility.getAbilityId().nameInMoveset())) {
				return true;
			}
			return super.canBeCancelledInto(cancellingAbility);
		}
	}
}
