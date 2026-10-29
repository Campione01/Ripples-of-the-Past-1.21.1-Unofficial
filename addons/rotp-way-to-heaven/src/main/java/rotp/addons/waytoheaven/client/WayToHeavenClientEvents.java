package rotp.addons.waytoheaven.client;

import rotp.core.client.firstperson.FirstPersonRender;
import rotp.core.client.ui.hud_power.PowerHud;
import rotp.core.mrpresident.CocoJumboTurtleEntity;
import rotp.core.util.functions.UtilFunctions;

import rotp.addons.waytoheaven.WayToHeavenAddon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;

@EventBusSubscriber(
        modid = WayToHeavenAddon.MOD_ID,
        value = Dist.CLIENT)
public final class WayToHeavenClientEvents {
    private static boolean renderingExtraArm;

    private WayToHeavenClientEvents() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRenderHand(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        boolean selectedAndEnabled =
                PowerHud.abilityHUDInstance != null
                        && PowerHud.abilityHUDInstance
                                .isAbilitySelected(
                                        WayToHeavenAddon
                                                .WHITESNAKE_RITE_NAME);
        boolean offHandFree = isOffHandFree(player);
        boolean vanillaAlreadyRendersOffHandArm =
                FirstPersonRender
                        .vanillaRendersBothMapArms(event);

        if (!WayToHeavenFirstPersonArmPolicy.shouldRender(
                event.isCanceled(),
                renderingExtraArm,
                event.getHand() == InteractionHand.MAIN_HAND,
                player.isInvisible(),
                selectedAndEnabled,
                offHandFree,
                vanillaAlreadyRendersOffHandArm)) {
            return;
        }

        renderingExtraArm = true;
        try {
            FirstPersonRender.renderExtraPlayerArm(
                    event,
                    player,
                    InteractionHand.OFF_HAND);
        }
        finally {
            renderingExtraArm = false;
        }
    }

    static boolean isOffHandFree(LocalPlayer player) {
        return !player.isHandsBusy()
                && UtilFunctions.itemHandFree(
                        player.getOffhandItem())
                && player.getPassengers().stream().noneMatch(
                        passenger ->
                                CocoJumboTurtleEntity
                                        .isCarriedTurtle(
                                                passenger,
                                                player));
    }
}
