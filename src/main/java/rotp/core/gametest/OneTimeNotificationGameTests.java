package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.command.commands.JojoControlsCommand;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.entityattachment.PlayerOneTimeNotifications;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.AttachmentInternals;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 PlayerUtilCap one-time notifications: the /jojocontrols tip when a power is first given
 * (PowerBaseImpl.onNewPowerGiven) and the view distance warning for manual Stand control (StandEntity.move).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OneTimeNotificationGameTests {
	private OneTimeNotificationGameTests() {}

	private static ServerPlayer addPlayer(GameTestHelper helper, String name) {
		ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
		Vec3 pos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		player.moveTo(pos.x, pos.y, pos.z);
		helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add " + name);
		return player;
	}

	// Sets the PlayerList fields for this one call (the setters would resend chunks to every player), then restores them
	private static boolean warnWithServerDistances(StandEntity stand, ServerPlayer player, double distanceSqr, int view, int simulation) {
		player.removeData(ModDataAttachmentTypes.PLAYER_ONE_TIME_NOTIFICATIONS);
		PlayerList players = player.server.getPlayerList();
		try {
			Field viewField = PlayerList.class.getDeclaredField("viewDistance");
			Field simulationField = PlayerList.class.getDeclaredField("simulationDistance");
			viewField.setAccessible(true);
			simulationField.setAccessible(true);
			int oldView = viewField.getInt(players);
			int oldSimulation = simulationField.getInt(players);
			viewField.setInt(players, view);
			simulationField.setInt(players, simulation);
			try {
				return stand.warnManualControlBeyondViewDistance(player, distanceSqr);
			}
			finally {
				viewField.setInt(players, oldView);
				simulationField.setInt(players, oldSimulation);
			}
		}
		catch (NoSuchFieldException | IllegalAccessException e) {
			throw new AssertionError("PlayerList view/simulation distance fields are not accessible", e);
		}
	}

	private static boolean hintSent(ServerPlayer player) {
		return PlayerOneTimeNotifications.get(player).wasSent(PlayerOneTimeNotifications.POWER_CONTROLS);
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void firstStandSendsControlsHintOnce(GameTestHelper helper) {
		ServerPlayer player = addPlayer(helper, "HintStandUser");
		try {
			helper.assertFalse(hintSent(player), "A new player already had the controls hint");
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("magicians_red"));
			helper.assertTrue(type != null, "Missing registered Magician's Red");
			StandPower power = PowerClass.STAND.attachGet(player);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Magician's Red");
			helper.assertTrue(hintSent(player), "A first Stand did not send the /jojocontrols hint (1.16 onNewPowerGiven)");
			helper.assertFalse(JojoControlsCommand.sendPowerControlsHint(player), "The controls hint was sent a second time");
			helper.succeed();
		}
		finally {
			player.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void firstPlayerPowerSendsControlsHint(GameTestHelper helper) {
		ServerPlayer player = addPlayer(helper, "HintHamonUser");
		try {
			helper.assertFalse(hintSent(player), "A new player already had the controls hint");
			PowerClass.PLAYER_POWER.attachGet(player).setPowerType(ModPlayerPowers.HAMON.get());
			helper.assertTrue(hintSent(player), "A first Hamon power did not send the /jojocontrols hint (1.16 onNewPowerGiven)");
			helper.succeed();
		}
		finally {
			player.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void controlsHintSuggestsTheCommand(GameTestHelper helper) {
		MutableComponent hint = JojoControlsCommand.powerControlsHint();
		helper.assertTrue(hint.getContents() instanceof TranslatableContents tr && "jojo.chat.controls.message".equals(tr.getKey()),
				"The hint does not use jojo.chat.controls.message");
		Object arg = ((TranslatableContents) hint.getContents()).getArgs()[0];
		helper.assertTrue(arg instanceof Component, "The hint has no command link");
		Component link = (Component) arg;
		ClickEvent click = link.getStyle().getClickEvent();
		helper.assertTrue("/jojocontrols".equals(link.getString()), "Link text is " + link.getString());
		helper.assertTrue(click != null && click.getAction() == ClickEvent.Action.SUGGEST_COMMAND
				&& "/jojocontrols".equals(click.getValue()), "The link does not suggest /jojocontrols");
		helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.GREEN).equals(link.getStyle().getColor()), "The link is not green");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void sentFlagsSurviveSaveAndDeath(GameTestHelper helper) {
		ServerPlayer oldPlayer = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "HintOld"));
		ServerPlayer newPlayer = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "HintNew"));
		PlayerOneTimeNotifications data = PlayerOneTimeNotifications.get(oldPlayer);
		helper.assertTrue(data.markSent(PlayerOneTimeNotifications.HIGH_STAND_RANGE), "First mark returned false");
		helper.assertFalse(data.markSent(PlayerOneTimeNotifications.HIGH_STAND_RANGE), "Second mark returned true");

		CompoundTag tag = data.serializeNBT(helper.getLevel().registryAccess());
		PlayerOneTimeNotifications loaded = new PlayerOneTimeNotifications();
		loaded.deserializeNBT(helper.getLevel().registryAccess(), tag);
		helper.assertTrue(loaded.wasSent(PlayerOneTimeNotifications.HIGH_STAND_RANGE), "The sent flag was lost on save and load");
		helper.assertFalse(loaded.wasSent(PlayerOneTimeNotifications.POWER_CONTROLS), "An unsent flag came back as sent");

		AttachmentInternals.copyEntityAttachments(oldPlayer, newPlayer, true);
		helper.assertTrue(PlayerOneTimeNotifications.get(newPlayer).wasSent(PlayerOneTimeNotifications.HIGH_STAND_RANGE),
				"The sent flag was lost on death");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void viewDistanceWarningThreshold(GameTestHelper helper) {
		// 1.16: horizontal distance beyond viewDistance * 16 - 4
		helper.assertFalse(StandEntity.isBeyondManualControlViewDistance(156 * 156, 10), "Warned at exactly 156 blocks, view distance 10");
		helper.assertTrue(StandEntity.isBeyondManualControlViewDistance(156 * 156 + 1, 10), "No warning past 156 blocks, view distance 10");
		helper.assertFalse(StandEntity.isBeyondManualControlViewDistance(28 * 28, 2), "Warned at exactly 28 blocks, view distance 2");
		helper.assertTrue(StandEntity.isBeyondManualControlViewDistance(28 * 28 + 1, 2), "No warning past 28 blocks, view distance 2");
		// 1.21: entities stop ticking past the simulation distance, so the lower of both limits applies
		helper.assertTrue(StandEntity.isBeyondManualControlViewDistance(60 * 60 + 1, 10, 4), "No warning past 60 blocks, simulation distance 4");
		helper.assertFalse(StandEntity.isBeyondManualControlViewDistance(60 * 60, 10, 4), "Warned at exactly 60 blocks, simulation distance 4");
		helper.assertTrue(StandEntity.isBeyondManualControlViewDistance(60 * 60 + 1, 4, 10), "No warning past 60 blocks, view distance 4");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void manualStandWarnsOnceBeyondViewDistance(GameTestHelper helper) {
		ServerPlayer player = addPlayer(helper, "HintRangeUser");
		StandEntity stand = null;
		try {
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(type != null, "Missing registered Star Platinum");
			StandPower power = PowerClass.STAND.attachGet(player);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			helper.assertTrue(type.summon(player, power), "Could not summon Star Platinum");
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Summoned Star Platinum entity is missing");
			stand.moveTo(player.getX(), player.getY(), player.getZ());

			// Explicit distances: the gametest server never sets view or simulation distance on its PlayerList (both 0)
			helper.assertFalse(stand.warnManualControlBeyondViewDistance(player, 200.0 * 200.0, 10, 10),
					"Warned while the Stand was not manually controlled");
			stand.setManuallyControlled(true);
			helper.assertFalse(stand.warnManualControlBeyondViewDistance(player, 156.0 * 156.0, 10, 10), "Warned within view distance");
			// 1.16 never warns within 27 blocks, whatever the view distance
			helper.assertFalse(stand.warnManualControlBeyondViewDistance(player, 26.0 * 26.0, 0, 0), "Warned within 27 blocks");
			// Only the horizontal distance counts: 150 across and 60 up stays within 156
			stand.moveTo(player.getX(), player.getY() + 60, player.getZ());
			helper.assertFalse(stand.warnManualControlBeyondViewDistance(player, 150.0 * 150.0 + 60.0 * 60.0, 10, 10),
					"Warned because of the vertical distance");
			stand.moveTo(player.getX(), player.getY(), player.getZ());
			helper.assertFalse(PlayerOneTimeNotifications.get(player).wasSent(PlayerOneTimeNotifications.HIGH_STAND_RANGE),
					"The warning flag was set too early");
			// Simulation distance 4 caps manual control at 60 blocks
			helper.assertTrue(stand.warnManualControlBeyondViewDistance(player, 61.0 * 61.0, 10, 4), "No warning beyond simulation distance");
			helper.assertTrue(PlayerOneTimeNotifications.get(player).wasSent(PlayerOneTimeNotifications.HIGH_STAND_RANGE),
					"The warning flag was not set");
			helper.assertFalse(stand.warnManualControlBeyondViewDistance(player, 200.0 * 200.0, 10, 10), "The warning was sent a second time");

			// The move() path reads this server's PlayerList: distinct view and simulation distances tell them apart
			PlayerList players = player.server.getPlayerList();
			int viewBefore = players.getViewDistance();
			int simulationBefore = players.getSimulationDistance();
			helper.assertTrue(warnWithServerDistances(stand, player, 61.0 * 61.0, 10, 2),
					"move() path ignored the server simulation distance (view 10, simulation 2, 61 blocks)");
			helper.assertTrue(warnWithServerDistances(stand, player, 61.0 * 61.0, 2, 10),
					"move() path ignored the server view distance (view 2, simulation 10, 61 blocks)");
			helper.assertFalse(warnWithServerDistances(stand, player, 61.0 * 61.0, 10, 10),
					"move() path warned within both server distances (view 10, simulation 10, 61 blocks)");
			helper.assertTrue(players.getViewDistance() == viewBefore && players.getSimulationDistance() == simulationBefore,
					"The server distances were not restored");
			helper.succeed();
		}
		finally {
			if (stand != null) {
				stand.discard();
			}
			player.discard();
		}
	}
}
