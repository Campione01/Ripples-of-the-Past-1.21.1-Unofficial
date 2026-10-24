package rotp.core.impl.stands.silverchariot;

import rotp.core.customobjects.DamageSourceModified;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.standpower.StandInstance.StandPart;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import rotp.core.api.stand.StandSkillWalls;
import rotp.core.client.ClientGlobals;
import rotp.core.client.sound.ClientsideSoundsHelper;
import rotp.core.init.ModSoundEvents;
import rotp.core.mechanics.KnockbackCollisionImpact;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.subsystems.target.StandPrecisionRay;
import rotp.core.subsystems.timestop.TimeStopState;
import rotp.core.util.functions.MathUtil;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility.CrosshairRule;

import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class SilverChariotDashAttackAbility extends StandEntityAbility {

	private static final int STAMINA_COST = 50;
	private static final float DASH_KNOCKBACK_TO_STRENGTH = 0.5F;
	private static final float ARMORED_DASH_EXTRA_KNOCKBACK = 1.5F;
	private static final float FAST_DASH_EXTRA_KNOCKBACK = 0.25F;
	private static final float FAST_DASH_KNOCKBACK_XROT = -90F;

	public SilverChariotDashAttackAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, DashStrike::new);
		partsRequired(StandPart.MAIN_BODY, StandPart.ARMS);
		setDefaultPhaseLength(ActionPhase.WINDUP, 0);
		setDefaultPhaseLength(ActionPhase.PERFORM, 2);
	}

	/** 1.16 SilverChariotDashAttack: standKeepsTarget false and checkStandTarget NEGATIVE, so the press took no target. */
	@Override
	public boolean takesPressedTarget(StandEntity standEntity, ActionTarget target) {
		return false;
	}

	@Override
	public void initActionFromConfig(EntityActionInstance action, Level level,
			LivingEntity powerUser, LivingEntity performer) {
		super.initActionFromConfig(action, level, powerUser, performer);
		if (!level.isClientSide() && performer instanceof StandEntity stand) {
			action.phasesLength.put(ActionPhase.PERFORM, Math.max(StandStatFormulas.getHeavyAttackWindup(
					stand.getAttackSpeed(), stand.getFinisherMeter()), 2));
			// recovery is set in onActionSet from the finisher snapshot
		}
	}

	/** 1.16 StandEntityHeavyAttack onClick/onTaskSet: snapshot the finisher meter, recover by it, spend 0.51. */
	static void setHeavyFinisher(EntityActionInstance action, StandEntity stand) {
		// the client keeps the recovery the action came with: its copy of the snapshot is still the previous one; an
		// attack a failed input puts back keeps what it took and spent when it was first set
		if (!stand.level().isClientSide() && !action.isRestoringInputLifecycle()) {
			stand.setHeavyPunchFinisher();
			action.phasesLength.put(ActionPhase.RECOVERY, StandStatFormulas.getHeavyAttackRecovery(
					stand.getAttackSpeed(), stand.getLastHeavyFinisherValue()));
			stand.addFinisherMeter(-0.51F, 0);
		}
	}

	/**
	 * 1.16 StandEntityHeavyAttack.getStandRecoveryTicks, read by StandEntityTask as the recovery starts: the recovery
	 * follows the Stand's attack speed at that moment, not the one setHeavyFinisher saw. Call from onSetPhase.
	 */
	static boolean readRecoveryAtEntry(EntityActionInstance action, ActionPhase newPhase) {
		if (newPhase == ActionPhase.RECOVERY && action.getPhase() != null && action.getPhase() != newPhase
				&& action.getPerformer() instanceof StandEntity stand && !stand.level().isClientSide()) {
			float before = action.phasesLength.getFloat(ActionPhase.RECOVERY);
			action.phasesLength.put(ActionPhase.RECOVERY, StandStatFormulas.getHeavyAttackRecovery(
					stand.getAttackSpeed(), stand.getLastHeavyFinisherValue()));
			return Float.compare(before, action.phasesLength.getFloat(ActionPhase.RECOVERY)) != 0;
		}
		return false;
	}

	/** 1.16 HeavyPunchInstance.afterAttack: stop a hit Stand's barrage, arm the wall impact on the knocked-back entity. */
	static void afterHeavyHit(StandEntity stand, Entity target) {
		EntityActionAbility.onHitByHeavyAttack(target);
		Entity knockedBack = target;
		if (target instanceof StandEntity targetStand && targetStand.getUser() != null) {
			knockedBack = targetStand.getUser();
		}
		KnockbackCollisionImpact kbImpact = KnockbackCollisionImpact.getHandler(knockedBack);
		if (kbImpact != null) {
			kbImpact.onPunchSetKnockbackImpact(knockedBack.getDeltaMovement(), stand)
					.withImpactExplosion(Math.max(StandEntityHeavyPunchAbility.calcExplosionRadius(stand) - 0.5F, 0), null, 0);
		}
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		if (!check.isPositive()) {
			return check;
		}
		if (lacksRapier(context)) {
			return ConditionCheck.createNegative("chariot_rapier");
		}
		return check;
	}

	private static boolean lacksRapier(Power<?> context) {
		// Client-aware: the HUD greys the move from the Stand's synced flag
		return !SilverChariotState.hasRapier(PowerClass.STAND.cast(context));
	}

	@Override
	public boolean noAdheringToUserOffset(StandPower standPower, StandEntity standEntity) {
		return true;
	}

	@Override
	public boolean noAdheringToUserOffsetClientFallback(StandEntity standEntity) {
		return true;
	}

	@Override
	public boolean lockStandManualMovement(StandPower standPower, StandEntity standEntity) {
		return true;
	}

	public static class DashStrike extends EntityActionInstance {
		private final Set<Integer> hitEntities = new HashSet<>();
		private Vec3 dashStep = Vec3.ZERO;
		private Vec3 dashForward = Vec3.ZERO;
		private boolean retractAfterDash;
		private boolean recoveryReread;

		public DashStrike(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			if (getPerformer() instanceof StandEntity stand) {
				ActionTarget target = captureActionTargetFromAim(stand);
				keepStandAimedAtTarget(target);
				retractAfterDash = stand.followingUserIsEnabled() && !stand.isManuallyControlled() && !stand.isBeingRetracted();
				setHeavyFinisher(this, stand);
			}
		}

		@Override
		public void setPhase(ActionPhase newPhase, int tick) {
			recoveryReread = false;
			super.setPhase(newPhase, tick);
			if (recoveryReread) {
				recoveryReread = false;
				syncPhaseChanges();
			}
		}

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			recoveryReread = readRecoveryAtEntry(this, newPhase);
			// a failed input that puts the dash back mid-dash keeps the stamina it paid, its path and what it hit
			if (newPhase != ActionPhase.PERFORM || isRestoringInputLifecycle() || !(getPerformer() instanceof StandEntity stand)) {
				return;
			}
			LivingEntity user = getPowerUser();
			if (!level().isClientSide()) {
				if (user == null) {
					startRecovery();
					return;
				}
				SilverChariotState state = SilverChariotState.get(user);
				if (state != null && !state.hasRapier()) {
					startRecovery();
					return;
				}
				// 1.16 StandEntityTask paid 50 as the perform started and ignored the result: a short bar just drops to 0
				StandAbilityStamina.consume(ability, StandPower.get(user), STAMINA_COST, false);
			}
			else if (ClientGlobals.canHearStand(stand)) {
				level().playLocalSound(stand, ClientsideSoundsHelper.withStandSkin(
						ModSoundEvents.SILVER_CHARIOT_DASH.get(), stand), 
						stand.getSoundSource(), 1.0F, 1.0F);
			}
			dashForward = dashLook(stand, user);
			hitEntities.clear();
			if (!level().isClientSide() && retractAfterDash && stand.getAttackSpeed() < 24 && user != null) {
				Vec3 userForward = horizontalLook(user, null, 0.0);
				stand.setPos(user.getX() + userForward.x, stand.getY(), user.getZ() + userForward.z);
			}
			float performTicks = Math.max(phasesLength.getFloat(ActionPhase.PERFORM), 1.0F);
			dashStep = dashForward.scale(10D / performTicks);
		}

		@Override
		public void actionTick() {
			if (getPhase() != ActionPhase.PERFORM || !(getPerformer() instanceof StandEntity stand)) {
				return;
			}
			boolean moveForward = getCurPhaseLength() <= 0 || getPhaseRatio() <= 0.5F;
			boolean hadForwardMovement = stand.getDeltaMovement().lengthSqr() > 1.0E-7;

			Level level = level();
			if (!level.isClientSide()) {
				if (moveForward) {
					hitDashTargets(level, stand);
				}
				else if (hadForwardMovement) {
					// 1.16 struck the task's target here, which the dash never kept
					hitReaimedDashTarget(level, stand);
				}
			}
			if (!level.isClientSide() && getPhaseTicksLeft() <= 1 && retractAfterDash) {
				stand.retract();
			}
			stand.setDeltaMovement(moveForward ? dashStep : Vec3.ZERO);
			stand.hurtMarked = true;
		}

		@Override
		public void actionPerformEnd() {
			if (getPerformer() instanceof StandEntity stand) {
				stand.setDeltaMovement(Vec3.ZERO);
				if (!level().isClientSide() && retractAfterDash) {
					stand.retract();
				}
			}
		}

		@Override
		public void onActionCleared(EntityActionInstance newAction) {
			if (getPerformer() instanceof StandEntity stand) {
				stand.setDeltaMovement(Vec3.ZERO);
			}
		}

		@Override
		public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
			return getPhase() == ActionPhase.RECOVERY && cancellingAbility == ability;
		}

		private void hitDashTargets(Level level, StandEntity stand) {
			double reach = stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
			// 1.16's ray took what StandEntity.canTarget took; the strike's own harm check spares what the Stand may not harm
			Predicate<Entity> canTarget = StandPrecisionRay.canTarget(stand);
			List<ActionTarget> targets = StandSkillWalls.wallsHideEntities(level, this)
					? dashTargetsBeforeWalls(level, stand, reach, canTarget)
					: HitResultUtil.clipMultipleTargets(stand, reach, canTarget, 0.25D, stand.getPrecision());
			if (targets.isEmpty()) {
				// 1.16 punched the miss of a ray that met nothing in the air
				hitReaimedDashTarget(level, stand);
				return;
			}
			for (ActionTarget target : targets) {
				hitDashTarget(level, stand, target);
			}
		}

		/**
		 * 1.16 StandEntity.punch with no target: aimWithThisOrUser aimed again (the user's precision ray, else the Stand's
		 * own, under manual control only the Stand's), turned the Stand toward what that met and struck it if it lay in
		 * the Stand's reach.
		 */
		private void hitReaimedDashTarget(Level level, StandEntity stand) {
			ActionTarget aimed = StandEntityPunchAbility.strikeAim(stand, ActionTarget.EMPTY, CrosshairRule.NONE);
			stand.turnTowardsStrike(aimed);
			ActionTarget inReach = StandEntityPunchAbility.validatePunchTarget(stand, aimed);
			if (!inReach.isEmpty(level) && !wallHidesDashTarget(level, stand, inReach)) {
				strikeDashTarget(level, stand, inReach);
			}
			else {
				// 1.16 SilverChariotEntity.attackTarget ran for a punch that struck nothing too
				StandEntityPunchAbility.deflectSilverChariotProjectiles(stand, ActionTarget.EMPTY);
			}
		}

		private boolean wallHidesDashTarget(Level level, StandEntity stand, ActionTarget target) {
			target = target.resolveEntityId(level);
			return target.getType() == ActionTarget.TargetType.ENTITY && target.getEntity() != null
					&& StandSkillWalls.wallsHideEntities(level, this)
					&& StandEntityHeavyPunchAbility.wallHidesFromStand(stand, target.getEntity());
		}

		/**
		 * 1.16's dash ray (entities first, the block it meets when there are none) with the original story's walls: the
		 * rapier's path ends at the first block it meets, and an entity a wall hides from the Stand is not hit.
		 */
		private static List<ActionTarget> dashTargetsBeforeWalls(Level level, StandEntity stand, double reach,
				Predicate<Entity> canTarget) {
			Vec3 start = stand.getEyePosition();
			Vec3 look = stand.getLookAngle();
			if (look.lengthSqr() < 1.0E-7) {
				return List.of();
			}
			Vec3 end = start.add(look.normalize().scale(reach));
			BlockHitResult wall = HitResultUtil.clipBlocks(new ClipContext(start, end,
					ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, stand), level);
			double pathSqr = wall.getType() == HitResult.Type.MISS ? Double.MAX_VALUE : start.distanceToSqr(wall.getLocation());
			List<ActionTarget> entities = HitResultUtil.clipMultipleEntities(stand, reach, canTarget, 0.25D, stand.getPrecision())
					.stream()
					.filter(target -> target.getClipPos().map(clip -> start.distanceToSqr(clip) <= pathSqr).orElse(true)
							&& !StandEntityHeavyPunchAbility.wallHidesFromStand(stand, target.getEntity()))
					.toList();
			if (!entities.isEmpty()) {
				return entities;
			}
			ActionTarget block = ActionTarget.fromVanilla(HitResultUtil.clipBlocks(new ClipContext(start, end,
					ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, stand), level));
			return block.isEmpty(level) ? List.of() : List.of(block);
		}

		private void hitDashTarget(Level level, StandEntity stand, ActionTarget target) {
			target = target.resolveEntityId(level);
			if (target.isEmpty(level)) {
				return;
			}
			// 1.16 SilverChariotDashAttack struck each target through StandEntity.punch
			stand.turnTowardsStrike(target);
			strikeDashTarget(level, stand, target);
		}

		private void strikeDashTarget(Level level, StandEntity stand, ActionTarget target) {
			// 1.16 SilverChariotEntity.attackTarget and attackOrDeflect, on every punch of the dash: at Resolve 4 the
			// projectiles flying at the Stand in front of it, and a projectile it strikes, are turned aside
			boolean deflected = StandEntityPunchAbility.deflectSilverChariotProjectiles(stand, target);
			switch (target.getType()) {
				case ENTITY -> {
					Entity entity = target.getMainEntity();
					if (entity == null) {
						return;
					}
					if (deflected) {
						break;
					}
					if (!(entity instanceof LivingEntity candidate)) {
						strikeNonLiving(stand, entity);
						break;
					}
					// 1.16's Stand invulnerability gave a hurt target no damage on the later forward ticks; one that was
					// not hurt (a raised shield) was struck again
					if (hitEntities.contains(candidate.getId())) {
						return;
					}
					float damage = StandStatFormulas.getHeavyAttackDamage(stand.getAttackDamage());
					DamageSource dmgSource = makePunchDamageSource();
					if (dmgSource instanceof DamageSourceModified modified) {
						modified.jojo_ripples$setStandInvulTicks(10);
					}
					float sideYaw = sideKnockbackYaw(stand, candidate);
					// read before the hit, which starts the Stand invulnerability
					boolean admitted = damage > 0 && stand.canAttackEntity(candidate) && !StandEntity.isStandAttackInvulnerable(candidate);
					boolean hurt = standEntityAttack(stand, candidate, dmgSource, damage);
					// 1.16 StandEntityPunch.doAttack: one push for a hurt target, then knockbackTarget's for every admitted one
					if (admitted) {
						if (hurt) {
							knockbackTarget(stand, candidate, sideYaw, true);
						}
						knockbackTarget(stand, candidate, sideYaw, hurt);
					}
					if (hurt) {
						hitEntities.add(candidate.getId());
						candidate.hurtMarked = true;
						afterHeavyHit(stand, candidate);
					}
				}
				case BLOCK -> StandEntityPunchAbility.StandEntityPunch.hitBlockTarget(target, level, stand, !isUserCreative());
				default -> {
				}
			}
			punchedTarget = target;
		}

		/*
		 * 1.16 StandEntityPunch.doHit on an entity that is not living (a boat, a minecart, an item frame, a painting, an end
		 * crystal), as the plain punch strikes one: StandEntity.attackEntity's harm check, the heavy damage, then the dash's
		 * knockback set as the entity's motion whether the hit hurt it or not, and afterAttack on a hurt one. No impact
		 * sound: the dash's punchEntity set it to null.
		 */
		private void strikeNonLiving(StandEntity stand, Entity entity) {
			float damage = StandStatFormulas.getHeavyAttackDamage(stand.getAttackDamage());
			if (damage <= 0 || !stand.canAttackEntity(entity)) {
				return;
			}
			float sideYaw = sideKnockbackYaw(stand, entity);
			boolean hurt = standEntityAttack(stand, entity, makePunchDamageSource(), damage);
			if (stand.getAttackSpeed() < 24) {
				entity.setDeltaMovement(new Vec3(0.0D, 0.0D, ARMORED_DASH_EXTRA_KNOCKBACK * DASH_KNOCKBACK_TO_STRENGTH)
						.yRot(-sideYaw * MathUtil.DEG_TO_RAD));
			}
			else {
				entity.setDeltaMovement(0.0D, -FAST_DASH_EXTRA_KNOCKBACK * DASH_KNOCKBACK_TO_STRENGTH
						* Mth.sin(FAST_DASH_KNOCKBACK_XROT * MathUtil.DEG_TO_RAD), 0.0D);
			}
			entity.hurtMarked = true;
			if (hurt) {
				afterHeavyHit(stand, entity);
			}
		}

		private static Vec3 horizontalLook(LivingEntity entity, LivingEntity fallback, double yScale) {
			Vec3 look = entity.getLookAngle().multiply(1.0, yScale, 1.0);
			if (look.lengthSqr() < 1.0E-6 && fallback != null) {
				look = fallback.getLookAngle().multiply(1.0, yScale, 1.0);
			}
			if (look.lengthSqr() < 1.0E-6) {
				return new Vec3(0.0, 0.0, 1.0);
			}
			return look.normalize();
		}

		private static Vec3 dashLook(StandEntity stand, LivingEntity fallback) {
			Vec3 look = stand.getLookAngle();
			if (look.lengthSqr() < 1.0E-6 && fallback != null) {
				look = fallback.getLookAngle();
			}
			if (look.lengthSqr() < 1.0E-6) {
				return new Vec3(0.0, 0.0, 1.0);
			}
			return look.normalize();
		}

		/*
		 * 1.16 SilverChariotDashAttack.punchEntity below attack speed 24, in its float arithmetic: the side comes from the
		 * Stand's yaw, which the strike has just turned toward the target, so a target within that turn goes to the
		 * Stand's left and one beyond it keeps its own side.
		 */
		private static float sideKnockbackYaw(StandEntity stand, Entity target) {
			if (stand.getAttackSpeed() >= 24) {
				return 0.0F;
			}
			float yawToTarget = MathUtil.yRotDegFromVec(target.position().subtract(stand.position()));
			boolean left = Mth.wrapDegrees(stand.getYRot() - yawToTarget) < 0.0F;
			return yawToTarget + (60.0F + stand.getRandom().nextFloat() * 30.0F) * (left ? 1 : -1);
		}

		private static void knockbackTarget(StandEntity stand, LivingEntity target, float sideYaw, boolean hurt) {
			if (stand.getAttackSpeed() < 24) {
				target.knockback(ARMORED_DASH_EXTRA_KNOCKBACK * DASH_KNOCKBACK_TO_STRENGTH,
						Mth.sin(sideYaw * MathUtil.DEG_TO_RAD), -Mth.cos(sideYaw * MathUtil.DEG_TO_RAD));
			}
			else {
				float knockbackStrength = FAST_DASH_EXTRA_KNOCKBACK * DASH_KNOCKBACK_TO_STRENGTH;
				float vertical = -knockbackStrength * Mth.sin(FAST_DASH_KNOCKBACK_XROT * MathUtil.DEG_TO_RAD);
				applyVerticalKnockback(target, vertical, hurt);
			}
		}

		// 1.16 DamageUtil.upwardsKnockback never marked the target: a player's own client sees the lift of a hit that
		// landed (its hurt marked it), not of one a shield blocked
		private static void applyVerticalKnockback(LivingEntity target, float strength, boolean hurt) {
			if (target.level() instanceof ServerLevel serverLevel && serverLevel.hasData(ModDataAttachmentTypes.TIME_STOP.get())) {
				TimeStopState state = serverLevel.getData(ModDataAttachmentTypes.TIME_STOP.get());
				if (state.shouldFreeze(target)) {
					state.queueOnTimeResume(target, () -> applyVerticalKnockback(target, strength, hurt));
					return;
				}
			}
			double kbRes = target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
			float finalStrength = strength * (1.0F - (float) kbRes);
			if (finalStrength != 0) {
				target.setDeltaMovement(target.getDeltaMovement().add(0.0D, finalStrength, 0.0D));
				if (hurt) {
					target.hurtMarked = true;
				}
			}
			if (target instanceof StandEntity targetStand) {
				LivingEntity user = targetStand.getUser();
				if (user != null && !user.is(target)) {
					applyVerticalKnockback(user, finalStrength, hurt);
				}
			}
		}

	}

	@Override
	public float getStaminaCost(Power<?> context) {
		return STAMINA_COST;
	}
}
