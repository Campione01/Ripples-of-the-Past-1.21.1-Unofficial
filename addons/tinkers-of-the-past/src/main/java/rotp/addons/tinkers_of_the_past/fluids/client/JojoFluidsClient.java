package rotp.addons.tinkers_of_the_past.fluids.client;

import rotp.addons.tinkers_of_the_past.TinkersOfThePast;
import rotp.addons.tinkers_of_the_past.fluids.JojoFluids;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(
        modid = TinkersOfThePast.MOD_ID,
        value = Dist.CLIENT)
public final class JojoFluidsClient {
    private static final ResourceLocation MOLTEN_STILL =
            TinkersOfThePast.id("block/fluids/molten_metal_still");
    private static final ResourceLocation MOLTEN_FLOWING =
            TinkersOfThePast.id("block/fluids/molten_metal_flowing");

    private JojoFluidsClient() {}

    @SubscribeEvent
    public static void registerClientExtensions(
            RegisterClientExtensionsEvent event) {
        event.registerFluidType(
                moltenExtension(
                        JojoFluids.STAND_ARROW_ALLOY_TINT),
                JojoFluids.MOLTEN_STAND_ARROW_ALLOY_TYPE);
        event.registerFluidType(
                moltenExtension(JojoFluids.METEOR_TINT),
                JojoFluids.MOLTEN_METEOR_TYPE);
    }

    private static IClientFluidTypeExtensions moltenExtension(
            int tint) {
        return new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return MOLTEN_STILL;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return MOLTEN_FLOWING;
            }

            @Override
            public int getTintColor() {
                return tint;
            }
        };
    }
}
