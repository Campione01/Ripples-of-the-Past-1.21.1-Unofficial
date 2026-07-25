package com.github.standobyte.v1_21_4_stuff.missingmethods;

import net.minecraft.resources.ResourceLocation;

public class ContextKey<T> {
    private final ResourceLocation name;

    public ContextKey(ResourceLocation name) {
        this.name = name;
    }

    public static <T> ContextKey<T> vanilla(String name) {
        return new ContextKey<>(ResourceLocation.withDefaultNamespace(name));
    }

    public ResourceLocation name() {
        return this.name;
    }

    @Override
    public String toString() {
        return "<parameter " + this.name + ">";
    }
}
