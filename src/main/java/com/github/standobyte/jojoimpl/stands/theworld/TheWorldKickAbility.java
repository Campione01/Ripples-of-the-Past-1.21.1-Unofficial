package com.github.standobyte.jojoimpl.stands.theworld;

import com.github.standobyte.jojo.customobjects.DamageSourceModified;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.mechanics.KnockbackCollisionImpact;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance.StandPart;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandStatFormulas;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojoimpl.stands._entitybase.StandEntityBarrageAbility;
import com.github.standobyte.jojoimpl.stands._entitybase.StandEntityHeavyPunchAbility;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class TheWorldKickAbility extends StandEntityHeavyPunchAbility {
	private static final float KICK_SWEEP_DAMAGE_FACTOR = 0.5F;

	public TheWorldKickAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId);
		this.createActionObj = TheWorldKick::new;
		partsRequired(StandPart.LEGS);
	}

	@Override
	public void initActionFromConfig(EntityActionInstance action, Level level,
			LivingEntity powerUser, LivingEntity performer) {
		super.initActionFromConfig(action, level, powerUser, performer);
		if (!level.isClientSide() && powerUser != null) {
			JojoModUtil.sayVoiceLine(powerUser, ModSoundEvents.DIO_DIE);
		}
	}

	public static class TheWorldKick extends StandEntityHeavyPunchAbility.StandEntityHeavyPunch {

		public TheWorldKick(EntityActionType ability) {
			super(ability);
		}

		@Override
		protected Holder<SoundEvent> getHeavyPunchImpactSound(ActionTarget target) {
			return ModSoundEvents.THE_WORLD_KICK_HEAVY;
		}

		@Override
		protected void addKnockback(DamageSource dmgSource) {
			super.addKnockback(dmgSource);
			((DamageSourceModified) dmgSource).jojo_ripples$knockbackYRot(60F);
		}

		@Override
		protected void hitEntity(ActionTarget target, Level level, StandEntity stand,
				DamageSource dmgSource, float dmgAmount, float explRadius) {
			Entity targetEntity = target.getMainEntity();
			disableTargetStandBlocking(stand, targetEntity);
			if (targetEntity instanceof LivingEntity targetLiving) {
				addKnockback(dmgSource);
				boolean hurt = standEntityAttack(stand, targetLiving, dmgSource, dmgAmount);

				if (hurt) {
					sweepTargetsAroundKick(targetLiving, level, stand, dmgAmount * KICK_SWEEP_DAMAGE_FACTOR);

					Entity knockedBack = targetEntity;
					EntityActionInstance targetAction = LivingComponentAction.getCurEntityAction(targetLiving);
					if (targetAction instanceof StandEntityBarrageAbility.StandEntityBarrage) {
						targetAction.setPhaseStart(ActionPhase.RECOVERY);
						targetAction.syncPhaseChanges();
					}

					if (targetEntity instanceof StandEntity targetStand) {
						LivingEntity standUser = targetStand.getUser();
						if (standUser != null) {
							knockedBack = standUser;
						}
					}

					KnockbackCollisionImpact kbImpact = KnockbackCollisionImpact.getHandler(knockedBack);
					if (kbImpact != null) {
						kbImpact
						.onPunchSetKnockbackImpact(knockedBack.getDeltaMovement(), stand)
						.withImpactExplosion(Math.max(explRadius - 0.5f, 0), null, 0);
					}
				}
			}
		}

		private void disableTargetStandBlocking(StandEntity stand, Entity targetEntity) {
			if (targetEntity instanceof StandEntity targetStand && stand.getRandom().nextFloat() < 1.0F) {
				targetStand.breakStandBlocking(StandStatFormulas.getGuardBreakTicks(targetStand.getDurability()));
			}
		}

		private void sweepTargetsAroundKick(LivingEntity primaryTarget, Level level, StandEntity stand, float sweepDamage) {
			LivingEntity user = stand.getUser();
			for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
					primaryTarget.getBoundingBox().inflate(0.5, 0, 0.5),
					entity -> entity != primaryTarget
							&& entity != stand
							&& entity != user
							&& entity.isAlive()
							&& stand.canAttackEntity(entity))) {
				standEntityAttack(stand, target, makePunchDamageSource(), sweepDamage);
			}
		}
	}
}
