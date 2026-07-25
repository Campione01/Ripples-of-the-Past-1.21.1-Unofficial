package com.github.standobyte.jojo.client.entityrender.parsemodel.generic;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;
import org.joml.Vector3f;

import com.github.standobyte.jojo.client.entityrender.parsemodel.CustomModelCube;
import com.github.standobyte.v1_21_4_stuff.missingmethods._ModelPart$Polygon;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.ModelPart.Cube;
import net.minecraft.client.model.geom.builders.CubeDefinition;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;

public final class BlockbenchMeshDefinition extends CubeDefinition {
	public static final Set<Direction> NO_DIRECTIONAL_FACES = EnumSet.noneOf(Direction.class);
	
	protected final Vector3f origin;
	protected final Vector3f dimensions;
	protected final UVPair texScale;
	protected final List<? extends MeshFace> faces;
	
	public BlockbenchMeshDefinition(
			float originX, float originY, float originZ, 
			float dimensionX, float dimensionY, float dimensionZ, 
			float texScaleU, float texScaleV, 
			List<FaceDefinition> faces) {
		super("Blockbench mesh", 
				0, 0, 
				originX, originY, originZ, 
				dimensionX, dimensionY, dimensionZ, 
				new CubeDeformation(0), 
				false, texScaleU, texScaleV, NO_DIRECTIONAL_FACES);
		this.origin = new Vector3f(originX, originY, originZ);
		this.dimensions = new Vector3f(dimensionX, dimensionY, dimensionZ);
		this.texScale = new UVPair(texScaleU, texScaleV);
		this.faces = faces;
	}
	
	protected BlockbenchMeshDefinition(
			float originX, float originY, float originZ, 
			float dimensionX, float dimensionY, float dimensionZ, 
			List<? extends MeshFace> faces) {
		super("Blockbench mesh", 
				0, 0, 
				originX, originY, originZ, 
				dimensionX, dimensionY, dimensionZ, 
				new CubeDeformation(0), 
				false, 1, 1, NO_DIRECTIONAL_FACES);
		this.origin = new Vector3f(originX, originY, originZ);
		this.dimensions = new Vector3f(dimensionX, dimensionY, dimensionZ);
		this.texScale = new UVPair(1, 1);
		this.faces = faces;
	}
	
	
	public static class MeshBuilder {
		protected final boolean livingEntityRenderHacks;
		protected final MeshFaceBuilder faceBuilder;
		protected float minX;
		protected float minY;
		protected float minZ;
		protected float maxX;
		protected float maxY;
		protected float maxZ;
		protected final List<MeshFace> faces = new ArrayList<>();
		
		public MeshBuilder(boolean livingEntityRenderHacks) {
			this.livingEntityRenderHacks = livingEntityRenderHacks;
			faceBuilder = new MeshFaceBuilder(this);
		}
		
		public MeshFaceBuilder startFace(Direction lightingDir) {
			if (livingEntityRenderHacks) {
				lightingDir = lightingDir.getAxis() == Axis.Z ? lightingDir : lightingDir.getOpposite();
			}
			return faceBuilder.withState(lightingDir, null, false, false);
		}
		
		public MeshFaceBuilder startFace(Vector3f faceNormal) {
			return faceBuilder.withState(null, faceNormal, false, false);
		}
		
		public MeshFaceBuilder startFaceCalcNormal() {
			return faceBuilder.withState(null, null, true, false);
		}
		
		/**
		 * In case the automatically calculated lighting vector is incorrect
		 */
		public MeshFaceBuilder startFaceCalcNormal(boolean invertVec) {
			return faceBuilder.withState(null, null, true, invertVec);
		}
		
		
		@Nullable
		public BlockbenchMeshDefinition buildCube() {
			if (faces.isEmpty()) return null;
			BlockbenchMeshDefinition cube = new BlockbenchMeshDefinition(
					minX, minY, minZ, maxX - minX, maxY - minY, maxZ - minZ, faces);
			return cube;
		}
		
		public class MeshFaceBuilder {
			protected final BlockbenchMeshDefinition.MeshBuilder boxBuilder;
			
			protected Direction direction;
			protected Vector3f faceNormal;
			protected boolean calcNormalFromVertices;
			protected boolean invertCalcNormal;
			
			protected List<VertexDefinition> verticesBuilder = new ArrayList<>();
			
			protected MeshFaceBuilder(BlockbenchMeshDefinition.MeshBuilder boxBuilder) {
				this.boxBuilder = boxBuilder;
			}
			
			public MeshFaceBuilder withVertex(double x, double y, double z, double texU, double texV) {
				if (boxBuilder.livingEntityRenderHacks) {
					x = -x;
					y = -y;
				}
				float xF = (float) x;
				float yF = (float) y;
				float zF = (float) z;
				boxBuilder.minX = Math.min(boxBuilder.minX, xF);
				boxBuilder.minY = Math.min(boxBuilder.minY, yF);
				boxBuilder.minZ = Math.min(boxBuilder.minZ, zF);
				boxBuilder.maxX = Math.max(boxBuilder.maxX, xF);
				boxBuilder.maxY = Math.max(boxBuilder.maxY, yF);
				boxBuilder.maxZ = Math.max(boxBuilder.maxZ, zF);
				
				VertexDefinition vertex = new VertexDefinition(
						new Vector3f(xF, yF, zF), (float) texU, (float) texV);
				verticesBuilder.add(vertex);
				return this;
			}
			
			protected MeshFaceBuilder withState(Direction direction, Vector3f faceNormal, 
					boolean calcNormalFromVertices, boolean invertCalcNormal) {
				this.direction = direction;
				this.faceNormal = faceNormal;
				this.calcNormalFromVertices = calcNormalFromVertices;
				this.invertCalcNormal = invertCalcNormal;
				return this;
			}
			
			public BlockbenchMeshDefinition.MeshBuilder createFace() {
				if (verticesBuilder.size() > 2) {
					VertexDefinition[] vertices = this.verticesBuilder.toArray(new VertexDefinition[4]);
					if (this.verticesBuilder.size() == 3) {
						vertices[3] = vertices[2];
					}
					
					MeshFace face;
					if (calcNormalFromVertices) {
						face = new FaceLazyNormal(vertices, invertCalcNormal);
					}
					else {
						if (faceNormal == null) {
							faceNormal = (direction != null ? direction : Direction.UP).step();
						}
						face = new FaceDefinition(vertices, faceNormal);
					}
					
					boxBuilder.faces.add(face);
				}
				
				this.verticesBuilder.clear();
				return boxBuilder;
			}
		}
	}
	
	@ApiStatus.Internal
	protected static interface MeshFace {
		ModelPart.Polygon createFace(Vector3f cubeCenter, float texWidth, float texHeight);
	}
	
	protected static record FaceLazyNormal(VertexDefinition[] vertices, boolean invertNormal) implements MeshFace {
		
		@Override
		public ModelPart.Polygon createFace(Vector3f cubeCenter, float texWidth, float texHeight) {
			var vertices = new ModelPart.Vertex[this.vertices.length];
			for (int i = 0; i < vertices.length; i++) {
				vertices[i] = this.vertices[i].createVertex(texWidth, texHeight);
			}
			Vector3f a = vertices[0].pos;
			Vector3f b = vertices[1].pos;
			Vector3f c = vertices[2].pos;
			Vector3f vec1 = new Vector3f(b).sub(a);
			Vector3f vec2 = new Vector3f(c).sub(a);
			Vector3f faceNormal = new Vector3f(vec1); faceNormal.cross(vec2);
			faceNormal.normalize();
			
			Vector3f vecFromCenter = new Vector3f(
					(a.x() + b.x() + c.x()) / 3,
					(a.y() + b.y() + c.y()) / 3,
					(a.z() + b.z() + c.z()) / 3);
			vecFromCenter = vecFromCenter.sub(cubeCenter);
			
			if (faceNormal.dot(vecFromCenter) < 0) {
				reverseWinding(vertices, this.vertices[2] == this.vertices[3]);
				faceNormal.mul(-1);
			}
			if (invertNormal) {
				faceNormal.mul(-1);
			}
//			return new ModelPart.Polygon(vertices, new Vector3f(faceNormal));
			return _ModelPart$Polygon.create(vertices, new Vector3f(faceNormal));
		}

		private static void reverseWinding(ModelPart.Vertex[] vertices, boolean triangle) {
			ModelPart.Vertex second = vertices[1];
			vertices[1] = triangle ? vertices[2] : vertices[3];
			vertices[3] = second;
			if (triangle) {
				vertices[2] = second;
			}
		}
	}
	
	public static record FaceDefinition(VertexDefinition[] vertices, Vector3f normal) implements MeshFace {
		
		public ModelPart.Polygon createFace(float texWidth, float texHeight) {
			return createFace(null, texWidth, texHeight);
		}
		
		@Override
		public ModelPart.Polygon createFace(Vector3f cubeCenter, float texWidth, float texHeight) {
			var vertices = new ModelPart.Vertex[this.vertices.length];
			for (int i = 0; i < vertices.length; i++) {
				vertices[i] = this.vertices[i].createVertex(texWidth, texHeight);
			}
//			return new ModelPart.Polygon(vertices, new Vector3f(normal));
			return _ModelPart$Polygon.create(vertices, new Vector3f(normal));
		}
	}
	
	public static record VertexDefinition(Vector3f pos, float uPos, float vPos) {
		
		public ModelPart.Vertex createVertex(float texWidth, float texHeight) {
			return new ModelPart.Vertex(new Vector3f(pos), uPos / texWidth, vPos / texHeight);
		}
	}
	
	
	@Override
	public Cube bake(int pTexWidth, int pTexHeight) {
		Cube cube = new CustomModelCube(
				0,
				0,
				this.origin.x(),
				this.origin.y(),
				this.origin.z(),
				this.dimensions.x(),
				this.dimensions.y(),
				this.dimensions.z(),
				0,
				0,
				0,
				false,
				(float)pTexWidth * this.texScale.u(),
				(float)pTexHeight * this.texScale.v(),
				NO_DIRECTIONAL_FACES
				);
		
		float texWidth = pTexWidth * texScale.u();
		float texHeight = pTexHeight * texScale.v();
		Vector3f center = new Vector3f(origin.x() + dimensions.x() / 2, origin.y() + dimensions.y() / 2, origin.z() + dimensions.z() / 2);
		ModelPart.Polygon[] polygons = this.faces.stream()
				.map(face -> face.createFace(center, texWidth, texHeight))
				.toArray(ModelPart.Polygon[]::new);
		cube.polygons = polygons;
		return cube;
	}

}
