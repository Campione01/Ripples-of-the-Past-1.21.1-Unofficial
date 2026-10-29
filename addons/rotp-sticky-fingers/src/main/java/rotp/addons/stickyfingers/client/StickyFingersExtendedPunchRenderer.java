package rotp.addons.stickyfingers.client;

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

import rotp.addons.stickyfingers.StickyFingersAddon;
import rotp.addons.stickyfingers.entity.StickyFingersExtendedPunchEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class StickyFingersExtendedPunchRenderer
        extends EntityRenderer<StickyFingersExtendedPunchEntity> {
    private static final ResourceLocation BASE_TEXTURE =
            StickyFingersAddon.id(
                    "textures/entity/"
                            + "sticky_fingers_ranged_attack.png");
    private static final float ZIPPER_SEGMENT_LENGTH = 6.0F / 16.0F;

    private final ModelPart forearm;
    private final ModelPart zipperSegment;

    public StickyFingersExtendedPunchRenderer(
            EntityRendererProvider.Context context) {
        super(context);
        ModelPart root = SegmentLayout.mesh().bakeRoot();
        forearm = root.getChild("forearm");
        zipperSegment = root.getChild("zipper_segment");
    }

    @Override
    public ResourceLocation getTextureLocation(
            StickyFingersExtendedPunchEntity entity) {
        StandSkin standSkin = SimpleEntityRenderer.getStandSkin(entity);
        return standSkin != null
                ? standSkin.getTexture(BASE_TEXTURE)
                : BASE_TEXTURE;
    }

    @Override
    public void render(
            StickyFingersExtendedPunchEntity entity,
            float yRotation,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        if (!entity.isInvisible()
                || !entity.isInvisibleTo(
                        Minecraft.getInstance().player)) {
            renderExtendedArm(
                    entity,
                    partialTick,
                    poseStack,
                    buffer,
                    ownerPackedLight(
                            entity, partialTick, packedLight),
                    alpha(entity, partialTick));
            super.render(
                    entity,
                    yRotation,
                    partialTick,
                    poseStack,
                    buffer,
                    packedLight);
        }
    }

    private int ownerPackedLight(
            StickyFingersExtendedPunchEntity entity,
            float partialTick,
            int fallbackLight) {
        LivingEntity owner = entity.getOwner();
        return owner != null
                ? entityRenderDispatcher.getPackedLightCoords(
                        owner, partialTick)
                : fallbackLight;
    }

    private float alpha(
            StickyFingersExtendedPunchEntity entity,
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

    private void renderExtendedArm(
            StickyFingersExtendedPunchEntity entity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            float alpha) {
        Vec3 endpoint = entity.getPosition(partialTick);
        Vec3 origin = entity.getOriginPoint(partialTick);
        Vec3 extent = endpoint.subtract(origin);
        float length = (float) extent.length();
        if (length <= 1.0E-5F) {
            return;
        }

        poseStack.pushPose();
        try {
            poseStack.scale(1.0F, -1.0F, -1.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(
                    MathUtil.yRotDegFromVec(extent)));
            poseStack.mulPose(Axis.XP.rotationDegrees(
                    MathUtil.xRotDegFromVec(extent)));
            // Two-sided as in the donor: the flat chain is textured on one face only.
            RenderType renderType = alpha > 0.0F
                    ? ModRenderTypes.standTranslucent(
                            getTextureLocation(entity))
                    : buffer instanceof OutlineBufferSource
                            ? RenderType.outline(
                                    getTextureLocation(entity))
                            : null;
            if (renderType != null) {
                MultiBufferSource renderBuffer = alpha > 0.0F
                        ? AlphaMultiBufferSource.wrap(buffer, alpha)
                        : buffer;
                renderSegments(
                        length,
                        poseStack,
                        renderBuffer.getBuffer(renderType),
                        packedLight);
            }
        } finally {
            poseStack.popPose();
        }
    }

    private void renderSegments(
            float length,
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            int packedLight) {
        if (SegmentLayout.renderForearm(length)) {
            renderSegment(
                    forearm,
                    poseStack,
                    vertexConsumer,
                    packedLight);
        }
        int segmentCount = SegmentLayout.zipperCount(length);
        for (int segment = 0; segment < segmentCount; segment++) {
            renderSegment(
                    zipperSegment,
                    poseStack,
                    vertexConsumer,
                    packedLight);
            if (segment + 1 < segmentCount) {
                poseStack.translate(
                        0.0D, 0.0D, ZIPPER_SEGMENT_LENGTH);
            }
        }
    }

    static final class SegmentLayout {
        // Donor allocation is one pixel, not the forearm mesh's six-pixel length.
        private static final float MAIN_PART_BUDGET = 1.0F / 16.0F;

        static boolean renderForearm(float length) {
            return length >= MAIN_PART_BUDGET;
        }

        static int zipperCount(float length) {
            float remaining = renderForearm(length) ? length - MAIN_PART_BUDGET : length;
            int count = 1; // The donor always draws a final zipper segment.
            while (remaining >= ZIPPER_SEGMENT_LENGTH) {
                remaining -= ZIPPER_SEGMENT_LENGTH;
                count++;
            }
            return count;
        }

        static LayerDefinition mesh() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            // Donor ExtendedPunchNewModel: the turned box reaches ahead of the entity, away from the chain.
            PartDefinition forearm = root.addOrReplaceChild(
                    "forearm",
                    CubeListBuilder.create()
                            .texOffs(56, 0)
                            .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F)
                            .texOffs(56, 10)
                            .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.25F)),
                    PartPose.rotation(-1.5708F, 0.0F, 1.5708F));
            forearm.addOrReplaceChild(
                    "zipper_slider",
                    CubeListBuilder.create()
                            .texOffs(72, 12)
                            .addBox(-0.5F, 0.5F, -1.5F, 1.0F, 4.0F, 3.0F)
                            .texOffs(72, 19)
                            .addBox(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F),
                    PartPose.offsetAndRotation(-2.25F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0436F));
            root.addOrReplaceChild(
                    "zipper_segment",
                    CubeListBuilder.create()
                            .texOffs(74, 12)
                            .addBox(-1.0F, 0.0F, 0.0F, 2.0F, 0.0F, 6.0F),
                    PartPose.ZERO);
            return LayerDefinition.create(mesh, 128, 128);
        }
    }

    private static void renderSegment(
            ModelPart segment,
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            int packedLight) {
        segment.render(
                poseStack,
                vertexConsumer,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                0xFFFFFFFF);
    }
}
