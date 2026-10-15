package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.compat.v1_21_4.missingmethods._EntitySelector;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.StandEntityHeavyPunch;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility.StandEntityPunch;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityAttributes;
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
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The aim assist per attack, as 1.16 kept or refused the target an attack was pressed at: the light punch keeps the
 * block or the mob it was pressed at (StandEntityLightAttack.standKeepsTarget), the heavy punch only a mob it can attack
 * (StandEntityHeavyAttack.standKeepsTarget), the barrage nothing (StandEntityMeleeBarrage.checkStandTarget); a pressed
 * mob the Stand cannot attack is refused (PowerBaseImpl.checkTarget, StandEntity.canAttack). What an attack does not
 * keep, StandEntity.punch aims again with the precision ray (aimWithThisOrUser, precisionRayTrace), whose boxes take a
 * mob within about 1.9 blocks of the eyes before the ground. Without the aim assist every attack strikes what the
 * crosshair meets. Each test has its own batch: the rays reach past the test area.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandAttackAimRulesGameTests {
	private static final String BATCH = "stand_attack_aim_rules_";
	private static final short KEY = 177;

	private StandAttackAimRulesGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "ground_heavy")
	public static void heavyPunchAtTheGroundHitsTheMobInThePrecisionBoxesAsIn116(GameTestHelper helper) {
		atTheGround(helper, "heavy_punch", InputMethod.CLICK, true);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "ground_barrage")
	public static void barrageAtTheGroundHitsTheMobInThePrecisionBoxesAsIn116(GameTestHelper helper) {
		atTheGround(helper, "barrage", InputMethod.HOLD, true);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "ground_punch")
	public static void punchAtTheGroundStrikesTheBlockItWasPressedAtAsIn116(GameTestHelper helper) {
		atTheGround(helper, "punch", InputMethod.CLICK, true);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "ground_heavy_off")
	public static void heavyPunchWithoutAimAssistStrikesTheGroundOnTheCrosshair(GameTestHelper helper) {
		atTheGround(helper, "heavy_punch", InputMethod.CLICK, false);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "ground_barrage_off")
	public static void barrageWithoutAimAssistStrikesTheGroundOnTheCrosshair(GameTestHelper helper) {
		atTheGround(helper, "barrage", InputMethod.HOLD, false);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "ground_punch_off")
	public static void punchWithoutAimAssistStrikesTheGroundOnTheCrosshair(GameTestHelper helper) {
		atTheGround(helper, "punch", InputMethod.CLICK, false);
	}

	// the crosshair on the ground 2 blocks ahead, a cow beside the crosshair inside Star Platinum's precision boxes
	private static void atTheGround(GameTestHelper helper, String name, InputMethod method, boolean aimAssist) {
		Scene scene = new Scene(helper, aimAssist);
		BlockPos ground = scene.groundStrip();
		Cow cow = scene.besideTheCrosshair();
		scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.upFromBottomCenterOf(ground, 1));
		helper.assertTrue(scene.vanillaPick().getType() == ActionTarget.TargetType.BLOCK
				&& ground.equals(scene.vanillaPick().getBlockPos()), "Fixture: the crosshair does not meet the ground at " + ground
				+ ": " + scene.vanillaPick());
		helper.assertTrue(StandEntityPunchAbility.userPrecisionRayTarget(scene.stand, scene.user).getMainEntity() == cow,
				"Fixture: the precision ray does not take the cow before the ground");
		boolean mobFirst = aimAssist && !name.equals("punch");
		String how = name + (aimAssist ? "" : " without the aim assist");
		float health = cow.getHealth();
		EntityActionInstance[] action = new EntityActionInstance[1];
		scene.run(tick -> {
			if (tick == 3) {
				scene.sendClientAims();
				action[0] = scene.press(name, method);
				helper.assertTrue(action[0] instanceof StandEntityPunch || action[0] instanceof StandEntityHeavyPunch
						|| StandEntityBarrageAbility.isDirectionalBarrage(scene.stand, action[0]),
						"Fixture: the " + name + " input started " + action[0]);
				return false;
			}
			if (tick < 3) {
				return false;
			}
			ActionTarget struck = action[0].punchedTarget;
			boolean blockStruck = struck != null && struck.getType() == ActionTarget.TargetType.BLOCK;
			if (mobFirst) {
				helper.assertTrue(!blockStruck && scene.isStone(ground), "The " + how + " struck " + struck
						+ " instead of the cow in the precision boxes, which 1.16's precision ray took before the ground");
				return cow.getHealth() < health && tick > 15;
			}
			if (struck == null || struck.getType() == ActionTarget.TargetType.EMPTY) {
				helper.assertTrue(cow.getHealth() >= health, "The " + how + " hurt the cow beside the crosshair before it struck");
				return false;
			}
			helper.assertTrue(blockStruck && ground.equals(struck.getBlockPos()), "The " + how + " struck " + struck
					+ " instead of the ground on the crosshair" + (aimAssist ? ", the block 1.16's light punch kept from its press" : ""));
			return true;
		}, 60, () -> "The " + how + (mobFirst ? " never hit the cow in the precision boxes" : " never struck the ground"));
	}

	// the user's own wolf on the ground on the crosshair with the ground right behind it: 1.16 refused the pressed wolf
	// and aimed again with the precision ray, which skips it, so the cow beside is hit, never the ground
	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "wolf_punch")
	public static void punchAtTheUsersOwnWolfOnTheGroundHitsTheMobBesideAsIn116(GameTestHelper helper) {
		atOwnWolfOnTheGround(helper, "punch", InputMethod.CLICK);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "wolf_heavy")
	public static void heavyPunchAtTheUsersOwnWolfOnTheGroundHitsTheMobBesideAsIn116(GameTestHelper helper) {
		atOwnWolfOnTheGround(helper, "heavy_punch", InputMethod.CLICK);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "wolf_barrage")
	public static void barrageAtTheUsersOwnWolfOnTheGroundHitsTheMobBesideAsIn116(GameTestHelper helper) {
		atOwnWolfOnTheGround(helper, "barrage", InputMethod.HOLD);
	}

	private static void atOwnWolfOnTheGround(GameTestHelper helper, String name, InputMethod method) {
		Scene scene = new Scene(helper, true);
		BlockPos ground = scene.groundStrip();
		Cow cow = scene.besideTheCrosshair();
		Wolf wolf = scene.ownWolf(Vec3.upFromBottomCenterOf(ground, 1));
		// low on its front, so that the crosshair ray goes on into the ground under it
		scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, wolf.position().add(0, 0.15, -0.25));
		helper.assertTrue(scene.vanillaPick().getMainEntity() == wolf, "Fixture: the vanilla pick is " + scene.vanillaPick());
		ActionTarget behind = StandEntityPunchAbility.crosshairPick(scene.stand, scene.user, 1,
				entity -> entity != scene.stand && StandEntityPunchAbility.canStandHit(scene.stand, entity));
		helper.assertTrue(behind.getType() == ActionTarget.TargetType.BLOCK && ground.equals(behind.getBlockPos()),
				"Fixture: past the wolf the crosshair does not meet the ground under it: " + behind);
		helper.assertTrue(StandEntityPunchAbility.userPrecisionRayTarget(scene.stand, scene.user).getMainEntity() == cow,
				"Fixture: the precision ray does not take the cow beside");
		float wolfHealth = wolf.getHealth();
		float health = cow.getHealth();
		EntityActionInstance[] action = new EntityActionInstance[1];
		scene.run(tick -> {
			if (tick == 3) {
				ActionTarget aim = scene.sendClientAims();
				helper.assertTrue(aim.getMainEntity() == wolf, "Fixture: a client's Stand aim, the marker, is " + aim);
				action[0] = scene.press(name, method);
				helper.assertTrue(action[0] != null, "Fixture: the " + name + " input started nothing");
				helper.assertTrue(action[0].standRotationTarget == null || action[0].standRotationTarget.getMainEntity() != wolf,
						"The " + name + " turned the Stand to the user's own wolf it was pressed at, which 1.16 refused");
				if (!name.equals("barrage")) {
					// with no pressed target the Stand stands off at its farthest, nothing being within 2 blocks on the look;
					// stepping up to the wolf 2 blocks ahead would leave it within a block of its user
					Vec3 offset = action[0].getSyncedStandOffset();
					helper.assertTrue(offset != null && offset.z > 1.0, "The " + name + " stepped the Stand up to the"
							+ " user's own wolf it was pressed at, which 1.16 refused: offset " + offset);
				}
				return false;
			}
			if (tick < 3) {
				return false;
			}
			ActionTarget struck = action[0].punchedTarget;
			helper.assertTrue(wolf.getHealth() >= wolfHealth, "The " + name + " hurt the user's own wolf");
			helper.assertTrue((struck == null || struck.getType() != ActionTarget.TargetType.BLOCK) && scene.isStone(ground),
					"The " + name + " struck " + struck + ", the ground behind the user's own wolf; 1.16's precision ray"
							+ " took the cow beside once the wolf was refused");
			return cow.getHealth() < health && tick > 15;
		}, 60, () -> "The " + name + " at the user's own wolf on the ground never hit the cow beside it");
	}

	private interface Step {
		boolean run(int tick);
	}

	private record PlacedBlock(BlockPos pos, BlockState previous) {}

	static final class Scene {
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
		boolean closed;

		Scene(GameTestHelper helper, boolean aimAssist) {
			this.helper = helper;
			origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
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
			user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(16);
			StandEntity.updateStandStatAttributes(stand, user);
			power.setStamina(power.getMaxStamina());
			stand.updateStrengthMultipliers();
			var settings = user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get());
			settings.standAttackTargetLock = false;
			settings.standAimAssist = aimAssist;
		}

		// stone at the user's feet level from 1 to 3 blocks ahead; returns the one 2 blocks ahead
		BlockPos groundStrip() {
			BlockPos feet = BlockPos.containing(origin.x, origin.y - 0.5, origin.z);
			for (int z = 1; z <= 3; z++) {
				block(feet.south(z), Blocks.STONE.defaultBlockState());
			}
			return feet.south(2);
		}

		boolean isStone(BlockPos pos) {
			return helper.getLevel().getBlockState(pos).is(Blocks.STONE);
		}

		void block(BlockPos pos, BlockState state) {
			placedBlocks.add(new PlacedBlock(pos, helper.getLevel().getBlockState(pos)));
			helper.getLevel().setBlockAndUpdate(pos, state);
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
			return cow;
		}

		// the user's own tame wolf; the Stand cannot attack it
		Wolf ownWolf(Vec3 pos) {
			Wolf wolf = new Wolf(EntityType.WOLF, helper.getLevel()) {
				// the level finds a real owner, a server player, by its UUID; this mock user is not one
				@Override
				public LivingEntity getOwner() {
					return user;
				}
			};
			wolf.setNoAi(true);
			wolf.setNoGravity(true);
			wolf.noPhysics = true;
			wolf.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
			wolf.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
			wolf.setHealth(500);
			wolf.setTame(true, false);
			wolf.setOwnerUUID(user.getUUID());
			wolf.moveTo(pos.x, pos.y, pos.z, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(wolf), "Could not add a wolf");
			entities.add(wolf);
			helper.assertTrue(wolf.getOwner() == user && !StandEntityPunchAbility.canStandHit(stand, wolf),
					"Fixture: the wolf is not the user's own, or the Stand can attack it");
			return wolf;
		}

		// what the vanilla pick meets; the Stand standing on its user here is no part of a real view
		Predicate<Entity> pickable() {
			return entity -> entity != stand && _EntitySelector.CAN_BE_PICKED.test(entity);
		}

		ActionTarget vanillaPick() {
			return StandEntityPunchAbility.crosshairPick(stand, user, 1, pickable());
		}

		// the aims a client sends, its vanilla pick and its Stand aim, through the packet handlers' own step
		ActionTarget sendClientAims() {
			ActionTarget pick = vanillaPick();
			ActionTarget standAim = StandEntityPunchAbility.userStandAim(stand, user, pick,
					entity -> StandEntityAbility.canDefaultTargetEntityForAiming(stand, entity));
			LivingComponentAction.getComponent(user).entityAim.setTargetFromClient(pick, helper.getLevel());
			LivingComponentAction.getComponent(stand).entityAim.setTargetFromClient(standAim, helper.getLevel());
			return standAim;
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
			AbilityInput.keyRelease(KEY, user);
			if (power.isSummoned()) type.forceUnsummon(user, power);
			entities.forEach(Entity::discard);
			for (int i = placedBlocks.size() - 1; i >= 0; i--) {
				helper.getLevel().setBlockAndUpdate(placedBlocks.get(i).pos(), placedBlocks.get(i).previous());
			}
			placedBlocks.clear();
		}
	}
}
