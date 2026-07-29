package com.github.standobyte.jojo.api.control;

/**
 * Server-owned player operations that addons may deny.
 */
public enum PlayerOperation {
	MENU_OPEN_STANDARD,
	MENU_OPEN_AS_NON_PLAYER,
	MENU_OPEN_EXTERNAL,
	CRAFT_RESULT_RECOMPUTE,
	CRAFT_RESULT_TAKE
}
