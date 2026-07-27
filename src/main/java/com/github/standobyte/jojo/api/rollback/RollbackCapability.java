package com.github.standobyte.jojo.api.rollback;

/**
 * State surfaces that must participate in one rollback transaction.
 */
public enum RollbackCapability {
	PLAYER,
	LIVING_ENTITY,
	PROJECTILE,
	ITEM_ENTITY,
	OTHER_ENTITY,
	BLOCK,
	BLOCK_ENTITY,
	CONTAINER,
	ITEM_LINEAGE,
	DEATH_REMOVAL,
	SCHEDULED_TICK,
	ALLOWLISTED_WORLD_STATE,
	ADDON_STATE
}
