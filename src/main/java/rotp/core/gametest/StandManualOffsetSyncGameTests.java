package rotp.core.gametest;

import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModEntityTypes;
import rotp.core.network.s2c.TrDirectEntityPosPacket;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// R148-C23: a server position sync (TrDirectEntityPosPacket) must stick on
// the client that controls the Stand manually.
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandManualOffsetSyncGameTests {
	private StandManualOffsetSyncGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void directPosSyncReanchorsManualOffset(GameTestHelper helper) {
		FakePlayer user = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ManualOffsetSync"));
		StandPower power = null;
		OffsetStand stand = null;
		try {
			Vec3 userPos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(userPos.x, userPos.y, userPos.z);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Stand user");
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(type != null, "Missing registered Star Platinum");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant the Stand");
			stand = new OffsetStand(helper.getLevel());
			stand.withStandType(type);
			power.setSummonedStand(stand);
			helper.assertTrue(stand.getUser() == user, "Stand user did not resolve");

			// Old spot beside the user, anchored the way manual control keeps it.
			Vec3 oldPos = userPos.add(1.0D, 0.0D, 0.0D);
			stand.moveTo(oldPos.x, oldPos.y, oldPos.z);
			stand.anchor();
			Vec3 newPos = userPos.add(0.0D, 0.0D, 1.0D);
			TrDirectEntityPosPacket.applyPos(stand, newPos);
			helper.assertTrue(stand.position().distanceTo(newPos) < 1.0E-6D,
					"Position sync did not place the Stand at " + newPos);
			Vec3 offset = stand.offset();
			helper.assertTrue(offset != null && offset.distanceTo(newPos.subtract(userPos)) < 1.0E-6D,
					"Position sync kept the old manual offset " + offset);

			// A manual-control step with no input keeps the synced spot.
			stand.manualControlInput(Vec3.ZERO);
			stand.manualStep();
			helper.assertTrue(stand.position().distanceTo(newPos) < 1.0E-6D,
					"Manual control pulled the Stand back to " + stand.position());
		}
		finally {
			if (power != null) power.setSummonedStand(null);
			if (stand != null) stand.discard();
			user.discard();
		}
		helper.succeed();
	}

	private static final class OffsetStand extends StandEntity {
		OffsetStand(Level level) {
			super(ModEntityTypes.HUMANOID_STAND.get(), level);
		}

		// Plays the controlling client's side of manual control.
		@Override
		public boolean isControlledByLocalInstance() {
			return true;
		}

		void anchor() {
			updateUserOffset(getUser());
		}

		Vec3 offset() {
			return _offsetFromUserVec;
		}

		void manualStep() {
			moveStandManualControl();
		}
	}
}
