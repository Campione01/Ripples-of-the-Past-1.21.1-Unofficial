package rotp.addons.echoes.client;

import rotp.addons.echoes.RotpEchoes;
import rotp.addons.echoes.init.EchoesDataComponents;
import rotp.addons.echoes.init.EchoesEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = RotpEchoes.MOD_ID, value = Dist.CLIENT)
public final class EchoesClientEvents {
    private EchoesClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                EchoesEntities.ACT1.get(), EchoesStandRenderer::new);
        event.registerEntityRenderer(
                EchoesEntities.ACT2.get(), EchoesStandRenderer::new);
        event.registerEntityRenderer(
                EchoesEntities.ACT3.get(), EchoesStandRenderer::new);
    }

    // 092 ClientEventHandler.onItemTooltip: mark gravity-infused stacks.
    // Game-bus listener kept apart from the mod-bus renderer hook.
    @EventBusSubscriber(modid = RotpEchoes.MOD_ID, value = Dist.CLIENT)
    public static final class Tooltips {
        private Tooltips() {}

        @SubscribeEvent
        public static void onItemTooltip(ItemTooltipEvent event) {
            Long until = event.getItemStack().get(
                    EchoesDataComponents.GRAVITY_INFUSED_UNTIL);
            ClientLevel level = Minecraft.getInstance().level;
            if (until == null || level == null
                    || until <= level.getGameTime()) {
                return;
            }
            event.getToolTip().add(Component.literal("\u2726 ")
                    .append(Component.translatable(
                            "rotp_echoes.tooltip.gravity_infused"))
                    .withStyle(ChatFormatting.DARK_PURPLE));
            event.getToolTip().add(Component.translatable(
                            "rotp_echoes.tooltip.gravity_infused.desc")
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
