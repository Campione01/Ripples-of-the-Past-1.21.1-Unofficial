package rotp.addons.tickettoride.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.tickettoride.TicketToRideAddon;
import rotp.addons.tickettoride.entity.DeadPhotoMementoEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public final class DeadPhotoMementoModel
        extends EntityModel<DeadPhotoMementoEntity> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(
                    TicketToRideAddon.id(
                            "dead_photo_memento"),
                    "main");

    private final ModelPart root;

    public DeadPhotoMementoModel(ModelPart root) {
        this.root = root;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeListBuilder mass = CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(
                        18.3881F, -36.4963F, 14.9855F,
                        16.0F, 16.0F, 16.0F,
                        new CubeDeformation(4.0F))
                .texOffs(0, 32)
                .addBox(
                        15.8418F, -40.6104F, -9.9758F,
                        16.0F, 16.0F, 16.0F,
                        new CubeDeformation(5.0F))
                .texOffs(0, 32)
                .addBox(
                        39.8418F, -9.6104F, 11.0242F,
                        16.0F, 16.0F, 16.0F,
                        new CubeDeformation(3.0F))
                .texOffs(0, 32)
                .addBox(
                        37.8418F, -40.6104F, 14.0242F,
                        16.0F, 16.0F, 16.0F,
                        new CubeDeformation(3.0F))
                .texOffs(0, 32)
                .addBox(
                        26.8418F, -22.6104F, 0.0242F,
                        16.0F, 16.0F, 16.0F,
                        new CubeDeformation(13.0F))
                .texOffs(0, 0)
                .addBox(
                        13.8418F, -11.6104F, -14.9758F,
                        16.0F, 16.0F, 16.0F,
                        new CubeDeformation(5.0F))
                .texOffs(0, 32)
                .addBox(
                        13.8418F, 5.7793F, 17.0242F,
                        16.0F, 3.6104F, 16.0F,
                        new CubeDeformation(4.0F))
                .texOffs(0, 0)
                .addBox(
                        13.8418F, -6.6104F, 17.0242F,
                        16.0F, 4.3896F, 16.0F,
                        new CubeDeformation(4.0F))
                .texOffs(0, 0)
                .addBox(
                        35.8418F, -6.6104F, -14.9758F,
                        16.0F, 16.0F, 16.0F,
                        new CubeDeformation(4.0F))
                .texOffs(0, 0)
                .addBox(
                        39.8418F, -36.6104F, -14.9758F,
                        16.0F, 16.0F, 16.0F,
                        new CubeDeformation(4.0F))
                .texOffs(0, 0)
                .addBox(
                        11.8418F, -37.6104F, 11.0242F,
                        16.0F, 16.0F, 16.0F,
                        new CubeDeformation(4.0F));
        PartDefinition bone = root.addOrReplaceChild(
                "bone",
                mass,
                PartPose.offsetAndRotation(
                        -11.0F,
                        22.0F,
                        2.0F,
                        0.7418F,
                        0.0F,
                        0.0F));
        bone.addOrReplaceChild(
                "cube_r1",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(
                                -1.0F,
                                -16.0F,
                                -15.0F,
                                16.0F,
                                16.0F,
                                16.0F,
                                new CubeDeformation(4.0F)),
                PartPose.offsetAndRotation(
                        21.8418F,
                        -8.6104F,
                        0.0242F,
                        0.2753F,
                        -0.7926F,
                        -0.3776F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(
            DeadPhotoMementoEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
    }

    @Override
    public void renderToBuffer(
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay,
            int color) {
        root.render(
                poseStack,
                buffer,
                packedLight,
                packedOverlay,
                color);
    }
}
