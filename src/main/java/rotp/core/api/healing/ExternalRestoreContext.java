package rotp.core.api.healing;

import java.util.Objects;

import javax.annotation.Nullable;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public record ExternalRestoreContext(
		Entity target,
		LivingEntity healer,
		@Nullable StandEntity crazyDiamond) {
	public ExternalRestoreContext {
		Objects.requireNonNull(target, "target");
		Objects.requireNonNull(healer, "healer");
	}
}
