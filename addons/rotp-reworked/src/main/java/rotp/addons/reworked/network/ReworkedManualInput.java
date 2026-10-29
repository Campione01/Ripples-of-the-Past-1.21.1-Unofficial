package rotp.addons.reworked.network;

import javax.annotation.Nullable;

import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.entity_puppetcontrol.StandManualInput;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

// The blink modifier of the Reworked blink punches and the TS punch
// recovery: Shift, as 1.16. Port manual control clears the user's sneak,
// so a manually controlled Stand reads the Shift key core's manual input
// channel carries (StandManualInput).
public final class ReworkedManualInput {
    private ReworkedManualInput() {}

    public static boolean isBlinkModifierHeld(
            @Nullable LivingEntity user, @Nullable StandEntity stand) {
        return StandManualInput.isShiftHeld(user, stand);
    }

    public static void clear(Player player) {
        StandManualInput.clear(player);
    }
}
