package rotp.core.gametest;

import rotp.core.core.JojoMod;
import rotp.core.subsystems.soul.SoulAscensionClientState;
import rotp.core.subsystems.soul.SoulEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 ControllerSoul followed a dying Stand user's soul with the camera from its spawn, held the vanilla death screen back
 * while the soul ascended or was awaited, and let it open once the soul was gone (its respawn() call sat behind a condition
 * that never held). The server sends the soul, the camera on it and the kill in the tick the player dies, the player's
 * health of 0 only with its next tick; a client that dropped the soul in the ticks between stayed dead without a screen.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SoulAscensionClientGameTests {
	private static final int ASCENSION_TICKS = 200;

	private SoulAscensionClientGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void soulFollowedFromTheKillThenVanillaDeathScreen(GameTestHelper helper) {
		// client ticks between the kill and the health of 0: none, one, or two on a slow client
		for (int ticksBeforeHealth = 0; ticksBeforeHealth <= 2; ticksBeforeHealth++) {
			String run = " (" + ticksBeforeHealth + " client ticks between the kill and the health of 0)";
			Client client = new Client();
			client.tick();
			client.soulSpawnsWithCamera();
			check(helper, !client.deathScreenOpens(), "The kill's death screen opened although the soul spawned" + run);
			for (int i = 0; i < ticksBeforeHealth; i++) {
				client.tick();
				check(helper, client.state.followsSoul(),
						"The client let go of its soul before it saw its player die; 1.16 followed it from its spawn" + run);
			}
			client.playerDead = true;
			for (int i = 0; i < ASCENSION_TICKS; i++) {
				check(helper, !client.deathScreenOpens(), "A death screen opened while the soul ascended, tick " + i + run);
				client.tick();
				check(helper, client.state.followsSoul() && client.cameraOnSoul,
						"The camera left the ascending soul at tick " + i + run);
				check(helper, client.state.showsSkipPrompt(true), "The skip prompt went away while the soul ascended" + run);
			}
			client.soulEnds();
			check(helper, client.deathScreenOpens(),
					"The vanilla death screen did not open once the soul was gone; 1.16 opened it" + run);
			client.tick();
			check(helper, !client.state.followsSoul() && !client.state.showsSkipPrompt(true) && !client.state.awaitsSoul(),
					"The client still follows or awaits a soul that is gone" + run);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void skippedAscensionOpensVanillaDeathScreen(GameTestHelper helper) {
		Client ascending = new Client();
		ascending.tick();
		ascending.soulSpawnsWithCamera();
		ascending.deathScreenOpens();
		ascending.tick();
		ascending.playerDead = true;
		ascending.deathScreenOpens();
		ascending.tick();
		check(helper, ascending.state.followsSoul(), "Fixture: the client does not follow its soul");
		// the jump skips the ascension; the server ends the soul on its next tick
		ascending.soulEnds();
		check(helper, ascending.deathScreenOpens(), "The vanilla death screen did not open after the ascension was skipped");

		// the soul is awaited, as when time is stopped, and the jump gives up on it
		Client awaiting = new Client();
		awaiting.tick();
		awaiting.playerDead = true;
		check(helper, !awaiting.deathScreenOpens(), "The death screen opened although a soul was awaited");
		awaiting.tick();
		check(helper, awaiting.state.awaitsSoul() && awaiting.state.showsSkipPrompt(true),
				"The awaited soul shows no skip prompt");
		awaiting.state.stopWaiting();
		check(helper, awaiting.deathScreenOpens(), "The vanilla death screen did not open after the awaited soul was skipped");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void noSoulKeepsVanillaDeathScreen(GameTestHelper helper) {
		Client noStand = new Client();
		noStand.willSoulSpawn = false;
		noStand.tick();
		check(helper, noStand.deathScreenOpens(), "Without a soul to come the kill's death screen did not open");
		noStand.tick();
		check(helper, !noStand.state.showsSkipPrompt(false) && !noStand.state.showsSkipPrompt(true),
				"Without a soul to come the skip prompt shows");

		// the server's no-soul answer arrives before the kill
		Client failedSpawn = new Client();
		failedSpawn.tick();
		failedSpawn.willSoulSpawn = false;
		failedSpawn.state.stopWaiting();
		check(helper, failedSpawn.deathScreenOpens(), "The death screen did not open after the server said no soul spawned");

		// a soul that was awaited and then reported as not spawned
		Client late = new Client();
		late.tick();
		late.playerDead = true;
		check(helper, !late.deathScreenOpens(), "Fixture: the death screen opened although a soul was awaited");
		late.willSoulSpawn = false;
		late.state.stopWaiting();
		late.tick();
		check(helper, late.deathScreenOpens(), "The death screen did not open after the awaited soul failed to spawn");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void nextDeathAwaitsItsSoulAgain(GameTestHelper helper) {
		Client client = new Client();
		client.tick();
		client.soulSpawnsWithCamera();
		client.deathScreenOpens();
		client.tick();
		client.playerDead = true;
		client.deathScreenOpens();
		client.tick();
		client.soulEnds();
		check(helper, client.deathScreenOpens(), "Fixture: the first death's screen did not open");
		// respawned: a new local player, alive
		client.playerDead = false;
		client.tick();
		client.playerDead = true;
		check(helper, !client.deathScreenOpens(), "The next death's screen did not wait for its soul");
		client.tick();
		client.soulSpawnsWithCamera();
		client.tick();
		check(helper, client.state.followsSoul() && !client.state.awaitsSoul(),
				"The client does not follow the next death's soul");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void onlyServerEndsSoulOfLivingOrigin(GameTestHelper helper) {
		Cow origin = EntityType.COW.create(helper.getLevel());
		helper.assertTrue(origin != null, "Fixture: could not create the cow");
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
		origin.setNoAi(true);
		origin.setNoGravity(true);
		origin.moveTo(pos.x, pos.y, pos.z, 0, 0);
		helper.assertTrue(helper.getLevel().addFreshEntity(origin), "Fixture: could not add the cow");
		try {
			check(helper, SoulEntity.endedByOrigin(origin, 2, false),
					"The server keeps the soul of an origin that lives on");
			check(helper, !SoulEntity.endedByOrigin(origin, 1, false), "The server ends the soul in its first tick");
			check(helper, !SoulEntity.endedByOrigin(origin, 2, true) && !SoulEntity.endedByOrigin(origin, 50, true),
					"A client ends the soul of an origin it has not yet seen die; the server ends it and the client removes it");
			origin.setHealth(0.0F);
			check(helper, !SoulEntity.endedByOrigin(origin, 2, false) && !SoulEntity.endedByOrigin(origin, 2, true),
					"The soul of a dying origin ends");
			check(helper, SoulEntity.endedByOrigin(null, 0, true) && SoulEntity.endedByOrigin(null, 0, false),
					"A soul without an origin lives on");
			origin.discard();
			check(helper, SoulEntity.endedByOrigin(origin, 2, true) && SoulEntity.endedByOrigin(origin, 2, false),
					"The soul of a removed origin lives on");
		}
		finally {
			origin.discard();
		}
		helper.succeed();
	}

	private static void check(GameTestHelper helper, boolean ok, String message) {
		helper.assertTrue(ok, message);
	}

	// ControllerSoul's calls, with what the server and the client classes make of them
	private static final class Client {
		final SoulAscensionClientState state = new SoulAscensionClientState();
		boolean playerDead;
		boolean soulAlive;
		boolean cameraOnSoul;
		boolean willSoulSpawn = true;

		void soulSpawnsWithCamera() {
			soulAlive = true;
			cameraOnSoul = true;
		}

		// the server puts the camera back on the player and removes the soul
		void soulEnds() {
			soulAlive = false;
			cameraOnSoul = false;
		}

		// ClientTickEvent.Post: the client keeps the camera on a soul it follows, and puts it back on the player otherwise
		void tick() {
			state.tick(soulAlive && (cameraOnSoul || state.followsSoul()), playerDead, false);
			cameraOnSoul = state.followsSoul();
		}

		// Minecraft.setScreen with a DeathScreen, through ScreenEvent.Opening; the soul is known once a tick found it
		boolean deathScreenOpens() {
			return !state.holdsDeathScreen(state.followsSoul() && cameraOnSoul && soulAlive, willSoulSpawn);
		}
	}
}
