package rotp.addons.d4c.entity;

import java.util.Optional;
import java.util.UUID;

import rotp.addons.d4c.D4CAddon;
import rotp.addons.d4c.D4CPhase2BSpec;
import rotp.addons.d4c.D4CRuntimeRules;
import rotp.addons.d4c.travel.D4CDimensionTravelService;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class D4CMarkerEntity extends Entity {
    public enum Mode {
        FLAG,
        PULL,
        KICK_FLAG,
        KICK_ORIGIN
    }

    private static final EntityDataAccessor<Optional<UUID>> OWNER =
            SynchedEntityData.defineId(
                    D4CMarkerEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> TARGET =
            SynchedEntityData.defineId(
                    D4CMarkerEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Byte> MODE =
            SynchedEntityData.defineId(
                    D4CMarkerEntity.class,
                    EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> AGE =
            SynchedEntityData.defineId(
                    D4CMarkerEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> PORTAL_PLACED =
            SynchedEntityData.defineId(
                    D4CMarkerEntity.class,
                    EntityDataSerializers.BOOLEAN);

    private BlockPos pullPosition;
    private ResourceLocation pullDestination;

    public D4CMarkerEntity(
            EntityType<? extends D4CMarkerEntity> type,
            Level level) {
        super(type, level);
        noPhysics = true;
    }

    public void initialize(
            ServerPlayer owner,
            Entity target,
            Mode mode) {
        entityData.set(OWNER, Optional.of(owner.getUUID()));
        entityData.set(
                TARGET,
                Optional.of((target != null ? target : owner).getUUID()));
        entityData.set(MODE, (byte) mode.ordinal());
        entityData.set(AGE, 0);
        entityData.set(PORTAL_PLACED, false);
        pullPosition = null;
        pullDestination = null;
        followAnchor(target != null ? target : owner);
        if (mode == Mode.PULL && target != null
                && level() instanceof ServerLevel sourceLevel) {
            pullPosition = target.blockPosition().immutable();
            pullDestination = D4CDimensionTravelService.resolvePulledDestination(
                    owner, sourceLevel);
        }
    }

    // 1.16 ag.af type 1 with no anchor: a kick flag that stays put
    public void initializeFixed(
            ServerPlayer owner, double x, double y, double z) {
        entityData.set(OWNER, Optional.of(owner.getUUID()));
        entityData.set(TARGET, Optional.empty());
        entityData.set(MODE, (byte) Mode.KICK_FLAG.ordinal());
        entityData.set(AGE, 0);
        entityData.set(PORTAL_PLACED, false);
        setPos(x, y, z);
    }

    public Mode mode() {
        int id = Byte.toUnsignedInt(entityData.get(MODE));
        return Mode.values()[Math.min(id, Mode.values().length - 1)];
    }

    public float animationProgress(float partialTick) {
        return net.minecraft.util.Mth.clamp(
                (entityData.get(AGE) + partialTick)
                        / animationDurationTicks(),
                0.0F,
                1.0F);
    }

    public int animationDurationTicks() {
        return mode() == Mode.KICK_FLAG ? 38 : 31;
    }

    public int ageTicks() {
        return entityData.get(AGE);
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        builder.define(OWNER, Optional.empty());
        builder.define(TARGET, Optional.empty());
        builder.define(MODE, (byte) 0);
        builder.define(AGE, 0);
        builder.define(PORTAL_PLACED, false);
    }

    @Override
    public void tick() {
        super.tick();
        int age = entityData.get(AGE) + 1;
        entityData.set(AGE, age);
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        ServerPlayer owner = entityData.get(OWNER)
                .map(serverLevel.getServer()
                        .getPlayerList()::getPlayer)
                .orElse(null);
        // players outside the player list (fake players, GameTest mocks)
        if (owner == null
                && entityData.get(OWNER)
                        .map(serverLevel::getPlayerByUUID)
                        .orElse(null) instanceof ServerPlayer present) {
            owner = present;
        }
        Entity targetEntity = entityData.get(TARGET)
                .map(serverLevel::getEntity)
                .orElse(null);
        if (mode() == Mode.KICK_FLAG
                || mode() == Mode.KICK_ORIGIN) {
            if (targetEntity != null && targetEntity.isAlive()) {
                followAnchor(targetEntity);
            }
            if (D4CRuntimeRules.shouldDiscardFlag(
                    age, animationDurationTicks())) {
                discard();
            } else if (age % 3 == 0) {
                serverLevel.sendParticles(
                        D4CAddon.GOLD_LIGHT.get(),
                        getX(),
                        getY() + 0.8D,
                        getZ(),
                        3,
                        0.2D,
                        0.35D,
                        0.2D,
                        0.01D);
            }
            return;
        }
        if (mode() == Mode.PULL) {
            tickPull(serverLevel, owner, targetEntity, age);
            return;
        }
        if (owner == null
                || targetEntity == null
                || !targetEntity.isAlive()) {
            discard();
            return;
        }
        followAnchor(targetEntity);
        if (age % 4 == 0) {
            serverLevel.sendParticles(
                    D4CAddon.GOLD_LIGHT.get(),
                    getX(),
                    getY() + 0.6D,
                    getZ(),
                    5,
                    0.25D,
                    0.45D,
                    0.25D,
                    0.01D);
        }
        if (age < D4CPhase2BSpec.PORTAL_ARM_TICKS) {
            return;
        }
        if (entityData.get(PORTAL_PLACED)) {
            if (D4CRuntimeRules.shouldDiscardFlag(
                    age, animationDurationTicks())) {
                discard();
            }
            return;
        }
        if (age > D4CPhase2BSpec.PORTAL_LIFESPAN_TICKS
                || distanceToSqr(targetEntity) > 9.0D) {
            discard();
            return;
        }
        boolean placed = D4CDimensionTravelService.placeSelectedPortal(
                owner, targetEntity);
        if (placed) {
            entityData.set(PORTAL_PLACED, true);
            serverLevel.playSound(
                    null,
                    blockPosition(),
                    D4CAddon.D4C_TELEPORT.get(),
                    SoundSource.PLAYERS,
                    1.0F,
                    1.0F);
        }
        if (D4CRuntimeRules.shouldDiscardFlag(
                age, animationDurationTicks())) {
            discard();
        }
    }

    private void tickPull(
            ServerLevel sourceLevel, ServerPlayer owner,
            Entity targetEntity, int age) {
        if (targetEntity != null) {
            pullPosition = targetEntity.blockPosition().immutable();
            followAnchor(targetEntity);
        }
        if (owner != null) {
            ResourceLocation selected = D4CDimensionTravelService.resolvePulledDestination(
                    owner, sourceLevel);
            if (selected != null) {
                pullDestination = selected;
            }
        }
        if (age % 4 == 0) {
            sourceLevel.sendParticles(
                    D4CAddon.GOLD_LIGHT.get(), getX(), getY() + 0.6D, getZ(),
                    5, 0.25D, 0.45D, 0.25D, 0.01D);
        }
        // Once committed, 1.16 aa/aj completes even if its captured target dies.
        // An absent owner uses the last observed selection, not invented history.
        if (age >= D4CPhase2BSpec.PORTAL_ARM_TICKS
                && age <= D4CPhase2BSpec.PORTAL_LIFESPAN_TICKS
                && !entityData.get(PORTAL_PLACED)
                && pullPosition != null && pullDestination != null
                && D4CDimensionTravelService.placePulledPortal(
                        sourceLevel, pullPosition, pullDestination)) {
            entityData.set(PORTAL_PLACED, true);
            sourceLevel.playSound(
                    null, pullPosition, D4CAddon.D4C_TELEPORT.get(),
                    SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        if (D4CRuntimeRules.shouldDiscardFlag(age, animationDurationTicks())) {
            discard();
        }
    }

    private void followAnchor(Entity anchor) {
        double y = mode() == Mode.KICK_FLAG
                ? anchor.getY() + anchor.getBbHeight() * 0.9D - 4.0D
                : anchor.getY() + 0.9D;
        setPos(anchor.getX(), y, anchor.getZ());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        entityData.get(OWNER).ifPresent(uuid -> tag.putUUID("Owner", uuid));
        entityData.get(TARGET).ifPresent(uuid -> tag.putUUID("Target", uuid));
        tag.putByte("Mode", entityData.get(MODE));
        tag.putInt("Age", entityData.get(AGE));
        tag.putBoolean(
                "PortalPlaced",
                entityData.get(PORTAL_PLACED));
        if (pullPosition != null) {
            tag.putLong("PullPosition", pullPosition.asLong());
        }
        if (pullDestination != null) {
            tag.putString("PullDestination", pullDestination.toString());
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(
                OWNER,
                tag.hasUUID("Owner")
                        ? Optional.of(tag.getUUID("Owner"))
                        : Optional.empty());
        entityData.set(
                TARGET,
                tag.hasUUID("Target")
                        ? Optional.of(tag.getUUID("Target"))
                        : Optional.empty());
        entityData.set(MODE, tag.getByte("Mode"));
        entityData.set(AGE, tag.getInt("Age"));
        entityData.set(
                PORTAL_PLACED,
                tag.getBoolean("PortalPlaced"));
        pullPosition = tag.contains("PullPosition", Tag.TAG_LONG)
                ? BlockPos.of(tag.getLong("PullPosition")) : null;
        pullDestination = ResourceLocation.tryParse(tag.getString("PullDestination"));
    }
}
