package rotp.addons.pearljam.food;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

public record PearlJamDishData(
        UUID chefUuid,
        String chefName,
        @Nullable
        UUID intendedConsumerUuid,
        String intendedConsumerName,
        PearlJamDishKind kind,
        PearlJamTreatment treatment,
        int potency) {
    public static final int MIN_POTENCY = 1;
    public static final int MAX_POTENCY = 3;
    public static final int MAX_NAME_LENGTH = 64;

    public static final Codec<PearlJamDishData> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    UUIDUtil.CODEC.fieldOf("chef")
                            .forGetter(PearlJamDishData::chefUuid),
                    Codec.STRING.optionalFieldOf("chef_name", "")
                            .forGetter(PearlJamDishData::chefName),
                    UUIDUtil.CODEC.optionalFieldOf("consumer")
                            .forGetter(
                                    data -> Optional.ofNullable(
                                            data.intendedConsumerUuid())),
                    Codec.STRING.optionalFieldOf("consumer_name", "")
                            .forGetter(
                                    PearlJamDishData::intendedConsumerName),
                    PearlJamDishKind.CODEC.fieldOf("kind")
                            .forGetter(PearlJamDishData::kind),
                    PearlJamTreatment.CODEC
                            .optionalFieldOf(
                                    "treatment",
                                    PearlJamTreatment.NONE)
                            .forGetter(PearlJamDishData::treatment),
                    Codec.INT.fieldOf("potency")
                            .forGetter(PearlJamDishData::potency))
                    .apply(instance, (
                            chef,
                            chefName,
                            consumer,
                            consumerName,
                            kind,
                            treatment,
                            potency) -> new PearlJamDishData(
                                    chef,
                                    chefName,
                                    consumer.orElse(null),
                                    consumerName,
                                    kind,
                                    treatment,
                                    potency)));

    public static final StreamCodec<
            RegistryFriendlyByteBuf, PearlJamDishData> STREAM_CODEC =
            StreamCodec.ofMember(
                    PearlJamDishData::encode,
                    PearlJamDishData::decode);

    public PearlJamDishData {
        Objects.requireNonNull(
                chefUuid, "Pearl Jam chef UUID must not be null");
        chefName = boundedName(chefName);
        intendedConsumerName = Objects.requireNonNullElse(
                intendedConsumerName, "");
        intendedConsumerName = boundedName(intendedConsumerName);
        kind = Objects.requireNonNullElse(
                kind, PearlJamDishKind.INJECTED);
        treatment = Objects.requireNonNullElse(
                treatment, PearlJamTreatment.NONE);
        if (kind == PearlJamDishKind.POISONED) {
            intendedConsumerUuid = null;
            intendedConsumerName = "";
            treatment = PearlJamTreatment.NONE;
        }
        potency = Mth.clamp(
                potency, MIN_POTENCY, MAX_POTENCY);
    }

    public boolean isIntendedFor(UUID consumerUuid) {
        return kind == PearlJamDishKind.POISONED
                || intendedConsumerUuid != null
                        && intendedConsumerUuid.equals(consumerUuid);
    }

    public boolean hasVisibleChef() {
        return !chefName.isBlank();
    }

    private static String boundedName(String value) {
        String name = Objects.requireNonNullElse(value, "");
        return name.length() > MAX_NAME_LENGTH
                ? name.substring(0, MAX_NAME_LENGTH)
                : name;
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(chefUuid);
        buffer.writeUtf(chefName, MAX_NAME_LENGTH);
        buffer.writeBoolean(intendedConsumerUuid != null);
        if (intendedConsumerUuid != null) {
            buffer.writeUUID(intendedConsumerUuid);
        }
        buffer.writeUtf(
                intendedConsumerName, MAX_NAME_LENGTH);
        buffer.writeVarInt(kind.ordinal());
        buffer.writeVarInt(treatment.ordinal());
        buffer.writeVarInt(potency);
    }

    private static PearlJamDishData decode(
            RegistryFriendlyByteBuf buffer) {
        return new PearlJamDishData(
                buffer.readUUID(),
                buffer.readUtf(MAX_NAME_LENGTH),
                buffer.readBoolean() ? buffer.readUUID() : null,
                buffer.readUtf(MAX_NAME_LENGTH),
                PearlJamDishKind.byId(buffer.readVarInt()),
                PearlJamTreatment.byId(buffer.readVarInt()),
                buffer.readVarInt());
    }
}
