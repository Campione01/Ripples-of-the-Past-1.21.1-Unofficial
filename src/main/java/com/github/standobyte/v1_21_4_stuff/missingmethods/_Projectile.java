package com.github.standobyte.v1_21_4_stuff.missingmethods;

import java.util.function.Consumer;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;

public class _Projectile {

    public static <T extends Projectile> T spawnProjectileFromRotation(
        _Projectile.ProjectileFactory<T> factory,
        ServerLevel level,
        ItemStack spawnedFrom,
        LivingEntity owner,
        float z,
        float velocity,
        float inaccuracy
    ) {
        return spawnProjectile(
            factory.create(level, owner, spawnedFrom),
            level,
            spawnedFrom,
            p_390281_ -> p_390281_.shootFromRotation(owner, owner.getXRot(), owner.getYRot(), z, velocity, inaccuracy)
        );
    }

    public static <T extends Projectile> T spawnProjectileUsingShoot(
        _Projectile.ProjectileFactory<T> factory,
        ServerLevel level,
        ItemStack spawnedFrom,
        LivingEntity owner,
        double x,
        double y,
        double z,
        float velocity,
        float inaccuracy
    ) {
        return spawnProjectile(
            factory.create(level, owner, spawnedFrom),
            level,
            spawnedFrom,
            p_359978_ -> p_359978_.shoot(x, y, z, velocity, inaccuracy)
        );
    }

    public static <T extends Projectile> T spawnProjectileUsingShoot(
        T projectile, ServerLevel level, ItemStack spawnedFrom, double x, double y, double z, float velocity, float inaccuracy
    ) {
        return spawnProjectile(projectile, level, spawnedFrom, p_359970_ -> projectile.shoot(x, y, z, velocity, inaccuracy));
    }

    public static <T extends Projectile> T spawnProjectile(T projectile, ServerLevel level, ItemStack spawnedFrom) {
        return spawnProjectile(projectile, level, spawnedFrom, p_359984_ -> {
        });
    }

    public static <T extends Projectile> T spawnProjectile(T projectile, ServerLevel level, ItemStack stack, Consumer<T> adapter) {
        adapter.accept(projectile);
        level.addFreshEntity(projectile);
        return projectile;
    }

    @FunctionalInterface
    public interface ProjectileFactory<T extends Projectile> {
        T create(ServerLevel level, LivingEntity owner, ItemStack spawnedFrom);
    }
}
