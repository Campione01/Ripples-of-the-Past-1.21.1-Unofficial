package rotp.addons.purplehaze.entity;

import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;

import rotp.addons.purplehaze.PurpleHazeAddon;
import rotp.addons.purplehaze.effect.PurpleHazeVirusEffect;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public final class CapsuleProjectileEntity
        extends ModdedProjectileEntity {
    public static final int LIFESPAN_TICKS = 200;

    public CapsuleProjectileEntity(
            EntityType<? extends CapsuleProjectileEntity> type,
            Level level) {
        super(type, level);
    }

    public CapsuleProjectileEntity(
            LivingEntity shooter, Level level) {
        super(
                PurpleHazeAddon.CAPSULE_PROJECTILE.get(),
                shooter,
                level);
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
        return LIFESPAN_TICKS;
    }

    @Override
    protected void afterBlockHit(
            BlockHitResult hit, boolean blockDestroyed) {
        if (level() instanceof ServerLevel serverLevel) {
            spawnCloud(serverLevel, hit.getLocation());
            playSound(
                    PurpleHazeAddon.CAPSULE_EXPLOSION.get(),
                    1.0F,
                    1.0F);
        }
    }

    @Override
    protected void afterEntityHit(
            EntityHitResult hit, boolean entityHurt) {
        if (!(level() instanceof ServerLevel serverLevel)
                || !(hit.getEntity()
                        instanceof LivingEntity target)) {
            return;
        }
        spawnCloud(serverLevel, position());
        PurpleHazeVirusEffect.applyVirusUnattenuated(
                target, 2, 240);
        playSound(
                PurpleHazeAddon.CAPSULE_EXPLOSION.get(),
                1.0F,
                1.0F);
    }

    private void spawnCloud(
            ServerLevel level, Vec3 position) {
        LivingEntity owner = getOwner();
        PurpleHazeEntity controller =
                PurpleHazeAddon.controller(owner);
        if (owner != null) {
            PurpleHazeCloudEntity.spawn(
                    level,
                    owner,
                    controller,
                    position,
                    PurpleHazeCloudEntity.DEFAULT_SPREAD_SPEED);
        }
    }
}
