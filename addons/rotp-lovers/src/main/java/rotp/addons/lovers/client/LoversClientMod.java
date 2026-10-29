package rotp.addons.lovers.client;

import rotp.addons.lovers.LoversAddon;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = LoversAddon.MOD_ID, dist = Dist.CLIENT)
public final class LoversClientMod {
    public LoversClientMod(ModContainer modContainer) {
        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
