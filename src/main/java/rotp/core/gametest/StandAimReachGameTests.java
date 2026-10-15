package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility.StandEntityBarrage;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.StandEntityHeavyPunch;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility.StandEntityPunch;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntity.punch / aimWithThisOrUser: an attack with no target in the Stand's reach aims from the user's eyes
 * at the user's 5-block reach with the Stand's precision ray (precisionRayTrace) and keeps what the Stand, moved
 * forward, can reach. The owner restored that aim assist for the first punch and the default barrage (2026-10-14) and
 * kept walls blocking it. The heavy punch's Stand keeps its distance from its target through the windup. Own batch:
 * the rays reach several blocks and must not meet a neighbouring test's mobs.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandAimReachGameTests {
	private static final String BATCH = "stand_aim_reach";
	private static final short KEY = 171;

	private StandAimReachGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 60, batch = BATCH)
	public static void firstPunchFromIdleReachesTheDonorDistance(GameTestHelper helper) {
		Scene scene = new Scene(helper, GameType.SURVIVAL, false, 16);
		Cow cow = scene.cow(0, 4.2);
		scene.user.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, cow.getBoundingBox().getCenter());
		helper.assertTrue(cow.getBoundingBox().distanceToSqr(scene.user.getEyePosition()) > 3.5 * 3.5,
				"Fixture: the cow is not past the vanilla and idle Stand aims");
		float health = cow.getHealth();
		scene.run(tick -> {
			if (tick == 3) {
				scene.clearAims();
				EntityActionInstance action = scene.press("punch", InputMethod.CLICK);
				helper.assertTrue(action instanceof StandEntityPunch, "The punch input started " + action);
			}
			else if (tick > 3 && cow.getHealth() < health) {
				return true;
			}
			else if (tick > 25) {
				helper.fail("The first punch from idle whiffed at a cow 4.2 blocks away, which 1.16 reached");
			}
			return false;
		});
	}

	@GameTest(template = "empty", timeoutTicks = 60, batch = BATCH)
	public static void heavyWindupFollowsARetreatingTarget(GameTestHelper helper) {
		Scene scene = new Scene(helper, GameType.CREATIVE, true, 8);
		Cow cow = scene.cow(0, 2.6);
		EntityActionInstance[] action = new EntityActionInstance[1];
		scene.run(tick -> {
			if (tick == 3) {
				LivingComponentAction.getComponent(scene.stand).entityAim.setTarget(new ActionTarget(cow));
				action[0] = scene.press("heavy_punch", InputMethod.CLICK);
				helper.assertTrue(action[0] instanceof StandEntityHeavyPunch, "The heavy input started " + action[0]);
			}
			else if (tick == 5) {
				helper.assertTrue(action[0].getPhase() == ActionPhase.WINDUP, "Fixture: the windup ended early");
				scene.assertOffset(action[0], 2.6 - 1.45, "start of the windup");
				cow.moveTo(cow.getX(), cow.getY(), cow.getZ() + 1.0, 0, 0);
			}
			else if (tick == 7) {
				helper.assertTrue(action[0].getPhase() == ActionPhase.WINDUP, "Fixture: the windup ended early");
				scene.assertOffset(action[0], 2.0, "after the target stepped back (upper clamp 2)");
				return true;
			}
			return false;
		});
	}

	@GameTest(template = "empty", timeoutTicks = 60, batch = BATCH)
	public static void defaultBarrageAimsWithTheUserReach(GameTestHelper helper) {
		Scene scene = new Scene(helper, GameType.CREATIVE, false, 16);
		// on the crosshair, past the old 2.5-block eye ray, inside the moved Stand's reach
		Cow cow = scene.cow(0, 3.4);
		scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, cow.getBoundingBox().getCenter());
		float health = cow.getHealth();
		scene.run(tick -> {
			if (tick == 3) {
				scene.clearAims();
				EntityActionInstance action = scene.press("barrage", InputMethod.HOLD);
				helper.assertTrue(StandEntityBarrageAbility.isDirectionalBarrage(scene.stand, action),
						"Fixture: the default barrage is not the unlocked directional one");
			}
			else if (tick > 3 && cow.getHealth() < health) {
				return true;
			}
			else if (tick > 30) {
				helper.fail("The default barrage missed a cow 3.4 blocks ahead on the crosshair, which 1.16 hit");
			}
			return false;
		});
	}

	// 1.16 StandEntityMeleeBarrage hit through StandEntity.punch -> aimWithThisOrUser -> precisionRayTrace: Star
	// Platinum's precision boxes (about 1.9 blocks each side) take a mob this close whichever way the user looks
	@GameTest(template = "empty", timeoutTicks = 60, batch = BATCH)
	public static void defaultBarrageHitsAMobBesideTheCrosshair(GameTestHelper helper) {
		Scene scene = new Scene(helper, GameType.CREATIVE, false, 16);
		Cow cow = scene.besideTheCrosshair();
		float health = cow.getHealth();
		scene.run(tick -> {
			if (tick == 3) {
				scene.clearAims();
				EntityActionInstance action = scene.press("barrage", InputMethod.HOLD);
				helper.assertTrue(StandEntityBarrageAbility.isDirectionalBarrage(scene.stand, action),
						"Fixture: the default barrage is not the unlocked directional one");
			}
			else if (tick > 3 && cow.getHealth() < health) {
				return true;
			}
			else if (tick > 30) {
				helper.fail("The default barrage missed a cow beside the crosshair within 1.9 blocks of the user, which"
						+ " 1.16's precision aim took");
			}
			return false;
		});
	}

	// 1.16 StandEntityLightAttack hit through the same StandEntity.punch -> aimWithThisOrUser -> precisionRayTrace
	@GameTest(template = "empty", timeoutTicks = 60, batch = BATCH)
	public static void firstPunchHitsAMobBesideTheCrosshair(GameTestHelper helper) {
		Scene scene = new Scene(helper, GameType.SURVIVAL, false, 16);
		Cow cow = scene.besideTheCrosshair();
		float health = cow.getHealth();
		scene.run(tick -> {
			if (tick == 3) {
				scene.clearAims();
				EntityActionInstance action = scene.press("punch", InputMethod.CLICK);
				helper.assertTrue(action instanceof StandEntityPunch, "The punch input started " + action);
			}
			else if (tick > 3 && cow.getHealth() < health) {
				return true;
			}
			else if (tick > 25) {
				helper.fail("The first punch from idle missed a cow beside the crosshair within 1.9 blocks of the user,"
						+ " which 1.16's precision aim took");
			}
			return false;
		});
	}

	// the owner kept walls blocking the aim (2026-10-14): bedrock right ahead hides the cow the precision boxes reach
	@GameTest(template = "empty", timeoutTicks = 60, batch = BATCH)
	public static void defaultBarrageDoesNotHitAMobBesideTheCrosshairBehindAWall(GameTestHelper helper) {
		Scene scene = new Scene(helper, GameType.CREATIVE, false, 16);
		Cow cow = scene.besideTheCrosshair();
		scene.wallAhead();
		float health = cow.getHealth();
		scene.run(tick -> {
			if (tick == 3) {
				scene.clearAims();
				EntityActionInstance action = scene.press("barrage", InputMethod.HOLD);
				helper.assertTrue(StandEntityBarrageAbility.isDirectionalBarrage(scene.stand, action),
						"Fixture: the default barrage is not the unlocked directional one");
			}
			else if (tick > 3 && cow.getHealth() < health) {
				helper.fail("The default barrage hit a cow behind the bedrock; walls block the aim");
			}
			else if (tick > 25) {
				helper.assertTrue(scene.stand.getCurStandAction() instanceof StandEntityBarrage,
						"Fixture: the barrage ended before the check");
				return true;
			}
			return false;
		});
	}

	@GameTest(template = "empty", timeoutTicks = 60, batch = BATCH)
	public static void firstPunchDoesNotHitAMobBesideTheCrosshairBehindAWall(GameTestHelper helper) {
		Scene scene = new Scene(helper, GameType.SURVIVAL, false, 16);
		Cow cow = scene.besideTheCrosshair();
		scene.wallAhead();
		float health = cow.getHealth();
		EntityActionInstance[] punch = new EntityActionInstance[1];
		scene.run(tick -> {
			if (tick == 3) {
				scene.clearAims();
				punch[0] = scene.press("punch", InputMethod.CLICK);
				helper.assertTrue(punch[0] instanceof StandEntityPunch, "The punch input started " + punch[0]);
			}
			else if (tick > 3 && cow.getHealth() < health) {
				helper.fail("The first punch hit a cow behind the bedrock; walls block the aim");
			}
			else if (tick > 25) {
				helper.assertTrue(punch[0].punchedTarget != null
								&& punch[0].punchedTarget.getType() == ActionTarget.TargetType.BLOCK,
						"Fixture: the punch did not strike the bedrock: " + punch[0].punchedTarget);
				return true;
			}
			return false;
		});
	}

	// 1.16 REACH_DISTANCE was 5 in Survival and Creative alike; a 1.21 server player gets half a block more in Creative
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH)
	public static void userAimReachIsFiveBlocksInEitherGameMode(GameTestHelper helper) {
		Scene scene = new Scene(helper, GameType.SURVIVAL, false, 16);
		try {
			AttributeInstance range = scene.user.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
			double survival = StandEntityPunchAbility.userAimReach(scene.user, scene.stand);
			range.addOrUpdateTransientModifier(new AttributeModifier(
					ResourceLocation.withDefaultNamespace("creative_mode_block_range"), 0.5, AttributeModifier.Operation.ADD_VALUE));
			double creative = StandEntityPunchAbility.userAimReach(scene.user, scene.stand);
			helper.assertTrue(Math.abs(survival - 5) < 1.0E-6 && Math.abs(creative - 5) < 1.0E-6,
					"1.16 aimed at the user's 5-block reach in either game mode: Survival " + survival + ", Creative " + creative);
			helper.succeed();
		}
		finally {
			scene.close();
		}
	}

	// the 1.16 precision aim with the owner's walls: an entity is measured by its own box, even one so close that its
	// precision box holds the user's eyes
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH)
	public static void precisionAimDoesNotPickAMobBehindAWall(GameTestHelper helper) {
		Scene scene = new Scene(helper, GameType.SURVIVAL, false, 16);
		BlockPos wall = BlockPos.containing(scene.origin.add(0, 1, 1));
		BlockState oldWall = helper.getLevel().getBlockState(wall);
		try {
			helper.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
			Cow cow = scene.cow(0, 1.9);
			helper.assertTrue(cow.getBoundingBox().distanceToSqr(scene.user.getEyePosition()) < 1.9 * 1.9,
					"Fixture: the cow is not within 1.9 blocks of the user's eyes");
			ActionTarget aimed = StandEntityPunchAbility.userPrecisionRayTarget(scene.stand, scene.user);
			helper.assertTrue(aimed.getType() == ActionTarget.TargetType.BLOCK && aimed.getBlockPos().equals(wall),
					"The precision aim picked " + aimed + " behind the stone at " + wall + "; walls hide entities");
			helper.succeed();
		}
		finally {
			helper.getLevel().setBlockAndUpdate(wall, oldWall);
			scene.close();
		}
	}

	// 1.16 StandEntity.aimWithThisOrUser: a punch and an action that is not one (an add-on's bomb or erase) both aimed
	// with the precision ray
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH)
	public static void punchAndNonPunchActionAimWithThePrecisionRay(GameTestHelper helper) {
		Scene scene = new Scene(helper, GameType.SURVIVAL, false, 16);
		LivingComponentAction component = LivingComponentAction.getComponent(scene.stand);
		try {
			Cow cow = scene.besideTheCrosshair();
			scene.clearAims();
			component.setAction(action(helper, scene, "punch"), scene.user, SyncType.NO_SYNC);
			ActionTarget punch = StandEntityPunchAbility.getFreshPunchTarget(scene.stand, ActionTarget.EMPTY);
			helper.assertTrue(punch.getMainEntity() == cow,
					"1.16 aimed the light punch with the precision ray, which takes the cow beside the crosshair; got "
							+ punch);
			component.setAction(action(helper, scene, "star_finger"), scene.user, SyncType.NO_SYNC);
			ActionTarget finger = StandEntityPunchAbility.getFreshPunchTarget(scene.stand, ActionTarget.EMPTY);
			helper.assertTrue(finger.getMainEntity() == cow,
					"1.16 aimed an action that is not a punch with the precision ray, which takes the cow beside the"
							+ " crosshair; got " + finger);
			helper.succeed();
		}
		finally {
			component.setAction(null, scene.user, SyncType.NO_SYNC);
			scene.close();
		}
	}

	// 1.16 EntityStandType.clientHitResult sent the vanilla pick, the cow on the crosshair, and aimWithThisOrUser kept it
	// while the Stand could reach it. A client also sends its Stand aim, a precision clip that takes the nearer cow.
	@GameTest(template = "empty", timeoutTicks = 60, batch = BATCH)
	public static void firstPunchTakesTheCrosshairMobOverTheStandAim(GameTestHelper helper) {
		crosshairOverStandAim(helper, "punch", InputMethod.CLICK, false);
	}

	@GameTest(template = "empty", timeoutTicks = 60, batch = BATCH)
	public static void heavyPunchTakesTheCrosshairMobOverTheStandAim(GameTestHelper helper) {
		crosshairOverStandAim(helper, "heavy_punch", InputMethod.CLICK, false);
	}

	@GameTest(template = "empty", timeoutTicks = 60, batch = BATCH)
	public static void defaultBarrageTakesTheCrosshairMobOverTheStandAim(GameTestHelper helper) {
		crosshairOverStandAim(helper, "barrage", InputMethod.HOLD, false);
	}

	// the target lock keeps the target of the press, which 1.16 took from the crosshair too
	@GameTest(template = "empty", timeoutTicks = 60, batch = BATCH)
	public static void lockedBarrageKeepsTheCrosshairMobOverTheStandAim(GameTestHelper helper) {
		crosshairOverStandAim(helper, "barrage", InputMethod.HOLD, true);
	}

	private static void crosshairOverStandAim(GameTestHelper helper, String name, InputMethod method, boolean locked) {
		Scene scene = new Scene(helper, GameType.SURVIVAL, locked, 16);
		Cow beside = scene.besideTheCrosshair();
		Cow crosshair = scene.cow(0, 2.0);
		scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, crosshair.getBoundingBox().getCenter());
		Vec3 eyes = scene.user.getEyePosition();
		helper.assertTrue(beside.getBoundingBox().clip(eyes, eyes.add(scene.user.getViewVector(1).scale(5))).isEmpty(),
				"Fixture: the cow beside is on the crosshair");
		helper.assertTrue(StandEntityPunchAbility.validatePunchTarget(scene.stand, new ActionTarget(crosshair)).getMainEntity()
				== crosshair, "Fixture: the cow on the crosshair is not within the Stand's reach");
		float crosshairHealth = crosshair.getHealth();
		float besideHealth = beside.getHealth();
		scene.run(tick -> {
			if (tick == 3) {
				// the aims a client sends with the press, through the packet handlers' own step
				LivingComponentAction.getComponent(scene.stand).entityAim.setTargetFromClient(
						new ActionTarget(beside), helper.getLevel());
				LivingComponentAction.getComponent(scene.user).entityAim.setTargetFromClient(
						new ActionTarget(crosshair), helper.getLevel());
				EntityActionInstance action = scene.press(name, method);
				helper.assertTrue(action != null && (!name.equals("barrage") || locked
						!= StandEntityBarrageAbility.isDirectionalBarrage(scene.stand, action)),
						"Fixture: the " + name + " input started " + action);
			}
			else if (tick > 3 && beside.getHealth() < besideHealth) {
				helper.fail("The " + name + (locked ? " with the target lock" : "") + " hit the cow beside the crosshair,"
						+ " the Stand's synced aim; 1.16 hit the cow on the crosshair the user aimed at");
			}
			else if (tick > 3 && crosshair.getHealth() < crosshairHealth) {
				return true;
			}
			else if (tick > 40) {
				helper.fail("The " + name + " never hit the cow on the crosshair");
			}
			return false;
		});
	}

	// 1.16 StandEntity.aimWithThisOrUser: when the user's precision ray met nothing within its reach, the Stand's own
	// precision ray from its eyes; the owner's walls block that one too
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH)
	public static void punchFallsBackToTheStandsOwnPrecisionRayWhenTheUsersMeetsNothing(GameTestHelper helper) {
		Scene scene = new Scene(helper, GameType.SURVIVAL, false, 16);
		LivingComponentAction component = LivingComponentAction.getComponent(scene.stand);
		try {
			Cow cow = scene.cow(0, 2.0);
			scene.user.moveTo(scene.origin.x, scene.origin.y, scene.origin.z - 3, 0, -90);
			scene.stand.lookAt(EntityAnchorArgument.Anchor.EYES, cow.getBoundingBox().getCenter());
			ActionTarget userRay = StandEntityPunchAbility.userPrecisionRayTarget(scene.stand, scene.user);
			helper.assertTrue(userRay.getType() == ActionTarget.TargetType.EMPTY,
					"Fixture: the user's precision ray meets " + userRay);
			scene.clearAims();
			component.setAction(action(helper, scene, "punch"), scene.user, SyncType.NO_SYNC);
			ActionTarget open = StandEntityPunchAbility.getFreshPunchTarget(scene.stand, ActionTarget.EMPTY);
			helper.assertTrue(open.getMainEntity() == cow, "1.16 aimed with the Stand's own precision ray when the user's"
					+ " met nothing, which takes the cow the Stand looks at; got " + open);
			BlockPos wall = BlockPos.containing(scene.origin.add(0, 1, 1));
			scene.placedBlocks.add(new PlacedBlock(wall, helper.getLevel().getBlockState(wall)));
			helper.getLevel().setBlockAndUpdate(wall, Blocks.BEDROCK.defaultBlockState());
			ActionTarget walled = StandEntityPunchAbility.getFreshPunchTarget(scene.stand, ActionTarget.EMPTY);
			helper.assertTrue(walled.getType() == ActionTarget.TargetType.BLOCK && walled.getBlockPos().equals(wall),
					"The Stand's own precision ray took " + walled + " behind the bedrock at " + wall + "; walls block the aim");
			helper.succeed();
		}
		finally {
			component.setAction(null, scene.user, SyncType.NO_SYNC);
			scene.close();
		}
	}

	// 1.16 StandEntity.precisionRayTrace: a mob that is neither a player, a Stand nor hostile keeps the priority of the
	// entity before it, the lowest at first, so a boat behind a cow on the ray comes first
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH)
	public static void precisionAimTakesABoatBeforeACowInFrontOfIt(GameTestHelper helper) {
		Scene scene = new Scene(helper, GameType.SURVIVAL, false, 16);
		try {
			Cow cow = scene.cow(0, 1.5);
			Boat boat = EntityType.BOAT.create(helper.getLevel());
			helper.assertTrue(boat != null, "Could not create a boat");
			boat.setNoGravity(true);
			boat.moveTo(scene.origin.x, scene.origin.y, scene.origin.z + 4.2, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(boat), "Could not add a boat");
			scene.entities.add(boat);
			scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, boat.getBoundingBox().getCenter());
			helper.assertTrue(StandEntityPunchAbility.canStandHit(scene.stand, boat), "Fixture: the Stand cannot hit the boat");
			Vec3 eyes = scene.user.getEyePosition();
			Vec3 end = eyes.add(scene.user.getViewVector(1).scale(5));
			helper.assertTrue(cow.getBoundingBox().clip(eyes, end).isPresent() && boat.getBoundingBox().clip(eyes, end)
					.isPresent(), "Fixture: the cow and the boat are not both on the crosshair");
			ActionTarget aimed = StandEntityPunchAbility.userPrecisionRayTarget(scene.stand, scene.user);
			helper.assertTrue(aimed.getMainEntity() == boat,
					"1.16's precision ray took the boat behind the cow, a passive mob keeping the lowest priority; got " + aimed);
			helper.succeed();
		}
		finally {
			scene.close();
		}
	}

	private static EntityActionInstance action(GameTestHelper helper, Scene scene, String name) {
		Ability ability = scene.power.getAbility(name);
		helper.assertTrue(ability instanceof EntityActionType, "Fixture: no " + name + " action");
		return ((EntityActionType) ability).initActionOnAbilityUse(helper.getLevel(), scene.user, scene.stand, null);
	}

	private interface Step {
		boolean run(int tick);
	}

	private static final class Scene {
		final GameTestHelper helper;
		final Player user;
		final StandPower power;
		final StandType type;
		final StandEntity stand;
		final Vec3 origin;
		final List<Entity> entities = new ArrayList<>();
		final List<PlacedBlock> placedBlocks = new ArrayList<>();
		int ticks;
		boolean finished;

		Scene(GameTestHelper helper, GameType gameType, boolean locked, double speed) {
			this.helper = helper;
			origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
			user = GameTestPlayers.makeServerMockPlayer(helper, gameType);
			user.getAbilities().instabuild = gameType == GameType.CREATIVE;
			user.setNoGravity(true);
			user.noPhysics = true;
			user.moveTo(origin.x, origin.y, origin.z, 0, 0);
			user.setYHeadRot(0);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Stand user");
			entities.add(user);
			power = PowerClass.STAND.attachGet(user);
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			helper.assertTrue(type.summon(user, power), "Could not summon Star Platinum");
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null && stand.getUser() == user, "Missing Star Platinum entity");
			stand.summonLockTicks = 0;
			stand.gradualSummonWeaknessTicks = 0;
			stand.setNoGravity(true);
			stand.moveTo(origin.x, origin.y, origin.z, 0, 0);
			user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(speed);
			StandEntity.updateStandStatAttributes(stand, user);
			power.setStamina(power.getMaxStamina());
			stand.updateStrengthMultipliers();
			user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock = locked;
		}

		Cow cow(double x, double z) {
			Cow cow = EntityType.COW.create(helper.getLevel());
			helper.assertTrue(cow != null, "Could not create a cow");
			cow.setNoAi(true);
			cow.setNoGravity(true);
			cow.noPhysics = true;
			cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
			cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
			cow.setHealth(500);
			cow.moveTo(origin.x + x, origin.y, origin.z + z, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Could not add a cow");
			entities.add(cow);
			return cow;
		}

		// within Star Platinum's precision boxes of the user's eyes, off the crosshair
		Cow besideTheCrosshair() {
			Cow cow = cow(1.2, 0.8);
			helper.assertTrue(cow.getBoundingBox().distanceToSqr(user.getEyePosition()) < 1.9 * 1.9,
					"Fixture: the cow is not within 1.9 blocks of the user's eyes");
			helper.assertTrue(cow.getBoundingBox().clip(user.getEyePosition(),
					user.getEyePosition().add(user.getViewVector(1).scale(5))).isEmpty(),
					"Fixture: the cow is on the crosshair");
			helper.assertTrue(StandEntityPunchAbility.userPrecisionRayTarget(stand, user).getMainEntity() == cow,
					"Fixture: the precision ray does not take the cow beside the crosshair");
			return cow;
		}

		// bedrock half a block ahead of the eyes, between them and the cow beside the crosshair
		void wallAhead() {
			BlockPos wall = BlockPos.containing(origin.add(0, 1.62, 1));
			placedBlocks.add(new PlacedBlock(wall, helper.getLevel().getBlockState(wall)));
			helper.getLevel().setBlockAndUpdate(wall, Blocks.BEDROCK.defaultBlockState());
			ActionTarget aimed = StandEntityPunchAbility.userPrecisionRayTarget(stand, user);
			helper.assertTrue(aimed.getType() == ActionTarget.TargetType.BLOCK && aimed.getBlockPos().equals(wall),
					"Fixture: the precision aim does not stop at the bedrock at " + wall + ": " + aimed);
		}

		void clearAims() {
			LivingComponentAction.getComponent(stand).entityAim.setTarget(ActionTarget.EMPTY);
			LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
		}

		EntityActionInstance press(String name, InputMethod method) {
			Ability base = power.getAbility(name);
			var selected = power.updateAvailableMoves().getContextVariationContainer(base);
			helper.assertTrue(selected != null && AbilityInput.withConditionCheck(selected, user, method),
					"The " + name + " input was not admitted");
			BufferingState buffer = BufferingState.clickOnly();
			var held = AbilityInput.keyPress(KEY, selected.ability, user, null, method, 0, buffer, base.getAbilityId());
			helper.assertTrue(buffer.isActionSuccess, "The " + name + " key press failed");
			return held != null && held.action instanceof EntityActionInstance a ? a : stand.getCurStandAction();
		}

		void assertOffset(EntityActionInstance action, double expected, String when) {
			Vec3 synced = action.getSyncedStandOffset();
			helper.assertTrue(synced != null && Math.abs(synced.z - expected) < 0.01,
					"Heavy punch Stand distance at the " + when + " is " + synced + ", expected " + expected);
		}

		void run(Step step) {
			helper.onEachTick(() -> {
				if (finished) return;
				try {
					if (step.run(++ticks)) {
						finished = true;
						close();
						helper.succeed();
					}
				}
				catch (RuntimeException | Error error) {
					finished = true;
					close();
					throw error;
				}
			});
		}

		void close() {
			AbilityInput.keyRelease(KEY, user);
			if (power.isSummoned()) type.forceUnsummon(user, power);
			entities.forEach(Entity::discard);
			placedBlocks.forEach(placed -> helper.getLevel().setBlockAndUpdate(placed.pos(), placed.previous()));
			placedBlocks.clear();
		}
	}

	private record PlacedBlock(BlockPos pos, BlockState previous) {}
}
