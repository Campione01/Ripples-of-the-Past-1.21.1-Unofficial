package com.github.standobyte.jojoimpl.stands.silverchariot;

import java.util.List;

import com.github.standobyte.jojo.client.ClientGlobals;
import com.github.standobyte.jojo.client.sound.ClientsideSoundsHelper;
import com.github.standobyte.jojo.customobjects.DamageSourceModified;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance.StandPart;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntityAbility;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandStatFormulas;
import com.github.standobyte.jojo.subsystems.target.AimingEntity;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojoimpl.stands._entitybase.StandAbilityStamina;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class SilverChariotSweepingAttackAbility extends StandEntityAbility {

	private static final int STAMINA_COST = 50;
	private static final float SWEEP_HALF_DAMAGE_FACTOR = 0.5F;
	private static final int PERFORM_TICKS = 3;

	public SilverChariotSweepingAttackAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, SweepingStrike::new);
		partsRequired(StandPart.ARMS);
		setDefaultPhaseLength(ActionPhase.PERFORM, PERFORM_TICKS);
	}

	@Override
	public void initActionFromConfig(EntityActionInstance action, Level level, 
			LivingEntity powerUser, LivingEntity performer) {
		super.initActionFromConfig(action, level, powerUser, performer);
		if (!level.isClientSide() && performer instanceof StandEntity stand) {
			action.phasesLength.put(ActionPhase.WINDUP, Math.max(StandStatFormulas.getHeavyAttackWindup(
					stand.getAttackSpeed(), stand.getFinisherMeter()) - PERFORM_TICKS / 2, 1));
			action.phasesLength.put(ActionPhase.RECOVERY, StandStatFormulas.getHeavyAttackRecovery(stand.getAttackSpeed(), stand.getFinisherMeter()));
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
		return StandAbilityStamina.check(context, STAMINA_COST);
	}

	private static boolean lacksRapier(Power<?> context) {
		StandPower standPower = PowerClass.STAND.cast(context);
		if (standPower == null) {
			return false;
		}
		LivingEntity user = standPower.getUser();
		if (user == null) {
			return false;
		}
		SilverChariotState state = SilverChariotState.get(user);
		return state != null && !state.hasRapier();
	}

	public static class SweepingStrike extends EntityActionInstance {
		private boolean playedSwingSound;

		public SweepingStrike(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			playedSwingSound = false;
			aimAs = AimingEntity.STAND;
			if (performer instanceof StandEntity stand) {
				double minOffset = Math.min(0.5, stand.getEffectiveRange());
				double maxOffset = Math.min(2, stand.getMaxRange());
				ActionTarget target = captureActionTargetFromAim(stand);
				keepStandAimedAtTarget(target);
				setStandFrontOffsetFromTarget(stand, target, minOffset, maxOffset);
			}
			else {
				keepStandAimedAtTarget();
			}
		}

		@Override
		public void actionTick() {
			if (playedSwingSound || getPhase() != ActionPhase.WINDUP || getPhaseTicksLeft() > 1) {
				return;
			}
			Level level = level();
			if (!level.isClientSide() || !(getPerformer() instanceof StandEntity stand) || !ClientGlobals.canHearStand(stand)) {
				return;
			}
			LivingEntity user = getPowerUser();
			Vec3 lookXZ = stand.getLookAngle().multiply(1.0, 0.0, 1.0);
			if (lookXZ.lengthSqr() < 1.0E-6 && user != null) {
				lookXZ = user.getLookAngle().multiply(1.0, 0.0, 1.0);
			}
			if (lookXZ.lengthSqr() < 1.0E-6) {
				lookXZ = new Vec3(0.0, 0.0, 1.0);
			}
			lookXZ = lookXZ.normalize();
			level.playLocalSound(stand.getX(), stand.getEyeY(), stand.getZ(),
					ClientsideSoundsHelper.withStandSkin(ModSoundEvents.SILVER_CHARIOT_SWEEP_HEAVY.get(), stand),
					stand.getSoundSource(), 1.0F, 1.0F, false);
			level.addParticle(ParticleTypes.SWEEP_ATTACK,
					stand.getX() + lookXZ.x,
					stand.getY(0.5),
					stand.getZ() + lookXZ.z,
					lookXZ.x, 0.0, lookXZ.z);
			playedSwingSound = true;
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
			SilverChariotState state = SilverChariotState.get(user);
			if (state != null && !state.hasRapier()) {
				return;
			}
			if (!(getPerformer() instanceof StandEntity stand)) {
				return;
			}
			StandPower standPower = StandPower.get(user);
			if (!StandAbilityStamina.consumeOrMessage(ability, standPower, user, STAMINA_COST)) {
				return;
			}

			Vec3 lookVec = stand.getLookAngle();
			if (lookVec.lengthSqr() < 1.0E-6) {
				lookVec = user.getLookAngle();
			}
			if (lookVec.lengthSqr() < 1.0E-6) {
				lookVec = new Vec3(0.0, 0.0, 1.0);
			}
			lookVec = lookVec.normalize();

			Vec3 lookXZ = lookVec.multiply(1.0, 0.0, 1.0);
			if (lookXZ.lengthSqr() < 1.0E-6) {
				lookXZ = user.getLookAngle().multiply(1.0, 0.0, 1.0);
			}
			if (lookXZ.lengthSqr() < 1.0E-6) {
				lookXZ = new Vec3(0.0, 0.0, 1.0);
			}
			lookXZ = lookXZ.normalize();
			double reach = stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
			AABB area = stand.getBoundingBox().inflate(reach, 0.0, reach);
			List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, area,
					e -> e != user && e != stand && !e.isSpectator() && e.isAlive() && e.isPickable() && stand.canAttack(e));
			for (LivingEntity target : targets) {
				Vec3 toTarget = target.position().subtract(stand.position());
				if (toTarget.lengthSqr() < 1e-6) continue;
				Vec3 targetVec = toTarget.normalize();
				double cos = lookVec.dot(targetVec);
				if (cos <= -0.5) continue;
				float dmgAmount = StandStatFormulas.getHeavyAttackDamage(stand.getAttackDamage());
				if (cos < 0.5) {
					dmgAmount *= SWEEP_HALF_DAMAGE_FACTOR;
				}
				DamageSource dmgSource = makePunchDamageSource();
				((DamageSourceModified) dmgSource).jojo_ripples$modifyKnockback(1F, 1);
				((DamageSourceModified) dmgSource).jojo_ripples$setStandInvulTicks(10);
				standEntityAttack(stand, target, dmgSource, dmgAmount);
			}

			level.addParticle(ParticleTypes.SWEEP_ATTACK,
					stand.getX() + lookXZ.x,
					stand.getY(0.5),
					stand.getZ() + lookXZ.z,
					lookXZ.x, 0.0, lookXZ.z);

			if (stand.isSilverChariotRapierOnFire()) {
				SCFlameSwingEntity flame = new SCFlameSwingEntity(stand, level);
				flame.shootFromRotation(stand, 1.5F, 0.0F);
				addProjectileWithStandStats(flame);
				stand.removeSilverChariotRapierFire();
			}

		}
	}
}
