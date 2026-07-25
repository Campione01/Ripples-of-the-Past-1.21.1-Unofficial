package com.github.standobyte.jojo.client.sound.sounds;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Like {@link net.minecraft.client.resources.sounds.EntityBoundSoundInstance}, but stays in place when the entity is removed
 */
public class EntityLingeringSoundInstance extends AbstractTickableSoundInstance {
	private Entity entity;

	public EntityLingeringSoundInstance(SoundEvent soundEvent, SoundSource source, float volume, float pitch, Entity entity, Level level) {
		super(soundEvent, source, RandomSource.create(level.random.nextLong()));
		this.volume = volume;
		this.pitch = pitch;
		this.entity = entity;
		this.x = entity.getX();
		this.y = entity.getY();
		this.z = entity.getZ();
	}

	@Override
	public boolean canPlaySound() {
		return entity == null || !entity.isSilent();
	}

	@Override
	public void tick() {
		if (entity != null) {
			if (entity.isRemoved()) {
				if (entity.isSilent()) {
					stop();
				}
				else {
					entity = null;
				}
			} else {
				this.x = entity.getX();
				this.y = entity.getY();
				this.z = entity.getZ();
			}
		}
	}

}
