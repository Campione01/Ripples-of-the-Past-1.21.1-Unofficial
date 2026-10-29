package rotp.addons.speedking.entity;

import java.util.ArrayList;
import java.util.Comparator;

import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;
import rotp.core.subsystems.target.ActionTarget.TargetType;

import rotp.addons.speedking.SpeedKingAddon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class SpeedKingPickaxeEntity
        extends ModdedProjectileEntity implements ItemSupplier {
    private float durability = 100.0F;
    private boolean preventEntityBreaking;

    public SpeedKingPickaxeEntity(
            EntityType<? extends SpeedKingPickaxeEntity> type,
            Level level) {
        super(type, level);
    }

    public SpeedKingPickaxeEntity(LivingEntity shooter, Level level) {
        super(SpeedKingAddon.PICKAXE_ENTITY.get(), shooter, level);
    }

    @Override
    protected float getBaseDamage() {
        return 6.0F;
    }

    @Override
    protected float getMaxHardnessBreakable() {
        return 100.0F;
    }

    @Override
    public boolean standDamage() {
        return true;
    }

    @Override
    public int ticksLifespan() {
        return Integer.MAX_VALUE;
    }

    @Override
    public ItemStack getItem() {
        return new ItemStack(Items.DIAMOND_PICKAXE);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Durability", durability);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        durability = tag.contains("Durability")
                ? tag.getFloat("Durability") : 100.0F;
    }

    @Override
    protected boolean destroyBlock(BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        BlockState state = level().getBlockState(pos);
        if (state.getCollisionShape(level(), pos).isEmpty()) {
            preventEntityBreaking = true;
            return false;
        }
        float hardness = state.getDestroySpeed(level(), pos);
        boolean fitting = !state.requiresCorrectToolForDrops()
                || state.is(BlockTags.MINEABLE_WITH_PICKAXE)
                && !state.is(BlockTags.INCORRECT_FOR_DIAMOND_TOOL);
        if (fitting) {
            hardness *= 0.3F;
        }
        if (hardness >= 0.0F
                && hardness < durability
                && super.destroyBlock(hit)) {
            durability -= hardness;
            preventEntityBreaking = true;
            return true;
        }
        return false;
    }

    @Override
    protected void breakProjectile(
            TargetType targetType, HitResult hitTarget) {
        if (!level().isClientSide()) {
            if (preventEntityBreaking) {
                preventEntityBreaking = false;
                return;
            }
            if (level() instanceof ServerLevel serverLevel) {
                playSound(
                        SoundEvents.ITEM_BREAK,
                        0.8F,
                        0.8F + random.nextFloat() * 0.4F);
                ItemParticleOption particle = new ItemParticleOption(
                        ParticleTypes.ITEM,
                        new ItemStack(Items.DIAMOND_PICKAXE));
                serverLevel.sendParticles(
                        particle,
                        getX(), getY(), getZ(),
                        20,
                        0.5D, 0.5D, 0.5D,
                        0.1D);
            }
        }
        super.breakProjectile(targetType, hitTarget);
    }

    @Override
    protected HitResult[] rayTrace() {
        HitResult[] first = super.rayTrace();
        if (level().isClientSide()
                || first.length > 0
                && first[0].getType() == HitResult.Type.ENTITY) {
            return first;
        }

        AABB swept = getBoundingBox().expandTowards(getDeltaMovement());
        ArrayList<BlockPos> blocks = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(swept.minX, swept.minY, swept.minZ),
                BlockPos.containing(swept.maxX, swept.maxY, swept.maxZ))) {
            BlockState state = level().getBlockState(pos);
            if (!state.isAir()
                    && !state.getCollisionShape(level(), pos).isEmpty()) {
                blocks.add(pos.immutable());
            }
        }
        Vec3 center = position();
        return blocks.stream()
                .sorted(Comparator.comparingDouble(
                        pos -> pos.distToCenterSqr(center.x, center.y, center.z)))
                .map(pos -> {
                    Vec3 blockCenter = Vec3.atCenterOf(pos);
                    Vec3 direction = blockCenter.subtract(center);
                    return new BlockHitResult(
                            blockCenter,
                            Direction.getNearest(
                                    direction.x,
                                    direction.y,
                                    direction.z),
                            pos,
                            false);
                })
                .toArray(HitResult[]::new);
    }
}
