package rotp.addons.hermitpurple.client;

import javax.annotation.Nullable;

import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.customobjects.entity_projectile.OwnerBoundProjectileEntity;

import rotp.addons.hermitpurple.HermitPurpleAddon;
import rotp.addons.hermitpurple.entity.HermitPurpleStandEntity;
import net.minecraft.world.entity.LivingEntity;

final class HermitPurpleVisuals {
    private HermitPurpleVisuals() {}

    @Nullable
    static StandVisualContext context(
            OwnerBoundProjectileEntity visibleChild,
            float partialTick) {
        LivingEntity owner = visibleChild.getOwner();
        return owner instanceof HermitPurpleStandEntity controller
                ? context(controller, partialTick)
                : null;
    }

    @Nullable
    static StandVisualContext context(
            LivingEntity user, float partialTick) {
        return context(
                HermitPurpleAddon.controller(user),
                partialTick);
    }

    @Nullable
    private static StandVisualContext context(
            @Nullable HermitPurpleStandEntity controller,
            float partialTick) {
        if (controller == null
                || !controller.isAlive()
                || controller.isRemoved()
                || controller.clientStuff == null) {
            return null;
        }
        float lifecycleAlpha =
                controller.clientStuff.getAlpha(
                        controller, partialTick);
        return StandVisualContext.world(
                controller, lifecycleAlpha);
    }
}
