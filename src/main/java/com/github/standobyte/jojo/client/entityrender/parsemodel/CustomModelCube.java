package com.github.standobyte.jojo.client.entityrender.parsemodel;

import java.util.Set;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;

public class CustomModelCube extends ModelPart.Cube {

	public CustomModelCube(int texCoordU, int texCoordV, float originX, float originY, float originZ, float dimensionX,
			float dimensionY, float dimensionZ, float gtowX, float growY, float growZ, boolean mirror, float texScaleU,
			float texScaleV, Set<Direction> visibleFaces) {
		super(texCoordU, texCoordV, originX, originY, originZ, dimensionX, dimensionY, dimensionZ, gtowX, growY, growZ, mirror,
				texScaleU, texScaleV, visibleFaces);
	}

	/* 
	 * Have to create this subclass and completely copypaste the compile method,
	 * so that Sodium doesn't assume that this is a vanilla cube (its mixin breaks muh models).
	 * This does mean that Sodium doesn't optimize our Stand models, 
	 * I would try and optimize smth myself if I didn't suck at all this low-level graphics stuff.
	 */
	@Override
	public void compile(PoseStack.Pose pose, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
		Matrix4f matrix4f = pose.pose();
		Vector3f vector3f = new Vector3f();

		for (ModelPart.Polygon modelpart$polygon : this.polygons) {
			Vector3f vector3f1 = pose.transformNormal(modelpart$polygon.normal, vector3f);
			float f = vector3f1.x();
			float f1 = vector3f1.y();
			float f2 = vector3f1.z();

			for (ModelPart.Vertex modelpart$vertex : modelpart$polygon.vertices) {
				float f3 = modelpart$vertex.pos.x() / 16.0F;
				float f4 = modelpart$vertex.pos.y() / 16.0F;
				float f5 = modelpart$vertex.pos.z() / 16.0F;
				Vector3f vector3f2 = matrix4f.transformPosition(f3, f4, f5, vector3f);
				buffer.addVertex(
					vector3f2.x(), vector3f2.y(), vector3f2.z(), color, modelpart$vertex.u, modelpart$vertex.v, packedOverlay, packedLight, f, f1, f2
				);
			}
		}
	}

}
