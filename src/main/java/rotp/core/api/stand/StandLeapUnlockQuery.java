package rotp.core.api.stand;

import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.EntityStandType;

import net.minecraft.world.entity.LivingEntity;

public record StandLeapUnlockQuery(
		LivingEntity user,
		StandPower power,
		EntityStandType standType) {}
