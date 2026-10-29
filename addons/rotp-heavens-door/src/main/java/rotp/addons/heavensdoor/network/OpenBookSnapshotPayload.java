package rotp.addons.heavensdoor.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.heavensdoor.HeavensDoorAddon;
import rotp.addons.heavensdoor.state.BookState;
import rotp.addons.heavensdoor.state.BookCommandSessionService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OpenBookSnapshotPayload(
        int entityId,
        String name,
        Component entityTypeName,
        float health,
        float maxHealth,
        int ageSeconds,
        float width,
        float height,
        Component ownerLine,
        Component standName,
        Component playerPowerName,
        int pageCount,
        long remainingTicks,
        UUID sessionNonce,
        long sessionGeneration,
        long sessionExpiresAt,
        int availableCommandMask,
        boolean blindnessActive,
        boolean selfHarmActive,
        List<BookPlayerOption> playerOptions)
        implements CustomPacketPayload {
    private static final int MAX_TEXT_LENGTH = 128;
    private static final int MAX_PLAYER_OPTIONS = 256;
    private static final int MAX_PLAYER_OPTION_NAME_LENGTH = 64;
    public static final String NONE_KEY = "gui.rotp_hd.book.none";
    public static final String OWNER_KEY = "gui.rotp_hd.book.owner";
    public static final String MASTER_KEY = "gui.rotp_hd.book.master";
    public static final String PLAYER_KEY = "gui.rotp_hd.book.itsplayer";
    public static final Type<OpenBookSnapshotPayload> TYPE =
            new Type<>(HeavensDoorAddon.id("open_book_snapshot"));
    private static Consumer<OpenBookSnapshotPayload>
            clientOpenHandler = payload -> {};

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            OpenBookSnapshotPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public OpenBookSnapshotPayload decode(
                        RegistryFriendlyByteBuf buffer) {
                    return new OpenBookSnapshotPayload(
                            buffer.readVarInt(),
                            buffer.readUtf(MAX_TEXT_LENGTH),
                            ComponentSerialization.STREAM_CODEC
                                    .decode(buffer),
                            buffer.readFloat(),
                            buffer.readFloat(),
                            buffer.readVarInt(),
                            buffer.readFloat(),
                            buffer.readFloat(),
                            ComponentSerialization.STREAM_CODEC
                                    .decode(buffer),
                            ComponentSerialization.STREAM_CODEC
                                    .decode(buffer),
                            ComponentSerialization.STREAM_CODEC
                                    .decode(buffer),
                            buffer.readVarInt(),
                            buffer.readVarLong(),
                            buffer.readUUID(),
                            buffer.readVarLong(),
                            buffer.readVarLong(),
                            buffer.readVarInt(),
                            buffer.readBoolean(),
                            buffer.readBoolean(),
                            readPlayerOptions(buffer));
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        OpenBookSnapshotPayload payload) {
                    buffer.writeVarInt(payload.entityId());
                    buffer.writeUtf(
                            payload.name(), MAX_TEXT_LENGTH);
                    ComponentSerialization.STREAM_CODEC.encode(
                            buffer, payload.entityTypeName());
                    buffer.writeFloat(payload.health());
                    buffer.writeFloat(payload.maxHealth());
                    buffer.writeVarInt(payload.ageSeconds());
                    buffer.writeFloat(payload.width());
                    buffer.writeFloat(payload.height());
                    ComponentSerialization.STREAM_CODEC.encode(
                            buffer, payload.ownerLine());
                    ComponentSerialization.STREAM_CODEC.encode(
                            buffer, payload.standName());
                    ComponentSerialization.STREAM_CODEC.encode(
                            buffer, payload.playerPowerName());
                    buffer.writeVarInt(payload.pageCount());
                    buffer.writeVarLong(payload.remainingTicks());
                    buffer.writeUUID(payload.sessionNonce());
                    buffer.writeVarLong(payload.sessionGeneration());
                    buffer.writeVarLong(payload.sessionExpiresAt());
                    buffer.writeVarInt(payload.availableCommandMask());
                    buffer.writeBoolean(payload.blindnessActive());
                    buffer.writeBoolean(payload.selfHarmActive());
                    writePlayerOptions(
                            buffer, payload.playerOptions());
                }
            };

    public OpenBookSnapshotPayload {
        name = boundedText(name, MAX_TEXT_LENGTH);
        // Server-built display components; the client translates them.
        entityTypeName = Objects.requireNonNull(entityTypeName);
        ownerLine = Objects.requireNonNull(ownerLine);
        standName = Objects.requireNonNull(standName);
        playerPowerName = Objects.requireNonNull(playerPowerName);
        sessionNonce = Objects.requireNonNull(sessionNonce);
        playerOptions = boundedPlayerOptions(playerOptions);
    }

    public static OpenBookSnapshotPayload from(
            LivingEntity target,
            BookState state,
            BookCommandSessionService.IssuedSession session) {
        // 1.16 BookGui read a Stand's powers and life time from its user.
        LivingEntity holder = target instanceof StandEntity stand
                && stand.getUser() != null
                        ? stand.getUser()
                        : target;
        StandPower standPower = StandPower.get(holder);
        PlayerPower playerPower = PlayerPower.get(holder);
        return new OpenBookSnapshotPayload(
                target.getId(),
                target.getDisplayName().getString(),
                target.getType().getDescription(),
                target.getHealth(),
                target.getMaxHealth(),
                liveSeconds(holder),
                target.getBbWidth(),
                target.getBbHeight(),
                ownerLine(target),
                standPower != null && standPower.hasPower()
                        ? nameOrNone(standPower.getName())
                        : Component.translatable(NONE_KEY),
                playerPower != null && playerPower.hasPower()
                        ? nameOrNone(playerPower.getName())
                        : Component.translatable(NONE_KEY),
                state.pageCount(),
                Math.max(
                        0L,
                        state.expiresAt()
                                - target.level().getGameTime()),
                session.nonce(),
                session.generation(),
                session.expiresAt(),
                session.commandMask(),
                writtenCommandActive(target, state, BookCommand.BLINDNESS_TOGGLE),
                writtenCommandActive(target, state, BookCommand.SELF_HARM_TOGGLE),
                session.playerOptions());
    }

    // 1.16 BookGui: a pet shows its tame owner, a Stand its user.
    static Component ownerLine(LivingEntity target) {
        if (target instanceof StandEntity stand) {
            LivingEntity user = stand.getUser();
            return user != null
                    ? Component.translatable(
                            MASTER_KEY, user.getDisplayName())
                    : Component.translatable(MASTER_KEY + ".none");
        }
        if (target instanceof OwnableEntity ownable) {
            LivingEntity owner = ownable.getOwner();
            return owner != null
                    ? Component.translatable(
                            OWNER_KEY, owner.getDisplayName())
                    : Component.translatable(OWNER_KEY + ".none");
        }
        if (target instanceof Player) {
            return Component.translatable(PLAYER_KEY);
        }
        // Keep the page line filled for untamed mobs.
        return Component.translatable(OWNER_KEY + ".none");
    }

    // 1.16 counted persisted ticks alive; players keep time since death.
    static int liveSeconds(LivingEntity entity) {
        long ticks = entity instanceof ServerPlayer player
                ? player.getStats().getValue(
                        Stats.CUSTOM.get(Stats.TIME_SINCE_DEATH))
                : entity.tickCount;
        return (int) Math.min(
                Integer.MAX_VALUE, Math.max(0L, ticks) / 20L);
    }

    private static Component nameOrNone(Component name) {
        return name != null
                ? name
                : Component.translatable(NONE_KEY);
    }

    // Plain-text views kept for the add-on smoke harness.
    public String entityType() {
        return entityTypeName.getString();
    }

    public String ownerName() {
        return ownerLine.getString();
    }

    public String standId() {
        return standName.getString();
    }

    public String playerPowerId() {
        return playerPowerName.getString();
    }

    public static void installClientOpenHandler(
            Consumer<OpenBookSnapshotPayload> handler) {
        clientOpenHandler = Objects.requireNonNull(handler);
    }

    public static void handle(
            OpenBookSnapshotPayload payload,
            IPayloadContext context) {
        Player player = context.player();
        if (player != null) {
            clientOpenHandler.accept(payload);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static List<BookPlayerOption> readPlayerOptions(
            RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_PLAYER_OPTIONS) {
            throw new IllegalArgumentException(
                    "Invalid Heaven's Door player option count "
                            + count);
        }
        List<BookPlayerOption> options =
                new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            options.add(new BookPlayerOption(
                    buffer.readVarInt(),
                    buffer.readUtf(
                            MAX_PLAYER_OPTION_NAME_LENGTH)));
        }
        return List.copyOf(options);
    }

    private static void writePlayerOptions(
            RegistryFriendlyByteBuf buffer,
            List<BookPlayerOption> options) {
        buffer.writeVarInt(options.size());
        for (BookPlayerOption option : options) {
            buffer.writeVarInt(option.optionId());
            buffer.writeUtf(
                    option.displayName(),
                    MAX_PLAYER_OPTION_NAME_LENGTH);
        }
    }

    private static List<BookPlayerOption> boundedPlayerOptions(
            List<BookPlayerOption> options) {
        Objects.requireNonNull(options);
        int optionCount = Math.min(
                options.size(), MAX_PLAYER_OPTIONS);
        List<BookPlayerOption> bounded =
                new ArrayList<>(optionCount);
        for (int index = 0; index < optionCount; index++) {
            BookPlayerOption option =
                    Objects.requireNonNull(options.get(index));
            bounded.add(new BookPlayerOption(
                    option.optionId(),
                    boundedText(
                            option.displayName(),
                            MAX_PLAYER_OPTION_NAME_LENGTH)));
        }
        return List.copyOf(bounded);
    }

    private static boolean writtenCommandActive(LivingEntity target, BookState state, BookCommand command) {
        return target.hasData(HeavensDoorAddon.WRITTEN_COMMAND_STATE)
                && target.getData(HeavensDoorAddon.WRITTEN_COMMAND_STATE).has(command, state.ownerUuid())
                || command == BookCommand.SELF_HARM_TOGGLE
                        && BookCommandSessionService.ownsLegacySelfHarm(target, state.ownerUuid());
    }

    private static String boundedText(
            String value, int maxLength) {
        Objects.requireNonNull(value);
        if (value.length() <= maxLength) {
            return value;
        }
        int end = maxLength;
        if (Character.isHighSurrogate(value.charAt(end - 1))
                && Character.isLowSurrogate(value.charAt(end))) {
            end--;
        }
        return value.substring(0, end);
    }
}
