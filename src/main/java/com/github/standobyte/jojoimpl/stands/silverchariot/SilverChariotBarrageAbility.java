package com.github.standobyte.jojoimpl.stands.silverchariot;

import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandStatFormulas;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojoimpl.stands._entitybase.StandEntityBarrageAbility;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.level.Level;

public class SilverChariotBarrageAbility extends StandEntityBarrageAbility {

	public SilverChariotBarrageAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, SilverChariotBarrage::new);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		if (!check.isPositive()) {
			return check;
		}
		StandPower standPower = PowerClass.STAND.cast(context);
		if (standPower == null) {
			return ConditionCheck.NEGATIVE;
		}
		LivingEntity user = standPower.getUser();
		if (user != null) {
			SilverChariotState state = SilverChariotState.get(user);
			if (state != null && !state.hasRapier()) {
				return ConditionCheck.createNegative("chariot_rapier");
			}
		}
		return ConditionCheck.POSITIVE;
	}

	@Override
	public void initActionFromConfig(EntityActionInstance action, Level level,
			LivingEntity powerUser, LivingEntity performer) {
		super.initActionFromConfig(action, level, powerUser, performer);
		if (!level.isClientSide()) {
			SilverChariotState state = SilverChariotState.get(powerUser);
			if (state != null && !state.hasArmor()) {
				action.phasesLength.put(ActionPhase.PERFORM, Integer.MAX_VALUE);
			}
		}
	}

	public static class SilverChariotBarrage extends StandEntityBarrageAbility.StandEntityBarrage {

		public SilverChariotBarrage(EntityActionType ability) {
			super(ability);
		}

		@Override
		protected SoundEvent getBarrageSwingSound() {
			return ModSoundEvents.SILVER_CHARIOT_BARRAGE_SWIPE.get();
		}

		@Override
		protected float getBarrageSwingVolume(StandEntity stand) {
			return 0.25F;
		}

		@Override
		protected float getBarrageSwingPitch(StandEntity stand) {
			return 0.9F + stand.getRandom().nextFloat() * 0.2F;
		}

		@Override
		protected Holder<SoundEvent> getBarrageHitSound() {
			return null;
		}

		@Override
		protected void hitEntity(ActionTarget target, Level level, StandEntity stand) {
			Entity targetEntity = target.getMainEntity();
			if (targetEntity != null) {
				DamageSource dmgSource = makeBarrageDamageSource();
				float dmgAmount = StandStatFormulas.getBarrageHitDamage(stand.getAttackDamage(), stand.getPrecision()) * hitsThisTick;
				if (targetEntity instanceof Skeleton) {
					dmgAmount *= 0.75F;
				}
				standEntityAttack(stand, targetEntity, dmgSource, dmgAmount);

				stand.addFinisherMeter(0.005f * hitsThisTick);
			}
		}

		@Override
		protected void onBarrageSet(Level level, StandEntity stand) {
			if (level.isClientSide()) {
				return;
			}
			LivingEntity user = getPowerUser();
			if (user == null) {
				return;
			}
			SilverChariotState state = SilverChariotState.get(user);
			if (state != null) {
				if (!state.hasRapier()) {
					return;
				}
				if (!state.hasArmor() && state.ticksAfterArmorRemoval() < 40) {
					JojoModUtil.sayVoiceLine(user, ModSoundEvents.POLNAREFF_FENCING);
					return;
				}
			}
			JojoModUtil.sayVoiceLine(user, ModSoundEvents.POLNAREFF_HORA_HORA_HORA);
		}
	}
}
