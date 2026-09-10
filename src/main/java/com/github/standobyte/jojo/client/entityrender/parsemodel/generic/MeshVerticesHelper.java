package com.github.standobyte.jojo.client.entityrender.parsemodel.generic;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.joml.Vector3f;

import com.github.standobyte.jojo.client.entityrender.parsemodel.generic.BlockbenchMeshDefinition.VertexDefinition;

/**
 * Fixes vertex order for four-vertex faces in Blockbench-format models.
 *
 * The ordering algorithm derives from
 * <a href="https://github.com/JannisX11/blockbench/blob/368efc7c8275d11fac355efa90720ebcd850f3b8/js/outliner/mesh.js#L186">Blockbench</a>
 * by JannisX11, licensed under GPL-3.0-or-later.
 *
 * The line/plane math derives from
 * <a href="https://github.com/mrdoob/three.js/blob/8540d9f9a6818db6879d8a92abe162ea7efa3475/src/math/Line3.js#L84">three.js</a>,
 * licensed under the MIT License. Copyright (c) 2010-2025 three.js authors.
 *
 * Modification notice (2026-07-26): ported the relevant JavaScript operations
 * to Java/JOML and adapted them to BlockbenchMeshDefinition vertices.
 */
public class MeshVerticesHelper {
	/** Ear clipping on the dominant plane preserves concave boundaries and each vertex's UV. */
	public static List<VertexDefinition[]> triangulatePolygon(VertexDefinition[] vertices) {
		if (vertices.length < 3) {
			throw new IllegalArgumentException("A mesh polygon needs at least three vertices");
		}
		double nx = 0, ny = 0, nz = 0;
		for (int i = 0; i < vertices.length; i++) {
			Vector3f a = vertices[i].pos();
			Vector3f b = vertices[(i + 1) % vertices.length].pos();
			if (!Float.isFinite(a.x) || !Float.isFinite(a.y) || !Float.isFinite(a.z)) {
				throw new IllegalArgumentException("Non-finite mesh polygon position");
			}
			nx += ((double) a.y - b.y) * ((double) a.z + b.z);
			ny += ((double) a.z - b.z) * ((double) a.x + b.x);
			nz += ((double) a.x - b.x) * ((double) a.y + b.y);
		}
		int axis = Math.abs(nx) >= Math.abs(ny) && Math.abs(nx) >= Math.abs(nz)
				? 0 : Math.abs(ny) >= Math.abs(nz) ? 1 : 2;
		double[] x = new double[vertices.length];
		double[] y = new double[vertices.length];
		double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < vertices.length; i++) {
			Vector3f p = vertices[i].pos();
			x[i] = axis == 0 ? p.y : p.x;
			y[i] = axis == 2 ? p.y : p.z;
			minX = Math.min(minX, x[i]); maxX = Math.max(maxX, x[i]);
			minY = Math.min(minY, y[i]); maxY = Math.max(maxY, y[i]);
		}
		double scale = Math.max(maxX - minX, maxY - minY);
		double epsilon = Math.max(1.0E-20, scale * scale * 1.0E-10);
		double area = 0;
		for (int i = 1; i + 1 < vertices.length; i++) {
			area += cross(x, y, 0, i, i + 1);
		}
		if (!Double.isFinite(area) || Math.abs(area) <= epsilon) {
			throw new IllegalArgumentException("Degenerate mesh polygon cannot be triangulated");
		}
		double winding = Math.signum(area);
		List<Integer> remaining = new ArrayList<>();
		for (int i = 0; i < vertices.length; i++) remaining.add(i);
		List<VertexDefinition[]> triangles = new ArrayList<>();
		while (remaining.size() > 3) {
			boolean clipped = false;
			for (int i = 0; i < remaining.size(); i++) {
				int a = remaining.get((i + remaining.size() - 1) % remaining.size());
				int b = remaining.get(i);
				int c = remaining.get((i + 1) % remaining.size());
				if (winding * cross(x, y, a, b, c) <= epsilon) continue;
				boolean containsVertex = false;
				for (int p : remaining) {
					if (p != a && p != b && p != c
							&& winding * cross(x, y, a, b, p) >= -epsilon
							&& winding * cross(x, y, b, c, p) >= -epsilon
							&& winding * cross(x, y, c, a, p) >= -epsilon) {
						containsVertex = true;
						break;
					}
				}
				if (containsVertex) continue;
				triangles.add(new VertexDefinition[] { vertices[a], vertices[b], vertices[c] });
				remaining.remove(i);
				clipped = true;
				break;
			}
			if (!clipped) {
				throw new IllegalArgumentException("Mesh polygon has a degenerate or non-simple boundary");
			}
		}
		int a = remaining.get(0), b = remaining.get(1), c = remaining.get(2);
		if (winding * cross(x, y, a, b, c) <= epsilon) {
			throw new IllegalArgumentException("Degenerate final mesh polygon triangle");
		}
		triangles.add(new VertexDefinition[] { vertices[a], vertices[b], vertices[c] });
		return triangles;
	}

	private static double cross(double[] x, double[] y, int a, int b, int c) {
		return (x[b] - x[a]) * (y[c] - y[a]) - (y[b] - y[a]) * (x[c] - x[a]);
	}
	
	public static void sortVertices(VertexDefinition[] vertices) {
		if (vertices.length < 4) return;

		if (MeshVerticesHelper.magicFunction(vertices[1].pos(), vertices[2].pos(), vertices[0].pos(), vertices[3].pos())) {
			ArrayUtils.swap(vertices, 0, 1);
			ArrayUtils.swap(vertices, 0, 2);
		} else if (MeshVerticesHelper.magicFunction(vertices[0].pos(), vertices[1].pos(), vertices[2].pos(), vertices[3].pos())) {
			ArrayUtils.swap(vertices, 1, 2);
		}
		if (vertices.length == 4 && !hasNonDegenerateDiagonal(vertices)) {
			// Preserve the boundary and UVs, but use the other diagonal when an edge has a collinear vertex.
			VertexDefinition first = vertices[0];
			vertices[0] = vertices[1];
			vertices[1] = vertices[2];
			vertices[2] = vertices[3];
			vertices[3] = first;
		}
	}

	private static boolean hasNonDegenerateDiagonal(VertexDefinition[] vertices) {
		Vector3f a = vertices[0].pos(), b = vertices[1].pos();
		Vector3f c = vertices[2].pos(), d = vertices[3].pos();
		Vector3f first = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
		Vector3f second = new Vector3f(c).sub(a).cross(new Vector3f(d).sub(a));
		return first.lengthSquared() > 0 && second.lengthSquared() > 0;
	}
	
	private static Vector3f _startP = new Vector3f();
	private static Vector3f _startEnd = new Vector3f();
	private static Vector3f normal = new Vector3f();
	
	private static boolean magicFunction(Vector3f base1, Vector3f base2, Vector3f top, Vector3f check) {
		// Construct a plane with coplanar points "base1" and "base2" with a normal towards "top"
		subVectors(_startP, top, base1);
		subVectors(_startEnd, base2, base1);
		float startEnd2 = _startEnd.dot(_startEnd);
		float startEnd_startP = _startEnd.dot(_startP);
		float t = startEnd_startP / startEnd2;
		subVectors(normal, base2, base1);
		normal.mul(t);
		normal.add(base1);
		normal.sub(top);
		
		float planeConstant = -base2.dot(normal);
		float distance = normal.dot(check) + planeConstant;
		return distance > 0;
	}

	private static void subVectors(Vector3f target, Vector3f a, Vector3f b) {
		target.set(a.x(), a.y(), a.z());
		target.sub(b);
	}
	
}
