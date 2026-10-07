package rotp.core.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;
import rotp.core.config.client.ClientModSettings;
import rotp.core.core.JojoMod;
import rotp.core.network.s2c.TrAfkMenacingParticlePacket;
import rotp.core.subsystems.movement_input_sync.PlayerMovementInputData;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AfkMenacingParticleGameTests {
	private AfkMenacingParticleGameTests() {}

	private static final double EPS = 1.0E-6;

	// 1.16 GameplayEventHandler.onPlayerTick: every 60 ticks, visible, idle > 30 s, no held key > 30 s
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void afkMenacingParticleGate(GameTestHelper helper) {
		ServerPlayer player = FakePlayerFactory.get(helper.getLevel(),
				new GameProfile(UUID.randomUUID(), "AfkMenacingGate"));
		try {
			passTicks(PlayerMovementInputData.get(player), TrAfkMenacingParticlePacket.NO_INPUT_TICKS + 1);
			player.tickCount = 600;
			helper.assertTrue(TrAfkMenacingParticlePacket.tick(player, 31_000L) != null,
					"Idle visible player on a 60-tick boundary got no menacing particle");
			helper.assertTrue(TrAfkMenacingParticlePacket.tick(player, 30_000L) == null,
					"Menacing particle sent at exactly 30 s idle (1.16 needs more than 30 s)");
			helper.assertTrue(TrAfkMenacingParticlePacket.tick(player, 5_000L) == null,
					"Menacing particle sent to an active player");
			player.tickCount = 601;
			helper.assertTrue(TrAfkMenacingParticlePacket.tick(player, 31_000L) == null,
					"Menacing particle sent off the 60-tick boundary");
			player.tickCount = 660;
			player.setInvisible(true);
			helper.assertTrue(TrAfkMenacingParticlePacket.tick(player, 31_000L) == null,
					"Menacing particle sent over an invisible player");
			helper.succeed();
		}
		finally {
			player.discard();
		}
	}

	// 1.16 PlayerUtilCap.noClientInputTimer: a held movement, jump or sneak key blocks the particle
	// even when vanilla idle time grows (walking into a wall, holding sneak)
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void afkMenacingParticleNeedsNoHeldKey(GameTestHelper helper) {
		ServerPlayer player = FakePlayerFactory.get(helper.getLevel(),
				new GameProfile(UUID.randomUUID(), "AfkMenacingInput"));
		try {
			player.tickCount = 600;
			PlayerMovementInputData input = PlayerMovementInputData.get(player);
			helper.assertTrue(input != null, "Player has no synced movement input");
			input.setValues(0.0F, 1.0F, false, false, false);
			passTicks(input, 2000);
			helper.assertTrue(TrAfkMenacingParticlePacket.noInputTicks(input) == 0
					&& TrAfkMenacingParticlePacket.tick(player, 120_000L) == null,
					"Menacing particle sent while forward is held");
			input.setValues(0.0F, 0.0F, false, true, false);
			passTicks(input, 2000);
			helper.assertTrue(TrAfkMenacingParticlePacket.tick(player, 120_000L) == null,
					"Menacing particle sent while sneak is held");
			input.setValues(0.0F, 0.0F, true, false, false);
			passTicks(input, 2000);
			helper.assertTrue(TrAfkMenacingParticlePacket.tick(player, 120_000L) == null,
					"Menacing particle sent while jump is held");
			input.setValues(-1.0F, 0.0F, false, false, false);
			passTicks(input, 2000);
			helper.assertTrue(TrAfkMenacingParticlePacket.tick(player, 120_000L) == null,
					"Menacing particle sent while strafe is held");

			// released: counts from the release, more than 30 s needed
			input.setValues(0.0F, 0.0F, false, false, false);
			passTicks(input, TrAfkMenacingParticlePacket.NO_INPUT_TICKS);
			helper.assertTrue(TrAfkMenacingParticlePacket.noInputTicks(input) == TrAfkMenacingParticlePacket.NO_INPUT_TICKS
					&& TrAfkMenacingParticlePacket.tick(player, 120_000L) == null,
					"Menacing particle sent at exactly 30 s without a held key (1.16 needs more than 30 s)");
			passTicks(input, 1);
			helper.assertTrue(TrAfkMenacingParticlePacket.tick(player, 120_000L) != null,
					"No menacing particle after more than 30 s without a held key");
			// sprint alone was not input in 1.16
			input.setValues(0.0F, 0.0F, false, false, true);
			helper.assertTrue(TrAfkMenacingParticlePacket.tick(player, 120_000L) != null,
					"Sprint key alone blocked the menacing particle");
			// a short tap restarts the count
			input.setValues(0.0F, 0.3F, false, false, true);
			input.setValues(0.0F, 0.0F, false, false, true);
			helper.assertTrue(TrAfkMenacingParticlePacket.noInputTicks(input) == 0
					&& TrAfkMenacingParticlePacket.tick(player, 120_000L) == null,
					"A key tap did not restart the no-input count");
			helper.succeed();
		}
		finally {
			player.discard();
		}
	}

	private static void passTicks(PlayerMovementInputData input, int ticks) {
		for (int i = 0; i < ticks; i++) {
			input.tickTimers();
		}
	}

	// 1.16: MENACING at eye height, direction (cos yRot, 0.5, sin yRot) * 0.005, survives the wire
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void afkMenacingParticleAtEyesAlongYaw(GameTestHelper helper) {
		ServerPlayer player = FakePlayerFactory.get(helper.getLevel(),
				new GameProfile(UUID.randomUUID(), "AfkMenacingPos"));
		try {
			player.moveTo(10.5, 64.0, -3.25, 90.0F, 0.0F);
			TrAfkMenacingParticlePacket side = TrAfkMenacingParticlePacket.at(player);
			helper.assertTrue(Math.abs(side.x() - 10.5) < EPS && Math.abs(side.z() + 3.25) < EPS
					&& Math.abs(side.y() - player.getEyeY()) < EPS && side.y() > 64.0 + 1.0,
					"Menacing particle not at eye height: " + side);
			helper.assertTrue(Math.abs(side.xSpeed()) < 1.0E-4 && Math.abs(side.zSpeed() - 0.005F) < 1.0E-4
					&& Math.abs(side.ySpeed() - 0.0025F) < EPS,
					"Yaw 90 menacing drift should be (0, 0.0025, 0.005): " + side);
			player.moveTo(10.5, 64.0, -3.25, 0.0F, 0.0F);
			TrAfkMenacingParticlePacket front = TrAfkMenacingParticlePacket.at(player);
			helper.assertTrue(Math.abs(front.xSpeed() - 0.005F) < 1.0E-4 && Math.abs(front.zSpeed()) < 1.0E-4,
					"Yaw 0 menacing drift should be (0.005, 0.0025, 0): " + front);

			TrAfkMenacingParticlePacket.Handler handler = new TrAfkMenacingParticlePacket.Handler(JojoMod.resLoc("trafkmenacing"));
			RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
			try {
				handler.encode(side, buf);
				TrAfkMenacingParticlePacket decoded = handler.decode(buf);
				helper.assertTrue(side.equals(decoded) && buf.readableBytes() == 0,
						"Menacing packet round trip changed it: " + side + " -> " + decoded);
				helper.assertTrue(player.getUUID().equals(side.owner()) && player.getUUID().equals(decoded.owner()),
						"Menacing packet lost the idle player as its owner: sent " + side.owner() + ", read " + decoded.owner());

				TrAfkMenacingParticlePacket unknown = new TrAfkMenacingParticlePacket(1.5, 70.25, -8.0, 0.001F, 0.002F, 0.003F);
				helper.assertTrue(unknown.owner() == null, "The six-number constructor should leave the owner unknown");
				handler.encode(unknown, buf);
				TrAfkMenacingParticlePacket decodedUnknown = handler.decode(buf);
				helper.assertTrue(unknown.equals(decodedUnknown) && decodedUnknown.owner() == null && buf.readableBytes() == 0,
						"Ownerless menacing packet round trip changed it: " + unknown + " -> " + decodedUnknown);
			}
			finally {
				buf.release();
			}
			helper.succeed();
		}
		finally {
			player.discard();
		}
	}

	// 1.16 SpawnParticlePacket.handle: AFK particles are dropped when menacingParticles is off
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void afkMenacingParticleFollowsClientSetting(GameTestHelper helper) {
		ClientModSettings.Settings settings = new ClientModSettings.Settings();
		helper.assertTrue(settings.menacingParticles && TrAfkMenacingParticlePacket.shown(settings),
				"Menacing particles hidden with the default client setting");
		settings.menacingParticles = false;
		helper.assertFalse(TrAfkMenacingParticlePacket.shown(settings),
				"Menacing particles shown with the client setting off");
		// the first-person option never brings glyphs back while the master setting is off
		settings.ownAfkMenacingFirstPerson = true;
		helper.assertFalse(TrAfkMenacingParticlePacket.shown(settings),
				"Own first-person option overrode the master menacing setting");
		settings.ownAfkMenacingFirstPerson = false;
		helper.assertFalse(TrAfkMenacingParticlePacket.shown(settings),
				"Menacing particles shown with both client settings off");
		settings.menacingParticles = true;
		helper.assertTrue(TrAfkMenacingParticlePacket.shown(settings),
				"Own first-person option stopped the glyphs of every player from spawning");
		helper.succeed();
	}

	// owner decision 2026-10-07: a client option hides only the local player's own AFK glyphs, only in its own first person
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void afkMenacingParticleOwnFirstPersonOption(GameTestHelper helper) {
		UUID self = UUID.randomUUID();
		UUID other = UUID.randomUUID();
		ClientModSettings.Settings settings = new ClientModSettings.Settings();
		helper.assertTrue(settings.ownAfkMenacingFirstPerson,
				"Own AFK glyphs in first person should be on by default (the 1.16 look)");
		helper.assertFalse(TrAfkMenacingParticlePacket.hiddenInOwnFirstPerson(settings, self, self, self, true, false),
				"Own AFK glyph hidden in first person with the option on");

		settings.ownAfkMenacingFirstPerson = false;
		helper.assertTrue(TrAfkMenacingParticlePacket.hiddenInOwnFirstPerson(settings, self, self, self, true, false),
				"Own AFK glyph still drawn in own first person with the option off");
		helper.assertFalse(TrAfkMenacingParticlePacket.hiddenInOwnFirstPerson(settings, other, self, self, true, false),
				"Another player's AFK glyph hidden by the own first-person option");
		helper.assertFalse(TrAfkMenacingParticlePacket.hiddenInOwnFirstPerson(settings, null, self, self, true, false),
				"A glyph of unknown owner (command, block, entity emitter) hidden by the own first-person option");
		helper.assertFalse(TrAfkMenacingParticlePacket.hiddenInOwnFirstPerson(settings, self, self, self, false, false),
				"Own AFK glyph hidden in third person");
		helper.assertFalse(TrAfkMenacingParticlePacket.hiddenInOwnFirstPerson(settings, self, self, self, false, true),
				"Own AFK glyph hidden in a detached third-person camera");
		helper.assertFalse(TrAfkMenacingParticlePacket.hiddenInOwnFirstPerson(settings, self, self, self, true, true),
				"Own AFK glyph hidden under a detached camera");
		helper.assertFalse(TrAfkMenacingParticlePacket.hiddenInOwnFirstPerson(settings, self, self, other, true, false),
				"Own AFK glyph hidden while the camera sits in another entity");
		helper.assertFalse(TrAfkMenacingParticlePacket.hiddenInOwnFirstPerson(settings, self, self, null, true, false),
				"Own AFK glyph hidden without a camera entity");
		helper.assertFalse(TrAfkMenacingParticlePacket.hiddenInOwnFirstPerson(settings, other, self, other, true, false),
				"A spectated player's AFK glyph hidden by the spectator's own first-person option");
		helper.assertFalse(TrAfkMenacingParticlePacket.hiddenInOwnFirstPerson(settings, self, null, self, true, false),
				"AFK glyph hidden without a local player");

		// independent of the master setting: master off drops the packet earlier, this rule does not change
		settings.menacingParticles = false;
		helper.assertTrue(TrAfkMenacingParticlePacket.hiddenInOwnFirstPerson(settings, self, self, self, true, false)
				&& !TrAfkMenacingParticlePacket.shown(settings),
				"Master off should show nothing whatever the own first-person option says");
		helper.succeed();
	}
}
