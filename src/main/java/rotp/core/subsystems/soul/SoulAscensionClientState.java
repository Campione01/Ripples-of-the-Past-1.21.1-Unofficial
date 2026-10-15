package rotp.core.subsystems.soul;

/**
 * The decisions of 1.16 ControllerSoul, without the client classes they act on: whether the client follows its player's
 * soul with the camera, whether it holds the vanilla death screen back, and whether it shows the prompt to skip the
 * ascension. The client keeps one instance and calls it once a tick and whenever a death screen would open.
 */
public final class SoulAscensionClientState {
	private boolean followingSoul;
	private boolean firstDeathFrame;
	private boolean soulEntityWaiting;

	public void reset() {
		followingSoul = false;
		firstDeathFrame = false;
		soulEntityWaiting = false;
	}

	/**
	 * Once a client tick, after the client has looked for its player's soul on the camera.
	 * <p>
	 * In the tick its player dies the server spawns the soul, puts the camera on it and sends the kill; the player's
	 * health of 0 only follows with the server's next tick. Like 1.16, which followed the soul from its spawn whatever the
	 * health, the client follows a live soul of its player also before it has seen the player die.
	 *
	 * @param ownSoulLives the soul the client found on its camera, or already follows, is in the level and its player's
	 */
	public void tick(boolean ownSoulLives, boolean playerDead, boolean playerSpectator) {
		followingSoul = ownSoulLives && !playerSpectator;
		if (playerDead) {
			if (soulEntityWaiting && followingSoul) {
				soulEntityWaiting = false;
			}
		}
		else {
			firstDeathFrame = true;
			soulEntityWaiting = false;
		}
	}

	public boolean followsSoul() {
		return followingSoul;
	}

	/**
	 * A death screen is about to open. The first one of a death waits for the soul when the server said one will spawn;
	 * once the soul is gone (or none came) the vanilla death screen opens, as in 1.16.
	 *
	 * @param soulCamera the camera is on the player's live soul right now
	 * @return true to hold the death screen back
	 */
	public boolean holdsDeathScreen(boolean soulCamera, boolean willSoulSpawn) {
		if (!soulEntityWaiting && firstDeathFrame && willSoulSpawn) {
			soulEntityWaiting = true;
			firstDeathFrame = false;
		}
		return soulCamera || soulEntityWaiting;
	}

	public boolean awaitsSoul() {
		return soulEntityWaiting;
	}

	/** The server said no soul spawned, or the player skipped the ascension before its soul came. */
	public void stopWaiting() {
		soulEntityWaiting = false;
	}

	public boolean showsSkipPrompt(boolean playerDead) {
		return followingSoul || playerDead && soulEntityWaiting;
	}
}
