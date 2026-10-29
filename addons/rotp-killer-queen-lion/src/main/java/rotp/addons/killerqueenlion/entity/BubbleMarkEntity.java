package rotp.addons.killerqueenlion.entity;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.killerqueenlion.KillerQueenLionAddon;
import rotp.addons.killerqueenlion.KillerQueenPhase1Spec;
import rotp.addons.killerqueenlion.state.KillerQueenAuthority;
import rotp.addons.killerqueenlion.state.KillerQueenStateService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

public final class BubbleMarkEntity extends ModdedProjectileEntity {
    private static final EntityDataAccessor<Optional<UUID>> CONTROLLER_UUID =
            SynchedEntityData.defineId(
                    BubbleMarkEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> STAND_UUID =
            SynchedEntityData.defineId(
                    BubbleMarkEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Long> EXPIRES_AT =
            SynchedEntityData.defineId(
                    BubbleMarkEntity.class,
                    EntityDataSerializers.LONG);

    private String boundDimension = "";

    public BubbleMarkEntity(
            EntityType<? extends BubbleMarkEntity> type, Level level) {
        super(type, level);
    }

    // 1.16 BubbleMarkAction was the user's StandAction: a mark is launched
    // with or without Killer Queen out; one launched with the Stand out stays
    // bound to it, one launched without it to its owner alone.
    public BubbleMarkEntity(
            ServerPlayer owner, @Nullable StandEntity stand, Level level) {
        super(KillerQueenLionAddon.BUBBLE_MARK_ENTITY.get(), owner, level);
        if (!(level instanceof ServerLevel serverLevel)
                || !(stand != null
                        ? KillerQueenAuthority.validController(owner, stand)
                        : ownerControls(serverLevel, owner.getUUID()))) {
            discard();
            return;
        }
        entityData.set(CONTROLLER_UUID, Optional.of(owner.getUUID()), true);
        entityData.set(STAND_UUID,
                Optional.ofNullable(stand != null ? stand.getUUID() : null), true);
        entityData.set(
                EXPIRES_AT,
                serverLevel.getGameTime()
                        + KillerQueenPhase1Spec.BUBBLE_MARK_LIFETIME_TICKS,
                true);
        boundDimension = serverLevel.dimension().location().toString();
        if (stand != null) {
            withStandSkin(stand.getStandType(), stand.getStandSkin());
        } else {
            StandPower power = StandPower.get(owner);
            if (power != null && power.getPowerType() != null) {
                withStandSkin(power.getPowerType().getId(), power.getSelectedSkin());
            }
        }
    }

    @Override
    public void tick() {
        if (level() instanceof ServerLevel level
                && !authorized(level)) {
            discard();
            return;
        }
        super.tick();
    }

    @Override
    protected boolean hurtTarget(
            Entity target, @Nullable LivingEntity owner) {
        if (level() instanceof ServerLevel
                && owner instanceof ServerPlayer player
                && target instanceof LivingEntity living
                && living != owner
                && !(living instanceof StandEntity)) {
            KillerQueenStateService.markPrimaryBomb(player, living);
        } else if (level() instanceof ServerLevel
                && owner instanceof ServerPlayer player
                && !(target instanceof LivingEntity)) {
            KillerQueenStateService.clearPrimaryBomb(player);
        }
        discard();
        return false;
    }

    @Override
    protected void afterBlockHit(
            BlockHitResult hitResult, boolean blockDestroyed) {
        if (level() instanceof ServerLevel level) {
            UUID controllerUuid = getControllerUuid();
            ServerPlayer owner = controllerUuid != null
                    ? level.getServer().getPlayerList().getPlayer(controllerUuid)
                    : null;
            if (owner != null) {
                KillerQueenStateService.markBubbleBlock(
                        owner, hitResult.getBlockPos());
            }
        }
        discard();
    }

    public UUID getControllerUuid() {
        return entityData.get(CONTROLLER_UUID).orElse(null);
    }

    public UUID getStandUuid() {
        return entityData.get(STAND_UUID).orElse(null);
    }

    public boolean isOwnedBy(UUID ownerUuid) {
        return ownerUuid != null
                && entityData.get(CONTROLLER_UUID)
                        .filter(ownerUuid::equals)
                        .isPresent();
    }

    private boolean authorized(ServerLevel level) {
        UUID ownerUuid = getControllerUuid();
        UUID standUuid = getStandUuid();
        return ownerUuid != null
                && level.getGameTime() < entityData.get(EXPIRES_AT)
                && level.dimension().location().toString().equals(boundDimension)
                && (standUuid != null
                        ? KillerQueenAuthority.validController(level, ownerUuid, standUuid)
                        : ownerControls(level, ownerUuid));
    }

    private static boolean ownerControls(ServerLevel level, UUID ownerUuid) {
        Entity controller = level.getEntity(ownerUuid);
        return controller instanceof ServerPlayer player
                && player.isAlive()
                && !player.isRemoved()
                && player.level() == level
                && KillerQueenLionAddon.isKillerQueenUser(player);
    }

    @Override
    protected float getBaseDamage() {
        return 0.0F;
    }

    @Override
    protected float getMaxHardnessBreakable() {
        return 0.0F;
    }

    @Override
    public boolean standDamage() {
        return true;
    }

    @Override
    public int ticksLifespan() {
        return KillerQueenPhase1Spec.BUBBLE_MARK_LIFETIME_TICKS;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CONTROLLER_UUID, Optional.empty());
        builder.define(STAND_UUID, Optional.empty());
        builder.define(EXPIRES_AT, 0L);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        entityData.get(CONTROLLER_UUID)
                .ifPresent(uuid -> tag.putUUID("Controller", uuid));
        entityData.get(STAND_UUID)
                .ifPresent(uuid -> tag.putUUID("Stand", uuid));
        tag.putLong("ExpiresAt", entityData.get(EXPIRES_AT));
        tag.putString("BoundDimension", boundDimension);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(
                CONTROLLER_UUID,
                tag.hasUUID("Controller")
                        ? Optional.of(tag.getUUID("Controller"))
                        : Optional.empty());
        entityData.set(
                STAND_UUID,
                tag.hasUUID("Stand")
                        ? Optional.of(tag.getUUID("Stand"))
                        : Optional.empty());
        entityData.set(EXPIRES_AT, tag.getLong("ExpiresAt"));
        boundDimension = tag.getString("BoundDimension");
    }
}
