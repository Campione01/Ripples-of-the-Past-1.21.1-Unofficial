package rotp.addons.lovers.state;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public final class LoversLinkState {
    public static final StreamCodec<
            RegistryFriendlyByteBuf, LoversLinkState> STREAM_CODEC =
            StreamCodec.ofMember(
                    LoversLinkState::encode,
                    LoversLinkState::decode);

    @Nullable
    private UUID ownerUuid;
    @Nullable
    private UUID targetUuid;
    @Nullable
    private UUID standUuid;
    @Nullable
    private UUID generationUuid;
    @Nullable
    private ResourceLocation dimension;

    public boolean isActive() {
        return ownerUuid != null
                && targetUuid != null
                && standUuid != null
                && generationUuid != null
                && dimension != null;
    }

    @Nullable
    public UUID ownerUuid() {
        return ownerUuid;
    }

    @Nullable
    public UUID targetUuid() {
        return targetUuid;
    }

    @Nullable
    public UUID standUuid() {
        return standUuid;
    }

    @Nullable
    public UUID generationUuid() {
        return generationUuid;
    }

    @Nullable
    public ResourceLocation dimension() {
        return dimension;
    }

    public void link(
            UUID ownerUuid,
            UUID targetUuid,
            UUID standUuid,
            UUID generationUuid,
            ResourceLocation dimension) {
        this.ownerUuid = ownerUuid;
        this.targetUuid = targetUuid;
        this.standUuid = standUuid;
        this.generationUuid = generationUuid;
        this.dimension = dimension;
    }

    public boolean clear() {
        if (!isActive()
                && ownerUuid == null
                && targetUuid == null
                && standUuid == null
                && generationUuid == null
                && dimension == null) {
            return false;
        }
        ownerUuid = null;
        targetUuid = null;
        standUuid = null;
        generationUuid = null;
        dimension = null;
        return true;
    }

    public boolean matchesStand(UUID uuid) {
        return uuid != null && uuid.equals(standUuid);
    }

    public boolean matchesTarget(UUID uuid) {
        return uuid != null && uuid.equals(targetUuid);
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(isActive());
        if (isActive()) {
            buffer.writeUUID(ownerUuid);
            buffer.writeUUID(targetUuid);
            buffer.writeUUID(standUuid);
            buffer.writeUUID(generationUuid);
            buffer.writeResourceLocation(dimension);
        }
    }

    private static LoversLinkState decode(
            RegistryFriendlyByteBuf buffer) {
        LoversLinkState state = new LoversLinkState();
        if (buffer.readBoolean()) {
            state.link(
                    buffer.readUUID(),
                    buffer.readUUID(),
                    buffer.readUUID(),
                    buffer.readUUID(),
                    buffer.readResourceLocation());
        }
        return state;
    }
}
