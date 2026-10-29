package rotp.addons.madeinheaven.client;

import rotp.addons.madeinheaven.MadeInHeavenAddon;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = MadeInHeavenAddon.MOD_ID, dist = Dist.CLIENT)
public final class MadeInHeavenClientMod {
    public MadeInHeavenClientMod(ModContainer modContainer) {
        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
