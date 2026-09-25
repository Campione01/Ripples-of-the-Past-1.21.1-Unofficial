package rotp.core.gametest;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 giveStandFromInstance -> onNewPowerGiven set the leap cooldown to getLeapCooldownPeriod(), 0 with no Stand
 * manifested. A leftover cooldown from before a non-destructive loss (legacy extract) must not carry over to the next
 * Stand (insert, arrow and /stand give), nor through an evolution (legacy replace). Tusk's act change (context
 * replace) keeps it, as 1.16 Tusk was one Stand.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NewStandLeapCooldownGameTests {
	private static final ResourceLocation STAR_PLATINUM = JojoMod.resLoc("star_platinum");
	private static final ResourceLocation THE_WORLD = JojoMod.resLoc("the_world");
	private static final int LEFTOVER = 60;

	private NewStandLeapCooldownGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void insertAfterLegacyExtractZeroesTheLeapCooldown(GameTestHelper helper) {
		User user = new User(helper, "LeapCdExtractInsert", new BlockPos(2, 2, 2));
		try {
			user.give(STAR_PLATINUM);
			StandPowerTransitions.Result extracted = StandPowerTransitions.extract(user.power, STAR_PLATINUM);
			helper.assertTrue(extracted.applied() && !user.power.hasPower(),
					"The legacy extract did not take the Stand: " + extracted.status());
			user.leftover();
			user.give(THE_WORLD);
			user.assertZero("The insert after a legacy extract");
		}
		finally {
			user.close();
		}
		helper.succeed();
	}

	// the Stand arrow and /stand give set the Stand directly
	@GameTest(template = "empty", timeoutTicks = 80)
	public static void setStandAfterLegacyExtractZeroesTheLeapCooldown(GameTestHelper helper) {
		User user = new User(helper, "LeapCdExtractSetStand", new BlockPos(2, 2, 2));
		try {
			user.give(STAR_PLATINUM);
			helper.assertTrue(StandPowerTransitions.extract(user.power, STAR_PLATINUM).applied(),
					"The legacy extract did not take the Stand");
			user.leftover();
			user.power.setStand(user.type(THE_WORLD));
			helper.assertTrue(user.power.getPowerType() == user.type(THE_WORLD), "setStand did not give The World");
			user.assertZero("StandPower.setStand (arrow, /stand give) after a legacy extract");
		}
		finally {
			user.close();
		}
		helper.succeed();
	}

	// 1.16 evolutions ran clear() + give
	@GameTest(template = "empty", timeoutTicks = 80)
	public static void legacyReplaceZeroesTheLeapCooldown(GameTestHelper helper) {
		User user = new User(helper, "LeapCdLegacyReplace", new BlockPos(2, 2, 2));
		try {
			user.give(STAR_PLATINUM);
			user.leftover();
			StandPowerTransitions.Result replaced = StandPowerTransitions.replace(user.power, STAR_PLATINUM,
					user.instance(THE_WORLD));
			helper.assertTrue(replaced.applied() && user.power.getPowerType() == user.type(THE_WORLD),
					"The legacy replace did not evolve the Stand: " + replaced.status());
			user.assertZero("The legacy replace (evolution)");
		}
		finally {
			user.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void contextReplaceKeepsTheLeapCooldown(GameTestHelper helper) {
		User user = new User(helper, "LeapCdContextReplace", new BlockPos(2, 2, 2));
		try {
			user.give(STAR_PLATINUM);
			user.leftover();
			StandPowerTransitions.Result replaced = StandPowerTransitions.replace(user.power, STAR_PLATINUM,
					user.instance(THE_WORLD), new StandPowerTransitions.TransitionContext(
							JojoMod.resLoc("gametest_act_change"), user.player));
			helper.assertTrue(replaced.applied() && user.power.getPowerType() == user.type(THE_WORLD),
					"The context replace did not change the Stand: " + replaced.status());
			helper.assertTrue(user.power.getLeapCooldown() == LEFTOVER,
					"The context replace (act change) reset the leap cooldown: " + user.power.getLeapCooldown());
		}
		finally {
			user.close();
		}
		helper.succeed();
	}

	private static final class User {
		final GameTestHelper helper;
		final ServerPlayer player;
		final StandPower power;

		User(GameTestHelper helper, String name, BlockPos pos) {
			this.helper = helper;
			player = FakePlayerFactory.get(helper.getLevel(),
					new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.US_ASCII)), name));
			Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(pos));
			player.moveTo(at.x, at.y, at.z, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add " + name);
			power = PowerClass.STAND.attachGet(player);
		}

		void give(ResourceLocation standId) {
			helper.assertTrue(StandPowerTransitions.insert(power, instance(standId)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant " + standId);
		}

		StandType type(ResourceLocation standId) {
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(standId);
			helper.assertTrue(type != null, "Missing registered Stand " + standId);
			return type;
		}

		StandInstance instance(ResourceLocation standId) {
			return new StandInstance(type(standId));
		}

		// the cooldown a leap with a manifested Stand leaves
		void leftover() {
			power.setLeapCooldown(LEFTOVER);
			helper.assertTrue(power.getLeapCooldown() == LEFTOVER, "Could not set the leap cooldown");
		}

		void assertZero(String what) {
			helper.assertTrue(power.getLeapCooldown() == 0,
					what + " kept the leftover leap cooldown: " + power.getLeapCooldown());
		}

		void close() {
			if (power.isSummoned() && power.getPowerType() != null) {
				power.getPowerType().forceUnsummon(player, power);
			}
			player.discard();
		}
	}
}
