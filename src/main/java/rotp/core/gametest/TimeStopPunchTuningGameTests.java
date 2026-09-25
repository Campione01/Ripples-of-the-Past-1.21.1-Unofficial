package rotp.core.gametest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

import com.mojang.authlib.GameProfile;

import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.theworld.TheWorldTSPunchAbility;
import rotp.core.impl.stands.theworld.TimeStopAbility;
import rotp.core.impl.stands.theworld.TimeStopBlinkAbility;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.network.s2c.StandSkinSoundPacket;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.timestop.TimeStopCooldowns;
import rotp.core.subsystems.timestop.TimeStopLearning;
import rotp.core.subsystems.timestop.TimeStopState;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 TheWorldTSHeavyAttack was built with its blink: the time skip cost half the blink's start cost plus the blink's
 * per-tick cost, and an add-on TS punch set its own staminaCost (Diego's 200, which trained nothing). 1.16
 * TimeStopInstant#getEntityTargetTeleportPos was an add-on hook (Shadow The World), and TimeStop.Builder#heldWalkSpeed
 * a per-time-stop setting. The core's own numbers must not move.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TimeStopPunchTuningGameTests {
	private static final PowerSkillUnlocks.Owner TEST_UNLOCKS =
			PowerSkillUnlocks.register(JojoMod.resLoc("time_stop_punch_tuning_gametest"));
	private static final float EPS = 1.0E-3F;

	private TimeStopPunchTuningGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void theWorldTsPunchKeepsItsCoreNumbers(GameTestHelper helper) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		try {
			StandPower power = grantTrainedTimeStop(helper, player, "the_world");
			TheWorldTSPunchAbility punch = tsPunch(helper, power);
			helper.assertTrue(punch.getStaminaCost() == 50.0F && punch.trainsTimeStop(),
					"The World's TS punch must cost 50 and train its time stop (1.16 DEFAULT_STAMINA_COST)");
			// 225 * 0.8 * 0.5 and 9 * 100 / 100 * 0.8
			assertNear(helper, TimeStopLearning.getTsPunchTimeStopBaseStaminaCost(power), 90.0F,
					"The World's TS punch time skip start");
			assertNear(helper, TimeStopLearning.getTsPunchTimeStopStaminaCostTicking(power), 7.2F,
					"The World's TS punch time skip per tick");
			power.setStamina(500.0F);
			assertAffordable(helper, power, 90.0F, 7.2F, "The World");
			assertChargeWalks(helper, power, "The World");
		}
		finally {
			player.discard();
		}
		Player spUser = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		try {
			StandPower power = grantTrainedTimeStop(helper, spUser, "star_platinum");
			helper.assertTrue(power.getMoveset().getAbility("ts_punch") == null, "Star Platinum gained a TS punch");
			assertChargeWalks(helper, power, "Star Platinum");
		}
		finally {
			spUser.discard();
		}
		helper.succeed();
	}

	// 1.16 heldWalkSpeed(0) for both; the owner's Batch907 boundary keeps the charge free to walk
	private static void assertChargeWalks(GameTestHelper helper, StandPower power, String stand) {
		Ability timeStop = power.getMoveset().getAbility(TimeStopLearning.TIME_STOP);
		helper.assertTrue(timeStop instanceof TimeStopAbility ability && ability.getHeldWalkSpeed() == 1.0F,
				stand + " time stop must keep its charge free to walk");
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void anAddOnBlinkPricesItsTsPunch(GameTestHelper helper) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		try {
			StandPower power = grantTrainedTimeStop(helper, player, "the_world");
			TheWorldTSPunchAbility punch = tsPunch(helper, power);
			Ability blinkAbility = power.getMoveset().getAbility(TimeStopCooldowns.TIME_STOP_BLINK);
			helper.assertTrue(blinkAbility instanceof TimeStopBlinkAbility, "The World lost its time_stop_blink");
			// Reworked's 1.16 time stop: staminaCost(300), staminaCostTick(8.875)
			((TimeStopBlinkAbility) blinkAbility).setBaseTimeStopStaminaCosts(300.0F, 8.875F);
			assertNear(helper, TimeStopLearning.getTsPunchTimeStopBaseStaminaCost(power), 120.0F,
					"a TS punch time skip start from a 300 / 8.875 blink");
			assertNear(helper, TimeStopLearning.getTsPunchTimeStopStaminaCostTicking(power), 7.1F,
					"a TS punch time skip per tick from a 300 / 8.875 blink");
			power.setStamina(500.0F);
			assertAffordable(helper, power, 120.0F, 7.1F, "a 300 / 8.875 blink");

			// Diego's 1.16 THEWORLDTSHeavyAttack: staminaCost(200), no addLearningProgressPoints
			helper.assertTrue(punch.setStaminaCost(200.0F).setTrainsTimeStop(false) == punch
					&& punch.getStaminaCost() == 200.0F && !punch.trainsTimeStop(),
					"an add-on could not set its TS punch cost or turn its training off");
			boolean refused = false;
			try {
				punch.setStaminaCost(-1.0F);
			}
			catch (IllegalArgumentException expected) {
				refused = true;
			}
			helper.assertTrue(refused && punch.getStaminaCost() == 200.0F, "a negative TS punch cost was taken");
		}
		finally {
			player.discard();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void aBlinkCappedTsPunchSkipStartsFromTheBlinkReach(GameTestHelper helper) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		try {
			StandPower power = grantTrainedTimeStop(helper, player, "the_world");
			TheWorldTSPunchAbility punch = tsPunch(helper, power);
			Ability blinkAbility = power.getMoveset().getAbility(TimeStopCooldowns.TIME_STOP_BLINK);
			helper.assertTrue(blinkAbility instanceof TimeStopBlinkAbility, "The World lost its time_stop_blink");
			TimeStopBlinkAbility blink = (TimeStopBlinkAbility) blinkAbility;
			helper.assertTrue(PlayerClientBroadcastedSettings.getTimeStopStaminaCostMultiplier(power) == 1.0F,
					"the fixture player pays a time stop stamina multiplier");
			// Diego's donor blink: 0.8 of 225 / 7.5, so a 180 start and 6 per tick at 100 ticks
			blink.setBaseTimeStopStaminaCosts(225.0F, 7.5F);
			helper.assertTrue(!punch.skipCappedByBlinkImpliedTicks(), "the core TS punch must start from the trained ticks");
			// core: clamp(floor((stamina - 90) / 6), 0, 100)
			assertSkip(helper, power, punch, 300.0F, 35, "the core TS punch at 300 stamina");
			assertSkip(helper, power, punch, 150.0F, 10, "the core TS punch at 150 stamina");

			helper.assertTrue(punch.setSkipCappedByBlinkImpliedTicks(true) == punch && punch.skipCappedByBlinkImpliedTicks(),
					"an add-on could not cap its TS punch skip by its blink");
			// 1.16 Diego: reach = clamp(floor((stamina - 180) / 6), 5, 100), then clamp(floor((stamina - 90) / 6), 0, reach)
			power.setStamina(300.0F);
			helper.assertTrue(blink.getMaxImpliedTicks(power) == 20, "blink reach at 300 stamina: " + blink.getMaxImpliedTicks(power));
			power.setStamina(150.0F);
			helper.assertTrue(blink.getMaxImpliedTicks(power) == 5, "blink reach at 150 stamina: " + blink.getMaxImpliedTicks(power));
			assertSkip(helper, power, punch, 300.0F, 20, "a blink-capped TS punch at 300 stamina");
			assertSkip(helper, power, punch, 150.0F, 5, "a blink-capped TS punch at 150 stamina (blink minimum 5)");
			assertSkip(helper, power, punch, 100.0F, 1, "a blink-capped TS punch at 100 stamina");

			punch.setSkipCappedByBlinkImpliedTicks(false);
			assertSkip(helper, power, punch, 300.0F, 35, "a TS punch whose blink cap was cleared");
		}
		finally {
			player.discard();
		}
		helper.succeed();
	}

	// 1.16 Diego THEWORLDTSHeavyAttack: world.playSound(null, ..., THE_WORLD_TIME_STOP_BLINK) for everyone in range
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void aHeardByAllTsPunchSkipSoundsTheBlinkToEveryone(GameTestHelper helper) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		TheWorldTSPunchAbility punch = null;
		try {
			StandPower power = grantTrainedTimeStop(helper, player, "the_world");
			punch = tsPunch(helper, power);
			ServerPlayer blind = soundListener(helper, false);
			ServerPlayer seer = soundListener(helper, true);
			helper.assertTrue(!TimeStopState.canPlayerSeeInStoppedTime(blind) && TimeStopState.canPlayerSeeInStoppedTime(seer),
					"the listener fixtures must split by stopped-time sight");
			helper.assertTrue(!punch.timeSkipSoundHeardByAll(), "The World's TS punch must keep its unrevealed sound");
			assertSkipSound(helper, blind, punch, ModSoundEvents.THE_WORLD_TIME_STOP_UNREVEALED, "the core TS punch, blind listener");
			assertSkipSound(helper, seer, punch, ModSoundEvents.THE_WORLD_TIME_STOP_BLINK, "the core TS punch, seeing listener");

			helper.assertTrue(punch.setTimeSkipSoundHeardByAll(true) == punch && punch.timeSkipSoundHeardByAll(),
					"an add-on could not let every listener hear its TS punch blink");
			assertSkipSound(helper, blind, punch, ModSoundEvents.THE_WORLD_TIME_STOP_BLINK, "a heard-by-all TS punch, blind listener");
			assertSkipSound(helper, seer, punch, ModSoundEvents.THE_WORLD_TIME_STOP_BLINK, "a heard-by-all TS punch, seeing listener");
		}
		finally {
			// the moveset ability is The World's own
			if (punch != null) {
				punch.setTimeSkipSoundHeardByAll(false);
			}
			player.discard();
		}
		helper.succeed();
	}

	// The two flags above, through the ts_punch action itself: its onActionSet blink takes the skip and the sound from them
	@GameTest(template = "empty", timeoutTicks = 80)
	public static void aTsPunchActionSkipsAndSoundsByItsOwnFlags(GameTestHelper helper) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Vec3 userPos = helper.absoluteVec(new Vec3(2.5D, 2.0D, 2.5D));
		player.moveTo(userPos.x, userPos.y, userPos.z, 0.0F, 0.0F);
		ServerPlayer blind = soundListener(helper, false);
		// ServerPlayer.moveTo(x, y, z) resets its connection, which this listener lacks until SoundRecorder; setPos does not
		blind.setPos(userPos.x + 2.0D, userPos.y, userPos.z);
		SoundRecorder heard = new SoundRecorder(helper.getLevel(), blind);
		List<ServerPlayer> listeners = mutablePlayerList(helper);
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("the_world"));
		StandPower power = null;
		TheWorldTSPunchAbility punch = null;
		try {
			power = grantTrainedTimeStop(helper, player, "the_world");
			punch = tsPunch(helper, power);
			// Diego's donor blink: a 90 skip start and 6 per tick; blink reach 20 at 300 stamina
			((TimeStopBlinkAbility) power.getMoveset().getAbility(TimeStopCooldowns.TIME_STOP_BLINK))
					.setBaseTimeStopStaminaCosts(225.0F, 7.5F);
			helper.assertTrue(standType.summon(player, power) && power.getSummonedStandEntity() != null,
					"Could not summon The World");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(!TimeStopState.canPlayerSeeInStoppedTime(blind), "the listener fixture sees in stopped time");
			helper.assertTrue(Math.abs(blind.position().distanceTo(userPos) - 2.0D) < 1.0E-6D && blind.connection == heard,
					"the listener fixture is not 2 blocks from the user with its recording connection");
			listeners.add(blind);

			// core: 35 ticks for 90 + 35 * 6 = 300 stamina; the blind listener gets the unrevealed sound
			int skipped = runTsPunch(helper, power, stand, punch, userPos, 300.0F);
			helper.assertTrue(skipped == 35, "the core ts_punch action skipped " + skipped + " ticks, not 35");
			assertNear(helper, power.getStamina(), 0.0F, "stamina left after the core ts_punch action");
			heard.assertTimeSkipSound(helper, ModSoundEvents.THE_WORLD_TIME_STOP_UNREVEALED, "the core ts_punch action");

			// 1.16 Diego: 20 ticks (blink reach) for 90 + 20 * 6 = 210 stamina, and the blink for everyone
			punch.setSkipCappedByBlinkImpliedTicks(true).setTimeSkipSoundHeardByAll(true);
			skipped = runTsPunch(helper, power, stand, punch, userPos, 300.0F);
			helper.assertTrue(skipped == 20, "a blink-capped ts_punch action skipped " + skipped + " ticks, not 20");
			assertNear(helper, power.getStamina(), 90.0F, "stamina left after a blink-capped ts_punch action");
			heard.assertTimeSkipSound(helper, ModSoundEvents.THE_WORLD_TIME_STOP_BLINK, "a heard-by-all ts_punch action");
		}
		finally {
			listeners.remove(blind);
			if (punch != null) {
				punch.setSkipCappedByBlinkImpliedTicks(false).setTimeSkipSoundHeardByAll(false);
			}
			if (power != null && power.isSummoned() && standType != null) {
				standType.forceUnsummon(player, power);
			}
			player.discard();
		}
		helper.succeed();
	}

	// Sets the ts_punch action on the Stand (its onActionSet blinks) and returns the ticks the user skipped.
	private static int runTsPunch(GameTestHelper helper, StandPower power, StandEntity stand, TheWorldTSPunchAbility punch,
			Vec3 userPos, float stamina) {
		LivingEntity user = power.getUser();
		// two blocks behind the user, facing its way; a near-still Stand keeps the blink short, so no distance clamp
		stand.moveTo(userPos.x, userPos.y, userPos.z - 2.0D, 0.0F, 0.0F);
		stand.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(1.0E-4D);
		helper.assertTrue(stand.getAttributeValue(Attributes.MOVEMENT_SPEED) < 1.0E-3D, "the Stand fixture is not slowed");
		power.setStamina(stamina);
		helper.assertTrue(power.getStamina() == stamina, "fixture stamina did not set, got " + power.getStamina());
		int userTicks = user.tickCount;
		int standTicks = stand.tickCount;
		LivingComponentAction standAction = LivingComponentAction.getComponent(stand);
		EntityActionInstance action = punch.initActionOnAbilityUse(helper.getLevel(), user, stand, null);
		try {
			standAction.setAction(action, user, SyncType.NO_SYNC);
			helper.assertTrue(standAction.getAction() == action, "the ts_punch action did not start");
			int skipped = user.tickCount - userTicks;
			helper.assertTrue(stand.tickCount - standTicks == skipped,
					"the Stand skipped " + (stand.tickCount - standTicks) + " ticks, its user " + skipped);
			return skipped;
		}
		finally {
			standAction.setAction(null, SyncType.NO_SYNC);
		}
	}

	// getPlayers() is a read-only view; StandUtil.broadcastSoundWithCondition walks it
	@SuppressWarnings("unchecked")
	private static List<ServerPlayer> mutablePlayerList(GameTestHelper helper) {
		try {
			Field field = PlayerList.class.getDeclaredField("players");
			field.setAccessible(true);
			return (List<ServerPlayer>) field.get(helper.getLevel().getServer().getPlayerList());
		}
		catch (ReflectiveOperationException error) {
			throw new AssertionError("Could not reach PlayerList.players", error);
		}
	}

	/** Keeps the Stand sounds sent to one listener. */
	private static final class SoundRecorder extends ServerGamePacketListenerImpl {
		final List<Holder<SoundEvent>> sounds = new ArrayList<>();

		SoundRecorder(ServerLevel level, ServerPlayer player) {
			super(level.getServer(), new Connection(PacketFlow.SERVERBOUND), player,
					CommonListenerCookie.createInitial(player.getGameProfile(), false));
		}

		@Override
		public void send(Packet<?> packet) {
			if (packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof StandSkinSoundPacket sound) {
				sounds.add(sound.sound());
			}
		}

		@Override
		public void send(Packet<?> packet, PacketSendListener listener) {
			send(packet);
		}

		private boolean heard(Holder<SoundEvent> sound) {
			return sounds.stream().anyMatch(sent -> sent.value() == sound.value());
		}

		void assertTimeSkipSound(GameTestHelper helper, Holder<SoundEvent> expected, String what) {
			Holder<SoundEvent> other = expected == ModSoundEvents.THE_WORLD_TIME_STOP_BLINK
					? ModSoundEvents.THE_WORLD_TIME_STOP_UNREVEALED : ModSoundEvents.THE_WORLD_TIME_STOP_BLINK;
			helper.assertTrue(heard(expected) && !heard(other), what + ": the blind listener should hear only "
					+ expected.getRegisteredName() + ", got " + sounds.stream().map(Holder::getRegisteredName).toList());
			sounds.clear();
		}
	}

	// not added to the level; creative as GameTestHelper.makeMockServerPlayerInLevel
	private static ServerPlayer soundListener(GameTestHelper helper, boolean creative) {
		ServerLevel level = helper.getLevel();
		GameProfile profile = new GameProfile(UUID.randomUUID(), "ts-sound-listener");
		if (!creative) {
			return new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
		}
		return new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault()) {
			@Override
			public boolean isCreative() {
				return true;
			}
		};
	}

	private static void assertSkipSound(GameTestHelper helper, ServerPlayer listener, TheWorldTSPunchAbility punch,
			Holder<SoundEvent> expected, String what) {
		Holder<SoundEvent> actual = TheWorldTSPunchAbility.getTimeSkipSound(listener, punch.timeSkipSoundHeardByAll());
		helper.assertTrue(actual == expected, what + " time skip sound: expected " + expected.getRegisteredName()
				+ ", got " + actual.getRegisteredName());
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void aBlinkTargetPositionHookReplacesTheBehindRule(GameTestHelper helper) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Zombie target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 2, 4));
		try {
			Vec3 start = helper.absoluteVec(new Vec3(2.5D, 2.0D, 0.5D));
			player.moveTo(start.x, start.y, start.z, 0.0F, 0.0F);
			StandPower power = grantTrainedTimeStop(helper, player, "the_world");
			TimeStopBlinkAbility blink = (TimeStopBlinkAbility) power.getMoveset().getAbility(TimeStopCooldowns.TIME_STOP_BLINK);
			// the zombie faces south and looks 60 degrees down
			target.setYRot(0.0F);
			target.setXRot(60.0F);
			double widths = target.getBbWidth() + player.getBbWidth();
			Vec3 behind = target.position().subtract(Vec3.directionFromRotation(0.0F, 0.0F).scale(widths));
			assertVec(helper, teleportPos(blink, player, target), behind, "The World's blink behind a target (core rule)");

			// Shadow The World's 1.16 SHADOWWORLDTimeStopInstant: the whole look vector, pitch included
			BiFunction<LivingEntity, Entity, Vec3> lookBehind = (user, entity) ->
					entity.position().subtract(entity.getLookAngle().scale(entity.getBbWidth() + user.getBbWidth()));
			helper.assertTrue(blink.setEntityTargetTeleportPos(lookBehind) == blink, "the hook setter must chain");
			Vec3 pitched = target.position().subtract(target.getLookAngle().scale(widths));
			helper.assertTrue(Math.abs(pitched.y - target.getY()) > 0.5D, "the fixture's pitch does not move the landing");
			assertVec(helper, teleportPos(blink, player, target), pitched, "a blink with the add-on target position hook");

			blink.setEntityTargetTeleportPos(null);
			assertVec(helper, teleportPos(blink, player, target), behind, "a blink whose hook was cleared");
		}
		finally {
			player.discard();
			target.discard();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void aTimeStopHeldWalkSpeedAppliesWhileItCharges(GameTestHelper helper) {
		TimeStopAbility timeStop = ModStandAbilities.TIME_STOP.get().createInstance(
				new AbilityId(PowerClass.STAND, JojoMod.resLoc("the_world"), TimeStopLearning.TIME_STOP));
		TimeStopAbility.TimeStopAction action = new TimeStopAbility.TimeStopAction(timeStop);
		action.onSetPhase(ActionPhase.BUTTON_CHARGE);
		helper.assertTrue(action.userWalkSpeed == 1.0F, "the core time stop's charge locked walking: " + action.userWalkSpeed);
		// Shadow The World's 1.16 heldWalkSpeed(0.7F)
		helper.assertTrue(timeStop.setHeldWalkSpeed(0.7F) == timeStop && timeStop.getHeldWalkSpeed() == 0.7F,
				"an add-on could not set its time stop's held walk speed");
		action.onSetPhase(ActionPhase.BUTTON_CHARGE);
		helper.assertTrue(Math.abs(action.userWalkSpeed - 0.7F) < EPS, "the charge ignored the held walk speed: " + action.userWalkSpeed);
		action.onSetPhase(ActionPhase.PERFORM);
		helper.assertTrue(action.userWalkSpeed == 1.0F, "the held walk speed outlived the charge: " + action.userWalkSpeed);
		boolean refused = false;
		try {
			timeStop.setHeldWalkSpeed(1.5F);
		}
		catch (IllegalArgumentException expected) {
			refused = true;
		}
		helper.assertTrue(refused && timeStop.getHeldWalkSpeed() == 0.7F, "a held walk speed above 1 was taken");
		helper.succeed();
	}

	private static StandPower grantTrainedTimeStop(GameTestHelper helper, Player player, String standName) {
		helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add the " + standName + " test player");
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standName));
		helper.assertTrue(standType != null, "Missing Stand type " + standName);
		StandPower power = PowerClass.STAND.attachGet(player);
		helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
				== StandPowerTransitions.Status.APPLIED, "Could not grant " + standName);
		PowerSkillUnlocks.Result unlocked = TEST_UNLOCKS.forceUnlock(power, TimeStopLearning.TIME_STOP);
		helper.assertTrue(unlocked == PowerSkillUnlocks.Result.UNLOCKED || unlocked == PowerSkillUnlocks.Result.ALREADY_UNLOCKED,
				"Could not unlock " + standName + "'s time stop: " + unlocked);
		// trained to 100 ticks
		power.getCurTypeData().setAbilityLearningProgressPoints(TimeStopLearning.TIME_STOP,
				TimeStopLearning.HUMAN_MAX_TIME_STOP_TICKS - TimeStopLearning.MIN_TIME_STOP_TICKS,
				TimeStopLearning.getMaxTrainingPoints(power), power);
		helper.assertTrue(TimeStopLearning.getTimeStopTicks(power) == TimeStopLearning.HUMAN_MAX_TIME_STOP_TICKS,
				standName + " time stop did not train to 100 ticks: " + TimeStopLearning.getTimeStopTicks(power));
		return power;
	}

	private static TheWorldTSPunchAbility tsPunch(GameTestHelper helper, StandPower power) {
		Ability punch = power.getMoveset().getAbility("ts_punch");
		helper.assertTrue(punch instanceof TheWorldTSPunchAbility, "The World lost its ts_punch");
		return (TheWorldTSPunchAbility) punch;
	}

	// 1.16 TheWorldTSHeavyAttack: clamp(floor((stamina - start) / tick), 0, time stop ticks)
	private static void assertAffordable(GameTestHelper helper, StandPower power, float start, float tick, String what) {
		float multiplier = PlayerClientBroadcastedSettings.getTimeStopStaminaCostMultiplier(power);
		int expected = Mth.clamp(Mth.floor((power.getStamina() - start * multiplier) / (tick * multiplier)),
				0, TimeStopLearning.getTimeStopTicks(power));
		int actual = TimeStopLearning.getAffordableTsPunchTimeStopTicks(power);
		helper.assertTrue(actual == expected, what + " TS punch affordable ticks: expected " + expected + ", got " + actual);
	}

	private static void assertSkip(GameTestHelper helper, StandPower power, TheWorldTSPunchAbility punch,
			float stamina, int expected, String what) {
		power.setStamina(stamina);
		helper.assertTrue(power.getStamina() == stamina, what + ": fixture stamina did not set, got " + power.getStamina());
		int actual = punch.getAffordableTimeSkipTicks(power);
		helper.assertTrue(actual == expected, what + " skip ticks: expected " + expected + ", got " + actual);
	}

	private static Vec3 teleportPos(TimeStopBlinkAbility blink, LivingEntity user, Entity target) {
		try {
			Method method = TimeStopBlinkAbility.class.getDeclaredMethod("getEntityTargetTeleportPos",
					LivingEntity.class, Entity.class);
			method.setAccessible(true);
			return (Vec3) method.invoke(blink, user, target);
		}
		catch (ReflectiveOperationException error) {
			throw new AssertionError("Could not ask the blink for its entity target position", error);
		}
	}

	private static void assertNear(GameTestHelper helper, float actual, float expected, String what) {
		helper.assertTrue(Math.abs(actual - expected) < EPS, what + ": expected " + expected + ", got " + actual);
	}

	private static void assertVec(GameTestHelper helper, Vec3 actual, Vec3 expected, String what) {
		helper.assertTrue(actual.distanceTo(expected) < EPS, what + ": expected " + expected + ", got " + actual);
	}
}
