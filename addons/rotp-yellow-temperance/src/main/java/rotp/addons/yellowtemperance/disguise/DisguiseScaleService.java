package rotp.addons.yellowtemperance.disguise;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import virtuoel.pehkui.api.ScaleData;

public final class DisguiseScaleService {
    private static final float MIN = 0.05F;
    private static final float MAX = 8.0F;

    private DisguiseScaleService() {}

    public static void apply(
            LivingEntity user, EntityDimensions target) {
        if (user == null || target == null
                || user.level().isClientSide()) {
            return;
        }
        EntityDimensions base = user.getType().getDimensions();
        set(
                YellowTemperanceScaleTypes.widthScale()
                        .getScaleData(user),
                clamp(target.width() / base.width()));
        set(
                YellowTemperanceScaleTypes.heightScale()
                        .getScaleData(user),
                clamp(target.height() / base.height()));
    }

    public static void reset(LivingEntity user) {
        if (user == null || user.level().isClientSide()) {
            return;
        }
        reset(YellowTemperanceScaleTypes.widthScale()
                .getScaleData(user));
        reset(YellowTemperanceScaleTypes.heightScale()
                .getScaleData(user));
    }

    // Saves from before R146 hold persistent:0b on these types. Null it
    // as the entity joins, before anyone tracks it: a later resetScale()
    // would null it while Netty may be encoding this ScaleData.
    public static void clearLegacyPersistence(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide()) {
            return;
        }
        clearLegacyPersistence(YellowTemperanceScaleTypes.widthScale()
                .getScaleData(entity));
        clearLegacyPersistence(YellowTemperanceScaleTypes.heightScale()
                .getScaleData(entity));
    }

    private static void clearLegacyPersistence(ScaleData data) {
        if (data.getPersistence() != null) {
            data.setPersistence(null);
        }
    }

    // Persistence stays at Pehkui's null default (the types are registered
    // non-persistent). Setting it to false and then calling resetScale()
    // flips it false -> null while Netty may be encoding this ScaleData, which
    // NPEs in ScaleData.toPacket and kicks remote clients.
    private static void set(ScaleData data, float value) {
        data.setScaleTickDelay(0);
        data.setScale(value);
    }

    // Data already at its defaults is left alone: a reset would only mark
    // it for another pehkui:scale sync (every tick while the user lies dead,
    // and on every mob death).
    private static void reset(ScaleData data) {
        if (data.isReset()) {
            return;
        }
        data.setScaleTickDelay(0);
        data.resetScale();
    }

    private static float clamp(float value) {
        return Math.max(MIN, Math.min(MAX, value));
    }
}
