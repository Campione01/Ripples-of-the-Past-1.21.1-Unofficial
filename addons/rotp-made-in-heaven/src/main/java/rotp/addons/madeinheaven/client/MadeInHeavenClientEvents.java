package rotp.addons.madeinheaven.client;

import java.util.OptionalInt;

import rotp.core.api.client.render.AbilitySelectionVisualPolicies;
import rotp.core.client.entityrender.stand.StandEntityRenderer;

import rotp.addons.madeinheaven.MadeInHeavenAddon;
import rotp.addons.madeinheaven.MadeInHeavenSelectionPredicates;
import rotp.addons.madeinheaven.state.TimeAccelerationService;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

@EventBusSubscriber(
        modid = MadeInHeavenAddon.MOD_ID,
        bus = EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class MadeInHeavenClientEvents {
    private MadeInHeavenClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                MadeInHeavenAddon.MADE_IN_HEAVEN_ENTITY.get(),
                StandEntityRenderer::new);
    }

    @SubscribeEvent
    public static void registerSelectionPolicy(
            RegisterGuiLayersEvent event) {
        AbilitySelectionVisualPolicies.register(
                MadeInHeavenAddon.id("time_system_selection"),
                query -> {
                    boolean activeOwner = query.power() != null
                            && TimeAccelerationService.isActiveOwner(
                                    query.power().getUser());
                    return MadeInHeavenSelectionPredicates
                            .donorGreenSelection(
                                    query.ability()
                                            .getAbilityId()
                                            .nameInMoveset(),
                                    activeOwner)
                            ? OptionalInt.of(0xFF00FF00)
                            : OptionalInt.empty();
                });
    }

}
