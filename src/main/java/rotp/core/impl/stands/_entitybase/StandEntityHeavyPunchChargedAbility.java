package rotp.core.impl.stands._entitybase;

import rotp.core.client.ClientGlobals;
import rotp.core.client.sound.ClientsideSoundsHelper;
import rotp.core.client.sound.sounds.EntityLingeringSoundInstance;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.customobjects.explosion.CustomExplosion;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.subsystems.target.AimingEntity;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.HeavyPunchExplosion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class StandEntityHeavyPunchChargedAbility extends StandEntityAbility {

	public StandEntityHeavyPunchChargedAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, StandEntityChargedHeavy::new);
		usageGroup = AbilityUsageGroup.COMBAT;
		setDefaultPhaseLength(ActionPhase.BUTTON_CHARGE, 21);
		setButtonHoldPhase(ActionPhase.WINDUP);
		setDefaultPhaseLength(ActionPhase.PERFORM, 7);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 12);
	}

	@Override
	public boolean isAbilityAvailable(Power<?> context) {
		return super.isAbilityAvailable(context) && StandUtil.getStandGrabTarget(context) == null;
	}

	@Override
	public void initActionFromConfig(EntityActionInstance action, Level level,
			LivingEntity powerUser, LivingEntity performer) {
		super.initActionFromConfig(action, level, powerUser, performer);
		if (!level.isClientSide() && performer instanceof StandEntity stand) {
			action.phasesLength.put(ActionPhase.BUTTON_CHARGE, StandStatFormulas.getChargedHeavyButtonWindup(
					stand.getAttackSpeed(), stand.getFinisherMeter()));
			action.phasesLength.put(ActionPhase.PERFORM, StandStatFormulas.getChargedHeavyPunchWindup(
					stand.getAttackSpeed(), stand.getFinisherMeter()));
		}
	}

	public static class StandEntityChargedHeavy extends EntityActionInstance {
		protected float buttonChargeRatio;
		// the key went up during BUTTON_CHARGE (server state)
		protected boolean releasedInCharge;

		public StandEntityChargedHeavy(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onButtonStopHold() {
			switch (getPhase()) {
			case BUTTON_CHARGE -> {
				// fires when the charge ends; checkNextPhase rechecks then
				phasesLength.put(ActionPhase.WINDUP, 0F);
				releasedInCharge = true;
				syncPhaseChanges();
			}
			case WINDUP -> {
				// 1.16 stopHeldAction(true): a release that fails the recheck fires nothing
				if (releaseCheckPasses()) {
					setPhaseStart(ActionPhase.PERFORM);
				}
				else {
					forceStop();
				}
				syncPhaseChanges();
			}
			default -> {}
			}
		}

		// Released during the charge: the key is up, so the per-tick held recheck no longer runs.
		@Override
		protected void checkNextPhase() {
			if (releasedInCharge && phase == ActionPhase.BUTTON_CHARGE && getPhaseTick() >= curPhaseLength
					&& !shouldHoldPhaseAtEnd() && !releaseCheckPasses()) {
				forceStop();
				syncPhaseChanges();
				return;
			}
			super.checkNextPhase();
		}

		// server side; the client follows the synced phase
		private boolean releaseCheckPasses() {
			return !(ability instanceof EntityActionAbility entityAbility) || entityAbility.canFireReleasedHold(this);
		}

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			if (getPhase() == ActionPhase.BUTTON_CHARGE && newPhase != ActionPhase.BUTTON_CHARGE) {
				this.buttonChargeRatio = getPhaseRatio();
			}
		}

		@Override
		public void actionPerformStart() {
			if (performer instanceof StandEntity stand) {
				Level level = performer.level();
				// lock onto the target at perform time (the charge lets the user re-aim), like the heavy punch
				ActionTarget aimed = capturePerformTarget(stand);
				if (!aimed.isEmpty(level)) {
					setStandFrontOffsetFromTarget(stand, aimed,
							Math.min(0.5, stand.getEffectiveRange()), Math.min(2, stand.getMaxRange()));
					keepStandAimedAtTarget(aimed);
				}
				else {
					setStandOffset(0, Math.max(stand.offsetFromUser.getRelativeOffset().z, 0) + 2,
							StandOffsetFromUser.Rotations.HEAD_XY,
							false);
				}

				if (level.isClientSide() && ClientGlobals.canHearStand(stand)) {
					ClientsideSoundsHelper.playNonVanillaClassSound(new EntityLingeringSoundInstance(ClientsideSoundsHelper.withStandSkin(
							ModSoundEvents.STAND_PUNCH_HEAVY_SWING.get(), stand),
							stand.getSoundSource(), 1, 1, stand, stand.level()));

					ClientsideSoundsHelper.playNonVanillaClassSound(new EntityLingeringSoundInstance(ClientsideSoundsHelper.withStandSkin(
							ModSoundEvents.STAND_PUNCH_HEAVY_CRY.get(), stand),
							stand.getSoundSource(), 1, 1, stand, stand.level()));
				}
			}
			aimAs = AimingEntity.STAND;
		}

		@Override
		public void actionPerformEnd() {
			Level level = level();
			if (performer instanceof StandEntity stand) {
				ActionTarget target = getPerformTarget(stand);
				if (!level.isClientSide()) {
					StandPower standPower = StandPower.get(getPowerUser());

					if (StandEntityPunchAbility.playHitSound(target, level)) {
						StandUtil.broadcastSound((ServerLevel) level, target.getCenterPos(),
								ModSoundEvents.STAND_PUNCH_HEAVY_CHARGED, true, standPower,
								stand.getSoundSource(), 1, 1);
					}
					DamageSource dmgSource = makePunchDamageSource();
					float dmgAmount = StandStatFormulas.getChargedHeavyAttackDamage(stand.getAttackDamage());
					float explRadius = Math.min((float) stand.getAttackDamage() * 0.25F, 10);

					switch (target.getType()) {
					case ENTITY -> hitEntity(target, level, stand, dmgSource, dmgAmount);
					case BLOCK -> hitBlock(target, level, stand, dmgSource, dmgAmount, explRadius);
					default -> {}
					}

					punchedTarget = target;
					standPower.consumeStamina(100);
					stand.consumeFinisherMeter(1.0001F);
				}
				if (target.getType() == TargetType.ENTITY) {
					standRotationTarget = target;
				}
				else {
					aimAs = AimingEntity.CAMERA_ENTITY;
				}
			}
		}

		// the synced crosshair target in the Stand's reach, else what the user looks at
		protected ActionTarget capturePerformTarget(StandEntity stand) {
			Level level = stand.level();
			ActionTarget aimed = StandEntityPunchAbility.validatePunchTarget(stand, captureActionTargetFromAim(stand));
			if (aimed.isEmpty(level) && !stand.isManuallyControlled()) {
				aimed = StandEntityBarrageAbility.clipDirectionalBarrageTarget(stand, this, 1.0F);
			}
			return setActionTargetSnapshot(aimed);
		}

		// the perform-start target if still valid, else the user's view (a target between user and Stand),
		// else the Stand's own look for the lunge reach
		protected ActionTarget getPerformTarget(StandEntity stand) {
			Level level = stand.level();
			ActionTarget target = StandEntityPunchAbility.validatePunchTarget(stand, getActionTargetSnapshot(level));
			if (target.isEmpty(level) && !stand.isManuallyControlled()) {
				target = StandEntityBarrageAbility.clipDirectionalBarrageTarget(stand, this, 1.0F);
			}
			if (target.isEmpty(level)) {
				target = HitResultUtil.clipEntityLook(stand, entity -> StandEntityPunchAbility.canStandHit(stand, entity), 0);
			}
			return target;
		}

		protected void hitEntity(ActionTarget target, Level level, StandEntity stand,
				DamageSource dmgSource, float dmgAmount) {
			Entity targetEntity = target.getMainEntity();
			if (targetEntity instanceof LivingEntity targetLiving) {
				addKnockback(dmgSource);
				standEntityAttack(stand, targetLiving, dmgSource, dmgAmount);
			}
		}

		protected void addKnockback(DamageSource dmgSource) {
			DamageSourceModified knockback = (DamageSourceModified) dmgSource;
			knockback.jojo_ripples$modifyKnockback(2.5F, 1);
		}

		protected void hitBlock(ActionTarget target, Level level, StandEntity stand,
				DamageSource dmgSource, float dmgAmount, float explRadius) {
			BlockPos blockPos = target.getBlockPos();
			Direction face = target.getFace();
			Vec3 pos = Vec3.atCenterOf(blockPos).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.6));
			DamageSource aoeDmgSource = dmgSource;
			float aoeDmg = dmgAmount * 0.5F;
			HeavyPunchExplosion explosion = new HeavyPunchExplosion(level, stand,
					new ActionTarget(blockPos, face), stand.getLookAngle(),
					aoeDmgSource,
					pos.x, pos.y, pos.z,
					explRadius, false,
					JojoModUtil.breakingBlocksEnabled(level) ? Explosion.BlockInteraction.DESTROY : Explosion.BlockInteraction.KEEP)
					.aoeDamage(aoeDmg)
					.createBlockShards(stand.getAttackDamage(), stand.getPrecision());
			CustomExplosion.explode(explosion);
		}

		@Override
		public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
			return phase.ordinal() < ActionPhase.PERFORM.ordinal();
		}
	}
}
