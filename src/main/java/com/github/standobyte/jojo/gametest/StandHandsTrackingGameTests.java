package com.github.standobyte.jojo.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import com.github.standobyte.jojo.api.stand.StandPowerTransitions;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.core.JojoRegistries;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.type.StandType;
import com.github.standobyte.jojo.subsystems.entity_externalcontainer.PlayerExternalContainers;
import com.github.standobyte.jojo.subsystems.entity_externalcontainer._stand.StandHandsContainerMenu;
import com.github.standobyte.jojo.subsystems.entity_externalcontainer.packet.ExternalContainerClosePacket;
import com.github.standobyte.jojo.subsystems.entity_externalcontainer.packet.ExternalContainerOpenPacket;
import com.github.standobyte.jojo.subsystems.entity_externalcontainer.packet.ExternalContainerSyncSetContentPacket;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandHandsTrackingGameTests {
	private StandHandsTrackingGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void pairingPrecedesMenuAndRetrackingPreservesHands(GameTestHelper helper) {
		FakePlayer user = new FakePlayer(helper.getLevel(),
				new GameProfile(UUID.randomUUID(), "StandHandsOwner"));
		FakePlayer observer = new FakePlayer(helper.getLevel(),
				new GameProfile(UUID.randomUUID(), "StandHandsObserver"));
		RecordingConnection connection = new RecordingConnection(user);
		PlayerExternalContainers containers = PlayerExternalContainers.get(user);
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		StandPower power = null;
		StandEntity unrelatedStand = null;
		try {
			helper.assertTrue(standType != null, "Missing Star Platinum Stand type");
			Vec3 position = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 3, 2)));
			user.moveTo(position.x, position.y, position.z);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add hands-menu owner");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			// Keep the owner unpaired until the explicit ServerEntity boundary below.
			connection.chunkSender.markChunkPendingToSend(helper.getLevel().getChunkAt(user.blockPosition()));
			connection.packets.clear();
			helper.assertTrue(standType.summon(user, power), "Could not summon Star Platinum");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Summoned Stand is missing");
			helper.assertTrue(containers.getAllContainers().isEmpty(),
					"Adding an untracked Stand opened its menu before owner pairing");
			ServerEntity tracker = new ServerEntity(helper.getLevel(), stand, 1, true, packet -> {});
			tracker.addPairing(user);
			StandHandsContainerMenu firstMenu = onlyMenu(helper, containers, stand);
			assertPairingBeforeMenu(helper, connection, stand, firstMenu);

			connection.packets.clear();
			tracker.addPairing(observer);
			helper.assertTrue(connection.packets.isEmpty(), "Observer tracking reopened the owner's menu");
			helper.assertTrue(PlayerExternalContainers.get(observer).getAllContainers().isEmpty(),
					"Observer tracking opened a hands menu for a non-owner");
			tracker.removePairing(observer);

			ItemStack mainHand = new ItemStack(Items.DIAMOND, 7);
			ItemStack offHand = new ItemStack(Items.EMERALD, 3);
			stand.setItemInHand(InteractionHand.MAIN_HAND, mainHand);
			stand.setItemInHand(InteractionHand.OFF_HAND, offHand);
			unrelatedStand = (StandEntity) stand.getType().create(helper.getLevel());
			helper.assertTrue(unrelatedStand != null, "Could not create unrelated hands-menu fixture");
			unrelatedStand.setUserAndPower(user, power);
			int unrelatedMenu = containers.openMenu(StandHandsContainerMenu.createServerSide(unrelatedStand), null)
					.orElseThrow();
			connection.packets.clear();
			tracker.removePairing(user);
			tracker.addPairing(user);
			StandHandsContainerMenu replacement = onlyMenu(helper, containers, stand);
			helper.assertTrue(replacement.containerId != firstMenu.containerId
					&& containers.getContainer(firstMenu.containerId) == null,
					"Retracking retained the menu bound to the old client entity");
			helper.assertTrue(containers.getContainer(unrelatedMenu) != null,
					"Retracking closed another Stand's menu");
			assertPairingBeforeMenu(helper, connection, stand, replacement);
			int closeIndex = connection.indexOf(packet -> packet instanceof ClientboundCustomPayloadPacket custom
					&& custom.payload() instanceof ExternalContainerClosePacket close
					&& close.containerId() == firstMenu.containerId);
			helper.assertTrue(closeIndex >= 0 && closeIndex < openIndex(connection, replacement.containerId),
					"Retracking did not close the old menu before opening its replacement");
			helper.assertTrue(stand.getMainHandItem() == mainHand && stand.getOffhandItem() == offHand
					&& mainHand.getCount() == 7 && offHand.getCount() == 3,
					"Replacing the hands menu changed the held item stacks");
			int contentIndex = connection.indexOf(packet -> packet instanceof ClientboundCustomPayloadPacket custom
					&& custom.payload() instanceof ExternalContainerSyncSetContentPacket content
					&& content.containerId() == replacement.containerId
					&& ItemStack.matches(content.items().get(0), mainHand)
					&& ItemStack.matches(content.items().get(1), offHand));
			helper.assertTrue(contentIndex > openIndex(connection, replacement.containerId),
					"Replacement menu did not sync both held items after opening");

			stand.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			stand.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			standType.forceUnsummon(user, power);
			containers.tick();
			helper.assertTrue(stand.isRemoved() && containers.getContainer(replacement.containerId) == null,
					"Unsummon did not invalidate the old hands menu");
			connection.packets.clear();
			helper.assertTrue(standType.summon(user, power), "Could not resummon Star Platinum");
			StandEntity resummoned = power.getSummonedStandEntity();
			helper.assertTrue(resummoned != null && resummoned != stand, "Resummon reused the old entity");
			new ServerEntity(helper.getLevel(), resummoned, 1, true, packet -> {}).addPairing(user);
			assertPairingBeforeMenu(helper, connection, resummoned, onlyMenu(helper, containers, resummoned));
			helper.succeed();
		}
		finally {
			if (power != null && power.getSummonedStandEntity() != null) {
				StandEntity stand = power.getSummonedStandEntity();
				stand.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				stand.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
				standType.forceUnsummon(user, power);
			}
			for (var menu : List.copyOf(containers.getAllContainers())) {
				containers.closeMenu(menu.containerId);
			}
			if (unrelatedStand != null) {
				unrelatedStand.discard();
			}
			observer.discard();
			user.discard();
		}
	}

	private static StandHandsContainerMenu onlyMenu(GameTestHelper helper,
			PlayerExternalContainers containers, StandEntity stand) {
		List<StandHandsContainerMenu> menus = containers.getAllContainers().stream()
				.filter(menu -> menu instanceof StandHandsContainerMenu hands && hands.standEntity == stand)
				.map(menu -> (StandHandsContainerMenu) menu).toList();
		helper.assertTrue(menus.size() == 1, "Expected exactly one hands menu for the tracked Stand");
		return menus.getFirst();
	}

	private static void assertPairingBeforeMenu(GameTestHelper helper, RecordingConnection connection,
			StandEntity stand, StandHandsContainerMenu menu) {
		int spawnIndex = connection.indexOf(packet -> packet instanceof ClientboundAddEntityPacket spawn
				&& spawn.getId() == stand.getId());
		int firstOpenIndex = connection.indexOf(packet -> packet instanceof ClientboundCustomPayloadPacket custom
				&& custom.payload() instanceof ExternalContainerOpenPacket);
		helper.assertTrue(spawnIndex >= 0 && firstOpenIndex > spawnIndex
				&& openIndex(connection, menu.containerId) >= firstOpenIndex,
				"Hands menu was sent before the Stand's spawn packet");
	}

	private static int openIndex(RecordingConnection connection, int containerId) {
		return connection.indexOf(packet -> packet instanceof ClientboundCustomPayloadPacket custom
				&& custom.payload() instanceof ExternalContainerOpenPacket open && open.containerId() == containerId);
	}

	private static final class RecordingConnection extends ServerGamePacketListenerImpl {
		final List<Packet<?>> packets = new ArrayList<>();

		RecordingConnection(FakePlayer player) {
			super(player.getServer(), player.connection.getConnection(), player,
					CommonListenerCookie.createInitial(player.getGameProfile(), false));
		}

		@Override
		public void send(Packet<?> packet) {
			if (packet instanceof ClientboundBundlePacket bundle) {
				bundle.subPackets().forEach(this::send);
			}
			else {
				packets.add(packet);
			}
		}

		@Override
		public void send(Packet<?> packet, PacketSendListener listener) {
			send(packet);
		}

		int indexOf(Predicate<Packet<?>> predicate) {
			for (int i = 0; i < packets.size(); i++) {
				if (predicate.test(packets.get(i))) {
					return i;
				}
			}
			return -1;
		}
	}
}
