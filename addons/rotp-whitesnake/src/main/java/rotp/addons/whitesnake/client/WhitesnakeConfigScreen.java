package rotp.addons.whitesnake.client;

import rotp.addons.whitesnake.WhitesnakeAddon;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = WhitesnakeAddon.MOD_ID, dist = Dist.CLIENT)
public final class WhitesnakeConfigScreen {
    public WhitesnakeConfigScreen(ModContainer modContainer) {
        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
