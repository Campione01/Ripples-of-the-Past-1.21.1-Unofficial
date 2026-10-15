package rotp.core.gametest;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Optional;

import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.core.JojoMod;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.network.c2s.ClBroadcastedModSettingsPacket;

import io.netty.buffer.Unpooled;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Vanilla's GameTestHelper.makeMockPlayer answers isLocalPlayer() true in the server level. A player in a server
 * level reads its own stored settings, never the client's.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BroadcastedSettingsMockPlayerGameTests {
	private BroadcastedSettingsMockPlayerGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void serverLevelPlayerReadsServerStoredSettings(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		try {
			helper.assertTrue(player.isLocalPlayer() && !player.level().isClientSide(),
					"Fixture: the vanilla mock player no longer claims to be local in the server level");
			PlayerClientBroadcastedSettings stored = player.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get());
			stored.standAttackTargetLock = true;
			stored.noStandAbilityCooldown = true;
			Object read;
			try {
				read = PlayerClientBroadcastedSettings.getPlayerSettings(player);
			}
			catch (Throwable failure) {
				read = failure;
			}
			helper.assertTrue(read instanceof Optional<?> settings && settings.isPresent() && settings.get() == stored,
					"A server-level player's settings did not come from its server-stored data: " + read);
			helper.succeed();
		}
		finally {
			player.discard();
		}
	}

	// the owner's "aim assist" Stand setting (2026-10-15): on by default, also for a save from before it existed
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void aimAssistSettingTravelsAndDefaultsOn(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		try {
			PlayerClientBroadcastedSettings off = new PlayerClientBroadcastedSettings();
			helper.assertTrue(off.standAimAssist, "New settings have the aim assist off");
			off.standAimAssist = false;
			FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
			off.toBuf(buf);
			PlayerClientBroadcastedSettings sent = new PlayerClientBroadcastedSettings();
			sent.fromBuf(buf);
			helper.assertTrue(!sent.standAimAssist && buf.readableBytes() == 0,
					"The aim assist set off came back " + sent.standAimAssist + " through the buffer");
			helper.assertTrue(!off.copy().standAimAssist, "A copy of the settings turned the aim assist back on");
			HolderLookup.Provider registries = helper.getLevel().registryAccess();
			CompoundTag nbt = off.serializeNBT(registries);
			PlayerClientBroadcastedSettings loaded = new PlayerClientBroadcastedSettings();
			loaded.deserializeNBT(registries, nbt);
			helper.assertTrue(!loaded.standAimAssist, "The aim assist set off came back on from NBT");
			nbt.remove("StandAimAssist");
			loaded.deserializeNBT(registries, nbt);
			helper.assertTrue(loaded.standAimAssist, "A save without the aim assist key read it as off");

			PlayerClientBroadcastedSettings stored = player.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get());
			helper.assertTrue(PlayerClientBroadcastedSettings.isStandAimAssistEnabled(player),
					"A player's aim assist is off by default");
			stored.standAimAssist = false;
			helper.assertTrue(!PlayerClientBroadcastedSettings.isStandAimAssistEnabled(player),
					"The server did not read the player's aim assist setting off");
			Cow mob = EntityType.COW.create(helper.getLevel());
			helper.assertTrue(mob != null && PlayerClientBroadcastedSettings.isStandAimAssistEnabled(mob),
					"A mob Stand user does not always have the aim assist");
			helper.succeed();
		}
		finally {
			player.discard();
		}
	}

	// 1.16 ClientEventHandler.clientLoggedIn sent the client's settings on every login; the server keeps what it gets,
	// and a server that got nothing yet reads the aim assist on
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void serverStoresTheSettingsAClientSendsAndReadsTheAimAssistOnWithoutThem(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		try {
			helper.assertTrue(PlayerClientBroadcastedSettings.getServerStored(player).isEmpty(),
					"Fixture: a new player already has stored settings");
			helper.assertTrue(PlayerClientBroadcastedSettings.isStandAimAssistEnabled(player),
					"A server with no settings stored for the player read the aim assist off");

			PlayerClientBroadcastedSettings client = new PlayerClientBroadcastedSettings();
			client.standAimAssist = false;
			client.standAttackTargetLock = true;
			receive(helper, player, client);
			Optional<PlayerClientBroadcastedSettings> stored = PlayerClientBroadcastedSettings.getServerStored(player);
			helper.assertTrue(stored.isPresent() && !stored.get().standAimAssist && stored.get().standAttackTargetLock,
					"The server did not store the settings a client sent at login: "
							+ stored.map(s -> "aimAssist=" + s.standAimAssist + " targetLock=" + s.standAttackTargetLock));
			helper.assertTrue(!PlayerClientBroadcastedSettings.isStandAimAssistEnabled(player),
					"The server read the aim assist on after the client sent it off");

			client.standAimAssist = true;
			receive(helper, player, client);
			helper.assertTrue(PlayerClientBroadcastedSettings.isStandAimAssistEnabled(player),
					"The server kept the aim assist off after the client sent it on");
			helper.succeed();
		}
		finally {
			player.discard();
		}
	}

	// through the packet's codec and its server handler, as a client's packet arrives
	private static void receive(GameTestHelper helper, Player player, PlayerClientBroadcastedSettings sent) {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		ClBroadcastedModSettingsPacket.Handler.STREAM_CODEC.encode(buf, new ClBroadcastedModSettingsPacket(sent.copy()));
		ClBroadcastedModSettingsPacket received = ClBroadcastedModSettingsPacket.Handler.STREAM_CODEC.decode(buf);
		IPayloadContext context = (IPayloadContext) Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(),
				new Class<?>[] { IPayloadContext.class }, (proxy, method, args) -> {
					if (method.getName().equals("player")) {
						return player;
					}
					throw new UnsupportedOperationException("Fixture: the handler asked the context for " + method.getName());
				});
		// the Handler constructor sets the packet's static type; put back the registered one
		Object registeredType;
		Field typeField;
		try {
			typeField = ClBroadcastedModSettingsPacket.class.getDeclaredField("type");
			typeField.setAccessible(true);
			registeredType = typeField.get(null);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Fixture: cannot read the packet type", e);
		}
		try {
			new ClBroadcastedModSettingsPacket.Handler(JojoMod.resLoc("clbroadcastedsettings")).handle(received, context);
		}
		finally {
			try {
				typeField.set(null, registeredType);
			}
			catch (ReflectiveOperationException e) {
				throw new IllegalStateException("Fixture: cannot restore the packet type", e);
			}
		}
	}
}
