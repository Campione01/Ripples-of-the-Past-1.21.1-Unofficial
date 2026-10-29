package rotp.addons.pearljam.client;

import rotp.addons.pearljam.PearlJamAddon;
import rotp.addons.pearljam.food.PearlJamDishData;
import rotp.addons.pearljam.food.PearlJamDishKind;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(
        modid = PearlJamAddon.MOD_ID,
        value = Dist.CLIENT)
public final class PearlJamClientEvents {
    private PearlJamClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                PearlJamAddon.PEARL_JAM_ENTITY.get(),
                PearlJamRenderer::new);
    }

    @SubscribeEvent
    public static void addPreparedDishTooltip(
            ItemTooltipEvent event) {
        if (!PearlJamAddon.isPearlJamUser(
                event.getEntity())) {
            return;
        }
        PearlJamDishData data = event.getItemStack()
                .get(PearlJamAddon.DISH_DATA.get());
        if (data == null) {
            return;
        }
        if (data.kind() == PearlJamDishKind.POISONED) {
            event.getToolTip().add(Component.translatable(
                            "rotp_pj.food.poisoned",
                            data.potency())
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
        else if (!data.treatment().isActive()) {
            // Injected without a diagnosis, as in 1.16.
            event.getToolTip().add(Component.translatable(
                            "rotp_pj.food.injected_plain")
                    .withStyle(ChatFormatting.GOLD));
        }
        else {
            event.getToolTip().add(Component.translatable(
                            "rotp_pj.food.injected",
                            Component.translatable(
                                    data.treatment()
                                            .translationKey()),
                            data.potency())
                    .withStyle(ChatFormatting.GOLD));
            event.getToolTip().add(Component.translatable(
                            "rotp_pj.food.target",
                            data.intendedConsumerName())
                    .withStyle(ChatFormatting.GRAY));
        }
        if (data.hasVisibleChef()) {
            event.getToolTip().add(Component.translatable(
                            "rotp_pj.food.chef",
                            data.chefName())
                    .withStyle(ChatFormatting.ITALIC));
        }
    }
}
