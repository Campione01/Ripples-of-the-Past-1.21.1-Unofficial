package rotp.core.gametest;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchChargedAbility.StandEntityChargedHeavy;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.StandPrecisionRay;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Husk;
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

	// the charged heavy punch's perform-start target without the aim assist: the Stand's own aim, not its user's view
	@GameTest(template = "empty", timeoutTicks = 20, batch = "stand_aims_without_user_4")
	public static void aStandThatAimsOnItsOwnStartsItsChargedHeavyPunchAtItsOwnTarget(GameTestHelper helper) {
		run(helper, 3);
	}

	// no user crosshair steers its punch, so it keeps its pressed target, as under manual control; its barrage aims again
	@GameTest(template = "empty", timeoutTicks = 20, batch = "stand_aims_without_user_5")
	public static void aStandThatAimsOnItsOwnKeepsItsAttacksPressedTarget(GameTestHelper helper) {
		run(helper, 4);
	}

	// 1.16 StandEntity.getAimDistance gave a Stand that is not manually controlled its user's reach (the 1.16 Purple Haze
	// hunted while mad with precisionRayTrace(this, its user's reach)), so a hostile mob on its look beyond its own reach
	// takes the precision ray's priority from the cow in reach, and the punch finds nothing it can reach
	@GameTest(template = "empty", timeoutTicks = 20, batch = "stand_aims_without_user_6")
	public static void aStandThatAimsOnItsOwnLooksAsFarAsItsUsersReachAsIn116(GameTestHelper helper) {
		run(helper, 5);
	}

	private static void run(GameTestHelper helper, int path) {
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		StandPower power = null;
		Cow crosshair = null;
		Cow own = null;
		Husk husk = null;
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
			EntityActionType heavyCharged = (EntityActionType) power.getAbility("heavy_charged");
			EntityActionType punch = (EntityActionType) power.getAbility("punch");
			EntityActionType barrage = (EntityActionType) power.getAbility("barrage");
			helper.assertTrue(heavyCharged != null && punch != null && barrage != null, "Fixture: a Star Platinum attack is missing");
			if (path == 3) {
				user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAimAssist = false;
				user.lookAt(EntityAnchorArgument.Anchor.EYES, crosshair.getBoundingBox().getCenter());
				ActionTarget plainStart = new ChargedHeavyProbe(heavyCharged).performStartTarget(plain);
				helper.assertTrue(plainStart.getMainEntity() == crosshair, "Fixture: without the aim assist a plain Stand's"
						+ " charged heavy punch did not start at its user's crosshair mob: " + plainStart);
			}
			else if (path == 4) {
				user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock = false;
				helper.assertTrue(!StandEntityPunchAbility.shouldRetainPunchTarget(plain, punch)
						&& !StandEntityPunchAbility.shouldRetainPunchTarget(plain, barrage),
						"Fixture: with keep attack target off a plain Stand's attacks keep their pressed target");
			}

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
			else if (path == 2) {
				LivingComponentAction.getComponent(hunter).entityAim.setTarget(ActionTarget.EMPTY);
				ActionTarget looked = StandEntityPunchAbility.aimWithThisOrUser(hunter, user, 1.0F,
						entity -> entity != hunter && StandEntityPunchAbility.canStandHit(hunter, entity), false);
				helper.assertTrue(looked.getMainEntity() == own, "A Stand that aims on its own, with nothing in its aim,"
						+ " aimed " + looked + " with its user's crosshair instead of its own eyes");
			}
			else if (path == 3) {
				// turned away from its target, as a Stand that has just put it into its aim: only that aim can find it
				hunter.setYRot(hunter.getYRot() + 180);
				hunter.setYHeadRot(hunter.getYRot());
				ActionTarget hunterStart = new ChargedHeavyProbe(heavyCharged).performStartTarget(hunter);
				helper.assertTrue(hunterStart.getMainEntity() == own, "Without the aim assist the charged heavy punch of a"
						+ " Stand that aims on its own started at " + hunterStart + " instead of the target in its own aim");
			}
			else if (path == 4) {
				helper.assertTrue(StandEntityPunchAbility.shouldRetainPunchTarget(hunter, punch), "The punch of a Stand that"
						+ " aims on its own follows its user's crosshair instead of keeping its pressed target");
				// the 1.16 Purple Haze's madness gave its barrage the target, which StandEntityMeleeBarrage.checkStandTarget
				// refused, so each hit aimed again from the Stand's eyes (PurpleHazeEntity.aimWithThisOrUser)
				helper.assertTrue(!StandEntityPunchAbility.shouldRetainPunchTarget(hunter, barrage), "The barrage of a Stand"
						+ " that aims on its own keeps its pressed target; 1.16 aimed every barrage hit again");
			}
			else {
				LivingComponentAction.getComponent(hunter).entityAim.setTarget(ActionTarget.EMPTY);
				Vec3 eyes = hunter.getEyePosition();
				Vec3 beyond = eyes.add(own.getBoundingBox().getCenter().subtract(eyes).normalize().scale(4.6));
				husk = EntityType.HUSK.create(helper.getLevel());
				helper.assertTrue(husk != null, "Fixture: could not create a husk");
				husk.setNoAi(true);
				husk.setNoGravity(true);
				husk.moveTo(beyond.x, beyond.y - husk.getBbHeight() / 2, beyond.z, 0, 0);
				helper.assertTrue(helper.getLevel().addFreshEntity(husk), "Fixture: could not add a husk");
				helper.assertTrue(StandEntityPunchAbility.validatePunchTarget(hunter, new ActionTarget(husk)).isEmpty(
						helper.getLevel()) && husk.getBoundingBox().clip(eyes, eyes.add(hunter.getLookAngle().scale(5)))
						.isPresent(), "Fixture: the husk is not on the Stand's look beyond its reach");
				ActionTarget looked = StandEntityPunchAbility.aimWithThisOrUser(hunter, user, 1.0F,
						entity -> entity != hunter && StandEntityPunchAbility.canStandHit(hunter, entity), false);
				Husk beyondReach = husk;
				helper.assertTrue(looked.isEmpty(helper.getLevel()), "A Stand that aims on its own looked only as far as its"
						+ " own reach and took " + looked + "; 1.16 looked as far as its user's reach, where the hostile mob"
						+ " beyond the Stand's reach comes first and is out of reach (precision " + hunter.getPrecision()
						+ ", own reach " + hunter.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + ", cow at "
						+ Math.sqrt(own.getBoundingBox().distanceToSqr(eyes)) + ", husk at "
						+ Math.sqrt(beyondReach.getBoundingBox().distanceToSqr(eyes)) + ", ray within its reach "
						+ StandPrecisionRay.clipTargets(hunter, hunter.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE),
								entity -> entity != hunter && entity != user, 0, hunter.getPrecision(), true) + ")");
			}
			helper.succeed();
		}
		finally {
			if (power != null && power.isSummoned() && type != null) {
				type.forceUnsummon(user, power);
			}
			if (crosshair != null) crosshair.discard();
			if (own != null) own.discard();
			if (husk != null) husk.discard();
			user.discard();
		}
	}

	// reads the charged heavy punch's perform-start target, as actionPerformStart does
	private static final class ChargedHeavyProbe extends StandEntityChargedHeavy {
		ChargedHeavyProbe(EntityActionType ability) {
			super(ability);
		}

		ActionTarget performStartTarget(StandEntity stand) {
			capturePerformTarget(stand);
			return getActionTargetSnapshot(stand.level());
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
