package rotp.addons.whitesnake.entity;

import java.util.Optional;
import java.util.UUID;

import rotp.core.init.ModEntityDataSerializers;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.whitesnake.WhitesnakeAddon;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class WhitesnakeEntity extends StandEntity {
    public static final long DISGUISE_SELECTION_WINDOW_TICKS = 600L;
    public static final long DISGUISE_REQUEST_INTERVAL_TICKS = 5L;

    private static final EntityDataAccessor<Optional<ResourceLocation>>
            DISGUISE_TYPE = SynchedEntityData.defineId(
                    WhitesnakeEntity.class,
                    ModEntityDataSerializers
                            .RESOURCE_LOCATION_OPTIONAL.get());
    private static final EntityDataAccessor<Integer>
            DISGUISE_CHANGED_TICK = SynchedEntityData.defineId(
                    WhitesnakeEntity.class,
                    EntityDataSerializers.INT);

    private long disguiseSelectionExpiresAt = Long.MIN_VALUE;
    private long lastDisguiseRequestAt = Long.MIN_VALUE;
    private UUID disguiseSelectionSessionId;

    public WhitesnakeEntity(
            EntityType<? extends WhitesnakeEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DISGUISE_TYPE, Optional.empty());
        builder.define(DISGUISE_CHANGED_TICK, 0);
    }

    public Optional<ResourceLocation> getDisguiseType() {
        return entityData.get(DISGUISE_TYPE);
    }

    public int getDisguiseChangedTick() {
        return entityData.get(DISGUISE_CHANGED_TICK);
    }

    public boolean isDisguised() {
        return getDisguiseType().isPresent();
    }

    public SelectionSession beginDisguiseSelection(LivingEntity user) {
        if (!level().isClientSide()
                && user != null
                && getUser() == user) {
            disguiseSelectionSessionId = UUID.randomUUID();
            disguiseSelectionExpiresAt =
                    level().getGameTime()
                            + DISGUISE_SELECTION_WINDOW_TICKS;
            lastDisguiseRequestAt = Long.MIN_VALUE;
            return new SelectionSession(
                    disguiseSelectionSessionId,
                    disguiseSelectionExpiresAt);
        }
        return null;
    }

    public boolean canAcceptDisguiseSelection(long gameTime) {
        if (level().isClientSide()
                || disguiseSelectionSessionId == null
                || disguiseSelectionExpiresAt < gameTime
                || isDisguised()) {
            return false;
        }
        EntityActionInstance currentAction = getCurStandAction();
        return currentAction == null
                || currentAction.ability
                        == WhitesnakeAddon.DISGUISE.get();
    }

    public boolean canAcceptDisguiseSelection(
            UUID sessionId, long gameTime) {
        return sessionId != null
                && sessionId.equals(disguiseSelectionSessionId)
                && canAcceptDisguiseSelection(gameTime);
    }

    public Optional<SelectionSession> disguiseSelectionSession() {
        return disguiseSelectionSessionId != null
                ? Optional.of(new SelectionSession(
                        disguiseSelectionSessionId,
                        disguiseSelectionExpiresAt))
                : Optional.empty();
    }

    public boolean acquireDisguiseRequestPermit(long gameTime) {
        if (lastDisguiseRequestAt != Long.MIN_VALUE
                && gameTime >= lastDisguiseRequestAt
                && gameTime - lastDisguiseRequestAt
                        < DISGUISE_REQUEST_INTERVAL_TICKS) {
            return false;
        }
        lastDisguiseRequestAt = gameTime;
        return true;
    }

    public boolean applyDisguise(
            UUID sessionId,
            ResourceLocation entityTypeId,
            long gameTime) {
        if (level().isClientSide()
                || !canAcceptDisguiseSelection(
                        sessionId, gameTime)) {
            return false;
        }
        entityData.set(
                DISGUISE_TYPE,
                Optional.of(entityTypeId),
                true);
        entityData.set(DISGUISE_CHANGED_TICK, tickCount, true);
        cancelDisguiseSelection();
        return true;
    }

    public void clearDisguiseState() {
        if (!level().isClientSide()) {
            if (isDisguised()) {
                entityData.set(
                        DISGUISE_TYPE, Optional.empty(), true);
                entityData.set(
                        DISGUISE_CHANGED_TICK, tickCount, true);
            }
            cancelDisguiseSelection();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()
                || !isDisguised()
                        && disguiseSelectionExpiresAt
                                == Long.MIN_VALUE) {
            return;
        }

        LivingEntity user = getUser();
        if (!WhitesnakeAddon.isActiveWhitesnakeUser(
                        user, "disguise")
                || rotp.core.powersystem
                                .standpower.StandPower.get(user)
                        .getSummonedStandEntity() != this) {
            clearDisguiseState();
            return;
        }

        if (disguiseSelectionExpiresAt != Long.MIN_VALUE) {
            EntityActionInstance currentAction = getCurStandAction();
            if (level().getGameTime()
                            > disguiseSelectionExpiresAt
                    || currentAction != null
                            && currentAction.ability
                                    != WhitesnakeAddon.DISGUISE.get()) {
                cancelDisguiseSelection();
            }
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        clearDisguiseState();
        super.remove(reason);
    }

    private void cancelDisguiseSelection() {
        disguiseSelectionSessionId = null;
        disguiseSelectionExpiresAt = Long.MIN_VALUE;
    }

    public record SelectionSession(
            UUID id, long expiryGameTick) {}
}
