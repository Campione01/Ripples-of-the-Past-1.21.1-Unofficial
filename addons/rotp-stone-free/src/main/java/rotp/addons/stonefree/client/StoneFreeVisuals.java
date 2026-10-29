package rotp.addons.stonefree.client;

import javax.annotation.Nullable;

import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.entityrender.stand.StandOpacityPolicy;
import rotp.core.config.client.ClientModSettings;

import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.stonefree.StoneFreeAddon;
import rotp.addons.stonefree.StoneFreeServices;
import rotp.addons.stonefree.entity.StoneFreeStandEntity;
import rotp.addons.stonefree.entity.StoneFreeStringEntity;
import net.minecraft.world.entity.LivingEntity;

final class StoneFreeVisuals {
    private StoneFreeVisuals() {}

    /**
     * Context for the overlays drawn on the wearer's own body.
     *
     * <p>1.16 drew these for anyone whose Stand was Stone Free and who carried the effect, whether or not the Stand
     * was out - the marks are on the user's body, not on the Stand. The port keeps its own lifecycle fade while the
     * Stand is summoned and falls back to fully opaque when it is not, which is what Heaven's Door's book layer
     * already does.
     */
    @Nullable
    static StandVisualContext overlayContext(
            LivingEntity user, float partialTick) {
        if (!ownsStoneFree(user)) {
            return null;
        }
        StandVisualContext summoned = context(user, partialTick);
        return summoned != null
                ? summoned
                : StandVisualContext.preview();
    }

    private static boolean ownsStoneFree(
            @Nullable LivingEntity user) {
        StandPower power =
                user != null ? StandPower.get(user) : null;
        return power != null
                && power.getPowerType()
                        == StoneFreeAddon.STONE_FREE.get();
    }

    @Nullable
    static StandVisualContext context(
            StoneFreeStringEntity visibleChild,
            float partialTick) {
        if (visibleChild.kind()
                == StoneFreeStringEntity.Kind.USER_BARRIER) {
            LivingEntity user =
                    visibleChild.getCurrentBarrierUser();
            if (user == null || !user.isAlive()
                    || user.isRemoved()
                    || !visibleChild.isBarrierFor(user.getUUID())
                    || !ownsStoneFree(user)) {
                return null;
            }
            // Placed strings outlive the controller's summon/recall fade.
            ClientModSettings.Settings settings =
                    ClientModSettings.getSettingsReadOnly();
            boolean classic = settings.classicStandObstruction;
            return new StandVisualContext(
                    1.0F,
                    StandOpacityPolicy.opacityMultiplierForUser(user),
                    StandOpacityPolicy.apply(1.0F, user),
                    StandOpacityPolicy.isOwnStandUser(user),
                    classic,
                    classic && settings.standOutline);
        }
        return context(
                visibleChild.getVisualOwner(), partialTick);
    }

    @Nullable
    static StandVisualContext context(
            LivingEntity user, float partialTick) {
        return context(
                StoneFreeServices.controller(user),
                partialTick);
    }

    @Nullable
    private static StandVisualContext context(
            @Nullable StoneFreeStandEntity controller,
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
