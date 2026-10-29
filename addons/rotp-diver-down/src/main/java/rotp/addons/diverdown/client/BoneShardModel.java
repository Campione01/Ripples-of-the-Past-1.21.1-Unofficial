package rotp.addons.diverdown.client;

import rotp.addons.diverdown.DiverDownAddon;
import rotp.addons.diverdown.entity.BoneShardEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public final class BoneShardModel
        extends HierarchicalModel<BoneShardEntity> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(
                    DiverDownAddon.id("bone_shard"),
                    "main");

    private final ModelPart root;
    private final ModelPart shard;

    public BoneShardModel(ModelPart root) {
        this.root = root;
        this.shard = root.getChild("shard");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild(
                "shard",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(
                                -1.0F,
                                -6.0F,
                                -1.0F,
                                2.0F,
                                12.0F,
                                2.0F,
                                CubeDeformation.NONE)
                        .texOffs(8, 0)
                        .addBox(
                                -0.5F,
                                -8.0F,
                                -0.5F,
                                1.0F,
                                4.0F,
                                1.0F,
                                CubeDeformation.NONE),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(
            BoneShardEntity entity,
            float walkAnimationPosition,
            float walkAnimationSpeed,
            float ageInTicks,
            float yRotationOffset,
            float xRotation) {
        shard.yRot =
                yRotationOffset * ((float) Math.PI / 180.0F);
        shard.xRot =
                xRotation * ((float) Math.PI / 180.0F);
        shard.zRot = ageInTicks * 0.35F;
    }
}
