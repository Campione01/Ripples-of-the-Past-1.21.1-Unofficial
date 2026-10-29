package rotp.addons.killerqueen.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.killerqueen.KillerQueenAddon;
import rotp.addons.killerqueen.entity.PrimaryBombMarkerEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.util.Mth;

public final class PrimaryBombMarkerModel
        extends EntityModel<PrimaryBombMarkerEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(
                    KillerQueenAddon.id(
                            "primary_bomb_marker"),
                    "main");

    private final ModelPart marker;

    public PrimaryBombMarkerModel(ModelPart root) {
        marker = root.getChild("marker");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild(
                "marker",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(
                                -2.0F,
                                -2.0F,
                                -0.5F,
                                4.0F,
                                4.0F,
                                1.0F,
                                new CubeDeformation(0.05F))
                        .texOffs(12, 0)
                        .addBox(
                                -1.75F,
                                -3.0F,
                                -0.25F,
                                1.0F,
                                2.0F,
                                1.0F)
                        .texOffs(16, 0)
                        .addBox(
                                0.75F,
                                -3.0F,
                                -0.25F,
                                1.0F,
                                2.0F,
                                1.0F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public void setupAnim(
            PrimaryBombMarkerEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        marker.yRot = ageInTicks * 0.08F;
        marker.y = Mth.sin(ageInTicks * 0.15F) * 0.75F;
    }

    @Override
    public void renderToBuffer(
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay,
            int color) {
        marker.render(
                poseStack,
                buffer,
                packedLight,
                packedOverlay,
                color);
    }
}
