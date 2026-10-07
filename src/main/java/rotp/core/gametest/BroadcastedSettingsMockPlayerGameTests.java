package rotp.core.gametest;

import java.util.Optional;

import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.core.JojoMod;
import rotp.core.init.ModDataAttachmentTypes;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Vanilla's GameTestHelper.makeMockPlayer answers isLocalPlayer() true in the server level. A player in a server
 * level reads its own stored settings, never the client's.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BroadcastedSettingsMockPlayerGameTests {
	private BroadcastedSettingsMockPlayerGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void serverLevelPlayerReadsServerStoredSettings(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		try {
			helper.assertTrue(player.isLocalPlayer() && !player.level().isClientSide(),
					"Fixture: the vanilla mock player no longer claims to be local in the server level");
			PlayerClientBroadcastedSettings stored = player.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get());
			stored.standAttackTargetLock = true;
			stored.noStandAbilityCooldown = true;
			Object read;
			try {
				read = PlayerClientBroadcastedSettings.getPlayerSettings(player);
			}
			catch (Throwable failure) {
				read = failure;
			}
			helper.assertTrue(read instanceof Optional<?> settings && settings.isPresent() && settings.get() == stored,
					"A server-level player's settings did not come from its server-stored data: " + read);
			helper.succeed();
		}
		finally {
			player.discard();
		}
	}
}
