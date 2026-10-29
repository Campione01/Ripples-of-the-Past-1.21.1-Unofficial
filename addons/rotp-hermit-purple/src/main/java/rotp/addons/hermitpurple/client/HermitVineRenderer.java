package rotp.addons.hermitpurple.client;

import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.entities.SimpleEntityRenderer;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.client.standskin.StandSkin;
import rotp.core.customobjects.entity_projectile.OwnerBoundProjectileEntity;
import rotp.core.util.functions.MathUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import rotp.addons.hermitpurple.HermitPurpleAddon;
import rotp.addons.hermitpurple.HermitPurpleSpec;
import rotp.addons.hermitpurple.entity.HermitBarrierVineEntity;
import rotp.addons.hermitpurple.entity.HermitGrappleVineEntity;
import rotp.addons.hermitpurple.entity.HermitPurpleStandEntity;
import rotp.addons.hermitpurple.entity.HermitVineProjectileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class HermitVineRenderer<
        T extends OwnerBoundProjectileEntity>
        extends EntityRenderer<T> {
    private static final ResourceLocation BASE_TEXTURE =
            HermitPurpleAddon.id(
                    "textures/entity/projectiles/hp_vine.png");
    private static final double MIN_LENGTH_SQR = 1.0E-8D;

    private final ModelPart segment;

    public HermitVineRenderer(
            EntityRendererProvider.Context context) {
        super(context);
        segment = HermitVineGeometry.createLayer()
                .bakeRoot()
                .getChild("segment");
    }

    static Vec3 visualOrigin(
            OwnerBoundProjectileEntity entity, float partialTick) {
        if (entity instanceof HermitBarrierVineEntity) {
            return entity.getOriginPoint(partialTick);
        }
        LivingEntity owner = entity.getOwner();
        LivingEntity user = owner instanceof HermitPurpleStandEntity controller
                ? controller.getUser() : owner;
        if (user == null) {
            return entity.getOriginPoint(partialTick);
        }
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 wrist = HermitVineGeometry.startsAtWrist(
                        user == minecraft.getCameraEntity(),
                        minecraft.options.getCameraType())
                ? HermitVineHandOrigin.forPlayer(user, partialTick) : null;
        if (wrist != null) {
            return wrist;
        }
        float bodyYaw = Mth.lerp(partialTick,
                user.yBodyRotO, user.yBodyRot);
        // Only the visual anchor uses the donor's player origin. The invisible
        // controller remains the projectile owner for gameplay and cleanup.
        return HermitVineGeometry.donorOrigin(
                user.getEyePosition(partialTick), bodyYaw * MathUtil.DEG_TO_RAD,
                entity instanceof HermitVineProjectileEntity,
                entity instanceof HermitGrappleVineEntity grapple
                        && grapple.isCommandGrab(),
                user.getMainArm() == HumanoidArm.LEFT);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        StandSkin skin =
                SimpleEntityRenderer.getStandSkin(entity);
        return skin != null
                ? skin.getTexture(BASE_TEXTURE)
                : BASE_TEXTURE;
    }

    @Override
    public void render(
            T entity,
            float yRotation,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        StandVisualContext visualContext =
                HermitPurpleVisuals.context(
                        entity, partialTick);
        if (!ClientGlobals.canSeeStands
                || visualContext == null
                || visualContext.effectiveAlpha() <= 0.0F) {
            return;
        }

        Vec3 endpoint = entity.getPosition(partialTick);
        Vec3 extent = endpoint.subtract(
                visualOrigin(entity, partialTick));
        if (extent.lengthSqr() <= MIN_LENGTH_SQR) {
            return;
        }
        double maxLength =
                entity instanceof HermitBarrierVineEntity
                        ? HermitPurpleSpec.BARRIER_RANGE
                        : HermitPurpleSpec.HELD_VINE_RANGE;
        if (extent.lengthSqr() > maxLength * maxLength) {
            extent = extent.normalize().scale(maxLength);
        }

        MultiBufferSource alphaBuffer =
                AlphaMultiBufferSource.wrap(
                        buffer,
                        visualContext.effectiveAlpha());
        VertexConsumer vertices = alphaBuffer.getBuffer(
                ModRenderTypes.standTranslucent(
                        getTextureLocation(entity)));

        // 1.16 ExtendingEntityRenderer.doRender lit the vine with its owner's
        // light, not the light at the tip.
        LivingEntity owner = entity.getOwner();
        int vineLight = owner != null
                ? entityRenderDispatcher.getPackedLightCoords(
                        owner, partialTick)
                : packedLight;

        poseStack.pushPose();
        poseStack.scale(1.0F, -1.0F, -1.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(
                MathUtil.yRotDegFromVec(extent)));
        poseStack.mulPose(Axis.XP.rotationDegrees(
                MathUtil.xRotDegFromVec(extent)));
        HermitVineGeometry.renderSegments(
                segment,
                (float) extent.length(),
                poseStack,
                vertices,
                vineLight,
                entity instanceof HermitBarrierVineEntity barrier
                                && barrier.wasRipped()
                        ? 0xFFFF3030
                        : 0xFFFFFFFF);
        poseStack.popPose();
        super.render(
                entity,
                yRotation,
                partialTick,
                poseStack,
                alphaBuffer,
                packedLight);
    }
}
