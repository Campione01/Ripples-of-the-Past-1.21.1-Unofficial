package com.github.standobyte.jojoimpl.npc.rps;

import java.util.UUID;

import com.github.standobyte.jojoimpl.npc.rps.RockPaperScissorsGame.Pick;

public final class RpsCheatStateSmokeTest {
	private RpsCheatStateSmokeTest() {}

	public static void run() {
		RockPaperScissorsGame game =
				new RockPaperScissorsGame(
						UUID.randomUUID(),
						UUID.randomUUID(),
						true,
						null,
						null,
						null,
						1,
						0,
						0);
		long epoch = game.sessionEpoch();
		check(epoch != 0L, "RPS session epoch was zero");
		check(!game.tryUseCheat(epoch + 1L),
				"stale RPS session epoch was accepted");
		check(game.tryUseCheat(epoch),
				"first RPS cheat in a round was rejected");
		check(!game.tryUseCheat(epoch),
				"duplicate RPS cheat in a round was accepted");
		check(game.markCheatedBefore(),
				"first match cheat was not marked as first use");
		check(!game.markCheatedBefore(),
				"later match cheat was marked as first use");

		RockPaperScissorsGame loaded =
				RockPaperScissorsGame.load(game.save());
		check(loaded.sessionEpoch() == epoch,
				"RPS session epoch did not persist");
		check(loaded.hasCheatedBefore(),
				"RPS first-use state did not persist");
		check(!loaded.tryUseCheat(epoch),
				"RPS per-round dedupe did not persist");

		loaded.submitPlayer(Pick.ROCK);
		loaded.submitOpponent(Pick.SCISSORS);
		loaded.advanceRoundAfterResolve();
		check(loaded.tryUseCheat(epoch),
				"RPS cheat was not re-enabled for a new round");
		check(!RockPaperScissorsGame.load(loaded.save())
						.tryUseCheat(epoch),
				"new-round RPS dedupe did not persist");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
