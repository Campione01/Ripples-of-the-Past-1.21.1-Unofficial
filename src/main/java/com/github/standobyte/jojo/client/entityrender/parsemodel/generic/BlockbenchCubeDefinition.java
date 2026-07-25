package com.github.standobyte.jojo.client.entityrender.parsemodel.generic;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

import org.joml.Vector3f;

import com.github.standobyte.jojo.client.entityrender.parsemodel.CustomModelCube;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.ModelPart.Cube;
import net.minecraft.client.model.geom.builders.CubeDefinition;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.core.Direction;

public final class BlockbenchCubeDefinition extends CubeDefinition {
	protected final Vector3f origin;
	protected final Vector3f dimensions;
	protected final Vector3f grow;
	protected final boolean mirror;
	protected final UVPair texCoord;
	protected final UVPair texScale;
	protected final Map<Direction, BoxFace> faces;

	public BlockbenchCubeDefinition(
			float originX, float originY, float originZ, 
			float dimensionX, float dimensionY, float dimensionZ, 
			float grow, Map<Direction, BoxFace> faces) {
		this(0, 0,
				originX, originY, originZ, 
				dimensionX, dimensionY, dimensionZ, 
				grow, grow, grow, 
				false, 1, 1, faces);
	}
	
	public BlockbenchCubeDefinition(
			float texCoordU, float texCoordV,
			float originX, float originY, float originZ, 
			float dimensionX, float dimensionY, float dimensionZ, 
			float growX, float growY, float growZ, 
			boolean mirror, float texScaleU, float texScaleV, Map<Direction, BoxFace> faces) {
		super("Blockbench per-face UV cube", 
				texCoordU, texCoordV, 
				originX, originY, originZ, 
				dimensionX, dimensionY, dimensionZ, 
				new CubeDeformation(growX, growY, growZ), 
				mirror, texScaleU, texScaleV, faces.keySet());
		this.texCoord = new UVPair(texCoordU, texCoordV);
		this.origin = new Vector3f(originX, originY, originZ);
		this.dimensions = new Vector3f(dimensionX, dimensionY, dimensionZ);
		this.grow = new Vector3f(growX, growY, growZ);
		this.mirror = mirror;
		this.texScale = new UVPair(texScaleU, texScaleV);
		this.faces = faces;
	}

	public record BoxFace(
			float[] uv,
			Integer texture) {}

	@Override
	public Cube bake(int pTexWidth, int pTexHeight) {
		Cube cube = new CustomModelCube(
				(int)this.texCoord.u(),
				(int)this.texCoord.v(),
				this.origin.x(),
				this.origin.y(),
				this.origin.z(),
				this.dimensions.x(),
				this.dimensions.y(),
				this.dimensions.z(),
				this.grow.x(),
				this.grow.y(),
				this.grow.z(),
				this.mirror,
				(float)pTexWidth * this.texScale.u(),
				(float)pTexHeight * this.texScale.v(),
				this.faces.keySet()
				);
		
		float texWidth = pTexWidth * texScale.u();
		float texHeight = pTexHeight * texScale.v();
		
		ModelPart.Polygon[] polygons = new ModelPart.Polygon[faces.size()];
		float x0 = origin.x() - grow.x();
		float y0 = origin.y() - grow.y();
		float z0 = origin.z() - grow.z();
		float x1 = origin.x() + grow.x() + dimensions.x();
		float y1 = origin.y() + grow.y() + dimensions.y();
		float z1 = origin.z() + grow.z() + dimensions.z();
		
		ModelPart.Vertex x0y0z0 = new ModelPart.Vertex(x0, y0, z0, 0.0F, 0.0F);
		ModelPart.Vertex x1y0z0 = new ModelPart.Vertex(x1, y0, z0, 0.0F, 8.0F);
		ModelPart.Vertex x1y1z0 = new ModelPart.Vertex(x1, y1, z0, 8.0F, 8.0F);
		ModelPart.Vertex x0y1z0 = new ModelPart.Vertex(x0, y1, z0, 8.0F, 0.0F);
		ModelPart.Vertex x0y0z1 = new ModelPart.Vertex(x0, y0, z1, 0.0F, 0.0F);
		ModelPart.Vertex x1y0z1 = new ModelPart.Vertex(x1, y0, z1, 0.0F, 8.0F);
		ModelPart.Vertex x1y1z1 = new ModelPart.Vertex(x1, y1, z1, 8.0F, 8.0F);
		ModelPart.Vertex x0y1z1 = new ModelPart.Vertex(x0, y1, z1, 8.0F, 0.0F);
		
		Map<Direction, ModelPart.Vertex[]> faceVertices = new EnumMap<>(Direction.class);
		if (faces.containsKey(Direction.UP)) faceVertices.put(Direction.DOWN, new ModelPart.Vertex[]{
				x1y0z1, 
				x0y0z1, 
				x0y0z0, 
				x1y0z0});
		if (faces.containsKey(Direction.DOWN)) faceVertices.put(Direction.UP, new ModelPart.Vertex[]{
				x1y1z0, 
				x0y1z0, 
				x0y1z1, 
				x1y1z1});
		if (faces.containsKey(Direction.EAST)) faceVertices.put(Direction.WEST, new ModelPart.Vertex[]{
				x0y0z0, 
				x0y0z1, 
				x0y1z1, 
				x0y1z0});
		if (faces.containsKey(Direction.NORTH)) faceVertices.put(Direction.NORTH, new ModelPart.Vertex[]{
				x1y0z0, 
				x0y0z0, 
				x0y1z0, 
				x1y1z0});
		if (faces.containsKey(Direction.WEST)) faceVertices.put(Direction.EAST, new ModelPart.Vertex[]{
				x1y0z1, 
				x1y0z0, 
				x1y1z0, 
				x1y1z1});
		if (faces.containsKey(Direction.SOUTH)) faceVertices.put(Direction.SOUTH, new ModelPart.Vertex[]{
				x0y0z1, 
				x1y0z1, 
				x1y1z1, 
				x0y1z1});
				
		
		int polygonsCount = 0;
		for (Direction direction : Direction.values()) {
			Direction uvPart = direction.getAxis() == Direction.Axis.Z ? direction : direction.getOpposite();
			if (faces.containsKey(uvPart)) {
				BoxFace uv = faces.get(uvPart);
				// if (uv.texture != null) {
					float u0;
					float v0;
					float u1;
					float v1;
					if (direction.getAxis() == Direction.Axis.Y) {
						u0 = uv.uv[2];
						v0 = uv.uv[3];
						u1 = uv.uv[0];
						v1 = uv.uv[1];
					}
					else {
						u0 = uv.uv[0];
						v0 = uv.uv[1];
						u1 = uv.uv[2];
						v1 = uv.uv[3];
					}
					polygons[polygonsCount++] = new ModelPart.Polygon(faceVertices.get(direction), 
							u0, v0, u1, v1, 
							texWidth, texHeight, false, direction);
				// }
			}
		}
		if (polygonsCount < polygons.length) {
			polygons = Arrays.copyOf(polygons, polygonsCount);
		}
		
		cube.polygons = polygons;
		return cube;
	}

}
