package com.github.standobyte.jojo.client.entityrender.parsemodel.generic;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joml.Vector3f;

import com.github.standobyte.jojo.client.entityrender.parsemodel.ParseModEntityModel.Utils.RotatedCubeCounter;
import com.github.standobyte.jojo.client.entityrender.parsemodel.gecko.GeckoModelFormat;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public final class GenericModelFormatSmokeTest {
	private GenericModelFormatSmokeTest() {}

	public static void main(String[] args) throws Exception {
		run();
		for (String path : args) {
			ModelPart root = GenericModelFormat.parseGenericModel(
					JsonParser.parseString(Files.readString(Path.of(path)))).bakeRoot();
			int faces = 0;
			for (ModelPart part : root.getAllParts().toList()) {
				for (ModelPart.Cube cube : part.cubes) {
					for (ModelPart.Polygon polygon : cube.polygons) {
						check(polygon.vertices.length == 4, "non-quad output in " + path);
						check(Float.isFinite(polygon.normal.lengthSquared())
								&& Math.abs(polygon.normal.lengthSquared() - 1) < 0.0001F,
								"invalid normal in " + path);
						faces++;
					}
				}
			}
			System.out.println("Supplied model baked: faces=" + faces + ", path=" + path);
		}
		System.out.println("GenericModelFormat smoke tests passed: outliner, triangle/quad, "
				+ "convex/concave/tilted polygons, collinear quad boundary, The World helmet, UVs and finite unit normals");
	}

	public static void run() {
		verifyLeafWithoutChildrenParses();
		verifyLeafWithNullChildrenParses();
		verifyNullChildEntryFailsWithPath();
		verifyUnexportedCycleIsPruned();
		verifyTriangleAndQuadBake();
		verifyConvexHexagonsBake();
		verifyConcaveHexagonBake();
		verifyTiltedHexagonSharesOrientation();
		verifyQuadWithCollinearBoundaryBake();
		verifyDirectQuadWithCollinearLeadingVertices();
		verifyTheWorldCollapsedQuad();
		verifyBundledTheWorldMeshBakes();
		verifyTranslatedClosedMeshBoundsAndNormals();
		verifyRepeatedVertexIdPreservesOrder();
		verifyDegenerateNormalFails();
	}

	private static void verifyTriangleAndQuadBake() {
		float[][] triangle = { { 0, 0, 2 }, { 3, 0, 2 }, { 0, 2, 2 } };
		ModelPart.Cube bakedTriangle = bakeFace(triangle, new int[] { 2, 0, 1 });
		verifyPolygons(bakedTriangle, triangle, 1, 3);
		ModelPart.Vertex[] vertices = bakedTriangle.polygons[0].vertices;
		check(vertices[2].pos.equals(vertices[3].pos)
				&& vertices[2].u == vertices[3].u && vertices[2].v == vertices[3].v,
				"triangle's fourth vertex must repeat the last position and UV");
		check(vertices[0].pos.distanceSquared(new Vector3f(0, -2, 2)) < 0.000001F,
				"triangle must retain its authored first vertex instead of HashSet iteration order");

		float[][] quad = { { 0, 0, 2 }, { 3, 0, 2 }, { 3, 2, 2 }, { 0, 2, 2 } };
		// Existing Blockbench quad order repair must still handle a crossed input order.
		verifyPolygons(bakeFace(quad, new int[] { 0, 2, 1, 3 }), quad, 1, 6);
	}

	private static void verifyTranslatedClosedMeshBoundsAndNormals() {
		float[][] points = {
				{ 1, 2, 5 }, { 3, 2, 5 }, { 3, 4, 5 }, { 1, 4, 5 },
				{ 1, 2, 7 }, { 3, 2, 7 }, { 3, 4, 7 }, { 1, 4, 7 }
		};
		JsonObject model = meshModel(points, new int[] { 0, 3, 2, 1 });
		JsonObject mesh = model.getAsJsonArray("elements").get(0).getAsJsonObject();
		JsonObject template = mesh.getAsJsonObject("faces").getAsJsonObject("face");
		JsonObject faces = new JsonObject();
		int[][] orders = {
				{ 0, 3, 2, 1 }, { 4, 5, 6, 7 }, { 0, 1, 5, 4 },
				{ 3, 7, 6, 2 }, { 0, 4, 7, 3 }, { 1, 2, 6, 5 }
		};
		for (int i = 0; i < orders.length; i++) {
			JsonObject face = template.deepCopy();
			JsonArray vertices = new JsonArray();
			for (int vertex : orders[i]) vertices.add("v" + vertex);
			face.add("vertices", vertices);
			faces.add("face" + i, face);
		}
		mesh.add("faces", faces);
		ModelPart.Cube cube = bakeModel(model);
		check(cube.minX == -3 && cube.maxX == -1 && cube.minY == -4 && cube.maxY == -2
				&& cube.minZ == 5 && cube.maxZ == 7, "mesh bounds incorrectly include the bone origin");
		check(cube.polygons.length == 6, "translated closed cube lost a face");
		Vector3f center = new Vector3f(-2, -3, 6);
		for (ModelPart.Polygon polygon : cube.polygons) {
			Vector3f faceCenter = new Vector3f();
			for (ModelPart.Vertex vertex : polygon.vertices) faceCenter.add(vertex.pos);
			faceCenter.mul(0.25F).sub(center);
			check(polygon.vertices.length == 4 && Math.abs(polygon.normal.lengthSquared() - 1) < 0.0001F,
					"translated cube emitted an invalid polygon");
			check(polygon.normal.dot(faceCenter) > 0.9999F,
					"translated closed mesh has an inward-facing surface from an origin-biased center");
		}
	}

	private static void verifyConvexHexagonsBake() {
		float[][] hexagon = {
				{ -2, 4, -2 }, { 2, 4, -2 }, { 3, 4, -1 },
				{ 3, 4, 2 }, { -3, 4, 2 }, { -3, 4, -1 }
		};
		verifyPolygons(bakeFace(hexagon, new int[] { 0, 1, 2, 3, 4, 5 }), hexagon, 4, 23);
		verifyPolygons(bakeFace(hexagon, new int[] { 5, 4, 3, 2, 1, 0 }), hexagon, 4, 23);

		JsonObject model = meshModel(hexagon, new int[] { 0, 1, 2, 3, 4, 5 });
		JsonObject faces = model.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("faces");
		JsonObject triangle = faces.getAsJsonObject("face").deepCopy();
		JsonArray order = new JsonArray();
		order.add("v0"); order.add("v1"); order.add("v2");
		triangle.add("vertices", order);
		faces.add("following_triangle", triangle);
		ModelPart.Cube mixed = bakeModel(model);
		check(mixed.polygons.length == 5, "hexagon followed by triangle must emit 4+1 quads");
		int submittedVertices = 0;
		for (ModelPart.Polygon polygon : mixed.polygons) {
			check(polygon.vertices.length == 4, "a six-vertex face can cross the QUADS packet boundary");
			submittedVertices += polygon.vertices.length;
		}
		check(submittedVertices == 20, "mixed mesh must submit exactly twenty vertices");
	}

	private static void verifyTiltedHexagonSharesOrientation() {
		float[][] hexagon = {
				{ 2, 0, 2 }, { 1, 4, 5 }, { -1, 4, 3 },
				{ -2, 0, -2 }, { -1, -4, -5 }, { 1, -4, -3 }
		};
		ModelPart.Cube cube = bakeFace(hexagon, new int[] { 0, 1, 2, 3, 4, 5 });
		verifyPolygons(cube, hexagon, 4, 24 * Math.sqrt(3));
		for (ModelPart.Polygon polygon : cube.polygons) {
			check(polygon.normal.equals(cube.polygons[0].normal),
					"one tilted polygon must use a single normal and orientation decision for every ear");
		}
	}

	private static void verifyQuadWithCollinearBoundaryBake() {
		float[][] quad = { { 0, 0, 2 }, { 1, 0, 2 }, { 2, 0, 2 }, { 0, 1, 2 } };
		ModelPart.Cube cube = bakeFace(quad, new int[] { 0, 1, 2, 3 });
		verifyPolygons(cube, quad, 1, 1);
		ModelPart.Vertex[] v = cube.polygons[0].vertices;
		Vector3f first = new Vector3f(v[1].pos).sub(v[0].pos).cross(new Vector3f(v[2].pos).sub(v[0].pos));
		Vector3f second = new Vector3f(v[2].pos).sub(v[0].pos).cross(new Vector3f(v[3].pos).sub(v[0].pos));
		check(first.lengthSquared() > 0 && second.lengthSquared() > 0,
				"quad diagonal must keep both triangles nondegenerate without dropping any UV vertex");
	}

	private static void verifyDirectQuadWithCollinearLeadingVertices() {
		float[][] points = { { 0, 0, 2 }, { 1, 0, 2 }, { 2, 0, 2 }, { 0, 1, 2 } };
		for (int[] order : List.of(new int[] { 0, 1, 2, 3 }, new int[] { 3, 2, 1, 0 })) {
			BlockbenchMeshDefinition.MeshBuilder builder = new BlockbenchMeshDefinition.MeshBuilder(false);
			var face = builder.startFaceCalcNormal();
			for (int index : order) {
				face.withVertex(points[index][0], points[index][1], points[index][2], index, index + 0.5F);
			}
			face.createFace();
			ModelPart.Cube cube = builder.buildCube().bake(16, 16);
			check(cube.polygons.length == 1, "valid leading-collinear quad was omitted");
			ModelPart.Polygon polygon = cube.polygons[0];
			check(polygon.vertices.length == 4 && Math.abs(polygon.normal.lengthSquared() - 1) < 0.0001F,
					"leading-collinear quad needs a finite unit normal");
			for (int i = 0; i < order.length; i++) {
				int index = order[i];
				ModelPart.Vertex vertex = polygon.vertices[i];
				check(vertex.pos.equals(new Vector3f(points[index][0], points[index][1], points[index][2]))
						&& vertex.u == index / 16F && vertex.v == (index + 0.5F) / 16F,
						"normal fallback changed authored quad winding, positions or UVs");
			}
			Vector3f a = polygon.vertices[0].pos;
			Vector3f first = new Vector3f(polygon.vertices[1].pos).sub(a)
					.cross(new Vector3f(polygon.vertices[2].pos).sub(a));
			Vector3f second = new Vector3f(polygon.vertices[2].pos).sub(a)
					.cross(new Vector3f(polygon.vertices[3].pos).sub(a));
			check(first.dot(polygon.normal) >= 0 && second.dot(polygon.normal) >= 0
					&& Math.abs((first.length() + second.length()) * 0.5F - 1) < 0.0001F,
					"normal fallback changed visible quad coverage or disagrees with its winding");
		}
	}

	private static void verifyTheWorldCollapsedQuad() {
		// helmet.poly_mesh.polys[9] uses position indices [15, 14, 15, 15].
		BlockbenchMeshDefinition.MeshBuilder builder = new BlockbenchMeshDefinition.MeshBuilder(false);
		builder.startFaceCalcNormal()
				.withVertex(0.41728, 26, -3.97344, 75, 100)
				.withVertex(0.4173, 32.4, -3.97344, 75, 106)
				.withVertex(0.41728, 26, -3.97344, 75, 100)
				.withVertex(0.41728, 26, -3.97344, 75, 100).createFace();
		check(builder.buildCube() == null, "two-position Meshy quad must not emit invented geometry or normals");

		builder.startFaceCalcNormal()
				.withVertex(0.41728, 26, -3.97344, 75, 100)
				.withVertex(0.4173, 32.4, -3.97344, 75, 106)
				.withVertex(0.41728, 26, -3.97344, 75, 100)
				.withVertex(Double.NaN, 26, -3.97344, 75, 100).createFace();
		try {
			builder.buildCube().bake(128, 128);
			throw new AssertionError("non-finite positions must not be hidden as a collapsed quad");
		}
		catch (IllegalArgumentException expected) {
			check(expected.getMessage().contains("non-finite"), "non-finite vertex needs an actionable error");
		}
	}

	private static void verifyBundledTheWorldMeshBakes() {
		String resource = "/assets/jojo_ripples/stand_skins/the_world/assets/jojo_ripples/geo/the_world.geo.json";
		try (InputStream input = GenericModelFormatSmokeTest.class.getResourceAsStream(resource)) {
			check(input != null, "bundled The World model resource is missing");
			ModelPart root = GeckoModelFormat.parseGeckoModel(
					JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))).bakeRoot();
			ModelPart helmet = root.getAllParts().filter(part -> part.hasChild("helmet")).findFirst()
					.orElseThrow(() -> new AssertionError("The World helmet bone is missing")).getChild("helmet");
			check(helmet.cubes.size() == 1 && helmet.cubes.get(0).polygons.length == 14,
					"The World helmet must preserve all fourteen non-collapsed mesh faces");
			for (ModelPart part : root.getAllParts().toList()) {
				for (ModelPart.Cube cube : part.cubes) {
					for (ModelPart.Polygon polygon : cube.polygons) {
						check(polygon.vertices.length == 4 && Float.isFinite(polygon.normal.lengthSquared())
								&& Math.abs(polygon.normal.lengthSquared() - 1) < 0.0001F,
								"bundled The World model emitted an invalid polygon normal");
					}
				}
			}
		}
		catch (IOException error) {
			throw new AssertionError("Cannot read bundled The World model", error);
		}
	}

	private static void verifyConcaveHexagonBake() {
		float[][] concave = {
				{ 0, 0, 3 }, { 3, 0, 3 }, { 3, 1, 3 },
				{ 1, 1, 3 }, { 1, 3, 3 }, { 0, 3, 3 }
		};
		// Starting at the outer corner makes a naive fan cross the missing top-right region.
		for (int[] order : List.of(new int[] { 1, 2, 3, 4, 5, 0 },
				new int[] { 0, 5, 4, 3, 2, 1 })) {
			ModelPart.Cube cube = bakeFace(concave, order);
			verifyPolygons(cube, concave, 4, 5);
			for (ModelPart.Polygon polygon : cube.polygons) {
				Vector3f a = polygon.vertices[0].pos;
				Vector3f b = polygon.vertices[1].pos;
				Vector3f c = polygon.vertices[2].pos;
				// Barycentric samples must stay within the authored L-shaped surface.
				for (int i = 1; i < 8; i++) {
					for (int j = 1; j < 8 - i; j++) {
						float u = i / 8.0F, v = j / 8.0F, w = 1 - u - v;
						float x = -(a.x * u + b.x * v + c.x * w);
						float y = -(a.y * u + b.y * v + c.y * w);
						check(x >= -0.0001F && y >= -0.0001F && x <= 3.0001F && y <= 3.0001F
								&& (x <= 1.0001F || y <= 1.0001F), "triangulation filled a concave notch");
					}
				}
			}
		}
	}

	private static void verifyRepeatedVertexIdPreservesOrder() {
		float[][] points = { { 0, 0, 2 }, { 3, 0, 2 }, { 0, 2, 2 } };
		ModelPart.Cube cube = bakeFace(points, new int[] { 2, 0, 1, 2 });
		verifyPolygons(cube, points, 1, 3);
		check(cube.polygons[0].vertices[0].pos.distanceSquared(new Vector3f(0, -2, 2)) < 0.000001F,
				"deduplication changed the original polygon order");
	}

	private static void verifyDegenerateNormalFails() {
		try {
			bakeFace(new float[][] { { 0, 0, 0 }, { 1, 0, 0 }, { 2, 0, 0 } }, new int[] { 0, 1, 2 });
			throw new AssertionError("collinear triangle must not emit a NaN normal");
		}
		catch (IllegalArgumentException expected) {
			check(expected.getMessage().contains("normal"), "degenerate face needs an actionable error");
		}
		BlockbenchMeshDefinition.MeshBuilder builder = new BlockbenchMeshDefinition.MeshBuilder(false);
		builder.startFace(new Vector3f(Float.NaN, 0, 1))
				.withVertex(0, 0, 0, 0, 0).withVertex(1, 0, 0, 1, 0).withVertex(0, 1, 0, 0, 1).createFace();
		try {
			builder.buildCube().bake(16, 16);
			throw new AssertionError("explicit non-finite normal must not reach the vertex consumer");
		}
		catch (IllegalArgumentException expected) {
			check(expected.getMessage().contains("normal"), "non-finite normal needs an actionable error");
		}
	}

	private static ModelPart.Cube bakeFace(float[][] points, int[] order) {
		return bakeModel(meshModel(points, order));
	}

	private static ModelPart.Cube bakeModel(JsonObject json) {
		ModelPart root = GenericModelFormat.parseGenericModel(json).bakeRoot();
		ModelPart part = root.getChild("root");
		check(part.cubes.size() == 1, "fixture must bake one real mesh cube");
		return part.cubes.get(0);
	}

	private static JsonObject meshModel(float[][] points, int[] order) {
		JsonObject model = new JsonObject();
		JsonObject resolution = new JsonObject();
		resolution.addProperty("width", 16); resolution.addProperty("height", 16);
		model.add("resolution", resolution);
		JsonObject vertices = new JsonObject();
		JsonObject uv = new JsonObject();
		for (int i = 0; i < points.length; i++) {
			vertices.add("v" + i, numbers(points[i]));
			uv.add("v" + i, numbers(new float[] { i + 0.25F, i + 0.75F }));
		}
		JsonArray faceVertices = new JsonArray();
		for (int i : order) faceVertices.add("v" + i);
		JsonObject face = new JsonObject();
		face.add("vertices", faceVertices); face.add("uv", uv); face.addProperty("texture", 0);
		JsonObject faces = new JsonObject(); faces.add("face", face);
		JsonObject element = new JsonObject();
		element.addProperty("type", "mesh"); element.addProperty("name", "test_mesh");
		element.addProperty("uuid", "00000000-0000-0000-0000-000000000001");
		element.addProperty("visibility", true);
		element.add("origin", numbers(new float[] { 0, 0, 0 }));
		element.add("vertices", vertices); element.add("faces", faces);
		JsonArray elements = new JsonArray(); elements.add(element); model.add("elements", elements);
		JsonObject group = new JsonObject(); group.addProperty("name", "root");
		group.add("origin", numbers(new float[] { 0, 0, 0 })); group.addProperty("visibility", true);
		JsonArray children = new JsonArray(); children.add("00000000-0000-0000-0000-000000000001");
		group.add("children", children);
		JsonArray outliner = new JsonArray(); outliner.add(group); model.add("outliner", outliner);
		return model;
	}

	private static JsonArray numbers(float[] values) {
		JsonArray array = new JsonArray();
		for (float value : values) array.add(value);
		return array;
	}

	private static void verifyPolygons(ModelPart.Cube cube, float[][] source, int expectedPolygons, double expectedArea) {
		check(cube.polygons.length == expectedPolygons, "unexpected number of baked polygons");
		Set<Integer> used = new HashSet<>();
		double area = 0;
		Vector3f firstNormal = cube.polygons[0].normal;
		for (ModelPart.Polygon polygon : cube.polygons) {
			check(polygon.vertices.length == 4, "every emitted polygon must be a four-vertex QUADS packet");
			Vector3f normal = polygon.normal;
			check(Float.isFinite(normal.x) && Float.isFinite(normal.y) && Float.isFinite(normal.z)
					&& Math.abs(normal.lengthSquared() - 1) < 0.0001F, "baked normal is not finite and unit length");
			check(normal.dot(firstNormal) > 0.9999F, "coplanar triangles disagree on their normal");
			for (ModelPart.Vertex vertex : polygon.vertices) {
				int found = -1;
				for (int i = 0; i < source.length; i++) {
					if (vertex.pos.distanceSquared(new Vector3f(-source[i][0], -source[i][1], source[i][2])) < 0.000001F) {
						found = i; break;
					}
				}
				check(found >= 0, "triangulation introduced or moved a source vertex");
				used.add(found);
				check(Math.abs(vertex.u - (found + 0.25F) / 16) < 0.00001F
						&& Math.abs(vertex.v - (found + 0.75F) / 16) < 0.00001F,
						"source position lost its per-vertex UV during triangulation");
			}
			ModelPart.Vertex[] v = polygon.vertices;
			Vector3f first = new Vector3f(v[1].pos).sub(v[0].pos).cross(new Vector3f(v[2].pos).sub(v[0].pos));
			Vector3f second = new Vector3f(v[2].pos).sub(v[0].pos).cross(new Vector3f(v[3].pos).sub(v[0].pos));
			check(first.dot(normal) > 0 && (second.lengthSquared() == 0 || second.dot(normal) > 0),
					"QUADS winding does not agree with its face normal");
			area += (first.length() + second.length()) * 0.5;
		}
		check(used.size() == source.length, "triangulation dropped a boundary vertex and its UV");
		check(Math.abs(area - expectedArea) < 0.0001, "triangulation overlaps or loses source polygon area: " + area);
	}

	private static void verifyLeafWithoutChildrenParses() {
		assertLeafParses("""
				{
				  "resolution": {"width": 16, "height": 16},
				  "elements": [],
				  "outliner": [{
				    "name": "root",
				    "origin": [0, 0, 0],
				    "children": [{
				      "name": "leaf",
				      "origin": [0, 0, 0]
				    }]
				  }]
				}
				""", "missing children");
	}

	private static void verifyLeafWithNullChildrenParses() {
		assertLeafParses("""
				{
				  "resolution": {"width": 16, "height": 16},
				  "elements": [],
				  "outliner": [{
				    "name": "root",
				    "origin": [0, 0, 0],
				    "children": [{
				      "name": "leaf",
				      "origin": [0, 0, 0],
				      "children": null
				    }]
				  }]
				}
				""", "null children");
	}

	private static void verifyNullChildEntryFailsWithPath() {
		try {
			GenericModelFormat.parseGenericModel(JsonParser.parseString("""
					{
					  "resolution": {"width": 16, "height": 16},
					  "elements": [],
					  "outliner": [{
					    "name": "root",
					    "origin": [0, 0, 0],
					    "children": [null]
					  }]
					}
					"""));
			throw new AssertionError("null outliner child must fail");
		}
		catch (JsonParseException error) {
			check(error.getMessage().contains("root[0]"),
					"null child error must include its outliner path");
		}
	}

	private static void verifyUnexportedCycleIsPruned() {
		GenericModelFormat.GroupParsed root =
				group("root", true);
		GenericModelFormat.GroupParsed hidden =
				group("hidden", false);
		root.children().add(hidden);
		hidden.children().add(root);

		GenericModelFormat.recursiveVisitChildren(
				root, Map.of(), new RotatedCubeCounter());
		check(root.children().isEmpty(),
				"unexported group remained in the model tree");
	}

	private static GenericModelFormat.GroupParsed group(
			String name, boolean exported) {
		return new GenericModelFormat.GroupParsed(
				name,
				new Vector3f(),
				null,
				null,
				exported,
				false,
				true,
				0,
				new ArrayList<>());
	}

	private static void assertLeafParses(String json, String caseName) {
		LayerDefinition definition = GenericModelFormat.parseGenericModel(JsonParser.parseString(json));
		PartDefinition root = definition.mesh.getRoot().getChild("root");
		check(root != null, caseName + " model lost its root group");
		check(root.getChild("leaf") != null, caseName + " model lost its leaf group");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
