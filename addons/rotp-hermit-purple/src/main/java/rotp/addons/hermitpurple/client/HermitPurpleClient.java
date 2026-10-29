package rotp.addons.hermitpurple.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import rotp.addons.hermitpurple.HermitPurpleAddon;

@Mod(value = HermitPurpleAddon.MOD_ID, dist = Dist.CLIENT)
public final class HermitPurpleClient {
    public HermitPurpleClient(ModContainer modContainer) {
        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
