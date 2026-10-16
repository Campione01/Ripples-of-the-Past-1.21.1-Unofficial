package rotp.core.gametest;

import java.util.List;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityAutoBlockAction;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
 * <p>
 * StandEntity.updatePosition measured every offset, an action's too, from the user's feet whatever the Stand's size.
 * getAbsoluteVec lowered an arms-only offset by 0.85 of the height the user's pose takes off its standing height, and
 * then scaled any offset by the user's bounding box against its type's size, across by the width and up by the
 * height: a crouching player's y by 1.5 / 1.8, a swimming one's by 0.6 / 1.8, a sleeping or dying one's offset to a
 * third across and a ninth up.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandIdlePlacementGameTests {
	private static final List<String> CORE_ENTITY_STANDS = List.of("star_platinum", "the_world", "hierophant_green",
			"silver_chariot", "magicians_red", "crazy_diamond", "gold_experience");
	private static final double EPS = 1.0E-4D;
	// a player's bounding box in a pose over its type's 0.6 by 1.8
	private static final double CROUCHING_UP = 1.5D / 1.8D;
	private static final double SWIMMING_UP = 0.6D / 1.8D;
	private static final double LYING_UP = 0.2D / 1.8D;
	private static final double LYING_ACROSS = 0.2D / 0.6D;

	private StandIdlePlacementGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void everyCoreStandIdlesBehindTheRightOfItsUsersViewAsIn116(GameTestHelper helper) {
		for (String id : CORE_ENTITY_STANDS) {
			withSummonedStand(helper, id, StandIdlePlacementGameTests::checkIdlePlacement);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void everyCoreStandFollowsFromItsUsersFeetAsIn116(GameTestHelper helper) {
		for (String id : CORE_ENTITY_STANDS) {
			withSummonedStand(helper, id, StandIdlePlacementGameTests::checkFeetRelativePlacement);
		}
		helper.succeed();
	}

	/*
	 * 1.16 HierophantGreenEmeraldSplash was built with standOffsetFront(), StandRelativeOffset.noYOffset(0, 0.5), and
	 * its getOffsetFromUser gave the idle offset in full mode outside PERFORM; it had no arms-only offset
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void emeraldSplashStandsInFrontOnlyWhileItFiresInFullModeAsIn116(GameTestHelper helper) {
		withSummonedStand(helper, "hierophant_green", StandIdlePlacementGameTests::checkFullModeEmeraldSplash);
		withSummonedStand(helper, "hierophant_green", StandIdlePlacementGameTests::checkArmsOnlyEmeraldSplash);
		helper.succeed();
	}

	private interface StandCheck {
		void check(GameTestHelper helper, String id, Player user, PlayerClientBroadcastedSettings settings,
				StandPower power, StandEntity stand);
	}

	private static void withSummonedStand(GameTestHelper helper, String id, StandCheck check) {
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
			check.check(helper, id, user, settings, power, stand);
		}
		finally {
			if (power != null && power.isSummoned() && type != null) {
				type.forceUnsummon(user, power);
			}
			user.discard();
		}
	}

	private static void checkIdlePlacement(GameTestHelper helper, String id, Player user,
			PlayerClientBroadcastedSettings settings, StandPower power, StandEntity stand) {
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

	private static void checkFeetRelativePlacement(GameTestHelper helper, String id, Player user,
			PlayerClientBroadcastedSettings settings, StandPower power, StandEntity stand) {
		turn(user, 0.0F, 50.0F);
		user.setPose(Pose.SWIMMING);
		assertIdleAt(helper, id, user, stand, -0.75D, 0.2D * SWIMMING_UP, -0.75D,
				"view yaw 0, a swimming user (0.6 high, eyes at " + user.getEyeHeight() + ")");
		for (Pose lying : List.of(Pose.SLEEPING, Pose.DYING)) {
			user.setPose(lying);
			assertIdleAt(helper, id, user, stand, -0.75D * LYING_ACROSS, 0.2D * LYING_UP, -0.75D * LYING_ACROSS,
					"view yaw 0, a " + lying + " user (0.2 wide and high)");
		}
		user.setPose(Pose.STANDING);

		// LivingEntity.tick applies a new scale; nothing ticks here
		AttributeInstance standScale = stand.getAttribute(Attributes.SCALE);
		standScale.setBaseValue(0.5D);
		stand.refreshDimensions();
		helper.assertTrue(Math.abs(stand.getEyeHeight() - 0.81F) < EPS,
				"setup: the half-size " + id + " has eye height " + stand.getEyeHeight());
		assertIdleAt(helper, id, user, stand, -0.75D, 0.2D, -0.75D,
				"view yaw 0, a half-size Stand (eye height " + stand.getEyeHeight() + ")");
		standScale.setBaseValue(1.0D);
		stand.refreshDimensions();

		checkArmsOnlyIdle(helper, id, user, stand);
		checkHeldGuard(helper, id, user, power, stand);
	}

	// 1.16 StandEntity.offsetDefaultArmsOnly, StandRelativeOffset.withYOffset(0, 0, 0.15)
	private static void checkArmsOnlyIdle(GameTestHelper helper, String id, Player user, StandEntity stand) {
		stand.setArmsOnlyMode();
		helper.assertTrue(stand.isArmsOnlyMode() && stand.offsetFromUser.isIdle()
				&& new Vec3(0, 0, 0.15).equals(stand.offsetFromUser.getRelativeOffset()),
				"setup: the arms-only " + id + " is not at its arms-only idle offset: " + stand.offsetFromUser.getRelativeOffset());
		String what = "an idle arms-only " + id;
		turn(user, 0.0F, 50.0F);
		assertAt(helper, "1.16 put " + what, true, user, stand, 0.0D, 0.0D, 0.15D, "view yaw 0, a standing user");

		// a crouch without the sneak key, as under a 1.5 high ceiling
		user.setPose(Pose.CROUCHING);
		assertAt(helper, "1.16 put " + what, true, user, stand, 0.0D, -0.2125D, 0.15D,
				"view yaw 0, a crouching user that does not sneak (the drop 0.85 * (1.5 - 1.8), times 1.5 / 1.8)");
		turn(user, 90.0F, 50.0F);
		assertAt(helper, "1.16 put " + what, true, user, stand, -0.15D, -0.2125D, 0.0D,
				"view yaw 90, a crouching user that does not sneak (the drop 0.85 * (1.5 - 1.8), times 1.5 / 1.8)");
		turn(user, 0.0F, 50.0F);
		user.setShiftKeyDown(true);
		assertAt(helper, "1.16 put " + what, true, user, stand, 0.0D, 0.0D, 0.15D,
				"view yaw 0, a sneaking user (taskOffset dropped the whole y, the arms-only drop too)");
		user.setShiftKeyDown(false);
		user.setPose(Pose.SWIMMING);
		assertAt(helper, "1.16 put " + what, true, user, stand, 0.0D, -0.34D, 0.15D,
				"view yaw 0, a swimming user (the drop 0.85 * (0.6 - 1.8), times 0.6 / 1.8)");
		user.setPose(Pose.STANDING);

		stand.setArmsOnlyMode(false);
		helper.assertTrue(!stand.isArmsOnlyMode() && stand.offsetFromUser.isIdle(),
				"setup: the " + id + " summoned in full again is not at its idle offset: " + stand.offsetFromUser.getRelativeOffset());
	}

	// 1.16 StandEntityBlock.standOffsetFromUser(0, 0.3); with a task the sneak rule left the offset's y alone
	private static void checkHeldGuard(GameTestHelper helper, String id, Player user, StandPower power, StandEntity stand) {
		helper.assertTrue(power.getAbility("guard") instanceof EntityActionType, "Missing the guard of " + id);
		EntityActionType guardType = (EntityActionType) power.getAbility("guard");
		EntityActionInstance held = guardType.createActionObj();
		guardType.initActionFromConfig(held, helper.getLevel(), user, stand);
		held.phasesLength.put(ActionPhase.BUTTON_CHARGE, 0F);
		held.phasesLength.put(ActionPhase.WINDUP, 0F);
		held.phasesLength.put(ActionPhase.PERFORM, 40F);
		held.setStartingPhase();
		LivingComponentAction.getComponent(stand).setAction(held, user, SyncType.NO_SYNC);
		helper.assertTrue(stand.getCurStandAction() == held && stand.isFollowingUser()
				&& new Vec3(0, stand.Y_OFFSET, 0.3).equals(stand.offsetFromUser.getRelativeOffset()),
				"setup: the held guard of " + id + " did not set its offset: " + stand.offsetFromUser.getRelativeOffset());
		turn(user, 0.0F, 50.0F);
		user.setPose(Pose.CROUCHING);
		user.setShiftKeyDown(true);
		assertAt(helper, "1.16 put the guarding " + id, false, user, stand, 0.0D, 0.2D * CROUCHING_UP, 0.3D,
				"view yaw 0, a crouching sneaking user (1.5 high, eyes at " + user.getEyeHeight() + ")");
		user.setShiftKeyDown(false);
		user.setPose(Pose.STANDING);

		// 1.16 BLOCK_STAND_ENTITY gave no offset, and a task without one showed the default offset
		EntityActionType autoGuard = StandEntityAutoBlockAction.get();
		EntityActionInstance noOffset = autoGuard.createActionObj();
		autoGuard.initActionFromConfig(noOffset, helper.getLevel(), user, stand);
		noOffset.phasesLength.put(ActionPhase.PERFORM, 5F);
		noOffset.setStartingPhase();
		LivingComponentAction.getComponent(stand).setAction(noOffset, user, SyncType.NO_SYNC);
		helper.assertTrue(stand.getCurStandAction() == noOffset, "setup: the action without an offset did not replace the guard of " + id);
		assertAt(helper, "1.16 put a " + id + " whose guard gave way to an action without an offset", true, user, stand,
				-0.75D, 0.2D, -0.75D, "view yaw 0 (the default offset, not the guard's leftover)");
	}

	private static void checkFullModeEmeraldSplash(GameTestHelper helper, String id, Player user,
			PlayerClientBroadcastedSettings settings, StandPower power, StandEntity stand) {
		EntityActionInstance splash = startEmeraldSplash(helper, id, user, power, stand);
		// looking down: an offset the pitch turned too would sink in front of the user
		turn(user, 0.0F, 50.0F);
		user.setXRot(45.0F);
		user.xRotO = 45.0F;
		assertAt(helper, "1.16 kept a " + id + " winding up its Emerald Splash", true, user, stand, -0.75D, 0.2D, -0.75D,
				"view yaw 0, pitch 45 (the idle offset)");
		user.setPose(Pose.CROUCHING);
		user.setShiftKeyDown(true);
		assertAt(helper, "1.16 kept a " + id + " winding up its Emerald Splash", true, user, stand,
				-0.75D, 0.2D * CROUCHING_UP, -0.75D,
				"view yaw 0, a crouching sneaking user (taskOffset dropped the y only with no task)");
		user.setShiftKeyDown(false);
		user.setPose(Pose.STANDING);
		splash.setPhase(ActionPhase.PERFORM, 0);
		assertAt(helper, "1.16 put a " + id + " firing its Emerald Splash", false, user, stand, 0.0D, 0.2D, 0.5D,
				"view yaw 0, pitch 45 (noYOffset(0, 0.5), turned by the yaw alone)");
		splash.setPhase(ActionPhase.RECOVERY, 0);
		assertAt(helper, "1.16 put a " + id + " recovering from its Emerald Splash", true, user, stand, -0.75D, 0.2D, -0.75D,
				"view yaw 0, pitch 45 (the idle offset)");
		user.setXRot(0.0F);
		user.xRotO = 0.0F;
	}

	private static void checkArmsOnlyEmeraldSplash(GameTestHelper helper, String id, Player user,
			PlayerClientBroadcastedSettings settings, StandPower power, StandEntity stand) {
		stand.setArmsOnlyMode();
		EntityActionInstance splash = startEmeraldSplash(helper, id, user, power, stand);
		turn(user, 0.0F, 50.0F);
		String what = "an arms-only " + id + " with its Emerald Splash";
		assertAt(helper, "1.16 kept " + what, true, user, stand, 0.0D, 0.0D, 0.15D, "view yaw 0, in the windup");
		splash.setPhase(ActionPhase.PERFORM, 0);
		assertAt(helper, "1.16 kept " + what, true, user, stand, 0.0D, 0.0D, 0.15D, "view yaw 0, firing");
		splash.setPhase(ActionPhase.RECOVERY, 0);
		assertAt(helper, "1.16 kept " + what, true, user, stand, 0.0D, 0.0D, 0.15D, "view yaw 0, in the recovery");
		stand.setArmsOnlyMode(false);
	}

	private static EntityActionInstance startEmeraldSplash(GameTestHelper helper, String id, Player user, StandPower power,
			StandEntity stand) {
		helper.assertTrue(power.getAbility("emerald_splash") instanceof EntityActionType, "Missing the Emerald Splash of " + id);
		EntityActionType splashType = (EntityActionType) power.getAbility("emerald_splash");
		EntityActionInstance splash = splashType.createActionObj();
		splashType.initActionFromConfig(splash, helper.getLevel(), user, stand);
		splash.phasesLength.put(ActionPhase.BUTTON_CHARGE, 0F);
		splash.phasesLength.put(ActionPhase.WINDUP, 10F);
		splash.phasesLength.put(ActionPhase.PERFORM, 30F);
		splash.phasesLength.put(ActionPhase.RECOVERY, 40F);
		splash.setStartingPhase();
		LivingComponentAction.getComponent(stand).setAction(splash, user, SyncType.NO_SYNC);
		helper.assertTrue(stand.getCurStandAction() == splash && splash.getPhase() == ActionPhase.WINDUP && stand.isFollowingUser(),
				"setup: the Emerald Splash of " + id + " is not in its windup with the Stand following: " + splash.getPhase());
		return splash;
	}

	private static void turn(Player user, float viewYaw, float bodyYaw) {
		user.setYRot(viewYaw);
		user.yRotO = viewYaw;
		user.setYHeadRot(viewYaw);
		user.yHeadRotO = viewYaw;
		user.yBodyRot = bodyYaw;
		user.yBodyRotO = bodyYaw;
	}

	private static void assertIdleAt(GameTestHelper helper, String id, Player user, StandEntity stand,
			double dx, double dy, double dz, String when) {
		assertAt(helper, "1.16 put an idle " + id, true, user, stand, dx, dy, dz, when);
	}

	// StandEntity.tick places a following Stand through updatePosition
	private static void assertAt(GameTestHelper helper, String claim, boolean idle, Player user, StandEntity stand,
			double dx, double dy, double dz, String when) {
		stand.updatePosition(user);
		Vec3 actual = stand.position().subtract(user.position());
		helper.assertTrue(stand.offsetFromUser.isIdle() == idle && Math.abs(actual.x - dx) < EPS
				&& Math.abs(actual.y - dy) < EPS && Math.abs(actual.z - dz) < EPS,
				claim + " at its user's feet + (" + dx + ", " + dy + ", " + dz + ") with " + when
						+ ", got " + actual + " (idle " + stand.offsetFromUser.isIdle() + ", offset "
						+ stand.offsetFromUser.getRelativeOffset() + " " + stand.offsetFromUser.getRotations() + ")");
	}

	private static boolean near(float actual, float expected) {
		return Math.abs(Mth.wrapDegrees(actual - expected)) < 1.0E-3F;
	}
}
