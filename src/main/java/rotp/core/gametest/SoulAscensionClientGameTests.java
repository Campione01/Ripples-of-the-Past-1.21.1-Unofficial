package rotp.core.gametest;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;

import rotp.core.core.JojoMod;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.power.ModStands;
import rotp.core.network.c2s.ClSoulRotationPacket;
import rotp.core.network.s2c.SoulSpawnPacket;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.soul.SoulAscensionClientState;
import rotp.core.subsystems.soul.SoulEntity;
import rotp.core.subsystems.timestop.TimeStopState;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 1.16 ControllerSoul followed a dying Stand user's soul with the camera from its spawn, held the vanilla death screen back
 * while the soul ascended or was awaited, and let it open once the soul was gone (its respawn() call sat behind a condition
 * that never held). The server sends the soul, the camera on it and the kill in the tick the player dies, the player's
 * health of 0 only with its next tick; a client that dropped the soul, or started a new life, in the ticks between stayed
 * dead without a screen.
 * <p>
 * ControllerSoul is a client class the gametest server cannot load: Client below makes its calls into the shared
 * SoulAscensionClientState, so a change to that wiring is not seen here.
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

	/**
	 * The jump skips the soul in a client tick between the kill and the health of 0, and the server ends the soul before
	 * that health reaches the client. Such a tick used to start a new life on the client: the next death screen waited
	 * again, for a soul that was gone, and held every death screen back.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void soulSkippedBeforeTheHealthOfZeroOpensVanillaDeathScreen(GameTestHelper helper) {
		for (boolean held : new boolean[] { false, true }) {
			// client ticks between the soul's end and the health of 0
			for (int ticksBeforeHealth = 0; ticksBeforeHealth <= 2; ticksBeforeHealth++) {
				String run = " (jump " + (held ? "held" : "pressed once") + ", " + ticksBeforeHealth
						+ " client ticks between the soul's end and the health of 0)";
				Client client = new Client();
				client.tick();
				client.soulSpawnsWithCamera();
				check(helper, !client.deathScreenOpens(), "Fixture: the kill's death screen opened although the soul spawned" + run);
				client.jumpDown = true;
				client.tick();
				check(helper, client.soulSkipped, "Fixture: the jump did not skip the soul" + run);
				client.jumpDown = held;
				client.soulEnds();
				for (int i = 0; i < ticksBeforeHealth; i++) {
					client.tick();
				}
				client.playerDead = true;
				check(helper, client.deathScreenOpens(), "The death screen was held back for a soul the player skipped before"
						+ " the client saw it die; 1.16 opened the vanilla death screen" + run);
				client.tick();
				check(helper, !client.state.awaitsSoul() && !client.state.showsSkipPrompt(true),
						"The client waits for, or prompts to skip, a soul that is gone" + run);
			}
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void heldJumpSkipsLikeAPress(GameTestHelper helper) {
		SoulAscensionClientState state = new SoulAscensionClientState();
		// 1.16 InputHandler called skipAscension in every tick the jump key was down
		boolean first = state.skipsAscension(true, true, false);
		boolean second = state.skipsAscension(true, true, false);
		check(helper, first && second, "A jump held on the soul skips in its first tick " + first + " and its second " + second
				+ "; 1.16 read the key every tick");
		check(helper, !state.skipsAscension(true, false, false) && !state.skipsAscension(true, true, true)
				&& !state.skipsAscension(false, true, false),
				"The ascension is skipped without the jump, behind a screen, or with neither a soul nor a wait for one");

		// a jump held since before the death ends the wait for the soul in its first tick
		Client awaiting = new Client();
		awaiting.jumpDown = true;
		awaiting.tick();
		awaiting.playerDead = true;
		check(helper, !awaiting.deathScreenOpens(), "Fixture: the death screen opened although a soul was awaited");
		awaiting.tick();
		check(helper, !awaiting.state.awaitsSoul() && awaiting.deathScreenOpens(),
				"A held jump did not end the wait for the soul; 1.16 ended it in the next tick");
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
		ascending.jumpDown = true;
		ascending.tick();
		check(helper, ascending.soulSkipped, "Fixture: the jump did not skip the soul");
		// the server ends the soul on its next tick
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
		awaiting.jumpDown = true;
		awaiting.tick();
		check(helper, awaiting.deathScreenOpens(), "The vanilla death screen did not open after the awaited soul was skipped");
		helper.succeed();
	}

	/** 1.16 took the skip prompt off the action bar in the tick the ascension was skipped. */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void skipTakesThePromptAtOnce(GameTestHelper helper) {
		Client client = new Client();
		client.tick();
		client.soulSpawnsWithCamera();
		client.deathScreenOpens();
		client.playerDead = true;
		client.deathScreenOpens();
		client.tick();
		check(helper, client.state.showsSkipPrompt(true), "Fixture: no skip prompt while the soul ascends");
		client.jumpDown = true;
		client.tick();
		// the client has the soul until the server ends it
		check(helper, client.soulSkipped && client.state.followsSoul() && !client.state.showsSkipPrompt(true),
				"The skip prompt stayed on the soul the player skipped; 1.16 took it off at once");
		client.jumpDown = false;
		client.tick();
		check(helper, !client.state.showsSkipPrompt(true), "The skip prompt came back on the skipped soul");
		client.soulEnds();
		client.tick();
		check(helper, !client.state.showsSkipPrompt(true), "The skip prompt shows without a soul");

		Client awaiting = new Client();
		awaiting.tick();
		awaiting.playerDead = true;
		awaiting.deathScreenOpens();
		awaiting.tick();
		check(helper, awaiting.state.showsSkipPrompt(true), "Fixture: the awaited soul shows no skip prompt");
		awaiting.jumpDown = true;
		awaiting.tick();
		check(helper, !awaiting.state.showsSkipPrompt(true), "The skip prompt stayed after the wait for the soul was skipped");
		helper.succeed();
	}

	/**
	 * 1.16 held a dead player's death time short of 20 in every tick of the death (LocalPlayer.tickDeath removes the body at
	 * 20), behind the death screen too; the soul only holds it while it ascends.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void deadBodyStaysForTheWholeDeath(GameTestHelper helper) {
		Client client = new Client();
		client.tick();
		client.soulSpawnsWithCamera();
		client.deathScreenOpens();
		client.playerDead = true;
		for (int i = 0; i < 60; i++) {
			client.deathScreenOpens();
			client.tick();
		}
		client.soulEnds();
		check(helper, client.deathScreenOpens(), "Fixture: the death screen did not open after the soul");
		// the death screen is open: no other opens
		for (int i = 1; i <= ASCENSION_TICKS; i++) {
			client.tick();
			check(helper, !client.bodyRemoved, "The dead player's body was removed behind the death screen " + i
					+ " ticks after the soul; 1.16 kept it for the whole death");
		}

		Client noStand = new Client();
		noStand.willSoulSpawn = false;
		noStand.tick();
		noStand.playerDead = true;
		check(helper, noStand.deathScreenOpens(), "Fixture: the death screen of a player without a soul did not open");
		for (int i = 1; i <= ASCENSION_TICKS; i++) {
			noStand.tick();
			check(helper, !noStand.bodyRemoved, "The body of a player who died without a soul was removed after " + i
					+ " ticks; 1.16 kept it for the whole death");
		}
		check(helper, new SoulAscensionClientState().keptDeathTime(false, 25) == 25,
				"The death time of a living player was changed");
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
		check(helper, client.state.followsSoul() && !client.state.awaitsSoul() && client.state.showsSkipPrompt(true),
				"The client does not follow the next death's soul");

		// revived while its soul ascends: the server ends the soul, and the next death waits for its own
		Client revived = new Client();
		revived.tick();
		revived.soulSpawnsWithCamera();
		revived.deathScreenOpens();
		revived.playerDead = true;
		revived.deathScreenOpens();
		revived.tick();
		revived.playerDead = false;
		revived.tick();
		revived.soulEnds();
		revived.tick();
		revived.tick();
		revived.playerDead = true;
		check(helper, !revived.deathScreenOpens(), "The death after a revival did not wait for its soul");

		// revived in the tick of its death (Gold Experience): the client never sees the health of 0, and the server ends
		// the soul, which was not skipped, because its player lives on
		Client revivedAtOnce = new Client();
		revivedAtOnce.tick();
		revivedAtOnce.soulSpawnsWithCamera();
		revivedAtOnce.deathScreenOpens();
		revivedAtOnce.tick();
		revivedAtOnce.soulEnds();
		revivedAtOnce.tick();
		check(helper, !revivedAtOnce.state.awaitsSoul(), "The client of a player revived in the tick of its death still awaits a soul");
		revivedAtOnce.tick();
		check(helper, !revivedAtOnce.deathScreenOpens(),
				"The kill's death screen of the death after a revival in the tick of the last one did not wait for its soul");
		helper.succeed();
	}

	/**
	 * The soul comes with the kill and is gone before the client ticks again: a client stalled for the soul's whole life,
	 * or a player revived in the tick of the death on a client that runs several ticks a frame. 1.16 kept the soul from
	 * its spawn data, so its wait ended all the same.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void soulGoneBeforeTheNextClientTickEndsTheWait(GameTestHelper helper) {
		Client stalled = new Client();
		stalled.tick();
		stalled.soulSpawnsWithCamera();
		check(helper, !stalled.deathScreenOpens(), "Fixture: the kill's death screen opened although the soul spawned");
		stalled.playerDead = true;
		stalled.soulEnds();
		stalled.deathScreenOpens();
		stalled.tick();
		check(helper, stalled.deathScreenOpens(), "The death screen was held back for a soul that came and went between two"
				+ " client ticks; 1.16 opened the vanilla death screen once it was gone");
		stalled.tick();
		check(helper, !stalled.state.awaitsSoul() && !stalled.state.showsSkipPrompt(true),
				"The stalled client still waits for, or prompts to skip, a soul that is gone");

		Client revived = new Client();
		revived.tick();
		revived.soulSpawnsWithCamera();
		check(helper, !revived.deathScreenOpens(), "Fixture: the kill's death screen opened although the soul spawned");
		revived.soulEnds();
		revived.tick();
		revived.tick();
		check(helper, !revived.state.awaitsSoul() && !revived.state.showsSkipPrompt(false),
				"The client of a player revived in the tick of its death, whose soul it never ticked with, still awaits it");
		revived.playerDead = true;
		check(helper, !revived.deathScreenOpens(), "The death after that revival did not wait for its soul");
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

	/** 1.16 registered the soul player-sized: the camera of a dead player rode it at a standing eye height. */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void soulIsPlayerSizedAsIn116(GameTestHelper helper) {
		EntityDimensions size = ModEntityTypes.SOUL.get().getDimensions();
		check(helper, size.width() == 0.6F && size.height() == 1.8F && Math.abs(size.eyeHeight() - 1.53F) < 1.0E-4F,
				"The soul is " + size.width() + " x " + size.height() + " with its eyes at " + size.eyeHeight()
						+ "; 1.16 made it 0.6 x 1.8 with the eyes at 1.53, where the dead player's camera sits");
		helper.succeed();
	}

	/**
	 * 1.16 SoulEntity gave the soul of the client's own player that player's look every tick: the mouse turns the player,
	 * the camera on the soul shows the soul's look, and the client sends it to the server, which gives Resolve to the Stand
	 * user of the entity the soul looks at. The client half runs only in a client; here its decision, and the server half.
	 * The scene sits above the barriers that encase the test, in a batch of its own, so that no block stops the soul's look.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = "soul_look")
	public static void soulLooksWhereItsPlayerLooks(GameTestHelper helper) {
		Player localPlayer = helper.makeMockPlayer(GameType.SURVIVAL);
		Player serverPlayer = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Player other = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Cow east = cow(helper, new BlockPos(3, 4, 1));
		Cow south = cow(helper, new BlockPos(1, 4, 3));
		SoulEntity soul = null;
		try {
			check(helper, SoulEntity.looksWithOrigin(true, localPlayer),
					"The soul of the client's own player does not take the player's look; 1.16 gave it the look every tick");
			check(helper, !SoulEntity.looksWithOrigin(false, localPlayer) && !SoulEntity.looksWithOrigin(true, serverPlayer)
					&& !SoulEntity.looksWithOrigin(true, east) && !SoulEntity.looksWithOrigin(true, null),
					"A soul takes the look of an origin that is not the client's own player, or on the server");

			Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 4, 1)));
			serverPlayer.moveTo(pos.x, pos.y, pos.z, 0, 0);
			serverPlayer.setHealth(0.0F);
			soul = new SoulEntity(helper.getLevel(), serverPlayer, ASCENSION_TICKS);
			helper.assertTrue(helper.getLevel().addFreshEntity(soul), "Fixture: could not add the soul");
			ClSoulRotationPacket.Handler handler = new ClSoulRotationPacket.Handler(JojoMod.resLoc("clsoulrot"));
			// a yaw of -90 looks to +x, 0 to +z
			handler.handle(new ClSoulRotationPacket(soul.getId(), -90.0F, 0.0F), from(serverPlayer));
			check(helper, soul.lookTarget() == east, "The soul whose player looks east looks at " + looking(soul, east));
			handler.handle(new ClSoulRotationPacket(soul.getId(), 0.0F, 0.0F), from(serverPlayer));
			check(helper, soul.lookTarget() == south, "The soul whose player looks south looks at " + looking(soul, south));
			handler.handle(new ClSoulRotationPacket(soul.getId(), -90.0F, 0.0F), from(other));
			check(helper, soul.lookTarget() == south, "Another player's look turned the soul: " + looking(soul, south));
		}
		finally {
			if (soul != null) {
				soul.discard();
			}
			east.discard();
			south.discard();
			localPlayer.discard();
			serverPlayer.discard();
			other.discard();
		}
		helper.succeed();
	}

	/**
	 * A soul of the user still ascends when the user dies, at once or when a time stop that deferred the new soul ends:
	 * no second soul spawns, and the client, which holds its death screen back for this death's soul, hears that none
	 * comes, as from a failed spawn. It used to hear nothing and wait until the player gave up with a jump.
	 */
	@GameTest(template = "empty", timeoutTicks = 40, batch = GameTestBatches.TIME_STOP)
	public static void soulAlreadyAscendingTellsTheClientNoneComes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CapturingPlayer player = new CapturingPlayer(level);
		StandPower power = PowerClass.STAND.attachGet(player);
		TimeStopState timeStops = level.getData(ModDataAttachmentTypes.TIME_STOP.get());
		int stop = -7_771_000 - player.getId();
		List<SoulEntity> souls = new ArrayList<>();
		try {
			Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			player.moveTo(pos.x, pos.y, pos.z, 0, 0);
			player.setGameMode(GameType.SURVIVAL);
			power.setStand(ModStands.STAR_PLATINUM.get());
			power.setResolveLevel(1);
			// the living user's client hears that its soul will spawn
			power.syncToPlayer(player);
			helper.assertTrue(power.canSpawnSoulOnDeath() && power.willSoulSpawn(), "Fixture: the Stand user's soul cannot spawn");
			player.setHealth(0.0F);

			souls.add(soulOf(helper, player));
			player.failedSpawnPackets.clear();
			power.spawnSoulOnDeath();
			check(helper, ownSouls(helper, player) == 1, "A second soul of the user spawned");
			check(helper, player.failedSpawnPackets.equals(List.of(true)) && !power.willSoulSpawn(),
					"With a soul of the user still ascending the client heard " + player.failedSpawnPackets
							+ " and the server's soul flag is " + power.willSoulSpawn()
							+ "; it must hear once that no soul comes, and the server keep the flag the client now has, false,"
							+ " so that the user's next life sends it again");

			souls.get(0).discard();
			player.failedSpawnPackets.clear();
			helper.assertTrue(timeStops.tryPutInstance(new TimeStopState.Instance(stop, 200, 200,
					new ChunkPos(player.blockPosition()), 2, -1, "soul_ascension_client_test")), "Fixture: could not stop time");
			timeStops.reconcileFrozenEntity(player);
			helper.assertTrue(timeStops.shouldFreeze(player), "Fixture: the dead Stand user is not stopped in time");
			power.spawnSoulOnDeath();
			helper.assertTrue(ownSouls(helper, player) == 0 && player.failedSpawnPackets.isEmpty(),
					"Fixture: the soul did not wait for time to resume");
			// a soul of the user came while time was stopped
			souls.add(soulOf(helper, player));
			// time resumes for the user: what waited on it runs
			timeStops.unfreezeEntity(player);
			check(helper, ownSouls(helper, player) == 1, "A second soul of the user spawned when time resumed");
			check(helper, player.failedSpawnPackets.equals(List.of(true)),
					"When time resumed with a soul of the user still ascending the client heard " + player.failedSpawnPackets
							+ "; it must hear once that no soul comes");
		}
		finally {
			timeStops.removeInstance(stop);
			for (SoulEntity soul : souls) {
				soul.discard();
			}
			power.setStand(null);
			player.getAdvancements().stopListening();
			player.discard();
			player.channel.finishAndReleaseAll();
		}
		helper.succeed();
	}

	private static void check(GameTestHelper helper, boolean ok, String message) {
		helper.assertTrue(ok, message);
	}

	private static Cow cow(GameTestHelper helper, BlockPos at) {
		Cow cow = EntityType.COW.create(helper.getLevel());
		helper.assertTrue(cow != null, "Fixture: could not create the cow");
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(at));
		cow.setNoAi(true);
		cow.setNoGravity(true);
		cow.moveTo(pos.x, pos.y, pos.z, 0, 0);
		helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Fixture: could not add the cow");
		return cow;
	}

	private static String looking(SoulEntity soul, Entity expected) {
		Vec3 start = soul.position().add(0.0D, soul.getBbHeight() * 0.5D, 0.0D);
		BlockHitResult block = soul.level().clip(new ClipContext(start, start.add(soul.getLookAngle().scale(32.0D)),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
		return soul.lookTarget() + " (soul at " + soul.position() + " with yaw " + soul.getYRot() + " and pitch "
				+ soul.getXRot() + ", " + expected + " in " + expected.getBoundingBox() + ", first block " + block.getType()
				+ " at " + block.getBlockPos() + ": " + soul.level().getBlockState(block.getBlockPos()) + ")";
	}

	private static IPayloadContext from(Player player) {
		return (IPayloadContext) Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(),
				new Class<?>[] { IPayloadContext.class }, (proxy, method, args) -> {
					if (method.getName().equals("player")) {
						return player;
					}
					throw new UnsupportedOperationException(method.getName());
				});
	}

	private static SoulEntity soulOf(GameTestHelper helper, Player player) {
		SoulEntity soul = new SoulEntity(helper.getLevel(), player, ASCENSION_TICKS);
		helper.assertTrue(helper.getLevel().addFreshEntity(soul), "Fixture: could not add the soul");
		return soul;
	}

	private static int ownSouls(GameTestHelper helper, Entity origin) {
		return helper.getLevel().getEntitiesOfClass(SoulEntity.class, origin.getBoundingBox().inflate(16.0D),
				soul -> soul.isAlive() && soul.getOriginEntity() == origin).size();
	}

	// ControllerSoul's calls, with what the server and the client classes make of them
	private static final class Client {
		final SoulAscensionClientState state = new SoulAscensionClientState();
		boolean playerDead;
		boolean soulAlive;
		boolean cameraOnSoul;
		boolean willSoulSpawn = true;
		boolean jumpDown;
		boolean soulSkipped;
		int deathTime;
		boolean bodyRemoved;

		void soulSpawnsWithCamera() {
			soulAlive = true;
			cameraOnSoul = true;
			soulSkipped = false;
		}

		// the server puts the camera back on the player and removes the soul
		void soulEnds() {
			soulAlive = false;
			cameraOnSoul = false;
		}

		// Minecraft.tick: the level (LocalPlayer.tickDeath, SoulEntity.tick holding its origin's death time), then
		// ClientTickEvent.Post: the client keeps the camera on a soul it follows and puts it back on the player otherwise
		void tick() {
			if (playerDead && !bodyRemoved && ++deathTime == 20) {
				bodyRemoved = true;
			}
			if (soulAlive) {
				deathTime = Math.min(deathTime, 18);
			}
			state.tick(soulAlive && (cameraOnSoul || state.followsSoul()), playerDead, false);
			cameraOnSoul = state.followsSoul();
			boolean soulCamera = cameraOnSoul && soulAlive;
			if (state.skipsAscension(soulCamera, jumpDown, false)) {
				if (soulCamera) {
					soulSkipped = true;
				}
				state.skip();
			}
			deathTime = state.keptDeathTime(playerDead, deathTime);
		}

		// Minecraft.setScreen with a DeathScreen, through ScreenEvent.Opening
		boolean deathScreenOpens() {
			return !state.holdsDeathScreen(cameraOnSoul && soulAlive, willSoulSpawn);
		}
	}

	// a server player whose connection keeps the failed-spawn flag of every SoulSpawnPacket sent to it
	private static final class CapturingPlayer extends FakePlayer {
		final List<Boolean> failedSpawnPackets = new ArrayList<>();
		final EmbeddedChannel channel;

		CapturingPlayer(ServerLevel level) {
			super(level, new GameProfile(UUID.randomUUID(), "soul-spawn-test"));
			Connection network = new Connection(PacketFlow.SERVERBOUND);
			channel = new EmbeddedChannel(network);
			connection = new RecordingConnection(level, this, network);
		}
	}

	private static final class RecordingConnection extends ServerGamePacketListenerImpl {
		private final List<Boolean> sink;

		RecordingConnection(ServerLevel level, CapturingPlayer player, Connection network) {
			super(level.getServer(), network, player, CommonListenerCookie.createInitial(player.getGameProfile(), false));
			sink = player.failedSpawnPackets;
		}

		@Override
		public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
			if (sink != null && packet instanceof ClientboundCustomPayloadPacket custom
					&& custom.payload() instanceof SoulSpawnPacket soulSpawn) {
				sink.add(soulSpawn.failedSpawnPacket());
			}
		}
	}
}
