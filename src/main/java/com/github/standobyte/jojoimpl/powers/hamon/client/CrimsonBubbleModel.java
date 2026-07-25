package com.github.standobyte.jojoimpl.powers.hamon.client;

import com.github.standobyte.jojoimpl.powers.hamon.entity.CrimsonBubbleEntity;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;

public class CrimsonBubbleModel extends HierarchicalModel<CrimsonBubbleEntity> {
    private final ModelPart root;
    private final ModelPart bubble;

    public CrimsonBubbleModel(ModelPart root) {
        this.root = root;
        this.bubble = root.getChild("bubble");
    }

    public static CrimsonBubbleModel create() {
        return new CrimsonBubbleModel(createBodyLayer().bakeRoot());
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("bubble", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F, CubeDeformation.NONE)
                .texOffs(32, 0).addBox(-3.0F, -3.0F, -5.0F, 6.0F, 6.0F, 10.0F, CubeDeformation.NONE)
                .texOffs(0, 16).addBox(-2.0F, -2.0F, -5.5F, 4.0F, 4.0F, 11.0F, CubeDeformation.NONE)
                .texOffs(30, 16).addBox(-5.0F, -3.0F, -3.0F, 10.0F, 6.0F, 6.0F, CubeDeformation.NONE)
                .texOffs(30, 28).addBox(-5.5F, -2.0F, -2.0F, 11.0F, 4.0F, 4.0F, CubeDeformation.NONE)
                .texOffs(0, 31).addBox(-3.0F, -5.0F, -3.0F, 6.0F, 10.0F, 6.0F, CubeDeformation.NONE)
                .texOffs(24, 36).addBox(-2.0F, -5.5F, -2.0F, 4.0F, 11.0F, 4.0F, CubeDeformation.NONE),
                PartPose.offset(0.0F, -5.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(CrimsonBubbleEntity entity, float walkAnimPos, float walkAnimSpeed,
            float ticks, float yRotationOffset, float xRotation) {
        bubble.yRot = yRotationOffset * ((float) Math.PI / 180F);
    }
}
