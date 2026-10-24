package rotp.core.gametest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility.StandEntityBarrage;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.StandEntityHeavyPunch;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility.CrosshairRule;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility.StandEntityPunch;
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

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntity.punch struck the task target while the Stand could reach it and otherwise aimed again
 * (aimWithThisOrUser with an empty target): the precision ray from the user's eyes, as far as the user's reach, entities
 * first by their category, and from the Stand's own eyes under manual control or when the user's ray met nothing. The
 * client's vanilla pick was only ever the target a press kept; a strike never took it. So after a press that kept nothing
 * (pressed at the air, or the kept target out of the Stand's reach) every punch, heavy punch, finisher and barrage strikes
 * what that ray meets, and turns to it when it is out of reach; without the aim assist, the strict crosshair of the owner's
 * rule of 2026-09-13 (only what the crosshair meets). Walls still hide entities (D-core-89). The scenes press while the
 * user looks up, then turn the user, whose synced vanilla pick the scene sets as the client sends it. Own batches.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EmptyPressStrike116GameTests {
	private static final String BATCH = "empty_press_strike_";
	private static final String[] ATTACKS = { "punch", "heavy_punch", "finisher_uppercut", "barrage", "kept_target_barrage" };

	private EmptyPressStrike116GameTests() {}

	/*
	 * Pressed at the air, then turned to a stone block on the crosshair with a cow beside the crosshair, in Star
	 * Platinum's precision boxes and before the stone: 1.16's strike aimed with the precision ray and met the cow, which
	 * every attack strikes with the aim assist; without it the strict crosshair meets the stone. The same for a punch whose
	 * kept cow has left the Stand's reach.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "stone")
	public static void attacksAfterAnEmptyPressStrikeTheCowInThePrecisionBoxesBesideTheStoneAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper)) {
			BlockPos stone = scene.stoneOnTheCrosshair(3);
			Entity cow = scene.mob(EntityType.COW, scene.user.getEyePosition().add(1.0D, 0.0D, 2.0D));
			scene.assertOffTheCrosshair(cow);
			scene.assertInStandReach(new ActionTarget(stone, Direction.NORTH));
			scene.assertInStandReach(new ActionTarget(cow));
			helper.assertTrue(StandEntityPunchAbility.userPrecisionRayTarget(scene.stand, scene.user).getEntity() == cow,
					"Fixture: the precision ray from the user's eyes does not meet the cow before the stone");
			helper.assertTrue(isBlock(scene.vanillaPick(), stone), "Fixture: the vanilla pick is not the stone: "
					+ describe(scene.vanillaPick()));
			ActionTarget atStone = new ActionTarget(stone, Direction.NORTH);
			for (boolean assist : new boolean[] { true, false }) {
				scene.aimAssist(assist);
				for (String attack : ATTACKS) {
					EntityActionInstance action = scene.pressAtTheAir(attack);
					scene.turnTo(atStone);
					ActionTarget struck = scene.strike(action);
					helper.assertTrue(assist ? struck.getEntity() == cow : isBlock(struck, stone), "The " + attack + " pressed at"
							+ " the air whose user turned to a stone block with a cow beside the crosshair struck "
							+ describe(struck) + scene.mode() + "; 1.16's strike aimed again with the precision ray and met the"
							+ " cow, the strict crosshair meets the stone at " + stone);
					scene.stop();
				}
			}
			// a second cow the punch is pressed at, which then walks off beside the user, out of the Stand's reach and the
			// precision boxes, still within the punch's range
			scene.attackTargetLock(false);
			Entity kept = scene.mob(EntityType.COW, scene.user.getEyePosition().add(0.0D, 0.0D, 2.0D));
			for (boolean assist : new boolean[] { true, false }) {
				scene.aimAssist(assist);
				kept.moveTo(scene.origin.x, scene.user.getEyeY() - kept.getBbHeight() / 2.0D, scene.origin.z + 2.0D, 0, 0);
				scene.pressAt(new ActionTarget(kept));
				EntityActionInstance punch = scene.start("punch");
				helper.assertTrue(punch.getTaskTarget(scene.level).getEntity() == kept, "Fixture: the punch pressed at the cow"
						+ " ahead kept " + describe(punch.getTaskTarget(scene.level)) + scene.mode());
				kept.moveTo(scene.origin.x - 3.0D, scene.user.getEyeY() - kept.getBbHeight() / 2.0D, scene.origin.z + 2.0D, 0, 0);
				helper.assertTrue(StandEntityPunchAbility.validatePunchTarget(scene.stand, new ActionTarget(kept)).isEmpty(
						scene.level) && StandEntityPunchAbility.keepsTargetInRange(scene.stand, new ActionTarget(kept),
								CrosshairRule.BLOCK_OR_ENTITY), "Fixture: the kept cow is in the Stand's reach or out of the punch's range");
				scene.turnTo(atStone);
				ActionTarget struck = scene.strike(punch);
				helper.assertTrue(assist ? struck.getEntity() == cow : isBlock(struck, stone), "The punch whose kept cow left"
						+ " the Stand's reach struck " + describe(struck) + scene.mode() + "; 1.16's strike aimed again with the"
						+ " precision ray and met the cow beside the crosshair, the strict crosshair meets the stone at " + stone);
				scene.stop();
			}
		}
		helper.succeed();
	}

	/*
	 * Pressed at the air, then turned to a cow on the crosshair, the user's vanilla pick, with a husk beside it in the
	 * precision boxes: 1.16's precision ray put a hostile mob before any other (StandEntity.precisionRayTrace), so every
	 * attack strikes the husk with the aim assist, the cow without it.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "priority")
	public static void attacksAfterAnEmptyPressStrikeTheHostileMobBesideTheCowUnderTheCrosshairAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper)) {
			Entity cow = scene.mob(EntityType.COW, scene.user.getEyePosition().add(0.0D, 0.0D, 2.0D));
			Entity husk = scene.mob(EntityType.HUSK, scene.user.getEyePosition().add(1.0D, 0.0D, 2.0D));
			scene.assertOffTheCrosshair(husk);
			scene.assertInStandReach(new ActionTarget(cow));
			scene.assertInStandReach(new ActionTarget(husk));
			helper.assertTrue(StandEntityPunchAbility.userPrecisionRayTarget(scene.stand, scene.user).getEntity() == husk,
					"Fixture: the precision ray from the user's eyes does not put the husk first");
			helper.assertTrue(scene.vanillaPick().getEntity() == cow, "Fixture: the vanilla pick is not the cow: "
					+ describe(scene.vanillaPick()));
			for (boolean assist : new boolean[] { true, false }) {
				scene.aimAssist(assist);
				for (String attack : ATTACKS) {
					EntityActionInstance action = scene.pressAtTheAir(attack);
					scene.turnTo(new ActionTarget(cow));
					ActionTarget struck = scene.strike(action);
					helper.assertTrue(struck.getEntity() == (assist ? husk : cow), "The " + attack + " pressed at the air whose"
							+ " user turned to a cow with a husk beside it struck " + describe(struck) + scene.mode() + "; 1.16's"
							+ " strike aimed again with the precision ray, a hostile mob first, the strict crosshair meets the cow");
					scene.stop();
				}
			}
		}
		helper.succeed();
	}

	/*
	 * The husk of the scene above out of the Stand's reach, the cow under the crosshair in it: 1.16's precision ray met the
	 * husk, which the Stand could not reach, so the strike missed and the Stand turned to the husk
	 * (aimWithThisOrUser.rotateTowards). Without the aim assist the strict crosshair takes the cow.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "miss")
	public static void attacksAfterAnEmptyPressMissAndTurnToTheHostileMobOutOfReachAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper)) {
			Entity cow = scene.mob(EntityType.COW, scene.user.getEyePosition().add(0.0D, 0.0D, 1.5D));
			Entity husk = scene.mob(EntityType.HUSK, scene.user.getEyePosition().add(1.0D, 0.0D, 3.5D));
			scene.assertOffTheCrosshair(husk);
			scene.assertInStandReach(new ActionTarget(cow));
			helper.assertTrue(StandEntityPunchAbility.validatePunchTarget(scene.stand, new ActionTarget(husk)).isEmpty(scene.level),
					"Fixture: the husk is in the Stand's reach");
			helper.assertTrue(StandEntityPunchAbility.userPrecisionRayTarget(scene.stand, scene.user).getEntity() == husk,
					"Fixture: the precision ray from the user's eyes does not put the husk first");
			for (boolean assist : new boolean[] { true, false }) {
				scene.aimAssist(assist);
				for (String attack : ATTACKS) {
					EntityActionInstance action = scene.pressAtTheAir(attack);
					scene.turnTo(new ActionTarget(cow));
					ActionTarget struck = scene.strike(action);
					CrosshairRule rule = action instanceof StandEntityHeavyPunch ? CrosshairRule.ENTITY
							: attack.equals("barrage") ? CrosshairRule.NONE : CrosshairRule.BLOCK_OR_ENTITY;
					ActionTarget turnedTo = StandEntityPunchAbility.strikeAim(scene.stand, struck, rule);
					helper.assertTrue(assist ? struck.isEmpty(scene.level) && turnedTo.getEntity() == husk
							: struck.getEntity() == cow, "The " + attack + " pressed at the air whose user turned to a cow with a"
							+ " husk beyond the Stand's reach beside it struck " + describe(struck) + " and turned to "
							+ describe(turnedTo) + scene.mode() + "; 1.16's precision ray met the husk out of reach, so the strike"
							+ " missed and the Stand turned to it; the strict crosshair meets the cow");
					scene.stop();
				}
			}
		}
		helper.succeed();
	}

	/*
	 * Under manual control 1.16's strike aimed with the precision ray from the Stand's own eyes (aimWithThisOrUser), not
	 * with the pick of the Stand's crosshair a client sends: pressed at the air, then turned to a stone block with a cow in
	 * its precision boxes beside its crosshair, every attack strikes the cow; without the aim assist, the stone.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "manual")
	public static void aManuallyControlledStandsAttacksAfterAnEmptyPressStrikeTheCowBesideTheStoneAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper)) {
			scene.stand.moveTo(scene.origin.x + 3.0D, scene.user.getEyeY() - scene.stand.getEyeHeight(), scene.origin.z, 0, 0);
			scene.stand.setYHeadRot(0);
			scene.stand.setManuallyControlled(true);
			scene.user.setYRot(180.0F);
			scene.user.setYHeadRot(180.0F);
			helper.assertTrue(scene.stand.aimsWithoutUser(), "Fixture: the Stand is not manually controlled");
			Vec3 standEyes = scene.stand.getEyePosition();
			BlockPos stone = BlockPos.containing(standEyes).south(2);
			scene.block(stone, Blocks.STONE.defaultBlockState());
			Entity cow = scene.mob(EntityType.COW, standEyes.add(1.0D, 0.0D, 1.2D));
			ActionTarget atStone = new ActionTarget(stone, Direction.NORTH);
			scene.assertInStandReach(atStone);
			scene.assertInStandReach(new ActionTarget(cow));
			helper.assertTrue(cow.getBoundingBox().clip(standEyes, standEyes.add(scene.stand.getLookAngle().scale(5.0D))).isEmpty(),
					"Fixture: the cow is on the Stand's crosshair line");
			for (boolean assist : new boolean[] { true, false }) {
				scene.aimAssist(assist);
				for (String attack : ATTACKS) {
					scene.attackTargetLock(attack.equals("kept_target_barrage"));
					scene.stand.setXRot(-90.0F);
					scene.pressAt(ActionTarget.EMPTY);
					EntityActionInstance action = scene.start(attack.equals("kept_target_barrage") ? "barrage" : attack);
					helper.assertTrue(action.getTaskTarget(scene.level).isEmpty(scene.level), "Fixture: the " + attack
							+ " pressed with the Stand looking up kept " + describe(action.getTaskTarget(scene.level)) + scene.mode());
					scene.stand.setXRot(0.0F);
					scene.pressAt(atStone);
					ActionTarget struck = scene.strike(action);
					helper.assertTrue(assist ? struck.getEntity() == cow : isBlock(struck, stone), "The manually controlled Stand's "
							+ attack + " pressed at the air, then turned to a stone block with a cow beside its crosshair, struck "
							+ describe(struck) + scene.mode() + "; 1.16's strike aimed with the precision ray from the Stand's eyes"
							+ " and met the cow, the Stand's strict crosshair meets the stone at " + stone);
					scene.stop();
				}
			}
			scene.attackTargetLock(false);
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

	private static final class Scene implements AutoCloseable {
		final GameTestHelper helper;
		final ServerLevel level;
		final Player user;
		final StandPower power;
		final StandType type;
		final StandEntity stand;
		final Vec3 origin;
		final List<Entity> entities = new ArrayList<>();
		final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
		private boolean assist = true;

		// a Survival user floating at the origin, looking south; Star Platinum's eyes level with the user's, 0.35 south,
		// past the user's box, looking the same way
		Scene(GameTestHelper helper) {
			this.helper = helper;
			level = helper.getLevel();
			origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 3, 1)));
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			user.setNoGravity(true);
			user.moveTo(origin.x, origin.y, origin.z, 0, 0);
			user.setYHeadRot(0);
			helper.assertTrue(level.addFreshEntity(user), "Could not add the Stand user");
			entities.add(user);
			power = PowerClass.STAND.attachGet(user);
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			helper.assertTrue(type.summon(user, power), "Could not summon Star Platinum");
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null && stand.getUser() == user, "Missing the Star Platinum entity");
			entities.add(stand);
			stand.setCanFollowUser(false);
			stand.setNoGravity(true);
			stand.moveTo(origin.x, user.getEyeY() - stand.getEyeHeight(), origin.z + 0.35D, 0, 0);
			stand.setYHeadRot(0);
			power.setStamina(power.getMaxStamina());
			pressAt(ActionTarget.EMPTY);
			aimAssist(true);
			attackTargetLock(false);
		}

		void aimAssist(boolean on) {
			assist = on;
			user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAimAssist = on;
		}

		// the user's "keep attack target" setting (the barrage keeps what it was pressed at)
		void attackTargetLock(boolean on) {
			user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock = on;
		}

		String mode() {
			return " (aim assist " + (assist ? "on" : "off") + ")";
		}

		// the client's vanilla pick as the press packet stores it, the user's aim and the Stand's
		void pressAt(ActionTarget aim) {
			LivingComponentAction.getComponent(user).entityAim.setTarget(aim);
			LivingComponentAction.getComponent(stand).entityAim.setTarget(aim);
		}

		// the attack pressed while its user looks up at nothing, which keeps no target
		EntityActionInstance pressAtTheAir(String attack) {
			boolean keepsTarget = attack.equals("kept_target_barrage");
			attackTargetLock(keepsTarget);
			user.setXRot(-90.0F);
			pressAt(ActionTarget.EMPTY);
			EntityActionInstance action = start(keepsTarget ? "barrage" : attack);
			helper.assertTrue(action.getTaskTarget(level).isEmpty(level), "Fixture: the " + attack + " pressed at the air kept "
					+ describe(action.getTaskTarget(level)) + mode());
			return action;
		}

		// the user looks south again, its client's vanilla pick the given target
		void turnTo(ActionTarget vanillaPick) {
			user.setXRot(0.0F);
			pressAt(vanillaPick);
		}

		ActionTarget vanillaPick() {
			return StandEntityPunchAbility.crosshairPick(stand, user, 1.0F, entity -> entity != stand);
		}

		EntityActionInstance start(String name) {
			Ability ability = power.getAbility(name);
			helper.assertTrue(ability instanceof EntityActionType, "Fixture: Star Platinum has no action " + name);
			EntityActionInstance action = ((EntityActionType) ability).initActionOnAbilityUse(level, user, stand, null);
			helper.assertTrue(action != null, "Fixture: " + name + " made no action");
			LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
			helper.assertTrue(LivingComponentAction.getCurEntityAction(stand) == action, "Fixture: " + name + " did not start");
			return action;
		}

		void stop() {
			LivingComponentAction.getComponent(stand).setAction(null, user, SyncType.NO_SYNC);
		}

		// the target the attack strikes now (its getPunchTarget, which 1.16's StandEntity.punch aimed)
		ActionTarget strike(EntityActionInstance attack) {
			Class<?> owner = attack instanceof StandEntityPunch ? StandEntityPunch.class
					: attack instanceof StandEntityHeavyPunch ? StandEntityHeavyPunch.class
					: attack instanceof StandEntityBarrage ? StandEntityBarrage.class : null;
			helper.assertTrue(owner != null, "Fixture: not a punch, heavy punch or barrage: " + attack);
			try {
				Method method = owner.getDeclaredMethod("getPunchTarget", StandEntity.class);
				method.setAccessible(true);
				return method.invoke(attack, stand) instanceof ActionTarget target ? target : ActionTarget.EMPTY;
			}
			catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
				throw new IllegalStateException("Fixture: getPunchTarget of " + attack + " failed", e);
			}
		}

		void assertInStandReach(ActionTarget target) {
			helper.assertTrue(!StandEntityPunchAbility.validatePunchTarget(stand, target).isEmpty(level),
					"Fixture: " + describe(target) + " is out of the Stand's reach");
		}

		void assertOffTheCrosshair(Entity entity) {
			Vec3 eyes = user.getEyePosition();
			helper.assertTrue(entity.getBoundingBox().clip(eyes, eyes.add(user.getLookAngle().scale(5.0D))).isEmpty(),
					"Fixture: " + entity + " is on the crosshair line");
		}

		// stone on the crosshair line, its near face distance - 0.5 blocks ahead of the eyes
		BlockPos stoneOnTheCrosshair(int distance) {
			BlockPos stone = BlockPos.containing(user.getEyePosition()).south(distance);
			block(stone, Blocks.STONE.defaultBlockState());
			return stone;
		}

		// a mob with its box centred on the point
		Entity mob(EntityType<? extends Mob> entityType, Vec3 at) {
			Mob mob = entityType.create(level);
			helper.assertTrue(mob != null, "Could not create " + entityType);
			mob.setNoAi(true);
			mob.setNoGravity(true);
			mob.moveTo(at.x, at.y - mob.getBbHeight() / 2.0D, at.z, 0, 0);
			helper.assertTrue(level.addFreshEntity(mob), "Could not add " + mob);
			entities.add(mob);
			return mob;
		}

		void block(BlockPos pos, BlockState state) {
			blocks.putIfAbsent(pos, level.getBlockState(pos));
			level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}

		@Override
		public void close() {
			stop();
			pressAt(ActionTarget.EMPTY);
			attackTargetLock(false);
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
