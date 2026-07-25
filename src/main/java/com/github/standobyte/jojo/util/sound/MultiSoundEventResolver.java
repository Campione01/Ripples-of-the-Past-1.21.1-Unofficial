package com.github.standobyte.jojo.util.sound;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModSoundEvents;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

public final class MultiSoundEventResolver {
	private static final Random RANDOM = new Random();
	private static final Map<String, List<Supplier<? extends SoundEvent>>> ORIGINAL_CANDIDATES = Map.ofEntries(
			entry("zeppeli_sunlight_yellow_overdrive",
					ModSoundEvents.ZEPPELI_SUNLIGHT_YELLOW_OVERDRIVE,
					ModSoundEvents.ZEPPELI_HAMON_OF_THE_SUN,
					ModSoundEvents.ZEPPELI_THIS_IS_SENDO,
					ModSoundEvents.ZEPPELI_THIS_IS_SENDO_POWER),
			entry("joseph_sunlight_yellow_overdrive",
					ModSoundEvents.JOSEPH_HAMON_OVERDRIVE_BEAT,
					ModSoundEvents.JOSEPH_HAMON_PUNCH),
			entry("caesar_sunlight_yellow_overdrive",
					ModSoundEvents.CAESAR_SUN_VIBRATION,
					ModSoundEvents.CAESAR_HAMON_OF_THE_SUN,
					ModSoundEvents.CAESAR_HAMON_SPARK),
			entry("jonathan_scarlet_overdrive",
					ModSoundEvents.JONATHAN_SCARLET_OVERDRIVE,
					ModSoundEvents.JONATHAN_HAMON_OF_FLAME),
			entry("zeppeli_hamon_cutter",
					ModSoundEvents.ZEPPELI_HAMON_CUTTER,
					ModSoundEvents.ZEPPELI_POPOW_POW_POW),
			entry("joseph_clacker_volley",
					ModSoundEvents.JOSEPH_CLACKER_VOLLEY,
					ModSoundEvents.JOSEPH_HAMON_CLACKER_VOLLEY),
			entry("caesar_bubble_launcher",
					ModSoundEvents.CAESAR_BUBBLE_LAUNCHER,
					ModSoundEvents.CAESAR_SECRET_HAMON_BUBBLE_LAUNCHER),
			entry("caesar_bubble_cutter",
					ModSoundEvents.CAESAR_BUBBLE_CUTTER,
					ModSoundEvents.CAESAR_DISC_SHAPED_HAMON_CUTTER),
			entry("jotaro_time_resumes",
					ModSoundEvents.JOTARO_TIME_RESUMES_DASU,
					ModSoundEvents.JOTARO_TIME_RESUMES_HAJIMETA),
			entry("kakyoin_hierophant_green",
					ModSoundEvents.KAKYOIN_HIEROPHANT_GREEN,
					ModSoundEvents.KAKYOIN_HIEROPHANT),
			entry("dio_time_stop",
					ModSoundEvents.DIO_TOKI_YO_TOMARE,
					ModSoundEvents.DIO_TOMARE_TOKI_YO),
			entry("dio_times_up",
					ModSoundEvents.DIO_TIME_RESUMES,
					ModSoundEvents.DIO_TIMES_UP,
					ModSoundEvents.DIO_ZERO),
			entry("polnareff_silver_chariot",
					ModSoundEvents.POLNAREFF_SILVER_CHARIOT,
					ModSoundEvents.POLNAREFF_CHARIOT));

	private MultiSoundEventResolver() {}

	public static Holder<SoundEvent> resolve(Holder<SoundEvent> sound) {
		if (sound == null || sound.value() == null) {
			return sound;
		}
		ResourceLocation soundId = sound.value().getLocation();
		if (!JojoMod.MOD_ID.equals(soundId.getNamespace())) {
			return sound;
		}
		List<Supplier<? extends SoundEvent>> candidates = ORIGINAL_CANDIDATES.get(soundId.getPath());
		if (candidates == null || candidates.isEmpty()) {
			return sound;
		}
		SoundEvent resolved = candidates.get(RANDOM.nextInt(candidates.size())).get();
		return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(resolved);
	}

	@SafeVarargs
	private static Map.Entry<String, List<Supplier<? extends SoundEvent>>> entry(String aggregate,
			Supplier<? extends SoundEvent>... candidates) {
		return Map.entry(aggregate, List.of(candidates));
	}
}
