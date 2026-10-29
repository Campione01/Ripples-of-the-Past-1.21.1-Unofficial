package rotp.addons.sexpistols.network;

import rotp.addons.sexpistols.SexPistolsIds;
import rotp.addons.sexpistols.config.SexPistolsConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SexPistolsConfigSyncPayload(
        double bulletDamageMultiplier,
        boolean bulletDestroysBlocks,
        int blockDamageMemoryTicks,
        double stableDamageEndDistance,
        double closeDamageDropDistance,
        double minDistanceDamage,
        int maxRicochetCount,
        double targetSearchRange,
        double transferAssistRange,
        int reviveTicks,
        boolean hungerEnabled,
        int hungerMinTicks,
        int hungerExtraTicks)
        implements CustomPacketPayload {
    public static final Type<SexPistolsConfigSyncPayload> TYPE =
            new Type<>(SexPistolsIds.id(
                    "trajectory_config_sync"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            SexPistolsConfigSyncPayload> STREAM_CODEC =
            StreamCodec.ofMember(
                    SexPistolsConfigSyncPayload::write,
                    SexPistolsConfigSyncPayload::new);

    public SexPistolsConfigSyncPayload {
        requireFiniteInRange(
                "bulletDamageMultiplier",
                bulletDamageMultiplier,
                SexPistolsConfig.MAX_BULLET_DAMAGE_MULTIPLIER);
        requireNonNegative(
                "blockDamageMemoryTicks",
                blockDamageMemoryTicks);
        requireFiniteInRange(
                "stableDamageEndDistance",
                stableDamageEndDistance,
                SexPistolsConfig.MAX_STABLE_DAMAGE_END_DISTANCE);
        requireFiniteInRange(
                "closeDamageDropDistance",
                closeDamageDropDistance,
                SexPistolsConfig.MAX_CLOSE_DAMAGE_DROP_DISTANCE);
        requireFiniteInRange(
                "minDistanceDamage",
                minDistanceDamage,
                SexPistolsConfig.MAX_MIN_DISTANCE_DAMAGE);
        if (maxRicochetCount < 0) {
            throw new IllegalArgumentException(
                    "maxRicochetCount must be non-negative");
        }
        requireFiniteInRange(
                "targetSearchRange",
                targetSearchRange,
                SexPistolsConfig.MAX_TARGET_SEARCH_RANGE);
        requireFiniteInRange(
                "transferAssistRange",
                transferAssistRange,
                SexPistolsConfig.MAX_TRANSFER_ASSIST_RANGE);
        if (reviveTicks < 1) {
            throw new IllegalArgumentException(
                    "reviveTicks must be positive");
        }
        if (hungerMinTicks < 100) {
            throw new IllegalArgumentException(
                    "hungerMinTicks must be at least 100");
        }
        requireNonNegative(
                "hungerExtraTicks", hungerExtraTicks);
    }

    private SexPistolsConfigSyncPayload(
            RegistryFriendlyByteBuf buffer) {
        this(
                buffer.readDouble(),
                buffer.readBoolean(),
                buffer.readVarInt(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readVarInt(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readVarInt(),
                buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeDouble(bulletDamageMultiplier);
        buffer.writeBoolean(bulletDestroysBlocks);
        buffer.writeVarInt(blockDamageMemoryTicks);
        buffer.writeDouble(stableDamageEndDistance);
        buffer.writeDouble(closeDamageDropDistance);
        buffer.writeDouble(minDistanceDamage);
        buffer.writeVarInt(maxRicochetCount);
        buffer.writeDouble(targetSearchRange);
        buffer.writeDouble(transferAssistRange);
        buffer.writeVarInt(reviveTicks);
        buffer.writeBoolean(hungerEnabled);
        buffer.writeVarInt(hungerMinTicks);
        buffer.writeVarInt(hungerExtraTicks);
    }

    private static void requireNonNegative(
            String name, int value) {
        if (value < 0) {
            throw new IllegalArgumentException(
                    name + " must be non-negative");
        }
    }

    private static void requireFiniteInRange(
            String name, double value, double maximum) {
        if (!Double.isFinite(value)
                || value < 0.0D
                || value > maximum) {
            throw new IllegalArgumentException(
                    name + " must be finite and in range [0.0, "
                            + maximum + "]");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
