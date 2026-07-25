package com.github.standobyte.jojo.init;

import net.minecraft.world.level.GameRules;

public class ModGamerules {
	public static void load() {}

	public static final GameRules.Key<GameRules.BooleanValue> BREAK_BLOCKS = GameRules.register("jojoAbilitiesBreakBlocks",
			GameRules.Category.PLAYER, GameRules.BooleanValue.create(true));
}
