package rotp.core.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.impl.stands.theworld.TheWorldTSPunchAbility;
import rotp.core.impl.stands.theworld.TheWorldTSPunchAbility.TheWorldTSPunch;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.subsystems.timestop.TimeStopLearning;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 TheWorldTSHeavyAttack.targetBeforePerform: the time-stop punch aims again when it starts and never uses the
 * clicked target, with stand.precisionRayTrace(aiming, getMaxRange(), getPrecision() / 16, false): hitboxes grown by
 * the Stand's precision, a player or Stand before a hostile mob before the rest, else the block by its outline (grass).
 * The owner kept the port's wall rule (2026-10-14): a mob behind a wall is not picked. At the strike, 1.16
 * StandEntity.punch aimed again when the target had gone out of reach.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TheWorldTSPunchAimGameTests {
	private static final short KEY = 157;
	private static final PowerSkillUnlocks.Owner UNLOCKS = PowerSkillUnlocks.register(
			JojoMod.resLoc("the_world_ts_punch_aim_gametest"));

	private TheWorldTSPunchAimGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void tsPunchAimsAgainInsteadOfUsingThePressAim(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, 0)) {
			Cow aside = scene.mob(EntityType.COW.create(scene.level), scene.start.add(-6, 0.5, 3));
			Cow ahead = scene.mob(EntityType.COW.create(scene.level), scene.start.add(0, 0.5, 5));
			scene.aimAt(aside);
			EntityActionInstance action = scene.press();
			helper.assertTrue(blinkTarget(action) == ahead,
					"1.16 aimed the time-stop punch again when it started: it went for "
							+ describe(action) + ", not the mob on the look line");
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void tsPunchPrefersTheHostileMobBehindACow(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, 0)) {
			scene.mob(EntityType.COW.create(scene.level), scene.start.add(0, 0.5, 3));
			Zombie zombie = scene.mob(EntityType.ZOMBIE.create(scene.level), scene.start.add(0, 0, 6));
			EntityActionInstance action = scene.press();
			helper.assertTrue(blinkTarget(action) == zombie,
					"1.16's precision ray put a hostile mob before a cow on the same line: it went for " + describe(action));
		}
		helper.succeed();
	}

	// the owner's aim assist setting, off (2026-10-15): the blink takes what the crosshair meets first, the cow, not the
	// hostile mob behind it that the precision ray's priority takes
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void tsPunchWithoutTheAimAssistTakesWhatTheCrosshairMeetsFirst(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, 0)) {
			Cow cow = scene.mob(EntityType.COW.create(scene.level), scene.start.add(0, 0.5, 3));
			scene.mob(EntityType.ZOMBIE.create(scene.level), scene.start.add(0, 0, 6));
			scene.user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAimAssist = false;
			EntityActionInstance action = scene.press();
			helper.assertTrue(blinkTarget(action) == cow,
					"With the aim assist off the time-stop punch went for " + describe(action)
							+ ", not the cow the crosshair meets first");
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void tsPunchRayStopsAtGrass(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, 90)) {
			BlockPos grass = BlockPos.containing(scene.start).below(4);
			scene.block(grass.below(), Blocks.STONE.defaultBlockState());
			scene.block(grass, Blocks.SHORT_GRASS.defaultBlockState());
			EntityActionInstance action = scene.press();
			ActionTarget target = action.standRotationTarget;
			helper.assertTrue(target != null && target.getType() == TargetType.BLOCK && grass.equals(target.getBlockPos()),
					"1.16's outline ray stops at the grass: the time-stop punch went for " + describe(action));
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void tsPunchStillCannotSeeAMobBehindAWall(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, 0)) {
			BlockPos wall = BlockPos.containing(scene.user.getEyePosition()).south(3);
			scene.block(wall, Blocks.STONE.defaultBlockState());
			scene.mob(EntityType.ZOMBIE.create(scene.level), scene.start.add(0, 0, 5));
			EntityActionInstance action = scene.press();
			ActionTarget target = action.standRotationTarget;
			helper.assertTrue(target != null && target.getType() == TargetType.BLOCK && wall.equals(target.getBlockPos()),
					"the owner kept the wall rule: a mob behind a wall is not the time-stop punch's target, got "
							+ describe(action));
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void tsPunchStrikesAFreshAimWhenItsTargetIsGone(GameTestHelper helper) {
		Scene scene = new Scene(helper, 0);
		try {
			Cow first = scene.mob(EntityType.COW.create(scene.level), scene.start.add(0, 0.5, 4));
			EntityActionInstance action = scene.press();
			helper.assertTrue(blinkTarget(action) == first, "fixture: the punch did not go for the cow ahead: " + describe(action));
			// the target leaves before the strike; another mob now stands in the user's reach, before the Stand
			first.moveTo(first.getX(), first.getY(), first.getZ() + 30, 0, 0);
			Cow second = scene.mob(EntityType.COW.create(scene.level),
					new Vec3(scene.stand.getX(), scene.start.y + 0.5, scene.stand.getZ() + 1));
			float firstHealth = first.getHealth();
			float secondHealth = second.getHealth();
			helper.runAfterDelay(12, () -> {
				try {
					helper.assertTrue(action.punchedTarget != null,
							"fixture: the time-stop punch did not strike: " + action.getPhase());
					helper.assertTrue(second.getHealth() < secondHealth && first.getHealth() == firstHealth,
							"1.16 StandEntity.punch aimed again when the target had gone: the mob in reach kept "
									+ second.getHealth() + "/" + secondHealth + ", the gone one " + first.getHealth()
									+ "/" + firstHealth + ", struck " + action.punchedTarget.getType());
				}
				finally {
					scene.close();
				}
				helper.succeed();
			});
		}
		catch (RuntimeException | Error failure) {
			scene.close();
			throw failure;
		}
	}

	// 1.16 TheWorldTSHeavyAttack.standPerform aimed again through StandEntity.aimWithThisOrUser: the precision ray
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void tsPunchFreshAimTakesAMobBesideTheCrosshair(GameTestHelper helper) {
		Scene scene = new Scene(helper, 0);
		try {
			Cow first = scene.mob(EntityType.COW.create(scene.level), scene.start.add(0, 0.5, 4));
			EntityActionInstance action = scene.press();
			helper.assertTrue(blinkTarget(action) == first, "fixture: the punch did not go for the cow ahead: " + describe(action));
			first.moveTo(first.getX(), first.getY(), first.getZ() + 30, 0, 0);
			Cow beside = scene.mob(EntityType.COW.create(scene.level),
					new Vec3(scene.start.x + 1.0, scene.start.y + 0.5, scene.stand.getZ() + 1));
			Vec3 eyes = scene.user.getEyePosition();
			helper.assertTrue(beside.getBoundingBox().clip(eyes, eyes.add(scene.user.getViewVector(1).scale(5))).isEmpty(),
					"fixture: the cow is on the crosshair");
			helper.assertTrue(StandEntityPunchAbility.userPrecisionRayTarget(scene.stand, scene.user).getMainEntity() == beside,
					"fixture: the precision ray does not take the cow beside the crosshair");
			float firstHealth = first.getHealth();
			float besideHealth = beside.getHealth();
			helper.runAfterDelay(12, () -> {
				try {
					helper.assertTrue(action.punchedTarget != null,
							"fixture: the time-stop punch did not strike: " + action.getPhase());
					helper.assertTrue(beside.getHealth() < besideHealth && first.getHealth() == firstHealth,
							"1.16 aimed the time-stop punch again with the precision ray when its target had gone: the cow"
									+ " beside the crosshair kept " + beside.getHealth() + "/" + besideHealth + ", struck "
									+ action.punchedTarget.getType());
				}
				finally {
					scene.close();
				}
				helper.succeed();
			});
		}
		catch (RuntimeException | Error failure) {
			scene.close();
			throw failure;
		}
	}

	private static Entity blinkTarget(EntityActionInstance action) {
		ActionTarget target = action.standRotationTarget;
		return target != null && target.getType() == TargetType.ENTITY ? target.getEntity() : null;
	}

	private static String describe(EntityActionInstance action) {
		ActionTarget target = action.standRotationTarget;
		if (target == null) {
			return "nothing";
		}
		return switch (target.getType()) {
			case ENTITY -> String.valueOf(target.getEntity());
			case BLOCK -> "block " + target.getBlockPos();
			default -> "nothing";
		};
	}

	private static final class Scene implements AutoCloseable {
		final GameTestHelper helper;
		final ServerLevel level;
		final Vec3 start;
		final Player user;
		final StandPower power;
		final StandType type;
		final StandEntity stand;
		final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
		final List<Entity> actors = new ArrayList<>();
		boolean closed;

		Scene(GameTestHelper helper, float pitch) {
			this.helper = helper;
			level = helper.getLevel();
			start = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 6, 2)));
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			user.setNoGravity(true);
			user.moveTo(start.x, start.y, start.z, 0, pitch);
			user.setYHeadRot(0);
			helper.assertTrue(level.addFreshEntity(user), "fixture: user not added");
			actors.add(user);
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("the_world"));
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "fixture: The World not granted");
			power.setResolveLevel(3);
			var unlocked = UNLOCKS.forceUnlock(power, "ts_punch");
			helper.assertTrue(unlocked == PowerSkillUnlocks.Result.UNLOCKED
					|| unlocked == PowerSkillUnlocks.Result.ALREADY_UNLOCKED, "fixture: TS punch not unlocked");
			// a trained time stop, so the time skip reaches across the scene
			power.getCurTypeData().setAbilityLearningProgressPoints(TimeStopLearning.TIME_STOP, 95,
					TimeStopLearning.getMaxTrainingPoints(power), power);
			power.setStamina(power.getMaxStamina());
			helper.assertTrue(type.summon(user, power), "fixture: The World not summoned");
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "fixture: no summoned Stand");
			actors.add(stand);
			stand.summonLockTicks = 0;
			stand.gradualSummonWeaknessTicks = 0;
			stand.setCanFollowUser(false);
			stand.setNoGravity(true);
			stand.moveTo(start.x, start.y, start.z, 0, 0);
			helper.assertTrue(stand.getMaxRange() >= 7, "fixture: The World's reach is " + stand.getMaxRange());
			aimAt(null);
		}

		<T extends LivingEntity> T mob(T entity, Vec3 pos) {
			helper.assertTrue(entity != null, "fixture: mob not created");
			entity.setNoGravity(true);
			if (entity instanceof Mob mob) {
				mob.setNoAi(true);
			}
			entity.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
			entity.setHealth(100);
			entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
			entity.moveTo(pos.x, pos.y, pos.z, 0, 0);
			helper.assertTrue(level.addFreshEntity(entity), "fixture: mob not added");
			actors.add(entity);
			return entity;
		}

		void aimAt(Entity target) {
			ActionTarget aim = target != null ? new ActionTarget(target) : ActionTarget.EMPTY;
			LivingComponentAction.getComponent(user).entityAim.setTarget(aim);
			LivingComponentAction.getComponent(stand).entityAim.setTarget(aim);
		}

		void block(BlockPos pos, BlockState state) {
			blocks.putIfAbsent(pos, level.getBlockState(pos));
			level.setBlock(pos, state, 3);
		}

		EntityActionInstance press() {
			Ability ts = power.getAbility("ts_punch");
			helper.assertTrue(ts instanceof TheWorldTSPunchAbility, "fixture: The World has no TS punch");
			var selected = power.updateAvailableMoves().getContextVariationContainer(ts);
			helper.assertTrue(selected != null && AbilityInput.withConditionCheck(selected, user, InputMethod.CLICK),
					"fixture: TS punch not admitted");
			BufferingState press = BufferingState.clickOnly();
			AbilityInput.keyPress(KEY, selected.ability, user, null, InputMethod.CLICK, 0, press, ts.getAbilityId());
			EntityActionInstance action = stand.getCurStandAction();
			helper.assertTrue(press.isActionSuccess && action instanceof TheWorldTSPunch,
					"fixture: the TS punch did not start: " + action);
			return action;
		}

		@Override
		public void close() {
			if (closed) {
				return;
			}
			closed = true;
			AbilityInput.keyRelease(KEY, user);
			if (power.isSummoned()) {
				type.forceUnsummon(user, power);
			}
			actors.forEach(Entity::discard);
			blocks.forEach((pos, state) -> level.setBlock(pos, state, 3));
		}
	}
}
