package com.github.standobyte.jojo.api.healing;

import java.util.Objects;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;

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
