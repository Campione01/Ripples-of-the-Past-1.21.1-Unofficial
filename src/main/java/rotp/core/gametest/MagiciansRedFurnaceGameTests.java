package rotp.core.gametest;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.mixin.stand.magiciansred.AbstractFurnaceBlockEntityLitAccessor;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 GameplayEventHandler.furnaceInteract: right-clicking a furnace (main hand) while Magician's Red is summoned
 * set its lit time and lit duration to 12000 ticks and its LIT state, unless it already burned that long.
 * The event was not canceled, so the furnace GUI still opened.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MagiciansRedFurnaceGameTests {
	private static final BlockPos FURNACE = new BlockPos(1, 2, 1);

	private MagiciansRedFurnaceGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void summonedMagiciansRedLightsFurnace(GameTestHelper helper) {
		Fixture fixture = new Fixture(helper, "magicians_red", true);
		try {
			PlayerInteractEvent.RightClickBlock event = fixture.rightClickFurnace(InteractionHand.MAIN_HAND);
			helper.assertTrue(!event.isCanceled(), "Lighting the furnace must not cancel the click (the GUI opens)");
			helper.assertTrue(helper.getBlockState(FURNACE).getValue(AbstractFurnaceBlock.LIT),
					"Magician's Red did not set the furnace LIT");
			AbstractFurnaceBlockEntityLitAccessor lit = fixture.lit();
			helper.assertTrue(lit.jojo_ripples$getLitTime() == 12000 && lit.jojo_ripples$getLitDuration() == 12000,
					"Furnace lit time/duration should be 12000/12000, got " + lit.jojo_ripples$getLitTime()
					+ "/" + lit.jojo_ripples$getLitDuration());
		}
		finally {
			fixture.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void offHandClickDoesNotLightFurnace(GameTestHelper helper) {
		Fixture fixture = new Fixture(helper, "magicians_red", true);
		try {
			fixture.rightClickFurnace(InteractionHand.OFF_HAND);
			fixture.assertUnlit("An off-hand click");
		}
		finally {
			fixture.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void unsummonedMagiciansRedDoesNotLightFurnace(GameTestHelper helper) {
		Fixture fixture = new Fixture(helper, "magicians_red", false);
		try {
			fixture.rightClickFurnace(InteractionHand.MAIN_HAND);
			fixture.assertUnlit("Magician's Red that is not summoned");
		}
		finally {
			fixture.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void otherSummonedStandDoesNotLightFurnace(GameTestHelper helper) {
		Fixture fixture = new Fixture(helper, "star_platinum", true);
		try {
			fixture.rightClickFurnace(InteractionHand.MAIN_HAND);
			fixture.assertUnlit("A summoned Star Platinum");
		}
		finally {
			fixture.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void longerBurnIsNotShortened(GameTestHelper helper) {
		Fixture fixture = new Fixture(helper, "magicians_red", true);
		try {
			AbstractFurnaceBlockEntityLitAccessor lit = fixture.lit();
			lit.jojo_ripples$setLitTime(15000);
			lit.jojo_ripples$setLitDuration(16000);
			fixture.rightClickFurnace(InteractionHand.MAIN_HAND);
			helper.assertTrue(lit.jojo_ripples$getLitTime() == 15000 && lit.jojo_ripples$getLitDuration() == 16000,
					"A furnace already lit for 12000+ ticks must keep its burn, got " + lit.jojo_ripples$getLitTime()
					+ "/" + lit.jojo_ripples$getLitDuration());
		}
		finally {
			fixture.close();
		}
		helper.succeed();
	}

	private static final class Fixture {
		final GameTestHelper helper;
		final Player user;
		final StandType type;
		final StandPower power;

		Fixture(GameTestHelper helper, String standId, boolean summon) {
			this.helper = helper;
			helper.setBlock(FURNACE, Blocks.FURNACE);
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(pos.x, pos.y, pos.z, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Stand user");
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standId));
			helper.assertTrue(type != null, "Missing registered Stand " + standId);
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant " + standId);
			if (summon) {
				helper.assertTrue(type.summon(user, power) && power.getSummonedStandEntity() != null,
						"Could not summon " + standId);
			}
		}

		AbstractFurnaceBlockEntityLitAccessor lit() {
			helper.assertTrue(helper.getBlockEntity(FURNACE) instanceof AbstractFurnaceBlockEntity,
					"The furnace block entity is missing");
			return (AbstractFurnaceBlockEntityLitAccessor) helper.getBlockEntity(FURNACE);
		}

		PlayerInteractEvent.RightClickBlock rightClickFurnace(InteractionHand hand) {
			BlockPos abs = helper.absolutePos(FURNACE);
			return CommonHooks.onRightClickBlock(user, hand, abs,
					new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
		}

		void assertUnlit(String what) {
			helper.assertTrue(!helper.getBlockState(FURNACE).getValue(AbstractFurnaceBlock.LIT)
					&& lit().jojo_ripples$getLitTime() == 0, what + " must not light the furnace");
		}

		void close() {
			if (power.isSummoned()) {
				type.forceUnsummon(user, power);
			}
			user.discard();
		}
	}
}
