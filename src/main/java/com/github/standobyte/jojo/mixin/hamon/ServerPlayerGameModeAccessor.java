package com.github.standobyte.jojo.mixin.hamon;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.server.level.ServerPlayerGameMode;

@Mixin(ServerPlayerGameMode.class)
public interface ServerPlayerGameModeAccessor {
	@Accessor("hasDelayedDestroy")
	boolean jojo_ripples$hasDelayedDestroy();
}
