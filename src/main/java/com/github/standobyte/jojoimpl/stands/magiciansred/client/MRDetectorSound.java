package com.github.standobyte.jojoimpl.stands.magiciansred.client;

import com.github.standobyte.jojoimpl.stands.magiciansred.MRDetectorEntity;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public class MRDetectorSound extends AbstractTickableSoundInstance {
	private final MRDetectorEntity detector;

	public MRDetectorSound(MRDetectorEntity detector) {
		super(SoundEvents.FIRE_AMBIENT, detector.getSoundSource(), RandomSource.create(detector.level().random.nextLong()));
		this.detector = detector;
		this.looping = true;
		this.delay = 0;
		this.x = detector.getX();
		this.y = detector.getY();
		this.z = detector.getZ();
	}

	@Override
	public boolean canPlaySound() {
		return !detector.isSilent();
	}

	@Override
	public void tick() {
		if (detector.isRemoved()) {
			stop();
		}
		else if (detector.isAlive() && detector.isEntityDetected()) {
			this.x = detector.getX();
			this.y = detector.getY();
			this.z = detector.getZ();
			float distance = Mth.sqrt((float) detector.getDetectedDirection().lengthSqr());
			this.volume = 1.0F - distance / (float) MRDetectorEntity.DETECTION_RADIUS * 0.5F;
		}
		else {
			stop();
		}
	}
}
