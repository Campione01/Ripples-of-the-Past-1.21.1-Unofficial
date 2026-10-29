package rotp.addons.tournament;

import rotp.addons.tournament.init.AddonBlocks;
import rotp.addons.tournament.init.AddonSounds;
import rotp.addons.tournament.item.AddonItems;
import rotp.core.api.RotpAddonApi;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(AddonMain.MOD_ID)
public final class AddonMain {
    public static final String MOD_ID = "tournament";
    private static final int REQUIRED_CORE_ABI = 1;

    public AddonMain(
            IEventBus modEventBus,
            ModContainer modContainer) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)) {
            throw new IllegalStateException(
                    "Tournament requires ROTP addon ABI 1.");
        }

        AddonBlocks.BLOCKS.register(modEventBus);
        AddonItems.ITEMS.register(modEventBus);
        AddonSounds.SOUNDS.register(modEventBus);
        modEventBus.addListener(AddonItems::buildCreativeTab);
        modContainer.registerConfig(
                ModConfig.Type.COMMON,
                TournamentConfig.SPEC,
                "tournament-common.toml");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
