package com.github.standobyte.jojoimpl.powers.hamon.client;

import com.github.standobyte.jojoimpl.powers.hamon.entity.LeavesGliderEntity;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public class LeavesGliderModel extends HierarchicalModel<LeavesGliderEntity> {
    private final ModelPart root;
    private final ModelPart glider;

    public LeavesGliderModel(ModelPart root) {
        this.root = root;
        this.glider = root.getChild("glider");
    }

    public static LeavesGliderModel create() {
        return new LeavesGliderModel(createBodyLayer().bakeRoot());
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition glider = root.addOrReplaceChild("glider", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-20.0F, -0.5F, -4.11F, 40.0F, 1.0F, 24.0F, new CubeDeformation(-0.375F)),
                PartPose.ZERO);

        glider.addOrReplaceChild("front_left", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-0.375F, -0.5F, -0.375F, 26.0F, 1.0F, 16.0F, new CubeDeformation(-0.375F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, -19.625F, 0.0F, -0.6806F, 0.0F));
        glider.addOrReplaceChild("front_right", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-25.625F, -0.5F, -0.375F, 26.0F, 1.0F, 16.0F, new CubeDeformation(-0.375F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, -19.625F, 0.0F, 0.6806F, 0.0F));

        return LayerDefinition.create(mesh, 16, 16);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(LeavesGliderEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float yRotationOffset, float xRotation) {
        glider.yRot = yRotationOffset * ((float) Math.PI / 180F);
    }
}
