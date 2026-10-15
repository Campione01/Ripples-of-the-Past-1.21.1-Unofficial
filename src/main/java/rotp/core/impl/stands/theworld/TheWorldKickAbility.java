package rotp.core.impl.stands.theworld;

import rotp.core.api.stand.StandSkillWalls;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class TheWorldKickAbility extends StandEntityHeavyPunchAbility {
	private static final float KICK_SWEEP_DAMAGE_FACTOR = 0.5F;

	public TheWorldKickAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId);
		this.createActionObj = TheWorldKick::new;
		partsRequired(StandPart.LEGS);
	}

	public static class TheWorldKick extends StandEntityHeavyPunchAbility.StandEntityHeavyPunch {

		public TheWorldKick(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			TheWorldHeavyPunchAbility.sayDieShout(this);
			super.onActionSet(prevAction);
		}

		@Override
		protected Holder<SoundEvent> getHeavyPunchImpactSound(ActionTarget target) {
			return ModSoundEvents.THE_WORLD_KICK_HEAVY;
		}

		@Override
		protected void addKnockback(DamageSource dmgSource) {
			super.addKnockback(dmgSource);
			((DamageSourceModified) dmgSource).jojo_ripples$knockbackYRot(60F);
			((DamageSourceModified) dmgSource).jojo_ripples$setBreakStandGuard(true);
		}

		@Override
		protected void hitEntity(ActionTarget target, Level level, StandEntity stand,
				DamageSource dmgSource, float dmgAmount, float explRadius) {
			Entity targetEntity = target.getMainEntity();
			if (targetEntity == null) {
				return;
			}
			if (!(targetEntity instanceof LivingEntity) && !usesDetachedPunchKnockback()) {
				addKnockback(dmgSource);
			}
			super.hitEntity(target, level, stand, dmgSource, dmgAmount, explRadius);
			// 1.16 StandEntityPunch.doHit sweeps after every primary attempt, including a refused hurt.
			sweepTargetsAroundKick(targetEntity, level, stand, dmgAmount * KICK_SWEEP_DAMAGE_FACTOR);
		}

		@Override
		protected void afterHeavyPunchHit(StandEntity stand, LivingEntity targetLiving, DamageSource dmgSource, float dmgAmount, boolean hurt) {
			if (!usesDetachedPunchKnockback()) {
				disableShieldOnHit(targetLiving, hurt, 1.0F);
			}
		}

		@Override
		protected void afterDetachedPunchHurt(StandEntity stand, LivingEntity targetLiving, DamageSource dmgSource, float dmgAmount, boolean hurt) {
			disableShieldOnHit(targetLiving, hurt, 1.0F);
		}

		private void sweepTargetsAroundKick(Entity primaryTarget, Level level, StandEntity stand, float sweepDamage) {
			LivingEntity user = stand.getUser();
			boolean wallsHide = StandSkillWalls.wallsHideEntities(level, this);
			for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
					primaryTarget.getBoundingBox().inflate(0.5, 0, 0.5),
					entity -> entity != primaryTarget
							&& entity != stand
							&& entity != user
							&& entity.isAlive()
							&& !entity.isSpectator()
							&& entity.isPickable()
							&& getSweepTargetDistance(stand, entity.getBoundingBox()) < stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE)
							&& stand.canAttackEntity(entity)
							&& !(wallsHide && StandEntityHeavyPunchAbility.wallHidesFromStand(stand, entity)))) {
				// 1.16 StandEntityPunch sweep targets went through doAttack, no shield wear
				DamageSource sweepSource = makePunchDamageSource();
				((DamageSourceModified) sweepSource).jojo_ripples$setStandInvulTicks(10);
				boolean hurt = attemptHeavyPunch(stand, target, primaryTarget, sweepSource, sweepDamage, false);
				if (!usesDetachedPunchKnockback()) {
					disableShieldOnHit(target, hurt, 1.0F);
				}
			}
		}

		// 1.16 JojoModUtil.getDistance: eye-to-box clip, less half the attacker's width; not primary nearest-box reach.
		private static double getSweepTargetDistance(StandEntity stand, AABB targetBox) {
			Vec3 start = stand.getEyePosition(1.0F);
			if (targetBox.contains(start)) {
				return 0;
			}
			Vec3 end = new Vec3(Mth.lerp(0.5D, targetBox.minX, targetBox.maxX),
					Mth.lerp(stand.getBbHeight() == 0 ? 0 : stand.getEyeHeight() / stand.getBbHeight(), targetBox.minY, targetBox.maxY),
					Mth.lerp(0.5D, targetBox.minZ, targetBox.maxZ));
			return targetBox.clip(start, end).map(clip -> start.distanceTo(clip) - stand.getBbWidth() / 2).orElse(-1D);
		}
	}
}
