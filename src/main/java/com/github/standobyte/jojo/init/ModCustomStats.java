package com.github.standobyte.jojo.init;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.StatFormatter;
import net.minecraft.stats.Stats;
import net.neoforged.neoforge.registries.RegisterEvent;

public final class ModCustomStats {
	public static final ResourceLocation VAMPIRE_PEOPLE_DRAINED = JojoMod.resLoc("vampire_people_drained");
	public static final ResourceLocation VAMPIRE_ANIMALS_DRAINED = JojoMod.resLoc("vampire_animals_drained");
	public static final ResourceLocation VAMPIRE_ZOMBIES_CREATED = JojoMod.resLoc("vampire_zombies_created");
	public static final ResourceLocation VAMPIRE_ZOMBIES_SUMMONED = JojoMod.resLoc("vampire_zombies_summoned");

	private ModCustomStats() {}

	public static void registerCustomStats(RegisterEvent event) {
		event.register(Registries.STAT_TYPE, helper -> {
			registerCustomStat(VAMPIRE_PEOPLE_DRAINED);
			registerCustomStat(VAMPIRE_ANIMALS_DRAINED);
			registerCustomStat(VAMPIRE_ZOMBIES_CREATED);
			registerCustomStat(VAMPIRE_ZOMBIES_SUMMONED);
		});
	}

	private static void registerCustomStat(ResourceLocation id) {
		Registry.register(BuiltInRegistries.CUSTOM_STAT, id, id);
		Stats.CUSTOM.get(id, StatFormatter.DEFAULT);
	}
}
