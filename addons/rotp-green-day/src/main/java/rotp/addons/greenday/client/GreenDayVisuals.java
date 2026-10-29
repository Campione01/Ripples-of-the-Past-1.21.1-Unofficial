package rotp.addons.greenday.client;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.greenday.GreenDayAddon;
import rotp.addons.greenday.state.GreenDayMoldState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class GreenDayVisuals {
    private GreenDayVisuals() {}

    @Nullable
    public static StandVisualContext fromOwner(
            @Nullable LivingEntity owner,
            float partialTick) {
        return fromController(controller(owner), partialTick);
    }

    /**
     * Context for a detached arm and its marker.
     *
     * <p>1.16 drew the arm unconditionally. The arm stays in the world, carrying its victim and waiting to be picked
     * up, while its owner's Stand is recalled, so with no Stand out it is drawn fully opaque instead of not at all.
     */
    public static StandVisualContext fromArmOwner(
            @Nullable LivingEntity owner,
            float partialTick) {
        StandVisualContext context =
                fromOwner(owner, partialTick);
        return context != null
                ? context
                : StandVisualContext.preview();
    }

    /**
     * Context for the mould drawn on a target's body.
     *
     * <p>1.16's MoldLayer asked only whether the target carried the mold effect, and both callers here check that
     * first. The source's Stand is looked up purely for the port's fade: the mould is on the target's body, not on
     * Green Day, so recalling the Stand - or having no recorded source at all - must not wipe it. With no source Stand
     * out the mould shows fully opaque, the way Heaven's Door's book layer already does.
     */
    public static StandVisualContext fromMoldTarget(
            LivingEntity target,
            float partialTick) {
        if (target.level() instanceof ClientLevel level
                && target.hasData(GreenDayAddon.MOLD_STATE)) {
            GreenDayMoldState state =
                    GreenDayMoldState.get(target);
            for (UUID sourceId : state.sourceIds()) {
                LivingEntity source = findLiving(level, sourceId);
                StandVisualContext context =
                        fromOwner(source, partialTick);
                if (context != null) {
                    return context;
                }
            }
        }
        return StandVisualContext.preview();
    }

    public static boolean canRender(
            @Nullable StandVisualContext context) {
        return ClientGlobals.canSeeStands
                && context != null
                && context.effectiveAlpha() > 0.0F;
    }

    @Nullable
    private static StandEntity controller(
            @Nullable LivingEntity owner) {
        StandPower power =
                owner != null ? StandPower.get(owner) : null;
        return power != null
                && power.getPowerType()
                        == GreenDayAddon.GREEN_DAY.get()
                && power.getSummonedStandEntity()
                        instanceof StandEntity stand
                && stand.isAlive()
                ? stand
                : null;
    }

    @Nullable
    private static StandVisualContext fromController(
            @Nullable StandEntity controller,
            float partialTick) {
        if (controller == null || !controller.isAlive()) {
            return null;
        }
        float lifecycleAlpha = controller.clientStuff != null
                ? controller.clientStuff.getAlpha(
                        controller, partialTick)
                : 1.0F;
        return StandVisualContext.world(
                controller, lifecycleAlpha);
    }

    @Nullable
    private static LivingEntity findLiving(
            ClientLevel level, UUID id) {
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living
                    && id.equals(living.getUUID())) {
                return living;
            }
        }
        return null;
    }
}
