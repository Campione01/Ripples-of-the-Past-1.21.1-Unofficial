package rotp.addons.neo_stand.client;

import javax.annotation.Nullable;

import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import rotp.addons.neo_stand.NeoStandAddon;
import rotp.addons.neo_stand.entity.PlantAttackEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class PlantAttackRenderer
        extends EntityRenderer<PlantAttackEntity> {
    private static final ResourceLocation TEXTURE =
            NeoStandAddon.id(
                    "textures/entity/plant_attack.png");
    private static final float[] GROWTH_TIMES = {
            0.0F, 0.0801F, 0.1603F,
            0.2404F, 0.3205F, 0.4006F
    };
    private static final float[] GROWTH_X = {
            0.1F, 0.7F, 0.8F, 0.9F, 1.0F, 1.0F
    };
    private static final float[] GROWTH_Y = {
            0.1F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F
    };
    private static final float[] GROWTH_Z = {
            0.0F, 0.3F, 0.5F, 0.6F, 0.8F, 1.0F
    };

    private final PlantAttackModel model;

    public PlantAttackRenderer(
            EntityRendererProvider.Context context) {
        super(context);
        model = new PlantAttackModel(
                context.bakeLayer(
                        PlantAttackModel.LAYER_LOCATION));
        shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(
            PlantAttackEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(
            PlantAttackEntity entity,
            Frustum frustum,
            double cameraX,
            double cameraY,
            double cameraZ) {
        StandVisualContext context =
                visualContext(entity);
        return ClientGlobals.canSeeStands
                && context != null
                && context.effectiveAlpha() > 0.0F;
    }

    @Override
    public void render(
            PlantAttackEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        StandVisualContext context = visualContext(entity);
        if (!ClientGlobals.canSeeStands
                || context == null
                || context.effectiveAlpha() <= 0.0F) {
            return;
        }

        MultiBufferSource alphaBuffer =
                AlphaMultiBufferSource.wrap(
                        buffer, context.effectiveAlpha());
        Vec3 forward = entity.getForward();
        float yaw = (float) Math.toDegrees(
                Math.atan2(forward.x, forward.z));
        float pitch = (float) Math.toDegrees(
                Math.asin(forward.y));

        poseStack.pushPose();
        poseStack.mulPose(
                Axis.YP.rotationDegrees(yaw + 180.0F));
        poseStack.mulPose(
                Axis.XP.rotationDegrees(pitch));
        poseStack.translate(-0.09375D, -1.6625D, 1.75D);
        applyGrowth(
                poseStack,
                (entity.tickCount + partialTick) / 20.0F,
                (float) (entity.getLength()
                        / PlantAttackEntity.DEFAULT_LENGTH));
        model.renderToBuffer(
                poseStack,
                alphaBuffer.getBuffer(
                        ModRenderTypes.standTranslucentCull(
                                TEXTURE)),
                packedLight,
                OverlayTexture.NO_OVERLAY,
                0xFFFFFFFF);
        poseStack.popPose();
        super.render(
                entity,
                entityYaw,
                partialTick,
                poseStack,
                alphaBuffer,
                packedLight);
    }

    private static void applyGrowth(
            PoseStack poseStack,
            float time,
            float lengthScale) {
        float x = 1.0F;
        float y = 1.0F;
        float z = 1.0F;
        if (time <= GROWTH_TIMES[GROWTH_TIMES.length - 1]) {
            int index = 0;
            while (index < GROWTH_TIMES.length - 2
                    && time > GROWTH_TIMES[index + 1]) {
                index++;
            }
            float progress = (time - GROWTH_TIMES[index])
                    / (GROWTH_TIMES[index + 1]
                            - GROWTH_TIMES[index]);
            x = lerp(
                    GROWTH_X[index],
                    GROWTH_X[index + 1],
                    progress);
            y = lerp(
                    GROWTH_Y[index],
                    GROWTH_Y[index + 1],
                    progress);
            z = lerp(
                    GROWTH_Z[index],
                    GROWTH_Z[index + 1],
                    progress);
        }
        poseStack.scale(x, y, z * lengthScale);
    }

    private static float lerp(
            float start, float end, float progress) {
        return start + (end - start) * progress;
    }

    @Nullable
    private static StandVisualContext visualContext(
            PlantAttackEntity entity) {
        Entity owner = entity.level().getEntity(
                entity.getOwnerId());
        StandEntity stand = owner instanceof StandEntity direct
                ? direct
                : owner instanceof LivingEntity living
                        ? summonedStand(living)
                        : null;
        return stand != null
                ? StandVisualContext.world(stand, 1.0F)
                : null;
    }

    @Nullable
    private static StandEntity summonedStand(
            LivingEntity living) {
        StandPower power = StandPower.get(living);
        return power != null
                ? power.getSummonedStandEntity()
                : null;
    }
}
