package rotp.core.mechanics;

import rotp.core.util.reflection.CommonReflection;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;

public class StunEffect extends ImmobilizeEffect {
	private static final String STUNNED_ON_DISCARD = "jojo_ripples:stunned_on_discard";

	public StunEffect(int color) {
		super(color);
		disableCreeperLinger = true;
	}

	@Override
	public boolean applyEffectTick(LivingEntity entity, int amplifier) {
		super.applyEffectTick(entity, amplifier);
		if (entity instanceof Creeper creeper) {
			CommonReflection.setCreeperSwell(creeper, -1);
		}
		return true;
	}

	@Override
	public void onAdded(LivingEntity entity, MobEffectInstance instance, Entity source) {
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

	// 1.21 discard (Mob.convertTo) clears effects before LivingConversionEvent.Post; 1.16 kept them.
	@Override
	public void onMobRemoved(LivingEntity entity, int amplifier, Entity.RemovalReason reason) {
		super.onMobRemoved(entity, amplifier, reason);
		if (reason == Entity.RemovalReason.DISCARDED && entity instanceof Mob) {
			entity.getPersistentData().putBoolean(STUNNED_ON_DISCARD, true);
		}
	}

	// True for a mob whose stun was cleared by its own discard (e.g. convertTo).
	public static boolean wasStunnedOnDiscard(LivingEntity entity) {
		return entity.isRemoved() && entity.getPersistentData().getBoolean(STUNNED_ON_DISCARD);
	}

	@Override
	public boolean isApplicable(LivingEntity entity) {
		return super.isApplicable(entity) && !(entity instanceof Mob mob && mob.isNoAi());
	}
}
