package rotp.core.customobjects.entity_projectile;

import javax.annotation.Nullable;

import net.minecraft.world.entity.Entity;

/** Projectile defense properties independent of the movement and impact implementation. */
public interface IProjectileEvasion {
    boolean canBeEvaded(@Nullable Entity context);

    boolean standDamage();
}
