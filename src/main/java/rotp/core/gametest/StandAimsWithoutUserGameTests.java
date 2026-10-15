package rotp.core.gametest;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * StandEntity.aimsWithoutUser: a Stand that puts a target of its own into its aim and starts its punches itself while
 * its user does not control it (an add-on Stand that hunts on its own; 1.16 gave such a task its own target) punches
 * that target, not what its user's crosshair meets. A plain Stand keeps its user's crosshair. Own batch: the rays
 * reach a few blocks.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandAimsWithoutUserGameTests {
	private StandAimsWithoutUserGameTests() {}

	// the punch's target (getFreshPunchTarget)
	@GameTest(template = "empty", timeoutTicks = 20, batch = "stand_aims_without_user_1")
	public static void aStandThatAimsOnItsOwnPunchesItsOwnTargetNotTheUsersCrosshair(GameTestHelper helper) {
		run(helper, 0);
	}

	// the target a punch is pressed at (pressTarget)
	@GameTest(template = "empty", timeoutTicks = 20, batch = "stand_aims_without_user_2")
	public static void aStandThatAimsOnItsOwnPressesItsPunchAtItsOwnTarget(GameTestHelper helper) {
		run(helper, 1);
	}

	// with nothing in its aim, its own eyes (aimWithThisOrUser), as under manual control
	@GameTest(template = "empty", timeoutTicks = 20, batch = "stand_aims_without_user_3")
	public static void aStandThatAimsOnItsOwnLooksWithItsOwnEyesWhenItsAimIsEmpty(GameTestHelper helper) {
		run(helper, 2);
	}

	private static void run(GameTestHelper helper, int path) {
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		StandPower power = null;
		Cow crosshair = null;
		Cow own = null;
		try {
			user.setNoGravity(true);
			user.moveTo(origin.x, origin.y, origin.z, 0, 0);
			user.setYHeadRot(0);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Fixture: could not add the Stand user");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Fixture: could not grant Star Platinum");
			crosshair = cow(helper, origin.add(0, 0, 2.0));
			own = cow(helper, origin.add(2.0, 0, 1.0));

			StandEntity plain = place(helper, user, power, type, new StandEntity(standEntityType(helper, user, power, type),
					helper.getLevel()), origin, own, crosshair);
			helper.assertTrue(StandEntityPunchAbility.getFreshPunchTarget(plain, ActionTarget.EMPTY, false).getMainEntity()
					== crosshair, "Fixture: a plain Stand's punch did not take its user's crosshair mob");

			StandEntity hunter = place(helper, user, power, type, new OwnAimStand(standEntityType(helper, user, power, type),
					helper.getLevel()), origin, own, crosshair);
			if (path == 0) {
				ActionTarget fresh = StandEntityPunchAbility.getFreshPunchTarget(hunter, ActionTarget.EMPTY, false);
				helper.assertTrue(fresh.getMainEntity() == own, "A Stand that aims on its own punched " + fresh
						+ " from its user's crosshair instead of the target in its own aim");
			}
			else if (path == 1) {
				ActionTarget pressed = StandEntityPunchAbility.pressTarget(hunter, ActionTarget.EMPTY);
				helper.assertTrue(pressed.getMainEntity() == own, "A punch a Stand that aims on its own starts was pressed at "
						+ pressed + " instead of the target in its own aim");
			}
			else {
				LivingComponentAction.getComponent(hunter).entityAim.setTarget(ActionTarget.EMPTY);
				ActionTarget looked = StandEntityPunchAbility.aimWithThisOrUser(hunter, user, 1.0F,
						entity -> entity != hunter && StandEntityPunchAbility.canStandHit(hunter, entity), false);
				helper.assertTrue(looked.getMainEntity() == own, "A Stand that aims on its own, with nothing in its aim,"
						+ " aimed " + looked + " with its user's crosshair instead of its own eyes");
			}
			helper.succeed();
		}
		finally {
			if (power != null && power.isSummoned() && type != null) {
				type.forceUnsummon(user, power);
			}
			if (crosshair != null) crosshair.discard();
			if (own != null) own.discard();
			user.discard();
		}
	}

	// what an add-on Stand that hunts on its own does
	private static final class OwnAimStand extends StandEntity {
		OwnAimStand(EntityType<? extends StandEntity> type, Level level) {
			super(type, level);
		}

		@Override
		public boolean aimsWithoutUser() {
			return true;
		}
	}

	private static Cow cow(GameTestHelper helper, Vec3 pos) {
		Cow cow = EntityType.COW.create(helper.getLevel());
		helper.assertTrue(cow != null, "Fixture: could not create a cow");
		cow.setNoAi(true);
		cow.setNoGravity(true);
		cow.moveTo(pos.x, pos.y, pos.z, 0, 0);
		helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Fixture: could not add a cow");
		return cow;
	}

	@SuppressWarnings("unchecked")
	private static EntityType<? extends StandEntity> standEntityType(GameTestHelper helper, Player user, StandPower power,
			StandType type) {
		if (!power.isSummoned()) {
			helper.assertTrue(type.summon(user, power), "Fixture: could not summon Star Platinum");
		}
		StandEntity summoned = power.getSummonedStandEntity();
		helper.assertTrue(summoned != null, "Fixture: the summoned Stand is missing");
		return (EntityType<? extends StandEntity>) summoned.getType();
	}

	// as EntityStandType.summon builds a Stand; it stands by its user, looks at its own target and has it in its aim,
	// while the user's synced crosshair is on the other cow
	private static StandEntity place(GameTestHelper helper, Player user, StandPower power, StandType type, StandEntity stand,
			Vec3 origin, Entity own, Entity crosshair) {
		if (power.isSummoned()) {
			type.forceUnsummon(user, power);
		}
		stand.withStandType(type);
		stand.refreshDimensions();
		stand.moveTo(origin.x + 0.6, origin.y, origin.z, 0, 0);
		power.setSummonedStand(stand);
		helper.assertTrue(helper.getLevel().addFreshEntity(stand), "Fixture: could not add the Stand");
		stand.setNoGravity(true);
		stand.moveTo(origin.x + 0.6, origin.y, origin.z, 0, 0);
		StandEntity.updateStandStatAttributes(stand, user);
		LivingComponentAction.getComponent(stand).setAction(null, user, SyncType.NO_SYNC);
		helper.assertTrue(stand.getUser() == user && !stand.isManuallyControlled(), "Fixture: the Stand is not its user's,"
				+ " or is manually controlled");
		stand.lookAt(EntityAnchorArgument.Anchor.EYES, own.getBoundingBox().getCenter());
		LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(own));
		LivingComponentAction.getComponent(user).entityAim.setTarget(new ActionTarget(crosshair));
		helper.assertTrue(StandEntityPunchAbility.validatePunchTarget(stand, new ActionTarget(own)).getMainEntity() == own
				&& StandEntityPunchAbility.validatePunchTarget(stand, new ActionTarget(crosshair)).getMainEntity() == crosshair,
				"Fixture: the Stand cannot reach both cows");
		return stand;
	}
}
