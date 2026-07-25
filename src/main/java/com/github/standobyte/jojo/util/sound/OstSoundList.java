package com.github.standobyte.jojo.util.sound;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class OstSoundList {
	private static final String[] POSTFIXES = {"_30", "_60", "_75", "_90", "_120", "_full"};
	private final List<DeferredHolder<SoundEvent, SoundEvent>> soundEvents;
	private final DeferredHolder<SoundEvent, SoundEvent> soundEventCassette;

	public OstSoundList(ResourceLocation resLoc, DeferredRegister<SoundEvent> register) {
		List<DeferredHolder<SoundEvent, SoundEvent>> soundEvents = new ArrayList<>();
		for (String postfix : POSTFIXES) {
			soundEvents.add(register(resLoc, register, postfix));
		}
		this.soundEvents = Collections.unmodifiableList(soundEvents);
		this.soundEventCassette = register(resLoc, register, "_cassette");
	}

	private static DeferredHolder<SoundEvent, SoundEvent> register(ResourceLocation resLoc,
			DeferredRegister<SoundEvent> register, String postfix) {
		String path = resLoc.getPath() + postfix;
		return register.register(path, SoundEvent::createVariableRangeEvent);
	}

	public SoundEvent get(int index) {
		return soundEvents.get(Mth.clamp(index, 0, soundEvents.size() - 1)).get();
	}

	public SoundEvent getFull() {
		return soundEvents.get(soundEvents.size() - 1).get();
	}

	public SoundEvent getForCassette() {
		return soundEventCassette.get();
	}
}
