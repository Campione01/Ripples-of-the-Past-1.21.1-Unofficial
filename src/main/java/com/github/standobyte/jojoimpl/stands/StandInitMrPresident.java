package com.github.standobyte.jojoimpl.stands;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.powersystem.MovesetBuilder;
import com.github.standobyte.jojo.powersystem.standpower.StandStats;
import com.github.standobyte.jojo.powersystem.standpower.type.NoSummonStandType;

import net.minecraft.resources.ResourceLocation;

public class StandInitMrPresident {

	@ApiStatus.Internal
	public static NoSummonStandType create(ResourceLocation id) {
		return new NoSummonStandType(
				new StandStats.Builder()
				.power(0)
				.speed(0)
				.range(0, 0)
				.durability(14)
				.precision(0)
				.build(),

				new MovesetBuilder(),

				id);
	}
}
