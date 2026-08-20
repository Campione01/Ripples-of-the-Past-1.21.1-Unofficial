package com.github.standobyte.jojo.powersystem.standpower.type;

import com.github.standobyte.jojo.powersystem.MovesetBuilder;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.StandStats;
import com.github.standobyte.jojo.powersystem.standpower.datapack.StandTypeClass;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandControlType;

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
