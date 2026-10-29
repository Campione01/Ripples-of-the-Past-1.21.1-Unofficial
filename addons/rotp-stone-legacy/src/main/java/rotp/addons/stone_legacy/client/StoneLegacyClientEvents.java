package rotp.addons.stone_legacy.client;

import rotp.core.item.StoneMaskItem;

import rotp.addons.stone_legacy.StoneLegacyAddon;
import rotp.addons.stone_legacy.item.StoneFruitItem;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(
        modid = StoneLegacyAddon.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class StoneLegacyClientEvents {
    private StoneLegacyClientEvents() {}

    @SubscribeEvent
    public static void clientSetup(
            FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(
                StoneLegacyAddon.FRUIT.get(),
                StoneLegacyAddon.id("stage"),
                (stack, level, entity, seed) ->
                        entity != null
                                ? StoneFruitItem.stage(stack)
                                : 21.0F);
            registerActivatedModel(
                    StoneLegacyAddon.PREDICTED_HEAT_MASK.get(),
                    StoneLegacyAddon.PREDICTED_HEAT);
            registerActivatedModel(
                    StoneLegacyAddon.PREDICTED_LIGHT_MASK.get(),
                    StoneLegacyAddon.PREDICTED_LIGHT);
            registerActivatedModel(
                    StoneLegacyAddon.PREDICTED_WIND_MASK.get(),
                    StoneLegacyAddon.PREDICTED_WIND);
            ItemBlockRenderTypes.setRenderLayer(
                    StoneLegacyAddon.PREDICTED_HEAT_BLOCK.get(),
                    RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(
                    StoneLegacyAddon.PREDICTED_LIGHT_BLOCK.get(),
                    RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(
                    StoneLegacyAddon.PREDICTED_WIND_BLOCK.get(),
                    RenderType.cutout());
        });
    }

    private static void registerActivatedModel(
            Item item,
            String itemId) {
        ItemProperties.register(
                item,
                StoneLegacyAddon.id(
                        "item/" + itemId + "_activated"),
                (stack, level, entity, seed) ->
                        StoneMaskItem.getActivatedTicks(stack) > 0
                                ? 1.0F
                                : 0.0F);
    }
}
