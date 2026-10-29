package rotp.addons.spicegirl.state;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public final class SoftAreaState {
    public static final int MAX_POSITIONS = 27;

    private final List<BlockPos> positions =
            new ArrayList<>(MAX_POSITIONS);
    @Nullable
    private UUID standUuid;
    @Nullable
    private ResourceLocation dimension;
    private long expiresAt;

    public boolean isActive() {
        return !positions.isEmpty()
                && standUuid != null
                && dimension != null
                && expiresAt > 0L;
    }

    public List<BlockPos> positions() {
        return Collections.unmodifiableList(positions);
    }

    @Nullable
    public UUID standUuid() {
        return standUuid;
    }

    @Nullable
    public ResourceLocation dimension() {
        return dimension;
    }

    public long expiresAt() {
        return expiresAt;
    }

    public void set(
            Collection<BlockPos> positions,
            UUID standUuid,
            ResourceLocation dimension,
            long expiresAt) {
        clear();
        positions.stream()
                .distinct()
                .limit(MAX_POSITIONS)
                .map(BlockPos::immutable)
                .forEach(this.positions::add);
        this.standUuid = standUuid;
        this.dimension = dimension;
        this.expiresAt = expiresAt;
    }

    public boolean contains(BlockPos position) {
        return position != null
                && positions.contains(position);
    }

    public boolean remove(BlockPos position) {
        return positions.remove(position);
    }

    public boolean matchesStand(UUID stand) {
        return stand != null && stand.equals(standUuid);
    }

    public boolean clear() {
        boolean changed = isActive()
                || !positions.isEmpty()
                || standUuid != null
                || dimension != null
                || expiresAt != 0L;
        positions.clear();
        standUuid = null;
        dimension = null;
        expiresAt = 0L;
        return changed;
    }
}
