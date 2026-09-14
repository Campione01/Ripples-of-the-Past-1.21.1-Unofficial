package rotp.core.client.sound.sounds;

import rotp.core.client.ClientTimeStopHandler;
import rotp.core.client.sound.ClientsideSoundsHelper;
import rotp.core.customobjects.entity_projectile.BladeHatEntity;
import rotp.core.init.ModSoundEvents;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.util.RandomSource;

public class BladeHatSoundInstance extends AbstractTickableSoundInstance {
	private final BladeHatEntity hat;

	private BladeHatSoundInstance(BladeHatEntity hat) {
		super(ModSoundEvents.BLADE_HAT_SPINNING.get(), hat.getSoundSource(),
				RandomSource.create(hat.level().random.nextLong()));
		this.hat = hat;
		this.looping = true;
		this.delay = 0;
		updateFromHat();
	}

	public static void play(BladeHatEntity hat) {
		ClientsideSoundsHelper.playNonVanillaClassSound(new BladeHatSoundInstance(hat));
	}

	@Override
	public boolean canPlaySound() {
		return !hat.isSilent();
	}

	@Override
	public void tick() {
		if (ClientTimeStopHandler.isEntityVisuallyFrozen(hat)) {
			volume = 0.0F;
		}
		else if (hat.isAlive() && !hat.isInGround()) {
			updateFromHat();
		}
		else {
			stop();
		}
	}

	private void updateFromHat() {
		x = hat.getX();
		y = hat.getY();
		z = hat.getZ();
		volume = (float) hat.getDeltaMovement().length();
	}
}
