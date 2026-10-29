package rotp.addons.speedwagon.client;

import rotp.addons.speedwagon.SpeedwagonAddon;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@EventBusSubscriber(
        modid = SpeedwagonAddon.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class SpeedwagonClientEvents {
    private static final ResourceLocation HENCHMAN_TEXTURE =
            SpeedwagonAddon.id(
                    "textures/entity/biped/henchman.png");
    private static final ResourceLocation HENCHMAN2_TEXTURE =
            SpeedwagonAddon.id(
                    "textures/entity/biped/henchman2.png");
    private static final ResourceLocation INSTRUCTOR_TEXTURE =
            SpeedwagonAddon.id(
                    "textures/entity/biped/instructor.png");

    private SpeedwagonClientEvents() {}

    public static void registerConfigScreen(ModContainer modContainer) {
        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                SpeedwagonAddon.HENCHMAN.get(),
                context -> new SpeedwagonNpcRenderer<>(
                        context, HENCHMAN_TEXTURE));
        event.registerEntityRenderer(
                SpeedwagonAddon.HENCHMAN2.get(),
                context -> new SpeedwagonNpcRenderer<>(
                        context, HENCHMAN2_TEXTURE));
        event.registerEntityRenderer(
                SpeedwagonAddon.INSTRUCTOR.get(),
                context -> new SpeedwagonNpcRenderer<>(
                        context, INSTRUCTOR_TEXTURE));
    }

    @SubscribeEvent
    public static void registerHud(
            RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                SpeedwagonAddon.id("speedwagon_energy"),
                new SpeedwagonEnergyHud());
    }
}
