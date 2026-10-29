package rotp.addons.whitesnake.client;

import rotp.addons.whitesnake.WhitesnakeAddon;
import rotp.addons.whitesnake.entity.projectile.DiscProjectile;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public final class DiscModel extends HierarchicalModel<DiscProjectile> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(WhitesnakeAddon.id("disc"), "main");

    private final ModelPart root;
    private final ModelPart disc;

    public DiscModel(ModelPart root) {
        this.root = root;
        this.disc = root.getChild("disc");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition disc = root.addOrReplaceChild(
                "disc",
                CubeListBuilder.create()
                        .texOffs(0, 3)
                        .addBox(
                                -3.0F,
                                -1.5F,
                                0.0F,
                                4.0F,
                                1.0F,
                                2.0F,
                                CubeDeformation.NONE)
                        .texOffs(0, 0)
                        .addBox(
                                -9.0F,
                                -1.0F,
                                -7.0F,
                                16.0F,
                                0.0F,
                                16.0F,
                                CubeDeformation.NONE),
                PartPose.offset(0.0F, -1.0F, 0.0F));
        disc.addOrReplaceChild(
                "center",
                CubeListBuilder.create()
                        .texOffs(0, 6)
                        .addBox(
                                -2.0F,
                                0.0F,
                                -1.0F,
                                4.0F,
                                1.0F,
                                2.0F,
                                CubeDeformation.NONE),
                PartPose.offsetAndRotation(
                        -1.0F, -1.5F, 1.0F, 0.0F, -1.5708F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(
            DiscProjectile entity,
            float walkAnimPos,
            float walkAnimSpeed,
            float ticks,
            float yRotationOffset,
            float xRotation) {
        disc.xRot = xRotation * ((float) Math.PI / 180.0F);
        disc.yRot = yRotationOffset * ((float) Math.PI / 180.0F);
    }
}
