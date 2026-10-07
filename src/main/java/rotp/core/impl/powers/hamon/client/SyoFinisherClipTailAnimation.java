package rotp.core.impl.powers.hamon.client;

import rotp.core.api.client.animation.AddonPlayerAnimations;
import rotp.core.api.client.animation.AddonPlayerAnimations.PlayerAnimationState;
import rotp.core.core.JojoMod;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.playerpower.PlayerPower;

/**
 * Plays the rest of syo_barrage_finisher on a player whose S.Y.O. Barrage has ended (HamonData holds the clip tick).
 * It is asked only while the player has no action and no persistent pose, like any other player animation provider.
 */
public final class SyoFinisherClipTailAnimation {
	private static final String FINISHER_CLIP = "syo_barrage_finisher";

	private SyoFinisherClipTailAnimation() {}

	public static void register() {
		AddonPlayerAnimations.register(JojoMod.resLoc("syo_barrage_finisher_tail"), 1000, (player, partialTick) -> {
			float clipTick = PlayerPower.getPowerData(player, ModPlayerPowers.HAMON)
					.map(hamon -> hamon.getSyoFinisherTailClipTick(player, partialTick)).orElse(-1.0F);
			return clipTick >= 0.0F
					? new PlayerAnimationState(ModPlayerPowers.HAMON.get().getId(), FINISHER_CLIP, clipTick)
					: null;
		});
	}
}
