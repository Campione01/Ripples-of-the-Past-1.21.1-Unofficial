package com.github.standobyte.jojo.client.sound.barrage;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BooleanSupplier;

import com.github.standobyte.jojo.client.sound.ClientsideSoundsHelper;
import com.github.standobyte.jojo.client.sound.sounds.EntityStoppableSoundInstance;
import com.github.standobyte.jojo.client.sound.util.EventlessSoundAccessor;
import com.github.standobyte.jojo.client.standskin.StandSkin;
import com.github.standobyte.jojo.client.standskin.StandSkinsLoader;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.client.sounds.Weighted;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public final class StandCrySoundHandler {
	private static final List<StandCrySoundHandler> TICKING_HANDLERS = new ArrayList<>();

	private final StandEntity stand;
	private final SoundEvent sound;
	private final SoundSource source;
	private final float volume;
	private final float pitch;
	private final BooleanSupplier stopWhen;
	private final RandomSource random = RandomSource.create();
	private final List<Weighted<Sound>> notPlayed = new ArrayList<>();
	private SoundInstance currentSound;
	private int soundsPlayed;
	private boolean stopped;

	private StandCrySoundHandler(StandEntity stand, SoundEvent sound, float volume, float pitch, BooleanSupplier stopWhen) {
		this.stand = stand;
		this.sound = sound;
		this.source = stand.getSoundSource();
		this.volume = volume;
		this.pitch = pitch;
		this.stopWhen = stopWhen;
	}

	public static void create(StandEntity stand, SoundEvent sound, float volume, float pitch, BooleanSupplier stopWhen) {
		if (stand == null || sound == null) {
			return;
		}
		TICKING_HANDLERS.add(new StandCrySoundHandler(stand, sound, volume, pitch, stopWhen));
	}

	@SubscribeEvent
	public static void tickAll(ClientTickEvent.Post event) {
		Iterator<StandCrySoundHandler> iter = TICKING_HANDLERS.iterator();
		while (iter.hasNext()) {
			StandCrySoundHandler handler = iter.next();
			handler.tick();
			if (handler.stopped) {
				iter.remove();
			}
		}
	}

	private void tick() {
		if (stopped) {
			return;
		}
		if (stand.isRemoved() || stand.isSilent() || stopWhen.getAsBoolean()) {
			stop();
			return;
		}
		if (currentSound == null || !Minecraft.getInstance().getSoundManager().isActive(currentSound)) {
			playNextSound();
		}
	}

	private void playNextSound() {
		ResolvedCrySound soundToPlay = pickNextSound();
		if (soundToPlay.sound == SoundManager.EMPTY_SOUND) {
			stop();
			return;
		}
		currentSound = new StandCrySoundInstance(soundToPlay,
				source, volume, pitch, stand, stand.level().random.nextLong(),
				() -> stopped || stand.isRemoved() || stand.isSilent() || stopWhen.getAsBoolean());
		ClientsideSoundsHelper.playNonVanillaClassSound(currentSound);
	}

	private ResolvedCrySound pickNextSound() {
		SoundEvent soundEvent = pickNextSoundEvent();
		SoundsResolved resolved = resolve(soundEvent);
		boolean consumesNormalQueue = soundEvent == sound;
		if (!consumesNormalQueue && resolved.sounds.isEmpty()) {
			soundEvent = sound;
			resolved = resolve(soundEvent);
			consumesNormalQueue = true;
		}
		Weighted<Sound> weightedSound = pickWeightedSound(resolved, consumesNormalQueue);
		Sound pickedSound = weightedSound != null ? weightedSound.getSound(random) : SoundManager.EMPTY_SOUND;
		pickedSound = applyStandSkinSoundOverride(pickedSound);
		soundsPlayed++;
		return new ResolvedCrySound(soundEvent, pickedSound, resolved.subtitle);
	}

	private SoundEvent pickNextSoundEvent() {
		if (sound == ModSoundEvents.GOLD_EXPERIENCE_MUDA_RUSH.get() && soundsPlayed % 7 == 4) {
			return ModSoundEvents.GOLD_EXPERIENCE_WRY.get();
		}
		return sound;
	}

	private Weighted<Sound> pickWeightedSound(SoundsResolved resolved, boolean consumesNormalQueue) {
		List<Weighted<Sound>> pickFrom;
		if (consumesNormalQueue) {
			if (notPlayed.isEmpty()) {
				notPlayed.addAll(resolved.sounds);
			}
			pickFrom = notPlayed;
		}
		else {
			pickFrom = resolved.sounds;
		}
		if (pickFrom.isEmpty()) {
			return null;
		}
		int weight = pickFrom.stream().map(Weighted::getWeight).reduce(0, Integer::sum);
		if (weight <= 0) {
			return null;
		}
		int rand = random.nextInt(weight);
		for (Weighted<Sound> weightedSound : pickFrom) {
			rand -= weightedSound.getWeight();
			if (rand < 0) {
				if (consumesNormalQueue) {
					notPlayed.remove(weightedSound);
				}
				return weightedSound;
			}
		}
		return null;
	}

	private SoundsResolved resolve(SoundEvent soundEvent) {
		SoundManager soundManager = Minecraft.getInstance().getSoundManager();
		WeighedSoundEvents sounds = getStandSkinSoundEvent(soundEvent);
		if (sounds == null) {
			sounds = soundManager.getSoundEvent(soundEvent.getLocation());
		}
		if (sounds == null) {
			return SoundsResolved.EMPTY;
		}
		return new SoundsResolved(sounds.list, sounds.getSubtitle());
	}

	private WeighedSoundEvents getStandSkinSoundEvent(SoundEvent soundEvent) {
		StandSkin standSkin = StandSkinsLoader.getInstance().getSkin(stand);
		return standSkin != null ? standSkin.getSoundEvent(soundEvent.getLocation()) : null;
	}

	private Sound applyStandSkinSoundOverride(Sound pickedSound) {
		if (pickedSound == null || pickedSound == SoundManager.EMPTY_SOUND) {
			return SoundManager.EMPTY_SOUND;
		}
		StandSkin standSkin = StandSkinsLoader.getInstance().getSkin(stand);
		if (standSkin != null) {
			Sound standSkinSound = standSkin.overrideSound(pickedSound);
			if (standSkinSound != null) {
				return standSkinSound;
			}
		}
		return pickedSound;
	}

	private void stop() {
		stopped = true;
		if (currentSound instanceof EntityStoppableSoundInstance stoppable) {
			stoppable.ITS_FUCKING_STOPPED_ALREADY = true;
		}
	}

	private static final class StandCrySoundInstance extends EntityStoppableSoundInstance {
		private final ResolvedCrySound resolvedSound;

		private StandCrySoundInstance(ResolvedCrySound resolvedSound, SoundSource source, float volume, float pitch,
				StandEntity stand, long seed, BooleanSupplier stopWhen) {
			super(ClientsideSoundsHelper.withStandSkin(resolvedSound.soundEvent, stand), source, volume, pitch, stand, seed, stopWhen);
			this.resolvedSound = resolvedSound;
		}

		@Override
		public WeighedSoundEvents resolve(SoundManager soundManager) {
			this.sound = resolvedSound.sound;
			return new EventlessSoundAccessor(resolvedSound.sound.getLocation(), resolvedSound.subtitle, resolvedSound.sound);
		}
	}

	private static final class ResolvedCrySound {
		private final SoundEvent soundEvent;
		private final Sound sound;
		private final Component subtitle;

		private ResolvedCrySound(SoundEvent soundEvent, Sound sound, Component subtitle) {
			this.soundEvent = soundEvent;
			this.sound = sound;
			this.subtitle = subtitle;
		}
	}

	private static final class SoundsResolved {
		private static final SoundsResolved EMPTY = new SoundsResolved(List.of(), null);

		private final List<Weighted<Sound>> sounds;
		private final Component subtitle;

		private SoundsResolved(List<Weighted<Sound>> sounds, Component subtitle) {
			this.sounds = sounds;
			this.subtitle = subtitle;
		}
	}

	private StandCrySoundHandler() {
		throw new UnsupportedOperationException();
	}
}
