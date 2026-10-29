package rotp.addons.hermitpurple.client;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import rotp.core.client.entityanim.playerbend.PlayerModelBends;
import rotp.core.client.entityanim.pose.AnimFramePose;
import rotp.core.client.entityanim.pose.AnimFramePose.ModelPartFrame;
import rotp.core.client.entityanim.pose.AnimatedEntity;
import rotp.core.compat.v1_21_4.renderstate.LivingEntityRenderState;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

final class HermitVineHandOrigin {
    private static final ModelPart CLASSIC = template(false);
    private static final ModelPart SLIM = template(true);

    private HermitVineHandOrigin() {}

    @Nullable
    static Vec3 forPlayer(LivingEntity user, float partialTick) {
        if (!(user instanceof AbstractClientPlayer player)
                || !(user instanceof AnimatedEntity animated)) {
            return null;
        }
        AnimFramePose pose = animated.jojo_ripples$getModelPose(
                AnimatedEntity.PoseType.FINAL);
        float bodyYaw = LivingEntityRenderState.solveBodyRot(player,
                Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot),
                partialTick);
        return wristFromPose(pose, player.getMainArm(),
                player.getSkin().model() == PlayerSkin.Model.SLIM,
                player.getPosition(partialTick), bodyYaw, player.getScale(),
                player.isCrouching());
    }

    @Nullable
    static Vec3 wristFromPose(@Nullable AnimFramePose pose,
            HumanoidArm side, boolean slim, Vec3 playerPosition,
            float bodyYawDegrees, float entityScale, boolean crouching) {
        String armName = side == HumanoidArm.LEFT ? "left_arm" : "right_arm";
        if (pose == null || pose.getIfPresent(armName) == null) {
            return null;
        }
        ModelPart template = slim ? SLIM : CLASSIC;
        ModelPart armTemplate = template.getChild(armName);
        ModelPart.Cube armCube = armTemplate.cubes.getFirst();
        float torsoHeight = PlayerModelBends.getLimbHeight(template.getChild("body"));
        float legHeight = PlayerModelBends.getLimbHeight(template.getChild("right_leg"));
        float bendY = PlayerModelBends.getLimbHeight(armTemplate) * 0.5F
                - armTemplate.getInitialPose().y;

        ModelPart body = posedPart(PartPose.offset(0, legHeight, 0), pose, "body");
        ModelPart torso = posedPart(PartPose.offset(0, torsoHeight * 0.5F, 0), pose, "torso");
        ModelPart torsoBend = posedPart(PartPose.offset(0, torsoHeight * 0.5F, 0), pose, "torso_bend");
        ModelPart arm = posedPart(armTemplate.getInitialPose(), pose, armName);
        ModelPart armBend = posedPart(PartPose.offset(0, bendY, 0), pose, armName + "_bend");

        // Follow PlayerModelBends' upper-body and lower-arm vertex transforms.
        // The private vanilla templates supply geometry only and are never posed.
        PoseStack matrices = new PoseStack();
        body.translateAndRotate(matrices);
        translateBack(matrices, body.getInitialPose());
        translateBack(matrices, torso.getInitialPose());
        torso.translateAndRotate(matrices);
        PlayerModelBends.rotateAndTranslateBack(torsoBend, matrices);
        arm.translateAndRotate(matrices);
        armBend.translateAndRotate(matrices);
        matrices.translate(0.0D, -bendY / 16.0D, 0.0D);
        Vector3f wrist = matrices.last().pose().transformPosition(
                (armCube.minX + armCube.maxX) / 32.0F,
                armCube.maxY / 16.0F,
                (armCube.minZ + armCube.maxZ) / 32.0F,
                new Vector3f());

        // LivingEntityRenderer's model inversion/translation and PlayerRenderer's scale.
        double scale = entityScale * 0.9375D;
        Vec3 offset = new Vec3(wrist.x, 1.501D - wrist.y, -wrist.z)
                .scale(scale).yRot(-bodyYawDegrees * Mth.DEG_TO_RAD);
        if (crouching) {
            offset = offset.add(0.0D, -entityScale * 0.125D, 0.0D);
        }
        Vec3 origin = playerPosition.add(offset);
        return Double.isFinite(origin.x) && Double.isFinite(origin.y)
                        && Double.isFinite(origin.z)
                ? origin : null;
    }

    private static ModelPart template(boolean slim) {
        return LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim),
                64, 64).bakeRoot();
    }

    private static ModelPart posedPart(PartPose initial,
            AnimFramePose pose, String name) {
        ModelPart part = new ModelPart(List.of(), Map.of());
        part.setInitialPose(initial);
        part.resetPose();
        ModelPartFrame frame = pose.getIfPresent(name);
        if (frame != null) {
            frame.apply(part);
        }
        return part;
    }

    private static void translateBack(PoseStack matrices, PartPose initial) {
        matrices.translate(-initial.x / 16.0D, -initial.y / 16.0D, -initial.z / 16.0D);
    }
}
