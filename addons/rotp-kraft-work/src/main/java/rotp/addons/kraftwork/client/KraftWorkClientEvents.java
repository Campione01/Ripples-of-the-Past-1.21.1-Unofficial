package rotp.addons.kraftwork.client;

import rotp.core.api.client.render.AbilitySelectionVisualPolicies;
import rotp.core.client.entityrender.entities.NoopEntityRenderer;
import rotp.core.client.entityrender.stand.StandEntityRenderer;
import rotp.core.client.input.InputHandler;
import rotp.core.client.input.controlscheme.ClientControlScheme;
import rotp.core.powersystem.PowerClass;

import rotp.addons.kraftwork.KraftWorkAddon;
import rotp.addons.kraftwork.ability.KraftWorkActionAbility;
import rotp.addons.kraftwork.ability.KraftWorkHeavyPunchAbility;
import rotp.addons.kraftwork.network.KraftWorkPayloads;
import rotp.addons.kraftwork.network.StandHudPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(
        modid = KraftWorkAddon.MOD_ID,
        value = Dist.CLIENT)
public final class KraftWorkClientEvents {
    private static Boolean sentStandHud;

    private KraftWorkClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                KraftWorkAddon.KRAFT_WORK_ENTITY.get(),
                StandEntityRenderer::new);
        event.registerEntityRenderer(
                KraftWorkAddon.KW_ITEM.get(),
                KraftWorkItemProjectileRenderer::new);
        event.registerEntityRenderer(
                KraftWorkAddon.KW_BLOCK.get(),
                NoopEntityRenderer::new);
        KraftWorkPayloads.installClientOpenHandler(
                KraftWorkClientPayloads::openAdvancedRelease);
        KraftWorkActionAbility.installClientStandHud(
                KraftWorkClientEvents::standHudShown);
    }

    // 1.16 framed a selected Lock Equipment green while usable (StandEntityActionModifier.greenSelection)
    @SubscribeEvent
    public static void registerSelectionPolicy(
            RegisterGuiLayersEvent event) {
        AbilitySelectionVisualPolicies.register(
                KraftWorkAddon.id("lock_armor_selection"),
                KraftWorkHeavyPunchAbility.LockArmorFollowup
                        ::selectionTint);
    }

    // the server reads Lock Target's aim from it, as 1.16's client did
    @SubscribeEvent
    public static void sendStandHud(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) {
            sentStandHud = null;
            return;
        }
        boolean shown = standHudShown();
        if (sentStandHud == null || sentStandHud != shown) {
            PacketDistributor.sendToServer(new StandHudPayload(shown));
            sentStandHud = shown;
        }
    }

    // the HUD shows the active control scheme's power (PowerHud.PowerIcon)
    private static boolean standHudShown() {
        InputHandler input = InputHandler.getInstance();
        ClientControlScheme scheme =
                input != null ? input.getActiveControlScheme() : null;
        return scheme != null && scheme.powerClassCosmetic == PowerClass.STAND;
    }
}
