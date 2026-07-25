package com.github.standobyte.jojoimpl.stands.magiciansred;

import com.github.standobyte.jojo.powersystem.standpower.StandInstance.StandPart;

import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.TrainableAbility;
import com.github.standobyte.jojo.powersystem.ability.condition.AvailableAbilities;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.ActionAnimIdentifier;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.entityaction.netcode.SyncType;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntityAbility;
import com.github.standobyte.jojo.powersystem.standpower.type.StandTypePersistentData;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.subsystems.target.HitResultUtil;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojoimpl.stands._entitybase.StandAbilityStamina;
import com.github.standobyte.jojoimpl.stands._entitybase.StandEntityPunchAbility;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class MagiciansRedCrossfireHurricaneAbility extends StandEntityAbility implements TrainableAbility {

	private static final ActionAnimIdentifier CROSSFIRE_HURRICANE_ANIM = ActionAnimIdentifier.getOrCreate("crossfire_hurricane", false);
	private static final ActionAnimIdentifier CROSSFIRE_HURRICANE_SPECIAL_ANIM = ActionAnimIdentifier.getOrCreate("crossfire_hurricane_special", false);
	private static final String SPECIAL_ABILITY = "crossfire_hurricane_special";
	public static final String CROSSFIRE_HURRICANE_LEARNING_ABILITY = "crossfire_hurricane";
	public static final float CROSSFIRE_HURRICANE_LEARNING_PER_HIT = 0.03125F;
	public static final float CROSSFIRE_HURRICANE_MAX_TRAINING = 1.0F;
	private static final int HOLD_TO_FIRE_TICKS = 20;
	private static final int STAMINA_COST = 500;
	private static final float NORMAL_SHOT_VELOCITY = 1.25F;
	private static final float SPECIAL_SHOT_VELOCITY = 2.0F;
	private static final int SPECIAL_SHOT_COUNT = 8;
	private static final float SPECIAL_SPREAD_DEGREES = 1.5F;
	private static final double SPECIAL_AIM_RANGE = 64.0D;

	public MagiciansRedCrossfireHurricaneAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, CrossfireShot::new);
		partsRequired(StandPart.MAIN_BODY);
		if (SPECIAL_ABILITY.equals(name())) {
			isSubAbility = true;
		}
		setDefaultPhaseLength(ActionPhase.BUTTON_CHARGE, HOLD_TO_FIRE_TICKS);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		return check.isPositive() ? StandAbilityStamina.check(context, STAMINA_COST) : check;
	}

	@Override
	public String getLearningAbilityName() {
		return CROSSFIRE_HURRICANE_LEARNING_ABILITY;
	}

	@Override
	public float getMaxTrainingPoints(StandPower power) {
		return CROSSFIRE_HURRICANE_MAX_TRAINING;
	}

	@Override
	public void onMaxTraining(StandPower power) {
		StandTypePersistentData data = power != null ? power.getCurTypeData() : null;
		if (data != null && !data.isSkillUnlocked(SPECIAL_ABILITY)) {
			data._setSkillUnlocked(SPECIAL_ABILITY, true, false);
		}
	}

	@Override
	public boolean isAbilityAvailable(Power<?> context) {
		if (SPECIAL_ABILITY.equals(name())) {
			return super.isAbilityAvailable(context) && isCrossfireHurricaneFullyTrained(context);
		}
		return super.isAbilityAvailable(context);
	}

	public static boolean isCrossfireHurricaneFullyTrained(Power<?> context) {
		if (context instanceof StandPower standPower) {
			if (standPower.isUserCreative()) {
				return true;
			}
			var data = standPower.getCurTypeData();
			return data != null && data.getAbilityLearningProgressPoints(CROSSFIRE_HURRICANE_LEARNING_ABILITY) >= CROSSFIRE_HURRICANE_MAX_TRAINING;
		}
		return false;
	}

	@Override
	public Ability replaceWithSubAbility(Power<?> context, AvailableAbilities abilities) {
		if (abilities == null || SPECIAL_ABILITY.equals(name())) {
			return this;
		}
		LivingEntity user = context.getUser();
		if (user != null && user.isShiftKeyDown()) {
			Ability special = abilities.getContextVariation(SPECIAL_ABILITY);
			if (special != null) {
				return special;
			}
		}
		return this;
	}

	@Override
	public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
		return SPECIAL_ABILITY.equals(name()) ? CROSSFIRE_HURRICANE_SPECIAL_ANIM : CROSSFIRE_HURRICANE_ANIM;
	}

	public static class CrossfireShot extends EntityActionInstance {

		public CrossfireShot(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			if (getPerformer() instanceof StandEntity stand) {
				ActionTarget target = captureActionTargetFromAim(stand);
				keepStandAimedAtTarget(target);
			}
		}

		@Override
		public void actionPerformStart() {
			Level level = level();
			if (level.isClientSide()) {
				return;
			}
			LivingEntity user = getPowerUser();
			if (user == null || !(getPerformer() instanceof StandEntity stand)) {
				clearAction();
				return;
			}
			StandPower standPower = StandPower.get(user);
			if (!StandAbilityStamina.consumeOrMessage(ability, standPower, user, STAMINA_COST)) {
				clearAction();
				return;
			}
			boolean special = SPECIAL_ABILITY.equals(ability.getAbilityId().nameInMoveset());
			int shots = special ? SPECIAL_SHOT_COUNT : 1;
			Vec3 targetPos = special ? getAimTarget(level, stand, user) : null;
			JojoModUtil.sayVoiceLine(user, special
					? ModSoundEvents.AVDOL_CROSSFIRE_HURRICANE_SPECIAL
					: ModSoundEvents.AVDOL_CROSSFIRE_HURRICANE);
			for (int i = 0; i < shots; i++) {
				MRCrossfireHurricaneEntity cross = new MRCrossfireHurricaneEntity(special, performer, level);
				cross.setShootingPosOf(performer);
				cross.setScale((float) stand.getStandEfficiency());
				if (special && targetPos != null) {
					cross.setSpecial(targetPos);
				}
				cross.shootFromRotation(performer,
						performer.getXRot() + specialXRotOffset(special, i),
						performer.getYRot() + specialYRotOffset(special, i),
						0,
						special ? SPECIAL_SHOT_VELOCITY : NORMAL_SHOT_VELOCITY,
						0);
				cross.withStandSkin(stand.getStandType(), stand.getStandSkin());
				level.addFreshEntity(cross);
			}
			StandUtil.playStandEntitySound(stand, ModSoundEvents.MAGICIANS_RED_CROSSFIRE_HURRICANE, 1.0F, 1.0F);
		}

		@Override
		public void onButtonStopHold() {
			if (getPhase() == ActionPhase.BUTTON_CHARGE) {
				clearAction();
			}
		}

		private void clearAction() {
			LivingComponentAction.getComponent(performer).setAction(null, SyncType.TRACKING_AND_SELF);
		}

		private Vec3 getAimTarget(Level level, StandEntity stand, LivingEntity user) {
			ActionTarget target = getActionTargetSnapshot(level);
			if (!target.isEmpty(level)
					&& !HitResultUtil.isTargetWithinRange(target, stand, level, SPECIAL_AIM_RANGE, SPECIAL_AIM_RANGE)) {
				target = ActionTarget.EMPTY;
			}
			if (target.isEmpty(level)) {
				target = fallbackAimTarget(level, stand, user);
			}
			if (target == null || target.isEmpty(level)) {
				return null;
			}
			stand.lookAtTarget(target, false);
			return getOriginalSpecialTargetPos(target);
		}

		private static Vec3 getOriginalSpecialTargetPos(ActionTarget target) {
			if (target.getType() == ActionTarget.TargetType.ENTITY && target.getEntity() != null) {
				return target.getEntity().getEyePosition(1.0F);
			}
			return target.getCenterPos();
		}

		private ActionTarget fallbackAimTarget(Level level, StandEntity stand, LivingEntity user) {
			ActionTarget target = ActionTarget.EMPTY;
			if (!stand.isManuallyControlled()) {
				target = clipAim(level, user, stand);
			}
			if (target.isEmpty(level)) {
				target = clipAim(level, stand, stand);
			}
			return target;
		}

		private static ActionTarget clipAim(Level level, LivingEntity aiming, StandEntity stand) {
			double standPrecision = aiming == stand ? stand.getPrecision() : 0.0D;
			return HitResultUtil.clip(aiming.getEyePosition(), aiming.getLookAngle(),
					SPECIAL_AIM_RANGE, SPECIAL_AIM_RANGE, level,
					entity -> StandEntityPunchAbility.canStandHit(stand, entity),
					aiming, standPrecision);
		}

		private static float specialXRotOffset(boolean special, int shotIndex) {
			if (!special || shotIndex == 0) {
				return 0;
			}
			double angle = ((double) shotIndex / (double) SPECIAL_SHOT_COUNT + 0.5D) * Math.PI;
			return (float) Math.sin(angle) * SPECIAL_SPREAD_DEGREES;
		}

		private static float specialYRotOffset(boolean special, int shotIndex) {
			if (!special || shotIndex == 0) {
				return 0;
			}
			double angle = ((double) shotIndex / (double) SPECIAL_SHOT_COUNT + 0.5D) * Math.PI;
			return (float) Math.cos(angle) * SPECIAL_SPREAD_DEGREES;
		}
	}
}
