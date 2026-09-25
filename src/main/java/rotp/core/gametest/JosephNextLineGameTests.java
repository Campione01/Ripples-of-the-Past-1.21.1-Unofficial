package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.HamonTechnique;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.mechanics.resolve.ResolveCounter;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
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

// 1.16 GameplayEventHandler.onChatMessage: Joseph-technique users within 8 blocks may predict a chat line
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JosephNextLineGameTests {
	private JosephNextLineGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void listenersAndLineFollow116(GameTestHelper helper) {
		List<ServerPlayer> players = new ArrayList<>();
		ServerPlayer sender = addPlayer(helper, players, FakePlayerFactory.get(helper.getLevel(),
				new GameProfile(UUID.randomUUID(), "JosephLineSender")), 0.0D);
		ServerPlayer joseph = addPlayer(helper, players, FakePlayerFactory.get(helper.getLevel(),
				new GameProfile(UUID.randomUUID(), "JosephLineNear")), 3.0D);
		ServerPlayer zeppeli = addPlayer(helper, players, FakePlayerFactory.get(helper.getLevel(),
				new GameProfile(UUID.randomUUID(), "JosephLineZeppeli")), 2.0D);
		ServerPlayer farJoseph = addPlayer(helper, players, FakePlayerFactory.get(helper.getLevel(),
				new GameProfile(UUID.randomUUID(), "JosephLineFar")), 12.0D);
		ServerPlayer hiddenJoseph = addPlayer(helper, players, new FakePlayer(helper.getLevel(),
				new GameProfile(UUID.randomUUID(), "JosephLineHidden")) {
			@Override
			public ChatVisiblity getChatVisibility() {
				return ChatVisiblity.HIDDEN;
			}
		}, 1.0D);
		Consumer<ServerChatEvent> cancelJoseph = event -> {
			if (event.getPlayer() == joseph) {
				event.setCanceled(true);
			}
		};
		try {
			grantTechnique(helper, sender, ModHamonSkills.CHARACTER_JOSEPH.get());
			grantTechnique(helper, joseph, ModHamonSkills.CHARACTER_JOSEPH.get());
			grantTechnique(helper, zeppeli, ModHamonSkills.CHARACTER_ZEPPELI.get());
			grantTechnique(helper, farJoseph, ModHamonSkills.CHARACTER_JOSEPH.get());
			grantTechnique(helper, hiddenJoseph, ModHamonSkills.CHARACTER_JOSEPH.get());

			List<ServerPlayer> listeners = ResolveCounter.josephNextLineListeners(sender);
			helper.assertTrue(listeners.contains(joseph), "A Joseph user 3 blocks away cannot predict the line");
			helper.assertTrue(!listeners.contains(sender), "The sender predicts his own line");
			helper.assertTrue(!listeners.contains(zeppeli), "A non-Joseph Hamon user predicts the line");
			helper.assertTrue(!listeners.contains(farJoseph), "A Joseph user 12 blocks away predicts the line");
			helper.assertTrue(!listeners.contains(hiddenJoseph), "A Joseph user with chat hidden predicts the line");
			helper.assertTrue(ResolveCounter.JOSEPH_NEXT_LINE_CHANCE == 0.05F,
					"Next-line chance is " + ResolveCounter.JOSEPH_NEXT_LINE_CHANCE + ", 1.16 had 0.05");

			for (int i = 1; i <= 3; i++) {
				helper.assertTrue(Language.getInstance().has("jojo.chat.joseph.next_line." + i),
						"Missing lang key jojo.chat.joseph.next_line." + i);
			}

			Component message = ResolveCounter.sayJosephNextLine(joseph, "hello there", 2);
			helper.assertTrue(message != null, "The next line was not broadcast");
			Component line = assertTranslatable(helper, message, "chat.type.text", 1);
			assertTranslatable(helper, line, "jojo.chat.joseph.next_line.2", 0);
			helper.assertTrue(line.getString().contains("hello there"), "The line lost the quoted text: " + line.getString());

			// a chat listener cancelling the Joseph's line stops it, as ForgeHooks.onServerChatEvent did
			NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ServerChatEvent.class, cancelJoseph);
			helper.assertTrue(ResolveCounter.sayJosephNextLine(joseph, "cancelled", 1) == null,
					"A cancelled next line was still broadcast");
			helper.succeed();
		}
		finally {
			NeoForge.EVENT_BUS.unregister(cancelJoseph);
			players.forEach(ServerPlayer::discard);
		}
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void chatRollsFivePercentForNearbyJoseph(GameTestHelper helper) {
		List<ServerPlayer> players = new ArrayList<>();
		ServerPlayer sender = addPlayer(helper, players, FakePlayerFactory.get(helper.getLevel(),
				new GameProfile(UUID.randomUUID(), "JosephRollSender")), 0.0D);
		ServerPlayer joseph = addPlayer(helper, players, FakePlayerFactory.get(helper.getLevel(),
				new GameProfile(UUID.randomUUID(), "JosephRollNear")), 3.0D);
		List<Component> lines = new ArrayList<>();
		Consumer<ServerChatEvent> capture = event -> {
			if (event.getPlayer() == joseph) {
				lines.add(event.getMessage());
			}
		};
		try {
			grantTechnique(helper, joseph, ModHamonSkills.CHARACTER_JOSEPH.get());
			RandomSource random = joseph.getRandom();
			long hitSeed = -1;
			long missSeed = -1;
			for (long seed = 0; seed < 100000 && (hitSeed < 0 || missSeed < 0); seed++) {
				random.setSeed(seed);
				if (random.nextFloat() < 0.05F) {
					if (hitSeed < 0) hitSeed = seed;
				}
				else if (missSeed < 0) {
					missSeed = seed;
				}
			}
			helper.assertTrue(hitSeed >= 0 && missSeed >= 0, "No seed found for the 5% roll");
			NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ServerChatEvent.class, capture);

			random.setSeed(missSeed);
			NeoForge.EVENT_BUS.post(new ServerChatEvent(sender, "no luck", Component.literal("no luck")));
			helper.assertTrue(lines.isEmpty(), "A roll over 0.05 still predicted the line");

			random.setSeed(hitSeed);
			NeoForge.EVENT_BUS.post(new ServerChatEvent(sender, "watch this", Component.literal("watch this")));
			helper.assertTrue(lines.size() == 1, "A roll under 0.05 predicted " + lines.size() + " lines, expected 1");
			Component line = lines.get(0);
			helper.assertTrue(line.getContents() instanceof TranslatableContents translatable
					&& translatable.getKey().startsWith("jojo.chat.joseph.next_line."),
					"Unexpected next line " + line);
			helper.assertTrue(line.getString().contains("watch this"), "The line lost the quoted text: " + line.getString());
			helper.succeed();
		}
		finally {
			NeoForge.EVENT_BUS.unregister(capture);
			players.forEach(ServerPlayer::discard);
		}
	}

	// 1.16 broadcast the line as CHAT: "Commands Only" and hidden chat drop it, full chat (the Joseph too) gets it
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void nextLineReachesOnlyFullChat(GameTestHelper helper) {
		List<ServerPlayer> players = new ArrayList<>();
		try {
			ChatPlayer joseph = (ChatPlayer) addPlayer(helper, players, new ChatPlayer(helper, "JosephChatSelf", ChatVisiblity.FULL), 0.0D);
			ChatPlayer full = (ChatPlayer) addPlayer(helper, players, new ChatPlayer(helper, "JosephChatFull", ChatVisiblity.FULL), 2.0D);
			ChatPlayer commandsOnly = (ChatPlayer) addPlayer(helper, players, new ChatPlayer(helper, "JosephChatSystem", ChatVisiblity.SYSTEM), 3.0D);
			ChatPlayer hidden = (ChatPlayer) addPlayer(helper, players, new ChatPlayer(helper, "JosephChatHidden", ChatVisiblity.HIDDEN), 4.0D);

			Component message = ResolveCounter.sayJosephNextLine(joseph, "nice weather", 3, List.copyOf(players));
			helper.assertTrue(message != null, "The next line was not broadcast");
			assertTranslatable(helper, message, "chat.type.text", 1);
			for (ChatPlayer player : List.of(joseph, full)) {
				helper.assertTrue(player.received.size() == 1 && player.received.get(0) == message,
						player.getGameProfile().getName() + " with full chat got " + player.received + ", expected the line once");
			}
			helper.assertTrue(commandsOnly.received.isEmpty(),
					"A player with chat set to Commands Only got the line: " + commandsOnly.received);
			helper.assertTrue(hidden.received.isEmpty(), "A player with chat hidden got the line: " + hidden.received);
			helper.succeed();
		}
		finally {
			players.forEach(ServerPlayer::discard);
		}
	}

	private static ServerPlayer addPlayer(GameTestHelper helper, List<ServerPlayer> players, ServerPlayer player, double offset) {
		player.setGameMode(GameType.SURVIVAL);
		player.setNoGravity(true);
		player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 4, 2))).add(offset, 0.0D, 0.0D));
		helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add " + player.getGameProfile().getName());
		players.add(player);
		return player;
	}

	private static void grantTechnique(GameTestHelper helper, ServerPlayer player, HamonTechnique technique) {
		PlayerPower power = PowerClass.PLAYER_POWER.attachGet(player);
		power.setPowerType(ModPlayerPowers.HAMON.get());
		HamonData hamon = PlayerPower.getPowerData(player, ModPlayerPowers.HAMON).orElseThrow();
		hamon.setHamonStatPoints(HamonData.HamonStat.STRENGTH, 1000, true, true);
		hamon.setHamonStatPoints(HamonData.HamonStat.CONTROL, 2000, true, true);
		helper.assertTrue(hamon.pickHamonTechnique(player, technique),
				"Could not pick " + technique.getRegistryKey().getPath() + " for " + player.getGameProfile().getName());
	}

	// returns the argument at argIndex as a Component
	private static Component assertTranslatable(GameTestHelper helper, Component component, String key, int argIndex) {
		helper.assertTrue(component.getContents() instanceof TranslatableContents,
				"Not a translatable message: " + component);
		TranslatableContents contents = (TranslatableContents) component.getContents();
		helper.assertTrue(key.equals(contents.getKey()), "Expected key " + key + ", got " + contents.getKey());
		Object[] args = contents.getArgs();
		helper.assertTrue(args.length > argIndex, "Message " + key + " has " + args.length + " args");
		Object arg = args[argIndex];
		return arg instanceof Component argComponent ? argComponent : Component.literal(String.valueOf(arg));
	}

	// records system messages; fixed chat setting
	private static final class ChatPlayer extends FakePlayer {
		final List<Component> received = new ArrayList<>();
		private final ChatVisiblity visibility;

		ChatPlayer(GameTestHelper helper, String name, ChatVisiblity visibility) {
			super(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
			this.visibility = visibility;
		}

		@Override
		public ChatVisiblity getChatVisibility() {
			return visibility != null ? visibility : super.getChatVisibility();
		}

		@Override
		public void sendSystemMessage(Component component, boolean bypassHiddenChat) {
			received.add(component);
		}
	}
}
