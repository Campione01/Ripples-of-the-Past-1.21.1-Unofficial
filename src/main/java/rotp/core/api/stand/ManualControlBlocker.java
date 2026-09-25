package rotp.core.api.stand;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.world.entity.LivingEntity;

/**
 * Add-on veto for entering Stand manual control. Leaving manual control is
 * never vetoed.
 */
@FunctionalInterface
public interface ManualControlBlocker {
	boolean isBlocked(
			LivingEntity user,
			StandEntity stand);
}
