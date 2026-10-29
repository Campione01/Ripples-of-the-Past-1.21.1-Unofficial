package rotp.addons.stonefree.client;

import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.entities.SimpleEntityRenderer;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.client.standskin.StandSkin;
import rotp.core.util.functions.MathUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import rotp.addons.stonefree.StoneFreeAddon;
import rotp.addons.stonefree.entity.StoneFreeStringEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class StoneFreeStringRenderer
        extends EntityRenderer<StoneFreeStringEntity> {
    private static final ResourceLocation BASE_TEXTURE =
            StoneFreeAddon.id(
                    "textures/entity/projectiles/sf_strings.png");
    private static final float SEGMENT_LENGTH = 8.0F / 16.0F;
    private static final float TIP_LENGTH = 2.0F / 16.0F;
    private static final double MIN_LENGTH_SQR = 1.0E-8D;

    private final ModelPart segment;
    private final ModelPart spike;
    private final ModelPart extendedPunch;

    public StoneFreeStringRenderer(
            EntityRendererProvider.Context context) {
        super(context);
        ModelPart root = createLayer().bakeRoot();
        segment = root.getChild("segment");
        spike = root.getChild("spike");
        extendedPunch = root.getChild("extended_punch");
    }

    private static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild(
                "segment",
                CubeListBuilder.create()
                        .texOffs(0, 9)
                        .addBox(
                                -1.5F,
                                -23.0F,
                                1.0F,
                                1.0F,
                                1.0F,
                                8.0F),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        root.addOrReplaceChild(
                "spike",
                CubeListBuilder.create()
                        .texOffs(0, 18)
                        .addBox(
                                -2.0F, -23.5F, 0.0F,
                                2.0F, 2.0F, 1.0F)
                        .texOffs(6, 18)
                        .addBox(
                                -2.0F, -23.5F, -0.8F,
                                2.0F, 2.0F, 1.0F,
                                new CubeDeformation(-0.2F))
                        .texOffs(6, 18)
                        .addBox(
                                -2.0F, -23.5F, -1.2F,
                                2.0F, 2.0F, 1.0F,
                                new CubeDeformation(-0.2F))
                        .texOffs(12, 18)
                        .addBox(
                                -1.5F, -23.0F, -2.0F,
                                1.0F, 1.0F, 1.0F)
                        .texOffs(16, 18)
                        .addBox(
                                -1.5F, -23.0F, -2.8F,
                                1.0F, 1.0F, 1.0F,
                                new CubeDeformation(-0.2F)),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        root.addOrReplaceChild(
                "extended_punch",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(
                                -3.0F, -24.0F, -5.0F,
                                4.0F, 3.0F, 6.0F,
                                new CubeDeformation(-0.001F))
                        .texOffs(10, 9)
                        .addBox(
                                -3.0F, -24.0F, -3.0F,
                                4.0F, 3.0F, 4.0F,
                                new CubeDeformation(0.099F))
                        .texOffs(18, 2)
                        .addBox(
                                -3.0F, -24.5F, -5.1F,
                                1.0F, 1.0F, 1.0F,
                                new CubeDeformation(-0.2F))
                        .texOffs(18, 4)
                        .addBox(
                                -2.0F, -24.5F, -5.1F,
                                1.0F, 1.0F, 1.0F,
                                new CubeDeformation(-0.2F))
                        .texOffs(14, 2)
                        .addBox(
                                -1.0F, -24.5F, -5.1F,
                                1.0F, 1.0F, 1.0F,
                                new CubeDeformation(-0.2F))
                        .texOffs(14, 4)
                        .addBox(
                                0.0F, -24.5F, -5.1F,
                                1.0F, 1.0F, 1.0F,
                                new CubeDeformation(-0.2F))
                        .texOffs(14, 0)
                        .addBox(
                                -3.0F, -24.5F, -5.1F,
                                4.0F, 1.0F, 1.0F,
                                new CubeDeformation(-0.2F)),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public ResourceLocation getTextureLocation(
            StoneFreeStringEntity entity) {
        StandSkin skin =
                SimpleEntityRenderer.getStandSkin(entity);
        return skin != null
                ? skin.getTexture(BASE_TEXTURE)
                : BASE_TEXTURE;
    }

    @Override
    public void render(
            StoneFreeStringEntity entity,
            float yRotation,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        StandVisualContext visualContext =
                StoneFreeVisuals.context(entity, partialTick);
        if (!ClientGlobals.canSeeStands
                || visualContext == null
                || visualContext.effectiveAlpha() <= 0.0F) {
            return;
        }

        LivingEntity barrierUser = entity.kind() == StoneFreeStringEntity.Kind.USER_BARRIER
                && !entity.isBarrierAttached() ? entity.getCurrentBarrierUser() : null;
        Vec3 endpoint = barrierUser != null
                ? StoneFreeBarrierGeometry.looseEndpoint(
                        renderPosition(barrierUser, partialTick), barrierUser.getEyeHeight())
                : entity.getPosition(partialTick);
        Vec3 origin = entity.getOriginPoint(partialTick);
        Vec3 extent = endpoint.subtract(origin);
        if (extent.lengthSqr() <= MIN_LENGTH_SQR) {
            return;
        }

        LivingEntity owner = entity.getOwner();
        if (owner != null) {
            packedLight = entityRenderDispatcher
                    .getPackedLightCoords(owner, partialTick);
        }
        MultiBufferSource alphaBuffer =
                AlphaMultiBufferSource.wrap(
                        buffer,
                        visualContext.effectiveAlpha());
        VertexConsumer vertices = alphaBuffer.getBuffer(
                ModRenderTypes.standTranslucent(
                        getTextureLocation(entity)));

        int color = 0xFFFFFFFF;
        poseStack.pushPose();
        if (entity.kind() == StoneFreeStringEntity.Kind.USER_BARRIER) {
            if (barrierUser != null) {
                StoneFreeBarrierGeometry.renderAnchored(
                        endpoint, origin, renderPosition(entity, partialTick),
                        poseStack, vertices, packedLight, color);
            }
            else {
                StoneFreeBarrierGeometry.render(
                        extent, poseStack, vertices, packedLight, color);
            }
        }
        else {
            poseStack.scale(1.0F, -1.0F, -1.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(
                    MathUtil.yRotDegFromVec(extent)));
            poseStack.mulPose(Axis.XP.rotationDegrees(
                    MathUtil.xRotDegFromVec(extent)));
            renderParts(
                    entity.kind(),
                    (float) extent.length(),
                    poseStack,
                    vertices,
                    packedLight,
                    color);
        }
        poseStack.popPose();
        super.render(
                entity,
                yRotation,
                partialTick,
                poseStack,
                alphaBuffer,
                packedLight);
    }

    private static Vec3 renderPosition(Entity entity, float partialTick) {
        // LevelRenderer positions both entities using these fields, not xo/yo/zo.
        return new Vec3(
                Mth.lerp((double) partialTick, entity.xOld, entity.getX()),
                Mth.lerp((double) partialTick, entity.yOld, entity.getY()),
                Mth.lerp((double) partialTick, entity.zOld, entity.getZ()));
    }

    private void renderParts(
            StoneFreeStringEntity.Kind kind,
            float length,
            PoseStack poseStack,
            VertexConsumer vertices,
            int packedLight,
            int color) {
        float remaining = length;
        ModelPart mainPart = switch (kind) {
            case USER_SPIKE -> spike;
            case EXTENDED_PUNCH -> extendedPunch;
            default -> null;
        };
        if (mainPart != null && remaining >= TIP_LENGTH) {
            renderPart(
                    mainPart,
                    poseStack,
                    vertices,
                    packedLight,
                    color);
            remaining -= TIP_LENGTH;
        }

        while (remaining >= SEGMENT_LENGTH) {
            renderPart(
                    segment,
                    poseStack,
                    vertices,
                    packedLight,
                    color);
            remaining -= SEGMENT_LENGTH;
            poseStack.translate(
                    0.0D, 0.0D, SEGMENT_LENGTH);
        }
        renderPart(
                segment,
                poseStack,
                vertices,
                packedLight,
                color);
    }

    private static void renderPart(
            ModelPart part,
            PoseStack poseStack,
            VertexConsumer vertices,
            int packedLight,
            int color) {
        part.render(
                poseStack,
                vertices,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                color);
    }
}
