package com.github.standobyte.jojoimpl.stands.goldexperience;

import com.github.standobyte.jojo.customobjects.StatusEffectApplicable;
import com.github.standobyte.jojo.customobjects.StatusEffectModified;
import com.github.standobyte.jojo.util.reflection.CommonReflection;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;

public class GELifeshotEffect extends StatusEffectModified implements StatusEffectApplicable {

	public GELifeshotEffect(int color) {
		super(MobEffectCategory.HARMFUL, color);
		setUncurable();
		disableCreeperLinger = true;
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
		return true;
	}

	@Override
	public boolean applyEffectTick(LivingEntity entity, int amplifier) {
		if (entity instanceof Creeper creeper) {
			CommonReflection.setCreeperSwell(creeper, -1);
		}
		return true;
	}

	@Override
	public void onAdded(LivingEntity entity, MobEffectInstance instance, net.minecraft.world.entity.Entity source) {
		if (entity instanceof Mob mob) {
			mob.setNoAi(true);
		}
	}

	@Override
	public void onRemoved(LivingEntity entity, MobEffectInstance instance) {
		if (entity instanceof Mob mob) {
			mob.setNoAi(false);
		}
	}

	@Override
	public boolean isApplicable(LivingEntity entity) {
		return !(entity instanceof Player player && player.getAbilities().instabuild)
				&& !(entity instanceof Mob mob && mob.isNoAi());
	}
}
