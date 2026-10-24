package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.hierophant.HierophantStringAttackAbility;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTargetAim;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 PowerBaseImpl.onClickAction ran StandEntityAction.onClick first, which summoned a Stand that was away, and only
 * then checked the press's mouse target: with the Stand away the client's vanilla pick (EntityStandType.clientHitResult
 * changes it only while the power is active). The task kept that target, so the summoned Stand turned to it whatever its
 * user did afterwards and struck it. The scenes press through the condition check and AbilityInput.keyPress, as
 * ClAbilityInputPacket does after it stored the press's aim, which with the Stand away is only the user's. Own batches.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandAwayPressAim116GameTests {
	private static final String BATCH = "stand_away_press_aim_116_";
	private static final short KEY = 121;

	private StandAwayPressAim116GameTests() {}

	/*
	 * Hierophant Green away, a cow 3 blocks ahead on its user's aim: the string attack summons it, its task keeps the cow
	 * (1.16 HierophantGreenStringAttack.standKeepsTarget), and with its user turned round and aiming at nothing from the
	 * next tick on, the Stand still faces the cow as it shoots its strings, which strike it.
	 */
	@GameTest(template = "empty", timeoutTicks = 140, batch = BATCH + "strike")
	public static void aStandSummonedByAPressTakesItsUsersAimAndStrikesItAsIn116(GameTestHelper helper) {
		Scene scene = new Scene(helper, new Vec3(1.5D, 3.0D, 1.5D));
		Entity cow = scene.cow(scene.user.getEyePosition().add(0.0D, 0.0D, 3.0D));
		float health = ((Mob) cow).getHealth();
		scene.aim(new ActionTarget(cow), false);
		EntityActionInstance action = scene.pressStringAttack();
		StandEntity stand = scene.stand;
		ActionTarget kept = action.getTaskTarget(scene.level);
		// the user turns round at once; its client's next aim packet carries what it looks at now, nothing
		scene.user.setYRot(180.0F);
		scene.user.setYHeadRot(180.0F);
		scene.user.yBodyRot = 180.0F;
		scene.aim(ActionTarget.EMPTY, false);
		LivingComponentAction.getComponent(stand).entityAim.setTarget(ActionTarget.EMPTY);
		float[] performYaw = { Float.NaN, Float.NaN };
		int pressTick = (int) helper.getTick();
		Runnable[] poll = new Runnable[1];
		poll[0] = () -> {
			try {
				if (Float.isNaN(performYaw[0]) && stand.getCurStandAction() == action && action.getPhase() == ActionPhase.PERFORM) {
					performYaw[0] = stand.getYRot();
					performYaw[1] = yawTo(stand.getEyePosition(), cow.getEyePosition());
				}
				boolean struck = ((Mob) cow).getHealth() < health;
				if (!struck && helper.getTick() < pressTick + 100) {
					// a new Runnable each time: GameTestInfo keys its timed tasks by the Runnable
					helper.runAfterDelay(1, () -> poll[0].run());
					return;
				}
				helper.assertTrue(kept.getEntity() == cow, "1.16's string attack pressed at a cow with Hierophant Green away"
						+ " kept the cow as its task's target (its mouse target, checked after the press summoned the Stand); the"
						+ " port's kept " + describe(kept));
				helper.assertTrue(!Float.isNaN(performYaw[0]) && Math.abs(Mth.wrapDegrees(performYaw[0] - performYaw[1])) < 5.0F,
						"1.16's Hierophant Green summoned by the press faced the cow it kept as it shot, its user turned away; the"
								+ " port's faced yaw " + performYaw[0] + ", the cow at yaw " + performYaw[1]);
				helper.assertTrue(struck, "1.16's strings shot at the kept cow struck it; the port's cow has "
						+ ((Mob) cow).getHealth() + " of " + health + " " + (helper.getTick() - pressTick) + " ticks after the press");
				scene.close();
				helper.succeed();
			}
			catch (RuntimeException | Error error) {
				scene.close();
				throw error;
			}
		};
		helper.runAfterDelay(1, () -> poll[0].run());
	}

	/*
	 * The user's aim was an entity the server cannot find (ActionTargetAim.isClientEntityMissing: 1.16
	 * PowerBaseImpl.checkTarget emptied the whole press): the Stand the press summons takes that too, so the press keeps
	 * nothing and a check that asks the Stand's aim sees the missing entity.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "missing")
	public static void aStandSummonedByAPressAtAnEntityTheServerLacksTakesNoTargetAsIn116(GameTestHelper helper) {
		Scene scene = new Scene(helper, new Vec3(1.5D, 3.0D, 1.5D));
		try {
			scene.aim(ActionTarget.EMPTY, true);
			EntityActionInstance action = scene.pressStringAttack();
			ActionTargetAim standAim = LivingComponentAction.getAim(scene.stand);
			helper.assertTrue(standAim != null && standAim.isClientEntityMissing()
					&& standAim.getTarget().getType() == ActionTarget.TargetType.EMPTY, "1.16 emptied a press at an entity the"
							+ " server could not find; the Stand the port's press summoned holds the aim " + (standAim == null ? "null"
									: describe(standAim.getTarget()) + " missing " + standAim.isClientEntityMissing()));
			helper.assertTrue(action.getTaskTarget(scene.level).isEmpty(scene.level), "The press at an entity the server"
					+ " lacks kept " + describe(action.getTaskTarget(scene.level)));
		}
		finally {
			scene.close();
		}
		helper.succeed();
	}

	private static float yawTo(Vec3 from, Vec3 to) {
		Vec3 d = to.subtract(from);
		return (float) (Mth.atan2(d.z, d.x) * (180.0D / Math.PI)) - 90.0F;
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

	private static final class Scene {
		final GameTestHelper helper;
		final ServerLevel level;
		final Player user;
		final StandPower power;
		final StandType type;
		final List<Entity> entities = new ArrayList<>();
		StandEntity stand;
		boolean closed;

		// a Survival Hierophant Green user floating at the cell's point, looking south, its Stand away
		Scene(GameTestHelper helper, Vec3 cellPoint) {
			this.helper = helper;
			level = helper.getLevel();
			Vec3 origin = helper.absoluteVec(cellPoint);
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			user.setNoGravity(true);
			user.moveTo(origin.x, origin.y, origin.z, 0, 0);
			user.setYHeadRot(0);
			user.yBodyRot = 0;
			helper.assertTrue(level.addFreshEntity(user), "Could not add the Stand user");
			entities.add(user);
			power = PowerClass.STAND.attachGet(user);
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("hierophant_green"));
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Hierophant Green");
			power.setStamina(power.getMaxStamina());
			helper.assertTrue(power.getSummonedStandEntity() == null, "Fixture: Hierophant Green is out before the press");
		}

		// the user's aim as ClAbilityInputPacket stores the press's: with the Stand away only the user's
		void aim(ActionTarget target, boolean clientEntityMissing) {
			LivingComponentAction.getComponent(user).entityAim.setTarget(target, clientEntityMissing);
		}

		EntityActionInstance pressStringAttack() {
			Ability ability = power.getAbility("string_attack");
			helper.assertTrue(ability instanceof HierophantStringAttackAbility, "Fixture: no string attack");
			AbilityConditionCheck selected = power.updateAvailableMoves().getContextVariationContainer(ability);
			helper.assertTrue(AbilityInput.withConditionCheck(selected, user, InputMethod.CLICK),
					"Fixture: the string attack was refused with the Stand away");
			BufferingState pressed = BufferingState.clickOnly();
			AbilityInput.keyPress(KEY, selected.ability, user, null, InputMethod.CLICK, 0, pressed, ability.getAbilityId());
			AbilityInput.keyRelease(KEY, user);
			stand = power.getSummonedStandEntity();
			helper.assertTrue(pressed.isActionSuccess && stand != null && stand.isAddedToLevel(),
					"Fixture: the press did not summon Hierophant Green and start the string attack");
			entities.add(stand);
			stand.setNoGravity(true);
			EntityActionInstance action = stand.getCurStandAction();
			helper.assertTrue(action instanceof HierophantStringAttackAbility.StringAttackShot,
					"Fixture: the summoned Stand's action is " + action);
			return action;
		}

		Entity cow(Vec3 at) {
			Mob cow = EntityType.COW.create(level);
			helper.assertTrue(cow != null, "Could not create a cow");
			cow.setNoAi(true);
			cow.setNoGravity(true);
			cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
			cow.setHealth(100.0F);
			cow.moveTo(at.x, at.y - cow.getBbHeight() / 2.0D, at.z, 0, 0);
			helper.assertTrue(level.addFreshEntity(cow), "Could not add the cow");
			entities.add(cow);
			return cow;
		}

		void close() {
			if (closed) {
				return;
			}
			closed = true;
			AbilityInput.keyRelease(KEY, user);
			if (stand != null) {
				LivingComponentAction.getComponent(stand).setAction(null, user, SyncType.NO_SYNC);
			}
			if (power.isSummoned()) {
				type.forceUnsummon(user, power);
			}
			entities.forEach(Entity::discard);
		}
	}
}
