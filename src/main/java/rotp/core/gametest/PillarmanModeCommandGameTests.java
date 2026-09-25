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
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;

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

	/**
	 * 1.16 made all of /pillarman op-only. The port's power_pillarman node merges with JojoEnergyCommand's, and
	 * Brigadier keeps the first node's requires, so its set subtree was open at permission 0.
	 */
	@GameTest(template = "empty", timeoutTicks = 80)
	public static void powerPillarmanSetNeedsOp(GameTestHelper helper) {
		ServerPlayer user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(
				UUID.nameUUIDFromBytes("PillarmanModeCommandOp".getBytes(StandardCharsets.US_ASCII)), "PillarmanModeCommandOp"));
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		user.moveTo(pos.x, pos.y, pos.z, 0, 0);
		helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the command target");
		try {
			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.PILLAR_MAN.get());
			PillarmanData data = PlayerPower.getPowerData(user, ModPlayerPowers.PILLAR_MAN).orElseThrow();
			int stageBefore = data.getEvolutionStage();
			CommandDispatcher<CommandSourceStack> dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
			CommandSourceStack plain = helper.getLevel().getServer().createCommandSourceStack()
					.withEntity(user).withLevel(helper.getLevel()).withPermission(0);
			CommandSourceStack op = plain.withPermission(2);
			CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild(JojoMod.MOD_ID);
			helper.assertTrue(root != null, "/" + JojoMod.MOD_ID + " is not registered");
			CommandNode<CommandSourceStack> pillarman = root.getChild("power_pillarman");
			helper.assertTrue(pillarman != null && !pillarman.canUse(plain) && pillarman.canUse(op),
					"power_pillarman is not op-only");
			CommandNode<CommandSourceStack> set = pillarman.getChild("set");
			helper.assertTrue(set != null && !set.canUse(plain) && set.canUse(op),
					"power_pillarman set is not op-only");
			CommandNode<CommandSourceStack> top = dispatcher.getRoot().getChild("pillarman");
			helper.assertTrue(top != null && !top.canUse(plain), "/pillarman is usable at permission 0");
			// By name: selectors need permission 2 and would hide a missing gate.
			String command = JojoMod.MOD_ID + " power_pillarman set stage " + user.getGameProfile().getName() + " 3";
			try {
				dispatcher.execute(command, plain);
				throw new AssertionError("/" + command + " ran at permission 0");
			}
			catch (CommandSyntaxException error) {
				helper.assertTrue(error.getType() == CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument(),
						"/" + command + " reached its arguments at permission 0: " + error.getMessage());
			}
			helper.assertTrue(data.getEvolutionStage() == stageBefore,
					"/" + command + " changed the stage at permission 0 to " + data.getEvolutionStage());
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
