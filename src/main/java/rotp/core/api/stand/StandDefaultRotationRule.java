package rotp.core.api.stand;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.world.entity.LivingEntity;

/**
 * An add-on's default rotation for a following Stand whose user is not a player, in place of the core's copy of the
 * user's rotation. See {@link StandDefaultRotationRules}.
 */
@FunctionalInterface
public interface StandDefaultRotationRule {
	/**
	 * @return true when the rule takes the Stand's default rotation over this tick: it turned the Stand itself, or leaves
	 *         it as it is; false leaves the copy to the core
	 */
	boolean rotate(StandEntity stand, LivingEntity user);
}
