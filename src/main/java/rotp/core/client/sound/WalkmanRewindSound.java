package rotp.core.client.sound;

import rotp.core.client.WalkmanSoundHandler;
import rotp.core.init.ModSoundEvents;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public class WalkmanRewindSound extends AbstractTickableSoundInstance {
	public WalkmanRewindSound() {
		super(ModSoundEvents.WALKMAN_REWIND.get(), SoundSource.MASTER, RandomSource.create());
		this.volume = 1.0F;
		this.pitch = 1.0F;
		this.x = 0;
		this.y = 0;
		this.z = 0;
		this.looping = true;
		this.delay = 0;
		this.attenuation = SoundInstance.Attenuation.NONE;
		this.relative = true;
	}

	@Override
	public void tick() {
		WalkmanSoundHandler.Playlist playlist = WalkmanSoundHandler.getCurrentPlaylist();
		if (playlist == null || playlist.getRewindSoundTicks() <= 0) {
			stop();
		}
	}
}
