package rotp.addons.lovers.entity;

import java.util.Optional;
import java.util.UUID;

import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.lovers.state.LoversLinkService;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public final class LoversStandEntity extends StandEntity {
    private static final EntityDataAccessor<Optional<UUID>> LINK_TARGET =
            SynchedEntityData.defineId(
                    LoversStandEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>>
            LINK_GENERATION = SynchedEntityData.defineId(
                    LoversStandEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);

    public LoversStandEntity(
            EntityType<? extends LoversStandEntity> type,
            Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(LINK_TARGET, Optional.empty());
        builder.define(LINK_GENERATION, Optional.empty());
    }

    public UUID beginLink(UUID targetUuid) {
        UUID generationUuid = UUID.randomUUID();
        entityData.set(LINK_TARGET, Optional.of(targetUuid));
        entityData.set(
                LINK_GENERATION, Optional.of(generationUuid));
        return generationUuid;
    }

    public boolean hasLinkData() {
        return entityData.get(LINK_TARGET).isPresent()
                || entityData.get(LINK_GENERATION).isPresent();
    }

    public boolean matchesLink(
            UUID targetUuid, UUID generationUuid) {
        return targetUuid != null
                && generationUuid != null
                && entityData.get(LINK_TARGET)
                        .filter(targetUuid::equals)
                        .isPresent()
                && entityData.get(LINK_GENERATION)
                        .filter(generationUuid::equals)
                        .isPresent();
    }

    public void clearLinkData(UUID generationUuid) {
        if (generationUuid == null
                || entityData.get(LINK_GENERATION)
                        .filter(generationUuid::equals)
                        .isPresent()) {
            clearLinkData();
        }
    }

    public void clearLinkData() {
        entityData.set(LINK_TARGET, Optional.empty());
        entityData.set(LINK_GENERATION, Optional.empty());
    }

    @Override
    public void onUnsummonUserInput() {
        if (!level().isClientSide()) {
            LoversLinkService.clearByStand(this);
        }
        super.onUnsummonUserInput();
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide()) {
            LoversLinkService.clearByStand(this);
        }
        super.remove(reason);
    }
}
