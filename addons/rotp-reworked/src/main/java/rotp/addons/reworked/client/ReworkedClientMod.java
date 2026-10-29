package rotp.addons.reworked.client;

import rotp.addons.reworked.ReworkedAddon;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = ReworkedAddon.MOD_ID, dist = Dist.CLIENT)
public final class ReworkedClientMod {
    public ReworkedClientMod(ModContainer modContainer) {
        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
