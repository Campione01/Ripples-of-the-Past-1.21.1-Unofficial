package com.github.standobyte.jojoimpl.powers.hamon.client;

import com.github.standobyte.jojoimpl.powers.hamon.entity.HamonBubbleBarrierEntity;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;

public class HamonBubbleBarrierModel extends HierarchicalModel<HamonBubbleBarrierEntity> {
    private final ModelPart root;
    private final ModelPart bubble;

    public HamonBubbleBarrierModel(ModelPart root) {
        this.root = root;
        this.bubble = root.getChild("bubble");
    }

    public static HamonBubbleBarrierModel create() {
        return new HamonBubbleBarrierModel(createBodyLayer().bakeRoot());
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("bubble", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-12.0F, -12.0F, -12.0F, 24.0F, 24.0F, 24.0F, CubeDeformation.NONE)
                .texOffs(96, 0).addBox(-9.0F, -9.0F, -15.0F, 18.0F, 18.0F, 30.0F, CubeDeformation.NONE)
                .texOffs(0, 48).addBox(-6.0F, -6.0F, -16.5F, 12.0F, 12.0F, 33.0F, CubeDeformation.NONE)
                .texOffs(90, 48).addBox(-15.0F, -9.0F, -9.0F, 30.0F, 18.0F, 18.0F, CubeDeformation.NONE)
                .texOffs(90, 84).addBox(-16.5F, -6.0F, -6.0F, 33.0F, 12.0F, 12.0F, CubeDeformation.NONE)
                .texOffs(0, 93).addBox(-9.0F, -15.0F, -9.0F, 18.0F, 30.0F, 18.0F, CubeDeformation.NONE)
                .texOffs(72, 108).addBox(-6.0F, -16.5F, -6.0F, 12.0F, 33.0F, 12.0F, CubeDeformation.NONE),
                PartPose.offset(0.0F, -15.0F, 0.0F));
        return LayerDefinition.create(mesh, 256, 256);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(HamonBubbleBarrierEntity entity, float walkAnimPos, float walkAnimSpeed,
            float ticks, float yRotationOffset, float xRotation) {
        bubble.yRot = yRotationOffset * ((float) Math.PI / 180F);
    }
}
