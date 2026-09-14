package rotp.core.powersystem.standpower.type;

import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.datapack.StandTypeClass;
import rotp.core.powersystem.standpower.entity.StandControlType;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

public class NoSummonStandType extends StandType {
	static {
		StandTypeClass.registerStandClass(NoSummonStandType.class, "nosummon", NoSummonStandType::new);
	}

	public NoSummonStandType(StandStats stats, MovesetBuilder moveset, ResourceLocation id) {
		super(stats, moveset, id);
		nonEntityStandControlPolicy(
				StandControlType.PHENOMENON,
				false,
				false);
	}

	@Override
	public boolean summon(LivingEntity user, StandPower standPower) {
		return false;
	}

	@Override
	public void unsummon(LivingEntity user, StandPower standPower) {}

	@Override
	public void forceUnsummon(LivingEntity user, StandPower standPower) {}
}
