package rotp.addons.stickyfingers.client;

import rotp.addons.stickyfingers.StickyFingersAddon;
import rotp.addons.stickyfingers.ability.StickyFingersClimbZipperAbility;
import rotp.addons.stickyfingers.ability.ZipperClimbRules;
import net.minecraft.client.player.Input;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

/**
 * 1.16 travelOnZipper on the owning client: forward/back climb
 * the zipper, other movement input is dropped.
 */
@EventBusSubscriber(
        modid = StickyFingersAddon.MOD_ID,
        value = Dist.CLIENT)
public final class StickyFingersZipperClimbClient {
    private StickyFingersZipperClimbClient() {}

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        Player player = event.getEntity();
        Direction wall =
                StickyFingersClimbZipperAbility.climbWall(player);
        if (wall == null) {
            return;
        }
        Input input = event.getInput();
        player.fallDistance = 0.0F;
        if (!player.isAlive() || player.isSpectator()
                || !StickyFingersClimbZipperAbility
                        .isOnZipperColumn(player, wall)
                || ZipperClimbRules.releases(
                        input.jumping,
                        player.onGround(),
                        input.forwardImpulse)) {
            StickyFingersClimbZipperAbility.stopClimbing(player);
            return;
        }
        double speed =
                ZipperClimbRules.verticalSpeed(input.forwardImpulse);
        input.forwardImpulse = 0.0F;
        input.leftImpulse = 0.0F;
        player.setDeltaMovement(0.0D, speed, 0.0D);
    }
}
