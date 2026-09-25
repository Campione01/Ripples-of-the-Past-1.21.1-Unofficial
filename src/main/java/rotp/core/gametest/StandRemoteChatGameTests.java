package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.stands._entitybase.StandEntityManualControlToggle;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.mechanics.resolve.ResolveCounter;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// 1.16 GameplayEventHandler.messageAsStand: chat from a user remote-controlling a Stand is spoken by the Stand
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandRemoteChatGameTests {
	private StandRemoteChatGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void remoteStandSpeaksToHearersFollow116(GameTestHelper helper) {
		List<ServerPlayer> players = new ArrayList<>();
		StandSetup setup = new StandSetup();
		try {
			ChatPlayer sender = addPlayer(helper, players, new ChatPlayer(helper, "StandChatSender", ChatVisiblity.FULL, 0), 0.0D);
			// Stand 8 blocks from its user: range is measured from the Stand
			setup.summon(helper, sender, 8.0D);
			ChatPlayer hearer = addPlayer(helper, players, new ChatPlayer(helper, "StandChatHearer", ChatVisiblity.FULL, 0), 20.0D);
			ChatPlayer behindUser = addPlayer(helper, players, new ChatPlayer(helper, "StandChatBehind", ChatVisiblity.FULL, 0), -10.0D);
			ChatPlayer deaf = addPlayer(helper, players, new ChatPlayer(helper, "StandChatDeaf", ChatVisiblity.FULL, 0), 9.0D);
			ChatPlayer hidden = addPlayer(helper, players, new ChatPlayer(helper, "StandChatHidden", ChatVisiblity.SYSTEM, 0), 9.0D);
			ChatPlayer op = addPlayer(helper, players, new ChatPlayer(helper, "StandChatOp", ChatVisiblity.FULL, 3), 7.0D);
			for (ServerPlayer player : List.of(hearer, behindUser, hidden, op)) {
				player.addEffect(new MobEffectInstance(ModStatusEffects.SPIRIT_VISION, 400));
			}
			setup.stand.onlyVisibleToStandUsers = true;
			helper.assertTrue(!setup.stand.getName().getString().equals(sender.getName().getString()),
					"Stand and user share a name, the speaker check is meaningless");
			helper.assertTrue(Language.getInstance().has("chat.stand_remote_reveal_name"),
					"Missing lang key chat.stand_remote_reveal_name");
			helper.assertTrue(ResolveCounter.STAND_CHAT_RANGE == 16.0D,
					"Stand chat range is " + ResolveCounter.STAND_CHAT_RANGE + ", 1.16 had 16");

			// granting the Stand sent the sender the one-time controls hint; count only chat from here
			helper.assertTrue(!setup.stand.isManuallyControlled(), "The Stand started out remote-controlled");
			players.forEach(player -> ((ChatPlayer) player).received.clear());

			// without remote control the chat is untouched
			ServerChatEvent plain = new ServerChatEvent(sender, "plain chat", Component.literal("plain chat"));
			NeoForge.EVENT_BUS.post(plain);
			helper.assertTrue(!plain.isCanceled(), "Chat without remote control was cancelled");
			helper.assertTrue(sender.received.isEmpty(),
					"Chat without remote control was taken over by the Stand: " + sender.received);

			StandEntityManualControlToggle.on(helper.getLevel(), setup.stand);
			helper.assertTrue(setup.stand.isManuallyControlled(), "Could not enter remote control");
			setup.place(helper, 8.0D);
			sender.received.clear();
			ServerChatEvent remote = new ServerChatEvent(sender, "ora ora", Component.literal("ora ora"));
			NeoForge.EVENT_BUS.post(remote);
			helper.assertTrue(remote.isCanceled(), "Remote-controlled chat still went out under the user's name");
			helper.assertTrue(sender.received.size() == 1,
					"The user got " + sender.received.size() + " Stand lines, expected 1");
			assertStandLine(helper, sender.received.get(0), setup.stand, "ora ora", false, "user");

			List<ServerPlayer> candidates = List.copyOf(players);
			players.forEach(player -> ((ChatPlayer) player).received.clear());
			ResolveCounter.sendStandChat(sender, setup.stand, Component.literal("yare yare"), candidates);
			assertStandLine(helper, single(helper, sender, "user"), setup.stand, "yare yare", false, "user");
			assertStandLine(helper, single(helper, hearer, "hearer 12 blocks from the Stand"), setup.stand, "yare yare", false, "hearer");
			assertStandLine(helper, single(helper, op, "op"), setup.stand, "yare yare", true, "op");
			helper.assertTrue(behindUser.received.isEmpty(), "A hearer 18 blocks from the Stand got the line");
			helper.assertTrue(deaf.received.isEmpty(), "A player who cannot hear Stands got the line");
			helper.assertTrue(hidden.received.isEmpty(), "A player hiding player chat got the line");

			// a Stand visible to all is heard by everyone near it
			setup.stand.onlyVisibleToStandUsers = false;
			players.forEach(player -> ((ChatPlayer) player).received.clear());
			ResolveCounter.sendStandChat(sender, setup.stand, Component.literal("yare yare"), candidates);
			assertStandLine(helper, single(helper, deaf, "non-Stand user near a visible Stand"), setup.stand, "yare yare", false, "deaf");
			helper.assertTrue(behindUser.received.isEmpty(), "A player 18 blocks from a visible Stand got the line");
			helper.succeed();
		}
		finally {
			setup.close();
			players.forEach(ServerPlayer::discard);
		}
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void standLineOnlyReachesJosephsWhoHearStands(GameTestHelper helper) {
		List<ServerPlayer> players = new ArrayList<>();
		StandSetup setup = new StandSetup();
		List<ServerPlayer> spoke = new ArrayList<>();
		ServerPlayer[] josephs = new ServerPlayer[2];
		// records and stops the Josephs' lines so they do not chain
		Consumer<ServerChatEvent> capture = event -> {
			if (event.getPlayer() == josephs[0] || event.getPlayer() == josephs[1]) {
				spoke.add(event.getPlayer());
				event.setCanceled(true);
			}
		};
		try {
			ServerPlayer sender = addPlayer(helper, players, FakePlayerFactory.get(helper.getLevel(),
					new GameProfile(UUID.randomUUID(), "StandChatJoSender")), 0.0D);
			setup.summon(helper, sender, 2.0D);
			ServerPlayer hearingJoseph = addPlayer(helper, players, FakePlayerFactory.get(helper.getLevel(),
					new GameProfile(UUID.randomUUID(), "StandChatJoHearing")), 2.0D);
			ServerPlayer deafJoseph = addPlayer(helper, players, FakePlayerFactory.get(helper.getLevel(),
					new GameProfile(UUID.randomUUID(), "StandChatJoDeaf")), 3.0D);
			josephs[0] = hearingJoseph;
			josephs[1] = deafJoseph;
			grantJoseph(helper, hearingJoseph);
			grantJoseph(helper, deafJoseph);
			hearingJoseph.addEffect(new MobEffectInstance(ModStatusEffects.SPIRIT_VISION, 400));

			List<ServerPlayer> asUser = ResolveCounter.josephNextLineListeners(sender, false);
			helper.assertTrue(asUser.contains(hearingJoseph) && asUser.contains(deafJoseph),
					"A line said by the user did not reach both Josephs");
			List<ServerPlayer> asStand = ResolveCounter.josephNextLineListeners(sender, true);
			helper.assertTrue(asStand.contains(hearingJoseph), "A Joseph who hears Stands cannot predict the Stand's line");
			helper.assertTrue(!asStand.contains(deafJoseph), "A Joseph who cannot hear Stands predicts the Stand's line");

			// through the chat event: both Josephs would roll a hit, only the one hearing Stands speaks
			StandEntityManualControlToggle.on(helper.getLevel(), setup.stand);
			helper.assertTrue(setup.stand.isManuallyControlled(), "Could not enter remote control");
			setup.place(helper, 2.0D);
			long hitSeed = hitSeed(hearingJoseph.getRandom());
			helper.assertTrue(hitSeed >= 0, "No seed found for the 5% roll");
			hearingJoseph.getRandom().setSeed(hitSeed);
			deafJoseph.getRandom().setSeed(hitSeed);
			NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ServerChatEvent.class, capture);
			ServerChatEvent remote = new ServerChatEvent(sender, "watch this", Component.literal("watch this"));
			NeoForge.EVENT_BUS.post(remote);
			helper.assertTrue(remote.isCanceled(), "Remote-controlled chat was not taken over by the Stand");
			helper.assertTrue(!spoke.contains(deafJoseph), "A Joseph who cannot hear Stands predicted the Stand's line");
			helper.assertTrue(spoke.contains(hearingJoseph), "A Joseph who hears Stands did not predict the Stand's line");
			helper.succeed();
		}
		finally {
			NeoForge.EVENT_BUS.unregister(capture);
			setup.close();
			players.forEach(ServerPlayer::discard);
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void standChatCountsTowardsSpamKick(GameTestHelper helper) {
		ServerPlayer spammer = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "StandChatSpammer"));
		helper.assertTrue(ResolveCounter.STAND_CHAT_SPAM_LIMIT == 200,
				"Spam limit is " + ResolveCounter.STAND_CHAT_SPAM_LIMIT + ", 1.16 had 200");
		int first = ResolveCounter.addStandChatSpamTicks(spammer);
		int second = ResolveCounter.addStandChatSpamTicks(spammer);
		helper.assertTrue(first == 20 && second == 40, "Stand chat spam ticks went " + first + ", " + second + ", expected 20, 40");
		helper.runAfterDelay(10, () -> {
			int later = ResolveCounter.addStandChatSpamTicks(spammer);
			// 40 - 10 ticks of decay + 20
			helper.assertTrue(later >= 48 && later <= 52, "After 10 ticks the spam count is " + later + ", expected about 50");
			helper.succeed();
		});
	}

	private static long hitSeed(RandomSource random) {
		for (long seed = 0; seed < 100000; seed++) {
			random.setSeed(seed);
			if (random.nextFloat() < ResolveCounter.JOSEPH_NEXT_LINE_CHANCE) {
				return seed;
			}
		}
		return -1;
	}

	private static Component single(GameTestHelper helper, ChatPlayer player, String who) {
		helper.assertTrue(player.received.size() == 1, "The " + who + " got " + player.received.size() + " Stand lines, expected 1");
		return player.received.get(0);
	}

	// "<Stand> text"; ops get the hover naming the user
	private static void assertStandLine(GameTestHelper helper, Component message, StandEntity stand, String text,
			boolean reveal, String who) {
		helper.assertTrue(message.getContents() instanceof TranslatableContents contents
				&& "chat.type.text".equals(contents.getKey()) && contents.getArgs().length == 2,
				"The " + who + " got a line that is not chat.type.text: " + message);
		Object[] args = ((TranslatableContents) message.getContents()).getArgs();
		helper.assertTrue(args[0] instanceof Component name && name.getString().equals(stand.getName().getString()),
				"The " + who + " saw the line spoken by " + args[0] + ", expected the Stand " + stand.getName().getString());
		helper.assertTrue(args[1] instanceof Component body && body.getString().equals(text),
				"The " + who + " got the text " + args[1] + ", expected " + text);
		HoverEvent hover = ((Component) args[0]).getStyle().getHoverEvent();
		HoverEvent.EntityTooltipInfo info = hover != null ? hover.getValue(HoverEvent.Action.SHOW_ENTITY) : null;
		boolean revealed = info != null && info.name.isPresent()
				&& info.name.get().getContents() instanceof TranslatableContents hoverKey
				&& "chat.stand_remote_reveal_name".equals(hoverKey.getKey());
		helper.assertTrue(revealed == reveal, "The " + who + (reveal ? " did not see" : " saw") + " the Stand user reveal");
		if (reveal) {
			helper.assertTrue(info.id.equals(stand.getUUID()), "The reveal hover does not point at the Stand");
		}
	}

	private static <T extends ServerPlayer> T addPlayer(GameTestHelper helper, List<ServerPlayer> players, T player, double offset) {
		player.setGameMode(GameType.SURVIVAL);
		player.setNoGravity(true);
		player.setPos(origin(helper).add(offset, 0.0D, 0.0D));
		helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add " + player.getGameProfile().getName());
		players.add(player);
		return player;
	}

	private static Vec3 origin(GameTestHelper helper) {
		return Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 4, 2)));
	}

	private static void grantJoseph(GameTestHelper helper, ServerPlayer player) {
		PlayerPower power = PowerClass.PLAYER_POWER.attachGet(player);
		power.setPowerType(ModPlayerPowers.HAMON.get());
		HamonData hamon = PlayerPower.getPowerData(player, ModPlayerPowers.HAMON).orElseThrow();
		hamon.setHamonStatPoints(HamonData.HamonStat.STRENGTH, 1000, true, true);
		hamon.setHamonStatPoints(HamonData.HamonStat.CONTROL, 2000, true, true);
		helper.assertTrue(hamon.pickHamonTechnique(player, ModHamonSkills.CHARACTER_JOSEPH.get()),
				"Could not pick the Joseph technique for " + player.getGameProfile().getName());
	}

	// a summoned Star Platinum, not ticked, placed along x from the test origin
	private static final class StandSetup {
		ServerPlayer user;
		StandPower power;
		StandEntity stand;

		void summon(GameTestHelper helper, ServerPlayer user, double offset) {
			this.user = user;
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(type != null, "Missing registered Star Platinum");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant the Stand");
			stand = ModEntityTypes.HUMANOID_STAND.get().create(helper.getLevel());
			helper.assertTrue(stand != null, "Could not create the Stand entity");
			stand.withStandType(type);
			stand.copyPosition(user);
			power.setSummonedStand(stand);
			helper.assertTrue(stand.getUser() == user && power.getSummonedStandEntity() == stand, "The Stand is not summoned");
			place(helper, offset);
		}

		void place(GameTestHelper helper, double offset) {
			Vec3 pos = origin(helper).add(offset, 0.0D, 0.0D);
			stand.setPos(pos.x, pos.y, pos.z);
		}

		void close() {
			if (user != null && user.hasData(ModDataAttachmentTypes.CONTROLLER)) {
				user.getData(ModDataAttachmentTypes.CONTROLLER).stopControlling();
			}
			if (stand != null) {
				StandEntityManualControlToggle.off(user.level(), stand, false);
				if (power != null) power.setSummonedStand(null);
				stand.discard();
			}
		}
	}

	// records system messages; fixed chat setting and permission level
	private static final class ChatPlayer extends FakePlayer {
		final List<Component> received = new ArrayList<>();
		private final ChatVisiblity visibility;
		private final int permissionLevel;

		ChatPlayer(GameTestHelper helper, String name, ChatVisiblity visibility, int permissionLevel) {
			super(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
			this.visibility = visibility;
			this.permissionLevel = permissionLevel;
		}

		@Override
		public ChatVisiblity getChatVisibility() {
			return visibility != null ? visibility : super.getChatVisibility();
		}

		@Override
		protected int getPermissionLevel() {
			return permissionLevel;
		}

		@Override
		public void sendSystemMessage(Component component, boolean bypassHiddenChat) {
			received.add(component);
		}
	}
}
