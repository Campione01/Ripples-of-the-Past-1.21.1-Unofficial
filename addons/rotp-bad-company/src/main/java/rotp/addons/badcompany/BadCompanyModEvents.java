package rotp.addons.badcompany;

import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.badcompany.entity.BadCompanySoldierEntity;
import rotp.addons.badcompany.entity.BadCompanyHelicopterEntity;
import rotp.addons.badcompany.entity.BadCompanyTankEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@EventBusSubscriber(
        modid = BadCompanyAddon.MOD_ID,
        bus = EventBusSubscriber.Bus.MOD)
public final class BadCompanyModEvents {
    private BadCompanyModEvents() {}

    @SubscribeEvent
    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                BadCompanyAddon.BAD_COMPANY_ENTITY.get(),
                StandEntity.createAttributes().build());
        event.put(
                BadCompanyAddon.BAD_SOLDIER.get(),
                BadCompanySoldierEntity
                        .createAttributes()
                        .build());
        event.put(
                BadCompanyAddon.BAD_TANK.get(),
                BadCompanyTankEntity
                        .createAttributes()
                        .build());
        event.put(
                BadCompanyAddon.BAD_HELICOPTER.get(),
                BadCompanyHelicopterEntity
                        .createAttributes()
                        .build());
    }
}
