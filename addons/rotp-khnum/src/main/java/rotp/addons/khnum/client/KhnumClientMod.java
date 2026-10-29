package rotp.addons.khnum.client;

import rotp.addons.khnum.KhnumAddon;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = KhnumAddon.MOD_ID, dist = Dist.CLIENT)
public final class KhnumClientMod {
    public KhnumClientMod(ModContainer modContainer) {
        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
