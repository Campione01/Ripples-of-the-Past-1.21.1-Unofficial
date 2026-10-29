package rotp.addons.spawn_with_stand;

import rotp.core.api.RotpAddonApi;
import rotp.addons.spawn_with_stand.config.StandSpawnConfig;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(RotpSpawnWithStandAddon.MOD_ID)
public final class RotpSpawnWithStandAddon {
    public static final String MOD_ID = "rotp_sws";
    public static final int REQUIRED_CORE_ABI = 1;

    public RotpSpawnWithStandAddon(
            IEventBus modBus, ModContainer modContainer) {
        requireCoreApi();
        modBus.addListener(
                StandSpawnConfig::onConfigLoading);
        modBus.addListener(
                StandSpawnConfig::onConfigReloading);
        modBus.addListener(
                StandSpawnConfig::onConfigUnloading);
        modContainer.registerConfig(
                ModConfig.Type.COMMON,
                StandSpawnConfig.SPEC);
        if (FMLEnvironment.dist.isClient()) {
            ClientConfigScreen.register(modContainer);
        }
    }

    private static void requireCoreApi() {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_STAND_POWER_TRANSITIONS_V1)) {
            throw new IllegalStateException(
                    "RotP Spawn With Stand requires ROTP addon ABI 1 "
                            + "with the public Stand transition API.");
        }
    }

    // Its own class so that only the physical client loads the config GUI classes.
    private static final class ClientConfigScreen {
        private ClientConfigScreen() {}

        static void register(ModContainer modContainer) {
            modContainer.registerExtensionPoint(
                    IConfigScreenFactory.class,
                    ConfigurationScreen::new);
        }
    }
}
