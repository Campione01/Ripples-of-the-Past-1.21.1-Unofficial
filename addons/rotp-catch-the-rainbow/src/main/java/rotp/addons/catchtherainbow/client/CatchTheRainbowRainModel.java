package rotp.addons.catchtherainbow.client;

import rotp.addons.catchtherainbow.CatchTheRainbowAddon;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;

public final class CatchTheRainbowRainModel
        extends HierarchicalModel<AbstractClientPlayer> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(
                    CatchTheRainbowAddon.id(
                            "catch_the_rainbow_rain_drops"),
                    "main");

    private final ModelPart root;
    private final ModelPart droplets;

    public CatchTheRainbowRainModel(ModelPart root) {
        this.root = root;
        droplets = root.getChild("droplets");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeDeformation shrink =
                new CubeDeformation(-0.2F);
        root.addOrReplaceChild(
                "droplets",
                CubeListBuilder.create()
                        .texOffs(10, 2).addBox(
                                -3.0F,
                                20.0F,
                                -2.0F,
                                1.0F,
                                2.0F,
                                1.0F,
                                shrink)
                        .texOffs(10, 0).addBox(
                                2.0F,
                                18.0F,
                                -1.0F,
                                1.0F,
                                3.0F,
                                1.0F,
                                shrink)
                        .texOffs(11, 2).addBox(
                                -1.0F,
                                19.0F,
                                2.0F,
                                1.0F,
                                2.0F,
                                1.0F,
                                shrink)
                        .texOffs(11, 0).addBox(
                                1.0F,
                                21.0F,
                                1.0F,
                                1.0F,
                                2.0F,
                                1.0F,
                                shrink),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 16, 16);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(
            AbstractClientPlayer player,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        droplets.y = (ageInTicks % 4.0F) - 2.0F;
        droplets.yRot = ageInTicks * 0.08F;
    }
}
