package rotp.core.api.soul;

import javax.annotation.Nullable;

import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.soul.SoulEntity;

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
