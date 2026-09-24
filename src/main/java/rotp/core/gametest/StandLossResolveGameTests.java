package rotp.core.gametest;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import rotp.core.JojoModConfig;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.npc.rps.RockPaperScissorsGame;
import rotp.core.init.power.ModStands;
import rotp.core.mechanics.resolve.ResolveCounter;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 cleared the Resolve value whenever a Stand was taken (StandPower.clear / putOutStand ->
 * ResolveCounter.onClearStandType), levels kept: Boy II Man's third win (RockPaperScissorsGame putOutStand) and the
 * add-ons' disc and mob removals (Whitesnake putOutStand, Mobs With Powers clear), which the port routes through the
 * legacy StandPowerTransitions.extract. 1.16 /stand clear on a player without a Stand still ran fullStandClear
 * (commands.stand.remove.success.single.no_stand).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandLossResolveGameTests {
	private static final ResourceLocation STAR_PLATINUM = JojoMod.resLoc("star_platinum");
	private static final int LEVEL = 2;
	private static final float EPS = 1.0E-3F;

	private StandLossResolveGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void legacyExtractTakesTheResolveValueButKeepsTheLevel(GameTestHelper helper) {
		User user = new User(helper, "LegacyExtractResolve", new BlockPos(2, 2, 2));
		try {
			user.give(STAR_PLATINUM);
			user.fillResolve();
			StandPowerTransitions.Result extracted = StandPowerTransitions.extract(user.power, STAR_PLATINUM);
			helper.assertTrue(extracted.applied() && !user.power.hasPower(), "The legacy extract did not take the Stand: "
					+ extracted.status());
			user.assertResolveCleared("The legacy StandPowerTransitions.extract");
			user.give(STAR_PLATINUM);
			helper.assertTrue(user.power.getResolveLevel() == Math.max(LEVEL, user.levelOfANewStand()),
					"1.16 putOutStand kept the Resolve level: level " + user.power.getResolveLevel());
		}
		finally {
			user.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void boyIIMansThirdWinTakesTheResolveValue(GameTestHelper helper) {
		User winner = new User(helper, "RpsBoyIIManWinner", new BlockPos(1, 2, 1));
		User loser = new User(helper, "RpsStandLoser", new BlockPos(3, 2, 3));
		try {
			winner.give(ModStands.BOY_II_MAN.getId());
			loser.give(STAR_PLATINUM);
			loser.fillResolve();
			RockPaperScissorsGame game = new RockPaperScissorsGame(winner.player.getUUID(), loser.player.getUUID(), false,
					RockPaperScissorsGame.Pick.ROCK, RockPaperScissorsGame.Pick.SCISSORS, null,
					RockPaperScissorsGame.WINS_NEEDED, RockPaperScissorsGame.WINS_NEEDED, 0);
			game.applyBoyIIManRoundResult(helper.getLevel(), winner.player, loser.player, RockPaperScissorsGame.Result.WIN);
			helper.assertTrue(!loser.power.hasPower(), "Boy II Man's third win did not take the whole Stand");
			loser.assertResolveCleared("Boy II Man's third win");
			loser.give(STAR_PLATINUM);
			helper.assertTrue(loser.power.getResolveLevel() == Math.max(LEVEL, loser.levelOfANewStand()),
					"1.16 putOutStand kept the Resolve level: level " + loser.power.getResolveLevel());
		}
		finally {
			winner.close();
			loser.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void standRemoveWithoutAStandStillClearsProgression(GameTestHelper helper) {
		User user = new User(helper, "StandRemoveNoStand", new BlockPos(2, 2, 2));
		try {
			user.give(STAR_PLATINUM);
			user.fillResolve();
			helper.assertTrue(StandPowerTransitions.extract(user.power, STAR_PLATINUM).applied() && !user.power.hasPower(),
					"Could not take the Stand before the command");
			// the port answered "no Stand" here; 1.16 /stand clear ran fullStandClear anyway
			user.run("stand remove @s");
			user.give(STAR_PLATINUM);
			helper.assertTrue(user.power.getResolveLevel() == user.levelOfANewStand(),
					"/jojo_ripples stand remove on a user without a Stand kept the Resolve level: level "
							+ user.power.getResolveLevel());
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
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(standId);
			helper.assertTrue(type != null, "Missing registered Stand " + standId);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant " + standId);
		}

		// a Resolve level, a value in its no-decay ticks, an attack boost and a record, as a fight leaves them
		void fillResolve() {
			power.setResolveLevel(LEVEL);
			helper.assertTrue(power.getResolveLevel() == LEVEL, "Could not set the Resolve level");
			CompoundTag nbt = power.resolveCounter.writeNBT();
			nbt.putFloat("Resolve", 400.0F);
			nbt.putInt("ResolveTicks", 300);
			nbt.putFloat("BoostAttack", 2.0F);
			ListTag records = new ListTag();
			records.add(FloatTag.valueOf(900.0F));
			nbt.put("ResolveRecord", records);
			nbt.putFloat("MaxAchieved", 900.0F);
			power.resolveCounter.readNBT(nbt);
			ResolveCounter counter = power.resolveCounter;
			helper.assertTrue(Math.abs(counter.getResolveValue() - 400.0F) < EPS && counter.boostAttack == 2.0F
					&& counter.maxAchievedValue == 900.0F, "Could not fill the Resolve");
		}

		void assertResolveCleared(String what) {
			ResolveCounter counter = power.resolveCounter;
			helper.assertTrue(counter.getResolveValue() == 0.0F && counter.noResolveDecayTicks == 0,
					what + " kept the Resolve value: " + counter.getResolveValue());
			helper.assertTrue(counter.boostAttack == 1.0F, what + " kept the attack boost: " + counter.boostAttack);
			helper.assertTrue(counter.maxAchievedValue == 0.0F
					&& counter.writeNBT().getList("ResolveRecord", Tag.TAG_FLOAT).isEmpty(),
					what + " kept the Resolve records (max " + counter.maxAchievedValue + ")");
		}

		// skipStandProgression gives a new Stand its highest level
		int levelOfANewStand() {
			return JojoModConfig.getCommonConfigInstance(false).skipStandProgression.get() ? power.getMaxResolveLevel() : 0;
		}

		void run(String command) {
			CommandSourceStack source = helper.getLevel().getServer().createCommandSourceStack()
					.withEntity(player).withLevel(helper.getLevel()).withPermission(4).withSuppressedOutput();
			try {
				helper.getLevel().getServer().getCommands().getDispatcher().execute(JojoMod.MOD_ID + " " + command, source);
			}
			catch (CommandSyntaxException error) {
				throw new AssertionError("/" + JojoMod.MOD_ID + " " + command + " failed: " + error.getMessage(), error);
			}
		}

		void close() {
			if (power.isSummoned() && power.getPowerType() != null) {
				power.getPowerType().forceUnsummon(player, power);
			}
			player.discard();
		}
	}
}
