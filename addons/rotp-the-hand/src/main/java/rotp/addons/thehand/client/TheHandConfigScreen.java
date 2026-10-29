package rotp.addons.thehand.client;

import rotp.addons.thehand.TheHandAddon;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = TheHandAddon.MOD_ID, dist = Dist.CLIENT)
public final class TheHandConfigScreen {
    public TheHandConfigScreen(ModContainer modContainer) {
        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
