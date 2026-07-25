package com.github.standobyte.jojo.client.sound;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

import org.apache.commons.lang3.mutable.MutableInt;

import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModSoundEvents;

import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public class HamonSparksLoopSound {
	private static final Random RANDOM = new Random();
	private static final Map<Entity, MutableInt> SOUND_DELAYS = new HashMap<>();
	private static final Map<EntityType<?>, MutableInt> SOUND_LIMIT_PER_TYPE = new HashMap<>();

	public static boolean playSparkSound(Entity entity, Vec3 soundPos, float volume, float reducedFrequency) {
		if (RANDOM.nextFloat() < 1.0F / getDelay() * reducedFrequency) {
			return playSparkSound(entity, soundPos, volume);
		}
		return false;
	}

	public static boolean playSparkSound(Entity entity, Vec3 soundPos, float volume) {
		return playSparkSound(entity, soundPos, volume, false);
	}

	public static boolean playSparkSound(Entity entity, Vec3 soundPos, float volume, boolean limitForEntityType) {
		if (soundPos.distanceToSqr(ClientProxy.getCameraPos()) > 256.0D) {
			return false;
		}
		limitForEntityType = limitForEntityType && soundPos.distanceToSqr(ClientProxy.getCameraPos()) > 8.0D;

		MutableInt typeDelay = null;
		if (limitForEntityType) {
			typeDelay = SOUND_LIMIT_PER_TYPE.computeIfAbsent(entity.getType(), type -> new MutableInt(0));
			if (typeDelay.getValue() > 0) {
				return false;
			}
		}

		MutableInt curDelay = SOUND_DELAYS.computeIfAbsent(entity, e -> new MutableInt(0));
		if (curDelay.getValue() > 0) {
			return false;
		}

		entity.level().playLocalSound(soundPos.x, soundPos.y, soundPos.z,
				ModSoundEvents.HAMON_SPARK_SHORT.get(), SoundSource.AMBIENT, volume, 1.0F, false);
		curDelay.setValue(getDelay());
		if (limitForEntityType) {
			typeDelay.setValue(1);
		}
		return true;
	}

	private static int getDelay() {
		return 3 + RANDOM.nextInt(3);
	}

	@SubscribeEvent
	public static void tick(ClientTickEvent.Post event) {
		if (!SOUND_DELAYS.isEmpty()) {
			Iterator<Map.Entry<Entity, MutableInt>> iter = SOUND_DELAYS.entrySet().iterator();
			while (iter.hasNext()) {
				Map.Entry<Entity, MutableInt> entry = iter.next();
				if (!entry.getKey().isAlive()) {
					iter.remove();
				}
				else if (entry.getValue().getValue() > 0) {
					entry.getValue().decrement();
				}
			}
		}

		if (!SOUND_LIMIT_PER_TYPE.isEmpty()) {
			for (MutableInt delay : SOUND_LIMIT_PER_TYPE.values()) {
				if (delay.getValue() > 0) {
					delay.decrement();
				}
			}
		}
	}
}
