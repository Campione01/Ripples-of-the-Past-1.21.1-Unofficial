package rotp.addons.badcompany;

import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.badcompany.entity.BadCompanyStandEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = BadCompanyAddon.MOD_ID)
public final class BadCompanyGameEvents {
    private BadCompanyGameEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity owner = event.getEntity();
        if (owner.level() instanceof ServerLevel level
                && hasBadCompany(owner)) {
            BadCompanyStandEntity.cleanupOwnerDependents(
                    level.getServer(), owner.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(
            PlayerEvent.PlayerLoggedOutEvent event) {
        cleanupPlayer(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        cleanupPlayer(event.getEntity());
    }

    private static void cleanupPlayer(Player owner) {
        if (owner.getServer() != null
                && hasBadCompany(owner)) {
            BadCompanyStandEntity.cleanupOwnerDependents(
                    owner.getServer(), owner.getUUID());
        }
    }

    private static boolean hasBadCompany(
            LivingEntity owner) {
        StandPower power = StandPower.get(owner);
        return power != null
                && power.hasPower()
                && power.getPowerType()
                        == BadCompanyAddon.BAD_COMPANY.get();
    }
}
