package com.github.standobyte.jojoimpl.powers.hamon.client;

import java.util.Random;

import com.github.standobyte.jojoimpl.powers.hamon.entity.HamonBubbleEntity;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public class HamonBubbleModel extends HierarchicalModel<HamonBubbleEntity> {
    private final ModelPart root;
    private final ModelPart bubble;

    public HamonBubbleModel(ModelPart root) {
        this.root = root;
        this.bubble = root.getChild("bubble");
    }

    public static HamonBubbleModel create() {
        return new HamonBubbleModel(createBodyLayer().bakeRoot());
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("bubble", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 2.0F, 2.0F, CubeDeformation.NONE)
                .texOffs(6, 0).addBox(-1.0F, -1.5F, -0.5F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.2F))
                .texOffs(0, 4).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.2F))
                .texOffs(6, 2).addBox(-0.5F, -1.5F, -1.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.2F)),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 16, 16);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(HamonBubbleEntity entity, float walkAnimPos, float walkAnimSpeed,
            float ticks, float yRotationOffset, float xRotation) {
        bubble.yRot = yRotationOffset * ((float) Math.PI / 180F);
        bubble.xRot = xRotation * ((float) Math.PI / 180F);
    }

    public static float entityScale(HamonBubbleEntity entity) {
        Random random = new Random(entity.getId());
        return 1.0F + (random.nextFloat() - 0.5F) * 0.4F;
    }
}
