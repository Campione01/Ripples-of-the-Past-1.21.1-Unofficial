package rotp.core.gametest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.crazydiamond.CrazyDBlockBulletEntity;
import rotp.core.impl.stands.crazydiamond.CrazyDHealAbility.HealingAction;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntityTask paid a Stand action's cost at perform start and its ticking cost on every perform tick it ran,
 * both results ignored. Crazy Diamond's Block Bullet (staminaCost 40, staminaCostTick 2, one perform tick) cost 42.
 * CrazyDiamondHeal.standCanTickPerform let a Heal perform tick run, and be paid, only with an entity target; Revert
 * (CrazyDiamondPreviousState) had no such condition and paid on its item path too.
 * A ticking payment is booked for the bar's next tick (StandPower.staminaAddNextTick) unless the bar is too short.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CrazyDiamondStaminaTickGameTests {
	private static final short KEY = 11;
	private static final float EPS = 1.0E-4F;

	private CrazyDiamondStaminaTickGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void blockBulletPaysItsPerformTickToo(GameTestHelper helper) {
		scene(helper, (user, power, stand, standAction) -> {
			user.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT, 4));
			power.setStamina(41.0F);
			EntityActionInstance shot = start(helper, user, power, stand, standAction, "block_bullet", false);
			helper.assertTrue(!helper.getLevel().getEntitiesOfClass(CrazyDBlockBulletEntity.class, stand.getBoundingBox().inflate(16.0D)).isEmpty(),
					"setup: no block bullet was shot, phase " + shot.getPhase());
			helper.assertTrue(power.getStamina() == 0.0F,
					"1.16 paid 40 at perform start and 2 for the perform tick: a bar of 41 ends at 0, got " + power.getStamina());

			user.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT, 4));
			power.setStamina(power.getMaxStamina());
			setBooked(power, 0.0F);
			start(helper, user, power, stand, standAction, "block_bullet", false);
			helper.assertTrue(near(power.getStamina(), power.getMaxStamina() - 40.0F) && near(booked(power), -2.0F),
					"1.16 Block Bullet costs 40 now and 2 for its perform tick: bar " + power.getStamina() + " of "
							+ power.getMaxStamina() + ", booked for the next tick " + booked(power));
		});
	}

	// 1.16 CrazyDiamondBlockBullet.standPerform shot nothing without a block in the off hand, but the task still paid
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void blockBulletWhoseBlockIsSwappedOutStillPays(GameTestHelper helper) {
		scene(helper, (user, power, stand, standAction) -> {
			user.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT, 4));
			power.setStamina(power.getMaxStamina());
			setBooked(power, 0.0F);
			standAction.setAction(null, user, SyncType.NO_SYNC);
			EntityActionInstance shot = ((EntityActionAbility) power.getAbility("block_bullet"))
					.initActionOnAbilityUse(helper.getLevel(), user, stand, null);
			standAction.setAction(shot, user, SyncType.NO_SYNC);
			standAction.tick();
			helper.assertTrue(shot.getPhase() == ActionPhase.WINDUP, "setup: the Block Bullet is not winding up: " + shot.getPhase());
			user.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			for (int tick = 0; tick < 40 && shot.getPhase() != ActionPhase.RECOVERY && !shot.isOver(); tick++) {
				standAction.tick();
			}
			helper.assertTrue(shot.getPhase() == ActionPhase.RECOVERY || shot.isOver(),
					"setup: the Block Bullet did not get through PERFORM: " + shot.getPhase());
			helper.assertTrue(near(power.getStamina(), power.getMaxStamina() - 40.0F) && near(booked(power), -2.0F),
					"1.16 paid 40 and 2 for the perform tick though the off-hand block was swapped out: bar "
							+ power.getStamina() + " of " + power.getMaxStamina() + ", booked for the next tick " + booked(power));
		});
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void healWithoutAnEntityTargetPaysNoTickButRevertDoes(GameTestHelper helper) {
		scene(helper, (user, power, stand, standAction) -> {
			// a hurt cow on the user's line of sight, two blocks ahead
			Cow cow = helper.spawn(EntityType.COW, new Vec3(2.5D, 12.6D, 4.5D));
			cow.setNoAi(true);
			cow.setNoGravity(true);
			cow.setHealth(cow.getMaxHealth() - 4.0F);
			// the Stand, not yet ticked to its place, stands in the user's eyes and would be the target
			stand.setPos(user.getX() - 2.0D, user.getY(), user.getZ() - 2.0D);
			power.setStamina(power.getMaxStamina());
			EntityActionInstance heal = start(helper, user, power, stand, standAction, "heal", true);
			setBooked(power, 0.0F);
			standAction.tick();
			helper.assertTrue(heal.getPhase() == ActionPhase.PERFORM && near(booked(power), -1.0F),
					"setup: the held Heal on the cow did not pay 1 for its perform tick: booked " + booked(power) + ", phase " + heal.getPhase()
							+ "; " + describe(heal, helper));
			// the target goes away while the key is held
			cow.discard();
			setBooked(power, 0.0F);
			float bar = power.getStamina();
			for (int tick = 0; tick < 5; tick++) {
				standAction.tick();
			}
			helper.assertTrue(heal.getPhase() == ActionPhase.PERFORM, "setup: the held Heal left PERFORM: " + heal.getPhase());
			helper.assertTrue(near(booked(power), 0.0F) && near(power.getStamina(), bar),
					"1.16 Heal ticked and paid only with an entity target; without one it paid nothing, but booked "
							+ booked(power) + ", bar " + bar + " -> " + power.getStamina() + "; " + describe(heal, helper));
			release(user);

			// Control: Revert's first perform tick without a target is paid (0.2), on its item path.
			setBooked(power, 0.0F);
			EntityActionInstance revert = start(helper, user, power, stand, standAction, "revert_state", true);
			standAction.tick();
			helper.assertTrue(near(booked(power), -0.2F),
					"1.16 Revert paid its tick cost without a target: booked " + booked(power) + ", phase " + revert.getPhase());
		});
	}

	/*
	 * 1.16 StandEntityTask.keepTarget, every server tick: a target that is the Stand itself, dead with deathTime >= 20
	 * or out of the action's range is dropped, and without a target the Heal neither heals nor pays.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void healDropsATargetThe116TaskWouldNotKeep(GameTestHelper helper) {
		scene(helper, (user, power, stand, standAction) -> {
			Cow cow = helper.spawn(EntityType.COW, new Vec3(2.5D, 12.6D, 4.5D));
			cow.setNoAi(true);
			cow.setNoGravity(true);
			cow.setHealth(cow.getMaxHealth() - 4.0F);
			Vec3 inReach = cow.position();
			stand.setPos(user.getX() - 2.0D, user.getY(), user.getZ() - 2.0D);
			power.setStamina(power.getMaxStamina());
			EntityActionInstance heal = start(helper, user, power, stand, standAction, "heal", true);
			setBooked(power, 0.0F);
			standAction.tick();
			helper.assertTrue(heal.getPhase() == ActionPhase.PERFORM && near(booked(power), -1.0F),
					"setup: the held Heal on the cow did not pay 1 for its perform tick: booked " + booked(power) + "; " + describe(heal, helper));

			cow.teleportTo(inReach.x, inReach.y, inReach.z + 12.0D);
			float health = cow.getHealth();
			assertNoHealTick(helper, heal, power, standAction, "a cow gone 12 blocks away");
			helper.assertTrue(cow.getHealth() == health, "1.16 dropped the cow out of range, but the Heal still healed it: "
					+ health + " -> " + cow.getHealth());

			cow.teleportTo(inReach.x, inReach.y, inReach.z);
			setTarget(heal, new ActionTarget(cow));
			setBooked(power, 0.0F);
			standAction.tick();
			helper.assertTrue(near(booked(power), -1.0F),
					"setup: the Heal back on the cow in reach did not pay: booked " + booked(power) + "; " + describe(heal, helper));

			cow.deathTime = 20;
			setTarget(heal, new ActionTarget(cow));
			assertNoHealTick(helper, heal, power, standAction, "a cow with deathTime 20");
			cow.deathTime = 0;

			setTarget(heal, new ActionTarget(stand));
			assertNoHealTick(helper, heal, power, standAction, "Crazy Diamond itself");
		});
	}

	private static void assertNoHealTick(GameTestHelper helper, EntityActionInstance heal, StandPower power,
			LivingComponentAction standAction, String target) {
		setBooked(power, 0.0F);
		float bar = power.getStamina();
		for (int tick = 0; tick < 3; tick++) {
			standAction.tick();
		}
		helper.assertTrue(heal.getPhase() == ActionPhase.PERFORM, "setup: the held Heal left PERFORM: " + heal.getPhase());
		helper.assertTrue(near(booked(power), 0.0F) && near(power.getStamina(), bar),
				"1.16 dropped " + target + " as the Heal's target and paid nothing, but booked " + booked(power)
						+ ", bar " + bar + " -> " + power.getStamina() + "; " + describe(heal, helper));
	}

	private static void setTarget(EntityActionInstance action, ActionTarget target) {
		try {
			Method set = HealingAction.class.getDeclaredMethod("setActionTarget", ActionTarget.class);
			set.setAccessible(true);
			set.invoke(action, target);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private interface Body {
		void run(Player user, StandPower power, StandEntity stand, LivingComponentAction standAction);
	}

	private static void scene(GameTestHelper helper, Body body) {
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("crazy_diamond"));
		StandPower power = null;
		AABB area = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(32.0D);
		try {
			helper.assertTrue(standType != null, "Missing Crazy Diamond Stand type");
			Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 12.0D, 2.5D));
			user.setNoGravity(true);
			user.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Crazy Diamond user");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Crazy Diamond");
			power.setResolveLevel(4);
			for (String skill : new String[] { "heal", "revert_state", "block_bullet" }) {
				power.getCurTypeData()._setSkillUnlocked(skill, true, false);
			}
			helper.assertTrue(standType.summon(user, power), "Could not summon Crazy Diamond");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "The summoned Crazy Diamond is missing");
			stand.summonLockTicks = 0;
			stand.gradualSummonWeaknessTicks = 0;
			body.run(user, power, stand, LivingComponentAction.getComponent(stand));
			helper.succeed();
		}
		finally {
			release(user);
			for (CrazyDBlockBulletEntity bullet : helper.getLevel().getEntitiesOfClass(CrazyDBlockBulletEntity.class, area)) {
				bullet.discard();
			}
			if (power != null && power.isSummoned() && standType != null) {
				standType.forceUnsummon(user, power);
			}
			user.discard();
		}
	}

	// the ability set on the Stand (held by its key when it holds) and ticked into PERFORM
	private static EntityActionInstance start(GameTestHelper helper, Player user, StandPower power, StandEntity stand,
			LivingComponentAction standAction, String name, boolean held) {
		standAction.setAction(null, user, SyncType.NO_SYNC);
		Ability found = power.getAbility(name);
		helper.assertTrue(found instanceof EntityActionAbility, "Missing registered " + name);
		EntityActionInstance action = ((EntityActionAbility) found).initActionOnAbilityUse(helper.getLevel(), user, stand, null);
		standAction.setAction(action, user, SyncType.NO_SYNC);
		if (held) {
			EntityActionInputState input = user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
			input.heldKeys.put(KEY, new HeldInputEntry(KEY, 1L, PowerClass.STAND, action));
		}
		// a held action stops in PERFORM; a click one goes through its perform tick
		for (int tick = 0; tick < 40 && (action.getPhase() != ActionPhase.PERFORM || !held)
				&& action.getPhase() != ActionPhase.RECOVERY && !action.isOver(); tick++) {
			standAction.tick();
		}
		helper.assertTrue(held ? action.getPhase() == ActionPhase.PERFORM || action.getPhase() == ActionPhase.RECOVERY
				: action.getPhase() == ActionPhase.RECOVERY || action.isOver(), "the " + name + " did not get through PERFORM: phase=" + action.getPhase());
		return action;
	}

	private static String describe(EntityActionInstance action, GameTestHelper helper) {
		try {
			Method get = HealingAction.class
					.getDeclaredMethod("getActionTarget", Level.class);
			get.setAccessible(true);
			ActionTarget target = (ActionTarget) get.invoke(action, helper.getLevel());
			return action.getClass().getName() + " target " + target.getType() + " " + target.getEntity()
					+ " empty " + target.isEmpty(helper.getLevel());
		}
		catch (ReflectiveOperationException e) {
			return action.getClass().getName() + " " + e;
		}
	}

	private static void release(Player user) {
		user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
	}

	private static float booked(StandPower power) {
		try {
			return bookedField().getFloat(power);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void setBooked(StandPower power, float value) {
		try {
			bookedField().setFloat(power, value);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static Field bookedField() throws NoSuchFieldException {
		Field field = StandPower.class.getDeclaredField("staminaAddNextTick");
		field.setAccessible(true);
		return field;
	}

	private static boolean near(float actual, float expected) {
		return Math.abs(actual - expected) < EPS;
	}
}
