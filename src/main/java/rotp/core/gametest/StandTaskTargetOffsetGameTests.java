package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.hierophant.HierophantStringAttackAbility.StringAttackShot;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.MathUtil;
import io.netty.buffer.Unpooled;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntity.taskOffset: while the current task's action locks on its target (StandEntityAction.lockOnTargetPosition,
 * true unless overridden) and that target has a position, every offset of the task, the default one included, is turned
 * by the yaw and pitch from the user's eyes to the target; otherwise by the action's yRotForOffset (the view yaw unless
 * overridden) and the user's pitch. The pitch reaches only an offset turned by it (withXRot). A task held a target only
 * if its action kept it (StandEntityTask.keepTarget), and a new task took the previous one's target when it kept that.
 * The light and heavy attacks never turned their offset; Previous State did not either, and turned it by the body yaw
 * without a target, as Repair Item always did.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandTaskTargetOffsetGameTests {
	private static final double EPS = 1.0E-4D;
	// the user looks south (+z) with its body turned away, a cow 3 blocks east (+x) of it
	private static final float VIEW_YAW = 0.0F;
	private static final float BODY_YAW = 50.0F;
	private static final Vec3 COW_FROM_USER = new Vec3(3.0D, 0.0D, 0.0D);

	private StandTaskTargetOffsetGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void crazyDiamondHealsAMobWithItsOffsetTurnedToTheMobAsIn116(GameTestHelper helper) {
		withStand(helper, "crazy_diamond", scene -> {
			Cow cow = scene.cow(COW_FROM_USER);
			cow.setHealth(cow.getMaxHealth() - 4.0F);
			userAims(scene.user, cow);
			EntityActionInstance heal = scene.start("heal", ActionPhase.PERFORM);
			turn(scene.user, VIEW_YAW, BODY_YAW);
			LivingComponentAction.getComponent(scene.stand).tick();
			Vec3 relative = scene.stand.offsetFromUser.getRelativeOffset();
			helper.assertTrue(heal.getTaskTarget(helper.getLevel()).getEntity() == cow && relative.z > 0.5D,
					"setup: the heal holds " + heal.getTaskTarget(helper.getLevel()) + " with the offset " + relative
							+ ", not the wounded cow it heals in front of the user");
			// offsetToTarget's withXRot(0, front): the whole offset along the line from the user's eyes to the cow's
			double front = Math.min(relative.z, scene.stand.getMaxRangeForMovement(scene.user));
			Vec3 expected = cow.getEyePosition().subtract(scene.user.getEyePosition()).normalize().scale(front);
			assertAt(helper, scene, expected, "1.16 put a crazy_diamond healing a cow 3 blocks east of its user who looks"
					+ " south, the offset turned from the user's eyes to the cow's (lockOnTargetPosition)");
		});
		helper.succeed();
	}

	/*
	 * 1.16 GoldExperienceHealOther (TargetRequirement.ENTITY): its task held the entity it was pressed at, the default
	 * offset turned to it, and standPerform healed that entity (task.getTarget()), whatever the crosshair met by then
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void goldExperienceHealsTheMobItWasPressedAtWithItsOffsetTurnedToItAsIn116(GameTestHelper helper) {
		withStand(helper, "gold_experience", scene -> {
			Cow pressedAt = scene.cow(COW_FROM_USER);
			Cow aimedAfter = scene.cow(new Vec3(-3.0D, 0.0D, 1.0D));
			pressedAt.setHealth(pressedAt.getMaxHealth() - 4.0F);
			aimedAfter.setHealth(aimedAfter.getMaxHealth() - 4.0F);
			scene.user.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_INGOT, 2));
			userAims(scene.user, pressedAt);
			EntityActionInstance heal = scene.set("heal_other");
			turn(scene.user, VIEW_YAW, BODY_YAW);
			helper.assertTrue(heal.getTaskTarget(helper.getLevel()).getEntity() == pressedAt,
					"1.16 GoldExperienceHealOther's task held the wounded cow it was pressed at; it holds "
							+ heal.getTaskTarget(helper.getLevel()));
			Vec3 toCow = pressedAt.getEyePosition().subtract(scene.user.getEyePosition());
			Vec3 expected = scene.stand.offsetFromUser.getRelativeOffset()
					.yRot(-MathUtil.yRotDegFromVec(toCow) * MathUtil.DEG_TO_RAD);
			assertAt(helper, scene, expected, "1.16 put a gold_experience healing a cow 3 blocks east of its user who looks"
					+ " south, the default offset turned by the yaw to the cow");

			userAims(scene.user, aimedAfter);
			LivingComponentAction.getComponent(scene.stand).tick();
			helper.assertTrue(pressedAt.hasEffect(MobEffects.REGENERATION) && !aimedAfter.hasEffect(MobEffects.REGENERATION),
					"1.16 GoldExperienceHealOther.standPerform healed the cow its task held, not the one aimed at since;"
							+ " regeneration on the pressed cow " + pressedAt.hasEffect(MobEffects.REGENERATION)
							+ ", on the one aimed at after " + aimedAfter.hasEffect(MobEffects.REGENERATION));
		});
		helper.succeed();
	}

	// the port heals the user's own pets, which 1.16 refused (canStandTargetEntity); its task holds what it heals
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void goldExperienceHealOtherHoldsTheUsersOwnWolfItHeals(GameTestHelper helper) {
		// a server player, which the level finds as the wolf's owner
		FakePlayer owner = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "StandTaskTargetOwner"));
		owner.setGameMode(GameType.SURVIVAL);
		withStand(helper, "gold_experience", owner, scene -> {
			Wolf wolf = scene.mob(EntityType.WOLF, COW_FROM_USER);
			wolf.setTame(true, false);
			wolf.setOwnerUUID(scene.user.getUUID());
			wolf.setHealth(wolf.getMaxHealth() - 4.0F);
			helper.assertTrue(wolf.getOwner() == scene.user && !scene.stand.canAttack(wolf),
					"setup: the wolf is not the user's own one the Stand may not attack");
			LivingComponentAction.getComponent(scene.user).entityAim.setTarget(new ActionTarget(wolf));
			EntityActionInstance heal = scene.set("heal_other");
			helper.assertTrue(heal.getTaskTarget(helper.getLevel()).getEntity() == wolf,
					"Heal Other pressed at its user's own wounded wolf holds " + heal.getTaskTarget(helper.getLevel())
							+ ", not the wolf it heals");
		});
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void stringAttackTurnsItsDefaultOffsetToItsTargetAndPassesItOnAsIn116(GameTestHelper helper) {
		withStand(helper, "hierophant_green", scene -> {
			Cow cow = scene.cow(COW_FROM_USER);
			standAims(scene.stand, cow);
			EntityActionInstance first = scene.start("string_attack", ActionPhase.PERFORM);
			turn(scene.user, VIEW_YAW, BODY_YAW);
			helper.assertTrue(first.getTaskTarget(helper.getLevel()).getEntity() == cow,
					"1.16 HierophantGreenStringAttack.standKeepsTarget kept the cow it was pressed at; the task holds "
							+ first.getTaskTarget(helper.getLevel()));
			Vec3 toCow = cow.getEyePosition().subtract(scene.user.getEyePosition());
			// the default offset withYOffset(-0.75, 0.2, -0.75), turned by the yaw alone
			Vec3 expected = new Vec3(-0.75D, 0.2D, -0.75D).yRot(-MathUtil.yRotDegFromVec(toCow) * MathUtil.DEG_TO_RAD);
			assertAt(helper, scene, expected, "1.16 put a hierophant_green whose String Attack was pressed at a cow 3 blocks"
					+ " east of its user who looks south, the default offset turned by the yaw to the cow");

			// pressed at nothing, the next one takes the cow over (StandEntity.setTask)
			standAims(scene.stand, null);
			EntityActionInstance next = scene.start("string_attack", ActionPhase.PERFORM);
			helper.assertTrue(next != first && next.getTaskTarget(helper.getLevel()).getEntity() == cow,
					"1.16 StandEntity.setTask gave a String Attack pressed at nothing the cow of the one it replaced; it holds "
							+ next.getTaskTarget(helper.getLevel()));
			assertAt(helper, scene, expected, "1.16 put a hierophant_green whose String Attack took the cow over from the"
					+ " one before, its user looking south");
		});
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aPunchKeepsItsTargetButNeverTurnsItsOffsetToItAsIn116(GameTestHelper helper) {
		withStand(helper, "star_platinum", scene -> {
			Cow cow = scene.cow(new Vec3(1.5D, 0.0D, 0.0D));
			userAims(scene.user, cow);
			EntityActionInstance punch = scene.start("punch", ActionPhase.WINDUP);
			turn(scene.user, VIEW_YAW, BODY_YAW);
			Vec3 relative = scene.stand.offsetFromUser.getRelativeOffset();
			helper.assertTrue(punch.getTaskTarget(helper.getLevel()).getEntity() == cow && relative.z > 0.0D,
					"setup: the punch keeps " + punch.getTaskTarget(helper.getLevel()) + " with the offset " + relative
							+ ", not the cow it was pressed at");
			// front3dOffset's withXRot(0, front), along the view
			assertAt(helper, scene, new Vec3(0.0D, 0.0D, relative.z), "1.16 StandEntityLightAttack.lockOnTargetPosition kept"
					+ " a star_platinum punching a cow east of its user along the user's view south");

			// pressed at nothing, the next punch takes the cow over (StandEntity.setTask) and the Stand turns to it
			LivingComponentAction.getComponent(scene.user).entityAim.setTarget(ActionTarget.EMPTY);
			EntityActionInstance next = scene.start("punch", ActionPhase.WINDUP);
			helper.assertTrue(next != punch && next.getTaskTarget(helper.getLevel()).getEntity() == cow
					&& next.standRotationTarget != null && next.standRotationTarget.getEntity() == cow,
					"1.16 StandEntity.setTask gave a punch pressed at nothing the cow of the punch it replaced; it keeps "
							+ next.getTaskTarget(helper.getLevel()) + " and turns the Stand to " + next.standRotationTarget);
		});
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void repairItemAndRevertWithoutATargetTurnByTheBodyYawAsIn116(GameTestHelper helper) {
		for (String skill : new String[] { "repair_item", "revert_state" }) {
			withStand(helper, "crazy_diamond", scene -> {
				startOnTheOffHandItem(scene, skill);
				turn(scene.user, VIEW_YAW, BODY_YAW);
				// standOffsetFromUser(0.667, 0.2, 0) with canInvertSide off, for a right-handed user
				assertAt(helper, scene, new Vec3(0.667D, 0.0D, 0.2D).yRot(-BODY_YAW * MathUtil.DEG_TO_RAD),
						"1.16 put a crazy_diamond using " + skill + " on the off-hand item, its user looking south with its"
								+ " body turned 50 degrees (yRotForOffset: the body yaw)");
			});
			withStand(helper, "crazy_diamond", scene -> {
				scene.stand.setArmsOnlyMode();
				startOnTheOffHandItem(scene, skill);
				turn(scene.user, VIEW_YAW, BODY_YAW);
				// no offset of the action in arms-only mode: the arms-only default withYOffset(0, 0, 0.15)
				assertAt(helper, scene, new Vec3(0.0D, 0.0D, 0.15D).yRot(-BODY_YAW * MathUtil.DEG_TO_RAD),
						"1.16 put the arms of a crazy_diamond using " + skill + " on the off-hand item, its user looking south"
								+ " with its body turned 50 degrees (the default offset, by yRotForOffset: the body yaw)");
			});
		}
		helper.succeed();
	}

	/*
	 * 1.16 gave these shots no yRotForOffset of their own, so their offsets turned by the view yaw: Rapier Launch
	 * standOffsetFromUser(0, 0.25), Blood Cutter (-0.1, -0.5), Block Bullet (0.25, -0.5, 0), Flame Burst and Red Bind
	 * standOffsetFront(); a noYOffset offset at the default 0.2 up
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void shotsTurnTheirOffsetByTheViewYawAsIn116(GameTestHelper helper) {
		Object[][] shots = {
				{ "silver_chariot", "rapier_launch", new Vec3(0.0D, 0.2D, 0.25D) },
				{ "crazy_diamond", "blood_cutter", new Vec3(-0.1D, 0.2D, -0.5D) },
				{ "crazy_diamond", "block_bullet", new Vec3(0.25D, 0.0D, -0.5D) },
				{ "magicians_red", "flame_burst", new Vec3(0.0D, 0.2D, 0.5D) },
				{ "magicians_red", "red_bind", new Vec3(0.0D, 0.2D, 0.5D) } };
		float viewYaw = 90.0F;
		for (Object[] shot : shots) {
			withStand(helper, (String) shot[0], scene -> {
				scene.set((String) shot[1]);
				turn(scene.user, viewYaw, viewYaw - BODY_YAW);
				assertAt(helper, scene, ((Vec3) shot[2]).yRot(-viewYaw * MathUtil.DEG_TO_RAD), "1.16 put a " + shot[0]
						+ " starting its " + shot[1] + ", its user looking west with its body turned 50 degrees away (the view yaw)");
			});
		}
		helper.succeed();
	}

	// Revert sets the item offset on its first perform tick; Repair Item as it starts, and stops on an empty hand
	private static void startOnTheOffHandItem(Scene scene, String skill) {
		scene.start(skill, ActionPhase.PERFORM);
		if (skill.equals("revert_state")) {
			LivingComponentAction.getComponent(scene.stand).tick();
		}
	}

	/*
	 * A client holds the action the server sent and turns the offset by the task target in it, whatever its own aim
	 * captures as the action starts (a client's String Attack takes the Stand's aim in onActionSet).
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aClientPlacesTheStandByTheTaskTargetTheServerSent(GameTestHelper helper) {
		withStand(helper, "hierophant_green", scene -> {
			Cow pressedAt = scene.cow(COW_FROM_USER);
			Cow clientAim = scene.cow(new Vec3(-3.0D, 0.0D, 1.0D));
			standAims(scene.stand, pressedAt);
			EntityActionInstance server = scene.start("string_attack", ActionPhase.PERFORM);
			turn(scene.user, VIEW_YAW, BODY_YAW);
			scene.stand.updatePosition(scene.user);
			Vec3 serverOffset = scene.stand.position().subtract(scene.user.position());
			helper.assertTrue(server.getTaskTarget(helper.getLevel()).getEntity() == pressedAt,
					"setup: the server's String Attack holds " + server.getTaskTarget(helper.getLevel()));

			EntityActionInstance received = received(helper, server);
			ClientStringAttack client = new ClientStringAttack(server.ability);
			copyAsSent(received, client);
			standAims(scene.stand, clientAim);
			LivingComponentAction actions = LivingComponentAction.getComponent(scene.stand);
			actions.setAction(null, scene.user, SyncType.NO_SYNC);
			actions.setAction(client, scene.user, SyncType.NO_SYNC);
			helper.assertTrue(scene.stand.getCurStandAction() == client,
					"setup: the client's String Attack is not the Stand's action");
			scene.stand.updatePosition(scene.user);
			Vec3 clientOffset = scene.stand.position().subtract(scene.user.position());
			helper.assertTrue(clientOffset.distanceTo(serverOffset) < EPS,
					"A client given the server's String Attack, held at the cow east of the user, while its own aim is on"
							+ " another cow west of it, put the hierophant_green at its user's feet + " + clientOffset
							+ "; the server put it at + " + serverOffset + " (the client's task holds "
							+ client.getTaskTarget(helper.getLevel()) + ")");
		});
		helper.succeed();
	}

	// a client's copy of the action (EntityActionInstance.takesTargetsFromServer)
	private static final class ClientStringAttack extends StringAttackShot {
		ClientStringAttack(EntityActionType ability) {
			super(ability, false);
		}

		@Override
		protected boolean takesTargetsFromServer() {
			return true;
		}
	}

	// the action as a client decodes it from the action packet
	private static EntityActionInstance received(GameTestHelper helper, EntityActionInstance sent) {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		try {
			EntityActionInstance.encode(buf, sent);
			EntityActionInstance read = EntityActionInstance.decode(helper.getLevel(), buf);
			helper.assertTrue(read != null && buf.readableBytes() == 0,
					"setup: the String Attack did not read back from its packet: " + read + ", " + buf.readableBytes() + " left");
			return read;
		}
		finally {
			buf.release();
		}
	}

	private static void copyAsSent(EntityActionInstance received, EntityActionInstance client) {
		try {
			for (String name : new String[] { "taskTarget", "phase" }) {
				Field field = EntityActionInstance.class.getDeclaredField(name);
				field.setAccessible(true);
				field.set(client, field.get(received));
			}
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("setup: cannot copy the received action", e);
		}
		client.id = received.id;
		client.phasesLength.putAll(received.phasesLength);
		client.standRotationTarget = received.standRotationTarget;
	}

	private interface StandCheck {
		void check(Scene scene);
	}

	private record Scene(GameTestHelper helper, Player user, StandPower power, StandEntity stand, List<Entity> spawned) {
		Cow cow(Vec3 fromUser) {
			return mob(EntityType.COW, fromUser);
		}

		<T extends Mob> T mob(EntityType<T> type, Vec3 fromUser) {
			T mob = type.create(helper.getLevel());
			helper.assertTrue(mob != null, "Could not make a " + type);
			Vec3 pos = user.position().add(fromUser);
			mob.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
			mob.setNoAi(true);
			mob.setNoGravity(true);
			helper.assertTrue(helper.getLevel().addFreshEntity(mob), "Could not add a " + type);
			spawned.add(mob);
			return mob;
		}

		EntityActionInstance start(String name, ActionPhase until) {
			Ability found = power.getAbility(name);
			helper.assertTrue(found instanceof EntityActionAbility, "Missing registered " + name);
			stand.summonLockTicks = 0;
			EntityActionInstance action = ((EntityActionAbility) found).initActionOnAbilityUse(helper.getLevel(), user, stand, null);
			LivingComponentAction actions = LivingComponentAction.getComponent(stand);
			actions.setAction(action, user, SyncType.NO_SYNC);
			for (int tick = 0; tick < 40 && action.getPhase() != until && !action.isOver(); tick++) {
				actions.tick();
			}
			helper.assertTrue(stand.getCurStandAction() == action && action.getPhase() == until,
					"setup: the " + name + " did not reach " + until + ": phase " + action.getPhase());
			return action;
		}

		// the action set as it starts, before it ticks (a shot would fire)
		EntityActionInstance set(String name) {
			Ability found = power.getAbility(name);
			if (!(found instanceof EntityActionAbility)) {
				power.getCurTypeData()._setSkillUnlocked(name, true, false);
				found = power.getAbility(name);
			}
			helper.assertTrue(found instanceof EntityActionAbility, "Missing registered " + name);
			stand.summonLockTicks = 0;
			EntityActionInstance action = ((EntityActionAbility) found).initActionOnAbilityUse(helper.getLevel(), user, stand, null);
			LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
			helper.assertTrue(stand.getCurStandAction() == action && stand.isFollowingUser(),
					"setup: the " + name + " is not the following Stand's action");
			return action;
		}
	}

	private static void withStand(GameTestHelper helper, String id, StandCheck check) {
		withStand(helper, id, GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL), check);
	}

	private static void withStand(GameTestHelper helper, String id, Player user, StandCheck check) {
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(id));
		StandPower power = null;
		List<Entity> spawned = new ArrayList<>();
		try {
			helper.assertTrue(type instanceof EntityStandType, "Missing the core entity Stand " + id);
			PlayerClientBroadcastedSettings settings = user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get());
			settings.standSide = HumanoidArm.RIGHT;
			user.setMainArm(HumanoidArm.RIGHT);
			Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 12.0D, 2.5D));
			user.setNoGravity(true);
			user.moveTo(pos.x, pos.y, pos.z, VIEW_YAW, 0.0F);
			turn(user, VIEW_YAW, VIEW_YAW);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the user of " + id);
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant " + id);
			helper.assertTrue(type.summon(user, power), "Could not summon " + id);
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null && stand.isFollowingUser(), "setup: the summoned " + id + " does not follow its user");
			check.check(new Scene(helper, user, power, stand, spawned));
		}
		finally {
			if (power != null && power.isSummoned() && type != null) {
				type.forceUnsummon(user, power);
			}
			spawned.forEach(Entity::discard);
			user.discard();
		}
	}

	// what the user's client sent as its crosshair target
	private static void userAims(Player user, Cow cow) {
		LivingComponentAction.getComponent(user).entityAim.setTarget(new ActionTarget(cow));
	}

	// what the Stand's aim met, as its user's client sent it
	private static void standAims(StandEntity stand, Cow cow) {
		LivingComponentAction.getComponent(stand).entityAim.setTarget(cow != null ? new ActionTarget(cow) : ActionTarget.EMPTY);
	}

	private static void turn(Player user, float viewYaw, float bodyYaw) {
		user.setYRot(viewYaw);
		user.yRotO = viewYaw;
		user.setYHeadRot(viewYaw);
		user.yHeadRotO = viewYaw;
		user.setXRot(0.0F);
		user.xRotO = 0.0F;
		user.yBodyRot = bodyYaw;
		user.yBodyRotO = bodyYaw;
	}

	// StandEntity.tick places a following Stand through updatePosition
	private static void assertAt(GameTestHelper helper, Scene scene, Vec3 expected, String claim) {
		scene.stand.updatePosition(scene.user);
		Vec3 actual = scene.stand.position().subtract(scene.user.position());
		helper.assertTrue(actual.distanceTo(expected) < EPS, claim + ": at its user's feet + " + expected + ", got " + actual
				+ " (offset " + scene.stand.offsetFromUser.getRelativeOffset() + " " + scene.stand.offsetFromUser.getRotations()
				+ ")");
	}
}
