package com.github.standobyte.jojoimpl.stands.magiciansred;

import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance.StandPart;

import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.ActionAnimIdentifier;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntityAbility;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandOffsetFromUser;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojoimpl.stands._entitybase.StandAbilityStamina;

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
				JojoModUtil.sayVoiceLine(user, ModSoundEvents.AVDOL_RED_BIND);
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
