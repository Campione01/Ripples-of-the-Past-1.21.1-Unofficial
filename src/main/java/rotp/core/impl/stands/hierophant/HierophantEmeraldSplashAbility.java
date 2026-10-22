package rotp.core.impl.stands.hierophant;

import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.standpower.StandInstance.StandPart;

import java.util.Optional;
import java.util.function.Predicate;

import rotp.core.mechanics.resolve.ResolveModeEffect;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.TrainableAbility;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.powersystem.standpower.type.StandTypePersistentData;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
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
			// 1.16 hierophant_green_es_concentrated was the SHIFT variation
			setPlaysVoiceLineOnSneak();
		}
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

	// no replaceWithSubAbility: the concentrated splash has its own wheel slot, LMB stays the normal splash
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

	// 1.16 HierophantGreenEmeraldSplash.noPhases: the stored Resolve level above 2, or any Resolve effect
	private static boolean noPhases(StandPower standPower, LivingEntity user) {
		return standPower != null && user != null
				&& (standPower.getResolveLevel() > 2 || ResolveModeEffect.getResolveEffectLvl(user) >= 0);
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

		// 1.16 HierophantGreenEmeraldSplash.getOffsetFromUser: standOffsetFront() while it fires in full mode, the idle
		// offset in every other phase and in arms-only mode, which had no offset for it
		private void updateStandOffset(ActionPhase phase) {
			if (!(getPerformer() instanceof StandEntity stand)) {
				return;
			}
			if (phase == ActionPhase.PERFORM && !stand.isArmsOnlyMode()) {
				setStandOffset(0.0D, 0.5D, StandOffsetFromUser.Rotations.HEAD, false);
			}
			else {
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
			if (!voiceLinePlayed && user != null) {
				Ability.sayShoutOf(ability, user, ModSoundEvents.KAKYOIN_EMERALD_SPLASH);
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
			// 1.16 StandEntityTask paid each perform tick after the shots and ignored the result
			StandAbilityStamina.consume(ability, standPower, staminaCostTick(), true);
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
					stand.getProjectileInaccuracy(inaccuracy));
			addProjectileWithStandStats(emerald);
		}

		private void shootFromPlacedBarriers(StandEntity stand, StandPower standPower) {
			if (!(stand instanceof HierophantGreenEntity hierophant) || hierophant.getPlacedBarriersCount() <= 0) {
				return;
			}
			barrierAimPoint(hierophant).ifPresent(targetPos -> hierophant.getBarriersNet().shootEmeraldsFromBarriers(
					standPower,
					hierophant,
					targetPos,
					(int) getPhaseTick(),
					Math.min(hierophant.getPlacedBarriersCount() / 5, 9) * hierophant.staminaCondition,
					CONCENTRATED_STAMINA_COST_TICK * 0.5F,
					8,
					true));
		}

		/**
		 * Where the barrier net fires its emeralds: what the user aims at, or the Stand under manual control. The net
		 * senses its target and fires without sight, as in the original story and as 1.16's aimForBarriers ray
		 * (JojoModUtil.rayTrace) did, so a mob behind blocks on the aim is the aim point; the emeralds still stop at blocks.
		 * That ray took no precision boxes, so the aim assist setting does not change it.
		 */
		public static Optional<Vec3> barrierAimPoint(HierophantGreenEntity stand) {
			Level level = stand.level();
			LivingEntity aimingEntity = stand.isManuallyControlled() ? stand : stand.getUser();
			if (aimingEntity == null) {
				return Optional.empty();
			}
			Predicate<Entity> netTarget = entity -> entity instanceof LivingEntity && stand.canAttackEntity(entity);
			var aim = LivingComponentAction.getAim(aimingEntity);
			ActionTarget target = aim != null ? aim.getTarget() : ActionTarget.EMPTY;
			if (target != null && !target.isEmpty(level)
					&& !HitResultUtil.isTargetWithinRange(target, aimingEntity, level,
							stand.getMaxRange(), stand.getMaxRange())) {
				target = ActionTarget.EMPTY;
			}
			// the ray's filter: an entity the net would not fire at is passed over
			if (target != null && target.getType() == TargetType.ENTITY && !netTarget.test(target.getEntity())) {
				target = ActionTarget.EMPTY;
			}
			// the Stand's synced aim under manual control is a precision pick at its own reach; the net's ray took none
			if (target != null && target.getType() == TargetType.ENTITY
					&& !strictRayMeets(aimingEntity, target.getEntity(), stand.getMaxRange())) {
				target = ActionTarget.EMPTY;
			}
			// a synced aim was picked with walls hiding what is behind them; 1.16's ray met a block by its outline
			if (target == null || target.isEmpty(level) || target.getType() == TargetType.BLOCK) {
				ActionTarget throughWalls = HitResultUtil.clip(aimingEntity.getEyePosition(), aimingEntity.getLookAngle(),
						stand.getMaxRange(), stand.getMaxRange(), level, netTarget, aimingEntity, 0, false, 0,
						ClipContext.Block.OUTLINE);
				if (target == null || target.isEmpty(level) || throughWalls.getType() == TargetType.ENTITY) {
					target = throughWalls;
				}
			}
			if (target == null || target.isEmpty(level)) {
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

		private static boolean strictRayMeets(LivingEntity aiming, Entity entity, double maxRange) {
			if (entity == null) {
				return false;
			}
			Vec3 eyes = aiming.getEyePosition();
			AABB box = entity.getBoundingBox().inflate(entity.getPickRadius());
			return box.contains(eyes) || box.clip(eyes, eyes.add(aiming.getLookAngle().scale(maxRange))).isPresent();
		}

		@Override
		public void onButtonStopHold() {
			if (getPhase() == ActionPhase.PERFORM) {
				startRecovery();
			}
		}

		// 1.16 HierophantGreenEmeraldSplash.isCancelable: only in RECOVERY, for any action, the other variant and the
		// chainable splash itself included; pressed before that it was queued (canBeQueued), which the input buffer does
		@Override
		public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
			return getPhase() == ActionPhase.RECOVERY;
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
