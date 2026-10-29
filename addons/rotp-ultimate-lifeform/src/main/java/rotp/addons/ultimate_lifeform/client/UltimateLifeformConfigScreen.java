package rotp.addons.ultimate_lifeform.client;

import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = UltimateLifeformAddon.MOD_ID, dist = Dist.CLIENT)
public final class UltimateLifeformConfigScreen {
    public UltimateLifeformConfigScreen(ModContainer modContainer) {
        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
