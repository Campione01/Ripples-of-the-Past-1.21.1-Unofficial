package rotp.core.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.entity.HamonBubbleBarrierEntity;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.LivingComponentAction.TransactionSnapshot;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 had no input transaction: an input either started its action or did nothing. When a failed input puts back the
 * action it had replaced or cleared (LivingComponentAction.rollbackFailedInputAction), that action's onActionSet and
 * onSetPhase run again; what they took or did once (a punching hand, a blink and its time skip, the dash's stamina, the
 * bearing shot's phase, the growing bubble barrier) stays as it was, as if the failed input never happened.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class InputRestoreGameTests {
	private InputRestoreGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20, batch = "input_restore_1")
	public static void heavyPunchPutBackKeepsItsPunchingHand(GameTestHelper helper) {
		handKept(helper, "heavy_punch");
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = "input_restore_2")
	public static void punchPutBackKeepsItsPunchingHand(GameTestHelper helper) {
		handKept(helper, "punch");
	}

	private static void handKept(GameTestHelper helper, String abilityName) {
		try (StandScene scene = new StandScene(helper, "star_platinum")) {
			EntityActionInstance action = scene.set(abilityName);
			// a later tick: a new swing would alternate the hand
			scene.stand.tickCount += 5;
			InteractionHand hand = scene.stand.getCurrentPunchingHand();
			scene.clearAndRollBack(action);
			helper.assertTrue(scene.stand.getCurrentPunchingHand() == hand, "The " + abilityName
					+ " a failed input put back swapped the punching hand: " + hand + " -> "
					+ scene.stand.getCurrentPunchingHand());
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = "input_restore_3")
	public static void timeStopPunchPutBackDoesNotBlinkAgain(GameTestHelper helper) {
		try (StandScene scene = new StandScene(helper, "the_world")) {
			EntityActionInstance action = scene.set("ts_punch");
			Vec3 afterBlink = scene.stand.position();
			float stamina = scene.power.getStamina();
			helper.assertTrue(stamina < scene.power.getMaxStamina(), "premise: the time-stop punch's time skip cost "
					+ "nothing, stamina " + stamina);
			scene.clearAndRollBack(action);
			helper.assertTrue(scene.stand.position().distanceTo(afterBlink) < 1.0E-6,
					"The time-stop punch a failed input put back blinked again: " + afterBlink + " -> "
							+ scene.stand.position());
			helper.assertTrue(scene.power.getStamina() == stamina, "The time-stop punch a failed input put back paid "
					+ "for its time skip again: stamina " + stamina + " -> " + scene.power.getStamina());
			helper.assertTrue(scene.stand.isNoGravity(), "The time-stop punch put back lost its gravity override");
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = "input_restore_4")
	public static void chariotDashPutBackMidDashDoesNotPayAgain(GameTestHelper helper) {
		try (StandScene scene = new StandScene(helper, "silver_chariot")) {
			EntityActionInstance dash = scene.set("dash_attack");
			for (int tick = 0; tick < 5 && dash.getPhase() != ActionPhase.PERFORM; tick++) {
				scene.component.tick();
			}
			helper.assertTrue(dash.getPhase() == ActionPhase.PERFORM, "premise: the dash did not start, phase "
					+ dash.getPhase());
			float stamina = scene.power.getStamina();
			helper.assertTrue(stamina < scene.power.getMaxStamina() && stamina >= 50,
					"premise: the dash paid its stamina once, " + stamina + " of " + scene.power.getMaxStamina());
			scene.clearAndRollBack(dash);
			helper.assertTrue(dash.getPhase() == ActionPhase.PERFORM, "The dash was not put back mid-dash");
			helper.assertTrue(scene.power.getStamina() == stamina, "The dash a failed input put back paid its stamina "
					+ "again: " + stamina + " -> " + scene.power.getStamina());
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = "input_restore_5")
	public static void bearingShotPutBackKeepsItsPhase(GameTestHelper helper) {
		try (StandScene scene = new StandScene(helper, "star_platinum")) {
			EntityActionInstance shot = scene.set("bearing_shot");
			helper.assertTrue(shot.phasesLength.getFloat(ActionPhase.WINDUP) >= 2, "premise: the shot's windup is "
					+ shot.phasesLength.getFloat(ActionPhase.WINDUP));
			ActionPhase phase = shot.getPhase();
			float tick = shot.getPhaseTick();
			// an input that started a second shot over it and then failed
			TransactionSnapshot before = scene.component.captureTransactionSnapshot();
			scene.component.setAction(scene.init("bearing_shot"), scene.user, SyncType.NO_SYNC);
			scene.component.rollbackFailedInputAction(before);
			helper.assertTrue(scene.component.getAction() == shot, "The shot was not put back");
			helper.assertTrue(shot.getPhase() == phase && shot.getPhaseTick() == tick, "The shot a failed input put back "
					+ "skipped half its windup: " + phase + " " + tick + " -> " + shot.getPhase() + " " + shot.getPhaseTick());
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = "input_restore_6")
	public static void bubbleBarrierPutBackKeepsTheBarrierItGrows(GameTestHelper helper) {
		Player user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "RestoreBarrier"));
		LivingComponentAction component = LivingComponentAction.getComponent(user);
		try {
			Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.setPos(origin.x, origin.y, origin.z);
			user.setNoGravity(true);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Hamon user");
			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.HAMON.get());
			HamonData hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
			hamon.learnSkill(ModHamonSkills.BUBBLE_BARRIER.get());
			hamon.setEnergy(hamon.getMaxEnergy());
			Ability ability = power.getAbility("bubble_barrier");
			helper.assertTrue(ability instanceof EntityActionType, "Missing registered bubble_barrier");
			EntityActionInstance hold = ((EntityActionType) ability).initActionOnAbilityUse(helper.getLevel(), user, user, null);
			component.setAction(hold, user, SyncType.NO_SYNC);
			helper.assertTrue(barriers(helper, user) == 1, "premise: the hold did not start one barrier");

			TransactionSnapshot before = component.captureTransactionSnapshot();
			component.setAction(null, user, SyncType.NO_SYNC);
			component.rollbackFailedInputAction(before);
			helper.assertTrue(component.getAction() == hold, "The bubble barrier hold was not put back");
			helper.assertTrue(barriers(helper, user) == 1, "The hold a failed input put back started another barrier: "
					+ barriers(helper, user) + " barriers");
		}
		finally {
			component.setAction(null, SyncType.NO_SYNC);
			helper.getLevel().getEntitiesOfClass(HamonBubbleBarrierEntity.class, user.getBoundingBox().inflate(8))
					.forEach(HamonBubbleBarrierEntity::discard);
			user.discard();
		}
		helper.succeed();
	}

	private static int barriers(GameTestHelper helper, Player user) {
		return helper.getLevel().getEntitiesOfClass(HamonBubbleBarrierEntity.class, user.getBoundingBox().inflate(8)).size();
	}

	private static final class StandScene implements AutoCloseable {
		final GameTestHelper helper;
		final StandType type;
		final Player user;
		final StandPower power;
		final StandEntity stand;
		final LivingComponentAction component;

		StandScene(GameTestHelper helper, String standName) {
			this.helper = helper;
			Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standName));
			helper.assertTrue(type != null, "Missing registered Stand " + standName);
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			user.setNoGravity(true);
			user.moveTo(origin.x, origin.y, origin.z, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Stand user");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant " + standName);
			power.setResolveLevel(power.getMaxResolveLevel());
			helper.assertTrue(type.summon(user, power), "Could not summon " + standName);
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "The summoned Stand is missing");
			stand.summonLockTicks = 0;
			stand.setNoGravity(true);
			power.setStamina(power.getMaxStamina());
			component = LivingComponentAction.getComponent(stand);
			component.setAction(null, SyncType.NO_SYNC);
		}

		EntityActionInstance init(String abilityName) {
			Ability ability = power.getMoveset().getAbility(abilityName);
			helper.assertTrue(ability instanceof EntityActionType, "Missing entity action " + abilityName);
			return ((EntityActionType) ability).initActionOnAbilityUse(helper.getLevel(), user, stand, null);
		}

		EntityActionInstance set(String abilityName) {
			EntityActionInstance action = init(abilityName);
			component.setAction(action, user, SyncType.NO_SYNC);
			helper.assertTrue(component.getAction() == action, abilityName + " was not set on the Stand");
			return action;
		}

		// an input that cleared the action and then failed
		void clearAndRollBack(EntityActionInstance action) {
			TransactionSnapshot before = component.captureTransactionSnapshot();
			component.setAction(null, user, SyncType.NO_SYNC);
			component.rollbackFailedInputAction(before);
			helper.assertTrue(component.getAction() == action, "The action was not put back: " + component.getAction());
		}

		@Override
		public void close() {
			component.setAction(null, SyncType.NO_SYNC);
			if (power.isSummoned()) {
				type.forceUnsummon(user, power);
			}
			user.discard();
		}
	}
}
