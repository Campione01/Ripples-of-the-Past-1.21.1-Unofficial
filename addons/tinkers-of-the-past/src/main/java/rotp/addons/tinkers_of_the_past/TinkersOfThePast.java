package rotp.addons.tinkers_of_the_past;

import rotp.core.init.ModItems;
import com.mojang.logging.LogUtils;
import rotp.addons.tinkers_of_the_past.fluids.JojoFluids;
import rotp.addons.tinkers_of_the_past.shared.JojoMaterials;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.slf4j.Logger;

@Mod(TinkersOfThePast.MOD_ID)
public final class TinkersOfThePast {
    public static final String MOD_ID = "tjojo";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TinkersOfThePast(IEventBus modEventBus) {
        JojoMaterials.register(modEventBus);
        JojoFluids.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::buildCreativeTab);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> DependencyBoundary.report(LOGGER));
    }

    private void buildCreativeTab(
            BuildCreativeModeTabContentsEvent event) {
        if (!ModItems.MAIN_TAB.getKey().equals(event.getTabKey())) {
            return;
        }

        event.accept(JojoMaterials.STAND_ARROW_ALLOY_BLOCK_ITEM.get());
        event.accept(JojoMaterials.STAND_ARROW_ALLOY_NUGGET.get());
        event.accept(JojoMaterials.METEOR_BLOCK_ITEM.get());
        event.accept(JojoMaterials.METEOR_INGOT.get());
        event.accept(JojoMaterials.METEOR_NUGGET.get());
        event.accept(JojoFluids.MOLTEN_STAND_ARROW_ALLOY_BUCKET.get());
        event.accept(JojoFluids.MOLTEN_METEOR_BUCKET.get());
    }
}
