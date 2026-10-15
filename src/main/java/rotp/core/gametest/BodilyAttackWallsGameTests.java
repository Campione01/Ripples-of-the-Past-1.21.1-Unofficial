package rotp.core.gametest;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.silverchariot.SilverChariotDashAttackAbility.DashStrike;
import rotp.core.impl.stands.theworld.TheWorldKickAbility.TheWorldKick;
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

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Owner, 2026-10-15 (ninth card): the bodily attacks of ordinary Stands are stopped by walls. Silver Chariot's
 * sweeping and dash attacks and The World kick's sweep hit nothing a wall hides from the Stand; in 1.16 they hit
 * through blocks (SilverChariotSweepingAttack 67, SilverChariotDashAttack 75, StandEntityPunch's sweep).
 * The Stand stands at block (2, 2, 2) looking south (+Z).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BodilyAttackWallsGameTests {
	private BodilyAttackWallsGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void chariotSweepSkipsAMobBehindAWall(GameTestHelper helper) {
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		try (Scene scene = new Scene(helper, "silver_chariot", pos)) {
			scene.wall(2, 3, Blocks.STONE);
			Mob open = scene.mob(EntityType.PIG, pos.add(-2.5, 0, 1.5));
			Mob hidden = scene.mob(EntityType.PIG, pos.add(0, 0, 2.3));
			EntityActionInstance sweep = scene.start("sweeping_attack");
			scene.face(pos);
			helper.assertTrue(scene.stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) >= 3,
					"premise: the sweep area reaches both pigs");
			sweep.actionPerformStart();
			helper.assertTrue(hurt(open), "The sweep did not hurt the pig in the open");
			helper.assertFalse(hurt(hidden), "The sweep hurt the pig behind the wall");
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void chariotDashStopsAtAWallOnItsPath(GameTestHelper helper) {
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		try (Scene scene = new Scene(helper, "silver_chariot", pos)) {
			// bedrock: the dash strikes a wall it meets with nothing before it, and this one has to stay
			scene.wall(2, 4, Blocks.BEDROCK);
			EntityActionInstance dash = scene.start("dash_attack");
			helper.assertTrue(dash instanceof DashStrike, "dash_attack is not a DashStrike");
			scene.face(pos);
			// the pig's middle on the rapier's line, at the Stand's eye height
			Mob hidden = scene.mob(EntityType.PIG, new Vec3(pos.x, scene.stand.getEyeY() - 0.45, pos.z + 3.0));
			hidden.setNoGravity(true);
			helper.assertTrue(scene.stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) >= 3.5,
					"premise: the dash reaches the pig");
			dashTargets(dash, scene.stand);
			helper.assertFalse(hurt(hidden), "The dash hurt the pig behind the wall on its path");

			scene.wall(2, 4, Blocks.AIR);
			scene.face(pos);
			dashTargets(dash, scene.stand);
			helper.assertTrue(hurt(hidden), "premise: without the wall the dash hurts the pig on its path");
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void theWorldKickSweepSkipsAMobBehindAWall(GameTestHelper helper) {
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		try (Scene scene = new Scene(helper, "the_world", pos)) {
			scene.wall(3, 3, Blocks.STONE);
			Mob primary = scene.mob(EntityType.COW, pos.add(0.1, 0, 2.0));
			Mob open = scene.mob(EntityType.COW, pos.add(-1.0, 0, 2.4));
			Mob hidden = scene.mob(EntityType.COW, pos.add(1.2, 0, 2.1));
			helper.assertTrue(primary.getBoundingBox().inflate(0.5, 0, 0.5).intersects(open.getBoundingBox())
					&& primary.getBoundingBox().inflate(0.5, 0, 0.5).intersects(hidden.getBoundingBox()),
					"premise: both cows stand inside the kick's sweep around the primary target");
			Ability kick = scene.power.getMoveset().getAbility("kick");
			helper.assertTrue(kick instanceof EntityActionType, "Missing entity action kick");
			EntityActionInstance action = ((EntityActionType) kick).initActionOnAbilityUse(
					helper.getLevel(), scene.user, scene.stand, null);
			helper.assertTrue(action instanceof TheWorldKick, "kick is not The World's kick");
			LivingComponentAction.getComponent(scene.stand).setAction(action, scene.user, SyncType.NO_SYNC);
			scene.face(pos);
			kickSweep(action, primary, scene.stand);
			helper.assertTrue(hurt(open), "The kick's sweep did not hurt the cow in the open");
			helper.assertFalse(hurt(hidden), "The kick's sweep hurt the cow behind the wall");
		}
		helper.succeed();
	}

	private static boolean hurt(LivingEntity entity) {
		return entity.getHealth() < entity.getMaxHealth();
	}

	private static void dashTargets(EntityActionInstance dash, StandEntity stand) {
		try {
			Method hit = DashStrike.class.getDeclaredMethod("hitDashTargets", Level.class, StandEntity.class);
			hit.setAccessible(true);
			hit.invoke(dash, stand.level(), stand);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Could not run the dash's ray hit", e);
		}
	}

	private static void kickSweep(EntityActionInstance kick, Entity primary, StandEntity stand) {
		try {
			Method sweep = TheWorldKick.class.getDeclaredMethod("sweepTargetsAroundKick",
					Entity.class, Level.class, StandEntity.class, float.class);
			sweep.setAccessible(true);
			sweep.invoke(kick, primary, stand.level(), stand, 7.5F);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Could not run the kick's sweep", e);
		}
	}

	private static final class Scene implements AutoCloseable {
		final GameTestHelper helper;
		final StandType type;
		final Player user;
		final StandPower power;
		final StandEntity stand;
		final List<Entity> mobs = new ArrayList<>();
		final List<BlockPos> walls = new ArrayList<>();

		Scene(GameTestHelper helper, String standId, Vec3 pos) {
			this.helper = helper;
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standId));
			helper.assertTrue(type != null, "Missing registered Stand " + standId);
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			user.moveTo(pos.x, pos.y, pos.z - 2, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add a player");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant " + standId);
			power.setResolveLevel(power.getMaxResolveLevel());
			power.getCurTypeData()._setSkillUnlocked("sweeping_attack", true, false);
			helper.assertTrue(type.summon(user, power), "Could not summon " + standId);
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null && stand.getUser() == user, "The summoned Stand is missing its user");
			stand.setCanFollowUser(false);
			stand.setNoGravity(true);
			LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
			power.setStamina(power.getMaxStamina());
		}

		EntityActionInstance start(String abilityName) {
			Ability ability = power.getAbility(abilityName);
			helper.assertTrue(ability instanceof EntityActionType, "Missing entity action " + abilityName);
			EntityActionInstance action = ((EntityActionType) ability).initActionOnAbilityUse(
					helper.getLevel(), user, stand, null);
			LivingComponentAction actions = LivingComponentAction.getComponent(stand);
			actions.setAction(action, user, SyncType.NO_SYNC);
			helper.assertTrue(actions.getAction() == action, abilityName + " was not set on the Stand");
			return action;
		}

		// a column three blocks high at relative x, z, from the Stand's feet up past its eyes
		void wall(int x, int z, Block block) {
			for (int y = 2; y <= 4; y++) {
				BlockPos at = new BlockPos(x, y, z);
				helper.setBlock(at, block);
				walls.add(at);
			}
		}

		void face(Vec3 standPos) {
			stand.moveTo(standPos.x, standPos.y, standPos.z, 0, 0);
			stand.setYHeadRot(0);
			stand.setYBodyRot(0);
		}

		Mob mob(EntityType<? extends Mob> entityType, Vec3 at) {
			Mob mob = entityType.create(helper.getLevel());
			helper.assertTrue(mob != null, "Could not create a mob");
			mob.setNoAi(true);
			mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
			mob.setHealth(200);
			mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
			mob.moveTo(at.x, at.y, at.z, 180, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(mob), "Could not add a mob");
			mobs.add(mob);
			return mob;
		}

		@Override
		public void close() {
			LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
			if (power.isSummoned()) {
				type.forceUnsummon(user, power);
			}
			user.discard();
			mobs.forEach(Entity::discard);
			walls.forEach(at -> helper.setBlock(at, Blocks.AIR));
		}
	}
}
