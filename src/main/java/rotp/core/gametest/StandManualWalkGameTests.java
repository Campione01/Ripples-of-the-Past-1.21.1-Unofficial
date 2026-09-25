package rotp.core.gametest;

import rotp.core.core.JojoMod;
import rotp.core.init.ModEntityTypes;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// 1.16 manual control: Stands fly; a Stand with gravity (RHCP) walks and jumps.
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandManualWalkGameTests {
	private StandManualWalkGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void flyingStandRisesSinksAndNeverJumps(GameTestHelper helper) {
		StandEntity stand = ModEntityTypes.HUMANOID_STAND.get().create(helper.getLevel());
		helper.assertTrue(stand != null, "Could not create a Stand entity");
		helper.assertTrue(!stand.walksUnderManualControl(), "Stands must fly under manual control by default");
		helper.assertTrue(stand.manualControlVerticalInput(true, false, 0.5) == 0.5,
				"Jump key must raise a flying Stand by its speed");
		helper.assertTrue(stand.manualControlVerticalInput(false, true, 0.5) == -0.5,
				"Sneak key must lower a flying Stand by its speed");
		helper.assertTrue(stand.manualControlHorizontalSpeed(0.5, true) == 0.5,
				"Sneaking must not halve a flying Stand's speed");
		helper.assertTrue(!stand.manualControlJump(true), "A flying Stand must not jump");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void walkingStandJumpsInsteadOfRising(GameTestHelper helper) {
		WalkingStand stand = new WalkingStand(helper.getLevel());
		helper.assertTrue(stand.manualControlVerticalInput(true, false, 0.5) == 0,
				"Jump key must not lift a walking Stand; got " + stand.manualControlVerticalInput(true, false, 0.5));
		helper.assertTrue(stand.manualControlVerticalInput(false, true, 0.5) == 0,
				"Sneak key must not lower a walking Stand; got " + stand.manualControlVerticalInput(false, true, 0.5));
		helper.assertTrue(stand.manualControlHorizontalSpeed(0.5, true) == 0.25,
				"A sneaking walking Stand must move at half speed; got " + stand.manualControlHorizontalSpeed(0.5, true));
		helper.assertTrue(stand.manualControlHorizontalSpeed(0.5, false) == 0.5,
				"A walking Stand must keep its speed without sneaking");
		helper.assertTrue(stand.manualControlJump(true) && stand.jumpingFlag(),
				"Jump key must set a walking Stand's vanilla jump flag");
		helper.assertTrue(!stand.manualControlJump(false) && !stand.jumpingFlag(),
				"Releasing the jump key must clear the jump flag");
		helper.succeed();
	}

	private static final class WalkingStand extends StandEntity {
		WalkingStand(Level level) {
			super(ModEntityTypes.HUMANOID_STAND.get(), level);
		}

		@Override
		public boolean walksUnderManualControl() {
			return true;
		}

		boolean jumpingFlag() {
			return jumping;
		}
	}
}
