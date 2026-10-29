package rotp.addons.kraftwork.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.kraftwork.KraftWorkAddon;
import rotp.addons.kraftwork.KraftWorkSpec;
import rotp.addons.kraftwork.state.KraftWorkLockService;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class KraftWorkBlockMarkerEntity extends Entity {
    private static final String OWNER_TAG = "Owner";
    @Nullable
    private UUID ownerUuid;

    public KraftWorkBlockMarkerEntity(
            EntityType<? extends KraftWorkBlockMarkerEntity> type,
            Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
    }

    public void initialize(
            LivingEntity owner, BlockPos blockPos) {
        ownerUuid = owner.getUUID();
        setPos(
                blockPos.getX() + 0.5D,
                blockPos.getY(),
                blockPos.getZ() + 0.5D);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity owner =
                KraftWorkLockService.resolveOwner(level, ownerUuid);
        if (owner == null
                || !KraftWorkAddon.isKraftWorkUser(owner)
                || level.isEmptyBlock(blockPosition())
                || !withinLockRange(owner, blockPosition())) {
            discard();
            return;
        }
        AABB block = new AABB(blockPosition());
        for (LivingEntity target :
                level.getEntitiesOfClass(
                        LivingEntity.class,
                        block,
                        entity -> entity != owner
                                && entity.isAlive()
                                && !(entity instanceof Creeper creeper
                                        && creeper.isIgnited()))) {
            KraftWorkLockService.addPositionLockEffect(
                    target, 3);
        }
    }

    // 1.16 KWBlockEntity kept a block lock only while its user stood within
    // sqrt(140), about 11.8 blocks, feet to the marker's bottom centre.
    public static boolean withinLockRange(
            Entity owner, BlockPos blockPos) {
        return owner.distanceToSqr(Vec3.atBottomCenterOf(blockPos))
                <= KraftWorkSpec.BLOCK_MARKER_RANGE_SQR;
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUuid != null) {
            tag.putUUID(OWNER_TAG, ownerUuid);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        ownerUuid = tag.hasUUID(OWNER_TAG)
                ? tag.getUUID(OWNER_TAG)
                : null;
    }
}
