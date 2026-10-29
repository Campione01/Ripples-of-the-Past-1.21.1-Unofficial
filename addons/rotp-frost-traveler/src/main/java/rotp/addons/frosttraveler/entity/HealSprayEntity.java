package rotp.addons.frosttraveler.entity;

import javax.annotation.Nullable;

import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;
import rotp.core.subsystems.target.ActionTarget.TargetType;

import rotp.addons.frosttraveler.FrostTravelerAddon;
import rotp.addons.frosttraveler.FrostTravelerTags;
import rotp.addons.frosttraveler.item.SprayerData;
import rotp.addons.frosttraveler.particle.MeatParticleOptions;
import rotp.addons.frosttraveler.state.CreamStarterService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Stray;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.HitResult;

public final class HealSprayEntity
        extends ModdedProjectileEntity {
    public static final int LIFETIME_TICKS = 20;
    public static final double MAX_OWNER_DISTANCE = 9.0D;
    public static final float HEAL_AMOUNT = 0.5F;
    public static final int SKELETON_CONVERSION_TICKS = 10;
    public static final int HORSE_CONVERSION_TICKS = 30;

    public HealSprayEntity(
            EntityType<? extends HealSprayEntity> type,
            Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public HealSprayEntity(
            LivingEntity shooter, Level level) {
        super(
                FrostTravelerAddon.HEAL_SPRAY_ENTITY.get(),
                shooter,
                level);
        setNoGravity(true);
    }

    @Override
    public void tick() {
        super.tick();
        LivingEntity owner = getOwner();
        MeatParticleOptions.spawnTrail(level(), this, owner);
        if (!level().isClientSide()
                && (owner == null
                        || distanceToSqr(owner)
                                > MAX_OWNER_DISTANCE
                                        * MAX_OWNER_DISTANCE)) {
            discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity)
                && entity != getOwner();
    }

    @Override
    protected void breakProjectile(
            TargetType targetType, @Nullable HitResult hitTarget) {
        if (targetType == TargetType.BLOCK
                || targetType == TargetType.ENTITY) {
            super.breakProjectile(targetType, hitTarget);
        }
    }

    @Override
    protected boolean hurtTarget(
            Entity target, @Nullable LivingEntity owner) {
        if (!(target instanceof LivingEntity living)
                || level().isClientSide()) {
            return false;
        }
        if (owner instanceof Player player
                && (living instanceof SkeletonHorse
                        || living instanceof Skeleton
                        || living instanceof Stray)) {
            int required = living instanceof SkeletonHorse
                    ? HORSE_CONVERSION_TICKS
                    : SKELETON_CONVERSION_TICKS;
            if (transformationReady(player, required)) {
                convertSkeleton(living);
            }
            return false;
        }
        if (living.getType().is(FrostTravelerTags.NO_MEATABLE)) {
            return false;
        }
        living.heal(HEAL_AMOUNT);
        return false;
    }

    private static boolean transformationReady(
            Player owner, int requiredTicks) {
        ItemStack stack =
                CreamStarterService.heldOwnedSprayer(owner);
        SprayerData data = stack.get(
                FrostTravelerAddon.SPRAYER_DATA);
        if (data == null) {
            return false;
        }
        if (data.transformationTicks() >= requiredTicks) {
            return true;
        }
        stack.set(
                FrostTravelerAddon.SPRAYER_DATA,
                data.withTransformationTicks(
                        data.transformationTicks() + 1));
        return false;
    }

    private void convertSkeleton(LivingEntity source) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        LivingEntity replacement = source instanceof SkeletonHorse
                ? EntityType.ZOMBIE_HORSE.create(serverLevel)
                : EntityType.ZOMBIE.create(serverLevel);
        if (replacement == null) {
            return;
        }
        replacement.moveTo(
                source.getX(),
                source.getY(),
                source.getZ(),
                source.getYRot(),
                source.getXRot());
        replacement.setCustomName(source.getCustomName());
        replacement.setCustomNameVisible(
                source.isCustomNameVisible());
        replacement.setHealth(Math.min(
                replacement.getMaxHealth(),
                replacement.getMaxHealth()
                        * source.getHealth()
                        / Math.max(1.0F, source.getMaxHealth())));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack equipment = source.getItemBySlot(slot);
            if (!equipment.isEmpty()) {
                replacement.setItemSlot(slot, equipment.copy());
                source.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
        if (source instanceof AbstractHorse sourceHorse
                && replacement
                        instanceof AbstractHorse replacementHorse) {
            replacementHorse.setTamed(true);
            replacementHorse.setOwnerUUID(
                    sourceHorse.getOwnerUUID());
            if (sourceHorse.isSaddled()) {
                replacementHorse.equipSaddle(
                        new ItemStack(Items.SADDLE),
                        SoundSource.NEUTRAL);
            }
        }
        serverLevel.addFreshEntity(replacement);
        source.discard();
    }

    @Override
    public int ticksLifespan() {
        return LIFETIME_TICKS;
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

    @Override
    protected boolean constVelocity() {
        return true;
    }

    @Override
    protected boolean hasGravity() {
        return false;
    }
}
