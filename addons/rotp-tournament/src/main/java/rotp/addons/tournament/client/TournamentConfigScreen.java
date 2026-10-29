package rotp.addons.tournament.client;

import rotp.addons.tournament.AddonMain;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = AddonMain.MOD_ID, dist = Dist.CLIENT)
public final class TournamentConfigScreen {
    public TournamentConfigScreen(ModContainer modContainer) {
        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
