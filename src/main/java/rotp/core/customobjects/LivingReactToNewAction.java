package rotp.core.customobjects;

import javax.annotation.Nullable;

import rotp.core.powersystem.entityaction.EntityActionInstance;

public interface LivingReactToNewAction {
	/**
	 * @param action - The new entity action, before it is set.
	 * @return true to cancel the action - it will not be set to the entity.
	 */
	boolean onActionSet(@Nullable EntityActionInstance action);
}
