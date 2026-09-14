package rotp.core.compat.v1_21_4.missingmethods;

import net.minecraft.client.Minecraft;
import net.minecraft.util.profiling.ProfilerFiller;

public class Profiler {

	public static ProfilerFiller get() {
		return Minecraft.getInstance().getProfiler();
	}
}
