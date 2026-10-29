package rotp.addons.yellowtemperance.entity;

import java.util.Optional;
import java.util.UUID;

import rotp.core.init.ModEntityDataSerializers;
import rotp.core.init.ModSpecialActions;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.yellowtemperance.YellowTemperanceAddon;
import rotp.addons.yellowtemperance.disguise.DisguiseMode;
import rotp.addons.yellowtemperance.disguise.DisguiseRetaliationService;
import rotp.addons.yellowtemperance.disguise.DisguiseScaleService;
import net.minecraft.nbt.CompoundTag;
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

public final class YellowTemperanceControllerEntity
        extends StandEntity {
    public static final long SELECTION_WINDOW_TICKS = 600L;
    public static final long REQUEST_INTERVAL_TICKS = 5L;
    /**
     * The 1.16 doll toggle lived in the user's {@code LivingData} capability (saved with the player and copied on
     * death), so it survived unsummoning, relogging and respawning; the port keeps the same per-player preference
     * in {@link Player#PERSISTED_NBT_TAG} and mirrors it onto the controller.
     */
    public static final String DOLL_PREFERENCE =
            "rotp_yellowtemperance.doll_rendering";

    private static final EntityDataAccessor<Byte> DISGUISE_MODE =
            SynchedEntityData.defineId(
                    YellowTemperanceControllerEntity.class,
                    EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Optional<ResourceLocation>>
            DISGUISE_ID = SynchedEntityData.defineId(
                    YellowTemperanceControllerEntity.class,
                    ModEntityDataSerializers
                            .RESOURCE_LOCATION_OPTIONAL.get());
    private static final EntityDataAccessor<Optional<UUID>>
            DISGUISE_PLAYER = SynchedEntityData.defineId(
                    YellowTemperanceControllerEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> DISGUISE_PLAYER_NAME =
            SynchedEntityData.defineId(
                    YellowTemperanceControllerEntity.class,
                    EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DOLL =
            SynchedEntityData.defineId(
                    YellowTemperanceControllerEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> BLOCKING =
            SynchedEntityData.defineId(
                    YellowTemperanceControllerEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> STATE_CHANGED_TICK =
            SynchedEntityData.defineId(
                    YellowTemperanceControllerEntity.class,
                    EntityDataSerializers.INT);

    private DisguiseMode pendingMode = DisguiseMode.NONE;
    private long selectionExpiresAt = Long.MIN_VALUE;
    private long lastRequestAt = Long.MIN_VALUE;
    private boolean dollPreferenceApplied;

    public YellowTemperanceControllerEntity(
            EntityType<? extends YellowTemperanceControllerEntity> type,
            Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setInvisible(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DISGUISE_MODE, DisguiseMode.NONE.id());
        builder.define(DISGUISE_ID, Optional.empty());
        builder.define(DISGUISE_PLAYER, Optional.empty());
        builder.define(DISGUISE_PLAYER_NAME, "");
        builder.define(DOLL, false);
        builder.define(BLOCKING, false);
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

    public boolean isDollEnabled() {
        return entityData.get(DOLL);
    }

    public boolean isYellowBlocking() {
        return entityData.get(BLOCKING);
    }

    public int getStateChangedTick() {
        return entityData.get(STATE_CHANGED_TICK);
    }

    public boolean isUnsummoning() {
        EntityActionInstance action = getCurStandAction();
        return action != null
                && action.ability == ModSpecialActions.STAND_UNSUMMON.get();
    }

    public void setDollEnabled(boolean enabled) {
        if (!level().isClientSide()) {
            entityData.set(DOLL, enabled, true);
            markChanged();
            if (getUser() instanceof Player player) {
                CompoundTag persisted = player.getPersistentData()
                        .getCompound(Player.PERSISTED_NBT_TAG);
                persisted.putBoolean(DOLL_PREFERENCE, enabled);
                player.getPersistentData().put(
                        Player.PERSISTED_NBT_TAG, persisted);
            }
        }
    }

    private void applyStoredDollPreference(LivingEntity user) {
        dollPreferenceApplied = true;
        boolean stored = user instanceof Player player
                && player.getPersistentData()
                        .getCompound(Player.PERSISTED_NBT_TAG)
                        .getBoolean(DOLL_PREFERENCE);
        if (stored != isDollEnabled()) {
            entityData.set(DOLL, stored, true);
            markChanged();
        }
    }

    public void setYellowBlocking(boolean active) {
        if (!level().isClientSide()) {
            entityData.set(BLOCKING, active, true);
        }
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

    public void applyEntityOrStandDisguise(
            DisguiseMode mode,
            ResourceLocation id,
            EntityDimensions dimensions) {
        if (level().isClientSide()
                || !canAcceptSelection(
                        mode, level().getGameTime())) {
            return;
        }
        entityData.set(DISGUISE_MODE, mode.id(), true);
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
        refreshUserDisplayName(); // chat shows the disguise name
        applyScale(target.getType().getDimensions());
        finishSelection();
    }

    public void clearDisguise() {
        if (level().isClientSide()) {
            return;
        }
        LivingEntity user = getUser();
        DisguiseScaleService.reset(user);
        if (isDisguised()) {
            boolean namedAsPlayer = getDisguiseMode() == DisguiseMode.PLAYER;
            entityData.set(
                    DISGUISE_MODE, DisguiseMode.NONE.id(), true);
            entityData.set(DISGUISE_ID, Optional.empty(), true);
            entityData.set(
                    DISGUISE_PLAYER, Optional.empty(), true);
            entityData.set(DISGUISE_PLAYER_NAME, "", true);
            markChanged();
            if (namedAsPlayer) {
                refreshUserDisplayName(); // chat shows the real name again
            }
        }
        cancelSelection();
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
                clearServerState();
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
                    || power.getPowerType()
                            != YellowTemperanceAddon
                                    .YELLOW_TEMPERANCE.get()
                    || power.getSummonedStandEntity() != this) {
                clearServerState();
                return;
            }
            // 1.16 YellowTemperanceEntity.tick :44-46 took the protection off
            // in the Stand's own tick on every tick of the unsummon, so
            // entities ticking after the Stand met none until the player's
            // tick put a held guard back
            if (isUnsummoning()) {
                user.removeEffect(YellowTemperanceAddon.PROTECTION);
            }
            // 1.16 AbstractDisguiseStandEntity.tick :195-196: the unsummon's
            // first tick took an entity disguise off; a player or Stand
            // disguise alone stayed until remove()
            if (isUnsummoning()
                    && getDisguiseMode() == DisguiseMode.ENTITY) {
                clearDisguise();
            }
            if (!dollPreferenceApplied) {
                applyStoredDollPreference(user);
            }
            if (selectionExpiresAt != Long.MIN_VALUE
                    && level().getGameTime() > selectionExpiresAt) {
                cancelSelection();
            }
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide()) {
            clearServerState();
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

    private void finishSelection() {
        markChanged();
        cancelSelection();
    }

    private void markChanged() {
        entityData.set(STATE_CHANGED_TICK, tickCount, true);
    }

    // Recomputes the cached display name through PlayerEvent.NameFormat.
    private void refreshUserDisplayName() {
        if (getUser() instanceof Player player) {
            player.refreshDisplayName();
        }
    }

    private void cancelSelection() {
        pendingMode = DisguiseMode.NONE;
        selectionExpiresAt = Long.MIN_VALUE;
    }

    private void clearServerState() {
        LivingEntity user = getUser();
        setYellowBlocking(false);
        // Only the controller's view is reset; the user's stored doll preference is kept.
        entityData.set(DOLL, false);
        dollPreferenceApplied = false;
        clearDisguise();
        if (user != null) {
            user.removeEffect(YellowTemperanceAddon.PROTECTION);
        }
        if (user instanceof Player player) {
            DisguiseRetaliationService.clear(player);
        }
    }
}
