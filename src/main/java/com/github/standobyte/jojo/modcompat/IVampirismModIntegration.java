package com.github.standobyte.jojo.modcompat;

import net.minecraft.world.entity.LivingEntity;

public interface IVampirismModIntegration {
	boolean isEntityVampire(LivingEntity entity);

	class Dummy implements IVampirismModIntegration {
		@Override
		public boolean isEntityVampire(LivingEntity entity) {
			return false;
		}
	}
}
