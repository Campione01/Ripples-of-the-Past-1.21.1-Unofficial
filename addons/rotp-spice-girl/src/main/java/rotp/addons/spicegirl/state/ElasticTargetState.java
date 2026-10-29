package rotp.addons.spicegirl.state;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;

public final class ElasticTargetState {
    @Nullable
    private UUID ownerUuid;
    @Nullable
    private UUID standUuid;
    @Nullable
    private UUID generationUuid;
    @Nullable
    private ResourceLocation dimension;
    private long expiresAt;
    private int amplifier;

    public boolean isActive() {
        return ownerUuid != null
                && standUuid != null
                && generationUuid != null
                && dimension != null
                && expiresAt > 0L;
    }

    @Nullable
    public UUID ownerUuid() {
        return ownerUuid;
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

    public long expiresAt() {
        return expiresAt;
    }

    public int amplifier() {
        return amplifier;
    }

    public void apply(
            UUID ownerUuid,
            UUID standUuid,
            UUID generationUuid,
            ResourceLocation dimension,
            long expiresAt,
            int amplifier) {
        this.ownerUuid = ownerUuid;
        this.standUuid = standUuid;
        this.generationUuid = generationUuid;
        this.dimension = dimension;
        this.expiresAt = expiresAt;
        this.amplifier = amplifier;
    }

    public boolean matches(
            UUID owner, UUID stand, UUID generation) {
        return owner != null
                && stand != null
                && generation != null
                && owner.equals(ownerUuid)
                && stand.equals(standUuid)
                && generation.equals(generationUuid);
    }

    public boolean clear() {
        if (!isActive()
                && ownerUuid == null
                && standUuid == null
                && generationUuid == null
                && dimension == null
                && expiresAt == 0L
                && amplifier == 0) {
            return false;
        }
        ownerUuid = null;
        standUuid = null;
        generationUuid = null;
        dimension = null;
        expiresAt = 0L;
        amplifier = 0;
        return true;
    }
}
