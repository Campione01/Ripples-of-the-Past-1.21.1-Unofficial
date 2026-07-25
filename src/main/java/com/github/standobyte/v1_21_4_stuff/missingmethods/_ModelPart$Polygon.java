package com.github.standobyte.v1_21_4_stuff.missingmethods;

import org.joml.Vector3f;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;

public class _ModelPart$Polygon {

	public static ModelPart.Polygon create(ModelPart.Vertex[] vertices, Vector3f normal) {
		ModelPart.Vertex[] dummy = new ModelPart.Vertex[4];
		System.arraycopy(vertices, 0, dummy, 0, 4); // good thing vertices are immutable
		ModelPart.Polygon polygon = new ModelPart.Polygon(dummy, 0, 0, 0, 0, 0, 0, false, Direction.SOUTH);
		polygon.vertices = vertices;
		polygon.normal = normal;
		return polygon;
	}
}
