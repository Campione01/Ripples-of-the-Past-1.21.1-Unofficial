package com.github.standobyte.jojo.mechanics;

import com.github.standobyte.jojo.customobjects.StatusEffectApplicable;
import com.github.standobyte.jojo.customobjects.StatusEffectModified;

import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class UndeadRegenerationEffect extends StatusEffectModified implements StatusEffectApplicable {

	public UndeadRegenerationEffect(MobEffectCategory category, int color) {
		super(category, color);
	}

	@Override
	public boolean applyEffectTick(LivingEntity entity, int amplifier) {
		if (entity.getHealth() < entity.getMaxHealth()) {
			entity.heal(1.0F);
		}
		return true;
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
		int tickGap = 50 >> amplifier;
		return tickGap <= 0 || duration % tickGap == 0;
	}

	@Override
	public boolean isApplicable(LivingEntity entity) {
		return !(entity instanceof Player player && JojoDefinitions.isPlayerJojoVampiric(player))
				&& entity.getType().is(EntityTypeTags.UNDEAD);
	}
}
