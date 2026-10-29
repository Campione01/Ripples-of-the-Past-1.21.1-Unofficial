package rotp.addons.blood_bottle.client;

import rotp.addons.blood_bottle.BloodBottleAddon;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = BloodBottleAddon.MOD_ID, dist = Dist.CLIENT)
public final class BloodBottleClient {
    public BloodBottleClient(ModContainer modContainer) {
        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
