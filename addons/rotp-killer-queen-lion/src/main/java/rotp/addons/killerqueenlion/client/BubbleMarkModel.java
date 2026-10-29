package rotp.addons.killerqueenlion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.killerqueenlion.entity.BubbleMarkEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;

public final class BubbleMarkModel extends EntityModel<BubbleMarkEntity> {
    private final ModelPart mark;

    public BubbleMarkModel(ModelPart root) {
        mark = root.getChild("mark");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild(
                "mark",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-1.5F, -1.5F, -0.5F, 3.0F, 3.0F, 1.0F)
                        .texOffs(0, 4)
                        .addBox(-0.5F, -1.5F, -1.5F, 1.0F, 3.0F, 3.0F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 16, 16);
    }

    @Override
    public void setupAnim(
            BubbleMarkEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        mark.yRot = netHeadYaw * ((float) Math.PI / 180.0F);
        mark.xRot = headPitch * ((float) Math.PI / 180.0F);
        mark.zRot = ageInTicks * 0.05F;
    }

    @Override
    public void renderToBuffer(
            PoseStack poseStack,
            VertexConsumer vertices,
            int packedLight,
            int packedOverlay,
            int color) {
        poseStack.pushPose();
        poseStack.scale(0.6F, 0.6F, 0.6F);
        mark.render(poseStack, vertices, packedLight, packedOverlay, color);
        poseStack.popPose();
    }
}
