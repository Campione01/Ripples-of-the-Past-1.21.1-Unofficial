package com.github.standobyte.jojoimpl.stands.theworld;

import com.github.standobyte.jojo.customobjects.DamageSourceModified;
import com.github.standobyte.jojo.init.ModDataAttachmentTypes;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.subsystems.target.ActionTarget.TargetType;
import com.github.standobyte.jojo.subsystems.timestop.TimeStopState;
import com.github.standobyte.jojo.util.functions.DamageUtil;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojoimpl.stands._entitybase.StandEntityHeavyPunchAbility;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class TheWorldHeavyPunchAbility extends StandEntityHeavyPunchAbility {

	public TheWorldHeavyPunchAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId);
		this.createActionObj = TheWorldHeavyPunch::new;
	}

	@Override
	public void initActionFromConfig(EntityActionInstance action, Level level,
			LivingEntity powerUser, LivingEntity performer) {
		super.initActionFromConfig(action, level, powerUser, performer);
		if (!level.isClientSide() && powerUser != null) {
			JojoModUtil.sayVoiceLine(powerUser, ModSoundEvents.DIO_DIE);
		}
	}

	public static class TheWorldHeavyPunch extends StandEntityHeavyPunchAbility.StandEntityHeavyPunch {

		public TheWorldHeavyPunch(EntityActionType ability) {
			super(ability);
		}

		@Override
		protected Holder<SoundEvent> getHeavyPunchImpactSound(ActionTarget target) {
			return target.getType() == TargetType.ENTITY ? ModSoundEvents.THE_WORLD_PUNCH_HEAVY_ENTITY : ModSoundEvents.THE_WORLD_PUNCH_HEAVY;
		}

		@Override
		protected void afterHeavyPunchHit(StandEntity stand, LivingEntity targetLiving, DamageSource dmgSource, float dmgAmount, boolean hurt) {
			if (hurt && targetLiving.level() instanceof ServerLevel serverLevel) {
				TimeStopState state = serverLevel.getData(ModDataAttachmentTypes.TIME_STOP.get());
				if (state.shouldFreeze(targetLiving)) {
					state.queueOnTimeResume(targetLiving, () -> targetLiving.playSound(ModSoundEvents.THE_WORLD_PUNCH_HEAVY_TS_IMPACT.get(), 1.0F, 1.0F));
				}
			}
		}

		@Override
		protected void addKnockback(DamageSource dmgSource) {
			DamageSourceModified knockback = (DamageSourceModified) dmgSource;
			knockback.jojo_ripples$modifyKnockback(6F, 1);
		}

		@Override
		protected void hitEntity(ActionTarget target, Level level, StandEntity stand,
				DamageSource dmgSource, float dmgAmount, float explRadius) {
			if (target.getMainEntity() instanceof LivingEntity targetLiving) {
				float armorPiercing = (float) stand.getAttackDamage() * 0.015F;
				dmgAmount = DamageUtil.addArmorPiercing(dmgAmount, armorPiercing, targetLiving, dmgSource);
			}
			super.hitEntity(target, level, stand, dmgSource, dmgAmount, explRadius);
		}
	}
}
