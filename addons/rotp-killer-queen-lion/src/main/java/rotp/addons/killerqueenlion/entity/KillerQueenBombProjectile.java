package rotp.addons.killerqueenlion.entity;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.killerqueenlion.KillerQueenLionAddon;

import rotp.addons.killerqueenlion.state.KillerQueenAuthority;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

public abstract class KillerQueenBombProjectile
        extends ModdedProjectileEntity {
    public static final int LIFESPAN_TICKS = 100;
    public static final float DIRECT_DAMAGE = 14.0F;
    public static final float IMPACT_EXPLOSION_RADIUS = 1.0F;
    public static final float BLOCK_EXPLOSION_RADIUS = 2.0F;
    public static final float MANUAL_EXPLOSION_RADIUS = 2.0F;

    private static final EntityDataAccessor<Optional<UUID>>
            CONTROLLER_UUID = SynchedEntityData.defineId(
                    KillerQueenBombProjectile.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>>
            STAND_UUID = SynchedEntityData.defineId(
                    KillerQueenBombProjectile.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Long> EXPIRES_AT =
            SynchedEntityData.defineId(
                    KillerQueenBombProjectile.class,
                    EntityDataSerializers.LONG);

    private String boundDimension = "";

    protected KillerQueenBombProjectile(
            EntityType<? extends KillerQueenBombProjectile> type,
            Level level) {
        super(type, level);
    }

    protected KillerQueenBombProjectile(
            EntityType<? extends KillerQueenBombProjectile> type,
            ServerPlayer owner,
            @Nullable StandEntity stand,
            Level level) {
        super(type, owner, level);
        bind(owner, stand);
    }

    public final UUID getControllerUuid() {
        return entityData.get(CONTROLLER_UUID).orElse(null);
    }

    public final UUID getStandUuid() {
        return entityData.get(STAND_UUID).orElse(null);
    }

    public final boolean isOwnedBy(UUID ownerUuid) {
        return ownerUuid != null
                && entityData.get(CONTROLLER_UUID)
                        .filter(ownerUuid::equals)
                        .isPresent();
    }

    // 1.16 SnowBomb / HBubbleBomb were StandActions of the user: a bomb is
    // launched with or without Killer Queen out; one launched with the Stand
    // out stays bound to it, one launched without it to its owner alone.
    private void bind(
            ServerPlayer owner, @Nullable StandEntity stand) {
        if (!(level() instanceof ServerLevel level)
                || !(stand != null
                        ? KillerQueenAuthority.validController(
                                owner, stand)
                        : ownerControls(level, owner.getUUID()))) {
            discard();
            return;
        }
        entityData.set(
                CONTROLLER_UUID,
                Optional.of(owner.getUUID()),
                true);
        entityData.set(
                STAND_UUID,
                Optional.ofNullable(
                        stand != null ? stand.getUUID() : null),
                true);
        entityData.set(
                EXPIRES_AT,
                level.getGameTime() + LIFESPAN_TICKS,
                true);
        boundDimension = level.dimension().location().toString();
        if (stand != null) {
            withStandSkin(stand.getStandType(), stand.getStandSkin());
        } else {
            StandPower power = StandPower.get(owner);
            if (power != null && power.getPowerType() != null) {
                withStandSkin(
                        power.getPowerType().getId(),
                        power.getSelectedSkin());
            }
        }
    }

    @Override
    public void tick() {
        if (level() instanceof ServerLevel level
                && !isAuthorized(level)) {
            discard();
            return;
        }
        super.tick();
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        if (level() instanceof ServerLevel
                && !isRemoved()) {
            Entity target = hit.getEntity();
            explodeAt(
                    this instanceof SnowBombEntity ? target : this,
                    target.getX(),
                    target.getY(),
                    target.getZ(),
                    IMPACT_EXPLOSION_RADIUS);
        }
        super.onHitEntity(hit);
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        if (level() instanceof ServerLevel
                && !isRemoved()) {
            explodeAt(
                    this,
                    getX(),
                    getY(),
                    getZ(),
                    BLOCK_EXPLOSION_RADIUS);
        }
        super.onHitBlock(hit);
    }

    public final void manualDetonate() {
        if (level() instanceof ServerLevel
                && !isRemoved()) {
            explodeAt(
                    getOwner(),
                    getX(),
                    getY(),
                    getZ(),
                    MANUAL_EXPLOSION_RADIUS);
            discard();
        }
    }

    private void explodeAt(
            Entity source, double x, double y, double z, float radius) {
        level().explode(
                source,
                x,
                y,
                z,
                radius,
                Level.ExplosionInteraction.NONE);
    }

    private boolean isAuthorized(ServerLevel level) {
        UUID ownerUuid = getControllerUuid();
        UUID standUuid = getStandUuid();
        return ownerUuid != null
                && level.getGameTime()
                        < entityData.get(EXPIRES_AT)
                && level.dimension().location().toString()
                        .equals(boundDimension)
                && isFlightControllerValid(
                        level, ownerUuid, standUuid);
    }

    protected boolean isFlightControllerValid(
            ServerLevel level, UUID ownerUuid, @Nullable UUID standUuid) {
        return standUuid != null
                ? KillerQueenAuthority.validController(
                        level, ownerUuid, standUuid)
                : ownerControls(level, ownerUuid);
    }

    protected final boolean ownerControls(
            ServerLevel level, UUID ownerUuid) {
        Entity controller = level.getEntity(ownerUuid);
        return controller instanceof ServerPlayer player
                && player.isAlive()
                && !player.isRemoved()
                && player.level() == level
                && KillerQueenLionAddon.isKillerQueenUser(player);
    }

    @Override
    protected float getBaseDamage() {
        return DIRECT_DAMAGE;
    }

    @Override
    protected DamageSource getDamageSource(LivingEntity owner) {
        // The donor's direct hit uses ordinary explosion armor and hurt cooldown.
        return damageSources().explosion(owner, owner);
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
        return LIFESPAN_TICKS;
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
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
