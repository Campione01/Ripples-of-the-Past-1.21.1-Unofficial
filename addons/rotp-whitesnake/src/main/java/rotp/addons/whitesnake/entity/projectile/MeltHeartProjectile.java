package rotp.addons.whitesnake.entity.projectile;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;
import rotp.core.subsystems.target.ActionTarget.TargetType;

import rotp.addons.whitesnake.WhitesnakeAddon;
import rotp.addons.whitesnake.block.MeltHeartBlock;
import rotp.addons.whitesnake.blockentity.MeltHeartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class MeltHeartProjectile
        extends ModdedProjectileEntity {
    private static final String ABILITY_NAME = "melt_your_heart";

    private boolean hitCeiling;
    @Nullable
    private UUID whitesnakeUser;

    public MeltHeartProjectile(
            LivingEntity shooter, Level level) {
        super(
                WhitesnakeAddon.MELT_HEART_PROJECTILE.get(),
                shooter,
                level);
    }

    public MeltHeartProjectile(
            EntityType<? extends MeltHeartProjectile> type,
            Level level) {
        super(type, level);
    }

    public void setWhitesnakeUser(UUID userId) {
        whitesnakeUser = userId;
    }

    @Override
    public void tick() {
        if (!level().isClientSide()
                && !hasValidWhitesnakeOwner()) {
            discard();
            return;
        }
        super.tick();
        if (!isAlive()) {
            return;
        }
        float progress = Mth.clamp(
                (float) tickCount / (ticksLifespan() / 4.0F),
                0.0F,
                1.0F);
        Vec3 movement = getDeltaMovement();
        if (!hitCeiling) {
            setDeltaMovement(
                    movement.x,
                    2.0D - Math.exp(progress),
                    movement.z);
        }
    }

    private boolean hasValidWhitesnakeOwner() {
        if (!(level() instanceof ServerLevel serverLevel)
                || whitesnakeUser == null) {
            return false;
        }
        LivingEntity user = findUser(serverLevel, whitesnakeUser);
        return user != null
                && user.level() == serverLevel
                && WhitesnakeAddon.isActiveWhitesnakeUser(
                        user, ABILITY_NAME);
    }

    @Nullable
    private static LivingEntity findUser(
            ServerLevel level, UUID userId) {
        Entity entity = level.getEntity(userId);
        if (entity instanceof LivingEntity living) {
            return living;
        }
        ServerPlayer player =
                level.getServer().getPlayerList().getPlayer(userId);
        return player;
    }

    @Override
    protected void breakProjectile(
            TargetType targetType, HitResult hitTarget) {
        if (targetType == TargetType.ENTITY) {
            return;
        }

        BlockPos position = blockPosition();
        Vec3 movement = getDeltaMovement();
        if (!level().getBlockState(position.above()).isAir()) {
            hitCeiling = true;
        }
        BlockState belowState =
                level().getBlockState(position.below());
        if (!belowState.isAir()) {
            super.breakProjectile(targetType, hitTarget);
        }
        if (hitCeiling) {
            setDeltaMovement(
                    movement.x * 1.5D,
                    -1.0D,
                    movement.z * 1.5D);
        }

        if (level() instanceof ServerLevel serverLevel
                && whitesnakeUser != null
                && !belowState.isAir()
                && !belowState.is(
                        WhitesnakeAddon.MELT_HEART_BLOCK.get())
                && level().getBlockState(position).isAir()) {
            int layers = getRandom().nextFloat() < 0.1F ? 2 : 1;
            BlockState state = WhitesnakeAddon.MELT_HEART_BLOCK
                    .get()
                    .defaultBlockState()
                    .setValue(MeltHeartBlock.LAYERS, layers);
            if (serverLevel.setBlockAndUpdate(position, state)
                    && serverLevel.getBlockEntity(position)
                            instanceof MeltHeartBlockEntity blockEntity) {
                blockEntity.initialize(whitesnakeUser);
            }
        }
    }

    @Override
    protected boolean constVelocity() {
        return true;
    }

    @Override
    protected boolean hasGravity() {
        return true;
    }

    @Override
    protected double getGravityAcceleration() {
        return 0.1D;
    }

    @Override
    protected double getInertia() {
        return 0.1D;
    }

    @Override
    public int ticksLifespan() {
        return 100;
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
        return false;
    }
}
