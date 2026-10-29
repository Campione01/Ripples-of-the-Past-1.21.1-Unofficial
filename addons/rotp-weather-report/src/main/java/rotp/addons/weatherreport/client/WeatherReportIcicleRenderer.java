package rotp.addons.weatherreport.client;

import rotp.core.client.entityrender.entities.SimpleEntityRenderer;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.client.standskin.StandSkin;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.util.functions.MathUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import rotp.addons.weatherreport.WeatherReportAddon;
import rotp.addons.weatherreport.entity.WeatherReportIcicleEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class WeatherReportIcicleRenderer
        extends EntityRenderer<WeatherReportIcicleEntity> {
    private static final ResourceLocation BASE_TEXTURE =
            WeatherReportAddon.id(
                    "textures/entity/projectiles/icicle.png");
    private static final float MAIN_PART_LENGTH = 2.0F / 16.0F;
    private static final float REPEATING_PART_LENGTH =
            8.0F / 16.0F;

    private final ModelPart icicleEnd;
    private final ModelPart icicle;

    public WeatherReportIcicleRenderer(
            EntityRendererProvider.Context context) {
        super(context);
        ModelPart root = createIcicleLayer().bakeRoot();
        icicleEnd = root.getChild("icicle_end");
        icicle = root.getChild("icicle");
    }

    private static LayerDefinition createIcicleLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild(
                "icicle",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(
                                -1.0F,
                                -1.0F,
                                -4.0F,
                                2.0F,
                                2.0F,
                                8.0F),
                PartPose.rotation(
                        0.0F,
                        0.0F,
                        -45.0F * Mth.DEG_TO_RAD));
        root.addOrReplaceChild(
                "icicle_end",
                CubeListBuilder.create()
                        .texOffs(1, 2)
                        .addBox(
                                0.0F,
                                -1.5F,
                                -3.0F,
                                0.0F,
                                3.0F,
                                3.0F)
                        .texOffs(-2, 2)
                        .addBox(
                                -1.5F,
                                0.0F,
                                -3.0F,
                                3.0F,
                                0.0F,
                                3.0F),
                PartPose.offsetAndRotation(
                        0.0F,
                        0.0F,
                        -4.0F,
                        0.0F,
                        0.0F,
                        45.0F * Mth.DEG_TO_RAD));
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public ResourceLocation getTextureLocation(
            WeatherReportIcicleEntity entity) {
        StandSkin standSkin =
                SimpleEntityRenderer.getStandSkin(entity);
        return standSkin != null
                ? standSkin.getTexture(BASE_TEXTURE)
                : BASE_TEXTURE;
    }

    @Override
    public void render(
            WeatherReportIcicleEntity entity,
            float yRotation,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        if (!entity.isInvisible()
                || !entity.isInvisibleTo(
                        Minecraft.getInstance().player)) {
            float alpha = getAlpha(entity, partialTick);
            MultiBufferSource visualBuffer = alpha > 0.0F
                    ? AlphaMultiBufferSource.wrap(buffer, alpha)
                    : buffer;
            renderIcicle(
                    entity,
                    partialTick,
                    poseStack,
                    visualBuffer,
                    getOwnerPackedLight(
                            entity, partialTick, packedLight),
                    alpha);
            super.render(
                    entity,
                    yRotation,
                    partialTick,
                    poseStack,
                    buffer,
                    packedLight);
        }
    }

    private int getOwnerPackedLight(
            WeatherReportIcicleEntity entity,
            float partialTick,
            int fallbackLight) {
        LivingEntity owner = entity.getOwner();
        return owner != null
                ? entityRenderDispatcher.getPackedLightCoords(
                        owner, partialTick)
                : fallbackLight;
    }

    private float getAlpha(
            WeatherReportIcicleEntity entity,
            float partialTick) {
        StandEntity stand = entity.getOwner()
                instanceof StandEntity ownerStand
                        ? ownerStand
                        : null;
        if (stand == null || !stand.isAlive()) {
            return 0.0F;
        }
        float lifecycleAlpha = stand.clientStuff != null
                ? stand.clientStuff.getAlpha(stand, partialTick)
                : 1.0F;
        return StandVisualContext.world(
                stand, lifecycleAlpha).effectiveAlpha();
    }

    private void renderIcicle(
            WeatherReportIcicleEntity entity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            float alpha) {
        Vec3 entityPos = entity.getPosition(partialTick);
        Vec3 originPos = entity.getOriginPoint(partialTick);
        Vec3 extent = entityPos.subtract(originPos);
        float length = (float) extent.length();
        if (length <= 1.0E-5F) {
            return;
        }

        poseStack.pushPose();
        poseStack.scale(1.0F, -1.0F, -1.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(
                MathUtil.yRotDegFromVec(extent)));
        poseStack.mulPose(Axis.XP.rotationDegrees(
                MathUtil.xRotDegFromVec(extent)));
        RenderType renderType = alpha > 0.0F
                ? ModRenderTypes.standTranslucentCull(
                        getTextureLocation(entity))
                : buffer instanceof OutlineBufferSource
                        ? RenderType.outline(
                                getTextureLocation(entity))
                        : null;
        if (renderType != null) {
            renderRepeatingIcicle(
                    length,
                    poseStack,
                    buffer.getBuffer(renderType),
                    packedLight);
        }
        poseStack.popPose();
    }

    private void renderRepeatingIcicle(
            float length,
            PoseStack poseStack,
            VertexConsumer vertexBuilder,
            int packedLight) {
        float modelLength = length;
        if (modelLength >= MAIN_PART_LENGTH) {
            renderSegment(
                    icicleEnd,
                    poseStack,
                    vertexBuilder,
                    packedLight);
            modelLength -= MAIN_PART_LENGTH;
        }
        while (modelLength >= REPEATING_PART_LENGTH) {
            renderSegment(
                    icicle,
                    poseStack,
                    vertexBuilder,
                    packedLight);
            modelLength -= REPEATING_PART_LENGTH;
            poseStack.translate(0.0D, 0.0D, REPEATING_PART_LENGTH);
        }
        renderSegment(
                icicle,
                poseStack,
                vertexBuilder,
                packedLight);
    }

    private static void renderSegment(
            ModelPart segment,
            PoseStack poseStack,
            VertexConsumer vertexBuilder,
            int packedLight) {
        segment.render(
                poseStack,
                vertexBuilder,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                0xFFFFFFFF);
    }
}
