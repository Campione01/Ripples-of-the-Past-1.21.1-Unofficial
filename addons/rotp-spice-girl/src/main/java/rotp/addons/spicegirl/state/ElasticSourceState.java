package rotp.addons.spicegirl.state;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;

public final class ElasticSourceState {
    @Nullable
    private UUID targetUuid;
    @Nullable
    private UUID standUuid;
    @Nullable
    private UUID generationUuid;
    @Nullable
    private ResourceLocation dimension;
    private long expiresAt;

    public boolean isActive() {
        return targetUuid != null
                && standUuid != null
                && generationUuid != null
                && dimension != null
                && expiresAt > 0L;
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

    public long expiresAt() {
        return expiresAt;
    }

    public void link(
            UUID targetUuid,
            UUID standUuid,
            UUID generationUuid,
            ResourceLocation dimension,
            long expiresAt) {
        this.targetUuid = targetUuid;
        this.standUuid = standUuid;
        this.generationUuid = generationUuid;
        this.dimension = dimension;
        this.expiresAt = expiresAt;
    }

    public boolean matchesTarget(
            UUID target, UUID generation) {
        return target != null
                && generation != null
                && target.equals(targetUuid)
                && generation.equals(generationUuid);
    }

    public boolean matchesStand(UUID stand) {
        return stand != null && stand.equals(standUuid);
    }

    public boolean clear() {
        if (!isActive()
                && targetUuid == null
                && standUuid == null
                && generationUuid == null
                && dimension == null
                && expiresAt == 0L) {
            return false;
        }
        targetUuid = null;
        standUuid = null;
        generationUuid = null;
        dimension = null;
        expiresAt = 0L;
        return true;
    }
}
