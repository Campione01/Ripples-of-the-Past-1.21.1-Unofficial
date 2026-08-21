package com.github.standobyte.jojo.mixin.client.time;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;

@Mixin(AbstractSoundInstance.class)
public interface AbstractSoundInstanceRegionalTimeDilationAccessor {
	@Accessor("pitch")
	float jojo_ripples$getRegionalTimeDilationBasePitch();

	@Accessor("pitch")
	void jojo_ripples$setRegionalTimeDilationPitch(float pitch);
}
