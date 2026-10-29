package rotp.addons.purplehaze.client;

import rotp.addons.purplehaze.entity.CapsuleProjectileEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

public final class CapsuleProjectileModel
        extends HierarchicalModel<CapsuleProjectileEntity> {
    private final ModelPart root;
    private final ModelPart capsule;

    public CapsuleProjectileModel(ModelPart root) {
        this.root = root;
        capsule = root.getChild("capsule");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild(
                "capsule",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(
                                -0.5F,
                                -0.5F,
                                -0.5F,
                                1.0F,
                                1.0F,
                                1.0F,
                                new CubeDeformation(
                                        -0.01F)),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        return LayerDefinition.create(mesh, 16, 16);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(
            CapsuleProjectileEntity entity,
            float walkAnimPos,
            float walkAnimSpeed,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        capsule.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        capsule.xRot = ageInTicks * 2.0F;
    }
}
