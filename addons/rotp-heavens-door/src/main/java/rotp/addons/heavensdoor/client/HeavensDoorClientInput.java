package rotp.addons.heavensdoor.client;

import rotp.addons.heavensdoor.HeavensDoorAddon;
import rotp.addons.heavensdoor.HeavensDoorEvents;
import rotp.addons.heavensdoor.state.CannotMoveRules;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

@EventBusSubscriber(
        modid = HeavensDoorAddon.MOD_ID,
        value = Dist.CLIENT)
public final class HeavensDoorClientInput {
    private static boolean wasShiftPressed;

    private HeavensDoorClientInput() {}

    // 1.16 GameplayHandler.onKeyInput :398-413: the sneak key is released
    // before any tick reads it, and the line shows once per press. The
    // server reset alone leaves the local crouch pose and camera.
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null
                || !player.hasEffect(HeavensDoorAddon.FORGOT_SHIFT)) {
            return;
        }
        KeyMapping sneak = minecraft.options.keyShift;
        if (sneak.isDown()) {
            sneak.setDown(false);
            if (!wasShiftPressed) {
                wasShiftPressed = true;
                player.setShiftKeyDown(false);
                player.displayClientMessage(
                        Component.translatable(
                                "message.rotp_hd.forgot_shift"),
                        true);
            }
        }
        else {
            wasShiftPressed = false;
        }
    }

    // Port addition. A sneak that no key event announced (a mouse button,
    // toggle mode, a key held since before the effect) must not reach the
    // local pose either; the server never sees it, so the line comes from
    // here, at most once a second.
    @SubscribeEvent
    public static void onMovementInput(
            MovementInputUpdateEvent event) {
        if (CannotMoveRules.blocksSelf(event.getEntity())) {
            var input = event.getInput();
            // Sneaking stays free for the sneak variants of abilities and
            // items; on a mount the sneak key is the way off it.
            boolean dismount = event.getEntity().isPassenger()
                    && input.shiftKeyDown;
            boolean attempt = dismount
                    || input.leftImpulse != 0.0F
                    || input.forwardImpulse != 0.0F
                    || input.left
                    || input.right
                    || input.up
                    || input.down
                    || input.jumping;
            input.leftImpulse = 0.0F;
            input.forwardImpulse = 0.0F;
            input.left = false;
            input.right = false;
            input.up = false;
            input.down = false;
            input.jumping = false;
            if (dismount) {
                input.shiftKeyDown = false;
            }
            CannotMoveRules.enforce(event.getEntity());
            if (attempt) {
                HeavensDoorEvents.notifyForgot(
                        event.getEntity(), "cannot_move");
            }
        }
        if (event.getEntity().hasEffect(
                HeavensDoorAddon.FORGOT_SHIFT)
                && event.getInput().shiftKeyDown) {
            event.getInput().shiftKeyDown = false;
            HeavensDoorEvents.notifyForgot(
                    event.getEntity(), "forgot_shift");
        }
    }
}
