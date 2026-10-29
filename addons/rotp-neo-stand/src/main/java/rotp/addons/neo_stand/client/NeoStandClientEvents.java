package rotp.addons.neo_stand.client;

import rotp.addons.neo_stand.NeoStandAddon;
import rotp.addons.neo_stand.ability.BulletTimeService;
import rotp.core.api.client.time.ClientRegionalTimeDilationPolicies;
import rotp.core.api.client.render.EntityPostRenderExtensions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(
        modid = NeoStandAddon.MOD_ID,
        value = Dist.CLIENT)
public final class NeoStandClientEvents {
    private NeoStandClientEvents() {}

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            BulletTimeService.installClientOwnerActiveLookup(
                    BulletTimeHud::isOwnerActive);
            ClientRegionalTimeDilationPolicies.register(
                    NeoStandAddon.id("bullet_time"),
                    BulletTimeHud::dilation);
            EntityPostRenderExtensions.register(
                    NeoStandAddon.id(
                            "hg_emerald_full_bright"),
                    0,
                    new NeoEmeraldRenderer());
        });
    }

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                NeoStandAddon.PLANT_ATTACK_ENTITY.get(),
                PlantAttackRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(
            EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(
                PlantAttackModel.LAYER_LOCATION,
                PlantAttackModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerHud(
            RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.HOTBAR,
                NeoStandAddon.id("bullet_time"),
                new BulletTimeHud());
    }
}
