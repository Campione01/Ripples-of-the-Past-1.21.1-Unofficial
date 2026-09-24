package rotp.core.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

/**
 * Vanilla GameTestHelper.makeMockPlayer answers isLocalPlayer() true in the server level, which sends
 * PlayerClientBroadcastedSettings.getPlayerSettings to the client's settings. A real server player is never the
 * local player, so tests that reach those settings use this mock: the same damageable player without that override.
 */
final class GameTestPlayers {
	private GameTestPlayers() {}

	static Player makeServerMockPlayer(GameTestHelper helper, GameType gameType) {
		return new Player(helper.getLevel(), BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "test-mock-player")) {
			@Override
			public boolean isSpectator() {
				return gameType == GameType.SPECTATOR;
			}

			@Override
			public boolean isCreative() {
				return gameType.isCreative();
			}
		};
	}
}
