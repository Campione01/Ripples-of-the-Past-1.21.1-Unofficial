package com.github.standobyte.v1_21_4_stuff.missingmethods;

import net.minecraft.util.profiling.ProfilerFiller;

public class _ProfilerFiller {

	public static Zone zone(ProfilerFiller profiler, String name) {
		profiler.push(name);
        return new Zone(profiler);
    }
}
