package rotp.addons.khnum.entity;

import java.util.Optional;
import java.util.UUID;

import rotp.core.init.ModEntityDataSerializers;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.khnum.KhnumAddon;
import rotp.addons.khnum.disguise.DisguiseMode;
import rotp.addons.khnum.disguise.DisguisePolicy;
import rotp.addons.khnum.disguise.DisguiseRetaliationService;
import rotp.addons.khnum.disguise.DisguiseScaleService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class KhnumControllerEntity extends StandEntity {
    public static final long SELECTION_WINDOW_TICKS = 600L;
    public static final long REQUEST_INTERVAL_TICKS = 5L;

    private static final String TAG_MODE = "KhnumDisguiseMode";
    private static final String TAG_ENTITY = "KhnumDisguiseEntity";
    private static final String TAG_PLAYER = "KhnumDisguisePlayer";
    private static final String TAG_PLAYER_NAME =
            "KhnumDisguisePlayerName";
    private static final String TAG_CHANGED = "KhnumStateChangedTick";

    private static final EntityDataAccessor<Byte> DISGUISE_MODE =
            SynchedEntityData.defineId(
                    KhnumControllerEntity.class,
                    EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Optional<ResourceLocation>>
            DISGUISE_ID = SynchedEntityData.defineId(
                    KhnumControllerEntity.class,
                    ModEntityDataSerializers
                            .RESOURCE_LOCATION_OPTIONAL.get());
    private static final EntityDataAccessor<Optional<UUID>>
            DISGUISE_PLAYER = SynchedEntityData.defineId(
                    KhnumControllerEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String>
            DISGUISE_PLAYER_NAME = SynchedEntityData.defineId(
                    KhnumControllerEntity.class,
                    EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer>
            STATE_CHANGED_TICK = SynchedEntityData.defineId(
                    KhnumControllerEntity.class,
                    EntityDataSerializers.INT);

    private DisguiseMode pendingMode = DisguiseMode.NONE;
    private long selectionExpiresAt = Long.MIN_VALUE;
    private long lastRequestAt = Long.MIN_VALUE;
    private boolean restoreScalePending;

    public KhnumControllerEntity(
            EntityType<? extends KhnumControllerEntity> type,
            Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setInvisible(true);
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DISGUISE_MODE, DisguiseMode.NONE.id());
        builder.define(DISGUISE_ID, Optional.empty());
        builder.define(DISGUISE_PLAYER, Optional.empty());
        builder.define(DISGUISE_PLAYER_NAME, "");
        builder.define(STATE_CHANGED_TICK, 0);
    }

    public DisguiseMode getDisguiseMode() {
        return DisguiseMode.byId(entityData.get(DISGUISE_MODE));
    }

    public Optional<ResourceLocation> getDisguiseId() {
        return entityData.get(DISGUISE_ID);
    }

    public Optional<UUID> getDisguisePlayer() {
        return entityData.get(DISGUISE_PLAYER);
    }

    public String getDisguisePlayerName() {
        return entityData.get(DISGUISE_PLAYER_NAME);
    }

    public boolean isDisguised() {
        return getDisguiseMode() != DisguiseMode.NONE;
    }

    public int getStateChangedTick() {
        return entityData.get(STATE_CHANGED_TICK);
    }

    public void beginSelection(DisguiseMode mode) {
        if (level().isClientSide() || mode == DisguiseMode.NONE) {
            return;
        }
        clearDisguise();
        pendingMode = mode;
        selectionExpiresAt =
                level().getGameTime() + SELECTION_WINDOW_TICKS;
    }

    public boolean canAcceptSelection(
            DisguiseMode mode, long gameTime) {
        return !level().isClientSide()
                && mode != DisguiseMode.NONE
                && pendingMode == mode
                && gameTime <= selectionExpiresAt;
    }

    public boolean acquireSelectionPermit(long gameTime) {
        if (lastRequestAt != Long.MIN_VALUE
                && gameTime >= lastRequestAt
                && gameTime - lastRequestAt
                        < REQUEST_INTERVAL_TICKS) {
            return false;
        }
        lastRequestAt = gameTime;
        return true;
    }

    public void applyEntityDisguise(
            ResourceLocation id, EntityDimensions dimensions) {
        if (level().isClientSide()
                || !canAcceptSelection(
                        DisguiseMode.ENTITY,
                        level().getGameTime())) {
            return;
        }
        entityData.set(
                DISGUISE_MODE, DisguiseMode.ENTITY.id(), true);
        entityData.set(DISGUISE_ID, Optional.of(id), true);
        entityData.set(DISGUISE_PLAYER, Optional.empty(), true);
        entityData.set(DISGUISE_PLAYER_NAME, "", true);
        applyScale(dimensions);
        finishSelection();
    }

    public void applyPlayerDisguise(Player target) {
        if (level().isClientSide()
                || target == null
                || !canAcceptSelection(
                        DisguiseMode.PLAYER,
                        level().getGameTime())) {
            return;
        }
        entityData.set(
                DISGUISE_MODE, DisguiseMode.PLAYER.id(), true);
        entityData.set(DISGUISE_ID, Optional.empty(), true);
        entityData.set(
                DISGUISE_PLAYER,
                Optional.of(target.getUUID()),
                true);
        entityData.set(
                DISGUISE_PLAYER_NAME,
                target.getGameProfile().getName(),
                true);
        applyScale(target.getType().getDimensions());
        finishSelection();
    }

    public void clearDisguise() {
        if (level().isClientSide()) {
            return;
        }
        LivingEntity user = getUser();
        DisguiseScaleService.reset(user);
        restoreScalePending = false;
        if (isDisguised()
                || getDisguiseId().isPresent()
                || getDisguisePlayer().isPresent()) {
            entityData.set(
                    DISGUISE_MODE, DisguiseMode.NONE.id(), true);
            entityData.set(DISGUISE_ID, Optional.empty(), true);
            entityData.set(DISGUISE_PLAYER, Optional.empty(), true);
            entityData.set(DISGUISE_PLAYER_NAME, "", true);
            markChanged();
        }
        cancelSelection();
    }

    public void clearForLifecycle() {
        if (level().isClientSide()) {
            return;
        }
        LivingEntity user = getUser();
        clearDisguise();
        if (user instanceof Player player) {
            DisguiseRetaliationService.clear(player);
        }
    }

    @Override
    public void tick() {
        super.tick();
        noPhysics = true;
        setNoGravity(true);
        setInvisible(true);
        LivingEntity user = getUser();
        if (user == null || !user.isAlive() || user.isRemoved()) {
            if (!level().isClientSide()) {
                clearForLifecycle();
            }
            return;
        }
        setPos(user.getX(), user.getY(), user.getZ());
        setYRot(user.getYRot());
        setXRot(user.getXRot());
        yBodyRot = user.yBodyRot;
        yBodyRotO = user.yBodyRotO;
        yHeadRot = user.yHeadRot;
        yHeadRotO = user.yHeadRotO;

        if (!level().isClientSide()) {
            StandPower power = StandPower.get(user);
            if (power == null
                    || power.getPowerType() != KhnumAddon.KHNUM.get()
                    || power.getSummonedStandEntity() != this) {
                clearForLifecycle();
                discard();
                return;
            }
            if (restoreScalePending) {
                restorePersistedScale();
            }
            if (selectionExpiresAt != Long.MIN_VALUE
                    && level().getGameTime() > selectionExpiresAt) {
                cancelSelection();
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte(TAG_MODE, getDisguiseMode().id());
        getDisguiseId().ifPresent(
                id -> tag.putString(TAG_ENTITY, id.toString()));
        getDisguisePlayer().ifPresent(
                uuid -> tag.putUUID(TAG_PLAYER, uuid));
        tag.putString(TAG_PLAYER_NAME, getDisguisePlayerName());
        tag.putInt(TAG_CHANGED, getStateChangedTick());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        DisguiseMode mode = DisguiseMode.byId(
                tag.getByte(TAG_MODE));
        ResourceLocation entityId = tag.contains(
                TAG_ENTITY, Tag.TAG_STRING)
                        ? ResourceLocation.tryParse(
                                tag.getString(TAG_ENTITY))
                        : null;
        UUID playerId = tag.hasUUID(TAG_PLAYER)
                ? tag.getUUID(TAG_PLAYER)
                : null;
        String playerName = tag.contains(
                TAG_PLAYER_NAME, Tag.TAG_STRING)
                        ? tag.getString(TAG_PLAYER_NAME)
                        : "";

        if (mode == DisguiseMode.ENTITY && entityId != null) {
            entityData.set(DISGUISE_MODE, mode.id(), true);
            entityData.set(
                    DISGUISE_ID, Optional.of(entityId), true);
            entityData.set(DISGUISE_PLAYER, Optional.empty(), true);
            entityData.set(DISGUISE_PLAYER_NAME, "", true);
            restoreScalePending = true;
        }
        else if (mode == DisguiseMode.PLAYER
                && playerId != null
                && !playerName.isBlank()) {
            entityData.set(DISGUISE_MODE, mode.id(), true);
            entityData.set(DISGUISE_ID, Optional.empty(), true);
            entityData.set(
                    DISGUISE_PLAYER,
                    Optional.of(playerId),
                    true);
            entityData.set(
                    DISGUISE_PLAYER_NAME, playerName, true);
            restoreScalePending = true;
        }
        else {
            entityData.set(
                    DISGUISE_MODE, DisguiseMode.NONE.id(), true);
            entityData.set(DISGUISE_ID, Optional.empty(), true);
            entityData.set(DISGUISE_PLAYER, Optional.empty(), true);
            entityData.set(DISGUISE_PLAYER_NAME, "", true);
            restoreScalePending = false;
        }
        entityData.set(
                STATE_CHANGED_TICK,
                tag.contains(TAG_CHANGED, Tag.TAG_INT)
                        ? tag.getInt(TAG_CHANGED)
                        : tickCount,
                true);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide()) {
            clearForLifecycle();
        }
        super.remove(reason);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return false;
    }

    private void applyScale(EntityDimensions dimensions) {
        LivingEntity user = getUser();
        DisguiseScaleService.apply(user, dimensions);
        if (user instanceof Player player) {
            DisguiseRetaliationService.clearNearby(player);
        }
    }

    private void restorePersistedScale() {
        DisguiseMode mode = getDisguiseMode();
        EntityDimensions dimensions = null;
        if (mode == DisguiseMode.ENTITY) {
            ResourceLocation id = getDisguiseId().orElse(null);
            EntityType<?> type = DisguisePolicy.entityType(
                    id, level());
            if (type != null) {
                dimensions = type.getDimensions();
            }
        }
        else if (mode == DisguiseMode.PLAYER
                && getDisguisePlayer().isPresent()) {
            dimensions = EntityType.PLAYER.getDimensions();
        }
        if (dimensions == null) {
            clearDisguise();
            return;
        }
        applyScale(dimensions);
        restoreScalePending = false;
    }

    private void finishSelection() {
        restoreScalePending = false;
        markChanged();
        cancelSelection();
    }

    private void markChanged() {
        entityData.set(STATE_CHANGED_TICK, tickCount, true);
    }

    private void cancelSelection() {
        pendingMode = DisguiseMode.NONE;
        selectionExpiresAt = Long.MIN_VALUE;
    }
}
