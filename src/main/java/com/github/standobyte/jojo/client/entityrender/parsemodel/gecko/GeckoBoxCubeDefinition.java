package com.github.standobyte.jojo.client.entityrender.parsemodel.gecko;

import java.util.EnumSet;
import java.util.Set;

import org.joml.Vector3f;

import com.github.standobyte.jojo.client.entityrender.parsemodel.CustomModelCube;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.ModelPart.Cube;
import net.minecraft.client.model.geom.builders.CubeDefinition;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.core.Direction;

// UV needs to be remapped, as if the sizes are integer
public final class GeckoBoxCubeDefinition extends CubeDefinition{
	public static final Set<Direction> ALL_FACES_VISIBLE = EnumSet.allOf(Direction.class);
	
	protected final Vector3f origin;
	protected final Vector3f dimensions;
	protected final Vector3f grow;
	protected final boolean mirror;
	protected final UVPair texCoord;
	protected final UVPair texScale;
	protected final Set<Direction> visibleFaces;

	public GeckoBoxCubeDefinition(
			float texCoordU, float texCoordV,
			float originX, float originY, float originZ, 
			float dimensionX, float dimensionY, float dimensionZ, 
			float grow, boolean mirror) {
		this(texCoordU, texCoordV,  
				originX, originY, originZ, 
				dimensionX, dimensionY, dimensionZ, 
				grow, grow, grow, 
				mirror,
				1, 1, ALL_FACES_VISIBLE);
	}

	public GeckoBoxCubeDefinition(
			float texCoordU, float texCoordV,
			float originX, float originY, float originZ, 
			float dimensionX, float dimensionY, float dimensionZ, 
			float growX, float growY, float growZ, 
			boolean mirror, float texScaleU, float texScaleV, Set<Direction> visibleFaces) {
		super("Gecko box UV cube", 
				texCoordU, texCoordV,  
				originX, originY, originZ, 
				dimensionX, dimensionY, dimensionZ, 
				new CubeDeformation(growX, growY, growZ), mirror,
				texScaleU, texScaleV, visibleFaces);
		this.texCoord = new UVPair(texCoordU, texCoordV);
		this.origin = new Vector3f(originX, originY, originZ);
		this.dimensions = new Vector3f(dimensionX, dimensionY, dimensionZ);
		this.grow = new Vector3f(growX, growY, growZ);
		this.mirror = mirror;
		this.texScale = new UVPair(texScaleU, texScaleV);
		this.visibleFaces = visibleFaces;
	}

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
				this.visibleFaces
				);

		float texWidth = pTexWidth * texScale.u();
		float texHeight = pTexHeight * texScale.v();
		float x0 = origin.x() - grow.x();
		float y0 = origin.y() - grow.y();
		float z0 = origin.z() - grow.z();
		float x1 = origin.x() + grow.x() + dimensions.x();
		float y1 = origin.y() + grow.y() + dimensions.y();
		float z1 = origin.z() + grow.z() + dimensions.z();
		
		if (mirror) {
			float swap = x1;
			x1 = x0;
			x0 = swap;
		}
		
		ModelPart.Vertex x0y0z0 = new ModelPart.Vertex(x0, y0, z0, 0.0F, 0.0F);
		ModelPart.Vertex x1y0z0 = new ModelPart.Vertex(x1, y0, z0, 0.0F, 8.0F);
		ModelPart.Vertex x1y1z0 = new ModelPart.Vertex(x1, y1, z0, 8.0F, 8.0F);
		ModelPart.Vertex x0y1z0 = new ModelPart.Vertex(x0, y1, z0, 8.0F, 0.0F);
		ModelPart.Vertex x0y0z1 = new ModelPart.Vertex(x0, y0, z1, 0.0F, 0.0F);
		ModelPart.Vertex x1y0z1 = new ModelPart.Vertex(x1, y0, z1, 0.0F, 8.0F);
		ModelPart.Vertex x1y1z1 = new ModelPart.Vertex(x1, y1, z1, 8.0F, 8.0F);
		ModelPart.Vertex x0y1z1 = new ModelPart.Vertex(x0, y1, z1, 8.0F, 0.0F);

		ModelPart.Polygon[] polygons = new ModelPart.Polygon[visibleFaces.size()];
		int texCoordU = (int) texCoord.u();
		int texCoordV = (int) texCoord.v();
		int sizeX = (int) dimensions.x();
		int sizeY = (int) dimensions.y();
		int sizeZ = (int) dimensions.z();
		float f4 = texCoordU;
		float f5 = texCoordU + sizeZ;
		float f6 = texCoordU + sizeZ + sizeX;
		float f7 = texCoordU + sizeZ + sizeX + sizeX;
		float f8 = texCoordU + sizeZ + sizeX + sizeZ;
		float f9 = texCoordU + sizeZ + sizeX + sizeZ + sizeX;
		float f10 = texCoordV;
		float f11 = texCoordV + sizeZ;
		float f12 = texCoordV + sizeZ + sizeY;
		
		int i = 0;
		if (visibleFaces.contains(Direction.DOWN)) polygons[i++] = new ModelPart.Polygon(new ModelPart.Vertex[]{
				x1y0z1, 
				x0y0z1, 
				x0y0z0, 
				x1y0z0}, f5, f10, f6, f11, texWidth, texHeight, mirror, Direction.DOWN);
		if (visibleFaces.contains(Direction.UP)) polygons[i++] = new ModelPart.Polygon(new ModelPart.Vertex[]{
				x1y1z0, 
				x0y1z0, 
				x0y1z1, 
				x1y1z1}, f6, f11, f7, f10, texWidth, texHeight, mirror, Direction.UP);
		if (visibleFaces.contains(Direction.WEST)) polygons[i++] = new ModelPart.Polygon(new ModelPart.Vertex[]{
				x0y0z0, 
				x0y0z1, 
				x0y1z1, 
				x0y1z0}, f4, f11, f5, f12, texWidth, texHeight, mirror, Direction.WEST);
		if (visibleFaces.contains(Direction.NORTH)) polygons[i++] = new ModelPart.Polygon(new ModelPart.Vertex[]{
				x1y0z0, 
				x0y0z0, 
				x0y1z0, 
				x1y1z0}, f5, f11, f6, f12, texWidth, texHeight, mirror, Direction.NORTH);
		if (visibleFaces.contains(Direction.EAST)) polygons[i++] = new ModelPart.Polygon(new ModelPart.Vertex[]{
				x1y0z1, 
				x1y0z0, 
				x1y1z0, 
				x1y1z1}, f6, f11, f8, f12, texWidth, texHeight, mirror, Direction.EAST);
		if (visibleFaces.contains(Direction.SOUTH)) polygons[i++] = new ModelPart.Polygon(new ModelPart.Vertex[]{
				x0y0z1, 
				x1y0z1, 
				x1y1z1, 
				x0y1z1}, f8, f11, f9, f12, texWidth, texHeight, mirror, Direction.SOUTH);
		
		cube.polygons = polygons;
		return cube;
	}

}
