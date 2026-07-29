package com.github.standobyte.jojo.api.soul;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.subsystems.soul.SoulEntity;

import net.minecraft.world.entity.LivingEntity;

/**
 * Immutable context for deciding whether a soul may award Resolve.
 */
public record SoulResolveQuery(
		SoulEntity soul,
		@Nullable LivingEntity origin,
		LivingEntity target,
		StandPower targetStandPower,
		boolean resolveCanLevelUp,
		boolean defaultEligibility) {}
