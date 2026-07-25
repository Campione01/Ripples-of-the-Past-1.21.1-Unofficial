package com.github.standobyte.jojoimpl.powers.hamon;

public final class HamonTrainingPhase2 {

	private HamonTrainingPhase2() {
	}

	public static float fullEnergyTicks(HamonData hamon) {
		return hamon != null ? hamon.getFullEnergyTicks() : 80.0F;
	}
}
