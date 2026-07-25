package com.github.standobyte.v1_21_4_stuff.missingmethods;

import javax.annotation.Nullable;

import net.minecraft.util.profiling.ProfilerFiller;

public class Zone implements AutoCloseable {
    private final ProfilerFiller profiler;

    public Zone(@Nullable ProfilerFiller profiler) {
        this.profiler = profiler;
    }

    @Override
    public void close() {
        if (this.profiler != null) {
            this.profiler.pop();
        }
    }

}
