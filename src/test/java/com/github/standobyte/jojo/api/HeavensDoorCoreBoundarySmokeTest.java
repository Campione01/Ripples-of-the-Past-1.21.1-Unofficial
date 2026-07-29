package com.github.standobyte.jojo.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class HeavensDoorCoreBoundarySmokeTest {
	private HeavensDoorCoreBoundarySmokeTest() {}

	public static void run() {
		try {
			Path root = Path.of(System.getProperty("user.dir"));
			String standardMenu = read(root,
					"src/main/java/com/github/standobyte/jojo/mixin/"
							+ "control/ServerPlayerOperationMixin.java");
			check(standardMenu.contains(
							"openMenu(Lnet/minecraft/world/MenuProvider;"
									+ "Ljava/util/function/Consumer;)"
									+ "Ljava/util/OptionalInt;"),
					"canonical ServerPlayer openMenu overload is not hooked");
			check(standardMenu.contains("menuProvider != null"),
					"null MenuProvider semantics are not preserved");

			String wrapper = read(root,
					"src/main/java/com/github/standobyte/jojo/subsystems/"
							+ "entity_playerwrapper/"
							+ "ServerPlayerLivingWrapper.java");
			assertBeforeInOpenMenu(wrapper,
					"PlayerOperationPolicies.intercept(",
					"nextContainerCounter()",
					"as-non-player policy runs after counter allocation");
			check(wrapper.contains("menuProvider != null"),
					"as-non-player null provider semantics changed");

			String external = read(root,
					"src/main/java/com/github/standobyte/jojo/subsystems/"
							+ "entity_externalcontainer/"
							+ "PlayerExternalContainers.java");
			assertBeforeInOpenMenu(external,
					"PlayerOperationPolicies.intercept(",
					"nextContainerCounter()",
					"external policy runs after counter allocation");
			assertBeforeInOpenMenu(external,
					"menu == null",
					"PlayerOperationPolicies.intercept(",
					"external null menu is evaluated by addon policy");

			String crafting = read(root,
					"src/main/java/com/github/standobyte/jojo/mixin/"
							+ "crafting/CraftingMenuMixin.java");
			assertBefore(crafting,
					"PlayerOperationPolicies.intercept(",
					"StandUserCraftingContext.push(player)",
					"crafting denial runs after context push");
			check(crafting.contains(
							"resultSlots.setItem(0, ItemStack.EMPTY)"),
					"denied crafting recompute does not clear stale result");

			String staleTake = read(root,
					"src/main/java/com/github/standobyte/jojo/mixin/"
							+ "crafting/CraftingResultSlotTakeMixin.java");
			check(staleTake.contains("method = \"mayPickup\"")
							&& staleTake.contains(
									"method = \"tryRemove\"")
							&& staleTake.contains(
									"instanceof ResultSlot"),
					"stale crafting result pickup is not blocked");

			String standPower = read(root,
					"src/main/java/com/github/standobyte/jojo/powersystem/"
							+ "standpower/StandPower.java");
			String playerPower = read(root,
					"src/main/java/com/github/standobyte/jojo/powersystem/"
							+ "playerpower/PlayerPower.java");
			check(standPower.contains(
							"LeapSource.STAND")
							&& playerPower.contains(
									"LeapSource.PLAYER_POWER"),
					"both powered leap authorities are not policy-bound");

			String screen = read(root,
					"src/main/java/com/github/standobyte/jojo/client/ui/"
							+ "screen_jojomenu/"
							+ "RockPaperScissorsScreen.java");
			assertBefore(screen,
					"StandPower standPower",
					"PlayerPower playerPower",
					"RPS cheat selection is not Stand-first");

			String cheatPacket = read(root,
					"src/main/java/com/github/standobyte/jojo/network/"
							+ "c2s/ClRPSGameInputPacket.java");
			check(cheatPacket.contains(
							"StandPower.get(player)")
							&& cheatPacket.contains(
									"RpsCheatRegistrations.find("),
					"server does not re-resolve the active Stand");
			String mindRead = between(cheatPacket,
					"private static void handleMindReadCheat(",
					"private static void handleVampirismCheat(");
			check(!mindRead.contains("Pick.random(")
							&& !mindRead.contains(
									"setOpponentThoughts("),
					"MIND_READ fabricates opponent input");
			check(mindRead.contains("isReciprocalGame("),
					"MIND_READ does not validate reciprocal sessions");
			check(cheatPacket.contains(
							"payload.cheatSessionEpoch")
							&& cheatPacket.contains(
									"game.tryUseCheat(sessionEpoch)"),
					"RPS cheat packet is not bound to the server session");
			String thoughtsPacket = read(root,
					"src/main/java/com/github/standobyte/jojo/network/"
							+ "c2s/ClRPSPickThoughtsPacket.java");
			check(thoughtsPacket.contains(
							"game.sessionEpoch() "
									+ "!= payload.sessionEpoch()")
							&& thoughtsPacket.contains(
									"opponentGame.opponent()"),
					"RPS thought input is not session and pair bound");
			String packetsRegister = read(root,
					"src/main/java/com/github/standobyte/jojo/"
							+ "PacketsRegister.java");
			check(packetsRegister.contains(
							"event.registrar(\"2\")"),
					"RPS wire change did not advance core protocol");

			String game = read(root,
					"src/main/java/com/github/standobyte/jojoimpl/npc/"
							+ "rps/RockPaperScissorsGame.java");
			check(game.contains("putLong(\"SessionEpoch\"")
							&& game.contains(
									"putInt(\"CheatUsedRound\"")
							&& game.contains(
									"putBoolean(\"CheatedBefore\""),
					"RPS cheat authority state is not persisted");
		}
		catch (IOException error) {
			throw new AssertionError(
					"failed to inspect Heaven's Door core boundaries",
					error);
		}
	}

	private static String read(Path root, String relative)
			throws IOException {
		return Files.readString(root.resolve(relative));
	}

	private static void assertBeforeInOpenMenu(
			String source,
			String first,
			String second,
			String message) {
		String method = source.substring(
				source.indexOf("public OptionalInt openMenu("));
		assertBefore(method, first, second, message);
	}

	private static void assertBefore(
			String source,
			String first,
			String second,
			String message) {
		int firstIndex = source.indexOf(first);
		int secondIndex = source.indexOf(second);
		check(firstIndex >= 0
						&& secondIndex >= 0
						&& firstIndex < secondIndex,
				message);
	}

	private static String between(
			String source,
			String start,
			String end) {
		int startIndex = source.indexOf(start);
		int endIndex = source.indexOf(end, startIndex + start.length());
		check(startIndex >= 0 && endIndex > startIndex,
				"could not isolate source contract section");
		return source.substring(startIndex, endIndex);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
