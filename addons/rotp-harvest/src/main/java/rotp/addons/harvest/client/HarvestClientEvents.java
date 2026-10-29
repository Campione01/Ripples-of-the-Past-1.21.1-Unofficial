package rotp.addons.harvest.client;

import java.util.OptionalInt;

import rotp.core.api.client.render.AbilitySelectionVisualPolicies;
import rotp.core.client.entityrender.entities.NoopEntityRenderer;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.harvest.HarvestAddon;
import rotp.addons.harvest.HarvestSelectionPredicates;
import rotp.addons.harvest.ability.HarvestCommandAbility;
import rotp.addons.harvest.entity.HarvestStandEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

@EventBusSubscriber(
        modid = HarvestAddon.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class HarvestClientEvents {
    private HarvestClientEvents() {}

    @SubscribeEvent
    public static void registerLayers(
            EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(
                HarvestMemberModel.LAYER_LOCATION,
                HarvestMemberModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                HarvestAddon.HARVEST_STAND_ENTITY.get(),
                NoopEntityRenderer::new);
        event.registerEntityRenderer(
                HarvestAddon.HARVEST_MEMBER.get(),
                HarvestMemberRenderer::new);
    }

    @SubscribeEvent
    public static void registerSelectionPolicy(
            RegisterGuiLayersEvent event) {
        AbilitySelectionVisualPolicies.register(
                HarvestAddon.id("command_selection"),
                query -> {
                    if (!(query.power() instanceof StandPower power)) {
                        return OptionalInt.empty();
                    }
                    // 1.16 BeCloser.greenSelection :269-271 read the user's
                    // flag, Harvest out or not
                    return HarvestSelectionPredicates
                            .donorGreenSelection(
                                    query.ability()
                                            .getAbilityId()
                                            .nameInMoveset(),
                                    HarvestCommandAbility.isCloser(power),
                                    power.getSummonedStandEntity()
                                                    instanceof HarvestStandEntity
                                                            coordinator
                                            && coordinator.isCarryUpActive())
                            ? OptionalInt.of(0xFF00FF00)
                            : OptionalInt.empty();
                });
    }

}
