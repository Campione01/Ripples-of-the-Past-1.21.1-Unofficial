package com.github.standobyte.v1_21_4_stuff.missingmethods;

import net.minecraft.client.Minecraft;
import net.minecraft.util.profiling.ProfilerFiller;

public class Profiler {

	public static ProfilerFiller get() {
		return Minecraft.getInstance().getProfiler();
	}
}
