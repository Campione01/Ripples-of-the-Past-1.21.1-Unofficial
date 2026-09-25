package rotp.core.gametest;

/** Gametest batch names. */
final class GameTestBatches {
	/**
	 * A batch runs its tests side by side (8 per row, 6 blocks apart, up to 50) and batches run one after another.
	 * A time stop started by another test of the same batch (1-2 chunks around it, held for several ticks) freezes
	 * every test cell it covers: a frozen user's action does not tick and a frozen target does not tick. Tests that
	 * need their user, action or target to tick in normal time run in this batch, which starts no time stop.
	 * The default (unnamed) batch is time-stop-free: every test that puts a TimeStopState instance or starts
	 * TimeStopAbility (via tryPutInstance, startTimeStopAfterHold or new TimeStopState.Instance) must instead
	 * carry batch = GameTestBatches.TIME_STOP, so it cannot freeze a neighbour cell in the default batch.
	 * GameTestBatchSourcePinGameTests checks this rule.
	 */
	static final String NO_TIME_STOP = "jojo_ripples_no_time_stop";

	/** Batch for tests that put a TimeStopState instance or start TimeStopAbility; keeps them off the default batch. */
	static final String TIME_STOP = "jojo_ripples_time_stop";

	private GameTestBatches() {}
}
