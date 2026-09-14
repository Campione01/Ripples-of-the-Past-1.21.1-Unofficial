package rotp.core.api.healing;

import java.util.Objects;

import javax.annotation.Nullable;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.world.entity.LivingEntity;

public record LivingRestoreContext(
		LivingEntity target,
		LivingEntity healer,
		@Nullable StandEntity crazyDiamond,
		boolean coreHealingActive,
		boolean coreBarrageVisuals,
		float coreHpForExperience) {
	public LivingRestoreContext {
		Objects.requireNonNull(target, "target");
		Objects.requireNonNull(healer, "healer");
	}
}
