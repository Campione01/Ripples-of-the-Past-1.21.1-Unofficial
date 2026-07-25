package com.github.standobyte.jojo.client.entityrender.entities;

import com.github.standobyte.jojo.mechanics.clothes.mannequin.MannequinEntity;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public class MannequinModel extends HumanoidModel<MannequinEntity> {
	
    public MannequinModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createBodyLayer(CubeDeformation cubeDeformation) {
        MeshDefinition meshdefinition = HumanoidModel.createMesh(cubeDeformation, 0.0F);
        PartDefinition partdefinition = meshdefinition.getRoot();
        partdefinition.addOrReplaceChild(
            "head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, cubeDeformation), PartPose.offset(0.0F, 1.0F, 0.0F)
        );
        partdefinition.addOrReplaceChild(
            "hat",
            CubeListBuilder.create().texOffs(32, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, cubeDeformation.extend(0.5F)),
            PartPose.offset(0.0F, 1.0F, 0.0F)
        );
        partdefinition.addOrReplaceChild(
            "right_leg",
            CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, cubeDeformation.extend(-0.1F)),
            PartPose.offset(-1.9F, 11.0F, 0.0F)
        );
        partdefinition.addOrReplaceChild(
            "left_leg",
            CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, cubeDeformation.extend(-0.1F)),
            PartPose.offset(1.9F, 11.0F, 0.0F)
        );
        return LayerDefinition.create(meshdefinition, 64, 32);
    }

    /**
     * Sets this entity's model rotation angles
     */
	@Override
    public void setupAnim(MannequinEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.head.xRot = (float) (Math.PI / 180.0) * entity.getHeadPose().getX();
        this.head.yRot = (float) (Math.PI / 180.0) * entity.getHeadPose().getY();
        this.head.zRot = (float) (Math.PI / 180.0) * entity.getHeadPose().getZ();
        this.body.xRot = (float) (Math.PI / 180.0) * entity.getBodyPose().getX();
        this.body.yRot = (float) (Math.PI / 180.0) * entity.getBodyPose().getY();
        this.body.zRot = (float) (Math.PI / 180.0) * entity.getBodyPose().getZ();
        this.leftArm.xRot = (float) (Math.PI / 180.0) * entity.getLeftArmPose().getX();
        this.leftArm.yRot = (float) (Math.PI / 180.0) * entity.getLeftArmPose().getY();
        this.leftArm.zRot = (float) (Math.PI / 180.0) * entity.getLeftArmPose().getZ();
        this.rightArm.xRot = (float) (Math.PI / 180.0) * entity.getRightArmPose().getX();
        this.rightArm.yRot = (float) (Math.PI / 180.0) * entity.getRightArmPose().getY();
        this.rightArm.zRot = (float) (Math.PI / 180.0) * entity.getRightArmPose().getZ();
        this.leftLeg.xRot = (float) (Math.PI / 180.0) * entity.getLeftLegPose().getX();
        this.leftLeg.yRot = (float) (Math.PI / 180.0) * entity.getLeftLegPose().getY();
        this.leftLeg.zRot = (float) (Math.PI / 180.0) * entity.getLeftLegPose().getZ();
        this.rightLeg.xRot = (float) (Math.PI / 180.0) * entity.getRightLegPose().getX();
        this.rightLeg.yRot = (float) (Math.PI / 180.0) * entity.getRightLegPose().getY();
        this.rightLeg.zRot = (float) (Math.PI / 180.0) * entity.getRightLegPose().getZ();
        this.hat.copyFrom(this.head);
    }
}
