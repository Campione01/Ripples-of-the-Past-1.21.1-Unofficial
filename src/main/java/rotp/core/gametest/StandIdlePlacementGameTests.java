package rotp.core.gametest;

import java.util.List;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntity.offsetDefault, StandRelativeOffset.withYOffset(-0.75, 0.2, -0.75) (left, up, forward), put an
 * idle Stand's feet at its user's feet plus that vector turned by the user's view yaw (StandEntity.taskOffset with
 * no task takes user.yRot): 0.75 to the right of the view, 0.75 behind it and 0.2 up. The "Stand offset side" setting
 * LEFT mirrors the side (StandRelativeOffset.getAbsoluteVec), and a sneaking user's Stand drops the 0.2. The Stand
 * took the user's view as its yaw and head yaw (StandEntity.defaultRotation).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandIdlePlacementGameTests {
	private static final List<String> CORE_ENTITY_STANDS = List.of("star_platinum", "the_world", "hierophant_green",
			"silver_chariot", "magicians_red", "crazy_diamond", "gold_experience");
	private static final double EPS = 1.0E-4D;

	private StandIdlePlacementGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void everyCoreStandIdlesBehindTheRightOfItsUsersViewAsIn116(GameTestHelper helper) {
		for (String id : CORE_ENTITY_STANDS) {
			checkIdlePlacement(helper, id);
		}
		helper.succeed();
	}

	private static void checkIdlePlacement(GameTestHelper helper, String id) {
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(id));
		StandPower power = null;
		try {
			helper.assertTrue(type instanceof EntityStandType, "Missing the core entity Stand " + id);
			// the server reads the side from this attachment, and the summoned Stand keeps a reference to it
			PlayerClientBroadcastedSettings settings = user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get());
			settings.standSide = HumanoidArm.RIGHT;
			Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 12.0D, 2.5D));
			user.setNoGravity(true);
			user.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the user of " + id);
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant " + id);
			helper.assertTrue(type.summon(user, power), "Could not summon " + id);
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null && stand.getUser() == user && stand.isFollowingUser() && !stand.isArmsOnlyMode()
					&& stand.offsetFromUser.isIdle(), "setup: the summoned " + id + " is not an idle full Stand following its user");

			// view yaw 0 faces south (+z), so the user's right is -x; the body is turned away from the view
			turn(user, 0.0F, 50.0F);
			assertIdleAt(helper, id, user, stand, -0.75D, 0.2D, -0.75D, "view yaw 0, body yaw 50, side RIGHT");
			helper.assertTrue(near(stand.getYRot(), 0.0F) && near(stand.getYHeadRot(), 0.0F),
					"1.16 turned an idle " + id + " and its head to the user's view yaw 0: yaw " + stand.getYRot()
							+ ", head " + stand.getYHeadRot());
			helper.assertTrue(near(stand.yBodyRot, 50.0F),
					"the idle " + id + "'s body lags the view as 1.16's head turn let it, by taking the user's body yaw 50: body "
							+ stand.yBodyRot);

			turn(user, 0.0F, 120.0F);
			assertIdleAt(helper, id, user, stand, -0.75D, 0.2D, -0.75D, "view yaw 0, body yaw 120 (the body alone turned)");

			settings.standSide = HumanoidArm.LEFT;
			assertIdleAt(helper, id, user, stand, 0.75D, 0.2D, -0.75D, "view yaw 0, side LEFT (mirrored to the left)");
			settings.standSide = HumanoidArm.RIGHT;

			// facing west (-x) the right is -z; facing north (-z) it is +x
			turn(user, 90.0F, 50.0F);
			assertIdleAt(helper, id, user, stand, 0.75D, 0.2D, -0.75D, "view yaw 90, body yaw 50, side RIGHT");
			turn(user, 180.0F, 50.0F);
			assertIdleAt(helper, id, user, stand, 0.75D, 0.2D, 0.75D, "view yaw 180, body yaw 50, side RIGHT");

			turn(user, 0.0F, 50.0F);
			user.setShiftKeyDown(true);
			assertIdleAt(helper, id, user, stand, -0.75D, 0.0D, -0.75D, "view yaw 0, sneaking (1.16 dropped the y offset)");
			user.setShiftKeyDown(false);
		}
		finally {
			if (power != null && power.isSummoned() && type != null) {
				type.forceUnsummon(user, power);
			}
			user.discard();
		}
	}

	private static void turn(Player user, float viewYaw, float bodyYaw) {
		user.setYRot(viewYaw);
		user.yRotO = viewYaw;
		user.setYHeadRot(viewYaw);
		user.yHeadRotO = viewYaw;
		user.yBodyRot = bodyYaw;
		user.yBodyRotO = bodyYaw;
	}

	// StandEntity.tick places a following Stand through updatePosition
	private static void assertIdleAt(GameTestHelper helper, String id, Player user, StandEntity stand,
			double dx, double dy, double dz, String when) {
		stand.updatePosition(user);
		Vec3 actual = stand.position().subtract(user.position());
		helper.assertTrue(stand.offsetFromUser.isIdle() && Math.abs(actual.x - dx) < EPS && Math.abs(actual.y - dy) < EPS
				&& Math.abs(actual.z - dz) < EPS,
				"1.16 put an idle " + id + " at its user's feet + (" + dx + ", " + dy + ", " + dz + ") with " + when
						+ ", got " + actual + " (idle " + stand.offsetFromUser.isIdle() + ", offset "
						+ stand.offsetFromUser.getRelativeOffset() + " " + stand.offsetFromUser.getRotations() + ")");
	}

	private static boolean near(float actual, float expected) {
		return Math.abs(Mth.wrapDegrees(actual - expected)) < 1.0E-3F;
	}
}
