package com.github.standobyte.jojo.client.standskin.sound;

import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.resources.ResourceLocation;

public class CustomPathSound extends Sound {
	protected ResourceLocation filePath;
	
	public CustomPathSound(Sound sound, ResourceLocation filePath) {
		super(sound.getLocation(), sound.getVolume(), sound.getPitch(), sound.getWeight(), sound.getType(), sound.shouldStream(), sound.shouldPreload(), sound.getAttenuationDistance());
		this.filePath = filePath != null ? filePath : sound.getPath();
	}

	@Override
	public ResourceLocation getPath() {
		return filePath;
	}

	@Override
	public String toString() {
		return "Sound[" + this.getLocation() + "] at path: + " + this.getPath();
	}
	
}
