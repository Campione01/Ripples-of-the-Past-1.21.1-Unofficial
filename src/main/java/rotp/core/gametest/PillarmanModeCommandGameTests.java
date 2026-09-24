package rotp.core.gametest;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

import rotp.core.impl.powers.pillarman.PillarmanData;
import rotp.core.impl.powers.pillarman.PillarmanMode;
import rotp.core.core.JojoMod;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 /pillarman set mode set the battle mode and reported it. The port's report threw IllegalArgumentException (an
 * enum translation argument) after the mode was set. The source here is not silenced, so the message is built.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PillarmanModeCommandGameTests {
	private PillarmanModeCommandGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void pillarmanSetModeReportsEveryMode(GameTestHelper helper) {
		ServerPlayer user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(
				UUID.nameUUIDFromBytes("PillarmanModeCommand".getBytes(StandardCharsets.US_ASCII)), "PillarmanModeCommand"));
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		user.moveTo(pos.x, pos.y, pos.z, 0, 0);
		helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the command target");
		try {
			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.PILLAR_MAN.get());
			PillarmanData data = PlayerPower.getPowerData(user, ModPlayerPowers.PILLAR_MAN).orElseThrow();
			CommandSourceStack source = helper.getLevel().getServer().createCommandSourceStack()
					.withEntity(user).withLevel(helper.getLevel()).withPermission(4);
			for (PillarmanMode mode : new PillarmanMode[] { PillarmanMode.WIND, PillarmanMode.HEAT,
					PillarmanMode.LIGHT, PillarmanMode.NONE }) {
				String command = "pillarman set mode @s " + mode.name().toLowerCase(Locale.ROOT);
				int result = run(helper, source, command);
				helper.assertTrue(result == 1, "/" + command + " returned " + result);
				helper.assertTrue(data.getMode() == mode, "/" + command + " left the mode at " + data.getMode());
			}
			int aliased = run(helper, source, JojoMod.MOD_ID + " power_pillarman set mode @s wind");
			helper.assertTrue(aliased == 1 && data.getMode() == PillarmanMode.WIND,
					"/" + JojoMod.MOD_ID + " power_pillarman set mode did not set the mode");
		}
		finally {
			user.discard();
		}
		helper.succeed();
	}

	private static int run(GameTestHelper helper, CommandSourceStack source, String command) {
		try {
			return helper.getLevel().getServer().getCommands().getDispatcher().execute(command, source);
		}
		catch (CommandSyntaxException error) {
			throw new AssertionError("/" + command + " failed: " + error.getMessage(), error);
		}
	}
}
