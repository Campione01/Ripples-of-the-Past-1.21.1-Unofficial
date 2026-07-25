package com.github.standobyte.jojoimpl.powers.hamon.client;

import com.github.standobyte.jojoimpl.powers.hamon.entity.HamonCutterEntity;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;

public class HamonCutterModel extends HierarchicalModel<HamonCutterEntity> {
    private final ModelPart root;
    private final ModelPart cutter;

    public HamonCutterModel(ModelPart root) {
        this.root = root;
        this.cutter = root.getChild("cutter");
    }

    public static HamonCutterModel create() {
        return new HamonCutterModel(createBodyLayer().bakeRoot());
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("cutter", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-2.0F, -1.0F, -3.0F, 4.0F, 1.0F, 6.0F, new CubeDeformation(-0.4F))
                .texOffs(0, 7).addBox(-3.0F, -1.0F, -2.0F, 6.0F, 1.0F, 4.0F, new CubeDeformation(-0.395F)),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(HamonCutterEntity entity, float walkAnimPos, float walkAnimSpeed,
            float ticks, float yRotationOffset, float xRotation) {
        yRotationOffset = (yRotationOffset + ticks * 60F) % 360F;
        cutter.yRot = yRotationOffset * ((float) Math.PI / 180F);
    }
}
