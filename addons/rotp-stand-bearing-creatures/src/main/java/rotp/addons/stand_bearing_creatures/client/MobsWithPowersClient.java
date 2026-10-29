package rotp.addons.stand_bearing_creatures.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import rotp.addons.stand_bearing_creatures.MobsWithPowersAddon;

@Mod(value = MobsWithPowersAddon.MOD_ID, dist = Dist.CLIENT)
public final class MobsWithPowersClient {
    public MobsWithPowersClient(ModContainer modContainer) {
        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
