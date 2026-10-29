package rotp.addons.tusk.client;

import rotp.core.client.firstperson.FirstPersonModelLayer;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import rotp.addons.tusk.TuskAddon;
import rotp.addons.tusk.entity.WormholeArmEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class TuskArmPortalLayer extends RenderLayer<
        AbstractClientPlayer,
        PlayerModel<AbstractClientPlayer>>
        implements FirstPersonModelLayer {
    private static final ResourceLocation TEXTURE =
            TuskAddon.id("textures/entity/tiny_wormhole.png");
    private final ModelPart rightShoulderPortal =
            TuskWormholeRenderUtil.createShoulderPortalPlane(false);
    private final ModelPart leftShoulderPortal =
            TuskWormholeRenderUtil.createShoulderPortalPlane(true);

    public TuskArmPortalLayer(
            RenderLayerParent<
                    AbstractClientPlayer,
                    PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            AbstractClientPlayer player,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        WormholeArmEntity arm = findArm(player);
        if (arm == null || player.isInvisible()) {
            return;
        }
        StandVisualContext visualContext =
                TuskVisuals.forOwner(player, partialTick);
        if (visualContext == null
                || visualContext.effectiveAlpha() <= 0.0F) {
            return;
        }
        MultiBufferSource standBuffer =
                AlphaMultiBufferSource.wrap(
                        buffer,
                        visualContext.effectiveAlpha());
        ModelPart playerArm = arm.isLeftArm()
                ? getParentModel().leftArm
                : getParentModel().rightArm;
        ModelPart shoulderPortal = arm.isLeftArm()
                ? leftShoulderPortal
                : rightShoulderPortal;
        shoulderPortal.xRot = (arm.tickCount + partialTick)
                * TuskWormholeRenderUtil.ROTATION_RADIANS_PER_TICK;
        poseStack.pushPose();
        playerArm.translateAndRotate(poseStack);
        TuskWormholeRenderUtil.renderModelPart(
                shoulderPortal,
                poseStack,
                standBuffer,
                TEXTURE,
                packedLight,
                TuskClientRenderDiagnostics.VisualRoute
                        .REMOTE_ARM_SHOULDER_PORTAL,
                arm,
                player.getUUID());
        poseStack.popPose();
        TuskClientRenderDiagnostics.recordFixedScreenRender(
                TuskClientRenderDiagnostics.VisualRoute
                        .REMOTE_ARM_SHOULDER_PORTAL,
                arm,
                player.getUUID());
    }

    @Override
    public void renderHandFirstPerson(
            HumanoidArm side,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int light,
            LivingEntity entity,
            LivingEntityRenderer<?, ?> entityRenderer,
            float partialTick) {
        if (entity instanceof Player player) {
            WormholeArmEntity arm = findArm(player);
            if (arm != null && arm.armSide() == side) {
                StandVisualContext visualContext =
                        TuskVisuals.forOwner(player, partialTick);
                if (visualContext == null
                        || visualContext.effectiveAlpha() <= 0.0F) {
                    return;
                }
                renderFirstPersonPortal(
                        arm,
                        side,
                        poseStack,
                        AlphaMultiBufferSource.wrap(
                                buffer,
                                visualContext.effectiveAlpha()),
                        partialTick);
            }
        }
    }

    public static void renderFirstPersonPortal(
            WormholeArmEntity arm,
            HumanoidArm side,
            PoseStack poseStack,
            MultiBufferSource buffer,
            float partialTick) {
        float direction = side == HumanoidArm.LEFT ? 1.0F : -1.0F;
        poseStack.pushPose();
        poseStack.translate(
                direction * 0.36D, -0.25D, -0.32D);
        poseStack.mulPose(Axis.ZP.rotationDegrees(
                (arm.tickCount + partialTick)
                        * TuskWormholeRenderUtil
                                .ROTATION_DEGREES_PER_TICK));
        TuskWormholeRenderUtil.renderTinyVertical(
                poseStack,
                buffer,
                TEXTURE,
                TuskWormholeRenderUtil.TINY_PORTAL_HALF_SIZE,
                TuskClientRenderDiagnostics.VisualRoute
                        .REMOTE_ARM_FIRST_PERSON_PORTAL,
                arm,
                arm.getOwnerUuid());
        poseStack.popPose();
        TuskClientRenderDiagnostics.recordFixedScreenRender(
                TuskClientRenderDiagnostics.VisualRoute
                        .REMOTE_ARM_FIRST_PERSON_PORTAL,
                arm,
                arm.getOwnerUuid());
    }

    public static WormholeArmEntity findArm(Player player) {
        if (player == null
                || !(player.level() instanceof ClientLevel level)) {
            return null;
        }
        WormholeArmEntity result = null;
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof WormholeArmEntity arm
                    && !arm.isRemoved()
                    && player.getUUID().equals(arm.getOwnerUuid())
                    && (result == null
                            || arm.getUUID().toString().compareTo(
                                    result.getUUID().toString()) < 0)) {
                result = arm;
            }
        }
        return result;
    }
}
