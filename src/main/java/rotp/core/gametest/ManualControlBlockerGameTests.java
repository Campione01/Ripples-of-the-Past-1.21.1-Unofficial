package rotp.core.gametest;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import rotp.core.api.stand.ManualControlBlockers;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityTypes;
import rotp.core.impl.stands._entitybase.StandEntityManualControlToggle;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntity.StandFlag;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.entity_puppetcontrol.EntityComponentController;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Add-on manual-control vetoes (1.16 Sticky Fingers headless gate) block entry only. */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ManualControlBlockerGameTests {
	private static final Set<UUID> BLOCKED_USERS = ConcurrentHashMap.newKeySet();
	private static final AtomicBoolean REGISTERED = new AtomicBoolean();

	private ManualControlBlockerGameTests() {}

	private static void ensureBlockerRegistered() {
		if (REGISTERED.compareAndSet(false, true)) {
			ManualControlBlockers.register(JojoMod.resLoc("gametest_manual_control_blocker"),
					(user, stand) -> BLOCKED_USERS.contains(user.getUUID()));
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void blockedUserCannotEnterButCanLeaveManualControl(GameTestHelper helper) {
		ensureBlockerRegistered();
		ServerLevel level = helper.getLevel();
		FakePlayer user = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ManualBlocker"));
		StandPower power = null;
		StandEntity stand = null;
		try {
			Vec3 position = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(position.x, position.y, position.z);
			helper.assertTrue(level.addFreshEntity(user), "Could not add manual-blocker owner");
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(type != null, "Missing registered Star Platinum");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant manual-blocker Stand");
			stand = ModEntityTypes.HUMANOID_STAND.get().create(level);
			helper.assertTrue(stand != null, "Could not create manual-blocker Stand entity");
			stand.withStandType(type);
			stand.copyPosition(user);
			power.setSummonedStand(stand);
			var ability = power.getAbility("manual_control");
			helper.assertTrue(ability instanceof StandEntityManualControlToggle,
					"Missing production manual-control ability");
			StandEntityManualControlToggle toggle = (StandEntityManualControlToggle) ability;

			BLOCKED_USERS.add(user.getUUID());
			ConditionCheck blockedEntry = toggle.checkSpecificConditions(power);
			helper.assertTrue(!blockedEntry.isPositive() && blockedEntry.getWarning() == null,
					"Vetoed user was not silently refused manual control: " + blockedEntry.isPositive());
			toggle.onClick(level, user, null);
			assertControlled(helper, user, stand, false, "Vetoed toggle press entered manual control");
			StandEntityManualControlToggle.on(level, stand);
			assertControlled(helper, user, stand, false, "Direct entry ignored the manual-control veto");

			BLOCKED_USERS.remove(user.getUUID());
			helper.assertTrue(toggle.checkSpecificConditions(power).isPositive(),
					"Lifting the veto did not restore manual-control eligibility");
			toggle.onClick(level, user, null);
			assertControlled(helper, user, stand, true, "Unvetoed toggle press did not enter manual control");

			// A user vetoed while already controlling must still be able to leave
			BLOCKED_USERS.add(user.getUUID());
			helper.assertTrue(toggle.checkSpecificConditions(power).isPositive(),
					"Veto trapped the user in manual control");
			toggle.onClick(level, user, null);
			assertControlled(helper, user, stand, false, "Vetoed user could not leave manual control");
		}
		finally {
			BLOCKED_USERS.remove(user.getUUID());
			if (user.hasData(ModDataAttachmentTypes.CONTROLLER)) {
				user.getData(ModDataAttachmentTypes.CONTROLLER).stopControlling();
			}
			if (stand != null) {
				StandEntityManualControlToggle.off(level, stand, false);
				if (power != null) power.setSummonedStand(null);
				stand.discard();
			}
			user.discard();
		}
		helper.succeed();
	}

	private static void assertControlled(GameTestHelper helper, FakePlayer user, StandEntity stand,
			boolean controlled, String message) {
		boolean flag = stand.getStandFlag(StandFlag.MANUAL_CONTROL);
		boolean bound = EntityComponentController.getControlTarget(user) == stand;
		helper.assertTrue(flag == controlled && bound == controlled,
				message + " (flag=" + flag + ", bound=" + bound + ")");
	}
}
