package rotp.addons.sexpistols.content.revolver.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

public record RevolverCooldowns(
        int shotTicks,
        int reloadTicks) {
    public static final int MAX_TICKS = 72_000;
    public static final RevolverCooldowns EMPTY =
            new RevolverCooldowns(0, 0);

    public static final Codec<RevolverCooldowns> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.intRange(0, MAX_TICKS)
                            .optionalFieldOf("shot", 0)
                            .forGetter(RevolverCooldowns::shotTicks),
                    Codec.intRange(0, MAX_TICKS)
                            .optionalFieldOf("reload", 0)
                            .forGetter(RevolverCooldowns::reloadTicks))
                    .apply(instance, RevolverCooldowns::new));

    public static final StreamCodec<
            RegistryFriendlyByteBuf, RevolverCooldowns> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public RevolverCooldowns decode(
                        RegistryFriendlyByteBuf buffer) {
                    return new RevolverCooldowns(
                            buffer.readVarInt(),
                            buffer.readVarInt());
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        RevolverCooldowns cooldowns) {
                    buffer.writeVarInt(cooldowns.shotTicks());
                    buffer.writeVarInt(cooldowns.reloadTicks());
                }
            };

    public RevolverCooldowns {
        shotTicks = Mth.clamp(shotTicks, 0, MAX_TICKS);
        reloadTicks = Mth.clamp(reloadTicks, 0, MAX_TICKS);
    }

    public RevolverCooldowns tick() {
        if (shotTicks == 0 && reloadTicks == 0) {
            return this;
        }
        return new RevolverCooldowns(
                Math.max(0, shotTicks - 1),
                Math.max(0, reloadTicks - 1));
    }

    public RevolverCooldowns withShotTicks(int ticks) {
        return new RevolverCooldowns(ticks, reloadTicks);
    }

    public RevolverCooldowns withReloadTicks(int ticks) {
        return new RevolverCooldowns(shotTicks, ticks);
    }
}
