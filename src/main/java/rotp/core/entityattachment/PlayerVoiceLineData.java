package rotp.core.entityattachment;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.annotation.Nullable;

import rotp.core.subsystems.timestop.TimeStopState;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;

public class PlayerVoiceLineData implements TickingEntityData {
	private final Map<Holder<SoundEvent>, Integer> recentlyPlayedVoiceLines = new HashMap<>();
	@Nullable private final Player player;

	public PlayerVoiceLineData() {
		this.player = null;
	}

	public PlayerVoiceLineData(Player player) {
		this.player = player;
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
		// 1.16 PlayerUtilCap.tickVoiceLines ran from the player tick, which a player frozen in stopped time skips.
		if (player != null && TimeStopState.shouldFreezeOnServer(player)) {
			return;
		}
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
