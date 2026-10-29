package rotp.addons.shadowtheworld.client;

import rotp.core.client.ClientPowerCache;
import rotp.core.client.input.InputHandler;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.shadowtheworld.ShadowTheWorldAddon;
import rotp.addons.shadowtheworld.network.ShadowStandPanelPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(
        modid = ShadowTheWorldAddon.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class ShadowStandPanelClient {
    private ShadowStandPanelClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ShadowStandPanelPayload.installClientHandler(
                ShadowStandPanelClient::openStandPanel);
    }

    // 1.16 ActionsOverlayGui.onStandSummon: leave a non-Stand HUD toggle
    // so the summoned Stand's controls show, as the summon key does
    static void openStandPanel() {
        InputHandler input = InputHandler.getInstance();
        StandPower stand = ClientPowerCache.getPower(PowerClass.STAND);
        if (input == null
                || stand == null
                || !stand.hasPower()
                || !stand.isSummoned()
                || input.curPowerClassToggle == PowerClass.STAND) {
            return;
        }
        input.curPowerClassToggle = null;
    }
}
