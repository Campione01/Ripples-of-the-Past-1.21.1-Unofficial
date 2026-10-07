package rotp.core.gametest;

import java.lang.reflect.Method;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.powers.vampirism.entity.HungryZombieEntity;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility.StandEntityPunch;
import rotp.core.impl.stands.goldexperience.GoldExperienceCreateLifeformAbility;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.DamageUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntity.attackEntity: a successful hit on a punch's main target, by a Stand that is not manually
 * controlled, is recorded as the last-hurt mob of the Stand and of its user. Sweep targets (doAttack) are not.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandAttackLastHurtMemoryGameTests {
	private StandAttackLastHurtMemoryGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void standPunchRecordsLastHurtMob(GameTestHelper helper) {
		Scene scene = new Scene(helper, "StandHurtMemory");
		try {
			Cow main = scene.cow(0.0D);
			Cow swept = scene.cow(1.0D);
			Cow manual = scene.cow(-1.0D);
			StandEntity stand = scene.stand;
			FakePlayer user = scene.user;
			helper.assertTrue(stand.getLastHurtMob() == null && user.getLastHurtMob() == null,
					"Fixture: the Stand or its user already has a last-hurt mob");

			scene.punch(main);
			helper.assertTrue(main.getHealth() < main.getMaxHealth(), "Fixture: the punch did not hurt its target");
			helper.assertTrue(stand.getLastHurtMob() == main,
					"1.16: a Stand punch records its target as the Stand's last-hurt mob, but it is " + stand.getLastHurtMob());
			helper.assertTrue(user.getLastHurtMob() == main,
					"1.16: a Stand punch records its target as the user's last-hurt mob, but it is " + user.getLastHurtMob());
			helper.assertTrue(lifeformHurtTarget(user, stand) == main,
					"Gold Experience's lifeform does not read the Stand-punched enemy as its follow target");

			// 1.16 doAttack on a sweep target bypasses attackEntity
			boolean hurt = EntityActionInstance.standEntityAttack(stand, swept,
					DamageUtil.make(helper.getLevel(), ModDamageTypes.STAND_ATTACK, stand), 2.0F, false);
			helper.assertTrue(hurt && swept.getHealth() < swept.getMaxHealth(), "Fixture: the sweep hit did not hurt");
			helper.assertTrue(stand.getLastHurtMob() == main && user.getLastHurtMob() == main,
					"1.16: a sweep target is not recorded as the last-hurt mob");

			stand.setManuallyControlled(true);
			helper.assertTrue(stand.isManuallyControlled(), "Fixture: manual control did not start");
			scene.punch(manual);
			helper.assertTrue(manual.getHealth() < manual.getMaxHealth(), "Fixture: the manual punch did not hurt its target");
			helper.assertTrue(stand.getLastHurtMob() == main && user.getLastHurtMob() == main,
					"1.16: a manually controlled Stand's punch is not recorded as the last-hurt mob");
			helper.succeed();
		}
		finally {
			scene.close();
		}
	}

	/**
	 * 1.16 HungryZombieOwnerHurtTargetGoal reads the owner's last-hurt mob; a vampire's Stand punch feeds it once the
	 * victim's last damage source has expired (LivingEntity.getLastDamageSource, 40 ticks).
	 */
	@GameTest(template = "empty", timeoutTicks = 120)
	public static void hungryZombieOwnerGoalAdmitsStandPunchedTarget(GameTestHelper helper) {
		Scene scene = new Scene(helper, "StandHurtZombie");
		try {
			helper.assertTrue(helper.getLevel().getDifficulty() != Difficulty.PEACEFUL, "Fixture: the level is peaceful");
			FakePlayer owner = scene.user;
			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(owner);
			helper.assertTrue(power.trySetPowerType(ModPlayerPowers.VAMPIRISM.get()), "Could not make the owner a vampire");
			scene.playerPower = power;
			PlayerPower.getPowerData(owner, ModPlayerPowers.VAMPIRISM).orElseThrow().setVampireFullPower(true, owner);

			HungryZombieEntity zombie = new HungryZombieEntity(helper.getLevel());
			scene.owned(zombie);
			zombie.setNoAi(true);
			zombie.setInvulnerable(true);
			zombie.moveTo(scene.origin.x + 1.5D, scene.origin.y, scene.origin.z, 0, 0);
			zombie.setOwner(owner);
			helper.assertTrue(helper.getLevel().addFreshEntity(zombie), "Could not add the hungry zombie");
			helper.assertTrue(zombie.getOwner() == owner, "Fixture: the zombie does not resolve its owner");
			Cow cow = scene.cow(0.0D);
			Goal goal = ownerHurtTargetGoal(helper, zombie);

			helper.runAfterDelay(3, () -> scene.guarded(() -> {
				helper.assertTrue(owner.tickCount > 0 && zombie.getOwner() == owner && zombie.getTarget() == null,
						"Fixture: the owner does not tick, or the zombie lost its owner or has a target");
				scene.punch(cow);
				LivingComponentAction.getComponent(scene.stand).setAction(null, SyncType.NO_SYNC);
				helper.assertTrue(cow.isAlive() && cow.getHealth() < cow.getMaxHealth(), "Fixture: the punch did not hurt the cow");
				helper.assertFalse(goal.canUse(), "1.16: the goal waits while the victim's last damage source is a Stand hit");
			}));
			helper.runAfterDelay(48, () -> scene.guarded(() -> {
				helper.assertTrue(cow.isAlive() && cow.getLastDamageSource() == null,
						"Fixture: the cow's last damage source has not expired");
				helper.assertTrue(goal.canUse(),
						"1.16: the hungry zombie's owner goal admits the target its owner's Stand punched; owner memory "
								+ owner.getLastHurtMob());
				goal.start();
				helper.assertTrue(zombie.getTarget() == cow, "The owner goal did not target the Stand-punched cow");
				scene.close();
				helper.succeed();
			}));
		}
		catch (RuntimeException | Error error) {
			scene.close();
			throw error;
		}
	}

	private static Goal ownerHurtTargetGoal(GameTestHelper helper, HungryZombieEntity zombie) {
		Goal found = null;
		for (WrappedGoal wrapped : zombie.targetSelector.getAvailableGoals()) {
			if (wrapped.getGoal().getClass().getSimpleName().equals("HungryZombieOwnerHurtTargetGoal")) {
				found = wrapped.getGoal();
			}
		}
		helper.assertTrue(found != null, "Missing the registered owner-hurt-target goal");
		return found;
	}

	private static LivingEntity lifeformHurtTarget(LivingEntity user, StandEntity stand) {
		try {
			Method method = GoldExperienceCreateLifeformAbility.class.getDeclaredMethod("getLastHurtTarget",
					LivingEntity.class, StandEntity.class);
			method.setAccessible(true);
			return (LivingEntity) method.invoke(null, user, stand);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Could not read Gold Experience's last hurt target", e);
		}
	}

	/** A player with a summoned Star Platinum facing south, and what the test added. */
	private static final class Scene {
		private final GameTestHelper helper;
		private final ServerLevel level;
		private final java.util.List<Entity> owned = new java.util.ArrayList<>();
		final Vec3 origin;
		final FakePlayer user;
		StandType standType;
		StandPower power;
		StandEntity stand;
		PlayerPower playerPower;
		private boolean closed;

		Scene(GameTestHelper helper, String name) {
			this.helper = helper;
			this.level = helper.getLevel();
			this.origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 1)));
			this.user = new FakePlayer(level, new GameProfile(UUID.randomUUID(), name));
			owned.add(user);
			try {
				user.setGameMode(GameType.SURVIVAL);
				user.moveTo(origin.x, origin.y, origin.z, 0, 0);
				helper.assertTrue(level.addFreshEntity(user), "Could not add the Stand user");
				helper.assertTrue(level.getPlayerByUUID(user.getUUID()) == user, "The Stand user is not a level player");
				standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
				helper.assertTrue(standType != null, "Missing registered Star Platinum");
				power = PowerClass.STAND.attachGet(user);
				helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
						== StandPowerTransitions.Status.APPLIED && standType.summon(user, power), "Could not summon Star Platinum");
				stand = power.getSummonedStandEntity();
				helper.assertTrue(stand != null && stand.getUser() == user, "The summoned Stand is missing its user");
				stand.moveTo(origin.x, origin.y, origin.z, 0, 0);
			}
			catch (RuntimeException | Error error) {
				close();
				throw error;
			}
		}

		void owned(Entity entity) {
			owned.add(entity);
		}

		// in front of the Stand, shifted sideways
		Cow cow(double xOffset) {
			Cow cow = EntityType.COW.create(level);
			helper.assertTrue(cow != null, "Could not create the cow");
			owned.add(cow);
			cow.setNoAi(true);
			cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
			cow.setHealth(200);
			cow.moveTo(origin.x + xOffset, origin.y, origin.z + 1.5D, 180, 0);
			helper.assertTrue(level.addFreshEntity(cow), "Could not add the cow");
			return cow;
		}

		// the production light punch's hit on its main target
		void punch(Entity target) {
			Ability ability = power.getAbility("punch");
			helper.assertTrue(ability instanceof EntityActionType, "Missing production action punch");
			power.setStamina(power.getMaxStamina());
			EntityActionInstance action = ((EntityActionType) ability).initActionOnAbilityUse(level, user, stand, null);
			// sets the performer and power user the damage source reads
			LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
			helper.assertTrue(action instanceof StandEntityPunch, "Star Platinum punch is not a light punch: " + action);
			try {
				Method method = StandEntityPunch.class.getDeclaredMethod("hitEntity", ActionTarget.class, Level.class, StandEntity.class);
				method.setAccessible(true);
				method.invoke(action, new ActionTarget(target), level, stand);
			}
			catch (ReflectiveOperationException e) {
				throw new IllegalStateException("Could not run the light punch hitEntity", e);
			}
		}

		void guarded(Runnable step) {
			try {
				step.run();
			}
			catch (RuntimeException | Error error) {
				close();
				throw error;
			}
		}

		void close() {
			if (closed) {
				return;
			}
			closed = true;
			if (stand != null) {
				LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
			}
			if (power != null && standType != null && power.isSummoned()) {
				standType.forceUnsummon(user, power);
			}
			if (playerPower != null) {
				playerPower.setPowerType(null);
			}
			for (Entity entity : owned) {
				if (!entity.isRemoved()) {
					entity.discard();
				}
			}
		}
	}
}
