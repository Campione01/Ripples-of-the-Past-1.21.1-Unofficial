package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import rotp.core.JojoModConfig;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.api.stand.StandSkillWalls;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility.StandEntityPunch;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The server setting standSkillsThroughWalls (owner, 2026-10-15): in "manga" mode a skill marked through
 * {@link StandSkillWalls} picks and hits a mob behind a block, as the 1.16 entity rays did; in "walls" mode, and for a
 * skill that is not marked, the block stops it. Star Platinum's punch is marked only inside these tests. Each test has
 * its own batch: the mark and the mode are global while a batch runs, and the rays reach past the test area.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandSkillWallsGameTests {
	private static final String PICK_BATCH = "stand_skill_walls_pick";
	private static final String MANGA_BATCH = "stand_skill_walls_manga";
	private static final String WALLS_BATCH = "stand_skill_walls_walls";
	private static final short KEY = 173;

	@Nullable
	private static Scene active;

	private StandSkillWallsGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20, batch = PICK_BATCH)
	public static void onlyAMarkedSkillPicksAMobBehindAWallAndOnlyInMangaMode(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		LivingComponentAction component = LivingComponentAction.getComponent(scene.stand);
		try {
			Cow cow = scene.cowBehindWall();
			EntityActionInstance punch = scene.action("punch");
			component.setAction(punch, scene.user, SyncType.NO_SYNC);
			for (String mode : List.of(JojoModConfig.WALLS_MODE_MANGA, JojoModConfig.SKILL_WALLS_MODE_WALLS)) {
				scene.mode.set(mode);
				for (boolean marked : new boolean[] { true, false }) {
					scene.mark(marked);
					scene.clearAims();
					ActionTarget target = StandEntityPunchAbility.getFreshPunchTarget(scene.stand, ActionTarget.EMPTY);
					if (marked && JojoModConfig.WALLS_MODE_MANGA.equals(mode)) {
						helper.assertTrue(target.getMainEntity() == cow,
								"A marked punch in manga mode must pick the cow behind the wall; got " + target);
					}
					else {
						helper.assertTrue(target.getType() == ActionTarget.TargetType.BLOCK,
								"The wall must stop the " + (marked ? "marked" : "unmarked") + " punch in " + mode
										+ " mode; got " + target);
					}
				}
			}
			helper.succeed();
		}
		finally {
			component.setAction(null, scene.user, SyncType.NO_SYNC);
			scene.close();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 60, batch = MANGA_BATCH)
	public static void markedPunchHitsAMobBehindAWallInMangaMode(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		Cow cow = scene.cowBehindWall();
		scene.mode.set(JojoModConfig.WALLS_MODE_MANGA);
		scene.mark(true);
		float health = cow.getHealth();
		scene.run(tick -> {
			if (tick == 3) {
				scene.clearAims();
				EntityActionInstance punch = scene.press("punch");
				helper.assertTrue(punch instanceof StandEntityPunch && StandSkillWalls.isMarked(punch),
						"Fixture: the punch input started " + punch + ", which is not the marked punch");
			}
			return tick > 3 && cow.getHealth() < health;
		}, 30, () -> "The marked punch in manga mode did not hit the cow behind the wall");
	}

	@GameTest(template = "empty", timeoutTicks = 60, batch = WALLS_BATCH)
	public static void markedPunchIsStoppedByAWallInWallsMode(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		Cow cow = scene.cowBehindWall();
		scene.mode.set(JojoModConfig.SKILL_WALLS_MODE_WALLS);
		scene.mark(true);
		float health = cow.getHealth();
		EntityActionInstance[] punch = new EntityActionInstance[1];
		scene.run(tick -> {
			if (tick == 3) {
				scene.clearAims();
				punch[0] = scene.press("punch");
				helper.assertTrue(punch[0] instanceof StandEntityPunch && StandSkillWalls.isMarked(punch[0]),
						"Fixture: the punch input started " + punch[0] + ", which is not the marked punch");
			}
			else if (tick > 3 && cow.getHealth() < health) {
				helper.fail("The marked punch in walls mode hit the cow behind the wall");
			}
			else if (tick > 25) {
				helper.assertTrue(punch[0].punchedTarget != null
								&& punch[0].punchedTarget.getType() == ActionTarget.TargetType.BLOCK,
						"Fixture: the punch did not strike the wall: " + punch[0].punchedTarget);
				return true;
			}
			return false;
		}, Integer.MAX_VALUE, () -> "unreachable");
	}

	@AfterBatch(batch = PICK_BATCH)
	public static void releasePickBatch(ServerLevel level) {
		releaseActive();
	}

	@AfterBatch(batch = MANGA_BATCH)
	public static void releaseMangaBatch(ServerLevel level) {
		releaseActive();
	}

	@AfterBatch(batch = WALLS_BATCH)
	public static void releaseWallsBatch(ServerLevel level) {
		releaseActive();
	}

	private static void releaseActive() {
		if (active != null) {
			active.close();
		}
	}

	static ModConfigSpec.ConfigValue<String> spec(String name) {
		return JojoModConfig.COMMON_SPEC.getValues().get(List.of("Stand settings", name));
	}

	private interface Step {
		boolean run(int tick);
	}

	private record PlacedBlock(BlockPos pos, BlockState previous) {}

	private static final class Scene {
		final GameTestHelper helper;
		final Player user;
		final StandPower power;
		final StandType type;
		final StandEntity stand;
		final Vec3 origin;
		final ModConfigSpec.ConfigValue<String> mode = spec("standSkillsThroughWalls");
		final String previousMode = mode.get();
		final List<Entity> entities = new ArrayList<>();
		final List<PlacedBlock> placedBlocks = new ArrayList<>();
		AbilityId markedId;
		int ticks;
		boolean finished;
		boolean closed;

		Scene(GameTestHelper helper) {
			helper.assertTrue(active == null, "Fixture: overlapping wall setting scenes");
			active = this;
			this.helper = helper;
			origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			user.setNoGravity(true);
			user.noPhysics = true;
			// looking down at the middle of a cow 2.2 blocks ahead
			user.moveTo(origin.x, origin.y, origin.z, 0, 22.7F);
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
			stand.moveTo(origin.x, origin.y, origin.z, 0, 22.7F);
			user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(16);
			StandEntity.updateStandStatAttributes(stand, user);
			power.setStamina(power.getMaxStamina());
			stand.updateStrengthMultipliers();
			user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock = false;
			Ability punch = power.getAbility("punch");
			helper.assertTrue(punch != null && punch.getAbilityId().powerTypeId() != null,
					"Fixture: Star Platinum's punch has no moveset id");
			markedId = punch.getAbilityId();
		}

		// two bedrock blocks from half a block to a block and a half ahead, the cow behind them on the line of sight
		Cow cowBehindWall() {
			wall(new BlockPos(1, 2, 2));
			wall(new BlockPos(1, 3, 2));
			Cow cow = EntityType.COW.create(helper.getLevel());
			helper.assertTrue(cow != null, "Could not create a cow");
			cow.setNoAi(true);
			cow.setNoGravity(true);
			cow.noPhysics = true;
			cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
			cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
			cow.setHealth(500);
			cow.moveTo(origin.x, origin.y, origin.z + 2.2, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Could not add a cow");
			entities.add(cow);
			Vec3 eyes = user.getEyePosition();
			helper.assertTrue(cow.getBoundingBox().clip(eyes, eyes.add(user.getViewVector(1).scale(5))).isPresent(),
					"Fixture: the cow is not on the crosshair");
			return cow;
		}

		void wall(BlockPos relative) {
			BlockPos pos = helper.absolutePos(relative);
			placedBlocks.add(new PlacedBlock(pos, helper.getLevel().getBlockState(pos)));
			helper.getLevel().setBlockAndUpdate(pos, Blocks.BEDROCK.defaultBlockState());
		}

		void mark(boolean marked) {
			if (marked) {
				StandSkillWalls.markThroughWalls(markedId.powerTypeId(), markedId.nameInMoveset());
			}
			else {
				StandSkillWalls.unmarkThroughWalls(markedId.powerTypeId(), markedId.nameInMoveset());
			}
		}

		EntityActionInstance action(String name) {
			Ability ability = power.getAbility(name);
			helper.assertTrue(ability instanceof EntityActionType, "Fixture: no " + name + " action");
			return ((EntityActionType) ability).initActionOnAbilityUse(helper.getLevel(), user, stand, null);
		}

		void clearAims() {
			LivingComponentAction.getComponent(stand).entityAim.setTarget(ActionTarget.EMPTY);
			LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
		}

		EntityActionInstance press(String name) {
			Ability base = power.getAbility(name);
			var selected = power.updateAvailableMoves().getContextVariationContainer(base);
			helper.assertTrue(selected != null && AbilityInput.withConditionCheck(selected, user, InputMethod.CLICK),
					"The " + name + " input was not admitted");
			BufferingState buffer = BufferingState.clickOnly();
			var held = AbilityInput.keyPress(KEY, selected.ability, user, null, InputMethod.CLICK, 0, buffer,
					base.getAbilityId());
			helper.assertTrue(buffer.isActionSuccess, "The " + name + " key press failed");
			return held != null && held.action instanceof EntityActionInstance a ? a : stand.getCurStandAction();
		}

		void run(Step step, int lastTick, java.util.function.Supplier<String> timeoutMessage) {
			helper.onEachTick(() -> {
				if (finished) return;
				try {
					if (step.run(++ticks)) {
						finished = true;
						close();
						helper.succeed();
					}
					else if (ticks >= lastTick) {
						finished = true;
						close();
						helper.fail(timeoutMessage.get());
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
			if (closed) return;
			closed = true;
			if (markedId != null) {
				StandSkillWalls.unmarkThroughWalls(markedId.powerTypeId(), markedId.nameInMoveset());
			}
			mode.set(previousMode);
			AbilityInput.keyRelease(KEY, user);
			if (power.isSummoned()) type.forceUnsummon(user, power);
			entities.forEach(Entity::discard);
			placedBlocks.forEach(placed -> helper.getLevel().setBlockAndUpdate(placed.pos(), placed.previous()));
			placedBlocks.clear();
			if (active == this) {
				active = null;
			}
		}
	}
}
