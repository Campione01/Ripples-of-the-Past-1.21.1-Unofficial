package rotp.addons.yellowtemperance.client;

import rotp.addons.yellowtemperance.YellowTemperanceAddon;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = YellowTemperanceAddon.MOD_ID, dist = Dist.CLIENT)
public final class YellowTemperanceConfigScreen {
    public YellowTemperanceConfigScreen(ModContainer modContainer) {
        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
