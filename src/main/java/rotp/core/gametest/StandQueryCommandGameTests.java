package rotp.core.gametest;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 /stand type &lt;player&gt; sent commands.stand.query.success (player, Stand name) and returned the Stand's
 * numeric registry id; a player without a Stand failed with commands.stand.query.failed.single.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandQueryCommandGameTests {
	private static final String SUCCESS_KEY = "rotp.commands.stand.query.success";
	private static final String FAILED_KEY = "rotp.commands.stand.query.failed.single";

	private StandQueryCommandGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void standTypeNamesTheStandAndReturnsItsId(GameTestHelper helper) {
		ServerPlayer user = addUser(helper, "StandTypeQuery");
		List<Component> messages = new ArrayList<>();
		CommandSourceStack source = source(helper, user, messages);
		try {
			PowerClass.STAND.attachGet(user);
			String[] stands = { "star_platinum", "magicians_red" };
			int[] results = new int[stands.length];
			for (int i = 0; i < stands.length; i++) {
				StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(stands[i]));
				helper.assertTrue(type != null, "Missing registered " + stands[i]);
				run(source, "stand give @s " + JojoMod.MOD_ID + ":" + stands[i] + " true");
				messages.clear();
				results[i] = run(source, "stand type @s");
				int registryId = JojoRegistries.DEFAULT_STANDS_REG.getId(type);
				helper.assertTrue(results[i] == registryId,
						"/stand type returned " + results[i] + " for " + stands[i] + ", its registry id is " + registryId);
				Object[] args = translatableArgs(messages, SUCCESS_KEY);
				helper.assertTrue(args != null && args.length == 2, "/stand type did not send " + SUCCESS_KEY + ": " + messages);
				helper.assertTrue(args[0] instanceof Component player && player.getString().equals(user.getName().getString()),
						"/stand type named the wrong player: " + args[0]);
				helper.assertTrue(Objects.equals(args[1], type.name.get()),
						"/stand type named the wrong Stand: " + args[1] + " for " + stands[i]);
			}
			helper.assertTrue(results[0] != results[1], "/stand type returned the same id for two Stands");
		}
		finally {
			close(user);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void standTypeFailsWithoutAStand(GameTestHelper helper) {
		ServerPlayer user = addUser(helper, "StandTypeQueryNoStand");
		List<Component> messages = new ArrayList<>();
		CommandSourceStack source = source(helper, user, messages);
		try {
			StandPower power = PowerClass.STAND.attachGet(user);
			run(source, "stand remove @s");
			helper.assertTrue(!power.hasPower(), "Could not clear the Stand");
			messages.clear();
			CommandSyntaxException failure = null;
			try {
				helper.getLevel().getServer().getCommands().getDispatcher().execute(JojoMod.MOD_ID + " stand type @s", source);
			}
			catch (CommandSyntaxException error) {
				failure = error;
			}
			helper.assertTrue(failure != null, "/stand type succeeded for a player without a Stand");
			helper.assertTrue(failure.getRawMessage() instanceof Component msg
					&& msg.getContents() instanceof TranslatableContents tl && tl.getKey().equals(FAILED_KEY),
					"/stand type failed with the wrong message: " + failure.getRawMessage());
			helper.assertTrue(translatableArgs(messages, SUCCESS_KEY) == null, "/stand type reported a Stand: " + messages);
		}
		finally {
			close(user);
		}
		helper.succeed();
	}

	private static ServerPlayer addUser(GameTestHelper helper, String name) {
		ServerPlayer user = FakePlayerFactory.get(helper.getLevel(),
				new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.US_ASCII)), name));
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		user.moveTo(pos.x, pos.y, pos.z, 0, 0);
		helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the command target");
		return user;
	}

	// records the chat feedback the command sends
	private static CommandSourceStack source(GameTestHelper helper, ServerPlayer user, List<Component> messages) {
		CommandSource capture = new CommandSource() {
			@Override public void sendSystemMessage(Component message) { messages.add(message); }
			@Override public boolean acceptsSuccess() { return true; }
			@Override public boolean acceptsFailure() { return true; }
			@Override public boolean shouldInformAdmins() { return false; }
		};
		return helper.getLevel().getServer().createCommandSourceStack()
				.withSource(capture).withEntity(user).withLevel(helper.getLevel()).withPermission(4);
	}

	private static Object[] translatableArgs(List<Component> messages, String key) {
		for (Component message : messages) {
			if (message.getContents() instanceof TranslatableContents tl && tl.getKey().equals(key)) {
				return tl.getArgs();
			}
		}
		return null;
	}

	private static int run(CommandSourceStack source, String command) {
		try {
			return source.getServer().getCommands().getDispatcher().execute(JojoMod.MOD_ID + " " + command, source);
		}
		catch (CommandSyntaxException error) {
			throw new AssertionError("/" + JojoMod.MOD_ID + " " + command + " failed: " + error.getMessage(), error);
		}
	}

	private static void close(ServerPlayer user) {
		StandPower power = StandPower.get(user);
		if (power != null && power.isSummoned() && power.getPowerType() != null) {
			power.getPowerType().forceUnsummon(user, power);
		}
		user.discard();
	}
}
