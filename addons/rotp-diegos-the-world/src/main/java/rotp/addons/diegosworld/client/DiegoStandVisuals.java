package rotp.addons.diegosworld.client;

import javax.annotation.Nullable;

import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.diegosworld.DiegosTheWorldAddon;
import rotp.addons.diegosworld.entity.DiegosWorldStandEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;

final class DiegoStandVisuals {
    private DiegoStandVisuals() {}

    @Nullable
    static StandVisualContext fromProjectile(
            Projectile projectile, float partialTick) {
        Entity owner = projectile.getOwner();
        if (!(owner instanceof LivingEntity livingOwner)) {
            return null;
        }
        StandPower power = StandPower.get(livingOwner);
        if (power == null
                || !power.hasPower()
                || power.getPowerType()
                        != DiegosTheWorldAddon.THE_WORLD.get()
                || !(power.getSummonedStandEntity()
                        instanceof DiegosWorldStandEntity stand)
                || !stand.isAlive()
                || stand.isRemoved()) {
            return null;
        }
        float lifecycleAlpha = stand.clientStuff.getAlpha(
                stand, partialTick);
        return StandVisualContext.world(
                stand, lifecycleAlpha);
    }
}
