package rotp.core.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;

import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.theworld.TheWorldTSPunchAbility;
import rotp.core.impl.stands.theworld.TheWorldTSPunchAbility.TheWorldTSPunch;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.timestop.TimeStopLearning;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 TheWorldTSHeavyAttack was a Stand task (a StandEntityAction) whose preTaskInit took the summon lock off its Stand
 * (summonLockTicks = 0) where the press ran: StandEntityAction.startedHolding and perform ran it on the server and on the
 * client of the user who pressed (PowerBaseImpl.clickAction, which the user's client called too), not on another player's
 * client. The port's time-stop punch takes it off as it is set on each of those two sides.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TheWorldTSPunchSummonLockGameTests {
	private static final short KEY = 158;
	private static final int LOCK = 5;
	private static final PowerSkillUnlocks.Owner UNLOCKS = PowerSkillUnlocks.register(
			JojoMod.resLoc("the_world_ts_punch_summon_lock_gametest"));

	private TheWorldTSPunchSummonLockGameTests() {}

	/*
	 * The World (speed 16, no lock of its own) with its summon lock made 5, as only a Stand slower than speed 16 has one,
	 * presses its time-stop punch: the server's Stand loses its lock, and so does the user's client's Stand as the
	 * user's client's copy of the punch (what the server sends it) is set there, while another player's client keeps it.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theTimeStopPunchTakesItsStandsSummonLockOffOnTheServerAndTheUsersClientAsIn116(GameTestHelper helper) {
		Vec3 start = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 6, 2)));
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		StandPower power = null;
		StandType type = null;
		try {
			user.setNoGravity(true);
			user.moveTo(start.x, start.y, start.z, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Fixture: the user was not added");
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("the_world"));
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Fixture: The World was not granted");
			power.setResolveLevel(3);
			PowerSkillUnlocks.Result unlocked = UNLOCKS.forceUnlock(power, "ts_punch");
			helper.assertTrue(unlocked == PowerSkillUnlocks.Result.UNLOCKED || unlocked == PowerSkillUnlocks.Result.ALREADY_UNLOCKED,
					"Fixture: the time-stop punch is not unlocked");
			power.getCurTypeData().setAbilityLearningProgressPoints(TimeStopLearning.TIME_STOP, 95,
					TimeStopLearning.getMaxTrainingPoints(power), power);
			power.setStamina(power.getMaxStamina());
			helper.assertTrue(type.summon(user, power), "Fixture: The World was not summoned");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null && stand.summonLockTicks == 0, "Fixture: The World has a summon lock of its own: "
					+ (stand != null ? stand.summonLockTicks : null));
			stand.gradualSummonWeaknessTicks = 0;
			stand.summonLockTicks = LOCK;

			Ability tsPunch = power.getAbility("ts_punch");
			helper.assertTrue(tsPunch instanceof TheWorldTSPunchAbility, "Fixture: The World has no time-stop punch");
			var selected = power.updateAvailableMoves().getContextVariationContainer(tsPunch);
			helper.assertTrue(selected != null && AbilityInput.withConditionCheck(selected, user, InputMethod.CLICK),
					"Fixture: the time-stop punch was refused");
			BufferingState pressed = BufferingState.clickOnly();
			AbilityInput.keyPress(KEY, selected.ability, user, null, InputMethod.CLICK, 0, pressed, tsPunch.getAbilityId());
			EntityActionInstance server = stand.getCurStandAction();
			helper.assertTrue(pressed.isActionSuccess && server instanceof TheWorldTSPunch,
					"Fixture: the time-stop punch did not start: " + server);
			int serverLock = stand.summonLockTicks;

			EntityActionInstance received = received(helper, server);
			helper.assertTrue(received instanceof TheWorldTSPunch, "Fixture: the punch decodes as " + received);
			Player usersClientPlayer = localPlayer(helper);
			stand.summonLockTicks = LOCK;
			new ClientTSPunch(received.ability, stand, usersClientPlayer)._onActionStarted(null);
			int usersClientLock = stand.summonLockTicks;
			stand.summonLockTicks = LOCK;
			new ClientTSPunch(received.ability, stand, user)._onActionStarted(null);
			int otherClientLock = stand.summonLockTicks;
			stand.summonLockTicks = 0;

			helper.assertTrue(serverLock == 0 && usersClientLock == 0 && otherClientLock == LOCK, "1.16's time-stop punch took"
					+ " its Stand's summon lock off on the server and on the user's client, where its press ran, not on"
					+ " another player's client: the lock " + LOCK + " became " + serverLock + " on the server, "
					+ usersClientLock + " on the user's client and " + otherClientLock + " on another player's client");
		}
		finally {
			AbilityInput.keyRelease(KEY, user);
			if (power != null && type != null && power.isSummoned()) {
				type.forceUnsummon(user, power);
			}
			user.discard();
		}
		helper.succeed();
	}

	// the action as a client decodes it from the action packet
	private static EntityActionInstance received(GameTestHelper helper, EntityActionInstance sent) {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		try {
			EntityActionInstance.encode(buf, sent);
			return EntityActionInstance.decode(helper.getLevel(), buf);
		}
		finally {
			buf.release();
		}
	}

	// the client's own player, as the user's client knows its user (LocalPlayer)
	private static Player localPlayer(GameTestHelper helper) {
		return new Player(helper.getLevel(), BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "test-local-player")) {
			@Override
			public boolean isSpectator() {
				return false;
			}

			@Override
			public boolean isCreative() {
				return false;
			}

			@Override
			public boolean isLocalPlayer() {
				return true;
			}
		};
	}

	// a client's copy of the punch (EntityActionInstance.takesTargetsFromServer) on a Stand whose user that client knows
	private static final class ClientTSPunch extends TheWorldTSPunch {
		ClientTSPunch(EntityActionType ability, StandEntity stand, LivingEntity user) {
			super(ability);
			performer = stand;
			powerUser.setEntity(user);
		}

		@Override
		protected boolean takesTargetsFromServer() {
			return true;
		}
	}
}
