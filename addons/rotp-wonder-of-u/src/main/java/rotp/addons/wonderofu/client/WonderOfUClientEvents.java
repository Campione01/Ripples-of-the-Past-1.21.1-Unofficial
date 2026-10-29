package rotp.addons.wonderofu.client;

import java.util.OptionalInt;

import rotp.core.api.client.render.AbilitySelectionVisualPolicies;
import rotp.core.client.entityrender.stand.StandEntityRenderer;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.wonderofu.WonderOfUAddon;
import rotp.addons.wonderofu.ability.CalamityActiveAbility;
import rotp.addons.wonderofu.ability.CalamityPassiveAbility;
import rotp.addons.wonderofu.ability.CarCalamityAbility;
import rotp.addons.wonderofu.entity.WonderOfUStandEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = WonderOfUAddon.MOD_ID, value = Dist.CLIENT)
public final class WonderOfUClientEvents {
    private WonderOfUClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        AbilitySelectionVisualPolicies.register(
                WonderOfUAddon.id("calamity_mode_selection"),
                query -> {
                    if (!(query.power()
                                    instanceof StandPower power)
                            || power.getPowerType()
                                    != WonderOfUAddon.WONDER_OF_YOU.get()
                            || !(power.getSummonedStandEntity()
                                    instanceof WonderOfUStandEntity
                                            stand)) {
                        return OptionalInt.empty();
                    }
                    boolean active;
                    if (query.ability()
                            instanceof CalamityPassiveAbility) {
                        active = stand.isPassiveEnabled();
                    }
                    else if (query.ability()
                            instanceof CalamityActiveAbility) {
                        active = stand.isActiveEnabled();
                    }
                    else if (query.ability()
                            instanceof CarCalamityAbility) {
                        active = stand.isCarEnabled();
                    }
                    else {
                        active = false;
                    }
                    return active
                            ? OptionalInt.of(0xFF00FF00)
                            : OptionalInt.empty();
                });
        event.registerEntityRenderer(
                WonderOfUAddon.WONDER_OF_YOU_ENTITY.get(),
                StandEntityRenderer::new);
        event.registerEntityRenderer(
                WonderOfUAddon.CAR_CALAMITY_ENTITY.get(),
                CarCalamityRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(
            EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(
                CarCalamityModel.LAYER_LOCATION,
                CarCalamityModel::createBodyLayer);
    }
}
