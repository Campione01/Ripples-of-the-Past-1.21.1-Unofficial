package rotp.addons.thesun.client;

import rotp.core.api.client.render.ClientSkyPresentation;
import rotp.core.api.client.render.ClientSkyPresentationQuery;

import rotp.addons.thesun.entity.TheSunEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class TheSunDaylightController {
    // The 1.16.5 donor's TheSunSkyClientState.dayTimeOfDay returns 0.25f, which is
    // what migration/manifests/visual-matrix.json and phase0-evidence.json record.
    // Keep the donor value: skyDarken/starBrightness already force full daylight,
    // so this only decides where the vanilla sun disc sits while The Sun is up.
    public static final float DAY_TIME_OF_DAY = 0.25F;
    public static final float DAY_SKY_DARKEN = 1.0F;
    public static final float DAY_STAR_BRIGHTNESS = 0.0F;
    public static final float DAY_SKY_RED = 0.56F;
    public static final float DAY_SKY_GREEN = 0.75F;
    public static final float DAY_SKY_BLUE = 1.0F;
    public static final float SKY_COLOR_STRENGTH = 0.85F;
    private static final ClientSkyPresentation DAY_PRESENTATION =
            new ClientSkyPresentation() {
                @Override
                public float timeOfDay(
                        float original, float partialTick) {
                    return DAY_TIME_OF_DAY;
                }

                @Override
                public float skyDarken(
                        float original, float partialTick) {
                    return Mth.lerp(
                            0.98F,
                            original,
                            DAY_SKY_DARKEN);
                }

                @Override
                public float starBrightness(
                        float original, float partialTick) {
                    return Mth.lerp(
                            0.98F,
                            original,
                            DAY_STAR_BRIGHTNESS);
                }

                @Override
                public Vec3 skyColor(
                        Vec3 original,
                        Vec3 cameraPosition,
                        float partialTick) {
                    return new Vec3(
                            skyRed((float) original.x),
                            skyGreen((float) original.y),
                            skyBlue((float) original.z));
                }
            };

    private TheSunDaylightController() {}

    public static ClientSkyPresentation presentation(
            ClientSkyPresentationQuery query) {
        Minecraft minecraft = Minecraft.getInstance();
        return query.level() != null
                        && minecraft.level == query.level()
                        && isStableSunInRange(
                                minecraft, query.level())
                ? DAY_PRESENTATION
                : null;
    }

    public static boolean isActive() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null
                && isStableSunInRange(
                        minecraft, minecraft.level);
    }

    public static float skyRed(float original) {
        return Mth.lerp(
                SKY_COLOR_STRENGTH, original, DAY_SKY_RED);
    }

    public static float skyGreen(float original) {
        return Mth.lerp(
                SKY_COLOR_STRENGTH, original, DAY_SKY_GREEN);
    }

    public static float skyBlue(float original) {
        return Mth.lerp(
                SKY_COLOR_STRENGTH, original, DAY_SKY_BLUE);
    }

    private static boolean isStableSunInRange(
            Minecraft minecraft,
            ClientLevel level) {
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Entity cameraEntity = camera.getEntity();
        if (cameraEntity == null) {
            cameraEntity = minecraft.player;
        }
        if (cameraEntity == null) {
            return false;
        }

        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof TheSunEntity theSun
                    && theSun.isAlive()
                    && !theSun.isRemoved()
                    && theSun.isSunStable()
                    && cameraEntity.distanceToSqr(theSun)
                            <= TheSunEntity.EFFECT_RADIUS_SQR) {
                return true;
            }
        }
        return false;
    }
}
