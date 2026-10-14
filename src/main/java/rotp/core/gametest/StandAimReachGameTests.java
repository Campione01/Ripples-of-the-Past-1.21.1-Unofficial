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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntity.punch / aimWithThisOrUser: an attack with no target in the Stand's reach aims from the user's eyes
 * at the user's 5-block reach and keeps what the Stand, moved forward, can reach. The default barrage and that
 * fallback follow the owner's strict crosshair rule: what the crosshair ray meets first, no precision widening. The
 * heavy punch's Stand keeps its distance from its target through the windup. Own batch: the rays reach several blocks
 * and must not meet a neighbouring test's mobs.
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

	// the owner's strict crosshair: Star Platinum's 1.16 precision boxes (about 1.9 blocks each side) would take a mob
	// this close whichever way the user looked
	@GameTest(template = "empty", timeoutTicks = 60, batch = BATCH)
	public static void defaultBarrageSkipsAMobBesideTheCrosshair(GameTestHelper helper) {
		Scene scene = new Scene(helper, GameType.CREATIVE, false, 16);
		Cow cow = scene.cow(1.2, 0.8);
		helper.assertTrue(cow.getBoundingBox().distanceToSqr(scene.user.getEyePosition()) < 1.9 * 1.9,
				"Fixture: the cow is not within 1.9 blocks of the user's eyes");
		helper.assertTrue(cow.getBoundingBox().clip(scene.user.getEyePosition(),
				scene.user.getEyePosition().add(scene.user.getViewVector(1).scale(5))).isEmpty(),
				"Fixture: the cow is on the crosshair");
		float health = cow.getHealth();
		scene.run(tick -> {
			if (tick == 3) {
				scene.clearAims();
				EntityActionInstance action = scene.press("barrage", InputMethod.HOLD);
				helper.assertTrue(StandEntityBarrageAbility.isDirectionalBarrage(scene.stand, action),
						"Fixture: the default barrage is not the unlocked directional one");
			}
			else if (tick > 3 && cow.getHealth() < health) {
				helper.fail("The default barrage hit a cow beside the crosshair 1.9 blocks from the user, off the owner's strict crosshair");
			}
			else if (tick > 25) {
				helper.assertTrue(scene.stand.getCurStandAction() instanceof StandEntityBarrage,
						"Fixture: the barrage ended before the check");
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

	// the 1.16 precision aim kept behind the aim switch: walls hide an entity measured by its own box, even one so close
	// that its precision box holds the user's eyes
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

	// 1.16 StandEntity.aimWithThisOrUser: an action that is not a punch or barrage (an add-on's bomb or erase) aimed with
	// the precision ray; the owner's strict crosshair covers only the default barrage and the light and heavy punches
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH)
	public static void nonPunchActionAimsWithThePrecisionRay(GameTestHelper helper) {
		Scene scene = new Scene(helper, GameType.SURVIVAL, false, 16);
		LivingComponentAction component = LivingComponentAction.getComponent(scene.stand);
		try {
			Cow cow = scene.cow(1.2, 0.8);
			helper.assertTrue(cow.getBoundingBox().clip(scene.user.getEyePosition(),
					scene.user.getEyePosition().add(scene.user.getViewVector(1).scale(5))).isEmpty(),
					"Fixture: the cow is on the crosshair");
			scene.clearAims();
			component.setAction(action(helper, scene, "punch"), scene.user, SyncType.NO_SYNC);
			ActionTarget punch = StandEntityPunchAbility.getFreshPunchTarget(scene.stand, ActionTarget.EMPTY);
			helper.assertTrue(punch.getMainEntity() != cow,
					"Fixture: the light punch took the cow beside the crosshair, off the owner's strict crosshair");
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
		}
	}
}
