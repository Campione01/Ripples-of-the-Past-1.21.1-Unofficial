package rotp.addons.stand_bearing_creatures.mechanics.virus;

import rotp.core.api.stand.StandVirusMobGiver;
import rotp.core.api.stand.StandVirusMobGiverContext;

import net.minecraft.world.entity.Mob;
import rotp.addons.stand_bearing_creatures.config.MobPowerConfig;
import rotp.addons.stand_bearing_creatures.util.MobPowerUtil;

public final class GenericMobStandGiver
        implements StandVirusMobGiver {
    @Override
    public boolean matches(
            net.minecraft.world.entity.LivingEntity target) {
        return target instanceof Mob
                && MobPowerUtil.isEligibleMob(target);
    }

    @Override
    public float survivalChance(
            StandVirusMobGiverContext context) {
        float configuredChance = MobPowerConfig
                .PERCENTAGE_CHANCE_TO_GETTING_A_STAND_FOR_MOB
                .get()
                / 100.0F;
        return Math.max(
                0.0F,
                configuredChance
                        - 0.15F * context.amplifier());
    }

    @Override
    public boolean giveStand(
            StandVirusMobGiverContext context) {
        if (!(context.target() instanceof Mob mob)
                || !MobPowerUtil.isEligibleMob(mob)) {
            return false;
        }
        MobPowerUtil.attachPowers(mob);
        if (MobPowerUtil.hasStand(mob)) {
            return false;
        }
        if (MobPowerUtil.convertBabyVillager(mob) != null) {
            return true;
        }
        return MobPowerUtil.grantStand(
                mob,
                MobPowerUtil.randomStand(mob),
                false);
    }

    @Override
    public float stopHealth(
            StandVirusMobGiverContext context) {
        return 2.5F;
    }

    @Override
    public float extraDamage(
            StandVirusMobGiverContext context) {
        return (0.15F + context.amplifier() * 0.2F)
                * Math.min(
                        2.0F,
                        context.target().getHealth() - 0.001F);
    }
}
