package rotp.addons.frosttraveler.entity;

import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;

import rotp.addons.frosttraveler.FrostTravelerAddon;
import rotp.addons.frosttraveler.FrostTravelerDamageTypes;
import rotp.addons.frosttraveler.FrostTravelerTags;
import rotp.addons.frosttraveler.particle.MeatParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class SprayBulletEntity
        extends ModdedProjectileEntity {
    public static final int LIFETIME_TICKS = 20;
    public static final float BASE_DAMAGE = 0.85F;
    public static final double MAX_OWNER_DISTANCE = 10.0D;

    public SprayBulletEntity(
            EntityType<? extends SprayBulletEntity> type,
            Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public SprayBulletEntity(
            LivingEntity shooter, Level level) {
        super(
                FrostTravelerAddon.SPRAY_BULLET_ENTITY.get(),
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
                && entity != getOwner()
                && !entity.getType().is(
                        FrostTravelerTags.NO_MEATABLE);
    }

    @Override
    public int ticksLifespan() {
        return LIFETIME_TICKS;
    }

    @Override
    protected float getBaseDamage() {
        return BASE_DAMAGE;
    }

    @Override
    protected ResourceKey<DamageType> getDamageTypeKey() {
        return FrostTravelerDamageTypes.CS_ROTTEN;
    }

    @Override
    protected float getMaxHardnessBreakable() {
        return 0.0F;
    }

    @Override
    protected float knockbackMultiplier() {
        return 0.05F;
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
