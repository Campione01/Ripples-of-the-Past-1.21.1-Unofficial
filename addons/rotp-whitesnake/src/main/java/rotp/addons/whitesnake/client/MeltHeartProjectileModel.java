package rotp.addons.whitesnake.client;

import rotp.addons.whitesnake.WhitesnakeAddon;
import rotp.addons.whitesnake.entity.projectile.MeltHeartProjectile;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public final class MeltHeartProjectileModel
        extends HierarchicalModel<MeltHeartProjectile> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(
                    WhitesnakeAddon.id("melt_heart_projectile"),
                    "main");

    private final ModelPart root;
    private final ModelPart melt;

    public MeltHeartProjectileModel(ModelPart root) {
        this.root = root;
        this.melt = root.getChild("melt");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition melt = root.addOrReplaceChild(
                "melt",
                CubeListBuilder.create()
                        .texOffs(0, 8)
                        .addBox(
                                0.0F,
                                -6.0F,
                                -3.0F,
                                0.0F,
                                8.0F,
                                6.0F,
                                CubeDeformation.NONE),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        melt.addOrReplaceChild(
                "cross_plane",
                CubeListBuilder.create()
                        .texOffs(0, 8)
                        .addBox(
                                0.0F,
                                -3.0F,
                                -3.0F,
                                0.0F,
                                8.0F,
                                6.0F,
                                CubeDeformation.NONE),
                PartPose.offsetAndRotation(
                        0.0F,
                        -3.0F,
                        0.0F,
                        0.0F,
                        -1.5708F,
                        0.0F));
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(
            MeltHeartProjectile entity,
            float walkAnimPos,
            float walkAnimSpeed,
            float ticks,
            float yRotationOffset,
            float xRotation) {
        melt.xRot = (float) (
                entity.getDeltaMovement().y * Math.PI);
    }
}
