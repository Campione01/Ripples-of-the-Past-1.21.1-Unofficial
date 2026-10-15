package rotp.core.subsystems.soul;

/**
 * The decisions of 1.16 ControllerSoul, without the client classes they act on: whether the client follows its player's
 * soul with the camera, whether it holds the vanilla death screen back, whether it shows the prompt to skip the
 * ascension and how long it keeps its dead player's body. The client keeps one instance and calls it once a tick and
 * whenever a death screen would open.
 */
public final class SoulAscensionClientState {
	// LocalPlayer.tickDeath removes the body at 20
	private static final int KEPT_DEATH_TIME = 18;

	private boolean followingSoul;
	private boolean firstDeathFrame;
	private boolean soulEntityWaiting;
	private boolean killSeen;
	private boolean deathSeen;
	// 1.16 kept its playerSoulEntity until the player lived again
	private boolean soulCame;
	private boolean ascensionSkipped;

	public void reset() {
		followingSoul = false;
		firstDeathFrame = false;
		soulEntityWaiting = false;
		killSeen = false;
		deathSeen = false;
		soulCame = false;
		ascensionSkipped = false;
	}

	/**
	 * Once a client tick, after the client has looked for its player's soul on the camera.
	 * <p>
	 * In the tick its player dies the server spawns the soul, puts the camera on it and sends the kill; the player's
	 * health of 0 only follows with the server's next tick. The client ticks in between belong to the death: like 1.16,
	 * which followed the soul from its spawn whatever the health, the client follows a live soul of its player before it
	 * has seen the player die, and a soul skipped in those ticks leaves no wait behind.
	 *
	 * @param ownSoulLives the soul the client found on its camera, or already follows, is in the level and its player's
	 */
	public void tick(boolean ownSoulLives, boolean playerDead, boolean playerSpectator) {
		followingSoul = ownSoulLives && !playerSpectator;
		if (playerDead) {
			deathSeen = true;
		}
		// a new life: alive after the death was seen (respawned or revived), alive with neither a kill nor a soul, or alive
		// after an unskipped soul ended, which the server does that early only for a player revived in the tick it died
		else if (deathSeen || !followingSoul && (!killSeen || soulCame && !ascensionSkipped)) {
			newLife();
		}
		if (followingSoul) {
			soulCame = true;
		}
		if (playerDead && soulCame) {
			soulEntityWaiting = false;
		}
	}

	private void newLife() {
		firstDeathFrame = true;
		soulEntityWaiting = false;
		killSeen = false;
		deathSeen = false;
		soulCame = false;
		ascensionSkipped = false;
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
		killSeen = true;
		if (!soulEntityWaiting && firstDeathFrame && willSoulSpawn) {
			soulEntityWaiting = true;
			firstDeathFrame = false;
		}
		return soulCamera || soulEntityWaiting;
	}

	public boolean awaitsSoul() {
		return soulEntityWaiting;
	}

	/** The server said no soul spawned. */
	public void stopWaiting() {
		soulEntityWaiting = false;
	}

	/** 1.16 InputHandler read the jump key every tick without a screen, so a held jump skips as a press does. */
	public boolean skipsAscension(boolean soulCamera, boolean jumpDown, boolean screenOpen) {
		return jumpDown && !screenOpen && (soulCamera || soulEntityWaiting);
	}

	/**
	 * The player skipped the ascension: the soul it follows, which the server ends, or the wait for one. As in 1.16 the
	 * prompt goes at once, and no later death screen of this death waits again.
	 */
	public void skip() {
		soulEntityWaiting = false;
		firstDeathFrame = false;
		ascensionSkipped = true;
	}

	public boolean showsSkipPrompt(boolean playerDead) {
		return followingSoul && !ascensionSkipped || playerDead && soulEntityWaiting;
	}

	/** 1.16 kept the dead player's body for the whole death, behind the death screen too. */
	public int keptDeathTime(boolean playerDead, int deathTime) {
		return playerDead ? Math.min(deathTime, KEPT_DEATH_TIME) : deathTime;
	}
}
