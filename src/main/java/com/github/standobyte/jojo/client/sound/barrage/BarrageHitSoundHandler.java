package com.github.standobyte.jojo.client.sound.barrage;

import java.util.Random;

import com.github.standobyte.jojo.client.ClientGlobals;
import com.github.standobyte.jojo.client.sound.ClientsideSoundsHelper;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandStatFormulas;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;

public class BarrageHitSoundHandler {
	private static final ResourceLocation SILVER_CHARIOT_ID = JojoMod.resLoc("silver_chariot");
	private final Random random = new Random();
	
	private SoundEvent sound;
	private Vec3 soundPos;
	private boolean pauseAfterNext;
	private boolean isBarraging;
	
	private double soundTickLast;
	private double nextSoundGap;
	
	public void hit(SoundEvent sound, Vec3 punchPos) {
		this.sound = sound;
		this.soundPos = punchPos;
		this.pauseAfterNext = false;
	}
	
	public void hitMissed() {
		this.pauseAfterNext = true;
	}
	
	public void setIsBarraging(boolean isBarraging) {
		this.isBarraging = isBarraging;
		if (!isBarraging) {
			sound = null;
			soundPos = null;
		}
	}

	public void playSound(StandEntity entity, float ticks) {
		if (ClientGlobals.canHearStand(entity) && !entity.isSilent()
				&& isBarraging && sound != null && soundPos != null && soundTickLast + nextSoundGap <= ticks) {
			soundTickLast = ticks;
			nextSoundGap = getSoundGap(entity);
			entity.level().playLocalSound(soundPos.x, soundPos.y, soundPos.z,
					ClientsideSoundsHelper.withStandSkin(sound, entity),
					entity.getSoundSource(),
					0.5F + random.nextFloat() * 0.25F,
					0.85F + random.nextFloat() * 0.3F,
					false);
			if (pauseAfterNext) {
				sound = null;
				soundPos = null;
				pauseAfterNext = false;
			}
		}
	}
	
	protected float getSoundGap(StandEntity entity) {
		float mean = 250.0F / (float) Math.max(40, StandStatFormulas
				.getBarrageHitsPerSecond(20 * entity.getStandEfficiency()));
		float gap = (float) random.nextGaussian() + mean;
		return SILVER_CHARIOT_ID.equals(entity.getStandType()) ? Math.min(gap * 0.5F, 1.5F) : gap;
	}
}
