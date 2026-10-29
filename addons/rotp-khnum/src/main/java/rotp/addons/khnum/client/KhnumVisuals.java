package rotp.addons.khnum.client;

import rotp.core.client.entityrender.stand.StandVisualContext;

import rotp.addons.khnum.KhnumAddon;
import rotp.addons.khnum.entity.KhnumControllerEntity;
import net.minecraft.world.entity.LivingEntity;

final class KhnumVisuals {
    private KhnumVisuals() {}

    static RenderState resolve(
            LivingEntity user, float partialTick) {
        if (user == null
                || user.isInvisible()
                || user.isSpectator()) {
            return null;
        }
        KhnumControllerEntity controller =
                KhnumAddon.controller(user);
        if (controller == null || controller.clientStuff == null) {
            return null;
        }
        float lifecycleAlpha = controller.clientStuff.getAlpha(
                controller, partialTick);
        StandVisualContext context = StandVisualContext.world(
                controller, lifecycleAlpha);
        return context.effectiveAlpha() > 0.0F
                ? new RenderState(controller, context)
                : null;
    }

    record RenderState(
            KhnumControllerEntity controller,
            StandVisualContext context) {}
}
