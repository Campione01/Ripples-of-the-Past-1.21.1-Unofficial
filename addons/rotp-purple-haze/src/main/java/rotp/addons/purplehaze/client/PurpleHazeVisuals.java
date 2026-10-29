package rotp.addons.purplehaze.client;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.stand.StandOpacityPolicy;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.config.client.ClientModSettings;

import rotp.addons.purplehaze.entity.PurpleHazeEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class PurpleHazeVisuals {
    private PurpleHazeVisuals() {}

    @Nullable
    public static StandVisualContext fromOwner(
            @Nullable LivingEntity owner,
            float partialTick) {
        return independentEffect(owner);
    }

    @Nullable
    public static StandVisualContext fromControllerId(
            Level level,
            int controllerId,
            @Nullable UUID ownerUuid,
            float partialTick) {
        Entity entity = controllerId >= 0
                ? level.getEntity(controllerId)
                : null;
        LivingEntity owner = entity instanceof PurpleHazeEntity stand
                ? stand.getUser()
                : null;
        if (ownerUuid != null
                && (owner == null || !ownerUuid.equals(owner.getUUID()))) {
            owner = level.getPlayerByUUID(ownerUuid);
        }
        return independentEffect(owner);
    }

    private static StandVisualContext independentEffect(
            @Nullable LivingEntity owner) {
        ClientModSettings.Settings settings =
                ClientModSettings.getSettingsReadOnly();
        boolean classic = settings.classicStandObstruction;
        // Capsules, clouds and particles outlive the summoned Stand's fade.
        return new StandVisualContext(
                1.0F,
                StandOpacityPolicy.opacityMultiplierForUser(owner),
                StandOpacityPolicy.apply(1.0F, owner),
                StandOpacityPolicy.isOwnStandUser(owner),
                classic,
                classic && settings.standOutline);
    }

    public static boolean canRender(
            @Nullable StandVisualContext context) {
        return ClientGlobals.canSeeStands
                && context != null
                && context.effectiveAlpha() > 0.0F;
    }
}
