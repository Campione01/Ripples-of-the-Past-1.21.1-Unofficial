package rotp.addons.d4c.client;

import rotp.addons.d4c.entity.D4CSummonKickEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class D4CSummonKickRenderer
        extends HumanoidMobRenderer<
                D4CSummonKickEntity,
                D4CSummonKickRenderer.KickModel> {
    public D4CSummonKickRenderer(
            EntityRendererProvider.Context context) {
        super(
                context,
                new KickModel(
                        context.bakeLayer(ModelLayers.PLAYER),
                        false),
                0.35F);
        wideModel = model;
        slimModel = new KickModel(
                context.bakeLayer(ModelLayers.PLAYER_SLIM),
                true);
    }

    private final KickModel wideModel;
    private final KickModel slimModel;

    @Override
    public void render(
            D4CSummonKickEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        PlayerSkin skin = D4CPlayerSkinResolver.resolve(
                entity.profileId(), entity.profileName());
        model = skin.model() == PlayerSkin.Model.SLIM
                ? slimModel
                : wideModel;
        super.render(
                entity,
                entityYaw,
                partialTick,
                poseStack,
                buffer,
                packedLight);
    }

    @Override
    protected void setupRotations(
            D4CSummonKickEntity entity,
            PoseStack poseStack,
            float bob,
            float yBodyRot,
            float partialTick,
            float scale) {
        super.setupRotations(
                entity,
                poseStack,
                bob,
                yBodyRot,
                partialTick,
                scale);
        float pitch = Mth.lerp(
                partialTick, entity.xRotO, entity.getXRot());
        switch (entity.phase()) {
            case D4CSummonKickEntity.WINDUP ->
                    poseStack.mulPose(
                            Axis.XP.rotationDegrees(-15.0F));
            case D4CSummonKickEntity.FLIGHT -> {
                poseStack.mulPose(
                        Axis.XP.rotationDegrees(pitch * -0.8F));
                poseStack.mulPose(
                        Axis.ZP.rotationDegrees(-10.0F));
            }
            case D4CSummonKickEntity.IMPACT -> {
                poseStack.mulPose(
                        Axis.XP.rotationDegrees(-45.0F));
                poseStack.mulPose(
                        Axis.ZP.rotationDegrees(-20.0F));
            }
            default -> {
                poseStack.mulPose(
                        Axis.XP.rotationDegrees(-30.0F));
                poseStack.mulPose(
                        Axis.ZP.rotationDegrees(-15.0F));
            }
        }
    }

    @Override
    public ResourceLocation getTextureLocation(
            D4CSummonKickEntity entity) {
        return D4CPlayerSkinResolver.resolve(
                entity.profileId(), entity.profileName()).texture();
    }

    static final class KickModel
            extends PlayerModel<D4CSummonKickEntity> {
        private KickModel(
                net.minecraft.client.model.geom.ModelPart root,
                boolean slim) {
            super(root, slim);
        }

        @Override
        public void setupAnim(
                D4CSummonKickEntity entity,
                float limbSwing,
                float limbSwingAmount,
                float ageInTicks,
                float netHeadYaw,
                float headPitch) {
            super.setupAnim(
                    entity,
                    limbSwing,
                    limbSwingAmount,
                    ageInTicks,
                    netHeadYaw,
                    headPitch);
            if (entity.phase() == D4CSummonKickEntity.WINDUP) {
                return;
            }
            float pitch = headPitch * Mth.DEG_TO_RAD;
            float yaw = netHeadYaw * Mth.DEG_TO_RAD;
            boolean impact =
                    entity.phase() == D4CSummonKickEntity.IMPACT;
            float legBase = impact ? -1.8F : -1.4F;
            float armBase = impact ? -0.8F : -0.5F;
            float bodyBase = impact ? 0.4F : 0.2F;
            float extendedLeg = legBase + pitch * 0.5F;
            rightLeg.xRot = Mth.clamp(
                    extendedLeg, -2.2F, -0.8F);
            leftLeg.xRot =
                    0.2F - (extendedLeg + legBase) * 0.3F;
            body.xRot = bodyBase + pitch * 0.2F;
            body.yRot = yaw * 0.1F;
            rightArm.xRot = armBase + pitch * 0.3F;
            leftArm.xRot = 0.3F - pitch * 0.2F;
            head.xRot = pitch * 0.4F;
            head.yRot = yaw * 0.3F;
            if (headPitch < -30.0F) {
                rightLeg.xRot = Math.min(
                        rightLeg.xRot - 0.3F, -0.5F);
                body.xRot += 0.2F;
            } else if (headPitch > 15.0F) {
                rightLeg.xRot = Math.max(
                        rightLeg.xRot + 0.2F, -2.0F);
                body.xRot -= 0.1F;
            }
        }
    }
}
