package rotp.addons.spicegirl;

import rotp.core.api.block.EntitySoftLandingDecision;
import rotp.core.api.block.EntitySoftLandingProvider;
import rotp.core.api.block.EntitySoftLandingQuery;

import rotp.addons.spicegirl.state.SpiceGirlSoftAreaService;
import net.minecraft.world.entity.LivingEntity;

public final class SpiceGirlSoftLandingProvider
        implements EntitySoftLandingProvider {
    public static final SpiceGirlSoftLandingProvider INSTANCE =
            new SpiceGirlSoftLandingProvider();
    public static final float FALL_DAMAGE_MULTIPLIER = 0.0F;
    public static final double NON_LIVING_BOUNCE_MULTIPLIER = 0.80D;

    private static final EntitySoftLandingDecision SOFT_BOUNCE =
            EntitySoftLandingDecision.softBounce(
                    FALL_DAMAGE_MULTIPLIER,
                    NON_LIVING_BOUNCE_MULTIPLIER);

    private SpiceGirlSoftLandingProvider() {}

    @Override
    public EntitySoftLandingDecision resolve(
            EntitySoftLandingQuery query) {
        if (query.entity() instanceof LivingEntity) {
            return EntitySoftLandingDecision.pass();
        }
        return SpiceGirlSoftAreaService.controllerAt(
                        query.level(), query.position()) != null
                ? SOFT_BOUNCE
                : EntitySoftLandingDecision.pass();
    }
}
