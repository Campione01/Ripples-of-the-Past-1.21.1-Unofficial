package rotp.addons.heavensdoor.client;

import javax.annotation.Nullable;

import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.stand.StandVisualContext;

import rotp.addons.heavensdoor.HeavensDoorAddon;
import rotp.addons.heavensdoor.state.BookState;
import net.minecraft.world.entity.LivingEntity;

public final class HeavensDoorVisuals {
    private HeavensDoorVisuals() {}

    @Nullable
    public static StandVisualContext fromBookTarget(
            LivingEntity target, float partialTick) {
        if (!target.level().isClientSide()
                || !target.hasData(HeavensDoorAddon.BOOK_STATE)) {
            return null;
        }
        BookState state = HeavensDoorAddon.bookState(target);
        // The donor renders the Book layer at full material alpha. It is a
        // persistent effect on the target, not Stand-body geometry, so the
        // owner's global Stand opacity must not wash it out.
        return state.active() ? StandVisualContext.preview() : null;
    }

    public static boolean canRender(
            @Nullable StandVisualContext context) {
        return ClientGlobals.canSeeStands
                && context != null
                && context.effectiveAlpha() > 0.0F;
    }

}
