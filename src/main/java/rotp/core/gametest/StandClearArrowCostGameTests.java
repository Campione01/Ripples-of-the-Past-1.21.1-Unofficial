package rotp.core.gametest;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import rotp.core.JojoModConfig;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.api.stand.StandPowerTransitions.TransitionContext;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.mechanics.standarrow.StandVirusActualEffect;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 /stand clear ran power.clear() and power.fullStandClear(); fullStandClear ended with
 * standArrowHandler.clear(), so the next Arrow cost standXpCostInitial again. power.clear() alone
 * (Remove Stand, /stand give ... true) kept the count.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandClearArrowCostGameTests {
	private static final int ARROW_STANDS = 3;

	private StandClearArrowCostGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void standRemoveCommandResetsArrowCost(GameTestHelper helper) {
		ServerPlayer user = spawn(helper, "StandClearArrowCost");
		StandPower power = PowerClass.STAND.attachGet(user);
		CommandSourceStack source = helper.getLevel().getServer().createCommandSourceStack()
				.withEntity(user).withLevel(helper.getLevel()).withPermission(4).withSuppressedOutput();
		try {
			run(helper, source, "stand give @s jojo_ripples:star_platinum true");
			helper.assertTrue(power.hasPower(), "/stand give did not give Star Platinum");
			user.setData(ModDataAttachmentTypes.STANDS_GOT_FROM_ARROW, ARROW_STANDS);
			assertCount(helper, user, ARROW_STANDS, "Could not set the Arrow Stand count");

			run(helper, source, "stand give @s jojo_ripples:magicians_red true");
			assertCount(helper, user, ARROW_STANDS, "/stand give ... true (1.16 power.clear()) must keep the Arrow cost");

			run(helper, source, "stand remove @s");
			helper.assertTrue(!power.hasPower(), "/stand remove left the Stand");
			assertCount(helper, user, 0, "/stand remove (1.16 fullStandClear) must reset the Arrow cost");
			int initial = JojoModConfig.getCommonConfigInstance(false).standXpCostInitial.get();
			int cost = StandVirusActualEffect.getStandXpLevelsRequirement(user, ItemStack.EMPTY);
			helper.assertTrue(cost == initial, "After /stand remove the Arrow costs " + cost + ", expected " + initial);

			// 1.16 ran fullStandClear on a target without a Stand too
			user.setData(ModDataAttachmentTypes.STANDS_GOT_FROM_ARROW, ARROW_STANDS);
			run(helper, source, "stand remove @s");
			assertCount(helper, user, 0, "/stand remove on a Stand-less player must reset the Arrow cost");
		}
		finally {
			close(user, power);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void apiFullResetResetsArrowCostButClearKeepsIt(GameTestHelper helper) {
		ServerPlayer user = spawn(helper, "StandFullResetArrowCost");
		StandPower power = PowerClass.STAND.attachGet(user);
		TransitionContext context = new TransitionContext(JojoMod.resLoc("gametest_full_reset"), null);
		try {
			grant(helper, power);
			user.setData(ModDataAttachmentTypes.STANDS_GOT_FROM_ARROW, ARROW_STANDS);
			helper.assertTrue(StandPowerTransitions.clear(power, context).applied(), "API clear was not applied");
			assertCount(helper, user, ARROW_STANDS, "API clear must keep the Arrow cost");

			grant(helper, power);
			helper.assertTrue(StandPowerTransitions.fullReset(power, context).applied(), "API fullReset was not applied");
			assertCount(helper, user, 0, "API fullReset must reset the Arrow cost");
		}
		finally {
			close(user, power);
		}
		helper.succeed();
	}

	private static ServerPlayer spawn(GameTestHelper helper, String name) {
		ServerPlayer user = FakePlayerFactory.get(helper.getLevel(),
				new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.US_ASCII)), name));
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		user.moveTo(pos.x, pos.y, pos.z, 0, 0);
		helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the test player");
		return user;
	}

	private static void grant(GameTestHelper helper, StandPower power) {
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		helper.assertTrue(type != null, "Missing registered Star Platinum");
		helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
				== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
	}

	private static void assertCount(GameTestHelper helper, ServerPlayer user, int expected, String message) {
		int count = StandVirusActualEffect.getStandsGotFromArrow(user);
		helper.assertTrue(count == expected, message + ": count " + count + ", expected " + expected);
	}

	private static void run(GameTestHelper helper, CommandSourceStack source, String command) {
		try {
			helper.getLevel().getServer().getCommands().getDispatcher().execute(JojoMod.MOD_ID + " " + command, source);
		}
		catch (CommandSyntaxException error) {
			throw new AssertionError("/" + JojoMod.MOD_ID + " " + command + " failed: " + error.getMessage(), error);
		}
	}

	private static void close(ServerPlayer user, StandPower power) {
		if (power.isSummoned() && power.getPowerType() != null) {
			power.getPowerType().forceUnsummon(user, power);
		}
		if (power.hasPower()) {
			power.applyDestructiveTransition(false);
		}
		StandVirusActualEffect.resetStandsGotFromArrow(user);
		user.discard();
	}
}
