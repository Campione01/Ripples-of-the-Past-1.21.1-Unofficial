package rotp.addons.hermitpurple.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenHermitTargetPayload(
        List<HermitTargetEntry> entries)
        implements CustomPacketPayload {
    private static final int MAX_ENTRIES = 4096;
    private static final int MAX_TEXT = 256;

    public static final Type<OpenHermitTargetPayload> TYPE =
            new Type<>(HermitTargetPayloads.id("open_target"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            OpenHermitTargetPayload> STREAM_CODEC =
            StreamCodec.ofMember(
                    OpenHermitTargetPayload::write,
                    OpenHermitTargetPayload::new);

    public OpenHermitTargetPayload {
        entries = List.copyOf(entries);
        if (entries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException(
                    "Too many Hermit Purple target entries");
        }
    }

    private OpenHermitTargetPayload(
            RegistryFriendlyByteBuf buffer) {
        this(readEntries(buffer));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(entries.size());
        for (HermitTargetEntry entry : entries) {
            buffer.writeVarInt(entry.mode());
            buffer.writeUtf(entry.id(), MAX_TEXT);
            buffer.writeUtf(entry.label(), MAX_TEXT);
        }
    }

    private static List<HermitTargetEntry> readEntries(
            RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) {
            throw new IllegalArgumentException(
                    "Invalid Hermit Purple target count: " + count);
        }
        List<HermitTargetEntry> entries =
                new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            entries.add(new HermitTargetEntry(
                    buffer.readVarInt(),
                    buffer.readUtf(MAX_TEXT),
                    buffer.readUtf(MAX_TEXT)));
        }
        return entries;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
