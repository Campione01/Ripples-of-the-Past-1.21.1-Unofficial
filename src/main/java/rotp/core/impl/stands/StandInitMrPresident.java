package rotp.core.impl.stands;

import org.jetbrains.annotations.ApiStatus;

import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.type.NoSummonStandType;

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
