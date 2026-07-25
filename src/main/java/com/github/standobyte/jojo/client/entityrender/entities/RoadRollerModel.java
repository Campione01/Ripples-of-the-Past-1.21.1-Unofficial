package com.github.standobyte.jojo.client.entityrender.entities;

import com.github.standobyte.jojo.customobjects.RoadRollerEntity;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public class RoadRollerModel extends HierarchicalModel<RoadRollerEntity> {
	private static final float DEG_TO_RAD = (float) Math.PI / 180.0F;
	private final ModelPart root;
	private final ModelPart roadRoller;

	public RoadRollerModel(ModelPart root) {
		this.root = root;
		this.roadRoller = root.getChild("road_roller");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		CubeListBuilder body = CubeListBuilder.create();
		body = box(body, 145, 218, -20.0F, 8.0F, -29.0F, 40.0F, 6.0F, 6.0F, 0.0F);
		body = box(body, 92, 0, -20.0F, 9.0F, -23.0F, 40.0F, 4.0F, 13.0F, 0.0F);
		body = box(body, 89, 157, -20.0F, -3.0F, -28.0F, 40.0F, 10.0F, 7.0F, 0.0F);
		body = box(body, 145, 230, -20.0F, 0.0F, -32.0F, 40.0F, 7.0F, 4.0F, 0.0F);
		body = box(body, 92, 17, -20.0F, 4.0F, -33.0F, 40.0F, 2.0F, 1.0F, 0.0F);
		body = box(body, 0, 0, -17.0F, 2.0F, -38.0F, 34.0F, 13.0F, 24.0F, 0.0F);
		body = box(body, 196, 70, -13.0F, 4.0F, -40.0F, 26.0F, 8.0F, 5.0F, 0.0F);
		body = box(body, 0, 143, -17.0F, -4.0F, -30.0F, 34.0F, 6.0F, 14.0F, 0.0F);
		body = box(body, 92, 216, 13.0F, -5.0F, -29.0F, 3.0F, 1.0F, 12.0F, 0.0F);
		body = box(body, 0, 0, 13.5F, -5.5F, -28.5F, 2.0F, 1.0F, 1.0F, 0.0F);
		body = box(body, 86, 49, -16.0F, -5.0F, -29.0F, 3.0F, 1.0F, 12.0F, 0.0F);
		body = box(body, 12, 19, -15.5F, -6.0F, -28.0F, 2.0F, 1.0F, 4.0F, 0.0F);
		body = box(body, 186, 49, -9.0F, 4.0F, -14.0F, 18.0F, 9.0F, 12.0F, 0.0F);
		body = box(body, 198, 36, -9.0F, -2.0F, -15.0F, 18.0F, 6.0F, 7.0F, 0.0F);
		body = box(body, 152, 196, -11.0F, 1.0F, -2.0F, 22.0F, 12.0F, 9.0F, 0.0F);
		body = box(body, 196, 104, -8.0F, 1.9F, -1.9F, 16.0F, 2.0F, 10.0F, 0.0F);
		body = box(body, 3, 226, -15.0F, 0.9F, 6.9F, 30.0F, 14.0F, 5.0F, 0.0F);
		body = box(body, 0, 37, -15.0F, -7.0F, 10.5F, 30.0F, 9.0F, 26.0F, 0.0F);
		body = box(body, 116, 20, -14.0F, -6.0F, 9.5F, 28.0F, 6.0F, 1.0F, 0.0F);
		body = box(body, 0, 186, -17.0F, -10.0F, 17.0F, 34.0F, 4.0F, 10.0F, 0.0F);
		body = box(body, 0, 72, -15.0F, -11.0F, 14.5F, 30.0F, 4.0F, 22.0F, 0.0F);
		body = box(body, 0, 132, -15.0F, -16.0F, 12.5F, 30.0F, 9.0F, 1.0F, 0.0F);
		body = box(body, 109, 216, 10.5F, -16.0F, 12.5F, 1.0F, 9.0F, 17.0F, 0.0F);
		body = box(body, 73, 216, -0.5F, -16.0F, 12.5F, 1.0F, 9.0F, 17.0F, 0.0F);
		body = box(body, 54, 200, -11.5F, -16.0F, 12.5F, 1.0F, 9.0F, 17.0F, 0.0F);
		body = box(body, 0, 200, 5.5F, -12.0F, 16.0F, 8.0F, 1.0F, 19.0F, 0.0F);
		body = box(body, 195, 198, -4.0F, -12.0F, 16.0F, 8.0F, 1.0F, 19.0F, 0.0F);
		body = box(body, 188, 84, -13.5F, -12.0F, 16.0F, 8.0F, 1.0F, 19.0F, 0.0F);
		body = box(body, 0, 14, 5.0F, -7.0F, 36.0F, 7.0F, 2.0F, 2.0F, 0.0F);
		body = box(body, 0, 18, 5.0F, -5.0F, 36.0F, 7.0F, 4.0F, 1.0F, 0.0F);

		PartDefinition roadRoller = root.addOrReplaceChild("road_roller", body, PartPose.offset(0.0F, -20.0F, 0.0F));
		PartDefinition frontWheel = roadRoller.addOrReplaceChild("front_wheel",
				box(CubeListBuilder.create(), 172, 123, -18.0F, -8.0F, -3.0F, 36.0F, 16.0F, 6.0F, 0.535F),
				PartPose.offset(0.0F, 11.0F, -26.0F));
		frontWheel.addOrReplaceChild("front_wheel2",
				box(CubeListBuilder.create(), 168, 174, -18.0F, -8.0F, -3.0F, 36.0F, 16.0F, 6.0F, 0.535F),
				PartPose.rotation(-0.7854F, 0.0F, 0.0F));
		frontWheel.addOrReplaceChild("front_wheel3",
				box(CubeListBuilder.create(), 84, 174, -18.0F, -8.0F, -3.0F, 36.0F, 16.0F, 6.0F, 0.535F),
				PartPose.rotation(-1.5708F, 0.0F, 0.0F));
		frontWheel.addOrReplaceChild("front_wheel4",
				box(CubeListBuilder.create(), 0, 163, -18.0F, -8.0F, -3.0F, 36.0F, 16.0F, 6.0F, 0.535F),
				PartPose.rotation(-2.3562F, 0.0F, 0.0F));
		roadRoller.addOrReplaceChild("front_slope",
				box(box(CubeListBuilder.create(), 183, 157, -17.0F, 0.0F, 0.0F, 34.0F, 5.0F, 10.0F, 0.0F),
						0, 7, 11.0F, -0.5F, 2.0F, 4.0F, 1.0F, 6.0F, 0.0F),
				PartPose.offsetAndRotation(0.0F, 2.0F, -38.0F, 0.6435F, 0.0F, 0.0F));
		roadRoller.addOrReplaceChild("mirror_left",
				box(CubeListBuilder.create(), 62, 132, 0.0F, -6.5F, 0.0F, 3.0F, 7.0F, 1.0F, 0.0F),
				PartPose.offsetAndRotation(17.0F, -2.5F, -18.0F, 0.0F, -0.5236F, 0.0F));
		roadRoller.addOrReplaceChild("mirror_right",
				box(CubeListBuilder.create(), 70, 132, -3.0F, -6.5F, 0.0F, 3.0F, 7.0F, 1.0F, 0.0F),
				PartPose.offsetAndRotation(-17.0F, -2.5F, -18.0F, 0.0F, 0.5236F, 0.0F));
		roadRoller.addOrReplaceChild("gaming_chair",
				box(CubeListBuilder.create(), 35, 200, -6.0F, -13.0F, 0.0F, 12.0F, 13.0F, 2.0F, 0.0F),
				PartPose.offsetAndRotation(0.0F, 2.0F, 6.0F, -0.1745F, 0.0F, 0.0F));
		roadRoller.addOrReplaceChild("steering_wheel",
				box(box(CubeListBuilder.create(), 4, 37, -0.5F, -8.0F, 0.0F, 1.0F, 8.0F, 1.0F, 0.0F),
						0, 0, -3.0F, -9.0F, -2.5F, 6.0F, 1.0F, 6.0F, 0.0F),
				PartPose.offsetAndRotation(0.0F, -2.0F, -9.5F, -0.4363F, 0.0F, 0.0F));
		roadRoller.addOrReplaceChild("lever1",
				box(CubeListBuilder.create(), 0, 37, -0.5F, -9.0F, 0.5F, 1.0F, 9.0F, 1.0F, 0.0F),
				PartPose.offsetAndRotation(7.0F, -2.0F, -11.0F, -0.7854F, 0.0F, 0.0F));
		roadRoller.addOrReplaceChild("lever2",
				box(CubeListBuilder.create(), 20, 7, -0.5F, -9.0F, 0.5F, 1.0F, 9.0F, 1.0F, 0.0F),
				PartPose.offsetAndRotation(5.0F, -2.0F, -11.0F, -1.0472F, 0.0F, 0.0F));
		PartDefinition backWheel = roadRoller.addOrReplaceChild("back_wheel",
				box(CubeListBuilder.create(), 82, 123, -18.0F, -12.0F, -5.0F, 36.0F, 24.0F, 10.0F, -0.05F),
				PartPose.offset(0.0F, 7.0F, 22.0F));
		backWheel.addOrReplaceChild("back_wheel2",
				box(CubeListBuilder.create(), 106, 27, -18.0F, -12.0F, -5.0F, 36.0F, 24.0F, 10.0F, -0.05F),
				PartPose.rotation(-0.7854F, 0.0F, 0.0F));
		backWheel.addOrReplaceChild("back_wheel3",
				box(CubeListBuilder.create(), 104, 62, -18.0F, -12.0F, -5.0F, 36.0F, 24.0F, 10.0F, -0.05F),
				PartPose.rotation(-1.5708F, 0.0F, 0.0F));
		backWheel.addOrReplaceChild("back_wheel4",
				box(CubeListBuilder.create(), 0, 98, -18.0F, -12.0F, -5.0F, 36.0F, 24.0F, 10.0F, -0.05F),
				PartPose.rotation(-2.3562F, 0.0F, 0.0F));
		roadRoller.addOrReplaceChild("back_wheel_thing",
				box(CubeListBuilder.create(), 96, 96, -21.0F, -15.0F, -4.0F, 42.0F, 19.0F, 8.0F, 0.0F),
				PartPose.offsetAndRotation(0.0F, 7.0F, 22.0F, -0.2618F, 0.0F, 0.0F));
		roadRoller.addOrReplaceChild("back",
				box(box(CubeListBuilder.create(), 84, 196, -15.0F, 0.0F, -4.0F, 30.0F, 16.0F, 4.0F, 0.0F),
						8, 37, -5.0F, 0.0F, 0.0F, 3.0F, 1.0F, 1.0F, 0.0F),
				PartPose.offsetAndRotation(0.0F, -2.0F, 36.5F, 0.5236F, 0.0F, 0.0F));

		return LayerDefinition.create(mesh, 256, 256);
	}

	private static CubeListBuilder box(CubeListBuilder builder, int texU, int texV,
			float x, float y, float z, float dx, float dy, float dz, float inflate) {
		return builder.texOffs(texU, texV).addBox(x, y, z, dx, dy, dz, new CubeDeformation(inflate));
	}

	@Override
	public ModelPart root() {
		return root;
	}

	@Override
	public void setupAnim(RoadRollerEntity entity, float limbSwing, float limbSwingAmount,
			float ageInTicks, float netHeadYaw, float headPitch) {
		roadRoller.yRot = netHeadYaw * DEG_TO_RAD;
		roadRoller.xRot = headPitch * DEG_TO_RAD;
	}
}
