package com.github.standobyte.jojoimpl.powers.vampirism.abilities;

import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.mechanics.JojoDefinitions;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojoimpl.powers.vampirism.entity.HungryZombieEntity;

import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public class VampirismDarkAuraAbility extends VampirismActionAbility {

	public VampirismDarkAuraAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, 3, 25.0F, DarkAuraInstance::new);
		setDefaultPhaseLength(ActionPhase.PERFORM, 1);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
		setIgnoresPerformerStun();
	}

	@Override
	public void setCooldownOnUse(Power<?> context) {
		setVampirismCooldown(context, 300, 300);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		if (!check.isPositive()) {
			return check;
		}
		LivingEntity user = context.getUser();
		if (user == null) {
			return ConditionCheck.NEGATIVE;
		}
		if (user.level().getDifficulty() == Difficulty.PEACEFUL) {
			return ConditionCheck.createNegative("peaceful");
		}
		return ConditionCheck.POSITIVE;
	}

	public static class DarkAuraInstance extends EntityActionInstance {
		public DarkAuraInstance(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void actionPerformStart() {
			Level level = level();
			if (level.isClientSide()) {
				return;
			}
			LivingEntity user = getPowerUser();
			if (user == null || !consumeBlood(user, 25.0F)) {
				return;
			}
			int difficulty = level.getDifficulty().getId();
			int range = 16 * difficulty - 8;
			int amplifier = Math.max(0, (int) Math.floor((difficulty - 1) * 1.5F));
			AABB area = user.getBoundingBox().inflate(range);
			for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
					entity -> entity != user && entity.isAlive())) {
				if (target instanceof StandEntity stand && stand.getUser() == user) {
					continue;
				}
				if (JojoDefinitions.isUndeadOrVampiric(target)) {
					continue;
				}
				boolean passive = target instanceof AgeableMob;
				int duration = passive ? 600 : 200;
				target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, amplifier, false, true));
				target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, amplifier, false, true));
				target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, duration, amplifier, false, true));
				if (passive) {
					target.addEffect(new MobEffectInstance(ModStatusEffects.STUN, duration, 0, false, true));
				}
			}
			if (level.getDifficulty() == Difficulty.HARD) {
				for (HungryZombieEntity zombie : level.getEntitiesOfClass(HungryZombieEntity.class, area,
						zombie -> zombie.isAlive() && zombie.isEntityOwner(user))) {
					zombie.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 300, 1, false, true));
					zombie.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 300, 0, false, true));
					zombie.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 300, 0, false, true));
				}
			}
			for (InteractionHand hand : InteractionHand.values()) {
				ItemStack item = user.getItemInHand(hand);
				if (item.is(Items.POPPY)) {
					user.setItemInHand(hand, item.transmuteCopy(Items.WITHER_ROSE));
				}
			}
			level.playSound(null, user, ModSoundEvents.VAMPIRE_EVIL_ATMOSPHERE.get(),
					user.getSoundSource(), (range + 16.0F) / 16.0F, 1.0F);
		}
	}
}
