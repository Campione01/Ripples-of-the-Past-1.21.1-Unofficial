package com.github.standobyte.jojoimpl.stands.hierophant;

import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance.StandPart;

import java.util.Optional;

import com.github.standobyte.jojo.mechanics.resolve.ResolveModeEffect;
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
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntityAbility;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandOffsetFromUser;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandStatFormulas;
import com.github.standobyte.jojo.powersystem.standpower.type.StandTypePersistentData;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.subsystems.target.ActionTarget.TargetType;
import com.github.standobyte.jojo.subsystems.target.HitResultUtil;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojoimpl.stands._entitybase.StandAbilityStamina;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class HierophantEmeraldSplashAbility extends StandEntityAbility implements TrainableAbility {
	private static final ActionAnimIdentifier EMERALD_SPLASH_ANIM = ActionAnimIdentifier.getOrCreate("emerald_splash", false);
	private static final ActionAnimIdentifier EMERALD_SPLASH_CONCENTRATED_ANIM = ActionAnimIdentifier.getOrCreate("emerald_splash_concentrated", false);
	public static final String EMERALD_SPLASH_LEARNING_ABILITY = "emerald_splash";
	public static final float EMERALD_SPLASH_LEARNING_PER_HIT = 0.002F;
	public static final float EMERALD_SPLASH_MAX_TRAINING = 1.0F;
	private static final int NORMAL_PERFORM_TICKS = 30;
	private static final int CONCENTRATED_PERFORM_TICKS = 5;
	private static final float STAMINA_COST_TICK = 3F;
	private static final float CONCENTRATED_STAMINA_COST_TICK = 6F;
	private static final double CONCENTRATED_FIRE_RATE_MULTIPLIER = 2.5;
	private static final float NORMAL_SHOT_VELOCITY = 2.0F;
	private static final float CONCENTRATED_SHOT_VELOCITY = 3.0F;
	private static final float NORMAL_INACCURACY = 8.0F;
	private static final float CONCENTRATED_INACCURACY = 1.0F;

	static float barrierRippedEmeraldStaminaCostTick() {
		return STAMINA_COST_TICK * 0.5F;
	}

	private final boolean concentrated;

	public HierophantEmeraldSplashAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, type -> new EmeraldSplashShot(type, abilityId.nameInMoveset().equals("emerald_splash_concentrated")));
		partsRequired(StandPart.ARMS);
		this.concentrated = abilityId.nameInMoveset().equals("emerald_splash_concentrated");
		setDefaultPhaseLength(ActionPhase.PERFORM, concentrated ? CONCENTRATED_PERFORM_TICKS : NORMAL_PERFORM_TICKS);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 40);
		if (concentrated) {
			cooldown(5, 60);
		}
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		return check.isPositive()
				? StandAbilityStamina.check(context, concentrated ? CONCENTRATED_STAMINA_COST_TICK : STAMINA_COST_TICK)
				: check;
	}

	@Override
	public String getLearningAbilityName() {
		return EMERALD_SPLASH_LEARNING_ABILITY;
	}

	@Override
	public float getMaxTrainingPoints(StandPower power) {
		return EMERALD_SPLASH_MAX_TRAINING;
	}

	@Override
	public void onMaxTraining(StandPower power) {
		StandTypePersistentData data = power != null ? power.getCurTypeData() : null;
		if (data != null && !data.isSkillUnlocked("emerald_splash_concentrated")) {
			data._setSkillUnlocked("emerald_splash_concentrated", true, false);
		}
	}

	@Override
	public boolean isAbilityAvailable(Power<?> context) {
		if (concentrated) {
			return super.isAbilityAvailable(context) && isEmeraldSplashFullyTrained(context);
		}
		return super.isAbilityAvailable(context);
	}

	@Override
	public Ability replaceWithSubAbility(Power<?> context, AvailableAbilities abilities) {
		if (!concentrated && isEmeraldSplashFullyTrained(context)) {
			Ability concentratedSplash = abilities.getContextVariation("emerald_splash_concentrated");
			if (concentratedSplash != null) {
				return concentratedSplash;
			}
		}
		return super.replaceWithSubAbility(context, abilities);
	}

	public static boolean isEmeraldSplashFullyTrained(Power<?> context) {
		if (context instanceof StandPower standPower) {
			if (standPower.isUserCreative()) {
				return true;
			}
			var data = standPower.getCurTypeData();
			return data != null && data.getAbilityLearningProgressPoints(EMERALD_SPLASH_LEARNING_ABILITY) >= EMERALD_SPLASH_MAX_TRAINING;
		}
		return false;
	}

	@Override
	public void initActionFromConfig(EntityActionInstance action, Level level, LivingEntity powerUser, LivingEntity performer) {
		super.initActionFromConfig(action, level, powerUser, performer);
		if (performer instanceof StandEntity stand) {
			StandPower standPower = StandPower.get(powerUser);
			boolean noPhases = noPhases(standPower, powerUser);
			EntityActionInstance curAction = LivingComponentAction.getCurEntityAction(performer);
			boolean sameSplashChain = curAction != null && curAction.ability instanceof HierophantEmeraldSplashAbility;
			action.phasesLength.put(ActionPhase.WINDUP, noPhases || sameSplashChain ? 0 : StandStatFormulas.getHeavyAttackWindup(stand.getAttackSpeed(), 0));
			action.phasesLength.put(ActionPhase.PERFORM, concentrated ? CONCENTRATED_PERFORM_TICKS : NORMAL_PERFORM_TICKS);
			action.phasesLength.put(ActionPhase.RECOVERY, noPhases ? 0 : 40);
		}
	}

	private static boolean noPhases(StandPower standPower, LivingEntity user) {
		return standPower != null && user != null
				&& (ResolveModeEffect.getEffectiveResolveLevel(user, standPower) > 2
						|| ResolveModeEffect.getResolveEffectLvl(user) >= 0);
	}

	@Override
	public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
		return concentrated ? EMERALD_SPLASH_CONCENTRATED_ANIM : EMERALD_SPLASH_ANIM;
	}

	public static class EmeraldSplashShot extends EntityActionInstance {
		private final boolean concentrated;
		private boolean voiceLinePlayed;
		private boolean standSoundPlayed;

		public EmeraldSplashShot(EntityActionType ability, boolean concentrated) {
			super(ability);
			this.concentrated = concentrated;
		}

		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			updateStandOffset(getPhase());
		}

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			updateStandOffset(newPhase);
		}

		private void updateStandOffset(ActionPhase phase) {
			if (!(getPerformer() instanceof StandEntity stand)) {
				return;
			}
			if (stand.isArmsOnlyMode() || phase == ActionPhase.PERFORM) {
				_setStandOffset(stand, new Vec3(0.0D, stand.Y_OFFSET, 0.5D), StandOffsetFromUser.Rotations.HEAD_XY, stand.isArmsOnlyMode());
			}
			else if (stand.offsetFromUser.standAbility == ability) {
				stand.offsetFromUser.resetToIdle();
			}
		}

		@Override
		public void actionTick() {
			if (getPhase() != ActionPhase.PERFORM || level().isClientSide()) {
				return;
			}
			if (!(getPerformer() instanceof StandEntity stand)) {
				return;
			}
			LivingEntity user = getPowerUser();
			StandPower standPower = user != null ? StandPower.get(user) : null;
			if (!StandAbilityStamina.consume(ability, standPower, staminaCostTick(), true)) {
				startRecovery();
				return;
			}
			if (!voiceLinePlayed && user != null) {
				JojoModUtil.sayVoiceLine(user, ModSoundEvents.KAKYOIN_EMERALD_SPLASH);
				voiceLinePlayed = true;
			}
			if (!standSoundPlayed) {
				StandUtil.playStandEntitySound(stand, ModSoundEvents.HIEROPHANT_GREEN_EMERALD_SPLASH, 1.0F, 1.0F);
				standSoundPlayed = true;
			}

			double fireRate = StandStatFormulas.projectileFireRateScaling(stand, standPower);
			if (concentrated) {
				fireRate *= CONCENTRATED_FIRE_RATE_MULTIPLIER;
			}
			runFractionalTimes(() -> shootEmerald(stand), fireRate, stand.getRandom()::nextDouble);
			shootFromPlacedBarriers(stand, standPower);
		}

		private float staminaCostTick() {
			return concentrated ? CONCENTRATED_STAMINA_COST_TICK : STAMINA_COST_TICK;
		}

		private void shootEmerald(StandEntity stand) {
			HGEmeraldEntity emerald = new HGEmeraldEntity(stand, level());
			emerald.setBreakBlocks(concentrated);
			emerald.setLowerKnockback(!concentrated);
			if (!concentrated) {
				emerald.grantEmeraldSplashTraining();
			}
			float velocity = concentrated ? CONCENTRATED_SHOT_VELOCITY : NORMAL_SHOT_VELOCITY;
			float inaccuracy = concentrated ? CONCENTRATED_INACCURACY : NORMAL_INACCURACY;
			emerald.shootFromRotation(stand, stand.getXRot(), stand.getYRot(), 0,
					velocity,
					StandStatFormulas.projectileInaccuracyScaling(stand.getPrecision(), inaccuracy));
			addProjectileWithStandStats(emerald);
		}

		private void shootFromPlacedBarriers(StandEntity stand, StandPower standPower) {
			if (!(stand instanceof HierophantGreenEntity hierophant) || hierophant.getPlacedBarriersCount() <= 0) {
				return;
			}
			barrierTarget(hierophant).ifPresent(targetPos -> hierophant.getBarriersNet().shootEmeraldsFromBarriers(
					standPower,
					hierophant,
					targetPos,
					(int) getPhaseTick(),
					Math.min(hierophant.getPlacedBarriersCount() / 5, 9) * hierophant.staminaCondition,
					CONCENTRATED_STAMINA_COST_TICK * 0.5F,
					8,
					true));
		}

		private Optional<Vec3> barrierTarget(HierophantGreenEntity stand) {
			LivingEntity aimingEntity = stand.isManuallyControlled() ? stand : stand.getUser();
			if (aimingEntity == null) {
				return Optional.empty();
			}
			var aim = LivingComponentAction.getAim(aimingEntity);
			ActionTarget target = aim != null ? aim.getTarget() : ActionTarget.EMPTY;
			if (target != null && !target.isEmpty(level())
					&& !HitResultUtil.isTargetWithinRange(target, aimingEntity, level(),
							stand.getMaxRange(), stand.getMaxRange())) {
				target = ActionTarget.EMPTY;
			}
			if (target == null || target.isEmpty(level())) {
				target = HitResultUtil.clip(aimingEntity.getEyePosition(), aimingEntity.getLookAngle(),
						stand.getMaxRange(), stand.getMaxRange(), level(),
						entity -> entity instanceof LivingEntity && stand.canAttackEntity(entity),
						aimingEntity, stand.getPrecision());
			}
			if (target == null || target.isEmpty(level())) {
				return Optional.empty();
			}
			if (target.getType() == TargetType.ENTITY) {
				Entity entity = target.getEntity();
				return entity != null ? Optional.of(entity.getBoundingBox().getCenter()) : Optional.empty();
			}
			if (target.getType() == TargetType.BLOCK) {
				Optional<Vec3> clipPos = target.getClipPos();
				return clipPos.isPresent() ? clipPos : Optional.of(Vec3.atCenterOf(target.getBlockPos()));
			}
			return Optional.empty();
		}

		@Override
		public void onButtonStopHold() {
			if (getPhase() == ActionPhase.PERFORM) {
				startRecovery();
			}
		}

		@Override
		public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
			return getPhase() == ActionPhase.RECOVERY || isEmeraldSplash(cancellingAbility);
		}

		private static boolean isEmeraldSplash(EntityActionType cancellingAbility) {
			if (cancellingAbility.getAbilityId() == null) {
				return false;
			}
			String abilityName = cancellingAbility.getAbilityId().nameInMoveset();
			return "emerald_splash".equals(abilityName)
					|| "emerald_splash_concentrated".equals(abilityName);
		}

		private static void runFractionalTimes(Runnable action, double times, RandomDouble random) {
			int wholeTimes = (int) Math.floor(times);
			for (int i = 0; i < wholeTimes; i++) {
				action.run();
			}
			double fraction = times - wholeTimes;
			if (fraction > 0 && random.nextDouble() < fraction) {
				action.run();
			}
		}

		@Override
		public void toBuf(FriendlyByteBuf buf) {
			buf.writeBoolean(concentrated);
		}

		@Override
		public void fromBuf(FriendlyByteBuf buf) {
			buf.readBoolean();
		}
	}

	@FunctionalInterface
	private interface RandomDouble {
		double nextDouble();
	}
}
