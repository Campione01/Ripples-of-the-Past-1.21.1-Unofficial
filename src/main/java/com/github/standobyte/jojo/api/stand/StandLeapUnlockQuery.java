package com.github.standobyte.jojo.api.stand;

import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.entity.EntityStandType;

import net.minecraft.world.entity.LivingEntity;

public record StandLeapUnlockQuery(
		LivingEntity user,
		StandPower power,
		EntityStandType standType) {}
