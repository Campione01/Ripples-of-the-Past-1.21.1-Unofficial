package rotp.core.gametest;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;

import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.api.timestop.TimeStopLifecycleEvent;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModCriteriaTriggers;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModItems;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.impl.powers.vampirism.VampirismState;
import rotp.core.impl.stands.theworld.TimeStopAbility;
import rotp.core.impl.stands.theworld.TimeStopBlinkAbility;
import rotp.core.mechanics.resolve.ResolveModeEffect;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.timestop.TimeStopCooldowns;
import rotp.core.subsystems.timestop.TimeStopLearning;
import rotp.core.subsystems.timestop.TimeStopState;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 time_ability ("Power to Reign over the World!", jojo:action_perform on the Star Platinum / The World
 * time stop, blink and TS punch) and the hidden time_stop_9 ("This is the Greatest High!", a jojo:time_stop
 * effect of 180+ ticks, 100 XP).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TimeAbilityAdvancementGameTests {
	private static final String TIME_ABILITY = "jojo/time_ability";
	private static final String TIME_STOP_9 = "jojo/time_stop_9";
	private static final int NINE_SECONDS = 180;
	private static final ChunkPos FAR_AWAY = new ChunkPos(50000, 50000);
	private static final PowerSkillUnlocks.Owner TEST_UNLOCKS =
			PowerSkillUnlocks.register(JojoMod.resLoc("time_ability_advancement_gametest"));

	private TimeAbilityAdvancementGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void timeAdvancementsKeepTheir116Display(GameTestHelper helper) {
		AdvancementHolder timeAbility = advancement(helper, TIME_ABILITY);
		AdvancementHolder timeStop9 = advancement(helper, TIME_STOP_9);
		helper.assertTrue(timeAbility.value().parent().map(JojoMod.resLoc("jojo/summon_stand")::equals).orElse(false),
				"time_ability should follow jojo/summon_stand");
		DisplayInfo abilityDisplay = timeAbility.value().display().orElseThrow();
		helper.assertTrue(abilityDisplay.getType() == AdvancementType.TASK && !abilityDisplay.isHidden()
				&& abilityDisplay.shouldAnnounceChat() && abilityDisplay.getIcon().is(Items.CLOCK),
				"time_ability should be a shown, announced task with a clock icon");
		helper.assertTrue(timeStop9.value().parent().map(JojoMod.resLoc(TIME_ABILITY)::equals).orElse(false),
				"time_stop_9 should follow jojo/time_ability");
		DisplayInfo stopDisplay = timeStop9.value().display().orElseThrow();
		helper.assertTrue(stopDisplay.getType() == AdvancementType.TASK && stopDisplay.isHidden(),
				"time_stop_9 should be a hidden task");
		helper.assertTrue(timeStop9.value().rewards().experience() == 100,
				"time_stop_9 should reward 100 XP, was " + timeStop9.value().rewards().experience());
		// 1.16 meteoric_scrap {Icon:7}
		ItemStack icon = stopDisplay.getIcon();
		CustomModelData data = icon.get(DataComponents.CUSTOM_MODEL_DATA);
		helper.assertTrue(icon.is(ModItems.METEORIC_SCRAP.get()) && data != null && data.value() == 7,
				"time_stop_9 icon should be meteoric_scrap custom_model_data 7, was " + icon + " " + data);
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void aTimeStandAbilityGrantsTimeAbility(GameTestHelper helper) {
		AdvancementHolder timeAbility = advancement(helper, TIME_ABILITY);
		ServerPlayer starPlatinum = new TestPlayer(helper, "TimeAbilitySP");
		ServerPlayer other = new TestPlayer(helper, "TimeAbilityOther");
		try {
			StandPower power = grantStand(helper, starPlatinum, "star_platinum");
			Ability blink = power.getMoveset().getAbility(TimeStopCooldowns.TIME_STOP_BLINK);
			helper.assertTrue(blink instanceof TimeStopBlinkAbility, "Star Platinum lost its time_stop_blink");
			helper.assertTrue(!done(starPlatinum, timeAbility), "getting Star Platinum granted time_ability");
			power.setStamina(power.getMaxStamina());
			helper.assertTrue(performBlink((TimeStopBlinkAbility) blink, helper.getLevel(), starPlatinum, power),
					"the Star Platinum time stop blink did not perform");
			helper.assertTrue(done(starPlatinum, timeAbility),
					"a Star Platinum blink did not grant time_ability (1.16 action_perform star_platinum_ts_blink)");

			// The World's real time stop start is run by onlyANineSecondTimeStopGrantsTimeStop9

			// 1.16 listed only the Star Platinum and The World actions
			ModCriteriaTriggers.triggerTimeAbility(other, grantStand(helper, other, "magicians_red"));
			ModCriteriaTriggers.triggerTimeAbility(other, null);
			helper.assertTrue(!done(other, timeAbility), "a Stand without a 1.16 time ability granted time_ability");
			helper.succeed();
		}
		finally {
			starPlatinum.getAdvancements().stopListening();
			other.getAdvancements().stopListening();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void onlyANineSecondTimeStopGrantsTimeStop9(GameTestHelper helper) {
		AdvancementHolder timeAbility = advancement(helper, TIME_ABILITY);
		AdvancementHolder timeStop9 = advancement(helper, TIME_STOP_9);
		ServerPlayer player = new TestPlayer(helper, "TimeStopNine");
		TimeStopState timeStops = helper.getLevel().getData(ModDataAttachmentTypes.TIME_STOP.get());
		// the add-on pre-start seam moves this stop far from every test structure, so nothing nearby freezes
		Consumer<TimeStopLifecycleEvent.PreStart> isolate = event -> {
			if (event.getInstance().userId() == player.getId()) {
				event.setInstance(event.getInstance().withArea(FAR_AWAY, 1));
			}
		};
		NeoForge.EVENT_BUS.addListener(TimeStopLifecycleEvent.PreStart.class, isolate);
		try {
			StandPower power = grantStand(helper, player, "the_world");
			TimeStopAbility timeStop = trainNineSecondTimeStop(helper, player, power);
			helper.assertTrue(!done(player, timeAbility) && !done(player, timeStop9),
					"the fixture granted a time advancement before any time stop");

			// a release at 179 ticks (TimeStopAction.onButtonStopHold)
			startTimeStop(helper, timeStop, player, power, 179);
			helper.assertTrue(done(player, timeAbility),
					"a The World time stop did not grant time_ability (1.16 action_perform the_world_time_stop)");
			helper.assertTrue(!done(player, timeStop9), "a 179-tick time stop granted time_stop_9");
			endTimeStop(timeStops, player);

			// the full charge: the trained 180 ticks (TimeStopAction.actionPerformStart)
			startTimeStop(helper, timeStop, player, power, -1);
			helper.assertTrue(done(player, timeStop9), "a 9 second time stop did not grant time_stop_9 (1.16 min 180 ticks)");
			helper.succeed();
		}
		finally {
			// removed before the level ticks
			endTimeStop(timeStops, player);
			NeoForge.EVENT_BUS.unregister(isolate);
			player.getAdvancements().stopListening();
		}
	}

	// 180 ticks needs a vampire high on blood (human cap 100); resolve 3+ starts without the Stand body
	private static TimeStopAbility trainNineSecondTimeStop(GameTestHelper helper, ServerPlayer player, StandPower power) {
		PowerSkillUnlocks.Result unlocked = TEST_UNLOCKS.forceUnlock(power, TimeStopLearning.TIME_STOP);
		helper.assertTrue(unlocked == PowerSkillUnlocks.Result.UNLOCKED || unlocked == PowerSkillUnlocks.Result.ALREADY_UNLOCKED,
				"Could not unlock The World's time stop: " + unlocked);
		power.setResolveLevel(power.getMaxResolveLevel());
		helper.assertTrue(ResolveModeEffect.getEffectiveResolveLevel(player, power) >= 3,
				"The World's resolve did not reach 3: " + ResolveModeEffect.getEffectiveResolveLevel(player, power));
		PowerClass.PLAYER_POWER.attachGet(player).setPowerType(ModPlayerPowers.VAMPIRISM.get());
		var blood = VampirismState.get(player).blood();
		blood.setCurrent(blood.max());
		int maxPoints = TimeStopLearning.getMaxTrainingPoints(power);
		power.getCurTypeData().setAbilityLearningProgressPoints(TimeStopLearning.TIME_STOP, maxPoints, maxPoints, power);
		helper.assertTrue(TimeStopLearning.getTimeStopTicks(power) == NINE_SECONDS,
				"The World did not train to a 180-tick time stop: " + TimeStopLearning.getTimeStopTicks(power));
		Ability ability = power.getMoveset().getAbility(TimeStopLearning.TIME_STOP);
		helper.assertTrue(ability instanceof TimeStopAbility, "The World lost its time_stop");
		return (TimeStopAbility) ability;
	}

	// TimeStopAbility.startTimeStopAfterHold: releasedTicks < 0 is the full charge (the trained ticks)
	private static void startTimeStop(GameTestHelper helper, TimeStopAbility timeStop, ServerPlayer player,
			StandPower power, int releasedTicks) {
		power.setStamina(power.getMaxStamina());
		boolean started;
		try {
			Method method = releasedTicks < 0
					? TimeStopAbility.class.getDeclaredMethod("startTimeStopAfterHold", LivingEntity.class, boolean.class)
					: TimeStopAbility.class.getDeclaredMethod("startTimeStopAfterHold",
							LivingEntity.class, boolean.class, int.class);
			method.setAccessible(true);
			started = (boolean) (releasedTicks < 0
					? method.invoke(timeStop, player, false)
					: method.invoke(timeStop, player, false, releasedTicks));
		}
		catch (ReflectiveOperationException error) {
			throw new AssertionError("Could not start the time stop", error);
		}
		int ticks = releasedTicks < 0 ? NINE_SECONDS : releasedTicks;
		helper.assertTrue(started, "The World's " + ticks + "-tick time stop did not start");
		TimeStopState.Instance instance = helper.getLevel().getData(ModDataAttachmentTypes.TIME_STOP.get())
				.getInstance(player.getId()).orElse(null);
		helper.assertTrue(instance != null && instance.totalTicks() == ticks && FAR_AWAY.equals(instance.centerPos()),
				"the isolated time stop instance should last " + ticks + " ticks, was " + instance);
		// the effect covers the stop and its opening; time_stop_9 checks 180 + opening
		MobEffectInstance effect = player.getEffect(ModStatusEffects.TIME_STOP);
		int expected = ticks + TimeStopAbility.TIME_STOP_OPENING_SETTLE_TICKS;
		helper.assertTrue(effect != null && effect.getDuration() == expected, "the time stop effect should last "
				+ expected + " ticks, was " + (effect != null ? effect.getDuration() : "none"));
	}

	private static void endTimeStop(TimeStopState timeStops, ServerPlayer player) {
		timeStops.removeInstance(player.getId());
		player.removeEffect(ModStatusEffects.TIME_STOP);
	}

	private static StandPower grantStand(GameTestHelper helper, ServerPlayer player, String standName) {
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standName));
		helper.assertTrue(standType != null, "Missing Stand type " + standName);
		StandPower power = PowerClass.STAND.attachGet(player);
		helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
				== StandPowerTransitions.Status.APPLIED, "Could not grant " + standName);
		return power;
	}

	private static boolean performBlink(TimeStopBlinkAbility blink, ServerLevel level, LivingEntity user, StandPower power) {
		try {
			Method method = TimeStopBlinkAbility.class.getDeclaredMethod("performBlink",
					ServerLevel.class, LivingEntity.class, StandPower.class);
			method.setAccessible(true);
			return (boolean) method.invoke(blink, level, user, power);
		}
		catch (ReflectiveOperationException error) {
			throw new AssertionError("Could not perform the time stop blink", error);
		}
	}

	private static AdvancementHolder advancement(GameTestHelper helper, String name) {
		AdvancementHolder holder = helper.getLevel().getServer().getAdvancements().get(JojoMod.resLoc(name));
		helper.assertTrue(holder != null, "missing advancement " + name);
		return holder;
	}

	private static boolean done(ServerPlayer player, AdvancementHolder holder) {
		return player.getAdvancements().getOrStartProgress(holder).isDone();
	}

	/** Real (non-fake) server player, so advancements are granted; packets are dropped. */
	private static final class TestPlayer extends ServerPlayer {
		TestPlayer(GameTestHelper helper, String name) {
			super(helper.getLevel().getServer(), helper.getLevel(),
					new GameProfile(UUID.randomUUID(), name), ClientInformation.createDefault());
			this.connection = new SilentConnection(helper.getLevel(), this);
			Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
			moveTo(origin.x, origin.y, origin.z);
		}
	}

	private static final class SilentConnection extends ServerGamePacketListenerImpl {
		SilentConnection(ServerLevel level, ServerPlayer player) {
			super(level.getServer(), openConnection(), player,
					CommonListenerCookie.createInitial(player.getGameProfile(), false));
		}

		// as GameTestHelper.makeMockServerPlayerInLevel: a live channel, so channel lookups do not fail
		private static Connection openConnection() {
			Connection connection = new Connection(PacketFlow.SERVERBOUND);
			new EmbeddedChannel(connection);
			return connection;
		}

		@Override
		public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
		}
	}
}
