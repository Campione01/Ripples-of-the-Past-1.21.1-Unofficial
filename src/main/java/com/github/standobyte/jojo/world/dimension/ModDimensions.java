package com.github.standobyte.jojo.world.dimension;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

public class ModDimensions {
	public static final ResourceKey<Level> MR_PRESIDENT = ResourceKey.create(
			Registries.DIMENSION, JojoMod.resLoc("mr_president"));
	public static final ResourceKey<DimensionType> MR_PRESIDENT_TYPE = ResourceKey.create(
			Registries.DIMENSION_TYPE, JojoMod.resLoc("mr_president"));

	private ModDimensions() {}
}
