package rotp.addons.kingcrimson.client;

import rotp.addons.kingcrimson.KingCrimsonAddon;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = KingCrimsonAddon.MOD_ID, dist = Dist.CLIENT)
public final class KingCrimsonClientMod {
    public KingCrimsonClientMod(ModContainer modContainer) {
        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
