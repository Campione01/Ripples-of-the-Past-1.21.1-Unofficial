package rotp.core.impl.stands.theworld;

import rotp.core.customobjects.DamageSourceModified;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.subsystems.timestop.TimeStopState;
import rotp.core.util.functions.DamageUtil;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;

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
