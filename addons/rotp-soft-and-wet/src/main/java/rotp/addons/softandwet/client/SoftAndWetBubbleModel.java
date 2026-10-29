package rotp.addons.softandwet.client;

import java.util.Random;

import rotp.addons.softandwet.SoftAndWetAddon;
import rotp.addons.softandwet.entity.SoftAndWetBubbleEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public final class SoftAndWetBubbleModel
        extends HierarchicalModel<SoftAndWetBubbleEntity> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(
                    SoftAndWetAddon.id("soft_and_wet_bubble"), "main");

    private final ModelPart root;
    private final ModelPart bubble;

    public SoftAndWetBubbleModel(ModelPart root) {
        this.root = root;
        this.bubble = root.getChild("bubble");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition bubble = root.addOrReplaceChild(
                "bubble",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(
                                -1.25F,
                                -2.25F,
                                -1.25F,
                                2.5F,
                                2.5F,
                                2.5F,
                                CubeDeformation.NONE)
                        .texOffs(48, 0).addBox(
                                -1.55F,
                                -1.95F,
                                -0.72F,
                                3.1F,
                                1.9F,
                                1.44F,
                                new CubeDeformation(0.08F))
                        .texOffs(96, 0).addBox(
                                -0.72F,
                                -2.55F,
                                -1.55F,
                                1.44F,
                                3.1F,
                                3.1F,
                                new CubeDeformation(0.06F))
                        .texOffs(144, 0).addBox(
                                -1.55F,
                                -1.72F,
                                -0.72F,
                                3.1F,
                                1.44F,
                                1.44F,
                                new CubeDeformation(0.12F)),
                PartPose.ZERO);
        bubble.addOrReplaceChild(
                "equator",
                CubeListBuilder.create()
                        .texOffs(0, 64).addBox(
                                -2.0F,
                                -1.3F,
                                -0.18F,
                                4.0F,
                                0.36F,
                                0.36F,
                                new CubeDeformation(0.05F))
                        .texOffs(64, 64).addBox(
                                -2.0F,
                                -0.28F,
                                -0.18F,
                                4.0F,
                                0.36F,
                                0.36F,
                                new CubeDeformation(0.05F)),
                PartPose.rotation(
                        0.0F,
                        (float) Math.PI * 0.25F,
                        0.0F));
        bubble.addOrReplaceChild(
                "meridian",
                CubeListBuilder.create()
                        .texOffs(0, 112).addBox(
                                -0.16F,
                                -2.65F,
                                -1.55F,
                                0.32F,
                                3.8F,
                                0.32F,
                                new CubeDeformation(0.04F))
                        .texOffs(64, 112).addBox(
                                -0.16F,
                                -2.65F,
                                1.23F,
                                0.32F,
                                3.8F,
                                0.32F,
                                new CubeDeformation(0.04F)),
                PartPose.rotation(
                        0.0F,
                        0.0F,
                        (float) Math.PI * 0.125F));
        bubble.addOrReplaceChild(
                "crown",
                CubeListBuilder.create()
                        .texOffs(0, 160).addBox(
                                -0.38F,
                                -2.92F,
                                -0.38F,
                                0.76F,
                                0.76F,
                                0.76F,
                                new CubeDeformation(0.04F))
                        .texOffs(48, 160).addBox(
                                -0.28F,
                                0.15F,
                                -0.28F,
                                0.56F,
                                0.56F,
                                0.56F,
                                new CubeDeformation(0.03F)),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 256, 256);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(
            SoftAndWetBubbleEntity entity,
            float walkAnimPos,
            float walkAnimSpeed,
            float ticks,
            float yRotationOffset,
            float xRotation) {
        bubble.yRot = yRotationOffset * ((float) Math.PI / 180.0F);
        bubble.xRot = xRotation * ((float) Math.PI / 180.0F);

        Random random = new Random(entity.getId());
        float scale = 1.0F + (random.nextFloat() - 0.5F) * 0.4F;
        bubble.xScale = scale;
        bubble.yScale = scale;
        bubble.zScale = scale;
    }
}
