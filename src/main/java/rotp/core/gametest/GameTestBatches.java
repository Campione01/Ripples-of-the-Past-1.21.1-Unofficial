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
	 * carry batch = GameTestBatches.TIME_STOP or its specifically pinned isolated batch, so it cannot freeze a
	 * neighbour cell in the default batch.
	 * GameTestBatchSourcePinGameTests checks this rule.
	 */
	static final String NO_TIME_STOP = "jojo_ripples_no_time_stop";

	/**
	 * Batch for tests that put a TimeStopState instance or start TimeStopAbility; keeps them off the default batch.
	 * Its tests share the level's time stops, so none of them may keep a stop over its test cells past the tick that
	 * put it: a stop held there through later ticks froze the neighbour cells' mobs and beams, which then failed
	 * depending on where the random test grid fell. A test that holds its stop in place runs in an exact batch of its
	 * own; one that only needs a stop to run its course moves it far away (TimeStopState.Instance.withArea).
	 */
	static final String TIME_STOP = "jojo_ripples_time_stop";

	/** The heavy-pulse suite needs normal-time inputs between its own stopped-target cases. */
	static final String DETACHED_HEAVY_TIME_STOP = "jojo_ripples_detached_heavy_time_stop";

	/** The frozen boat's stop stays over its cell until The World's heavy punch lands. */
	static final String HELD_TIME_STOP_BOAT = "jojo_ripples_held_time_stop_boat";

	/** The eye beam scenes hold their stops over their cells for several ticks, one scene at a time. */
	static final String HELD_TIME_STOP_EYE_BEAM = "jojo_ripples_held_time_stop_eye_beam";

	private GameTestBatches() {}
}
