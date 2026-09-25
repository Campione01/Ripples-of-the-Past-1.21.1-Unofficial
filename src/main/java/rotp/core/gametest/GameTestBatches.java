package rotp.core.gametest;

/** Gametest batch names. */
final class GameTestBatches {
	/**
	 * A batch runs its tests side by side (8 per row, 6 blocks apart, up to 50) and batches run one after another.
	 * A time stop started by another test of the same batch (1-2 chunks around it, held for several ticks) freezes
	 * every test cell it covers: a frozen user's action does not tick and a frozen target does not tick. Tests that
	 * need their user, action or target to tick in normal time run in this batch, which starts no time stop.
	 */
	static final String NO_TIME_STOP = "jojo_ripples_no_time_stop";

	private GameTestBatches() {}
}
