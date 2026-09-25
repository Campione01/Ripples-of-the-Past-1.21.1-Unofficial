package rotp.core.subsystems.entity_puppetcontrol;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import rotp.core.PacketsRegister;
import rotp.core.core.JojoMod;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The user's Shift for Stand moves. 1.16 kept the user's sneak under manual
 * control; the port clears it (ClientEntityController.clearInput), so the
 * controlling client sends the key on its own.
 */
@EventBusSubscriber(modid = JojoMod.MOD_ID)
public final class StandManualInput {
	private static final Set<UUID> SERVER_SHIFT = ConcurrentHashMap.newKeySet();
	private static volatile boolean clientShift;

	private StandManualInput() {}

	/**
	 * @return the user's sneak, or the sent Shift key while the user's
	 *         summoned Stand is manually controlled
	 */
	public static boolean isShiftHeld(@Nullable LivingEntity user) {
		if (user == null) {
			return false;
		}
		StandPower power = StandPower.get(user);
		return isShiftHeld(user, power != null ? power.getSummonedStandEntity() : null);
	}

	public static boolean isShiftHeld(@Nullable LivingEntity user, @Nullable StandEntity stand) {
		if (user == null) {
			return false;
		}
		if (stand == null || !stand.isManuallyControlled()) {
			return user.isShiftKeyDown();
		}
		if (user.level().isClientSide()) {
			return clientShift && user instanceof Player player && player.isLocalPlayer();
		}
		return SERVER_SHIFT.contains(user.getUUID());
	}

	public static void setServerShift(Player player, boolean held) {
		if (held) {
			SERVER_SHIFT.add(player.getUUID());
		}
		else {
			SERVER_SHIFT.remove(player.getUUID());
		}
	}

	public static void clear(Player player) {
		SERVER_SHIFT.remove(player.getUUID());
	}

	public static void setClientShift(boolean held) {
		clientShift = held;
	}

	@SubscribeEvent
	public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
		clear(event.getEntity());
	}

	public record ShiftPacket(boolean held) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<ShiftPacket> TYPE =
				new CustomPacketPayload.Type<>(JojoMod.resLoc("clstandshift"));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}

		public static class Handler implements PacketsRegister.PacketCodecHandler<ShiftPacket> {
			public static final StreamCodec<RegistryFriendlyByteBuf, ShiftPacket> STREAM_CODEC =
					StreamCodec.composite(ByteBufCodecs.BOOL, ShiftPacket::held, ShiftPacket::new);

			@Override
			public Type<ShiftPacket> type() {
				return TYPE;
			}

			@Override
			public StreamCodec<? super RegistryFriendlyByteBuf, ShiftPacket> reader() {
				return STREAM_CODEC;
			}

			@Override
			public void handle(ShiftPacket packet, IPayloadContext context) {
				setServerShift(context.player(), packet.held());
			}
		}
	}
}
