package rotp.core.gametest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility.CrosshairRule;
import rotp.core.impl.stands.crazydiamond.CrazyDAnchorMakeAbility;
import rotp.core.impl.stands.crazydiamond.CrazyDHealAbility;
import rotp.core.impl.stands.hierophant.HierophantBarrierAbility;
import rotp.core.impl.stands.hierophant.HierophantEmeraldSplashAbility.EmeraldSplashShot;
import rotp.core.impl.stands.hierophant.HierophantGreenEntity;
import rotp.core.impl.stands.magiciansred.MagiciansRedCrossfireHurricaneAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.subsystems.target.StandPrecisionRay;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What a crosshair meets is found by the blocks' outlines: the vanilla pick (GameRenderer.pick, Entity.pick with
 * ClipContext.Block.OUTLINE) a 1.16 client sent with a press stopped at grass, a flower or a torch, and so did 1.16's own
 * rays (JojoModUtil.rayTrace and rayTraceMultipleEntities, OUTLINE unless told otherwise), the precision ray of a Stand's
 * strike among them. The server's stand-ins for that crosshair take such a block too. The walls that hide an entity are
 * still the blocks with a collision shape (the owner's wall rule), and 1.16's COLLIDER rays still pass through grass. Own
 * batches: the rays reach a few blocks.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CrosshairBlockOutline116GameTests {
	private static final String BATCH = "crosshair_block_outline_";

	private CrosshairBlockOutline116GameTests() {}

	/*
	 * The default punch and barrage aim at the strike when no target is kept (1.16 StandEntity.punch: aimWithThisOrUser,
	 * whose precision ray met blocks by their outline; the default barrage aimed so for each hit). With the aim assist the
	 * port's punch asks the server's vanilla pick first (crosshairPick) and its barrage the precision ray, without it both
	 * take the strict crosshair of D-core-92. A wall torch with stone behind it, and grass with stone under it, are both
	 * within the Stand's reach: the strike takes the torch and the grass, not the stone behind.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "punch_torch")
	public static void defaultPunchAndBarrageTakeATorchUnderTheCrosshairAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, "star_platinum", 0)) {
			BlockPos[] torchOnWall = scene.wallTorch();
			strikesOutline(scene, torchOnWall[0], torchOnWall[1], "a wall torch");
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "punch_grass")
	public static void defaultPunchAndBarrageTakeGrassUnderTheCrosshairAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, "star_platinum", 90)) {
			BlockPos[] grassOnStone = scene.grassUnderfoot();
			strikesOutline(scene, grassOnStone[0], grassOnStone[1], "grass");
		}
		helper.succeed();
	}

	private static void strikesOutline(Scene scene, BlockPos outlined, BlockPos behind, String what) {
		GameTestHelper helper = scene.helper;
		helper.assertTrue(StandEntityPunchAbility.validatePunchTarget(scene.stand, new ActionTarget(behind, Direction.UP))
				.getType() == TargetType.BLOCK, "Fixture: the stone behind " + what + " is out of the Stand's reach");
		for (boolean assist : new boolean[] { true, false }) {
			scene.aimAssist(assist);
			String mode = " (aim assist " + (assist ? "on" : "off") + ")";
			if (assist) {
				ActionTarget pick = StandEntityPunchAbility.crosshairPick(scene.stand, scene.user, 1.0F,
						entity -> entity != scene.stand);
				helper.assertTrue(isBlock(pick, outlined), "The server's vanilla pick passed over " + what
						+ " the crosshair outlines: " + describe(pick) + ", " + what + " at " + outlined);
			}
			ActionTarget punch = StandEntityPunchAbility.getFreshPunchTarget(scene.stand, ActionTarget.EMPTY, true,
					CrosshairRule.BLOCK_OR_ENTITY);
			helper.assertTrue(isBlock(punch, outlined), "1.16's punch struck " + what + " under the crosshair when it kept no"
					+ " target; the port's went for " + describe(punch) + mode + ", " + what + " at " + outlined);
			EntityActionInstance barrage = scene.barrage();
			ActionTarget barrageHit = StandEntityBarrageAbility.clipDirectionalBarrageTarget(scene.stand, barrage, 1.0F);
			LivingComponentAction.getComponent(scene.stand).setAction(null, scene.user, SyncType.NO_SYNC);
			helper.assertTrue(isBlock(barrageHit, outlined), "1.16's default barrage hit " + what + " under the crosshair;"
					+ " the port's hit went for " + describe(barrageHit) + mode + ", " + what + " at " + outlined);
		}
		// the crosshair line the other Stand moves take without the aim assist (Heal and Revert, Bone Meal, Create
		// Lifeform, the time-stop punch, a manually controlled Stand's synced aim)
		ActionTarget line = StandPrecisionRay.crosshair(scene.user, scene.stand, 5.0D, true);
		helper.assertTrue(isBlock(line, outlined), "Without the aim assist the crosshair line passed over " + what + ": "
				+ describe(line) + ", " + what + " at " + outlined);
	}

	/*
	 * A Stand under manual control strikes along its own crosshair when it keeps no target (1.16 aimWithThisOrUser:
	 * precisionRayTrace from the Stand), with the aim assist and, by the strict crosshair, without it.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "manual")
	public static void manuallyControlledStandStrikesATorchOnItsCrosshairAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, "star_platinum", 0)) {
			BlockPos[] torchOnWall = scene.wallTorch();
			scene.stand.setManuallyControlled(true);
			// the Stand's eyes where the user's were, level with the torch
			scene.stand.moveTo(scene.origin.x, scene.user.getEyeY() - scene.stand.getEyeHeight(), scene.origin.z, 0, 0);
			scene.stand.setYHeadRot(0);
			scene.user.moveTo(scene.origin.x - 1.0D, scene.origin.y, scene.origin.z, 90, 0);
			for (boolean assist : new boolean[] { true, false }) {
				scene.aimAssist(assist);
				ActionTarget strike = StandEntityPunchAbility.getFreshPunchTarget(scene.stand, ActionTarget.EMPTY, true,
						CrosshairRule.BLOCK_OR_ENTITY);
				helper.assertTrue(isBlock(strike, torchOnWall[0]), "1.16's manually controlled Stand struck the torch on its"
						+ " crosshair; the port's went for " + describe(strike) + " (aim assist " + (assist ? "on" : "off")
						+ "), the torch at " + torchOnWall[0]);
			}
		}
		helper.succeed();
	}

	/*
	 * Crazy Diamond's heal takes an entity only. 1.16 pressed it at the client's vanilla pick, and a pressed block without a
	 * collision shape gave way to a COLLIDER ray (StandEntityAction.overrideVanillaMouseTarget), so a cow behind a torch on
	 * the crosshair was healed; the port's server-side aim (no client aim here) still takes the cow, with the aim assist
	 * (the precision ray) and without it (the crosshair line): the torch does not hide it.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "heal")
	public static void crazyDiamondsHealReachesACowBehindATorchAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, "crazy_diamond", 0)) {
			BlockPos torch = scene.torchInTheAir();
			Cow cow = scene.cow(2.5D);
			Method target = method(helper, CrazyDHealAbility.class, "getCurrentEntityTarget", LivingEntity.class, Level.class);
			for (boolean assist : new boolean[] { true, false }) {
				scene.aimAssist(assist);
				Object healed = invoke(helper, target, null, scene.user, helper.getLevel());
				helper.assertTrue(healed instanceof ActionTarget aim && aim.getType() == TargetType.ENTITY && aim.getEntity() == cow,
						"1.16's heal reached the cow behind a torch on the crosshair (only a collision shape hides an entity);"
								+ " the port's took " + (healed instanceof ActionTarget aim ? describe(aim) : healed)
								+ " (aim assist " + (assist ? "on" : "off") + "), the torch at " + torch);
			}
		}
		helper.succeed();
	}

	/*
	 * 1.16's COLLIDER rays keep passing through grass: StandEntityAction.front3dOffset, with no task target, placed the
	 * Stand by rayTraceMultipleEntities(..., BlockMode.COLLIDER, 0.25, 0) from the user's eyes (the port's
	 * EntityActionInstance.resolveFrontOffsetTarget), and HitResultUtil.clip's overloads without a block mode keep that
	 * mode, as add-ons call them.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "collider")
	public static void colliderRaysOf116StillPassThroughGrass(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, "star_platinum", 90)) {
			BlockPos[] grassOnStone = scene.grassUnderfoot();
			Method frontOffset = method(helper, EntityActionInstance.class, "resolveFrontOffsetTarget", StandEntity.class,
					LivingEntity.class, Level.class, ActionTarget.class, double.class);
			Object front = invoke(helper, frontOffset, null, scene.stand, scene.user, helper.getLevel(), null, 3.0D);
			helper.assertTrue(front instanceof ActionTarget aim && isBlock(aim, grassOnStone[1]),
					"1.16's front3dOffset ray (COLLIDER) passed through the grass to the stone under it; the port's met "
							+ (front instanceof ActionTarget aim ? describe(aim) : front));
			ActionTarget collider = HitResultUtil.clip(scene.user.getEyePosition(), scene.user.getLookAngle(), 3.0D, 3.0D,
					helper.getLevel(), entity -> false, scene.user, 0);
			helper.assertTrue(isBlock(collider, grassOnStone[1]), "HitResultUtil.clip without a block mode no longer passes"
					+ " through grass (COLLIDER): " + describe(collider));
		}
		helper.succeed();
	}

	/*
	 * Stand moves whose 1.16 action took a block from the crosshair or cast an OUTLINE ray of its own: Hierophant Green's
	 * Barrier (TargetRequirement.BLOCK, the vanilla pick unchanged), its Emerald Splash net (aimForBarriers,
	 * JojoModUtil.rayTrace), Crazy Diamond's block anchor (CrazyDiamondBlockCheckpointMake, TargetRequirement.BLOCK) and
	 * Magician's Red's Crossfire Hurricane special (aimWithThisOrUser, the precision ray) all aim at a wall torch, not the
	 * stone behind it.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "hierophant")
	public static void hierophantsBarrierAndNetAimAtATorchAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, "hierophant_green", 0)) {
			BlockPos[] torchOnWall = scene.wallTorch();
			ActionTarget barrier = HierophantBarrierAbility.getCurrentBlockTarget(scene.user, scene.stand, helper.getLevel());
			helper.assertTrue(isBlock(barrier, torchOnWall[0]), "1.16's Barrier took the torch the crosshair met; the port's"
					+ " server-side pick took " + describe(barrier) + ", the torch at " + torchOnWall[0]);
			helper.assertTrue(scene.stand instanceof HierophantGreenEntity, "Fixture: the Stand is not Hierophant Green");
			Optional<Vec3> net = EmeraldSplashShot.barrierAimPoint((HierophantGreenEntity) scene.stand);
			helper.assertTrue(net.equals(Optional.of(Vec3.atCenterOf(torchOnWall[0]))), "1.16's Emerald Splash net aimed at"
					+ " the torch its ray met by the outline; the port's aims at " + net + ", the torch at " + torchOnWall[0]);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "anchor")
	public static void crazyDiamondsAnchorTakesATorchAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, "crazy_diamond", 0)) {
			BlockPos[] torchOnWall = scene.wallTorch();
			Method find = method(helper, CrazyDAnchorMakeAbility.class, "findAnchorMakeTarget", LivingEntity.class);
			Object anchor = invoke(helper, find, null, scene.user);
			helper.assertTrue(anchor instanceof ActionTarget aim && isBlock(aim, torchOnWall[0]), "1.16's block anchor took"
					+ " the torch the crosshair met; the port's took " + (anchor instanceof ActionTarget aim ? describe(aim) : anchor)
					+ ", the torch at " + torchOnWall[0]);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "crossfire")
	public static void crossfireHurricaneSpecialAimsAtATorchAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper, "magicians_red", 0)) {
			BlockPos[] torchOnWall = scene.wallTorch();
			Class<?> shot = null;
			for (Class<?> inner : MagiciansRedCrossfireHurricaneAbility.class.getDeclaredClasses()) {
				if (inner.getSimpleName().equals("CrossfireShot")) {
					shot = inner;
				}
			}
			helper.assertTrue(shot != null, "Fixture: no CrossfireShot class");
			Method clipAim = method(helper, shot, "clipAim", Level.class, LivingEntity.class, StandEntity.class);
			for (boolean assist : new boolean[] { true, false }) {
				scene.aimAssist(assist);
				Object aim = invoke(helper, clipAim, null, helper.getLevel(), scene.user, scene.stand);
				helper.assertTrue(aim instanceof ActionTarget target && isBlock(target, torchOnWall[0]), "1.16's Crossfire"
						+ " Hurricane special aimed at the torch its precision ray met; the port's at "
						+ (aim instanceof ActionTarget target ? describe(target) : aim) + " (aim assist " + (assist ? "on" : "off")
						+ "), the torch at " + torchOnWall[0]);
			}
		}
		helper.succeed();
	}

	private static boolean isBlock(@Nullable ActionTarget target, BlockPos pos) {
		return target != null && target.getType() == TargetType.BLOCK && pos.equals(target.getBlockPos());
	}

	private static String describe(@Nullable ActionTarget target) {
		if (target == null) {
			return "nothing";
		}
		return switch (target.getType()) {
			case ENTITY -> String.valueOf(target.getEntity());
			case BLOCK -> "block " + target.getBlockPos();
			default -> "nothing";
		};
	}

	private static Method method(GameTestHelper helper, Class<?> owner, String name, Class<?>... parameters) {
		try {
			Method method = owner.getDeclaredMethod(name, parameters);
			method.setAccessible(true);
			return method;
		}
		catch (NoSuchMethodException e) {
			helper.fail("Fixture: " + owner.getSimpleName() + " has no " + name);
			throw new IllegalStateException(e);
		}
	}

	private static Object invoke(GameTestHelper helper, Method method, Object on, Object... args) {
		try {
			return method.invoke(on, args);
		}
		catch (IllegalAccessException | InvocationTargetException e) {
			throw new IllegalStateException("Fixture: " + method.getName() + " failed", e);
		}
	}

	private static final class Scene implements AutoCloseable {
		final GameTestHelper helper;
		final ServerLevel level;
		final Player user;
		final StandPower power;
		final StandType type;
		final StandEntity stand;
		final Vec3 origin;
		final BlockPos feet;
		final List<Entity> entities = new ArrayList<>();
		final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();

		// a Survival user floating at the origin, looking south (pitch 0) or down (90); its Stand a block to its west, off
		// the crosshair line but with what the scenes put on it within its reach
		Scene(GameTestHelper helper, String standName, float pitch) {
			this.helper = helper;
			level = helper.getLevel();
			feet = helper.absolutePos(new BlockPos(1, 3, 1));
			origin = Vec3.atBottomCenterOf(feet);
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			user.setNoGravity(true);
			user.moveTo(origin.x, origin.y, origin.z, 0, pitch);
			user.setYHeadRot(0);
			helper.assertTrue(level.addFreshEntity(user), "Could not add the Stand user");
			entities.add(user);
			power = PowerClass.STAND.attachGet(user);
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standName));
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant " + standName);
			helper.assertTrue(type.summon(user, power), "Could not summon " + standName);
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null && stand.getUser() == user, "Missing the " + standName + " entity");
			entities.add(stand);
			stand.setCanFollowUser(false);
			stand.setNoGravity(true);
			stand.moveTo(origin.x - 1.0D, origin.y, origin.z, 0, pitch);
			stand.setYHeadRot(0);
			LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
			LivingComponentAction.getComponent(stand).entityAim.setTarget(ActionTarget.EMPTY);
			aimAssist(true);
		}

		void aimAssist(boolean on) {
			user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAimAssist = on;
		}

		// the default barrage (keep attack target off), running on the Stand
		EntityActionInstance barrage() {
			Ability ability = power.getAbility("barrage");
			helper.assertTrue(ability instanceof EntityActionType, "Fixture: no barrage action");
			EntityActionInstance action = ((EntityActionType) ability).initActionOnAbilityUse(level, user, stand, null);
			helper.assertTrue(action != null && StandEntityBarrageAbility.isDirectionalBarrage(stand, action),
					"Fixture: the barrage is not the default one: " + action);
			LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
			return action;
		}

		// stone a block and a half ahead of the eyes, a wall torch on its face toward the user: {torch, stone}
		BlockPos[] wallTorch() {
			BlockPos torch = BlockPos.containing(user.getEyePosition()).south();
			BlockPos stone = torch.south();
			block(stone, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
			block(torch, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
			return new BlockPos[] { torch, stone };
		}

		// a wall torch on the crosshair line with nothing behind it, kept up without neighbour updates
		BlockPos torchInTheAir() {
			BlockPos torch = BlockPos.containing(user.getEyePosition()).south();
			block(torch, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH),
					Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			return torch;
		}

		// grass in the user's feet block, stone under it: {grass, stone}
		BlockPos[] grassUnderfoot() {
			BlockPos stone = feet.below();
			block(stone, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
			block(feet, Blocks.SHORT_GRASS.defaultBlockState(), Block.UPDATE_ALL);
			return new BlockPos[] { feet, stone };
		}

		// a cow with its box centred on the crosshair line, z blocks south of the eyes
		Cow cow(double z) {
			Cow cow = EntityType.COW.create(level);
			helper.assertTrue(cow != null, "Could not create a cow");
			cow.setNoAi(true);
			cow.setNoGravity(true);
			cow.moveTo(origin.x, user.getEyeY() - cow.getBbHeight() / 2.0D, origin.z + z, 0, 0);
			helper.assertTrue(level.addFreshEntity(cow), "Could not add a cow");
			entities.add(cow);
			return cow;
		}

		void block(BlockPos pos, BlockState state, int flags) {
			blocks.putIfAbsent(pos, level.getBlockState(pos));
			level.setBlock(pos, state, flags);
		}

		@Override
		public void close() {
			LivingComponentAction.getComponent(stand).setAction(null, user, SyncType.NO_SYNC);
			if (power.isSummoned()) {
				type.forceUnsummon(user, power);
			}
			entities.forEach(Entity::discard);
			List<BlockPos> placed = new ArrayList<>(blocks.keySet());
			for (int i = placed.size() - 1; i >= 0; i--) {
				level.setBlock(placed.get(i), blocks.get(placed.get(i)), Block.UPDATE_CLIENTS);
			}
		}
	}
}
