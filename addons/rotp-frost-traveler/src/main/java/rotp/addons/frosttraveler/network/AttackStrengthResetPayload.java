package rotp.addons.frosttraveler.network;

import rotp.addons.frosttraveler.FrostTravelerAddon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 1.16 cs_def (DeformTargetAction, swingHand() without withUserPunch): when
 * the charged spray went off, Action.onPerform reset the attack strength on
 * the server and, through the held-action stop packet, on the user's client
 * too. The port runs cs_def on the server only, so the server tells the
 * user's client to restart its attack indicator.
 */
public record AttackStrengthResetPayload() implements CustomPacketPayload {
    public static final String NETWORK_PROTOCOL_VERSION = "1";
    public static final AttackStrengthResetPayload INSTANCE =
            new AttackStrengthResetPayload();
    public static final Type<AttackStrengthResetPayload> TYPE =
            new Type<>(FrostTravelerAddon.id("attack_strength_reset"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf, AttackStrengthResetPayload>
            STREAM_CODEC = StreamCodec.unit(INSTANCE);

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(NETWORK_PROTOCOL_VERSION).playToClient(
                TYPE, STREAM_CODEC, AttackStrengthResetPayload::handle);
    }

    private static void handle(
            AttackStrengthResetPayload payload,
            IPayloadContext context) {
        context.player().resetAttackStrengthTicker();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
