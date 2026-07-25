package com.github.standobyte.jojo.util.mod;

import net.minecraft.world.entity.Entity;

public interface IPlayerLeap {
	boolean _isEntityOnGround();
	boolean isDoingLeap();
	void setIsDoingLeap(boolean isDoingLeap);
	
	default void leapFlagTick() {
		if (isDoingLeap() && _isEntityOnGround()) {
			setIsDoingLeap(false);
		}
	}
	
	static void onLeapFixWrongMovement(Entity entity) {
		if (entity instanceof IPlayerLeap playerLeap) {
			playerLeap.setIsDoingLeap(true);
		}
	}
}
