package com.github.standobyte.jojoimpl.powers.hamon.client;

import com.github.standobyte.jojo.client.sound.ClientsideSoundsHelper;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojoimpl.powers.hamon.entity.LeavesGliderEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public class GliderFlightSound extends AbstractTickableSoundInstance {
	private static final float VOLUME_HIGHER_PITCH = 0.8F;

	private final LeavesGliderEntity glider;
	private int time;
	private float trueVolume = 0.05F;

	private GliderFlightSound(LeavesGliderEntity glider) {
		super(ModSoundEvents.GLIDER_FLIGHT.get(), SoundSource.AMBIENT, RandomSource.create(glider.level().random.nextLong()));
		this.glider = glider;
		this.looping = true;
		this.delay = 0;
		this.volume = 0.05F;
		this.attenuation = SoundInstance.Attenuation.LINEAR;
	}

	public static void play(LeavesGliderEntity glider) {
		ClientsideSoundsHelper.playNonVanillaClassSound(new GliderFlightSound(glider));
	}

	@Override
	public void tick() {
		++time;
		if (!glider.isAlive() || !(time <= 20 || glider.isFlying())) {
			stop();
			return;
		}

		volume = trueVolume;
		x = glider.getX();
		y = glider.getY();
		z = glider.getZ();

		double movementSqr = glider.getDeltaMovement().lengthSqr();
		if (movementSqr >= 1.0E-7D) {
			volume = Mth.clamp((float) movementSqr * 1.5F, 0.0F, 1.0F);
		}
		else {
			volume = 0.0F;
		}

		if (time < 20) {
			volume = 0.0F;
		}
		else if (time < 40) {
			volume *= (float) (time - 20) / 20.0F;
		}

		pitch = volume > VOLUME_HIGHER_PITCH ? 1.0F + (volume - VOLUME_HIGHER_PITCH) : 1.0F;
		trueVolume = volume;
		manualAttenuation();
	}

	private void manualAttenuation() {
		Minecraft minecraft = Minecraft.getInstance();
		if (attenuation != SoundInstance.Attenuation.LINEAR || minecraft.player == null
				|| minecraft.player.getVehicle() == glider) {
			return;
		}
		Vec3 cameraPos = minecraft.gameRenderer.getMainCamera().getPosition();
		Vec3 vecTo = new Vec3(x, y, z).subtract(cameraPos);
		double maxDistance = getSound().getAttenuationDistance();
		volume *= Math.max(1.0D - vecTo.length() / maxDistance, 0.0D);
	}
}
