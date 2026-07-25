package com.github.standobyte.jojo.entityattachment;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;

public class PlayerVoiceLineData implements TickingEntityData {
	private final Map<Holder<SoundEvent>, Integer> recentlyPlayedVoiceLines = new HashMap<>();

	public PlayerVoiceLineData() {}

	public PlayerVoiceLineData(Player player) {
		addTicking(player);
	}

	public boolean checkNotRepeatingVoiceLine(Holder<SoundEvent> voiceLine, int voiceLineDelay) {
		if (recentlyPlayedVoiceLines.getOrDefault(voiceLine, 0) > 0) {
			return false;
		}
		recentlyPlayedVoiceLines.put(voiceLine, voiceLineDelay);
		return true;
	}

	@Override
	public void tick() {
		Iterator<Map.Entry<Holder<SoundEvent>, Integer>> iterator = recentlyPlayedVoiceLines.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<Holder<SoundEvent>, Integer> voiceLine = iterator.next();
			int ticks = voiceLine.getValue();
			if (ticks <= 1) {
				iterator.remove();
			}
			else {
				voiceLine.setValue(ticks - 1);
			}
		}
	}
}
