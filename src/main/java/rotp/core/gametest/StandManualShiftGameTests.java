package rotp.core.gametest;

import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModEntityTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.entity_puppetcontrol.StandManualInput;
import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// R148-C24 step 5: port manual control clears the user's sneak (1.16 kept
// it), so Stand moves read the Shift key the controlling client sends.
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandManualShiftGameTests {
	private StandManualShiftGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void manualControlReadsSentShift(GameTestHelper helper) {
		FakePlayer user = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ManualShift"));
		StandPower power = null;
		ShiftStand stand = null;
		try {
			Vec3 userPos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(userPos.x, userPos.y, userPos.z);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Stand user");
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(type != null, "Missing registered Star Platinum");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant the Stand");
			stand = new ShiftStand(helper.getLevel());
			stand.withStandType(type);
			stand.moveTo(userPos.x + 1.0D, userPos.y, userPos.z);
			power.setSummonedStand(stand);
			helper.assertTrue(stand.getUser() == user, "Stand user did not resolve");

			// Without manual control: the user's own sneak.
			StandManualInput.setServerShift(user, true);
			helper.assertTrue(!StandManualInput.isShiftHeld(user),
					"A sent Shift counted without manual control");
			user.setShiftKeyDown(true);
			helper.assertTrue(StandManualInput.isShiftHeld(user, stand),
					"The user's sneak did not count without manual control");
			user.setShiftKeyDown(false);
			StandManualInput.setServerShift(user, false);

			// Manual control: the sneak is cleared, the sent key counts.
			stand.setManuallyControlled(true);
			helper.assertTrue(stand.isManuallyControlled(), "Manual control did not start");
			helper.assertTrue(!StandManualInput.isShiftHeld(user),
					"Shift counted before the client sent it");
			StandManualInput.setServerShift(user, true);
			helper.assertTrue(StandManualInput.isShiftHeld(user)
					&& StandManualInput.isShiftHeld(user, stand),
					"The sent Shift did not count under manual control");
			StandManualInput.setServerShift(user, false);
			helper.assertTrue(!StandManualInput.isShiftHeld(user),
					"A released Shift still counted");

			// Logging out drops a held key.
			StandManualInput.setServerShift(user, true);
			StandManualInput.onLoggedOut(new PlayerEvent.PlayerLoggedOutEvent(user));
			helper.assertTrue(!StandManualInput.isShiftHeld(user),
					"Logging out kept the sent Shift");

			// The payload carries the key state.
			RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
					helper.getLevel().registryAccess());
			StandManualInput.ShiftPacket.Handler.STREAM_CODEC.encode(buf, new StandManualInput.ShiftPacket(true));
			helper.assertTrue(StandManualInput.ShiftPacket.Handler.STREAM_CODEC.decode(buf).held(),
					"The Shift payload lost the held key");
		}
		finally {
			StandManualInput.clear(user);
			if (power != null) power.setSummonedStand(null);
			if (stand != null) stand.discard();
			user.discard();
		}
		helper.succeed();
	}

	private static final class ShiftStand extends StandEntity {
		ShiftStand(Level level) {
			super(ModEntityTypes.HUMANOID_STAND.get(), level);
		}
	}
}
