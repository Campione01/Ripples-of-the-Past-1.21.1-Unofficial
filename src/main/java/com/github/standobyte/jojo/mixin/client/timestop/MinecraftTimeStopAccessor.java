package com.github.standobyte.jojo.mixin.client.timestop;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.Minecraft;

@Mixin(Minecraft.class)
public interface MinecraftTimeStopAccessor {
	@Accessor("pause")
	void jojo_ripples$setPause(boolean pause);
}
